package org.ligoj.app.ldap.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;

import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.app.api.GroupLdap;
import org.ligoj.app.api.LdapElement;
import org.ligoj.app.api.UserLdap;
import org.ligoj.app.iam.IGroupRepository;
import org.ligoj.app.ldap.LdapUtils;
import org.ligoj.app.ldap.dao.LdapCacheRepository.LdapData;
import org.ligoj.app.ldap.model.ContainerType;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Group LDAP repository
 */
@Slf4j
public class GroupLdapRepository extends AbstractContainerLdaRepository<GroupLdap> implements IGroupRepository {

	/**
	 * Default DN member for new group. This is required for some LDAP implementation where "uniqueMember" attribute is
	 * required for "groupOfUniqueNames" class.
	 * 
	 * @see <a href="https://msdn.microsoft.com/en-us/library/ms682261(v=vs.85).aspx">MSDN</a>
	 * @see <a href="https://tools.ietf.org/html/rfc4519#page-19">IETF</a>
	 */
	public static final String DEFAULT_MEMBER_DN = "uid=none";

	@Setter
	private String groupsBaseDn;

	private static final String DEPARTMENT_ATTRIBUTE = "businessCategory";
	private static final String GROUP_OF_UNIQUE_NAMES = "groupOfUniqueNames";
	private static final String UNIQUE_MEMBER = "uniqueMember";

	/**
	 * Default constructor for a container of type {@link ContainerType#GROUP}
	 */
	public GroupLdapRepository() {
		super(ContainerType.GROUP, GROUP_OF_UNIQUE_NAMES);
	}

	/**
	 * Fetch and return all normalized groups. Note the result use cache, so does not reflect the current state of LDAP.
	 * LDAP. Cache manager is involved.
	 * 
	 * @return the groups. Key is the normalized name, Value is the corresponding LDAP group containing real CN, DN and
	 *         normalized UID members.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Map<String, GroupLdap> findAll() {
		return (Map<String, GroupLdap>) ldapCacheRepository.getLdapData().get(LdapData.GROUP);
	}

	/**
	 * Fetch and return all normalized groups. Note the result use cache, so does not reflect the current state of LDAP.
	 * LDAP.
	 * 
	 * @return the groups. Key is the normalized name, Value is the corresponding LDAP group containing real CN, DN and
	 *         normalized UID members.
	 */
	public Map<String, GroupLdap> findAllNoCache() {
		final Map<String, GroupLdap> groups = new HashMap<>();
		final Map<String, Set<String>> subGroupsDn = new HashMap<>();
		final Map<String, GroupLdap> dnToGroups = new HashMap<>();

		// First pass, collect the groups and dirty relationships
		for (final DirContextAdapter groupRaw : template.search(groupsBaseDn, new EqualsFilter("objectClass", GROUP_OF_UNIQUE_NAMES).encode(),
				(Object ctx) -> (DirContextAdapter) ctx)) {
			final Set<String> members = new HashSet<>();
			final String dn = LdapUtils.normalize(groupRaw.getDn().toString());
			final String name = groupRaw.getStringAttribute("cn");
			final HashSet<String> subGroups = new HashSet<>();
			for (final String memberDN : ArrayUtils.nullToEmpty(groupRaw.getStringAttributes(UNIQUE_MEMBER))) {
				if (memberDN.startsWith("uid")) {
					// User membership
					members.add(memberDN);
				} else {
					// Group (or whatever) membership
					subGroups.add(memberDN);
				}
			}
			final GroupLdap group = new GroupLdap(dn, name, members);
			subGroupsDn.put(group.getId(), subGroups);
			groups.put(group.getId(), group);
			dnToGroups.put(dn, group);
		}

		// Second pass to validate the sub-groups and complete the opposite relation
		updateSubGroups(groups, subGroupsDn, dnToGroups);

		return groups;
	}

	/**
	 * Complete the sub-groups hierarchy and update the two-ways relationship
	 */
	private void updateSubGroups(final Map<String, GroupLdap> groups, final Map<String, Set<String>> subGroupsDn,
			final Map<String, GroupLdap> dnToGroups) {
		for (final GroupLdap group : groups.values()) {
			for (final String subGroupDn : subGroupsDn.get(group.getId())) {
				final GroupLdap subGroup = dnToGroups.get(LdapUtils.normalize(subGroupDn));
				if (subGroup == null) {
					// The unique member previously found does not match to an existing group, report it
					log.warn("Broken group reference found '" + group.getDn() + "' --> " + subGroupDn);
				} else {
					// This is a valid sub group, create both sides of this relation. Raw CN are used
					group.getSubGroups().add(subGroup.getId());
					subGroup.getGroups().add(group.getId());
				}
			}
		}
	}

	private void removeFromJavaCache(final GroupLdap group) {
		// Remove the sub groups from LDAP
		new ArrayList<>(group.getSubGroups()).stream().map(this::findById).filter(Objects::nonNull)
				.forEach(child -> removeGroup(child, group.getId()));

		// Remove from the parent LDAP groups
		new ArrayList<>(group.getGroups()).forEach(parent -> removeGroup(group, parent));

		// Also, update the raw cache
		findAll().remove(group.getId());
	}

	/**
	 * Delete the given group. There is no synchronized block, so error could occur; this is assumed for performance
	 * purpose.
	 * 
	 * @param group
	 *            the LDAP group.
	 */
	@Override
	public void delete(final GroupLdap group) {

		/*
		 * Remove from the managed groups, all groups within (sub LDAP DN) this group. This operation is needed since we
		 * are not rebuilding the cache from the LDAP. This save a lot of computations.
		 */
		findAll().values().stream().filter(g -> LdapUtils.equalsOrParentOf(group.getDn(), g.getDn())).collect(Collectors.toList())
				.forEach(this::removeFromJavaCache);

		// Remove from LDAP the recursively the node. Anything that was not nicely cleaned will be deleted there.
		template.unbind(org.springframework.ldap.support.LdapUtils.newLdapName(group.getDn()), true);

		// Also, update the cache
		ldapCacheRepository.delete(group);
	}

	/**
	 * Empty the group. All users from the group will not be anymore associated to this group, and the members of the
	 * group will be emptied. Not that the sub groups are not removed, only users are concerned.
	 * 
	 * @param group
	 *            the LDAP group.
	 * @param users
	 *            All known users could be removed from this group.
	 */
	public void empty(final GroupLdap group, final Map<String, UserLdap> users) {
		ldapCacheRepository.empty(group, users);
	}

	@Override
	public GroupLdap create(final String dn, final String cn) {
		final GroupLdap group = super.create(dn, cn);

		// Also, update the SQL cache
		ldapCacheRepository.create(group);
		return group;
	}

	/**
	 * Add an "uniqueMember" to given group. Cache is not updated there.
	 * 
	 * @param element
	 *            The new member to add.
	 * @param group
	 *            CN of the group to update. Must be normalized.
	 * @return the target {@link GroupLdap}.
	 */
	private GroupLdap addMember(final LdapElement element, final String group) {
		final GroupLdap groupLdap = findById(group);
		if (!groupLdap.getMembers().contains(element.getId())) {
			// Not useless operation
			addAttributes(groupLdap.getDn(), UNIQUE_MEMBER, Collections.singletonList(element.getDn()));
		}
		return groupLdap;
	}

	/**
	 * Update the uniqueMember attribute of the user having changed DN. Cache is not updated since.
	 * 
	 * @param oldUniqueMemberDn
	 *            Old DN of the member to update.
	 * @param newUniqueMemberDn
	 *            New DN of the member to update. UID of the DN should unchanged.
	 * @param group
	 *            CN of the group to update.
	 */
	public void updateMemberDn(final String group, final String oldUniqueMemberDn, final String newUniqueMemberDn) {
		final GroupLdap groupLdap = findById(group);
		final ModificationItem[] mods = new ModificationItem[2];
		mods[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, new BasicAttribute(UNIQUE_MEMBER, oldUniqueMemberDn));
		mods[1] = new ModificationItem(DirContext.ADD_ATTRIBUTE, new BasicAttribute(UNIQUE_MEMBER, newUniqueMemberDn));
		template.modifyAttributes(org.springframework.ldap.support.LdapUtils.newLdapName(groupLdap.getDn()), mods);
	}

	/**
	 * Add a user to given group. Cache is updated.
	 * 
	 * @param user
	 *            {@link UserLdap} to add.
	 * @param group
	 *            CN of the group to update.
	 */
	public void addUser(final UserLdap user, final String group) {
		// Add to Java cache and to SQL cache
		ldapCacheRepository.addUserToGroup(user, addMember(user, group));
	}

	/**
	 * Add a group to another group. Cache is updated.
	 * 
	 * @param subGroup
	 *            {@link GroupLdap} to add to a parent group.
	 * @param toGroup
	 *            CN of the parent group to update.
	 */
	public void addGroup(final GroupLdap subGroup, final String toGroup) {
		// Add to Java cache and to SQL cache
		ldapCacheRepository.addGroupToGroup(subGroup, addMember(subGroup, toGroup));
	}

	/**
	 * Remove a user from a given group. Cache is updated.
	 * 
	 * @param user
	 *            {@link UserLdap} to remove.
	 * @param group
	 *            CN of the group to update.
	 */
	public void removeUser(final UserLdap user, final String group) {
		// Remove from Java cache and from SQL cache
		ldapCacheRepository.removeUserFromGroup(user, removeMember(user, group));
	}

	/**
	 * Remove a group from another group. Cache is updated. There is no deletion.
	 * 
	 * @param subGroup
	 *            {@link GroupLdap} to remove.
	 * @param group
	 *            CN of the group to update.
	 */
	public void removeGroup(final GroupLdap subGroup, final String group) {
		// Remove from Java cache and from SQL cache
		ldapCacheRepository.removeGroupFromGroup(subGroup, removeMember(subGroup, group));
	}

	/**
	 * Remove an "uniqueMember" from given group. Cache is not updated there.
	 * 
	 * @param uniqueMember
	 *            DN of the member to remove.
	 * @param group
	 *            CN of the group to update. Must be normalized.
	 * @return the {@link GroupLdap} where the member has just been removed from.
	 */
	private GroupLdap removeMember(final LdapElement uniqueMember, final String group) {
		final GroupLdap groupLdap = findById(group);
		if (groupLdap.getMembers().contains(uniqueMember.getId())) {
			// Not useless LDAP operation, avoid LDAP duplicate deletion
			final ModificationItem[] mods = new ModificationItem[1];
			mods[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, new BasicAttribute(UNIQUE_MEMBER, uniqueMember.getDn()));
			try {
				template.modifyAttributes(org.springframework.ldap.support.LdapUtils.newLdapName(groupLdap.getDn()), mods);
			} catch (final org.springframework.ldap.AttributeInUseException aiue) {
				// Even if the membership update failed, the user does not exist anymore. A broken reference can remains
				// in LDAP, but this case is well managed.
				log.info("Unable to remove user {} from the group {} : {}", uniqueMember.getDn(), group, aiue.getMessage());
			} catch (final org.springframework.ldap.SchemaViolationException sve) {
				// Occurs when there is a LDAP schema violation such as as last member removed
				log.warn("Unable to remove user {} from the group {} : {}", uniqueMember.getDn(), group, sve.getMessage());
				throw new ValidationJsonException("groups", "last-member-of-group", "user", uniqueMember.getId(), "group", group); // NOPMD
			}
		}
		return groupLdap;
	}

	/**
	 * Map {@link GroupLdap} to LDAP
	 */
	@Override
	protected void mapToContext(final GroupLdap entry, final DirContextOperations context) {
		context.setAttributeValue("cn", entry.getName());
		// Dummy member for initial group, due to LDAP compliance of class "groupOfUniqueNames"
		context.setAttributeValue(UNIQUE_MEMBER, DEFAULT_MEMBER_DN);
	}

	@Override
	protected GroupLdap newContainer(final String dn, final String cn) {
		return new GroupLdap(LdapUtils.normalize(dn), cn, new HashSet<>());
	}

	/**
	 * Add attributes to the given DN.
	 * 
	 * @param dn
	 *            The target DN.
	 * @param attribute
	 *            The attribute name.
	 * @param values
	 *            The values to add. My be empty.
	 */
	public void addAttributes(final String dn, final String attribute, final Collection<String> values) {
		if (values.isEmpty()) {
			// Ignore this call
			return;
		}

		// Build the modification operation
		final ModificationItem[] mods = values.stream().map(v -> new ModificationItem(DirContext.ADD_ATTRIBUTE, new BasicAttribute(attribute, v)))
				.toArray(ModificationItem[]::new);
		try {
			// Perform the addition
			template.modifyAttributes(org.springframework.ldap.support.LdapUtils.newLdapName(dn), mods);
		} catch (final org.springframework.ldap.AttributeInUseException aiue) {
			if (!aiue.getMessage().matches(".*(value #0 already exists|error code 20|ATTRIBUTE_OR_VALUE_EXISTS).*")) {
				throw aiue;
			}
			log.info("{} is already member of {}", values, dn);
		}
	}

	@Override
	public GroupLdap findByDepartment(final String department) {
		final AndFilter filter = new AndFilter();
		filter.and(new EqualsFilter("objectclass", GROUP_OF_UNIQUE_NAMES));
		filter.and(new EqualsFilter(DEPARTMENT_ATTRIBUTE, department));
		return template.search(groupsBaseDn, filter.encode(), (Object ctx) -> (DirContextAdapter) ctx).stream().findFirst()
				.map(c -> c.getStringAttribute("cn")).map(LdapUtils::normalize).map(this::findById).orElse(null);
	}
}

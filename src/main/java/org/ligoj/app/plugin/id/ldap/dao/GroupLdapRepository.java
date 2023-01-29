/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.dao;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.ligoj.app.api.Normalizer;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.IGroupRepository;
import org.ligoj.app.iam.ResourceOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.dao.CacheGroupRepository;
import org.ligoj.app.iam.model.CacheGroup;
import org.ligoj.app.model.ContainerType;
import org.ligoj.app.plugin.id.DnUtils;
import org.ligoj.app.plugin.id.dao.AbstractMemCacheRepository.CacheDataType;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;

import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import java.util.*;

/**
 * Group LDAP repository
 */
@Slf4j
public class GroupLdapRepository extends AbstractContainerLdapRepository<GroupOrg, CacheGroup>
		implements IGroupRepository {

	/**
	 * Default DN member for new group. This is required for some LDAP implementation where "uniqueMember" attribute is
	 * required for "groupOfUniqueNames" class.
	 *
	 * @see <a href="https://msdn.microsoft.com/en-us/library/ms682261(v=vs.85).aspx">MSDN</a>
	 * @see <a href="https://tools.ietf.org/html/rfc4519#page-19">IETF</a>
	 */
	public static final String DEFAULT_MEMBER_DN = "uid=none";

	private static final String DEPARTMENT_ATTRIBUTE = "businessCategory";

	@Autowired
	private CacheGroupRepository cacheGroupRepository;

	@Setter
	private String memberAttribute;

	/**
	 * Default constructor for a container of type {@link ContainerType#GROUP}
	 */
	public GroupLdapRepository() {
		super(ContainerType.GROUP);
	}

	@Override
	public CacheGroupRepository getCacheRepository() {
		return cacheGroupRepository;
	}

	/**
	 * Fetch and return all normalized groups. Note the result use cache, so does not reflect the current state of LDAP.
	 * LDAP. Cache manager is involved.
	 *
	 * @return the groups. Key is the normalized name, Value is the corresponding LDAP group containing real CN, DN and
	 * normalized UID members.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Map<String, GroupOrg> findAll() {
		return (Map<String, GroupOrg>) cacheRepository.getData().get(CacheDataType.GROUP);
	}

	/**
	 * Fetch and return all normalized groups. Note the result use cache, so does not reflect the current state of LDAP.
	 * LDAP.
	 *
	 * @return the groups. Key is the normalized name, Value is the corresponding LDAP group containing real CN, DN and
	 * normalized UID members.
	 */
	@Override
	public Map<String, GroupOrg> findAllNoCache() {
		final var groups = new HashMap<String, GroupOrg>();
		final var subGroupsDn = new HashMap<String, Set<String>>();
		final var dnToGroups = new HashMap<String, GroupOrg>();

		// First pass, collect the groups and dirty relationships
		for (final var ldap : template.search(baseDn,
				new EqualsFilter(OBJECT_CLASS, className).encode(),
				(Object ctx) -> (DirContextAdapter) ctx)) {
			final var members = new HashSet<String>();
			final var dn = ldap.getDn().toString().toLowerCase(Locale.ENGLISH);
			final var name = ldap.getStringAttribute("cn");
			final var subGroups = new HashSet<String>();
			for (final var memberDN : ArrayUtils.nullToEmpty(ldap.getStringAttributes(memberAttribute))) {
				if (memberDN.startsWith("uid")) {
					// User membership
					members.add(memberDN);
				} else {
					// Group (or whatever) membership
					subGroups.add(memberDN);
				}
			}
			final var group = new GroupOrg(dn, name, members);
			subGroupsDn.put(group.getId(), subGroups);
			groups.put(group.getId(), group);
			dnToGroups.put(dn, group);
		}

		// Second pass to validate the subgroups and complete the opposite relation
		updateSubGroups(groups, subGroupsDn, dnToGroups);

		return groups;
	}

	/**
	 * Complete the subgroups hierarchy and update the two-ways relationship
	 */
	private void updateSubGroups(final Map<String, GroupOrg> groups, final Map<String, Set<String>> subGroupsDn,
			final Map<String, GroupOrg> dnToGroups) {
		for (final var group : groups.values()) {
			for (final var subGroupDn : subGroupsDn.get(group.getId())) {
				final var subGroup = dnToGroups.get(Normalizer.normalize(subGroupDn));
				if (subGroup == null) {
					// The unique member previously found does not match to an existing group, report it
					log.warn("Broken group reference found '{}' --> {}", group.getDn(), subGroupDn);
				} else {
					// This is a valid subgroup, create both sides of this relation. Raw CN are used
					group.getSubGroups().add(subGroup.getId());
					subGroup.getGroups().add(group.getId());
				}
			}
		}
	}

	private void removeFromJavaCache(final GroupOrg group) {
		// Remove the subgroups from LDAP
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
	 * @param group the LDAP group.
	 */
	@Override
	public void delete(final GroupOrg group) {

		/*
		 * Remove from this group, all groups within (sub LDAP DN) this group. This operation is needed since we are not
		 * rebuilding the cache from the LDAP. This save a lot of computations.
		 */
		findAll().values().stream().filter(g -> DnUtils.equalsOrParentOf(group.getDn(), g.getDn())).toList()
				.forEach(this::removeFromJavaCache);

		// Remove recursively from LDAP the group. Anything that was not nicely cleaned will be deleted there.
		template.unbind(org.springframework.ldap.support.LdapUtils.newLdapName(group.getDn()), true);

		// Also, update the cache
		cacheRepository.delete(group);
	}

	@Override
	public void empty(final GroupOrg group, final Map<String, UserOrg> users) {
		cacheRepository.empty(group, users);
	}

	@Override
	public GroupOrg create(final String dn, final String cn) {
		return cacheRepository.create(super.create(dn, cn));
	}

	/**
	 * Add an "uniqueMember" to given group. Cache is not updated there.
	 *
	 * @param element The new member to add.
	 * @param group   CN of the group to update. Must be normalized.
	 * @return the target {@link GroupOrg}.
	 */
	private GroupOrg addMember(final ResourceOrg element, final String group) {
		final var groupLdap = findById(group);
		if (!groupLdap.getMembers().contains(element.getId())) {
			// Not useless operation
			addAttributes(groupLdap.getDn(), memberAttribute, Collections.singletonList(element.getDn()));
		}
		return groupLdap;
	}

	/**
	 * Update the uniqueMember attribute of the user having changed DN. Cache is not updated since.
	 *
	 * @param oldUniqueMemberDn Old DN of the member to update.
	 * @param newUniqueMemberDn New DN of the member to update. UID of the DN should unchanged.
	 * @param group             CN of the group to update.
	 */
	public void updateMemberDn(final String group, final String oldUniqueMemberDn, final String newUniqueMemberDn) {
		final var groupLdap = findById(group);
		final var mods = new ModificationItem[2];
		mods[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
				new BasicAttribute(memberAttribute, oldUniqueMemberDn));
		mods[1] = new ModificationItem(DirContext.ADD_ATTRIBUTE, new BasicAttribute(memberAttribute, newUniqueMemberDn));
		template.modifyAttributes(org.springframework.ldap.support.LdapUtils.newLdapName(groupLdap.getDn()), mods);
	}

	@Override
	public void addUser(final UserOrg user, final String group) {
		// Add to Java cache and to SQL cache
		cacheRepository.addUserToGroup(user, addMember(user, group));
	}

	@Override
	public void addGroup(final GroupOrg subGroup, final String toGroup) {
		// Add to Java cache and to SQL cache
		cacheRepository.addGroupToGroup(subGroup, addMember(subGroup, toGroup));
	}

	@Override
	public void removeUser(final UserOrg user, final String group) {
		// Remove from Java cache and from SQL cache
		cacheRepository.removeUserFromGroup(user, removeMember(user, group));
	}

	/**
	 * Remove a group from another group. Cache is updated. There is no deletion.
	 *
	 * @param subGroup {@link GroupOrg} to remove.
	 * @param group    CN of the group to update.
	 */
	public void removeGroup(final GroupOrg subGroup, final String group) {
		// Remove from Java cache and from SQL cache
		cacheRepository.removeGroupFromGroup(subGroup, removeMember(subGroup, group));
	}

	/**
	 * Remove an "uniqueMember" from given group. Cache is not updated there.
	 *
	 * @param uniqueMember DN of the member to remove.
	 * @param group        CN of the group to update. Must be normalized.
	 * @return the {@link GroupOrg} where the member has just been removed from.
	 */
	private GroupOrg removeMember(final ResourceOrg uniqueMember, final String group) {
		final var groupLdap = findById(group);
		if (groupLdap.getMembers().contains(uniqueMember.getId())
				|| groupLdap.getSubGroups().contains(uniqueMember.getId())) {
			// Not useless LDAP operation, avoid LDAP duplicate deletion
			final var mods = new ModificationItem[1];
			mods[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
					new BasicAttribute(memberAttribute, uniqueMember.getDn()));
			try {
				template.modifyAttributes(org.springframework.ldap.support.LdapUtils.newLdapName(groupLdap.getDn()),
						mods);
			} catch (final org.springframework.ldap.AttributeInUseException aiue) {
				// Even if the membership update failed, the user does not exist anymore. A broken reference can remain
				// in LDAP, but this case is well managed.
				log.info("Unable to remove user {} from the group {} : {}", uniqueMember.getDn(), group, aiue);
			} catch (final org.springframework.ldap.SchemaViolationException sve) { // NOSONAR - Exception is logged
				// Occurs when there is a LDAP schema violation such as last member removed
				log.warn("Unable to remove user {} from the group {}", uniqueMember.getDn(), group, sve);
				throw new ValidationJsonException("groups", "last-member-of-group", "user", uniqueMember.getId(),
						"group", group);
			}
		}
		return groupLdap;
	}

	/**
	 * Map {@link GroupOrg} to LDAP
	 */
	@Override
	protected void mapToContext(final GroupOrg entry, final DirContextOperations context) {
		context.setAttributeValue("cn", entry.getName());
		// Dummy member for initial group, due to LDAP compliance of class "groupOfUniqueNames"
		context.setAttributeValue(memberAttribute, DEFAULT_MEMBER_DN);
	}

	@Override
	protected GroupOrg newContainer(final String dn, final String cn) {
		return new GroupOrg(dn.toLowerCase(Locale.ENGLISH), cn, new HashSet<>());
	}

	@Override
	public void addAttributes(final String dn, final String attribute, final Collection<String> values) {
		if (values.isEmpty()) {
			// Ignore this call
			return;
		}

		// Build the modification operation
		final var mods = values.stream()
				.map(v -> new ModificationItem(DirContext.ADD_ATTRIBUTE, new BasicAttribute(attribute, v)))
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
	public GroupOrg findByDepartment(final String department) {
		final var filter = new AndFilter().and(new EqualsFilter(OBJECT_CLASS, className))
				.and(new EqualsFilter(DEPARTMENT_ATTRIBUTE, department));
		return template.search(baseDn, filter.encode(), (Object ctx) -> (DirContextAdapter) ctx).stream()
				.findFirst().map(c -> c.getStringAttribute("cn")).map(Normalizer::normalize).map(this::findById)
				.orElse(null);
	}
}

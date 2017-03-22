package org.ligoj.app.ldap.resource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.transaction.Transactional;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.cxf.jaxrs.impl.UriInfoImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.ligoj.app.api.CompanyOrg;
import org.ligoj.app.api.ContainerOrg;
import org.ligoj.app.api.GroupOrg;
import org.ligoj.app.api.Normalizer;
import org.ligoj.app.api.SimpleUser;
import org.ligoj.app.api.UserOrg;
import org.ligoj.app.iam.ICompanyRepository;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.dao.DelegateOrgRepository;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.iam.model.DelegateType;
import org.ligoj.app.ldap.LdapUtils;
import org.ligoj.app.ldap.dao.GroupLdapRepository;
import org.ligoj.app.ldap.dao.UserLdapRepository;
import org.ligoj.app.plugin.id.resource.PasswordResource;
import org.ligoj.bootstrap.core.json.PaginationJson;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.security.SecurityHelper;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * LDAP User resource.
 */
@Path("/ldap/user")
@Service
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Transactional
public class UserLdapResource {

	/**
	 * The primary business key
	 */
	public static final String USER_KEY = "id";

	/**
	 * IAM provider.
	 */
	@Autowired
	@Setter
	protected IamProvider iamProvider;

	@Autowired
	private DelegateOrgRepository delegateRepository;

	@Autowired
	private PaginationJson paginationJson;

	@Autowired
	protected PasswordResource passwordRessource;

	@Autowired
	private CompanyLdapResource organizationResource;

	@Autowired
	private GroupLdapResource groupResource;

	@Autowired
	private SecurityHelper securityHelper;

	/**
	 * Ordered columns.
	 */
	private static final Map<String, String> ORDERED_COLUMNS = new HashMap<>();

	static {
		ORDERED_COLUMNS.put(USER_KEY, USER_KEY);
		ORDERED_COLUMNS.put("firstName", "firstName");
		ORDERED_COLUMNS.put("lastName", "lastName");
		ORDERED_COLUMNS.put("mails", "mail");
		ORDERED_COLUMNS.put(SimpleUser.COMPANY_ALIAS, SimpleUser.COMPANY_ALIAS);
	}

	/**
	 * Return users matching the given criteria. The managed groups, trees and companies are checked. The returned
	 * groups
	 * of each user depends on the groups the user can see. The result is not secured : it contains DN.
	 * 
	 * @param company
	 *            the optional company name to match.
	 * @param group
	 *            the optional group name to match.
	 * @return found users.
	 */
	public List<UserOrg> findAllNotSecure(final String company, final String group) {
		final Set<GroupOrg> managedGroups = groupResource.getContainers();

		// Search the users
		final MessageImpl message = new MessageImpl();
		message.put(Message.QUERY_STRING, DataTableAttributes.PAGE_LENGTH + "=10000000");
		return findAllNotSecure(managedGroups, company, group, null, new UriInfoImpl(message)).getContent();
	}

	/**
	 * Return users matching the given criteria. The managed groups, trees and companies are checked. The returned
	 * groups
	 * of each user depends on the groups the user can see and are in normalized CN form. The result is not secured, it
	 * contains DN.
	 * 
	 * @param managedGroups
	 *            the visible groups.
	 * @param company
	 *            the optional company name to match. Will be normalized.
	 * @param group
	 *            the optional group name to match.
	 * @param criteria
	 *            the optional criteria to match.
	 * @param uriInfo
	 *            filter data.
	 * @return found users.
	 */
	private Page<UserOrg> findAllNotSecure(final Set<GroupOrg> managedGroups, final String company, final String group, final String criteria,
			@Context final UriInfo uriInfo) {
		final PageRequest pageRequest = paginationJson.getPageRequest(uriInfo, ORDERED_COLUMNS);

		final Collection<String> managedCompanies = organizationResource.getContainers().stream().map(CompanyOrg::getId).collect(Collectors.toSet());
		final Map<String, GroupOrg> allGroups = getGroup().findAll();

		// The companies to use
		final Set<String> filteredCompanies = computeFilteredCompanies(Normalizer.normalize(company), managedCompanies);

		// The groups to use
		final Collection<GroupOrg> filteredGroups = computeFilteredGroups(group, managedGroups, allGroups);

		// Search the users
		return getRepository().findAll(filteredGroups, filteredCompanies, StringUtils.trimToNull(criteria), pageRequest);
	}

	/**
	 * Return users matching the given criteria. The managed groups, trees and companies are checked. The returned
	 * groups
	 * of each user depends on the groups the user can see/write, and are in CN form.
	 * 
	 * @param company
	 *            the optional company name to match.
	 * @param group
	 *            the optional group name to match.
	 * @param criteria
	 *            the optional criteria to match.
	 * @param uriInfo
	 *            filter data.
	 * @return found users.
	 */
	@GET
	public TableItem<UserOrgVo> findAll(@QueryParam(SimpleUser.COMPANY_ALIAS) final String company, @QueryParam("group") final String group,
			@QueryParam(DataTableAttributes.SEARCH) final String criteria, @Context final UriInfo uriInfo) {
		final Set<GroupOrg> managedGroups = groupResource.getContainers();
		final Set<GroupOrg> managedGroupsWrite = groupResource.getContainersForWrite();
		final Collection<String> managedCompaniesWrite = organizationResource.getContainersForWrite().stream().map(CompanyOrg::getId)
				.collect(Collectors.toList());

		// Search the users
		final Page<UserOrg> findAll = findAllNotSecure(managedGroups, company, group, criteria, uriInfo);

		// Apply pagination and secure the users data
		return paginationJson.applyPagination(uriInfo, findAll, rawUserLdap -> {

			final UserOrgVo securedUserLdap = new UserOrgVo();
			rawUserLdap.copy(securedUserLdap);
			securedUserLdap.setManaged(managedCompaniesWrite.contains(rawUserLdap.getCompany()) || !managedGroupsWrite.isEmpty());

			// Show only the groups that are also visible to current user
			securedUserLdap.setGroups(managedGroups.stream().filter(mGroup -> rawUserLdap.getGroups().contains(mGroup.getId())).map(mGroup -> {
				final GroupLdapVo vo = new GroupLdapVo();
				vo.setManaged(managedGroupsWrite.contains(mGroup));
				vo.setName(mGroup.getName());
				return vo;
			}).collect(Collectors.toList()));
			return securedUserLdap;
		});
	}

	/**
	 * Return a intersection of given set of visible companies and the optional requested company.
	 */
	private Set<String> computeFilteredCompanies(final String requestedCompany, final Collection<String> managedCompanies) {
		// Restrict access to visible companies
		final Set<String> filteredCompanies;
		if (StringUtils.isBlank(requestedCompany)) {
			// No requested company, use all of them
			filteredCompanies = new HashSet<>(managedCompanies);
		} else if (managedCompanies.contains(requestedCompany)) {
			// Requested company is visible, return it
			filteredCompanies = Collections.singleton(requestedCompany);
		} else {
			// Requested company does not exist, result would be an empty list
			filteredCompanies = Collections.emptySet();
		}

		return filteredCompanies;
	}

	/**
	 * Computed visible groups
	 */
	private Collection<GroupOrg> computeFilteredGroups(final String group, final Set<GroupOrg> managedGroups,
			final Map<String, GroupOrg> allGroups) {
		final Collection<GroupOrg> filteredGroups;
		// Restrict access to delegated groups
		if (StringUtils.isBlank(group)) {
			filteredGroups = null;
		} else {
			final GroupOrg filteredGroup = allGroups.get(Normalizer.normalize(group));
			// Filter the group, including the children
			filteredGroups = allGroups.values().stream().filter(groupEntry -> managedGroups.contains(groupEntry) && filteredGroup != null
					&& LdapUtils.equalsOrParentOf(filteredGroup.getDn(), groupEntry.getDn())).collect(Collectors.toList());
		}
		return filteredGroups;
	}

	/**
	 * Return a specific user from his/her login. When user does not exist or is within a non managed company, return a
	 * 404.
	 *
	 * @param user
	 *            The user to find. A normalized form will be used for the search.
	 * @return found user. Never <code>null</code>.
	 */
	@GET
	@Path("{user:" + SimpleUser.USER_PATTERN + "}")
	public UserOrg findById(@PathParam("user") final String user) {
		final UserOrg rawUserLdap = getRepository().findByIdExpected(Normalizer.normalize(user));
		if (organizationResource.findById(rawUserLdap.getCompany()) == null) {
			// No available delegation -> no result
			throw new ValidationJsonException(USER_KEY, BusinessException.KEY_UNKNOW_ID, "0", "user", "1", user);
		}

		// User has been found, secure the object regarding the visible groups
		final UserOrg securedUserLdap = new UserOrg();
		rawUserLdap.copy(securedUserLdap);

		// Show only the groups of user that are also visible to current user
		final Set<GroupOrg> managedGroups = groupResource.getContainers();
		securedUserLdap.setGroups(managedGroups.stream().filter(mGroup -> rawUserLdap.getGroups().contains(mGroup.getId())).sorted()
				.map(GroupOrg::getName).collect(Collectors.toList()));
		return securedUserLdap;
	}

	/**
	 * Add given user to the a group.
	 * 
	 * @param user
	 *            The user to add.
	 * @param group
	 *            The group to update.
	 */
	@PUT
	@Path("{user}/group/{group}")
	public void addUserToGroup(@PathParam("user") final String user, @PathParam("group") final String group) {
		updateGroupUser(user, Normalizer.normalize(group), Collection::add);
	}

	/**
	 * Remove given user from the a group.
	 * 
	 * @param user
	 *            The user to remove.
	 * @param group
	 *            The group to update.
	 */
	@DELETE
	@Path("{user}/group/{group}")
	public void removeUser(@PathParam("user") final String user, @PathParam("group") final String group) {
		updateGroupUser(user, Normalizer.normalize(group), Collection::remove);
	}

	/**
	 * Performs an operation on a group and a user.
	 * 
	 * @param user
	 *            The user to move.
	 * @param group
	 *            The group to update.
	 * @param updater
	 *            The function to execute on computed groups of current user.
	 */
	private void updateGroupUser(final String user, final String group, final BiFunction<Collection<String>, String, Boolean> updater) {

		// Get all delegates of current user
		final List<DelegateOrg> delegates = delegateRepository.findAllByUser(securityHelper.getLogin());

		// Get the implied user
		final UserOrg userLdap = getRepository().findByIdExpected(user);

		// Check the implied group
		final Map<String, GroupOrg> allGroups = getGroup().findAll();
		validateAndGroupsCN(Collections.singletonList(group), delegates, allGroups);

		// Compute the new groups
		final Set<String> newGroups = new HashSet<>(userLdap.getGroups());
		if (updater.apply(newGroups, group)) {

			// Replace the user groups by the normalized groups including the one we have just update
			final Collection<String> mergedGroups = mergeGroups(delegates, userLdap, allGroups, newGroups);

			// Update membership
			getRepository().updateMembership(new ArrayList<>(mergedGroups), userLdap);
		}
	}

	/**
	 * Update the given user.
	 * 
	 * @param user
	 *            The user definition, and associated groups. Group changes are checked.User definition changes are
	 *            checked.
	 */
	@PUT
	public void update(final UserLdapEdition user) {
		// Check the right on the company and the groups
		validateChanges(securityHelper.getLogin(), user);

		// Check the user exists
		getRepository().findByIdExpected(user.getId());

		saveOrUpdate(user);
	}

	/**
	 * Create the given user.
	 *
	 * @param user
	 *            The user definition, and associated groups. Initial groups are checked.User definition is checked.
	 */
	@POST
	public void create(final UserLdapEdition user) {
		// Check the right on the company and the groups
		validateChanges(securityHelper.getLogin(), user);

		// Check the user does not exists
		if (getRepository().findById(user.getId()) != null) {
			throw new ValidationJsonException(USER_KEY, "already-exist", "0", USER_KEY, "1", user.getId());
		}

		saveOrUpdate(user);
	}

	/**
	 * Validate the user changes regarding the current user's right, replace group names with the exact CN, and replace
	 * the company with a normalized one.<br>
	 * Rules, order is important :
	 * <ul>
	 * <li>At least one valid delegate must exist (valid or not against the involved user). If not, act as if the
	 * company does not exist.</li>
	 * <li>Involved company must be managed by the current user, if one attribute is changed act as if it does not
	 * exist.</li>
	 * <li>Involved company must exist</li>
	 * <li>Involved groups must be managed by the current user, if not, act as if it does not exist So the user can only
	 * involve groups he/she manages. These groups are completed with the other groups the user may already have.</li>
	 * <li>Involved groups must exist</li>
	 * <li>Company of user cannot be changed</li>
	 * </ul>
	 */
	private void validateChanges(final String principal, final UserLdapEdition importEntry) {
		// First cleanup the entry
		normalize(importEntry);

		// Get all delegates of current user
		final List<DelegateOrg> delegates = delegateRepository.findAllByUser(principal);

		// Get the stored data of the implied user
		final UserOrg userLdap = getRepository().findById(importEntry.getId());

		// Check the implied company and request changes
		final String cleanCompany = Normalizer.normalize(importEntry.getCompany());
		final String companyDn = ContainerOrg.getSafeDn(getCompany().findById(cleanCompany));
		final boolean hasAttributeChange = hasAttributeChange(importEntry, userLdap);
		if (!isGrantedAccess(delegates, companyDn, DelegateType.COMPANY, hasAttributeChange)) {
			// No right at all, unknown company, no (write|admin) right on this company, or no delegate on this company
			// Report this attempt
			log.warn("Attempt to create/update a user '{}' out of scope, company {}", importEntry.getId(), cleanCompany);
			throw new ValidationJsonException(SimpleUser.COMPANY_ALIAS, BusinessException.KEY_UNKNOW_ID, "0", SimpleUser.COMPANY_ALIAS, "1",
					importEntry.getCompany());
		}

		// Replace with the normalized company
		importEntry.setCompany(cleanCompany);

		// Check the groups : one group failed implies entry creation to fail
		final Map<String, GroupOrg> allGroups = getGroup().findAll();

		final List<String> groups = validateAndGroupsCN(userLdap, importEntry, delegates, allGroups);

		// Replace the user groups by the normalized groups including the ones the user does not see
		if (userLdap == null) {
			importEntry.setGroups(groups);
		} else {
			// Check the company change
			if (!userLdap.getCompany().equals(importEntry.getCompany())) {
				// Check the user can be removed from the old company
				checkDeletionRight(importEntry.getId(), "move");
			}

			// Compute merged group identifiers
			final Collection<String> mergedGroups = mergeGroups(delegates, userLdap, allGroups, groups);
			importEntry.setGroups(new ArrayList<>(mergedGroups));
		}
	}

	/**
	 * Validate assigned groups, and return corresponding group identifiers.
	 */
	private List<String> validateAndGroupsCN(final UserOrg userLdap, final UserLdapEdition importEntry, final List<DelegateOrg> delegates,
			final Map<String, GroupOrg> allGroups) {

		// First complete the groups with the implicit ones from department
		final String previous = Optional.ofNullable(userLdap).map(UserOrg::getDepartment).orElse(null);
		if (ObjectUtils.notEqual(previous, importEntry.getDepartment())) {
			Optional.ofNullable(toDepartmentGroup(previous)).map(GroupOrg::getId).ifPresent(importEntry.getGroups()::remove);
			Optional.ofNullable(toDepartmentGroup(importEntry.getDepartment())).map(GroupOrg::getId).ifPresent(importEntry.getGroups()::add);
		}
		return validateAndGroupsCN(importEntry.getGroups(), delegates, allGroups);
	}

	/**
	 * Validate assigned groups, and return corresponding group identifiers.
	 */
	private List<String> validateAndGroupsCN(final Collection<String> newGroups, final List<DelegateOrg> delegates,
			final Map<String, GroupOrg> allGroups) {
		final List<String> groupsCn = new ArrayList<>();
		for (final String group : CollectionUtils.emptyIfNull(newGroups)) {
			final GroupOrg groupLdap = allGroups.get(Normalizer.normalize(group));
			if (groupLdap == null || !isGrantedAccess(delegates, groupLdap.getDn(), DelegateType.GROUP, true)) {
				// No right on this group, or unknown LDAP group
				throw new ValidationJsonException("groups", BusinessException.KEY_UNKNOW_ID, "0", "groups", "1", group);
			}

			groupsCn.add(groupLdap.getId());
		}
		return groupsCn;
	}

	/**
	 * Merge user groups with this formula :
	 * <ul>
	 * <li>SG :Specified groups by current user, and to be set to the LDAP entry. These groups must have been previously
	 * checked regarding against the rights the current user has on these groups.</li>
	 * <li>CG : Current groups of internal LDAP entry</li>
	 * <li>VCG : Visible groups in CG</li>
	 * <li>WCG : Writable groups in VCG</li>
	 * <li>GG : Final groups of LDAP entry = CG-WCG+SG</li>
	 * </ul>
	 * 
	 * @param delegates
	 *            the available delegates of current user.
	 * @param userLdap
	 *            The internal user entry to update.
	 * @param allGroups
	 *            The available groups.
	 * @param groups
	 *            The visible and writable groups identifiers to be set to the LDAP entry.
	 * @return the merged group identifiers to be set internally.
	 */
	private Collection<String> mergeGroups(final List<DelegateOrg> delegates, final UserOrg userLdap, final Map<String, GroupOrg> allGroups,
			final Collection<String> groups) {
		// Compute the groups merged groups
		final Collection<String> newGroups = new HashSet<>(userLdap.getGroups());
		newGroups.addAll(groups);
		for (final String oldGroup : userLdap.getGroups()) {
			final String oldGroupDn = allGroups.get(oldGroup).getDn();
			if (!groups.contains(oldGroup) && isGrantedAccess(delegates, oldGroupDn, DelegateType.GROUP, true)) {
				// This group is visible, so it has been explicitly removed by the current user
				newGroups.remove(oldGroup);
			}
		}
		return newGroups;
	}

	/**
	 * Normalize the entry : capitalize and trimming.
	 */
	private void normalize(final UserLdapEdition importEntry) {
		// Normalize the identifiers
		importEntry.setCompany(Normalizer.normalize(importEntry.getCompany()));
		importEntry.setId(StringUtils.trimToNull(Normalizer.normalize(importEntry.getId())));
		importEntry.setGroups(new ArrayList<>(Normalizer.normalize(importEntry.getGroups())));

		// Fix the names of user
		importEntry.setDepartment(StringUtils.trimToNull(importEntry.getDepartment()));
		importEntry.setLocalId(StringUtils.trimToNull(importEntry.getLocalId()));
		importEntry.setLastName(WordUtils.capitalizeFully(StringUtils.trimToNull(importEntry.getLastName())));
		importEntry.setFirstName(WordUtils.capitalizeFully(StringUtils.trimToNull(importEntry.getFirstName())));
	}

	private boolean isGrantedAccess(final List<DelegateOrg> delegates, final String dn, final DelegateType type, final boolean requestUpdate) {
		return dn != null && (!requestUpdate || delegates.stream().anyMatch(delegate -> isGrantedAccess(delegate, dn, type, requestUpdate)));
	}

	protected boolean isGrantedAccess(final DelegateOrg delegate, final String dn, final DelegateType type, final boolean requestUpdate) {
		return (delegate.getType() == type || delegate.getType() == DelegateType.TREE)
				&& (!requestUpdate || delegate.isCanAdmin() || delegate.isCanWrite()) && LdapUtils.equalsOrParentOf(delegate.getDn(), dn);
	}

	/**
	 * Indicate the two user details have attribute differences
	 */
	@SuppressWarnings("unchecked")
	private boolean hasAttributeChange(final UserLdapEdition importEntry, final UserOrg userLdap) {
		return userLdap == null || hasAttributeChange(importEntry, userLdap, SimpleUser::getFirstName, SimpleUser::getLastName,
				SimpleUser::getCompany, SimpleUser::getLocalId, SimpleUser::getDepartment) || !userLdap.getMails().contains(importEntry.getMail());
	}

	/**
	 * Indicate the two user details have attribute differences
	 */
	private boolean hasAttributeChange(final SimpleUser user1, final SimpleUser user2,
			@SuppressWarnings("unchecked") final Function<SimpleUser, String>... equals) {
		return Arrays.stream(equals).anyMatch(f -> !StringUtils.equals(f.apply(user2), f.apply(user1)));
	}

	/**
	 * Create the LDAP user is not exist and update the related groups and company.<br>
	 * The mail of the entry will replace the one of LDAP if LDAP's one does not contain any mail. If LDAP entry did not
	 * exist or, if there was no password (or a dummy one), it will be set to the one of import of a new generated
	 * password. <br>
	 * When mail or password is update a mail is sent to the user with the account, and eventually the new password.<br>
	 * Groups of entry will be normalized.
	 * 
	 * @param importEntry
	 *            The entry to save or to update.
	 */
	public void saveOrUpdate(final UserLdapEdition importEntry) {

		// Create as needed the user, groups will be proceeded after.
		final UserLdapRepository repository = getRepository();
		UserOrg user = repository.findById(importEntry.getId());
		final UserOrg newUser = toUserLdap(importEntry);
		if (user == null) {
			// Create a new entry in LDAP
			log.info("{} will be created", newUser.getId());
			user = repository.create(newUser);

			// Set the password
			updatePassword(newUser);
		} else {
			updateUser(user, newUser);
		}

		// Update membership
		repository.updateMembership(importEntry.getGroups(), user);
	}

	/**
	 * Update the attributes the given user. Groups are not managed there.
	 */
	private void updateUser(final UserOrg oldUser, final UserOrg newUser) {
		// Update the LDAP
		log.info("{} already exists", newUser.getId());

		// First update the DN
		newUser.setDn(getRepository().buildDn(newUser).toString());
		updateCompanyAsNeeded(oldUser, newUser);

		// Then, update the no secured attributes : first name, etc.
		final boolean hadNoMail = oldUser.getMails().isEmpty();
		getRepository().updateUser(newUser);

		// Then update the mail and/or password
		if (newUser.getMails().isEmpty()) {
			// No mail, no notification
			log.info("{} already exists, but has no mail", newUser.getId());
		} else if (hadNoMail) {
			// Mail has been added, set a new password
			log.info("{} already exists, but a mail has been created", newUser.getId());
			updatePassword(newUser);
		} else if (oldUser.isNoPassword()) {
			// Override the password
			log.info("{} had no password, a mail will be sent", newUser.getId());
			updatePassword(newUser);
		}

	}

	/**
	 * Convert the import format to the internal format.
	 * 
	 * @param importEntry
	 *            The raw imported user.
	 * @return The internal format of the user.
	 */
	private UserOrg toUserLdap(final UserLdapEdition importEntry) {
		final UserOrg user = new UserOrg();
		importEntry.copy(user);
		user.setGroups(new ArrayList<>());
		final List<String> mails = new ArrayList<>();
		CollectionUtils.addIgnoreNull(mails, importEntry.getMail());
		user.setMails(mails);
		return user;
	}

	/**
	 * Delete an user.<br>
	 * Rules, order is important :
	 * <ul>
	 * <li>Only users managing the company of this user can perform the deletion, if not, act as if the user did not
	 * exist</li>
	 * <li>User must exist</li>
	 * </ul>
	 * Note : even if the user requesting this deletion has no right on the groups the involved user, this operation can
	 * be performed.
	 *
	 * @param user
	 *            The user to delete. A normalized form of this parameter will be used for this operation.
	 */
	@DELETE
	@Path("{user}")
	public void delete(@PathParam("user") final String user) {
		// Check the user can be deleted
		final UserOrg userLdap = checkDeletionRight(user, "delete");

		// Hard deletion
		// Check the group : You can't delete an user if he is the last member of a group
		final Map<String, GroupOrg> allGroups = getGroup().findAll();
		checkLastMemberInGroups(userLdap, allGroups);

		final UserLdapRepository repository = getRepository();
		// Revoke all memberships of this user
		repository.updateMembership(new ArrayList<>(), userLdap);

		repository.delete(userLdap);
	}

	/**
	 * Disable an user. The user's password is cleared (empty) and a flag is added to tag this user as locked to prevent
	 * further password reset. Other properties are untouched.<br>
	 * Rules, order is important :
	 * <ul>
	 * <li>Only users managing the company of this user can perform the lock, if not, act as if the user did not
	 * exist</li>
	 * <li>User must exist</li>
	 * </ul>
	 * Note : even if the user requesting this operation has no right on the groups of the involved user, this operation
	 * can
	 * be performed.
	 *
	 * @param user
	 *            The user to lock. A normalized form of this parameter will be used for this operation.
	 */
	@DELETE
	@Path("{user}/lock")
	public void lock(@PathParam("user") final String user) {
		getRepository().lock(securityHelper.getLogin(), checkDeletionRight(user, "lock"));
	}

	/**
	 * Isolate an user. The user is locked and also is moved to a different location from the user repository. This
	 * move ensure some tools to lost this user. Usually the target location is outside the scope/branch of users the
	 * other tools are watching.<br>
	 * All memberships are updated, the user's DN is changed, all groups must be updated.
	 * Rules, order is important :
	 * <ul>
	 * <li>Only users managing the company of this user can perform the disable, if not, act as if the user did not
	 * exist</li>
	 * <li>User must exist</li>
	 * </ul>
	 * Note : even if the user requesting this operation has no right on the groups the involved user, this operation
	 * can
	 * be performed.
	 *
	 * @param user
	 *            The user to move to isolate zone. A normalized form of this parameter will be used for this operation.
	 */
	@DELETE
	@Path("{user}/isolate")
	public void isolate(@PathParam("user") final String user) {
		getRepository().isolate(securityHelper.getLogin(), checkDeletionRight(user, "isolate"));
	}

	/**
	 * Unlock a user.<br>
	 * Rules, order is important :
	 * <ul>
	 * <li>Only users managing the company of this user can perform the enable, if not, act as if the user did not
	 * exist</li>
	 * <li>User must exist</li>
	 * </ul>
	 * Note : even if the user requesting this enable has no right on the groups the involved user, this operation can
	 * be performed.
	 *
	 * @param user
	 *            The user to unlock. A normalized form of this parameter will be used for this operation.
	 */
	@PUT
	@Path("{user}/unlock")
	public void unlock(@PathParam("user") final String user) {
		getRepository().unlock(checkDeletionRight(user, "unlock"));
	}

	/**
	 * Restore a user from the isolate zone to the old company.<br>
	 * Rules, order is important :
	 * <ul>
	 * <li>Only users managing the company of this user can perform the enable, if not, act as if the user did not
	 * exist</li>
	 * <li>User must exist</li>
	 * </ul>
	 * Note : even if the user requesting this enable has no right on the groups the involved user, this operation can
	 * be performed.
	 *
	 * @param user
	 *            The user to restore. A normalized form of this parameter will be used for this operation.
	 */
	@PUT
	@Path("{user}/restore")
	public void restore(@PathParam("user") final String user) {
		getRepository().restore(checkDeletionRight(user, "restore"));
	}

	/**
	 * Check the current user can delete, enable or disable the given user entry.
	 * 
	 * @param user
	 *            The user to alter.
	 * @param hard
	 *            When <code>true</code> the user is completely deleted, in other case, this a simple disable.
	 * @return The internal representation of found user.
	 */
	private UserOrg checkDeletionRight(final String user, final String mode) {
		// Check the user exists
		final UserOrg userLdap = getRepository().findByIdExpected(Normalizer.normalize(user));

		// Check the company
		final String companyDn = ContainerOrg.getSafeDn(getCompany().findById(Normalizer.normalize(userLdap.getCompany())));
		final List<Integer> ids = delegateRepository.findByMatchingDnForWrite(securityHelper.getLogin(), companyDn, DelegateType.COMPANY);
		if (ids.isEmpty()) {
			// Report this attempt to delete a non managed user
			log.warn("Attempt to {} a user '{}' out of scope", mode, user);
			throw new ValidationJsonException(USER_KEY, BusinessException.KEY_UNKNOW_ID, "0", "user", "1", user);
		}
		return userLdap;
	}

	/**
	 * Check the groups of given users would contain at least another user when it will be deleted.
	 * 
	 * @param userLdap
	 *            User o delete and to check the memberships.
	 * @param allGroups
	 *            Map of group by groupName
	 */
	private void checkLastMemberInGroups(final UserOrg userLdap, final Map<String, GroupOrg> allGroups) {
		for (final String group : userLdap.getGroups()) {
			if (allGroups.get(group).getMembers().size() == 1) {
				throw new ValidationJsonException(USER_KEY, "last-member-of-group", "user", userLdap.getId(), "group", group);
			}
		}
	}

	/**
	 * Get the password of given entry. If there is no password, a new one will be generated.
	 *
	 * @param user
	 *            then LDAP user.
	 */
	private String updatePassword(final UserOrg user) {
		// Have to generate a new password
		final String password = passwordRessource.generate(user.getId());

		// This user has a password now
		user.setNoPassword(false);
		return password;
	}

	/**
	 * Check the user credentials.
	 * 
	 * @param name
	 *            the user's name.
	 * @param password
	 *            the user's password.
	 * @return <code>true</code> when credentials are correct.
	 */
	public boolean authenticate(final String name, final String password) {
		return getRepository().authenticate(name, password);
	}

	/**
	 * Return the {@link UserOrg} list corresponding to the given attribute/value without using cache for the search,
	 * but using it for the instances.
	 * 
	 * @param attribute
	 *            the attribute name to match.
	 * @param value
	 *            the attribute value to match.
	 * @return the found users. May be empty.
	 */
	public List<UserOrg> findAllBy(final String attribute, final String value) {
		return getRepository().findAllBy(attribute, value);
	}

	/**
	 * Return the {@link UserOrg} corresponding to the given attribute/value without using cache.
	 * 
	 * @param user
	 *            The user to find. A normalized form will be used for the search.
	 * @return the found user or <code>null</code> when not found. Groups are not fetched for this operation.
	 */
	public UserOrg findByIdNoCache(final String user) {
		return getRepository().findByIdNoCache(Normalizer.normalize(user));
	}

	/**
	 * User repository provider.
	 * 
	 * @return User repository provider.
	 */
	private UserLdapRepository getRepository() {
		return (UserLdapRepository) iamProvider.getConfiguration().getUserRepository();
	}

	/**
	 * Company repository provider.
	 * 
	 * @return Company repository provider.
	 */
	private ICompanyRepository getCompany() {
		return iamProvider.getConfiguration().getCompanyRepository();
	}

	/**
	 * Group repository provider.
	 * 
	 * @return Group repository provider.
	 */
	private GroupLdapRepository getGroup() {
		return (GroupLdapRepository) iamProvider.getConfiguration().getGroupRepository();
	}

	/**
	 * Update internal user with the new user. Note the security is not checked there.
	 * 
	 * @param userLdap
	 *            The internal user to update. Note this must be the internal instance
	 * @param newUser
	 *            The new user data. Note this will not be the stored instance.
	 */
	private void updateCompanyAsNeeded(final UserOrg userLdap, final UserOrg newUser) {
		// Check the company
		if (ObjectUtils.notEqual(userLdap.getCompany(), newUser.getCompany())) {
			// Move the user
			getRepository().move(userLdap, getCompany().findById(newUser.getCompany()));
		}
	}

	/**
	 * Return the group corresponding to the given department.
	 * 
	 * @param department
	 *            The department to match.
	 * @return The group corresponding to the given department or <code>null</code>.
	 */
	private GroupOrg toDepartmentGroup(final String department) {
		return Optional.ofNullable(department).map(getGroup()::findByDepartment).orElse(null);
	}

	/**
	 * Update internal user with the new user for following attributes : department and local identifier. Note the
	 * security is not checked there.
	 * 
	 * @param userLdap
	 *            The user to update. Note this must be the internal instance.
	 * @param newUser
	 *            The new user data. Note this will not be the stored instance.
	 */
	public void mergeUser(final UserOrg userLdap, final UserOrg newUser) {
		boolean needUpdate = false;

		// Merge department
		if (ObjectUtils.notEqual(userLdap.getDepartment(), newUser.getDepartment())) {
			// Remove membership from the old department if exist
			Optional.ofNullable(toDepartmentGroup(userLdap.getDepartment())).ifPresent(g -> getGroup().removeUser(userLdap, g.getId()));

			// Add membership to the new department if exist
			Optional.ofNullable(toDepartmentGroup(newUser.getDepartment())).ifPresent(g -> getGroup().addUser(userLdap, g.getId()));

			userLdap.setDepartment(newUser.getDepartment());
			needUpdate = true;
		}

		// Merge local identifier
		if (ObjectUtils.notEqual(userLdap.getLocalId(), newUser.getLocalId())) {
			userLdap.setLocalId(newUser.getLocalId());
		}

		// Updated as needed
		if (needUpdate) {
			getRepository().updateUser(userLdap);
		}
	}
}

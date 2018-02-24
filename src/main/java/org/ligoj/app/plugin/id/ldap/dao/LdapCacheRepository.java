package org.ligoj.app.plugin.id.ldap.dao;

import java.util.EnumMap;
import java.util.Map;

import javax.cache.annotation.CacheResult;

import org.ligoj.app.api.Normalizer;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.ResourceOrg;
import org.ligoj.app.iam.UserOrg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * LDAP in memory cache with JPA back-end cache.
 */
@Component
@Slf4j
public class LdapCacheRepository {

	/**
	 * LDAP data type.
	 */
	public enum LdapData {
		GROUP, COMPANY, USER
	}

	@Autowired
	protected LdapCacheDao ldapCacheDao;

	/**
	 * IAM provider.
	 */
	@Autowired
	protected IamProvider[] iamProvider;

	/**
	 * Reset the database cache with the LDAP data. Note there is no synchronization for this method. Initial first
	 * concurrent calls may note involve the cache.
	 * 
	 * @return The cached LDAP data..
	 */
	@CacheResult(cacheName = "ldap")
	public Map<LdapData, Map<String, ? extends ResourceOrg>> getLdapData() {
		final Map<LdapData, Map<String, ? extends ResourceOrg>> result = new EnumMap<>(LdapData.class);

		// Fetch LDAP data
		log.info("Fetching LDAP data ...");
		final Map<String, CompanyOrg> companies = getCompany().findAllNoCache();
		final Map<String, GroupOrg> groups = getGroup().findAllNoCache();
		final Map<String, UserOrg> users = getUser().findAllNoCache(groups);
		result.put(LdapData.COMPANY, companies);
		result.put(LdapData.GROUP, groups);
		result.put(LdapData.USER, users);
		ldapCacheDao.reset(companies, groups, users);
		return result;
	}

	/**
	 * Add given group to the cache.
	 * 
	 * @param group
	 *            the new group.
	 */
	protected void create(final GroupOrg group) {
		ldapCacheDao.create(group);
		getGroup().findAll().put(group.getId(), group);
	}

	/**
	 * Add given company to the cache.
	 * 
	 * @param company
	 *            the new group.
	 */
	protected void create(final CompanyOrg company) {
		ldapCacheDao.create(company);
		getCompany().findAll().put(company.getId(), company);
	}

	/**
	 * Add given user to the cache. Membership is not considered.
	 * 
	 * @param user
	 *            the new user.
	 */
	protected void create(final UserOrg user) {
		ldapCacheDao.create(user);
		getUser().findAll().put(user.getId(), user);
	}

	/**
	 * Remove given group from the cache.
	 * 
	 * @param group
	 *            the group to remove.
	 */
	protected void delete(final GroupOrg group) {
		final Map<String, GroupOrg> groupsNameToDn = getGroup().findAll();

		// Remove the group from the users
		deleteMemoryAssociations(group, getUser().findAll());

		// Remove from JPA cache
		ldapCacheDao.delete(group);

		// Remove the group
		groupsNameToDn.remove(group.getId());
	}

	/**
	 * Remove all users from the given group and empty the group.
	 * 
	 * @param group
	 *            The group to empty.
	 * @param users
	 *            All known users could be removed from this group.
	 */
	protected void empty(final GroupOrg group, final Map<String, UserOrg> users) {
		// Remove the group from the users
		deleteMemoryAssociations(group, users);

		// Remove memberships from JPA cache
		ldapCacheDao.empty(group);
	}

	/**
	 * Remove all users from the given group and empty the group.
	 * 
	 * @param group
	 *            The group to empty.
	 * @param users
	 *            All known users.
	 */
	private void deleteMemoryAssociations(final GroupOrg group, final Map<String, UserOrg> users) {
		// Remove from in-memory cache all users
		for (final String member : group.getMembers()) {
			users.get(member).getGroups().remove(group.getId());
		}

		// Clear the members list
		group.getMembers().clear();
	}

	/**
	 * Remove given company from the cache. Warning, it is assumed there is no more user associated to the deleted
	 * company.
	 * 
	 * @param company
	 *            The company to remove.
	 */
	public void delete(final CompanyOrg company) {
		final Map<String, GroupOrg> companiesNameToDn = getGroup().findAll();

		// Remove from JPA cache
		ldapCacheDao.delete(company);

		// Remove from in-memory cache
		companiesNameToDn.remove(company.getId());
	}

	/**
	 * Remove given group from the cache. User should have been removed from each group before that, this function does
	 * not update in memory membership.
	 * 
	 * @param user
	 *            the user to remove.
	 */
	protected void delete(final UserOrg user) {
		final Map<String, UserOrg> users = getUser().findAll();

		// Remove from JPA cache
		ldapCacheDao.delete(user);

		// Remove it-self from in-memory cache
		users.remove(Normalizer.normalize(user.getId()));
	}

	/**
	 * Remove the user from the given group. Cache is also updated but only in group members.
	 * 
	 * @param user
	 *            The user to remove from the given group.
	 * @param group
	 *            The group to update.
	 */
	protected void removeUserFromGroup(final UserOrg user, final GroupOrg group) {
		// Remove from JPA cache
		ldapCacheDao.removeUserFromGroup(user, group);

		// Also update the membership cache
		user.getGroups().remove(group.getId());
		group.getMembers().remove(user.getId());
	}

	/**
	 * Remove the group from the another group. Cache is also updated but only in group members.
	 * 
	 * @param subGroup
	 *            The group to remove from the other group.
	 * @param group
	 *            The group to update.
	 */
	protected void removeGroupFromGroup(final GroupOrg subGroup, final GroupOrg group) {
		// Remove from JPA cache
		ldapCacheDao.removeGroupFromGroup(subGroup, group);

		// Also update the membership cache
		group.getSubGroups().remove(subGroup.getId());
		subGroup.getGroups().remove(group.getId());
	}

	/**
	 * Add the group to the given group.Cache is also updated.
	 * 
	 * @param subGroup
	 *            The group to add to the other group.
	 * @param group
	 *            The group to update.
	 */
	protected void addGroupToGroup(final GroupOrg subGroup, final GroupOrg group) {

		// Add to JPA cache
		ldapCacheDao.addGroupToGroup(subGroup, group);

		// Also update the membership cache
		group.getSubGroups().add(subGroup.getId());
		subGroup.getGroups().add(group.getId());
	}

	/**
	 * Add the user to the given group.Cache is also updated.
	 * 
	 * @param user
	 *            The user to add to the other group.
	 * @param group
	 *            The group to update.
	 */
	protected void addUserToGroup(final UserOrg user, final GroupOrg group) {

		// Add to JPA cache
		ldapCacheDao.addUserToGroup(user, group);

		// Also update the membership cache
		group.getMembers().add(user.getId());
		user.getGroups().add(group.getId());
	}

	/**
	 * Update the attributes.
	 * 
	 * @param user
	 *            The user to update.
	 */
	protected void update(final UserOrg user) {
		ldapCacheDao.update(user);
	}

	/**
	 * User repository provider.
	 * 
	 * @return User repository provider.
	 */
	private UserLdapRepository getUser() {
		return (UserLdapRepository) iamProvider[0].getConfiguration().getUserRepository();
	}

	/**
	 * Company repository provider.
	 * 
	 * @return Company repository provider.
	 */
	private CompanyLdapRepository getCompany() {
		return (CompanyLdapRepository) iamProvider[0].getConfiguration().getCompanyRepository();
	}

	/**
	 * Group repository provider.
	 * 
	 * @return Group repository provider.
	 */
	private GroupLdapRepository getGroup() {
		return (GroupLdapRepository) iamProvider[0].getConfiguration().getGroupRepository();
	}
}

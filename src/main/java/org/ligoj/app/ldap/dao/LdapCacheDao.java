package org.ligoj.app.ldap.dao;

import java.util.Map;
import java.util.Optional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;
import javax.transaction.Transactional;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.ligoj.app.api.CompanyLdap;
import org.ligoj.app.api.ContainerLdap;
import org.ligoj.app.api.GroupLdap;
import org.ligoj.app.api.UserLdap;
import org.ligoj.app.model.CacheCompany;
import org.ligoj.app.model.CacheContainer;
import org.ligoj.app.model.CacheGroup;
import org.ligoj.app.model.CacheMembership;
import org.ligoj.app.model.CacheUser;
import org.ligoj.bootstrap.core.DescribedBean;
import org.springframework.stereotype.Repository;

import lombok.extern.slf4j.Slf4j;

/**
 * Cache synchronization from LDAP to database.
 */
@Transactional
@Repository
@Slf4j
public class LdapCacheDao {

	@PersistenceContext(type = PersistenceContextType.TRANSACTION, unitName = "pu")
	private EntityManager em;

	/**
	 * Reset the database cache with the LDAP data.
	 * 
	 * @param users
	 *            All users.
	 * @param groups
	 *            All groups.
	 * @param companies
	 *            All companies.
	 */
	public void reset(final Map<String, CompanyLdap> companies, final Map<String, GroupLdap> groups, final Map<String, UserLdap> users) {
		final long start = System.currentTimeMillis();

		// Remove all CACHE_* entries
		log.info("Clearing database ...");
		clear();

		// Insert data into database
		log.info("Inserting data ...");
		persistCompanies(companies);
		em.flush();
		em.clear();
		persistGroups(groups);
		em.flush();
		em.clear();
		final int memberships = persistMemberships(users, groups);
		em.flush();
		em.clear();
		log.info("Synchronization finished in {} : {} groups, {} companies, {} users, {} memberships",
				DurationFormatUtils.formatDurationHMS(System.currentTimeMillis() - start), groups.size(), companies.size(), users.size(),
				memberships);
	}

	/**
	 * Persist association between users and groups.
	 * 
	 * @param users
	 *            The LDAP users.
	 * @param allGroups
	 *            The groups already persisted in database.
	 * @return the amount of persisted relations.
	 */
	private int persistMemberships(final Map<String, UserLdap> users, final Map<String, GroupLdap> allGroups) {
		int memberships = 0;
		for (final UserLdap user : users.values()) {

			// Persist users
			final CacheUser entity = createInternal(user);
			for (final String group : user.getGroups()) {
				addUserToGroupInternal(entity, allGroups.get(group));
				memberships++;
			}
		}
		return memberships;
	}

	/**
	 * Persist groups and return saved entities
	 */
	private void persistGroups(final Map<String, GroupLdap> groups) {
		groups.values().forEach(this::create);
	}

	/**
	 * Persist companies and return saved entities
	 */
	private void persistCompanies(final Map<String, CompanyLdap> companies) {
		companies.values().forEach(this::create);
	}

	/**
	 * Remove all data from database.
	 */
	public void clear() {
		em.createQuery("DELETE FROM CacheMembership").executeUpdate();
		em.createQuery("DELETE FROM CacheUser").executeUpdate();
		em.createQuery("DELETE FROM CacheGroup").executeUpdate();
		em.createQuery("DELETE FROM CacheCompany").executeUpdate();
		em.flush();
		em.clear();
	}

	/**
	 * Persist a new group
	 */
	private void createInternal(final GroupLdap group) {
		em.persist(toCacheGroup(group));
	}

	/**
	 * Persist a new company
	 */
	private void createInternal(final CompanyLdap company) {
		em.persist(toCacheCompany(company));
	}

	/**
	 * Transform group to JPA.
	 */
	private CacheGroup toCacheGroup(final GroupLdap group) {
		return fillCacheContainer(group, new CacheGroup());
	}

	/**
	 * Transform company to JPA.
	 */
	private CacheCompany toCacheCompany(final CompanyLdap company) {
		return fillCacheContainer(company, new CacheCompany());
	}

	/**
	 * Copy data from the LDAD object to the cache entity.
	 */
	private <T extends CacheContainer> T fillCacheContainer(final ContainerLdap container, final T entity) {
		DescribedBean.copy(container, entity);
		return entity;
	}

	/**
	 * Persist a new group and flush.
	 * 
	 * @param group
	 *            the group to persist.
	 */
	public void create(final GroupLdap group) {
		createInternal(group);
		em.flush();
		em.clear();
	}

	/**
	 * Persist a new company and flush.
	 * 
	 * @param company
	 *            the company to persist.
	 */
	public void create(final CompanyLdap company) {
		createInternal(company);
		em.flush();
		em.clear();
	}

	/**
	 * Persist a new user
	 */
	private CacheUser createInternal(final UserLdap user) {
		final CacheUser entity = toCacheUser(user);
		em.persist(entity);
		return entity;
	}

	/**
	 * Transform user to JPA.
	 */
	private CacheUser toCacheUser(final UserLdap user) {
		final CacheUser entity = new CacheUser();
		entity.setId(user.getId());
		entity.setFirstName(user.getFirstName());
		entity.setLastName(user.getLastName());
		if (CollectionUtils.isNotEmpty(user.getMails())) {
			entity.setMails(user.getMails().get(0));
		}

		// Set the company if defined
		entity.setCompany(Optional.ofNullable(user.getCompany()).map(c -> {
			final CacheCompany company = new CacheCompany();
			company.setId(user.getCompany());
			return company;
		}).orElse(null));
		return entity;
	}

	/**
	 * Persist a new user and flush
	 * 
	 * @param user
	 *            the user to persist.
	 */
	public void create(final UserLdap user) {
		createInternal(user);
		em.flush();
		em.clear();
	}

	/**
	 * Update given user.
	 * 
	 * @param user
	 *            user to update.
	 */
	public void update(final UserLdap user) {
		final CacheUser entity = toCacheUser(user);
		em.merge(entity);
		em.flush();
		em.clear();
	}

	/**
	 * Delete a group.
	 * 
	 * @param group
	 *            the group to delete.
	 */
	public void delete(final GroupLdap group) {
		em.createQuery("DELETE FROM CacheMembership WHERE group.id=:id OR subGroup.id=:id").setParameter("id", group.getId()).executeUpdate();
		em.createQuery("DELETE FROM CacheGroup WHERE id=:id").setParameter("id", group.getId()).executeUpdate();
		em.flush();
		em.clear();
	}

	/**
	 * Remove all user membership to this group. Sub groups are not removed.
	 * 
	 * @param group
	 *            the group to empty.
	 */
	public void empty(final GroupLdap group) {
		em.createQuery("DELETE FROM CacheMembership WHERE group.id=:id").setParameter("id", group.getId()).executeUpdate();
		em.flush();
		em.clear();
	}

	/**
	 * Delete a company. Warning, it is assumed there is no more user associated to the deleted company.
	 * 
	 * @param company
	 *            the company to delete.
	 */
	public void delete(final CompanyLdap company) {
		em.createQuery("DELETE FROM CacheCompany WHERE id=:id").setParameter("id", company.getId()).executeUpdate();
		em.flush();
		em.clear();
	}

	/**
	 * Delete a user.
	 * 
	 * @param user
	 *            the user to delete.
	 */
	public void delete(final UserLdap user) {
		em.createQuery("DELETE FROM CacheMembership WHERE user.id=:id").setParameter("id", user.getId()).executeUpdate();
		em.createQuery("DELETE FROM CacheUser WHERE id=:id").setParameter("id", user.getId()).executeUpdate();
		em.flush();
		em.clear();
	}

	/**
	 * Remove a user from a group.
	 * 
	 * @param user
	 *            the user to remove from the group
	 * @param group
	 *            the group to update.
	 */
	public void removeUserFromGroup(final UserLdap user, final GroupLdap group) {
		em.createQuery("DELETE FROM CacheMembership WHERE user.id=:user AND group.id=:group").setParameter("group", group.getId())
				.setParameter("user", user.getId()).executeUpdate();
	}

	/**
	 * Remove a group from a group.
	 * 
	 * @param subGroup
	 *            the user to remove from the group
	 * @param group
	 *            the group to update.
	 */
	public void removeGroupFromGroup(final GroupLdap subGroup, final GroupLdap group) {
		em.createQuery("DELETE FROM CacheMembership WHERE subGroup.id=:subGroup AND group.id=:group").setParameter("group", group.getId())
				.setParameter("subGroup", subGroup.getId()).executeUpdate();
	}

	/**
	 * Add a user to a group.
	 * 
	 * @param user
	 *            the user to add to the group.
	 * @param group
	 *            the group to update.
	 */
	public void addUserToGroup(final UserLdap user, final GroupLdap group) {
		addUserToGroupInternal(em.find(CacheUser.class, user.getId()), group);
	}

	/**
	 * Add a group to a group.
	 * 
	 * @param subGroup
	 *            the group to add the parent group.
	 * @param group
	 *            the group to update.
	 */
	public void addGroupToGroup(final GroupLdap subGroup, final GroupLdap group) {
		addGroupToGroupInternal(em.find(CacheGroup.class, subGroup.getId()), group);
	}

	/**
	 * Associate a user to a group.
	 */
	private void addUserToGroupInternal(final CacheUser entity, final GroupLdap groupLdap) {
		final CacheMembership membership = new CacheMembership();
		final CacheGroup cacheGroup = new CacheGroup();
		cacheGroup.setId(groupLdap.getId());
		membership.setUser(entity);
		membership.setGroup(cacheGroup);
		em.persist(membership);
	}

	/**
	 * Associate a group to another group.
	 */
	private void addGroupToGroupInternal(final CacheGroup entity, final GroupLdap groupLdap) {
		final CacheMembership membership = new CacheMembership();
		final CacheGroup cacheGroup = new CacheGroup();
		cacheGroup.setId(groupLdap.getId());
		membership.setSubGroup(entity);
		membership.setGroup(cacheGroup);
		em.persist(membership);
	}

}

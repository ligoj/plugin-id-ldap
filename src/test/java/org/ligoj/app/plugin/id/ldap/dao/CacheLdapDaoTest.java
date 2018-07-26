/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.dao;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.dao.DelegateOrgRepository;
import org.ligoj.app.iam.model.CacheCompany;
import org.ligoj.app.iam.model.CacheGroup;
import org.ligoj.app.iam.model.CacheMembership;
import org.ligoj.app.iam.model.CacheUser;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.iam.model.DelegateType;
import org.ligoj.app.iam.model.ReceiverType;
import org.ligoj.app.plugin.id.dao.IdCacheDao;
import org.ligoj.bootstrap.AbstractJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link IdCacheDao}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = { "classpath:/META-INF/spring/application-context-test.xml" })
@Rollback
@Transactional
public class CacheLdapDaoTest extends AbstractJpaTest {

	@Autowired
	private IdCacheDao dao;

	@Autowired
	private DelegateOrgRepository delegateOrgRepository;

	@BeforeEach
	public void initDbCache() {
		final CacheCompany company = new CacheCompany();
		company.setId("another-company");
		company.setName("Another-Company");
		company.setDescription("dna"); // DN
		em.persist(company);
		final CacheGroup group = new CacheGroup();
		group.setId("group");
		group.setName("Group");
		group.setDescription("dng"); // DN
		em.persist(group);
		final CacheGroup subgroup = new CacheGroup();
		subgroup.setId("another-group");
		subgroup.setName("Another-Group");
		subgroup.setDescription("dng2"); // DN
		em.persist(subgroup);
		final CacheUser user = new CacheUser();
		user.setId("u0");
		user.setCompany(company);
		em.persist(user);
		final CacheMembership membership = new CacheMembership();
		membership.setGroup(group);
		membership.setUser(user);
		em.persist(membership);
		final CacheMembership membershipSubGroup = new CacheMembership();
		membershipSubGroup.setGroup(group);
		membershipSubGroup.setSubGroup(subgroup);
		em.persist(membershipSubGroup);
		em.flush();
		em.clear();
	}

	@Test
	public void reset() {
		final Map<String, CompanyOrg> companies = new HashMap<>();
		companies.put("company", new CompanyOrg("dn=company1", "Company"));
		final Map<String, GroupOrg> groups = new HashMap<>();
		final Set<String> members = new HashSet<>();
		members.add("u");
		final GroupOrg groupLdap = new GroupOrg("dn=group1", "Group", members);
		groups.put("group", groupLdap);
		final UserOrg user = newUser();
		final UserOrg user2 = new UserOrg();
		user2.setId("u2");
		user2.setFirstName("f");
		user2.setLastName("l");
		user2.setCompany("company");
		user2.setGroups(Collections.emptyList());
		final Map<String, UserOrg> users = new HashMap<>();
		users.put("u", user);
		users.put("u2", user2);

		// Add delegates related to some containers

		// Broken, non existing related group
		final DelegateOrg brokenDelegate = new DelegateOrg();
		brokenDelegate.setName("broken");
		brokenDelegate.setDn("dn=group1");
		brokenDelegate.setType(DelegateType.GROUP);
		brokenDelegate.setReceiver("u2");
		brokenDelegate.setReceiverDn("uid=u2,dn=company1");
		brokenDelegate.setReceiverType(ReceiverType.USER);
		delegateOrgRepository.saveAndFlush(brokenDelegate);

		// Valid but DN need update
		final DelegateOrg updateDelegate = new DelegateOrg();
		updateDelegate.setName("company");
		updateDelegate.setDn("dn=old-company");
		updateDelegate.setType(DelegateType.COMPANY);
		updateDelegate.setReceiver("group");
		updateDelegate.setReceiverDn("dn=old-group");
		updateDelegate.setReceiverType(ReceiverType.GROUP);
		delegateOrgRepository.saveAndFlush(updateDelegate);

		// Valid but DN and up-to-date
		final DelegateOrg up2dateDelegate = new DelegateOrg();
		up2dateDelegate.setName("company");
		up2dateDelegate.setDn("dn=company1");
		up2dateDelegate.setType(DelegateType.COMPANY);
		up2dateDelegate.setReceiver("group");
		up2dateDelegate.setReceiverDn("dn=group1");
		up2dateDelegate.setReceiverType(ReceiverType.GROUP);
		delegateOrgRepository.saveAndFlush(up2dateDelegate);
		em.flush();

		// Pre state
		Assertions.assertNotNull(em.find(CacheCompany.class, "another-company"));
		Assertions.assertNotNull(em.find(CacheGroup.class, "group"));
		Assertions.assertNotNull(em.find(CacheUser.class, "u0"));
		Assertions.assertEquals(3, delegateOrgRepository.count());

		dao.reset(companies, groups, users);

		// Check previous cache is deleted
		Assertions.assertNull(em.find(CacheCompany.class, "another-company"));
		Assertions.assertNull(em.find(CacheGroup.class, "another-group"));
		Assertions.assertNull(em.find(CacheUser.class, "u0"));

		// Check the new state
		final CacheCompany company = em.find(CacheCompany.class, "company");
		Assertions.assertNotNull(company);
		Assertions.assertEquals("company", company.getId());
		Assertions.assertEquals("Company", company.getName());
		Assertions.assertEquals("dn=company1", company.getDescription());

		final CacheGroup group = em.find(CacheGroup.class, "group");
		Assertions.assertNotNull(group);
		Assertions.assertEquals("group", group.getId());
		Assertions.assertEquals("Group", group.getName());
		Assertions.assertEquals("dn=group1", group.getDescription());
		checkUser();
		final List<CacheMembership> memberships = em.createQuery("FROM CacheMembership", CacheMembership.class)
				.getResultList();
		Assertions.assertEquals(1, memberships.size());
		Assertions.assertEquals("group", memberships.get(0).getGroup().getId());
		Assertions.assertNull(memberships.get(0).getSubGroup());
		Assertions.assertEquals("u", memberships.get(0).getUser().getId());

		// Check the state of the previous delegates
		Assertions.assertEquals(2, delegateOrgRepository.count());

		// Broken and deleted delegate
		Assertions.assertFalse(delegateOrgRepository.existsById(brokenDelegate.getId()));

		// Updated delegate
		Assertions.assertEquals("dn=company1", delegateOrgRepository.findOneExpected(updateDelegate.getId()).getDn());
		Assertions.assertEquals("dn=group1", delegateOrgRepository.findOneExpected(updateDelegate.getId()).getReceiverDn());

		// Up to date delegate
		Assertions.assertEquals("dn=company1", delegateOrgRepository.findOneExpected(up2dateDelegate.getId()).getDn());
		Assertions.assertEquals("dn=group1", delegateOrgRepository.findOneExpected(up2dateDelegate.getId()).getReceiverDn());
	}

	private UserOrg newUser() {
		final UserOrg user = new UserOrg();
		user.setId("u");
		user.setFirstName("f");
		user.setLastName("l");
		user.setCompany("company");
		user.setGroups(Collections.singleton("group"));
		user.setMails(Collections.singletonList("mail"));
		return user;
	}

	private void checkUser() {
		final CacheUser user3 = em.find(CacheUser.class, "u");
		Assertions.assertNotNull(user3);
		Assertions.assertEquals("u", user3.getId());
		Assertions.assertEquals("company", user3.getCompany().getId());
		Assertions.assertEquals("f", user3.getFirstName());
		Assertions.assertEquals("l", user3.getLastName());
		Assertions.assertEquals("mail", user3.getMails());
	}

	@Test
	public void clear() {
		Assertions.assertNotNull(em.find(CacheCompany.class, "another-company"));
		Assertions.assertNotNull(em.find(CacheGroup.class, "group"));
		Assertions.assertNotNull(em.find(CacheUser.class, "u0"));
		Assertions.assertEquals(2, em.createQuery("FROM CacheMembership").getResultList().size());
		em.clear();
		dao.clear();
		Assertions.assertEquals(0, em.createQuery("FROM CacheMembership").getResultList().size());
		Assertions.assertEquals(0, em.createQuery("FROM CacheGroup").getResultList().size());
		Assertions.assertEquals(0, em.createQuery("FROM CacheUser").getResultList().size());
		Assertions.assertEquals(0, em.createQuery("FROM CacheMembership").getResultList().size());
	}

	@Test
	public void createUser() {
		Assertions.assertEquals(1, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0")
				.getResultList().size());
		final CacheCompany company = new CacheCompany();
		company.setId("company");
		company.setName("Company");
		company.setDescription("cn=company");
		em.persist(company);
		em.flush();
		em.clear();

		dao.create(newUser());

		Assertions.assertEquals("cn=company", em.find(CacheCompany.class, "company").getDescription());
		Assertions.assertNotNull(em.find(CacheCompany.class, "another-company"));
		Assertions.assertNotNull(em.find(CacheGroup.class, "group"));
		checkUser();
	}

	@Test
	public void updateUser() {
		Assertions.assertEquals(1, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0")
				.getResultList().size());
		final UserOrg newUser = newUser();
		newUser.setId("u0");
		newUser.setFirstName("F");
		newUser.setLastName("L");
		newUser.setCompany("another-company");
		newUser.setMails(null);
		dao.update(newUser);

		Assertions.assertNotNull(em.find(CacheCompany.class, "another-company"));
		Assertions.assertNotNull(em.find(CacheGroup.class, "group"));
		final CacheUser user3 = em.find(CacheUser.class, "u0");
		Assertions.assertNotNull(user3);
		Assertions.assertEquals("u0", user3.getId());
		Assertions.assertEquals("another-company", user3.getCompany().getId());
		Assertions.assertEquals("F", user3.getFirstName());
		Assertions.assertEquals("L", user3.getLastName());
		Assertions.assertNull(user3.getMails());
		final List<CacheMembership> memberships = em
				.createQuery("FROM CacheMembership WHERE user.id = :id", CacheMembership.class).setParameter("id", "u0")
				.getResultList();
		Assertions.assertEquals(1, memberships.size());
		Assertions.assertEquals("group", memberships.get(0).getGroup().getId());
		Assertions.assertNull(memberships.get(0).getSubGroup());
		Assertions.assertEquals("u0", memberships.get(0).getUser().getId());
	}

	private UserOrg newUser(final String login) {
		final UserOrg user = new UserOrg();
		user.setId(login);
		return user;
	}

	@Test
	public void removeUserFromGroup() {
		Assertions.assertEquals(1, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0")
				.getResultList().size());
		dao.removeUserFromGroup(newUser("u0"), new GroupOrg("dng", "Group", null));
		Assertions.assertEquals(0, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0")
				.getResultList().size());
	}

	@Test
	public void removeGroupFromGroup() {
		Assertions.assertEquals(1, em.createQuery("FROM CacheMembership WHERE group.id = :id AND subGroup.id = :sid")
				.setParameter("id", "group").setParameter("sid", "another-group").getResultList().size());
		dao.removeGroupFromGroup(new GroupOrg("dng2", "Another-Group", null), new GroupOrg("dng", "Group", null));
		Assertions.assertEquals(0, em.createQuery("FROM CacheMembership WHERE group.id = :id AND subGroup.id = :sid")
				.setParameter("id", "group").setParameter("sid", "another-group").getResultList().size());
	}

	@Test
	public void addUserToGroup() {
		em.createQuery("DELETE FROM CacheMembership").executeUpdate();
		Assertions.assertEquals(0, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0")
				.getResultList().size());
		dao.addUserToGroup(newUser("u0"), new GroupOrg("dng", "Group", null));
		Assertions.assertEquals(1, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0")
				.getResultList().size());
	}

	@Test
	public void createGroup() {
		Assertions.assertEquals(0, em.createQuery("FROM CacheGroup WHERE id = :id").setParameter("id", "namesg-other")
				.getResultList().size());
		dao.create(new GroupOrg("dng3", "NameSG-other", null));
		final CacheGroup group = em.find(CacheGroup.class, "namesg-other");
		Assertions.assertNotNull(group);
		Assertions.assertEquals("namesg-other", group.getId());
		Assertions.assertEquals("NameSG-other", group.getName());
		Assertions.assertEquals("dng3", group.getDescription());
	}

	@Test
	public void addGroupToGroup() {
		dao.create(new GroupOrg("dng3", "NameSG-other", null));
		Assertions.assertEquals(0, em.createQuery("FROM CacheMembership WHERE group.id = :id AND subGroup.id = :sid")
				.setParameter("id", "group").setParameter("sid", "namesg-other").getResultList().size());
		dao.addGroupToGroup(new GroupOrg("dng3", "NameSG-other", null), new GroupOrg("dng", "Group", null));

		final List<CacheMembership> memberships = em
				.createQuery("FROM CacheMembership WHERE group.id = :id AND subGroup.id = :sid", CacheMembership.class)
				.setParameter("id", "group").setParameter("sid", "namesg-other").getResultList();
		Assertions.assertEquals(1, memberships.size());
		Assertions.assertEquals("group", memberships.get(0).getGroup().getId());
		Assertions.assertEquals("namesg-other", memberships.get(0).getSubGroup().getId());
		Assertions.assertNull(memberships.get(0).getUser());
	}

	@Test
	public void deleteUser() {
		Assertions.assertEquals(1, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0")
				.getResultList().size());
		final UserOrg user = new UserOrg();
		user.setId("u0");

		dao.delete(user);

		Assertions.assertNotNull(em.find(CacheCompany.class, "another-company"));
		Assertions.assertNotNull(em.find(CacheGroup.class, "group"));
		Assertions.assertNull(em.find(CacheUser.class, "u0"));
		Assertions.assertEquals(0, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0")
				.getResultList().size());
	}

	@Test
	public void deleteGroup() {
		Assertions.assertEquals(2, em.createQuery("FROM CacheMembership WHERE group.id = :id")
				.setParameter("id", "group").getResultList().size());
		final GroupOrg groupLdap = new GroupOrg("dng", "Group", null);

		dao.delete(groupLdap);

		Assertions.assertNotNull(em.find(CacheCompany.class, "another-company"));
		Assertions.assertNull(em.find(CacheGroup.class, "group"));
		Assertions.assertNotNull(em.find(CacheUser.class, "u0"));
		Assertions.assertEquals(0, em.createQuery("FROM CacheMembership WHERE group.id = :id")
				.setParameter("id", "group").getResultList().size());
	}
}

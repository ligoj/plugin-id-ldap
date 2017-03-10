package org.ligoj.app.dao.ldap;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.transaction.Transactional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.ligoj.bootstrap.AbstractJpaTest;
import org.ligoj.app.model.ldap.CacheCompany;
import org.ligoj.app.model.ldap.CacheGroup;
import org.ligoj.app.model.ldap.CacheMembership;
import org.ligoj.app.model.ldap.CacheUser;
import org.ligoj.app.model.ldap.CompanyLdap;
import org.ligoj.app.model.ldap.GroupLdap;
import org.ligoj.app.model.ldap.UserLdap;

/**
 * Test class of {@link LdapCacheDao}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:/META-INF/spring/application-context-test.xml" })
@Rollback
@Transactional
public class LdapCacheDaoTest extends AbstractJpaTest {

	@Autowired
	private LdapCacheDao dao;

	@Before
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
	public void getLdapData() {
		final Map<String, CompanyLdap> companies = new HashMap<>();
		companies.put("company", new CompanyLdap("dnc", "Company"));
		final Map<String, GroupLdap> groups = new HashMap<>();
		final Set<String> members = new HashSet<>();
		members.add("u");
		final GroupLdap groupLdap = new GroupLdap("dng", "Group", members);
		groups.put("group", groupLdap);
		final UserLdap user = newUser();
		final UserLdap user2 = new UserLdap();
		user2.setId("u2");
		user2.setFirstName("f");
		user2.setLastName("l");
		user2.setCompany("company");
		user2.setGroups(Collections.EMPTY_LIST);
		final Map<String, UserLdap> users = new HashMap<>();
		users.put("u", user);
		users.put("u2", user2);

		// Pre state
		Assert.assertNotNull(em.find(CacheCompany.class, "another-company"));
		Assert.assertNotNull(em.find(CacheGroup.class, "group"));
		Assert.assertNotNull(em.find(CacheUser.class, "u0"));

		dao.reset(companies, groups, users);

		// Check previous cache is deleted
		Assert.assertNull(em.find(CacheCompany.class, "another-company"));
		Assert.assertNull(em.find(CacheGroup.class, "another-group"));
		Assert.assertNull(em.find(CacheUser.class, "u0"));

		// Check the new state
		final CacheCompany company = em.find(CacheCompany.class, "company");
		Assert.assertNotNull(company);
		Assert.assertEquals("company", company.getId());
		Assert.assertEquals("Company", company.getName());
		Assert.assertEquals("dnc", company.getDescription());

		final CacheGroup group = em.find(CacheGroup.class, "group");
		Assert.assertNotNull(group);
		Assert.assertEquals("group", group.getId());
		Assert.assertEquals("Group", group.getName());
		Assert.assertEquals("dng", group.getDescription());
		checkUser();
		final List<CacheMembership> memberships = em.createQuery("FROM CacheMembership", CacheMembership.class).getResultList();
		Assert.assertEquals(1, memberships.size());
		Assert.assertEquals("group", memberships.get(0).getGroup().getId());
		Assert.assertNull(memberships.get(0).getSubGroup());
		Assert.assertEquals("u", memberships.get(0).getUser().getId());
	}

	private UserLdap newUser() {
		final UserLdap user = new UserLdap();
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
		Assert.assertNotNull(user3);
		Assert.assertEquals("u", user3.getId());
		Assert.assertEquals("company", user3.getCompany().getId());
		Assert.assertEquals("f", user3.getFirstName());
		Assert.assertEquals("l", user3.getLastName());
		Assert.assertEquals("mail", user3.getMails());
	}

	@Test
	public void clear() {
		Assert.assertNotNull(em.find(CacheCompany.class, "another-company"));
		Assert.assertNotNull(em.find(CacheGroup.class, "group"));
		Assert.assertNotNull(em.find(CacheUser.class, "u0"));
		Assert.assertEquals(2, em.createQuery("FROM CacheMembership").getResultList().size());
		em.clear();
		dao.clear();
		Assert.assertEquals(0, em.createQuery("FROM CacheMembership").getResultList().size());
		Assert.assertEquals(0, em.createQuery("FROM CacheGroup").getResultList().size());
		Assert.assertEquals(0, em.createQuery("FROM CacheUser").getResultList().size());
		Assert.assertEquals(0, em.createQuery("FROM CacheMembership").getResultList().size());
	}

	@Test
	public void createUser() {
		Assert.assertEquals(1, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0").getResultList().size());
		final CacheCompany company = new CacheCompany();
		company.setId("company");
		company.setName("Company");
		company.setDescription("cn=company");
		em.persist(company);
		em.flush();
		em.clear();

		dao.create(newUser());

		Assert.assertEquals("cn=company", em.find(CacheCompany.class, "company").getDescription());
		Assert.assertNotNull(em.find(CacheCompany.class, "another-company"));
		Assert.assertNotNull(em.find(CacheGroup.class, "group"));
		checkUser();
	}

	@Test
	public void updateUser() {
		Assert.assertEquals(1, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0").getResultList().size());
		final UserLdap newUser = newUser();
		newUser.setId("u0");
		newUser.setFirstName("F");
		newUser.setLastName("L");
		newUser.setCompany("another-company");
		newUser.setMails(null);
		dao.update(newUser);

		Assert.assertNotNull(em.find(CacheCompany.class, "another-company"));
		Assert.assertNotNull(em.find(CacheGroup.class, "group"));
		final CacheUser user3 = em.find(CacheUser.class, "u0");
		Assert.assertNotNull(user3);
		Assert.assertEquals("u0", user3.getId());
		Assert.assertEquals("another-company", user3.getCompany().getId());
		Assert.assertEquals("F", user3.getFirstName());
		Assert.assertEquals("L", user3.getLastName());
		Assert.assertNull(user3.getMails());
		final List<CacheMembership> memberships = em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0").getResultList();
		Assert.assertEquals(1, memberships.size());
		Assert.assertEquals("group", memberships.get(0).getGroup().getId());
		Assert.assertNull(memberships.get(0).getSubGroup());
		Assert.assertEquals("u0", memberships.get(0).getUser().getId());
	}

	private UserLdap newUser(final String login) {
		final UserLdap user = new UserLdap();
		user.setId(login);
		return user;
	}

	@Test
	public void removeUserFromGroup() {
		Assert.assertEquals(1, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0").getResultList().size());
		dao.removeUserFromGroup(newUser("u0"), new GroupLdap("dng", "Group", null));
		Assert.assertEquals(0, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0").getResultList().size());
	}

	@Test
	public void removeGroupFromGroup() {
		Assert.assertEquals(1, em.createQuery("FROM CacheMembership WHERE group.id = :id AND subGroup.id = :sid").setParameter("id", "group")
				.setParameter("sid", "another-group").getResultList().size());
		dao.removeGroupFromGroup(new GroupLdap("dng2", "Another-Group", null), new GroupLdap("dng", "Group", null));
		Assert.assertEquals(0, em.createQuery("FROM CacheMembership WHERE group.id = :id AND subGroup.id = :sid").setParameter("id", "group")
				.setParameter("sid", "another-group").getResultList().size());
	}

	@Test
	public void addUserToGroup() {
		em.createQuery("DELETE FROM CacheMembership").executeUpdate();
		Assert.assertEquals(0, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0").getResultList().size());
		dao.addUserToGroup(newUser("u0"), new GroupLdap("dng", "Group", null));
		Assert.assertEquals(1, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0").getResultList().size());
	}

	@Test
	public void createGroup() {
		Assert.assertEquals(0, em.createQuery("FROM CacheGroup WHERE id = :id").setParameter("id", "namesg-other").getResultList().size());
		dao.create(new GroupLdap("dng3", "NameSG-other", null));
		final CacheGroup group = em.find(CacheGroup.class, "namesg-other");
		Assert.assertNotNull(group);
		Assert.assertEquals("namesg-other", group.getId());
		Assert.assertEquals("NameSG-other", group.getName());
		Assert.assertEquals("dng3", group.getDescription());
	}

	@Test
	public void addGroupToGroup() {
		dao.create(new GroupLdap("dng3", "NameSG-other", null));
		Assert.assertEquals(0, em.createQuery("FROM CacheMembership WHERE group.id = :id AND subGroup.id = :sid").setParameter("id", "group")
				.setParameter("sid", "namesg-other").getResultList().size());
		dao.addGroupToGroup(new GroupLdap("dng3", "NameSG-other", null), new GroupLdap("dng", "Group", null));

		final List<CacheMembership> memberships = em.createQuery("FROM CacheMembership WHERE group.id = :id AND subGroup.id = :sid")
				.setParameter("id", "group").setParameter("sid", "namesg-other").getResultList();
		Assert.assertEquals(1, memberships.size());
		Assert.assertEquals("group", memberships.get(0).getGroup().getId());
		Assert.assertEquals("namesg-other", memberships.get(0).getSubGroup().getId());
		Assert.assertNull(memberships.get(0).getUser());
	}

	@Test
	public void deleteUser() {
		Assert.assertEquals(1, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0").getResultList().size());
		final UserLdap user = new UserLdap();
		user.setId("u0");

		dao.delete(user);

		Assert.assertNotNull(em.find(CacheCompany.class, "another-company"));
		Assert.assertNotNull(em.find(CacheGroup.class, "group"));
		Assert.assertNull(em.find(CacheUser.class, "u0"));
		Assert.assertEquals(0, em.createQuery("FROM CacheMembership WHERE user.id = :id").setParameter("id", "u0").getResultList().size());
	}

	@Test
	public void deleteGroup() {
		Assert.assertEquals(2, em.createQuery("FROM CacheMembership WHERE group.id = :id").setParameter("id", "group").getResultList().size());
		final GroupLdap groupLdap = new GroupLdap("dng", "Group", null);

		dao.delete(groupLdap);

		Assert.assertNotNull(em.find(CacheCompany.class, "another-company"));
		Assert.assertNull(em.find(CacheGroup.class, "group"));
		Assert.assertNotNull(em.find(CacheUser.class, "u0"));
		Assert.assertEquals(0, em.createQuery("FROM CacheMembership WHERE group.id = :id").setParameter("id", "group").getResultList().size());
	}
}

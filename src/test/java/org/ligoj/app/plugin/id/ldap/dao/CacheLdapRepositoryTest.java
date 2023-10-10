/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.dao;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.*;
import org.ligoj.app.plugin.id.dao.AbstractMemCacheRepository.CacheDataType;
import org.ligoj.app.plugin.id.dao.IdCacheDao;
import org.ligoj.bootstrap.AbstractDataGeneratorTest;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.SpringUtils;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test class of {@link CacheLdapRepository}
 */
class CacheLdapRepositoryTest extends AbstractDataGeneratorTest {
	private UserOrg user;
	private GroupOrg groupImpl;
	private GroupOrg groupImpl2;
	private Map<String, GroupOrg> groups;
	private Map<String, CompanyOrg> companies;
	private Map<String, UserOrg> users;
	private CacheLdapRepository repository;
	private IdCacheDao cache;

	@BeforeEach
	void init() {
		final var companyRepository = Mockito.mock(CompanyLdapRepository.class);
		final var groupRepository = Mockito.mock(GroupLdapRepository.class);
		final var userRepository = Mockito.mock(UserLdapRepository.class);
		final var iamProvider = Mockito.mock(IamProvider.class);
		final ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
		SpringUtils.setSharedApplicationContext(applicationContext);
		final IamConfiguration iamConfiguration = new IamConfiguration();
		iamConfiguration.setCompanyRepository(companyRepository);
		iamConfiguration.setGroupRepository(groupRepository);
		iamConfiguration.setUserRepository(userRepository);
		Mockito.when(iamProvider.getConfiguration()).thenReturn(iamConfiguration);

		companies = new HashMap<>();
		companies.put("company", new CompanyOrg("dnc", "Company"));
		groups = new HashMap<>();
		final Set<String> members = new HashSet<>();
		members.add("u");
		groupImpl = new GroupOrg("dn", "Group", members);
		groups.put("group", groupImpl);
		groupImpl2 = new GroupOrg("dn2", "Group2", new HashSet<>());
		groups.put("group2", groupImpl2);
		user = new UserOrg();
		user.setId("u");
		user.setFirstName("f");
		user.setLastName("l");
		user.setCompany("company");
		final List<String> userGroups = new ArrayList<>();
		userGroups.add("group");
		user.setGroups(userGroups);
		user.setMails(Collections.singletonList("mail"));
		final var user2 = new UserOrg();
		user2.setId("u2");
		user2.setFirstName("f");
		user2.setLastName("l");
		user2.setCompany("company");
		user2.setGroups(new ArrayList<>());
		users = new HashMap<>();
		users.put("u", user);
		users.put("u2", user2);
		Mockito.when(companyRepository.findAllNoCache()).thenReturn(companies);
		Mockito.when(groupRepository.findAllNoCache()).thenReturn(groups);
		Mockito.when(userRepository.findAllNoCache(groups)).thenReturn(users);
		Mockito.when(companyRepository.findAll()).thenReturn(companies);
		Mockito.when(groupRepository.findAll()).thenReturn(groups);
		Mockito.when(userRepository.findAll()).thenReturn(users);

		repository = new CacheLdapRepository();
		repository.setIamProvider(new IamProvider[]{iamProvider});
		cache = Mockito.mock(IdCacheDao.class);
		repository.setCache(cache);
		repository.self = repository;
	}

	@Test
	void getDataImpl() {

		// Only there for coverage

		final var dataImpl = repository.getData();

		Assertions.assertEquals("Company", ((INamableBean<?>) dataImpl.get(CacheDataType.COMPANY).get("company")).getName());
		Assertions.assertEquals("dnc", dataImpl.get(CacheDataType.COMPANY).get("company").getDn());
		final GroupOrg groupImpl = (GroupOrg) dataImpl.get(CacheDataType.GROUP).get("group");
		Assertions.assertEquals("dn", groupImpl.getDn());
		Assertions.assertEquals("group", groupImpl.getId());
		Assertions.assertEquals("Group", groupImpl.getName());
		final UserOrg user = (UserOrg) dataImpl.get(CacheDataType.USER).get("u");
		Assertions.assertEquals("u", user.getId());
		Assertions.assertEquals("f", user.getFirstName());
		Assertions.assertEquals("l", user.getLastName());
		Assertions.assertEquals("company", user.getCompany());
		final UserOrg user2 = (UserOrg) dataImpl.get(CacheDataType.USER).get("u2");
		Assertions.assertEquals("u2", user2.getId());
		Assertions.assertEquals("f", user2.getFirstName());
		Assertions.assertEquals("l", user2.getLastName());
		Assertions.assertEquals("company", user2.getCompany());
	}

	@Test
	void addUserToGroup() {
		Assertions.assertEquals(1, user.getGroups().size());

		repository.addUserToGroup(user, groupImpl2);

		Assertions.assertEquals(2, user.getGroups().size());
		Assertions.assertTrue(user.getGroups().contains("group2"));
		Assertions.assertTrue(groups.get("group2").getMembers().contains("u"));
	}

	@Test
	void removeUserFromGroup() {
		Assertions.assertEquals(1, user.getGroups().size());

		repository.removeUserFromGroup(user, groupImpl);

		Assertions.assertEquals(0, user.getGroups().size());
		Assertions.assertTrue(groups.get("group").getMembers().isEmpty());
	}

	@Test
	void addGroupToGroup() {
		final GroupOrg parent = groupImpl2;
		final GroupOrg child = groupImpl;

		// Check the initial status
		Assertions.assertEquals(0, child.getSubGroups().size());
		Assertions.assertNull(child.getParent());
		Assertions.assertNull(parent.getParent());
		Assertions.assertEquals(0, parent.getSubGroups().size());

		repository.addGroupToGroup(child, parent);

		// Check the new status
		Assertions.assertEquals("group2", child.getParent());
		Assertions.assertEquals(0, child.getSubGroups().size());
		Assertions.assertNull(parent.getParent());
		Assertions.assertEquals(1, parent.getSubGroups().size());
		Assertions.assertTrue(parent.getSubGroups().contains("group"));

	}

	@Test
	void removeGroupFromGroup() {
		final GroupOrg parent = groupImpl2;
		final GroupOrg child = groupImpl;
		parent.getSubGroups().add(child.getId());
		child.setParent(parent.getId());

		// Check the initial status
		Assertions.assertNotNull(child.getParent());
		Assertions.assertEquals(0, child.getSubGroups().size());
		Assertions.assertNull(parent.getParent());
		Assertions.assertEquals(1, parent.getSubGroups().size());

		repository.removeGroupFromGroup(child, parent);

		// Check the new status
		Assertions.assertNull(child.getParent());
		Assertions.assertEquals(0, child.getSubGroups().size());
		Assertions.assertNull(parent.getParent());
		Assertions.assertEquals(0, parent.getSubGroups().size());
	}

	@Test
	void createGroup() {
		final var newGroup = new GroupOrg("dn3", "G3", new HashSet<>());
		repository.create(newGroup);
		Mockito.verify(cache).create(newGroup, Collections.emptyMap());
		Assertions.assertEquals(newGroup, groups.get("g3"));
	}

	@Test
	void createCompany() {
		final var newCompany = new CompanyOrg("dn3", "C3");
		repository.create(newCompany);
		Mockito.verify(cache).create(newCompany);
		Assertions.assertEquals(newCompany, companies.get("c3"));
	}

	@Test
	void createUser() {
		final UserOrg newUser = new UserOrg();
		newUser.setId("u3");
		newUser.setFirstName("f");
		newUser.setLastName("l");
		newUser.setCompany("company");

		repository.create(newUser);

		Mockito.verify(cache).create(newUser);
		Assertions.assertTrue(user.getGroups().contains("group"));
		Assertions.assertSame(newUser, users.get("u3"));
	}

	@Test
	void updateUser() {
		user.setFirstName("L");

		repository.update(user);

		Mockito.verify(cache).update(user);
		Assertions.assertSame("L", users.get("u").getFirstName());
	}

	@Test
	void deleteGroup() {
		Assertions.assertTrue(groups.containsKey("group"));
		Assertions.assertTrue(user.getGroups().contains("group"));

		repository.delete(groups.get("group"));

		Assertions.assertFalse(groups.containsKey("group"));
		Assertions.assertFalse(user.getGroups().contains("group"));
	}

	@Test
	void deleteUser() {
		Assertions.assertEquals(1, user.getGroups().size());
		Assertions.assertTrue(users.containsKey("u"));

		repository.delete(user);

		Mockito.verify(cache).delete(user);
		Assertions.assertFalse(users.containsKey("u"));
	}

	@Test
	void refreshData() {
		final var conflictDate = new AtomicLong(0);
		Mockito.when(cache.getCacheRefreshTime()).thenAnswer(a -> conflictDate.incrementAndGet());
		repository.refreshData();
	}
}

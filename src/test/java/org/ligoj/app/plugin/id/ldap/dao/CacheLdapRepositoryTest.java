/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.IamConfiguration;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.ResourceOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.plugin.id.dao.AbstractMemCacheRepository.CacheDataType;
import org.ligoj.app.plugin.id.dao.IdCacheDao;
import org.ligoj.bootstrap.AbstractDataGeneratorTest;
import org.ligoj.bootstrap.core.SpringUtils;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

/**
 * Test class of {@link CacheLdapRepository}
 */
public class CacheLdapRepositoryTest extends AbstractDataGeneratorTest {
	private CompanyLdapRepository companyRepository;
	private GroupLdapRepository groupRepository;
	private UserLdapRepository userRepository;
	private IamProvider iamProvider;
	private UserOrg user;
	private UserOrg user2;
	private GroupOrg groupLdap;
	private GroupOrg groupLdap2;
	private Map<String, GroupOrg> groups;
	private Map<String, CompanyOrg> companies;
	private Map<String, UserOrg> users;
	private CacheLdapRepository repository;
	private IdCacheDao cache;

	@BeforeEach
	public void init() {
		companyRepository = Mockito.mock(CompanyLdapRepository.class);
		groupRepository = Mockito.mock(GroupLdapRepository.class);
		userRepository = Mockito.mock(UserLdapRepository.class);
		iamProvider = Mockito.mock(IamProvider.class);
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
		groupLdap = new GroupOrg("dn", "Group", members);
		groups.put("group", groupLdap);
		groupLdap2 = new GroupOrg("dn2", "Group2", new HashSet<>());
		groups.put("group2", groupLdap2);
		user = new UserOrg();
		user.setId("u");
		user.setFirstName("f");
		user.setLastName("l");
		user.setCompany("company");
		final List<String> userGroups = new ArrayList<>();
		userGroups.add("group");
		user.setGroups(userGroups);
		user.setMails(Collections.singletonList("mail"));
		user2 = new UserOrg();
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

		cache = Mockito.mock(IdCacheDao.class);
		repository = new CacheLdapRepository();
		repository.setIamProvider(new IamProvider[] { iamProvider });
		repository.setCache(cache);
		repository.self = repository;
	}

	@Test
	public void getLdapData() {

		// Only there for coverage

		final Map<CacheDataType, Map<String, ? extends ResourceOrg>> ldapData = repository.getData();

		Assertions.assertEquals("Company", ((CompanyOrg) ldapData.get(CacheDataType.COMPANY).get("company")).getName());
		Assertions.assertEquals("dnc", ((CompanyOrg) ldapData.get(CacheDataType.COMPANY).get("company")).getDn());
		final GroupOrg groupLdap = (GroupOrg) ldapData.get(CacheDataType.GROUP).get("group");
		Assertions.assertEquals("dn", groupLdap.getDn());
		Assertions.assertEquals("group", groupLdap.getId());
		Assertions.assertEquals("Group", groupLdap.getName());
		final UserOrg user = (UserOrg) ldapData.get(CacheDataType.USER).get("u");
		Assertions.assertEquals("u", user.getId());
		Assertions.assertEquals("f", user.getFirstName());
		Assertions.assertEquals("l", user.getLastName());
		Assertions.assertEquals("company", user.getCompany());
		final UserOrg user2 = (UserOrg) ldapData.get(CacheDataType.USER).get("u2");
		Assertions.assertEquals("u2", user2.getId());
		Assertions.assertEquals("f", user2.getFirstName());
		Assertions.assertEquals("l", user2.getLastName());
		Assertions.assertEquals("company", user2.getCompany());
	}

	@Test
	public void addUserToGroup() {
		Assertions.assertEquals(1, user.getGroups().size());

		repository.addUserToGroup(user, groupLdap2);

		Assertions.assertEquals(2, user.getGroups().size());
		Assertions.assertTrue(user.getGroups().contains("group2"));
		Assertions.assertTrue(groups.get("group2").getMembers().contains("u"));
	}

	@Test
	public void removeUserFromGroup() {
		Assertions.assertEquals(1, user.getGroups().size());

		repository.removeUserFromGroup(user, groupLdap);

		Assertions.assertEquals(0, user.getGroups().size());
		Assertions.assertTrue(groups.get("group").getMembers().isEmpty());
	}

	@Test
	public void addGroupToGroup() {
		final GroupOrg parent = groupLdap2;
		final GroupOrg child = groupLdap;

		// Check the initial status
		Assertions.assertEquals(0, child.getSubGroups().size());
		Assertions.assertEquals(0, child.getGroups().size());
		Assertions.assertEquals(0, parent.getGroups().size());
		Assertions.assertEquals(0, parent.getSubGroups().size());

		repository.addGroupToGroup(child, parent);

		// Check the new status
		Assertions.assertEquals(1, child.getGroups().size());
		Assertions.assertEquals(0, child.getSubGroups().size());
		Assertions.assertEquals(0, parent.getGroups().size());
		Assertions.assertEquals(1, parent.getSubGroups().size());
		Assertions.assertTrue(parent.getSubGroups().contains("group"));
		Assertions.assertTrue(child.getGroups().contains("group2"));
	}

	@Test
	public void removeGroupFromGroup() {
		final GroupOrg parent = groupLdap2;
		final GroupOrg child = groupLdap;
		parent.getSubGroups().add(child.getId());
		child.getGroups().add(parent.getId());

		// Check the initial status
		Assertions.assertEquals(1, child.getGroups().size());
		Assertions.assertEquals(0, child.getSubGroups().size());
		Assertions.assertEquals(0, parent.getGroups().size());
		Assertions.assertEquals(1, parent.getSubGroups().size());

		repository.removeGroupFromGroup(child, parent);

		// Check the new status
		Assertions.assertEquals(0, child.getGroups().size());
		Assertions.assertEquals(0, child.getSubGroups().size());
		Assertions.assertEquals(0, parent.getGroups().size());
		Assertions.assertEquals(0, parent.getSubGroups().size());
	}

	@Test
	public void createGroup() {
		final GroupOrg newGroupLdap = new GroupOrg("dn3", "G3", new HashSet<>());

		repository.create(newGroupLdap);

		Mockito.verify(cache).create(newGroupLdap);
		Assertions.assertEquals(newGroupLdap, groups.get("g3"));
	}

	@Test
	public void createCompany() {
		final CompanyOrg newCompanyLdap = new CompanyOrg("dn3", "C3");

		repository.create(newCompanyLdap);

		Mockito.verify(cache).create(newCompanyLdap);
		Assertions.assertEquals(newCompanyLdap, companies.get("c3"));
	}

	@Test
	public void createUser() {
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
	public void updateUser() {
		user.setFirstName("L");

		repository.update(user);

		Mockito.verify(cache).update(user);
		Assertions.assertSame("L", users.get("u").getFirstName());
	}

	@Test
	public void deleteGroup() {
		Assertions.assertTrue(groups.containsKey("group"));
		Assertions.assertTrue(user.getGroups().contains("group"));

		repository.delete(groups.get("group"));

		Assertions.assertFalse(groups.containsKey("group"));
		Assertions.assertFalse(user.getGroups().contains("group"));
	}

	@Test
	public void deleteUser() {
		Assertions.assertEquals(1, user.getGroups().size());
		Assertions.assertTrue(users.containsKey("u"));

		repository.delete(user);

		Mockito.verify(cache).delete(user);
		Assertions.assertFalse(users.containsKey("u"));
	}
}

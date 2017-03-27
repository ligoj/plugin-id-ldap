package org.ligoj.app.plugin.id.ldap.dao;

import java.util.*;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import org.ligoj.bootstrap.AbstractDataGeneratorTest;
import org.ligoj.bootstrap.core.SpringUtils;
import org.ligoj.app.api.CompanyOrg;
import org.ligoj.app.api.GroupOrg;
import org.ligoj.app.api.UserOrg;
import org.ligoj.app.iam.IamConfiguration;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.plugin.id.ldap.dao.CompanyLdapRepository;
import org.ligoj.app.plugin.id.ldap.dao.GroupLdapRepository;
import org.ligoj.app.plugin.id.ldap.dao.LdapCacheDao;
import org.ligoj.app.plugin.id.ldap.dao.LdapCacheRepository;
import org.ligoj.app.plugin.id.ldap.dao.UserLdapRepository;
import org.ligoj.app.plugin.id.ldap.dao.LdapCacheRepository.LdapData;

import net.sf.ehcache.CacheManager;

/**
 * Test class of {@link LdapCacheRepository}
 */
public class LdapCacheRepositoryTest extends AbstractDataGeneratorTest {
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
	private LdapCacheRepository repository;

	@Before
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
		CacheManager.getInstance().getCache("ldap").removeAll();
		
		repository = new LdapCacheRepository();
		repository.iamProvider = iamProvider;
		repository.ldapCacheDao = Mockito.mock(LdapCacheDao.class);
	}

	@Test
	public void getLdapData() {

		// Only there for coverage
		LdapData.values();
		LdapData.valueOf(LdapData.COMPANY.name());

		final Map<LdapData, Map<String, ?>> ldapData = repository.getLdapData();

		Assert.assertEquals("Company", ((CompanyOrg) ldapData.get(LdapData.COMPANY).get("company")).getName());
		Assert.assertEquals("dnc", ((CompanyOrg) ldapData.get(LdapData.COMPANY).get("company")).getDn());
		final GroupOrg groupLdap = (GroupOrg) ldapData.get(LdapData.GROUP).get("group");
		Assert.assertEquals("dn", groupLdap.getDn());
		Assert.assertEquals("group", groupLdap.getId());
		Assert.assertEquals("Group", groupLdap.getName());
		final UserOrg user = (UserOrg) ldapData.get(LdapData.USER).get("u");
		Assert.assertEquals("u", user.getId());
		Assert.assertEquals("f", user.getFirstName());
		Assert.assertEquals("l", user.getLastName());
		Assert.assertEquals("company", user.getCompany());
		final UserOrg user2 = (UserOrg) ldapData.get(LdapData.USER).get("u2");
		Assert.assertEquals("u2", user2.getId());
		Assert.assertEquals("f", user2.getFirstName());
		Assert.assertEquals("l", user2.getLastName());
		Assert.assertEquals("company", user2.getCompany());
	}

	@Test
	public void addUserToGroup() {
		Assert.assertEquals(1, user.getGroups().size());
		
		repository.addUserToGroup(user, groupLdap2);
		
		Assert.assertEquals(2, user.getGroups().size());
		Assert.assertTrue(user.getGroups().contains("group2"));
		Assert.assertTrue(groups.get("group2").getMembers().contains("u"));
	}

	@Test
	public void removeUserFromGroup() {
		Assert.assertEquals(1, user.getGroups().size());
		
		repository.removeUserFromGroup(user, groupLdap);
		
		Assert.assertEquals(0, user.getGroups().size());
		Assert.assertTrue(groups.get("group").getMembers().isEmpty());
	}

	@Test
	public void addGroupToGroup() {
		final GroupOrg parent = groupLdap2;
		final GroupOrg child = groupLdap;

		// Check the initial status
		Assert.assertEquals(0, child.getSubGroups().size());
		Assert.assertEquals(0, child.getGroups().size());
		Assert.assertEquals(0, parent.getGroups().size());
		Assert.assertEquals(0, parent.getSubGroups().size());

		repository.addGroupToGroup(child, parent);

		// Check the new status
		Assert.assertEquals(1, child.getGroups().size());
		Assert.assertEquals(0, child.getSubGroups().size());
		Assert.assertEquals(0, parent.getGroups().size());
		Assert.assertEquals(1, parent.getSubGroups().size());
		Assert.assertTrue(parent.getSubGroups().contains("group"));
		Assert.assertTrue(child.getGroups().contains("group2"));
	}

	@Test
	public void removeGroupFromGroup() {
		final GroupOrg parent = groupLdap2;
		final GroupOrg child = groupLdap;
		parent.getSubGroups().add(child.getId());
		child.getGroups().add(parent.getId());

		// Check the initial status
		Assert.assertEquals(1, child.getGroups().size());
		Assert.assertEquals(0, child.getSubGroups().size());
		Assert.assertEquals(0, parent.getGroups().size());
		Assert.assertEquals(1, parent.getSubGroups().size());

		repository.removeGroupFromGroup(child, parent);

		// Check the new status
		Assert.assertEquals(0, child.getGroups().size());
		Assert.assertEquals(0, child.getSubGroups().size());
		Assert.assertEquals(0, parent.getGroups().size());
		Assert.assertEquals(0, parent.getSubGroups().size());
	}

	@Test
	public void createGroup() {
		final GroupOrg newGroupLdap = new GroupOrg("dn3", "G3", new HashSet<>());

		repository.create(newGroupLdap);

		Mockito.verify(repository.ldapCacheDao).create(newGroupLdap);
		Assert.assertEquals(newGroupLdap, groups.get("g3"));
	}

	@Test
	public void createCompany() {
		final CompanyOrg newCompanyLdap = new CompanyOrg("dn3", "C3");

		repository.create(newCompanyLdap);

		Mockito.verify(repository.ldapCacheDao).create(newCompanyLdap);
		Assert.assertEquals(newCompanyLdap, companies.get("c3"));
	}

	@Test
	public void createUser() {
		final UserOrg newUser = new UserOrg();
		newUser.setId("u3");
		newUser.setFirstName("f");
		newUser.setLastName("l");
		newUser.setCompany("company");

		repository.create(newUser);

		Mockito.verify(repository.ldapCacheDao).create(newUser);
		Assert.assertTrue(user.getGroups().contains("group"));
		Assert.assertSame(newUser, users.get("u3"));
	}

	@Test
	public void updateUser() {
		user.setFirstName("L");

		repository.update(user);

		Mockito.verify(repository.ldapCacheDao).update(user);
		Assert.assertSame("L", users.get("u").getFirstName());
	}

	@Test
	public void deleteGroup() {
		Assert.assertTrue(groups.containsKey("group"));
		Assert.assertTrue(user.getGroups().contains("group"));

		repository.delete(groups.get("group"));

		Assert.assertFalse(groups.containsKey("group"));
		Assert.assertFalse(user.getGroups().contains("group"));
	}

	@Test
	public void deleteUser() {
		Assert.assertEquals(1, user.getGroups().size());
		Assert.assertTrue(users.containsKey("u"));

		repository.delete(user);

		Mockito.verify(repository.ldapCacheDao).delete(user);
		Assert.assertFalse(users.containsKey("u"));
	}
}

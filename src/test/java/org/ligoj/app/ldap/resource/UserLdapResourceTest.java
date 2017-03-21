package org.ligoj.app.ldap.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import javax.transaction.Transactional;
import javax.ws.rs.core.UriInfo;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.api.SimpleUserLdap;
import org.ligoj.app.api.UserLdap;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.ldap.dao.GroupLdapRepository;
import org.ligoj.app.ldap.dao.UserLdapRepository;
import org.ligoj.app.model.DelegateOrg;
import org.ligoj.app.model.DelegateType;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import net.sf.ehcache.CacheManager;

/**
 * Test of {@link UserLdapResource}<br>
 * Delegate
 * <ul>
 * <li>user;type;name;write;admin;dn</li>
 * <li>fdaugan;group;dig rha;true;true;cn=DIG RHA,cn=DIG AS,cn=DIG,ou=fonction,ou=groups</li>
 * <li>fdaugan;group;any;true;true;cn=any,ou=groups</li>
 * <li>fdaugan;company;ing;true;true;ou=ing,ou=external,ou=people</li>
 * <li>fdaugan;company;any;true;true;cn=any,ou=groups</li>
 * <li>fdaugan;tree;ou=tools;true;true;ou=tools</li>
 * <li>someone;company;ing;true;true;ou=ing,ou=external,ou=people</li>
 * <li>someone;company;any;true;true;cn=any,ou=groups</li>
 * <li>someone;group;dig rha;true;true;cn=DIG RHA,cn=DIG AS,cn=DIG,ou=fonction,ou=groups</li>
 * <li>junit;tree;dc=sample,dc=com;true;true;dc=sample,dc=com</li>
 * <li>assist;company;socygan;true;true;ou=socygan,ou=external,ou=people</li>
 * <li>assist;company;ing;true;true;ou=ing,ou=external,ou=people</li>
 * <li>mmartin;group;dig sud ouest;true;true;cn=DIG Sud Ouest,cn=DIG AS,cn=DIG,ou=fonction,ou=groups</li>
 * <li>mmartin;group;any;true;true;cn=any,ou=groups</li>
 * <li>mmartin;company;socygan;true;true;ou=socygan,ou=external,ou=people</li>
 * <li>mmartin;company;any;true;true;cn=any,ou=groups</li>
 * <li>mtuyer;tree;ou=fonction,ou=groups;true;true;ou=fonction,ou=groups</li>
 * <li>mtuyer;company;ing;false;true;ou=ing,ou=external,ou=people</li>
 * <li>mlavoine;tree;cn=Biz Agency,ou=tools;false;false;cn=Biz Agency,ou=tools</li>
 * <li>gfi-gstack (group);company;ing;false;false;ou=ing,ou=external,ou=people,dc=sample,dc=com</li>
 * <li>ing (company);group;business solution;false;false;cn=business solution,ou=groups,dc=sample,dc=com</li>
 * </ul>
 * LDAP
 * <ul>
 * <li>ING : flast1(First1 Last1), fdoe2(First2 Doe2),jlast3(John3 Last3),jdoe4(John4 Doe4),jdoe5(First5 Last5)</li>
 * <li>socygan : flast0</li>
 * <li>DIG RHA : fdoe2,jlast3,jdoe4,jdoe5,gvescovi</li>
 * <li>DIG SUD OUEST : jlast3,pgenais</li>
 * </ul>
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
@org.junit.FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UserLdapResourceTest extends AbstractLdapTest {

	@Autowired
	private UserLdapResource resource;

	/**
	 * Check the result : expects one entry
	 */
	private void checkResult(final TableItem<UserLdapVo> tableItem) {
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());

		final UserLdapVo userLdap = tableItem.getData().get(0);
		Assert.assertEquals("flasta", userLdap.getId());
		Assert.assertEquals("Firsta", userLdap.getFirstName());
		Assert.assertEquals("Lasta", userLdap.getLastName());
		Assert.assertEquals("ing", userLdap.getCompany());
		Assert.assertEquals("flasta@ing.com", userLdap.getMails().get(0));
		Assert.assertEquals(1, userLdap.getGroups().size());
		Assert.assertEquals("DIG RHA", userLdap.getGroups().get(0).getName());
	}

	private UriInfo newUriInfoAsc(final String ascProperty) {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "100");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add("columns[2][data]", ascProperty);
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "asc");
		return uriInfo;
	}

	private UriInfo newUriInfoDesc(final String property) {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "100");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add("columns[2][data]", property);
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "desc");
		return uriInfo;
	}

	@Before
	public void prepareData() throws IOException {
		persistEntities("csv/app-test", new Class[] { DelegateOrg.class }, StandardCharsets.UTF_8.name());
		CacheManager.getInstance().getCache("ldap").removeAll();

		// Force the cache to be created
		getUser().findAll();
	}

	@Test
	public void findById() {
		final UserLdap userLdap = resource.findById("fdaugan");
		findById(userLdap);
	}

	@Test
	public void findByIdNoCache() {
		final UserLdap userLdap = resource.findByIdNoCache("fdaugan");
		Assert.assertNotNull(userLdap);
		Assert.assertEquals("fdaugan", userLdap.getId());
		Assert.assertEquals("Fabrice", userLdap.getFirstName());
		Assert.assertEquals("Daugan", userLdap.getLastName());
		Assert.assertEquals("gfi", userLdap.getCompany());
		Assert.assertEquals("fabrice.daugan@sample.com", userLdap.getMails().get(0));
	}

	@Test
	public void authenticate() {
		Assert.assertTrue(resource.authenticate("fdaugan", "Azerty01"));
		Assert.assertFalse(resource.authenticate("fdaugan", "-bad-"));
	}

	@Test
	public void findByIdCaseInsensitive() {
		final UserLdap userLdap = resource.findById("fdaugan");
		findById(userLdap);
	}

	@Test
	public void findBy() {
		final List<UserLdap> users = resource.findAllBy("mail", "marc.martin@sample.com");
		Assert.assertEquals(1, users.size());
		final UserLdap userLdap = users.get(0);
		Assert.assertEquals("mmartin", userLdap.getName());
		Assert.assertEquals("3890", userLdap.getDepartment());
		Assert.assertEquals("8234", userLdap.getLocalId());
	}

	private void findById(final UserLdap userLdap) {
		Assert.assertNotNull(userLdap);
		Assert.assertEquals("fdaugan", userLdap.getId());
		Assert.assertEquals("Fabrice", userLdap.getFirstName());
		Assert.assertEquals("Daugan", userLdap.getLastName());
		Assert.assertEquals("gfi", userLdap.getCompany());
		Assert.assertEquals("fabrice.daugan@sample.com", userLdap.getMails().get(0));
		Assert.assertEquals(1, userLdap.getGroups().size());
		Assert.assertEquals("Hub Paris", userLdap.getGroups().iterator().next());
	}

	@Test(expected = ValidationJsonException.class)
	public void findByIdNotExists() {
		resource.findById("any");
	}

	@Test(expected = ValidationJsonException.class)
	public void findByIdNotManagedUser() {
		initSpringSecurityContext("any");
		resource.findById("fdaugan");
	}

	/**
	 * Show users inside the company "ing" (or sub company), and members of group "dig rha", and matching to criteria
	 * "iRsT"
	 */
	@Test
	public void findAllAllFiltersAllRights() {

		final TableItem<UserLdapVo> tableItem = resource.findAll("ing", "dig rha", "iRsT", newUriInfoAsc("id"));
		Assert.assertEquals(2, tableItem.getRecordsTotal());
		Assert.assertEquals(2, tableItem.getRecordsFiltered());

		// Check the users
		final UserLdapVo userLdap = tableItem.getData().get(0);
		Assert.assertEquals("fdoe2", userLdap.getId());
		Assert.assertEquals("jdoe5", tableItem.getData().get(1).getId());

		// Check the other attributes
		Assert.assertEquals("ing", userLdap.getCompany());
		Assert.assertEquals("First2", userLdap.getFirstName());
		Assert.assertEquals("Doe2", userLdap.getLastName());
		Assert.assertEquals("first2.doe2@ing.fr", userLdap.getMails().get(0));
		Assert.assertTrue(userLdap.isManaged());
		final List<GroupLdapVo> groups = new ArrayList<>(userLdap.getGroups());
		Assert.assertEquals(2, groups.size());
		Assert.assertEquals("Biz Agency", groups.get(0).getName());
		Assert.assertEquals("DIG RHA", groups.get(1).getName());
	}

	@Test
	public void findAllAllFiltersReducesGroupsAscLogin() {
		initSpringSecurityContext("fdaugan");
		final TableItem<UserLdapVo> tableItem = resource.findAll("ing", "dig rha", "iRsT", newUriInfoAsc("id"));
		Assert.assertEquals(2, tableItem.getRecordsTotal());
		Assert.assertEquals(2, tableItem.getRecordsFiltered());

		// Check the users
		Assert.assertEquals("fdoe2", tableItem.getData().get(0).getId());
		Assert.assertEquals("jdoe5", tableItem.getData().get(1).getId());

		// Check the other attributes
		Assert.assertEquals("ing", tableItem.getData().get(0).getCompany());
		Assert.assertEquals("First2", tableItem.getData().get(0).getFirstName());
		Assert.assertEquals("Doe2", tableItem.getData().get(0).getLastName());
		Assert.assertEquals("first2.doe2@ing.fr", tableItem.getData().get(0).getMails().get(0));
		final List<GroupLdapVo> groups = new ArrayList<>(tableItem.getData().get(0).getGroups());
		Assert.assertEquals(2, groups.size());
		Assert.assertEquals("Biz Agency", groups.get(0).getName());
		Assert.assertEquals("DIG RHA", groups.get(1).getName());
	}

	@Test
	public void findAllNotSecure() {
		initSpringSecurityContext("fdaugan");
		final List<UserLdap> tableItem = resource.findAllNotSecure("ing", "dig rha");
		Assert.assertEquals(4, tableItem.size());

		// Check the users
		Assert.assertEquals("fdoe2", tableItem.get(0).getId());
		Assert.assertEquals("jdoe4", tableItem.get(1).getId());
		Assert.assertEquals("jdoe5", tableItem.get(2).getId());

		// Check the other attributes
		Assert.assertEquals("ing", tableItem.get(0).getCompany());
		Assert.assertEquals("First2", tableItem.get(0).getFirstName());
		Assert.assertEquals("Doe2", tableItem.get(0).getLastName());
		Assert.assertEquals("first2.doe2@ing.fr", tableItem.get(0).getMails().get(0));
		Assert.assertEquals(2, tableItem.get(0).getGroups().size());
		Assert.assertTrue(tableItem.get(0).getGroups().contains("biz agency"));
		Assert.assertTrue(tableItem.get(0).getGroups().contains("dig rha"));
	}

	@Test
	public void findAllDefaultDescFirstName() {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "5");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add(DataTableAttributes.START, "6");
		uriInfo.getQueryParameters().add("columns[2][data]", "firstName");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "desc");

		initSpringSecurityContext("fdaugan");
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, "e", uriInfo);
		Assert.assertEquals(13, tableItem.getRecordsTotal());
		Assert.assertEquals(13, tableItem.getRecordsFiltered());
		Assert.assertEquals(5, tableItem.getData().size());

		// Check the users

		// My company
		// [SimpleUser(id=jdoe4), SimpleUser(id=hdurant), SimpleUser(id=fdoe2), SimpleUser(id=fdauganb)]
		Assert.assertEquals("jdoe4", tableItem.getData().get(0).getId());
		Assert.assertEquals("hdurant", tableItem.getData().get(1).getId());
		Assert.assertEquals("fdoe2", tableItem.getData().get(3).getId());

		// Not my company, brought by delegation
		Assert.assertEquals("jdoe5", tableItem.getData().get(2).getId()); //
	}

	@Test
	public void findAllDefaultDescMail() {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "5");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add(DataTableAttributes.START, "2");
		uriInfo.getQueryParameters().add("columns[2][data]", "mail");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "desc");

		initSpringSecurityContext("fdaugan");
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, "@sample.com", uriInfo);
		Assert.assertEquals(6, tableItem.getRecordsTotal());
		Assert.assertEquals(6, tableItem.getRecordsFiltered());
		Assert.assertEquals(5, tableItem.getData().size());

		// Check the users
		Assert.assertEquals("fdaugan", tableItem.getData().get(1).getId());
	}

	/**
	 * One delegation to members of group "gfi-gstack" to see the company "ing"
	 */
	@Test
	public void findAllUsingDelegateReceiverGroup() {
		initSpringSecurityContext("alongchu");
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, null, newUriInfoAsc("id"));

		// Counts : 8 from ing, + 7 from the same company
		Assert.assertEquals(15, tableItem.getRecordsTotal());
		Assert.assertEquals(15, tableItem.getRecordsFiltered());
		Assert.assertEquals(15, tableItem.getData().size());

		// Check the users
		Assert.assertEquals("alongchu", tableItem.getData().get(0).getId());
		Assert.assertFalse(tableItem.getData().get(0).isManaged());

		// Check the groups
		Assert.assertEquals(1, tableItem.getData().get(0).getGroups().size());
		Assert.assertEquals("gfi-gStack", tableItem.getData().get(0).getGroups().get(0).getName());
	}

	/**
	 * No delegation for any group, but only for a company. So see only users within these company : ing(5) + socygan(1)
	 */
	@Test
	public void findAllForMyCompany() {
		initSpringSecurityContext("assist");
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, null, newUriInfoAsc("id"));
		Assert.assertEquals(9, tableItem.getRecordsTotal());
		Assert.assertEquals(9, tableItem.getRecordsFiltered());
		Assert.assertEquals(9, tableItem.getData().size());

		// Check the users
		Assert.assertEquals("fdoe2", tableItem.getData().get(0).getId());
		Assert.assertTrue(tableItem.getData().get(0).isManaged());

		// Check the groups
		Assert.assertEquals(0, tableItem.getData().get(0).getGroups().size());
	}

	/**
	 * No delegation for any group, but only for a company. So see only users within this company : ing(5)
	 */
	@Test
	public void findAllForMyCompanyFilter() {
		initSpringSecurityContext("assist");

		final TableItem<UserLdapVo> tableItem = resource.findAll("ing", null, null, newUriInfoAsc("id"));
		Assert.assertEquals(8, tableItem.getRecordsTotal());
		Assert.assertEquals(8, tableItem.getRecordsFiltered());
		Assert.assertEquals(8, tableItem.getData().size());

		// Check the users
		Assert.assertEquals("fdoe2", tableItem.getData().get(0).getId());
		Assert.assertTrue(tableItem.getData().get(0).isManaged());

		// Check the groups
		Assert.assertEquals(0, tableItem.getData().get(0).getGroups().size());
	}

	/**
	 * Whatever the managed company, if the current user sees a group, can search any user even in a different company
	 * this user can manage. <br>
	 */
	@Test
	public void findAllForMyGroup() {
		initSpringSecurityContext("mmartin");
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, "dig as", null, newUriInfoAsc("id"));

		// 4 users from delegate and 1 from my company
		Assert.assertEquals(5, tableItem.getRecordsTotal());
		Assert.assertEquals(5, tableItem.getRecordsFiltered());
		Assert.assertEquals(5, tableItem.getData().size());

		// Check the users (from delegate)
		Assert.assertEquals("fdoe2", tableItem.getData().get(0).getId());
		Assert.assertTrue(tableItem.getData().get(0).isManaged());
	}

	/**
	 * Whatever the managed company, if the current user sees a group, then he can search any user even in a different
	 * company
	 * this user can manage. <br>
	 */
	@Test
	public void findAllForMySubGroup() {
		initSpringSecurityContext("mmartin");
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, "biz agency", "fdoe2", newUriInfoAsc("id"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());

		// Check the users
		Assert.assertEquals("fdoe2", tableItem.getData().get(0).getId());
		Assert.assertTrue(tableItem.getData().get(0).isManaged());

		// Check the groups
		// "Biz Agency" is visible since "mmartin" is in the parent group "
		Assert.assertEquals(2, tableItem.getData().get(0).getGroups().size());
		Assert.assertEquals("Biz Agency", tableItem.getData().get(0).getGroups().get(0).getName());
		Assert.assertTrue(tableItem.getData().get(0).getGroups().get(0).isManaged());
		Assert.assertEquals("DIG RHA", tableItem.getData().get(0).getGroups().get(1).getName());
		Assert.assertFalse(tableItem.getData().get(0).getGroups().get(1).isManaged());
	}

	@Test
	public void findAllFullAscCompany() {
		initSpringSecurityContext("fdaugan");
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, null, newUriInfoAsc("company"));

		// 8 from delegate, 7 from my company
		Assert.assertEquals(15, tableItem.getRecordsTotal());
		Assert.assertEquals(15, tableItem.getRecordsFiltered());
		Assert.assertEquals(15, tableItem.getData().size());

		// Check the users
		Assert.assertEquals("fdoe2", tableItem.getData().get(7).getId());
	}

	@Test
	public void findAllFullDescCompany() {
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, null, newUriInfoDesc("company"));
		Assert.assertEquals(16, tableItem.getRecordsTotal());
		Assert.assertEquals(16, tableItem.getRecordsFiltered());
		Assert.assertEquals(16, tableItem.getData().size());

		// Check the users
		Assert.assertEquals("flast0", tableItem.getData().get(0).getId());
		Assert.assertEquals("socygan", tableItem.getData().get(0).getCompany());
		Assert.assertEquals("fdaugan", tableItem.getData().get(14).getId());
		Assert.assertEquals("gfi", tableItem.getData().get(14).getCompany());
	}

	@Test
	public void findAllFullAscLastName() {
		initSpringSecurityContext("fdaugan");
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, null, newUriInfoAsc("lastName"));

		// 8 from delegate, 7 from my company
		Assert.assertEquals(15, tableItem.getRecordsTotal());
		Assert.assertEquals(15, tableItem.getRecordsFiltered());
		Assert.assertEquals(15, tableItem.getData().size());

		// Check the users
		Assert.assertEquals("fdoe2", tableItem.getData().get(3).getId());
	}

	@Test
	public void findAllMemberDifferentCase() {
		final TableItem<UserLdapVo> tableItem = resource.findAll("GfI", "ProductioN", "mmarTIN", newUriInfoAsc("lastName"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());

		// Check the users
		Assert.assertEquals("mmartin", tableItem.getData().get(0).getId());
	}

	/**
	 * No available delegate for the current user -> 0
	 */
	@Test
	public void findAllNoRight() {
		initSpringSecurityContext("any");

		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, null, newUriInfoAsc("id"));
		Assert.assertEquals(0, tableItem.getRecordsTotal());
		Assert.assertEquals(0, tableItem.getRecordsFiltered());
		Assert.assertEquals(0, tableItem.getData().size());
	}

	/**
	 * Whatever the managed company, if the current user sees a group, can search any user even in a different company
	 * this user can manage. <br>
	 */
	@Test
	public void findAllNoWrite() {
		initSpringSecurityContext("mlavoine");
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, "fdoe2", newUriInfoAsc("id"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());

		// Check the users
		Assert.assertEquals("fdoe2", tableItem.getData().get(0).getId());
		Assert.assertFalse(tableItem.getData().get(0).isManaged());

		// Check the groups
		Assert.assertEquals(1, tableItem.getData().get(0).getGroups().size());
		Assert.assertEquals("Biz Agency", tableItem.getData().get(0).getGroups().get(0).getName());
		Assert.assertFalse(tableItem.getData().get(0).getGroups().get(0).isManaged());
	}

	@Test
	public void findAllNotExistingGroup() {
		initSpringSecurityContext("fdaugan");
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, "any", null, newUriInfoAsc("id"));
		Assert.assertEquals(0, tableItem.getRecordsTotal());
		Assert.assertEquals(0, tableItem.getRecordsFiltered());
		Assert.assertEquals(0, tableItem.getData().size());
	}

	@Test
	public void zcreateUser() {
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flasta");
		user.setFirstName("FirstA ");
		user.setLastName(" LASTA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rHA");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.create(user);

		// Check the result, using the cache
		checkResult(resource.findAll(null, null, "flasta", newUriInfoAsc("id")));

		// Check the result, using a fresh new cache
		CacheManager.getInstance().getCache("ldap").removeAll();
		checkResult(resource.findAll(null, null, "flasta", newUriInfoAsc("id")));

		// Restore the state, delete this new user
		resource.delete("flasta");
	}

	@Test
	public void createUserAlreadyExists() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("id", "already-exist"));
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast1");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flast12@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.create(user);
	}

	@Test
	public void zcreateUserDelegateCompanyNotExist() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("company", BusinessException.KEY_UNKNOW_ID));
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flastc");
		user.setFirstName("FirstC");
		user.setLastName("LastC");
		user.setCompany("any");
		user.setMail("flastc@ing.com");
		initSpringSecurityContext("fdaugan");
		resource.create(user);
	}

	@Test
	public void zcreateUserNoDelegate() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("company", BusinessException.KEY_UNKNOW_ID));
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flastd");
		user.setFirstName("FirstD");
		user.setLastName("LastD");
		user.setCompany("ing");
		user.setMail("flastd@ing.com");
		initSpringSecurityContext("any");

		resource.create(user);
	}

	@Test
	public void zcreateUserNoDelegateCompany() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("company", BusinessException.KEY_UNKNOW_ID));
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flastc");
		user.setFirstName("FirstC");
		user.setLastName("LastC");
		user.setCompany("socygan");
		user.setMail("flastc@ing.com");
		initSpringSecurityContext("fdaugan");
		resource.create(user);
	}

	@Test
	public void zcreateUserNoDelegateGroup() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("groups", BusinessException.KEY_UNKNOW_ID));
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flastg");
		user.setFirstName("FirstG");
		user.setLastName("LastG");
		user.setCompany("ing");
		user.setMail("flastg@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig sud ouest");
		user.setGroups(groups);
		initSpringSecurityContext("someone");
		resource.create(user);
	}

	@Test
	public void deleteUserNoDelegateCompany() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("id", BusinessException.KEY_UNKNOW_ID));
		initSpringSecurityContext("mmartin");
		resource.delete("flast1");
	}

	@Test
	public void deleteLastMember() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("id", "last-member-of-group"));
		resource.delete("mmartin");
	}

	@Test
	public void deleteUserNoDelegateWriteCompany() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("id", BusinessException.KEY_UNKNOW_ID));
		initSpringSecurityContext("mtuyer");
		resource.delete("flast1");
	}

	@Test
	public void mergeUserNoChange() {
		final UserLdap userLdap2 = getUser().findById("flast1");
		Assert.assertNull(userLdap2.getDepartment());
		Assert.assertNull(userLdap2.getLocalId());

		resource.mergeUser(userLdap2, new UserLdap());
		Assert.assertNull(userLdap2.getDepartment());
		Assert.assertNull(userLdap2.getLocalId());
	}

	@Test
	public void mergeUser() {
		final UserLdap userLdap2 = getUser().findById("flast1");
		Assert.assertNull(userLdap2.getDepartment());
		Assert.assertNull(userLdap2.getLocalId());

		final UserLdap newUser = new UserLdap();
		newUser.setDepartment("any");
		newUser.setLocalId("some");
		resource.mergeUser(userLdap2, newUser);
		Assert.assertEquals("any", userLdap2.getDepartment());
		Assert.assertEquals("some", userLdap2.getLocalId());

		// Revert to previous state (null)
		resource.mergeUser(userLdap2, new UserLdap());
		Assert.assertNull(userLdap2.getDepartment());
		Assert.assertNull(userLdap2.getLocalId());
	}

	/**
	 * Update everything : attributes and mails
	 */
	@Test
	public void update() {
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast1");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(user);
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, "flast1", newUriInfoAsc("id"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());

		final UserLdapVo userLdap = tableItem.getData().get(0);
		Assert.assertEquals("flast1", userLdap.getId());
		Assert.assertEquals("Firsta", userLdap.getFirstName());
		Assert.assertEquals("Lasta", userLdap.getLastName());
		Assert.assertEquals("ing", userLdap.getCompany());
		Assert.assertEquals("flasta@ing.com", userLdap.getMails().get(0));
		Assert.assertEquals(1, userLdap.getGroups().size());
		Assert.assertEquals("DIG RHA", userLdap.getGroups().get(0).getName());

		// Rollback attributes
		user.setId("flast1");
		user.setFirstName("First1");
		user.setLastName("Last1");
		user.setCompany("ing");
		user.setMail("first1.last1@ing.fr");
		user.setGroups(null);
		resource.update(user);
	}

	@Test
	public void updateFirstName() {
		// First name change only
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("jlast3");
		user.setFirstName("John31");
		user.setLastName("Last3");
		user.setCompany("ing");
		user.setMail("john3.last3@ing.com");
		user.setGroups(null);
		initSpringSecurityContext("assist");
		resource.update(user);
		TableItem<UserLdapVo> tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());

		UserLdapVo userLdap = tableItem.getData().get(0);
		Assert.assertEquals("jlast3", userLdap.getId());
		Assert.assertEquals("John31", userLdap.getFirstName());
		Assert.assertEquals("Last3", userLdap.getLastName());
		Assert.assertEquals("ing", userLdap.getCompany());
		Assert.assertEquals("john3.last3@ing.com", userLdap.getMails().get(0));
		Assert.assertEquals(0, userLdap.getGroups().size());
		rollbackUser();
	}

	@Test
	public void updateLastName() {
		// Last name change only
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("jlast3");
		user.setFirstName("John31");
		user.setLastName("Last31");
		user.setCompany("ing");
		user.setMail("john3.last3@ing.com");
		user.setGroups(null);
		user.setGroups(null);
		resource.update(user);
		TableItem<UserLdapVo> tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());

		UserLdapVo userLdap = tableItem.getData().get(0);
		Assert.assertEquals("jlast3", userLdap.getId());
		Assert.assertEquals("John31", userLdap.getFirstName());
		Assert.assertEquals("Last31", userLdap.getLastName());
		Assert.assertEquals("ing", userLdap.getCompany());
		Assert.assertEquals("john3.last3@ing.com", userLdap.getMails().get(0));
		Assert.assertEquals(0, userLdap.getGroups().size());
		rollbackUser();
	}

	@Test
	public void updateMail() {
		// Mail change only
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("jlast3");
		user.setFirstName("John31");
		user.setLastName("Last31");
		user.setCompany("ing");
		user.setMail("john31.last31@ing.com");
		user.setGroups(null);
		resource.update(user);
		TableItem<UserLdapVo> tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());

		UserLdapVo userLdap = tableItem.getData().get(0);
		user.setGroups(null);
		Assert.assertEquals("jlast3", userLdap.getId());
		Assert.assertEquals("John31", userLdap.getFirstName());
		Assert.assertEquals("Last31", userLdap.getLastName());
		Assert.assertEquals("ing", userLdap.getCompany());
		Assert.assertEquals("john31.last31@ing.com", userLdap.getMails().get(0));
		Assert.assertEquals(0, userLdap.getGroups().size());
		rollbackUser();
	}

	private void rollbackUser() {
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("jlast3");
		user.setFirstName("John3");
		user.setLastName("Last3");
		user.setCompany("ing");
		user.setMail("john3.last3@ing.com");
		user.setGroups(null);
		initSpringSecurityContext("assist");
		resource.update(user);
		TableItem<UserLdapVo> tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());

		UserLdapVo userLdap = tableItem.getData().get(0);
		Assert.assertEquals("jlast3", userLdap.getId());
		Assert.assertEquals("John3", userLdap.getFirstName());
		Assert.assertEquals("Last3", userLdap.getLastName());
		Assert.assertEquals("ing", userLdap.getCompany());
		Assert.assertEquals("john3.last3@ing.com", userLdap.getMails().get(0));
		Assert.assertEquals(0, userLdap.getGroups().size());
	}

	private DirContextAdapter getContext(final String base, final String uid) {
		final AndFilter filter = new AndFilter();
		filter.and(new EqualsFilter("objectclass", "inetOrgPerson"));
		filter.and(new EqualsFilter("uid", uid));
		return getTemplate().search(base, filter.encode(), (Object ctx) -> (DirContextAdapter) ctx).get(0);
	}

	private DirContextAdapter getContext(final String uid) {
		return getContext("dc=sample,dc=com", uid);
	}

	@Test
	public void updateUserChangeCompanyAndBackAgain() {
		Assert.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());

		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast0");
		user.setFirstName("First0"); // Unchanged
		user.setLastName("Last0"); // Unchanged
		user.setCompany("ing"); // Previous is "socygan"
		user.setMail("first0.last0@socygan.fr"); // Unchanged
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		initSpringSecurityContext("assist");
		resource.update(user);

		// Check the new DN and company everywhere
		Assert.assertEquals("uid=flast0,ou=ing,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());
		Assert.assertEquals("ing", resource.findAll(null, null, "flast0", newUriInfo()).getData().get(0).getCompany());
		Assert.assertEquals("ing", getUser().findByIdNoCache("flast0").getCompany());
		Assert.assertEquals("ing", getUser().findById("flast0").getCompany());

		user.setCompany("socygan"); // Previous is "socygan"
		resource.update(user);

		// Check the old DN and company everywhere
		Assert.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());
		Assert.assertEquals("socygan", resource.findAll(null, null, "flast0", newUriInfo()).getData().get(0).getCompany());
		Assert.assertEquals("socygan", getUser().findByIdNoCache("flast0").getCompany());
		Assert.assertEquals("socygan", getUser().findById("flast0").getCompany());
	}

	@Test(expected = ValidationJsonException.class)
	public void updateUserChangeDepartmentNotVisible() {
		initSpringSecurityContext("assist");
		Assert.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());

		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast0");
		user.setFirstName("First0"); // Unchanged
		user.setLastName("Last0"); // Unchanged
		user.setCompany("socygan"); // Unchanged
		user.setDepartment("456987"); // Previous is null -> "DIG AS" (not visible)
		user.setMail("first0.last0@socygan.fr"); // Unchanged
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		resource.update(user);
	}

	@Test
	public void updateUserChangeDepartmentAndBackAgain() {
		Assert.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());

		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast0");
		user.setFirstName("First0"); // Unchanged
		user.setLastName("Last0"); // Unchanged
		user.setCompany("socygan"); // Unchanged
		user.setDepartment("456987"); // Previous is null -> "DIG AS"
		user.setMail("first0.last0@socygan.fr"); // Unchanged
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		resource.update(user);

		// Check the new DN and department and group everywhere
		Assert.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());
		Assert.assertEquals("456987", resource.findAll(null, null, "flast0", newUriInfo()).getData().get(0).getDepartment());
		Assert.assertEquals("456987", getUser().findByIdNoCache("flast0").getDepartment());
		Assert.assertEquals("456987", getUser().findById("flast0").getDepartment());
		Assert.assertTrue(getUser().findById("flast0").getGroups().contains("dig as"));
		Assert.assertTrue(getGroup().findByDepartment("456987").getMembers().contains("flast0"));
		Assert.assertEquals("DIG AS", getGroup().findByDepartment("456987").getName());
		Assert.assertTrue(getGroup().findById("dig as").getMembers().contains("flast0"));

		user.setDepartment(null); // Previous is "DIG AS"
		resource.update(user);

		// Check the old DN and department everywhere
		Assert.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());
		Assert.assertNull(resource.findAll(null, null, "flast0", newUriInfo()).getData().get(0).getDepartment());
		Assert.assertNull(getUser().findByIdNoCache("flast0").getDepartment());
		Assert.assertNull(getUser().findById("flast0").getDepartment());
		Assert.assertFalse(getUser().findById("flast0").getGroups().contains("dig as"));
		Assert.assertFalse(getGroup().findByDepartment("456987").getMembers().contains("flast0"));
		Assert.assertEquals("DIG AS", getGroup().findByDepartment("456987").getName());
		Assert.assertFalse(getGroup().findById("dig as").getMembers().contains("flast0"));
	}

	@Test
	public void updateUserChangeDepartmentNotExists() {
		Assert.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());

		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast0");
		user.setFirstName("First0"); // Unchanged
		user.setLastName("Last0"); // Unchanged
		user.setCompany("socygan"); // Unchanged
		user.setDepartment("any"); // Previous is null -> No linked group exists
		user.setMail("first0.last0@socygan.fr"); // Unchanged
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		initSpringSecurityContext("assist");
		resource.update(user);

		// Check the new DN and department and group everywhere
		Assert.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());
		Assert.assertEquals("any", resource.findAll(null, null, "flast0", newUriInfo()).getData().get(0).getDepartment());
		Assert.assertEquals("any", getUser().findByIdNoCache("flast0").getDepartment());
		Assert.assertEquals("any", getUser().findById("flast0").getDepartment());
		Assert.assertNull(getGroup().findByDepartment("any"));

		user.setDepartment(null); // Previous is "any"
		resource.update(user);

		// Check the old DN and department everywhere
		Assert.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());
		Assert.assertNull(resource.findAll(null, null, "flast0", newUriInfo()).getData().get(0).getDepartment());
		Assert.assertNull(getUser().findByIdNoCache("flast0").getDepartment());
	}

	@Test
	public void updateUserCompanyNotExists() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("company", BusinessException.KEY_UNKNOW_ID));
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast0");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("any");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(user);
	}

	@Test
	public void updateUserGroupNotExists() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("groups", BusinessException.KEY_UNKNOW_ID));
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast1");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("any");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(user);
	}

	@Test
	public void updateUserNoChange() {
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("jlast3");
		user.setFirstName("John3");
		user.setLastName("Last3");
		user.setCompany("ing");
		user.setMail("jlast3@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(user);
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());

		final UserLdapVo userLdap = tableItem.getData().get(0);
		Assert.assertEquals("jlast3", userLdap.getId());
		Assert.assertEquals("John3", userLdap.getFirstName());
		Assert.assertEquals("Last3", userLdap.getLastName());
		Assert.assertEquals("ing", userLdap.getCompany());
		Assert.assertEquals("jlast3@ing.com", userLdap.getMails().get(0));
		Assert.assertEquals(1, userLdap.getGroups().size());
		Assert.assertEquals("DIG RHA", userLdap.getGroups().get(0).getName());
	}

	@Test
	public void updateUserNoDelegate() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("company", BusinessException.KEY_UNKNOW_ID));
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast1");
		user.setFirstName("FirstW");
		user.setLastName("LastW");
		user.setCompany("ing");
		user.setMail("flastw@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("any");

		resource.update(user);
	}

	@Test
	public void updateUserReadOnly() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("groups", BusinessException.KEY_UNKNOW_ID));
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast0");
		user.setFirstName("First0");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.last0@socygan.fr");
		final List<String> groups = new ArrayList<>();
		groups.add("Biz Agency");
		user.setGroups(groups);
		initSpringSecurityContext("mlavoine");
		resource.update(user);
	}

	@Test
	public void updateUserNoDelegateCompany() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("company", BusinessException.KEY_UNKNOW_ID));
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast0");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("socygan");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		initSpringSecurityContext("any");

		resource.update(user);
	}

	@Test
	public void updateUserNoDelegateCompanyChangeFirstName() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("company", BusinessException.KEY_UNKNOW_ID));
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast0");
		user.setFirstName("FirstA");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.last0@socygan.fr");
		initSpringSecurityContext("fdaugan");
		resource.update(user);
	}

	@Test
	public void updateUserNoDelegateCompanyChangeMail() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("company", BusinessException.KEY_UNKNOW_ID));
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast0");
		user.setFirstName("First0");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.lastA@socygan.fr");
		initSpringSecurityContext("fdaugan");
		resource.update(user);
	}

	@Test
	public void updateUserNoDelegateCompanyNoChange() {
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast0");
		user.setFirstName("First0");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.last0@socygan.fr");
		initSpringSecurityContext("assist");
		resource.update(user);
	}

	@Test
	public void updateUserNoDelegateGroupForTarget() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("groups", BusinessException.KEY_UNKNOW_ID));
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast1");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig sud ouest"); // no right on this group
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(user);
	}

	@Test
	public void zupdateUserHadNoMail() {
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("jdoe5");
		user.setFirstName("John5");
		user.setLastName("Doe5");
		user.setCompany("ing");
		user.setMail("first5.last5@ing.fr");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(user);
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, "jdoe5", newUriInfoAsc("id"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());

		final UserLdapVo userLdap = tableItem.getData().get(0);
		Assert.assertEquals("jdoe5", userLdap.getId());
		Assert.assertEquals("John5", userLdap.getFirstName());
		Assert.assertEquals("Doe5", userLdap.getLastName());
		Assert.assertEquals("ing", userLdap.getCompany());
		Assert.assertEquals("first5.last5@ing.fr", userLdap.getMails().get(0));
		Assert.assertEquals(1, userLdap.getGroups().size());
		Assert.assertEquals("DIG RHA", userLdap.getGroups().get(0).getName());
	}

	@Test
	public void zupdateUserHasNoMail() {
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("jdoe5");
		user.setFirstName("John5");
		user.setLastName("Doe5");
		user.setCompany("ing");
		user.setMail(null);
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(user);
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, "jdoe5", newUriInfoAsc("id"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());

		final UserLdapVo userLdap = tableItem.getData().get(0);
		Assert.assertEquals("jdoe5", userLdap.getId());
		Assert.assertEquals("John5", userLdap.getFirstName());
		Assert.assertEquals("Doe5", userLdap.getLastName());
		Assert.assertEquals("ing", userLdap.getCompany());
		Assert.assertTrue(userLdap.getMails().isEmpty());
		Assert.assertEquals(1, userLdap.getGroups().size());
		Assert.assertEquals("DIG RHA", userLdap.getGroups().get(0).getName());
	}

	@Test
	public void zupdateUserNoPassword() {
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("jdoe4");
		user.setFirstName("John4");
		user.setLastName("Doe4");
		user.setCompany("ing");
		user.setMail("fohn4.doe4@ing.fr");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(user);
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, "jdoe4", newUriInfoAsc("id"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());

		final UserLdapVo userLdap = tableItem.getData().get(0);
		Assert.assertEquals("jdoe4", userLdap.getId());
		Assert.assertEquals("John4", userLdap.getFirstName());
		Assert.assertEquals("Doe4", userLdap.getLastName());
		Assert.assertEquals("ing", userLdap.getCompany());
		Assert.assertEquals("fohn4.doe4@ing.fr", userLdap.getMails().get(0));
		Assert.assertEquals(1, userLdap.getGroups().size());
		Assert.assertEquals("DIG RHA", userLdap.getGroups().get(0).getName());
	}

	@Test
	public void updateUserNotExists() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("id", BusinessException.KEY_UNKNOW_ID));
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("flast11");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		initSpringSecurityContext("assist");
		resource.update(user);
	}

	@Test
	public void zupdateUserRemoveGroup() {
		// Pre-condition
		initSpringSecurityContext("fdaugan");
		final TableItem<UserLdapVo> initialResult = resource.findAll(null, null, "fdoe2", newUriInfoAsc("id"));
		Assert.assertEquals(1, initialResult.getData().size());
		Assert.assertEquals(2, initialResult.getData().get(0).getGroups().size());
		Assert.assertEquals("Biz Agency", initialResult.getData().get(0).getGroups().get(0).getName());
		Assert.assertTrue(initialResult.getData().get(0).getGroups().get(0).isManaged());
		Assert.assertEquals("DIG RHA", initialResult.getData().get(0).getGroups().get(1).getName());
		Assert.assertTrue(initialResult.getData().get(0).getGroups().get(1).isManaged());

		// Remove group "Biz Agency"
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("fdoe2");
		user.setFirstName("First2");
		user.setLastName("Doe2");
		user.setCompany("ing");
		user.setMail("fdoe2@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("DIG RHA");
		user.setGroups(groups);
		resource.update(user);
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, "fdoe2", newUriInfoAsc("id"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());

		final UserLdapVo userLdap = tableItem.getData().get(0);
		Assert.assertEquals("fdoe2", userLdap.getId());
		Assert.assertEquals("First2", userLdap.getFirstName());
		Assert.assertEquals("Doe2", userLdap.getLastName());
		Assert.assertEquals("ing", userLdap.getCompany());
		Assert.assertEquals("fdoe2@ing.com", userLdap.getMails().get(0));
		Assert.assertEquals(1, userLdap.getGroups().size());
		Assert.assertEquals("DIG RHA", userLdap.getGroups().get(0).getName());

		// Remove all groups
		user.setGroups(null);
		resource.update(user);
		final TableItem<UserLdapVo> tableItemNoGroup = resource.findAll(null, null, "fdoe2", newUriInfoAsc("id"));
		Assert.assertEquals(1, tableItemNoGroup.getData().size());
		Assert.assertEquals(0, tableItemNoGroup.getData().get(0).getGroups().size());

	}

	/**
	 * Add a group to user having already some groups but not visible from the current user.
	 */
	@Test
	public void updateUserAddGroup() {
		// Pre condition, check the user "wuser", has not yet the group "DIG RHA" we want to be added by "fdaugan"
		initSpringSecurityContext("fdaugan");
		final TableItem<UserLdapVo> initialResultsFromUpdater = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assert.assertEquals(1, initialResultsFromUpdater.getRecordsTotal());
		Assert.assertEquals(1, initialResultsFromUpdater.getData().get(0).getGroups().size());
		Assert.assertEquals("Biz Agency Manager", initialResultsFromUpdater.getData().get(0).getGroups().get(0).getName());

		// Pre condition, check the user "wuser", has no group visible by "assist"
		initSpringSecurityContext("assist");
		final TableItem<UserLdapVo> assisteResult = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assert.assertEquals(1, assisteResult.getRecordsTotal());
		Assert.assertEquals(0, assisteResult.getData().get(0).getGroups().size());

		// Pre condition, check the user "wuser", "Biz Agency Manager" is not visible by "mtuyer"
		initSpringSecurityContext("mtuyer");
		final TableItem<UserLdapVo> usersFromOtherGroupManager = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assert.assertEquals(1, usersFromOtherGroupManager.getRecordsTotal());
		Assert.assertEquals(0, usersFromOtherGroupManager.getData().get(0).getGroups().size());

		// Add a new valid group "DIG RHA" to "wuser" by "fdaugan"
		initSpringSecurityContext("fdaugan");
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("wuser");
		user.setFirstName("William");
		user.setLastName("User");
		user.setCompany("ing");
		user.setMail("wuser.wuser@ing.fr");
		final List<String> groups = new ArrayList<>();
		groups.add("DIG RHA");
		groups.add("Biz Agency Manager");
		user.setGroups(groups);
		resource.update(user);

		// Check the group "DIG RHA" is added and
		final TableItem<UserLdapVo> tableItem = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals(1, tableItem.getRecordsFiltered());
		Assert.assertEquals(1, tableItem.getData().size());
		Assert.assertEquals(2, tableItem.getData().get(0).getGroups().size());
		Assert.assertEquals("Biz Agency Manager", tableItem.getData().get(0).getGroups().get(0).getName());
		Assert.assertEquals("DIG RHA", tableItem.getData().get(0).getGroups().get(1).getName());

		// Check the user "wuser", still has no group visible by "assist"
		initSpringSecurityContext("assist");
		final TableItem<UserLdapVo> assisteResult2 = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assert.assertEquals(1, assisteResult2.getRecordsTotal());
		Assert.assertEquals(0, assisteResult2.getData().get(0).getGroups().size());

		// Check the user "wuser", still has the group "DIG RHA" visible by "mtuyer"
		initSpringSecurityContext("mtuyer");
		final TableItem<UserLdapVo> usersFromOtherGroupManager2 = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assert.assertEquals(1, usersFromOtherGroupManager2.getRecordsTotal());
		Assert.assertEquals("DIG RHA", usersFromOtherGroupManager2.getData().get(0).getGroups().get(0).getName());

		// Restore the old state
		initSpringSecurityContext("fdaugan");
		final UserLdapEdition user2 = new UserLdapEdition();
		user2.setId("wuser");
		user2.setFirstName("William");
		user2.setLastName("User");
		user2.setCompany("ing");
		user2.setMail("wuser.wuser@ing.fr");
		final List<String> groups2 = new ArrayList<>();
		groups2.add("Biz Agency Manager");
		user.setGroups(groups2);
		resource.update(user);
		final TableItem<UserLdapVo> initialResultsFromUpdater2 = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assert.assertEquals(1, initialResultsFromUpdater2.getRecordsTotal());
		Assert.assertEquals(1, initialResultsFromUpdater2.getData().get(0).getGroups().size());
		Assert.assertEquals("Biz Agency Manager", initialResultsFromUpdater2.getData().get(0).getGroups().get(0).getName());
	}

	@Test
	public void zlockUnlockUser() {
		checkUnlockedBefore();
		resource.lock("aLongchu");
		check("gfi", "ou=gfi,ou=france,ou=people,dc=sample,dc=com", "LOCKED\\|[0-9]+\\|junit\\|\\|", this::assertLocked);

		// Another lock
		resource.lock("aLongchu");
		check("gfi", "ou=gfi,ou=france,ou=people,dc=sample,dc=com", "LOCKED\\|[0-9]+\\|junit\\|\\|", this::assertLocked);

		resource.unlock("aLongchu");
		checkUnlockedAfter();

		// Another unlock
		resource.unlock("aLongchu");
		checkUnlockedAfter();
	}

	@Test
	public void zisolateRestoreUser() {
		checkDnAndMember(checkUnlockedBefore(), "uid=alongchu,ou=gfi,ou=france,ou=people,dc=sample,dc=com");

		// Isolate
		resource.isolate("aLongchu");
		check("quarantine", "ou=quarantine,dc=sample,dc=com", "LOCKED\\|[0-9]+\\|junit\\|gfi\\|", this::assertLocked);

		// Isolate again
		resource.isolate("aLongchu");
		check("quarantine", "ou=quarantine,dc=sample,dc=com", "LOCKED\\|[0-9]+\\|junit\\|gfi\\|", this::assertLocked);

		// Lock the user (useless)
		resource.lock("aLongchu");
		check("quarantine", "ou=quarantine,dc=sample,dc=com", "LOCKED\\|[0-9]+\\|junit\\|gfi\\|", this::assertLocked);

		// Unlock the user (useless)
		resource.unlock("aLongchu");
		check("quarantine", "ou=quarantine,dc=sample,dc=com", "LOCKED\\|[0-9]+\\|junit\\|gfi\\|", this::assertLocked);

		checkDnAndMember(check("quarantine", "ou=quarantine,dc=sample,dc=com", "LOCKED\\|[0-9]+\\|junit\\|gfi\\|", userLdap -> {
			assertLocked(userLdap);
			Assert.assertEquals("gfi", userLdap.getIsolated());
		}), "uid=alongchu,ou=quarantine,dc=sample,dc=com");

		// Restore
		resource.restore("aLongchu");

		// Check the uniqueMember is restored for the related groups
		checkDnAndMember(checkUnlockedAfter(), "uid=alongchu,ou=gfi,ou=france,ou=people,dc=sample,dc=com");

		// Restore again
		resource.restore("aLongchu");

		// Check the uniqueMember is restored for the related groups
		checkDnAndMember(checkUnlockedAfter(), "uid=alongchu,ou=gfi,ou=france,ou=people,dc=sample,dc=com");
	}

	/**
	 * Check the DN and uniqueMember is updated for the related groups
	 */
	private void checkDnAndMember(final DirContextAdapter context, final String dn) {
		// Check the DN is restored
		Assert.assertEquals(dn, context.getDn().toString());

		// Check the uniqueMember is restored for the related groups
		checkMember(dn);
	}

	/**
	 * Check the uniqueMember is updated for the related groups
	 */
	private void checkMember(final String dn) {
		final AndFilter filter = new AndFilter();
		filter.and(new EqualsFilter("objectclass", "groupOfUniqueNames"));
		filter.and(new EqualsFilter("cn", "gfi-gStack"));
		final DirContextAdapter groupContext = getTemplate()
				.search("ou=gfi,ou=project,dc=sample,dc=com", filter.encode(), (Object ctx) -> (DirContextAdapter) ctx).get(0);
		final String[] members = groupContext.getStringAttributes("uniqueMember");
		Assert.assertEquals(1, members.length);
		Assert.assertEquals(dn, members[0]);
	}

	private DirContextAdapter checkUnlockedBefore() {
		initSpringSecurityContext(DEFAULT_USER);

		// Restore lock status from LDAP
		getUser().set(getUser().findById("alongchu"), "userPassword", "secret");
		getUser().set(getUser().findById("alongchu"), "employeeType", null);

		// Asserts
		final DirContextAdapter contextAdapter = checkUnlocked();
		Assert.assertNotNull(contextAdapter.getObjectAttribute("userPassword"));
		return contextAdapter;
	}

	private DirContextAdapter checkUnlockedAfter() {
		final DirContextAdapter context = checkUnlocked();
		Assert.assertNull(context.getObjectAttribute("userPassword"));
		return context;
	}

	private DirContextAdapter checkUnlocked() {
		assertUnlocked(resource.findAll("gfi", null, "alongchu", newUriInfo()).getData().get(0));
		assertUnlocked(getUser().findByIdNoCache("alongchu"));
		assertUnlocked(getUser().findById("alongchu"));
		Assert.assertTrue(getGroup().findAll().get("gfi-gstack").getMembers().contains("alongchu"));

		final DirContextAdapter result = getContext("alongchu");
		Assert.assertNull(result.getStringAttribute("employeeType"));
		return result;
	}

	private DirContextAdapter check(final String company, final String base, final String patternLocked, final Consumer<SimpleUserLdap> checker) {
		// Check the status at business layer
		checker.accept(resource.findAll(company, null, "alongchu", newUriInfo()).getData().get(0));
		checker.accept(resource.findById("alongchu"));

		// Check the status at cache layer
		Assert.assertTrue(getGroup().findAll().get("gfi-gstack").getMembers().contains("alongchu"));
		checker.accept(getUser().findByIdNoCache("alongchu"));

		// Check in the status in the LDAP
		final DirContextAdapter result = getContext(base, "alongchu");
		Assert.assertNull(result.getObjectAttribute("userPassword"));
		Assert.assertTrue(result.getStringAttribute("employeeType").matches(patternLocked)); // LOCKED|1473983178923|junit||
		return result;
	}

	private void assertLocked(final SimpleUserLdap userLdap) {
		Assert.assertNotNull(userLdap.getLocked());
		Assert.assertEquals("junit", userLdap.getLockedBy());
	}

	private void assertUnlocked(final SimpleUserLdap userLdap) {
		Assert.assertNull(userLdap.getLocked());
		Assert.assertNull(userLdap.getLockedBy());
		Assert.assertNull(userLdap.getIsolated());
	}

	@Test
	public void zzdeleteUser() {
		initSpringSecurityContext("assist");
		Assert.assertEquals(1, resource.findAll("ing", null, "jdoe5", newUriInfo()).getData().size());
		Assert.assertNotNull(getUser().findByIdNoCache("jdoE5"));
		Assert.assertTrue(getGroup().findAll().get("dig rha").getMembers().contains("jdoe5"));
		resource.delete("jDOE5");
		Assert.assertEquals(0, resource.findAll("ing", null, "jdoe5", newUriInfo()).getData().size());
		Assert.assertNull(getUser().findByIdNoCache("jdoe5"));
		Assert.assertFalse(getGroup().findAll().get("dig rha").getMembers().contains("jdoe5"));

		final AndFilter filter = new AndFilter();
		filter.and(new EqualsFilter("objectclass", "groupOfUniqueNames"));
		filter.and(new EqualsFilter("cn", "dig rha"));
		final List<DirContextAdapter> groups = getTemplate().search("ou=groups,dc=sample,dc=com", filter.encode(),
				(Object ctx) -> (DirContextAdapter) ctx);
		Assert.assertEquals(1, groups.size());
		final DirContextAdapter group = groups.get(0);
		final String[] stringAttributes = group.getStringAttributes("uniqueMember");
		Assert.assertFalse(stringAttributes.length == 0);
		for (final String memberDN : stringAttributes) {
			Assert.assertFalse(memberDN.startsWith("uid=jdoe5"));
		}

		// Restore the state, create back the user
		initSpringSecurityContext(DEFAULT_USER);
		final UserLdapEdition user = new UserLdapEdition();
		user.setId("jdoe5");
		user.setFirstName("First5");
		user.setLastName("Last5");
		user.setCompany("ing-internal");
		final List<String> groups2 = new ArrayList<>();
		groups2.add("DIG RHA");
		user.setGroups(groups2);
		resource.create(user);
	}

	/**
	 * Test user addition to a group this user is already member.
	 */
	@Test
	public void addUserToGroup() {
		// Pre condition
		Assert.assertTrue(resource.findById("wuser").getGroups().contains("Biz Agency Manager"));

		resource.addUserToGroup("wuser", "biz agency manager");

		// Post condition -> no change
		Assert.assertTrue(resource.findById("wuser").getGroups().contains("Biz Agency Manager"));
	}

	/**
	 * Test user addition to a group.
	 */
	@Test
	public void zaddRemoveUser() {
		// Pre condition
		Assert.assertFalse(resource.findById("wuser").getGroups().contains("DIG RHA"));
		Assert.assertFalse(getGroup().findById("dig rha").getMembers().contains("wuser"));

		resource.addUserToGroup("wuser", "dig rha");

		// Post condition
		Assert.assertTrue(resource.findById("wuser").getGroups().contains("DIG RHA"));
		Assert.assertTrue(getGroup().findById("dig rha").getMembers().contains("wuser"));

		resource.removeUser("wuser", "dig rha");

		// Post condition 2
		Assert.assertFalse(resource.findById("wuser").getGroups().contains("DIG RHA"));
		Assert.assertFalse(getGroup().findById("dig rha").getMembers().contains("wuser"));
	}

	@Test
	public void deleteUserLastInGroupCase() {
		thrown.expect(ValidationJsonException.class);
		initSpringSecurityContext("mmartin");
		Assert.assertEquals(1, resource.findAll(null, null, "wuser", newUriInfo()).getData().size());

		Assert.assertNotNull(getUser().findByIdNoCache("wuser"));
		Assert.assertTrue(getGroup().findAll().get("biz agency manager").getMembers().contains("wuser"));

		resource.delete("wuser");
	}

	@Test
	public void deleteUserNotExists() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("id", BusinessException.KEY_UNKNOW_ID));
		initSpringSecurityContext("assist");
		resource.delete("any");
	}

	@Test
	public void updateMembership() {
		final UserLdapRepository repository = new UserLdapRepository();
		repository.setGroupLdapRepository(Mockito.mock(GroupLdapRepository.class));
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		final UserLdap user = new UserLdap();
		final Collection<String> oldGroups = new ArrayList<>();
		user.setGroups(oldGroups);
		user.setId("flast1");
		user.setCompany("ing");
		repository.updateMembership(groups, user);
	}

	@Test
	public void isGrantedNotSameType() {
		final DelegateOrg delegate = new DelegateOrg();
		delegate.setType(DelegateType.GROUP);
		Assert.assertFalse(resource.isGrantedAccess(delegate, null, DelegateType.COMPANY, true));
	}

	@Test
	public void isGrantedSameTypeNoRight() {
		final DelegateOrg delegate = new DelegateOrg();
		delegate.setType(DelegateType.GROUP);
		Assert.assertFalse(resource.isGrantedAccess(delegate, null, DelegateType.GROUP, true));
	}

	@Test
	public void isGrantedSameTypeNotSameDn() {
		final DelegateOrg delegate = new DelegateOrg();
		delegate.setType(DelegateType.GROUP);
		Assert.assertFalse(resource.isGrantedAccess(delegate, null, DelegateType.GROUP, false));
	}

	@Test
	public void isGranted() {
		final DelegateOrg delegate = new DelegateOrg();
		delegate.setType(DelegateType.GROUP);
		delegate.setDn("rightdn");
		Assert.assertTrue(resource.isGrantedAccess(delegate, "rightdn", DelegateType.GROUP, false));
	}

	@Test
	public void isGrantedAsAdmin() {
		final DelegateOrg delegate = new DelegateOrg();
		delegate.setType(DelegateType.GROUP);
		delegate.setCanAdmin(true);
		delegate.setDn("rightdn");
		Assert.assertTrue(resource.isGrantedAccess(delegate, "rightdn", DelegateType.GROUP, true));
	}

	@Test
	public void isGrantedAsWriter() {
		final DelegateOrg delegate = new DelegateOrg();
		delegate.setType(DelegateType.GROUP);
		delegate.setCanWrite(true);
		delegate.setDn("rightdn");
		Assert.assertTrue(resource.isGrantedAccess(delegate, "rightdn", DelegateType.GROUP, true));
	}

	@Test
	public void convertUserRaw() {
		final UserLdap userLdap = getUser().toUser("jdoe5");
		checkRawUser(userLdap);
		Assert.assertNotNull(userLdap.getGroups());
		Assert.assertEquals(1, userLdap.getGroups().size());
	}

	@Test
	public void convertUserNotExist() {
		final UserLdap userLdap = getUser().toUser("any");
		Assert.assertNotNull(userLdap);
		Assert.assertEquals("any", userLdap.getId());
		Assert.assertNull(userLdap.getCompany());
		Assert.assertNull(userLdap.getGroups());
		Assert.assertNull(userLdap.getFirstName());
		Assert.assertNull(userLdap.getLastName());
		Assert.assertNull(userLdap.getMails());
	}

	private void checkRawUser(final SimpleUserLdap userLdap) {
		Assert.assertNotNull(userLdap);
		Assert.assertEquals("jdoe5", userLdap.getId());
		Assert.assertEquals("ing-internal", userLdap.getCompany());
		Assert.assertEquals("First5", userLdap.getFirstName());
		Assert.assertEquals("Last5", userLdap.getLastName());
		Assert.assertNotNull(userLdap.getMails());
	}

	/**
	 * Check a user can see all users from the same company
	 */
	@Test
	public void findAllMyCompany() {
		initSpringSecurityContext("mmartin");

		final TableItem<UserLdapVo> tableItem = resource.findAll("gfi", null, null, newUriInfoAsc("id"));

		// 7 users from company 'gfi', 0 from delegate
		Assert.assertEquals(7, tableItem.getRecordsTotal());
		Assert.assertEquals(7, tableItem.getRecordsFiltered());

		// Check the users
		Assert.assertEquals("alongchu", tableItem.getData().get(0).getId());
	}

	/**
	 * When the requested company does not exists, return an empty set.
	 */
	@Test
	public void findAllUnknowFilteredCompany() {
		final TableItem<UserLdapVo> tableItem = resource.findAll("any", null, null, newUriInfoAsc("id"));
		Assert.assertEquals(0, tableItem.getRecordsTotal());
		Assert.assertEquals(0, tableItem.getRecordsFiltered());
	}

	@Test
	public void setIamProviderForTest() {
		// There for test by other plugin/application
		new UserLdapResource().setIamProvider(Mockito.mock(IamProvider.class));
	}
}

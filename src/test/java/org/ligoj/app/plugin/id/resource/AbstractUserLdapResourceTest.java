package org.ligoj.app.plugin.id.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import javax.transaction.Transactional;
import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.iam.SimpleUserOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.plugin.id.ldap.resource.AbstractLdapTest;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test of {@link UserOrgResource}<br>
 * Delegate
 * <ul>
 * <li>user;type;name;write;admin;dn</li>
 * <li>fdaugan;group;dig rha;true;true;cn=DIG RHA,cn=DIG
 * AS,cn=DIG,ou=fonction,ou=groups</li>
 * <li>fdaugan;group;any;true;true;cn=any,ou=groups</li>
 * <li>fdaugan;company;ing;true;true;ou=ing,ou=external,ou=people</li>
 * <li>fdaugan;company;any;true;true;cn=any,ou=groups</li>
 * <li>fdaugan;tree;ou=tools;true;true;ou=tools</li>
 * <li>someone;company;ing;true;true;ou=ing,ou=external,ou=people</li>
 * <li>someone;company;any;true;true;cn=any,ou=groups</li>
 * <li>someone;group;dig rha;true;true;cn=DIG RHA,cn=DIG
 * AS,cn=DIG,ou=fonction,ou=groups</li>
 * <li>junit;tree;dc=sample,dc=com;true;true;dc=sample,dc=com</li>
 * <li>assist;company;socygan;true;true;ou=socygan,ou=external,ou=people</li>
 * <li>assist;company;ing;true;true;ou=ing,ou=external,ou=people</li>
 * <li>mmartin;group;dig sud ouest;true;true;cn=DIG Sud Ouest,cn=DIG
 * AS,cn=DIG,ou=fonction,ou=groups</li>
 * <li>mmartin;group;any;true;true;cn=any,ou=groups</li>
 * <li>mmartin;company;socygan;true;true;ou=socygan,ou=external,ou=people</li>
 * <li>mmartin;company;any;true;true;cn=any,ou=groups</li>
 * <li>mtuyer;tree;ou=fonction,ou=groups;true;true;ou=fonction,ou=groups</li>
 * <li>mtuyer;company;ing;false;true;ou=ing,ou=external,ou=people</li>
 * <li>mlavoine;tree;cn=Biz Agency,ou=tools;false;false;cn=Biz
 * Agency,ou=tools</li>
 * <li>gfi-gstack
 * (group);company;ing;false;false;ou=ing,ou=external,ou=people,dc=sample,dc=com</li>
 * <li>ing (company);group;business solution;false;false;cn=business
 * solution,ou=groups,dc=sample,dc=com</li>
 * </ul>
 * LDAP
 * <ul>
 * <li>ING : flast1(First1 Last1), fdoe2(First2 Doe2),jlast3(John3
 * Last3),jdoe4(John4 Doe4),jdoe5(First5 Last5)</li>
 * <li>socygan : flast0</li>
 * <li>DIG RHA : fdoe2,jlast3,jdoe4,jdoe5</li>
 * <li>DIG SUD OUEST : jlast3,pgenais</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public abstract class AbstractUserLdapResourceTest extends AbstractLdapTest {

	@Autowired
	protected UserOrgResource resource;

	@BeforeEach
	public void prepareData() throws IOException {
		persistEntities("csv", new Class[] { DelegateOrg.class }, StandardCharsets.UTF_8.name());
		cacheManager.getCache("ldap").clear();

		// Force the cache to be created
		getUser().findAll();
	}

	/**
	 * Check the result : expects one entry
	 */
	protected void checkResult(final TableItem<UserOrgVo> tableItem) {
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		final UserOrgVo userLdap = tableItem.getData().get(0);
		Assertions.assertEquals("flasta", userLdap.getId());
		Assertions.assertEquals("Firsta", userLdap.getFirstName());
		Assertions.assertEquals("Lasta", userLdap.getLastName());
		Assertions.assertEquals("ing", userLdap.getCompany());
		Assertions.assertEquals("flasta@ing.com", userLdap.getMails().get(0));
		Assertions.assertEquals(1, userLdap.getGroups().size());
		Assertions.assertEquals("DIG RHA", userLdap.getGroups().get(0).getName());
	}

	protected UriInfo newUriInfoAsc(final String ascProperty) {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "100");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add("columns[2][data]", ascProperty);
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "asc");
		return uriInfo;
	}

	protected UriInfo newUriInfoDesc(final String property) {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "100");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add("columns[2][data]", property);
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "desc");
		return uriInfo;
	}

	protected void findById(final UserOrg userLdap) {
		Assertions.assertNotNull(userLdap);
		Assertions.assertEquals("fdaugan", userLdap.getId());
		Assertions.assertEquals("Fabrice", userLdap.getFirstName());
		Assertions.assertEquals("Daugan", userLdap.getLastName());
		Assertions.assertEquals("gfi", userLdap.getCompany());
		Assertions.assertEquals("fabrice.daugan@sample.com", userLdap.getMails().get(0));
		Assertions.assertEquals(1, userLdap.getGroups().size());
		Assertions.assertEquals("Hub Paris", userLdap.getGroups().iterator().next());
	}

	protected void rollbackUser() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("jlast3");
		user.setFirstName("John3");
		user.setLastName("Last3");
		user.setCompany("ing");
		user.setMail("john3.last3@ing.com");
		user.setGroups(null);
		initSpringSecurityContext("assist");
		resource.update(user);
		TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		UserOrgVo userLdap = tableItem.getData().get(0);
		Assertions.assertEquals("jlast3", userLdap.getId());
		Assertions.assertEquals("John3", userLdap.getFirstName());
		Assertions.assertEquals("Last3", userLdap.getLastName());
		Assertions.assertEquals("ing", userLdap.getCompany());
		Assertions.assertEquals("john3.last3@ing.com", userLdap.getMails().get(0));
		Assertions.assertEquals(0, userLdap.getGroups().size());
	}

	protected DirContextAdapter getContext(final String base, final String uid) {
		final AndFilter filter = new AndFilter();
		filter.and(new EqualsFilter("objectclass", "inetOrgPerson"));
		filter.and(new EqualsFilter("uid", uid));
		return getTemplate().search(base, filter.encode(), (Object ctx) -> (DirContextAdapter) ctx).get(0);
	}

	protected DirContextAdapter getContext(final String uid) {
		return getContext("dc=sample,dc=com", uid);
	}

	/**
	 * Check the DN and uniqueMember is updated for the related groups
	 */
	protected void checkDnAndMember(final DirContextAdapter context, final String dn) {
		// Check the DN is restored
		Assertions.assertEquals(dn, context.getDn().toString());

		// Check the uniqueMember is restored for the related groups
		checkMember(dn);
	}

	/**
	 * Check the uniqueMember is updated for the related groups
	 */
	protected void checkMember(final String dn) {
		final AndFilter filter = new AndFilter();
		filter.and(new EqualsFilter("objectclass", "groupOfUniqueNames"));
		filter.and(new EqualsFilter("cn", "gfi-gStack"));
		final DirContextAdapter groupContext = getTemplate()
				.search("ou=gfi,ou=project,dc=sample,dc=com", filter.encode(), (Object ctx) -> (DirContextAdapter) ctx).get(0);
		final String[] members = groupContext.getStringAttributes("uniqueMember");
		Assertions.assertEquals(1, members.length);
		Assertions.assertEquals(dn, members[0]);
	}

	protected DirContextAdapter checkUnlockedBefore() {
		initSpringSecurityContext(DEFAULT_USER);

		// Restore lock status from LDAP
		getUser().set(getUser().findById("alongchu"), "userPassword", "secret");
		getUser().set(getUser().findById("alongchu"), "employeeType", null);

		// Asserts
		final DirContextAdapter contextAdapter = checkUnlocked();
		Assertions.assertNotNull(contextAdapter.getObjectAttribute("userPassword"));
		return contextAdapter;
	}

	protected DirContextAdapter checkUnlockedAfter() {
		final DirContextAdapter context = checkUnlocked();
		Assertions.assertNull(context.getObjectAttribute("userPassword"));
		return context;
	}

	protected DirContextAdapter checkUnlocked() {
		assertUnlocked(resource.findAll("gfi", null, "alongchu", newUriInfo()).getData().get(0));
		assertUnlocked(getUser().findByIdNoCache("alongchu"));
		assertUnlocked(getUser().findById("alongchu"));
		Assertions.assertTrue(getGroup().findAll().get("gfi-gstack").getMembers().contains("alongchu"));

		final DirContextAdapter result = getContext("alongchu");
		Assertions.assertNull(result.getStringAttribute("employeeType"));
		return result;
	}

	protected DirContextAdapter check(final String company, final String base, final String patternLocked,
			final Consumer<SimpleUserOrg> checker) {
		// Check the status at business layer
		checker.accept(resource.findAll(company, null, "alongchu", newUriInfo()).getData().get(0));
		checker.accept(resource.findById("alongchu"));

		// Check the status at cache layer
		Assertions.assertTrue(getGroup().findAll().get("gfi-gstack").getMembers().contains("alongchu"));
		checker.accept(getUser().findByIdNoCache("alongchu"));

		// Check in the status in the LDAP
		final DirContextAdapter result = getContext(base, "alongchu");
		Assertions.assertNull(result.getObjectAttribute("userPassword"));
		Assertions.assertTrue(result.getStringAttribute("employeeType").matches(patternLocked)); // LOCKED|1473983178923|junit||
		return result;
	}

	protected void assertLocked(final SimpleUserOrg userLdap) {
		Assertions.assertNotNull(userLdap.getLocked());
		Assertions.assertEquals("junit", userLdap.getLockedBy());
	}

	protected void assertUnlocked(final SimpleUserOrg userLdap) {
		Assertions.assertNull(userLdap.getLocked());
		Assertions.assertNull(userLdap.getLockedBy());
		Assertions.assertNull(userLdap.getIsolated());
	}

	protected void checkRawUser(final SimpleUserOrg userLdap) {
		Assertions.assertNotNull(userLdap);
		Assertions.assertEquals("jdoe5", userLdap.getId());
		Assertions.assertEquals("ing-internal", userLdap.getCompany());
		Assertions.assertEquals("First5", userLdap.getFirstName());
		Assertions.assertEquals("Last5", userLdap.getLastName());
		Assertions.assertNotNull(userLdap.getMails());
	}
}

package org.ligoj.app.ldap.resource;

import javax.transaction.Transactional;
import javax.ws.rs.core.UriInfo;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.api.CompanyOrg;
import org.ligoj.app.api.GroupOrg;
import org.ligoj.app.model.ContainerType;
import org.ligoj.app.plugin.id.dao.ContainerScopeRepository;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Test of {@link CompanyLdapResource}<br>
 * Delegate are :
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
public class CompanyLdapResourceTest extends AbstractContainerLdapResourceTest {

	@Autowired
	private CompanyLdapResource resource;

	@Autowired
	private ContainerScopeRepository containerTypeLdapRepository;

	/**
	 * Check managed companies is filtered against available groups.
	 */
	@Test
	public void getContainers() {
		final TableItem<String> managed = resource.getContainers(newUriInfo());
		Assert.assertEquals(9, managed.getRecordsFiltered());
		Assert.assertEquals(9, managed.getRecordsTotal());
		Assert.assertEquals(9, managed.getData().size());
		Assert.assertEquals("external", managed.getData().get(0));
	}

	/**
	 * Check managed companies for write.
	 */
	@Test
	public void getContainersForWrite() {
		final TableItem<String> managed = resource.getContainersForWrite(newUriInfo());
		Assert.assertEquals(9, managed.getRecordsFiltered());
		Assert.assertEquals(9, managed.getRecordsTotal());
		Assert.assertEquals(9, managed.getData().size());
		Assert.assertEquals("external", managed.getData().get(0));
	}

	/**
	 * Check managed companies for write.
	 */
	@Test
	public void getContainersForWrite2() {
		initSpringSecurityContext("mtuyer");
		final TableItem<String> managed = resource.getContainersForWrite(newUriInfo());
		Assert.assertEquals(0, managed.getRecordsFiltered());
		Assert.assertEquals(0, managed.getRecordsTotal());
		Assert.assertEquals(0, managed.getData().size());
	}

	/**
	 * Check managed company is filtered against available groups for administration.
	 */
	@Test
	public void getContainersForAdmin() {
		initSpringSecurityContext("mlavoine");
		final TableItem<String> managed = resource.getContainersForAdmin(newUriInfo());
		Assert.assertEquals(0, managed.getRecordsFiltered());
		Assert.assertEquals(0, managed.getRecordsTotal());
		Assert.assertEquals(0, managed.getData().size());
	}

	@Test
	public void getContainersForAdmin2() {
		initSpringSecurityContext("fdaugan");
		final TableItem<String> managed = resource.getContainersForAdmin(newUriInfo());
		Assert.assertEquals(2, managed.getRecordsFiltered());
		Assert.assertEquals(2, managed.getRecordsTotal());
		Assert.assertEquals(2, managed.getData().size());
		Assert.assertEquals("ing", managed.getData().get(0));
		Assert.assertEquals("ing-internal", managed.getData().get(1));
	}

	@Test(expected = ValidationJsonException.class)
	public void findByIdExpectedNotExist() {
		initSpringSecurityContext("fdaugan");
		resource.findByIdExpected("any");
	}

	@Test(expected = ValidationJsonException.class)
	public void findByIdExpectedNotDelegate() {
		initSpringSecurityContext("fdaugan");
		resource.findByIdExpected("socygan");
	}

	@Test
	public void findByIdNotExists() {
		initSpringSecurityContext("fdaugan");
		Assert.assertNull(resource.findById("any"));
	}

	@Test
	public void findByIdExpected() {
		Assert.assertEquals("ou=ing,ou=external,ou=people,dc=sample,dc=com", resource.findByIdExpected("ing").getDn());
	}

	@Test
	public void findByIdExpectedMyCompany() {
		initSpringSecurityContext("mmartin");
		Assert.assertEquals("ou=gfi,ou=france,ou=people,dc=sample,dc=com", resource.findByIdExpected("gfi").getDn());
	}

	/**
	 * Check managed companies is filtered against available groups.
	 */
	@Test
	public void getContainersMyCompany() {
		initSpringSecurityContext("mmartin");
		final TableItem<String> managed = resource.getContainers(newUriInfo());
		Assert.assertEquals(4, managed.getRecordsFiltered());
		Assert.assertEquals(4, managed.getRecordsTotal());
		Assert.assertEquals(4, managed.getData().size());

		// gfi, ing, socygan
		Assert.assertEquals("gfi", managed.getData().get(0));
		Assert.assertEquals("ing", managed.getData().get(1));
		Assert.assertEquals("ing-internal", managed.getData().get(2));
		Assert.assertEquals("socygan", managed.getData().get(3));
	}

	@Test(expected = ValidationJsonException.class)
	public void createNoRight() {
		final ContainerScope typeLdap = containerTypeLdapRepository.findByName("France");
		final ContainerLdapEditionVo group = new ContainerLdapEditionVo();
		group.setName("New-Ax-1-z:Z 0");
		group.setType(typeLdap.getId());
		initSpringSecurityContext("mmartin");
		resource.create(group);
	}

	@Test
	public void deleteNoRight() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("company", "unknown-id"));
		initSpringSecurityContext("mmartin");
		resource.delete("gfi");
	}

	/**
	 * Container is locked itself
	 */
	@Test
	public void deleteLocked() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("company", "locked"));
		resource.delete("quarantine");
	}

	@Test
	public void deleteNotExists() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("company", "unknown-id"));
		resource.delete("any-any");
	}

	@Test
	public void deleteNotEmptyLeaf() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("company", "not-empty-company"));
		resource.delete("gfi");
	}

	@Test
	public void deleteNotEmptyParent() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("company", "not-empty-company"));
		resource.delete("france");
	}

	@Test(expected = ValidationJsonException.class)
	public void createAlreadyExists() {
		final ContainerScope typeLdap = containerTypeLdapRepository.findByName("France");
		final ContainerLdapEditionVo group = new ContainerLdapEditionVo();
		group.setName("orange");
		group.setType(typeLdap.getId());
		resource.create(group);
	}

	@Test(expected = ValidationJsonException.class)
	public void createInvalidType() {
		final ContainerScope typeLdap = containerTypeLdapRepository.findByName("Fonction");
		final ContainerLdapEditionVo company = new ContainerLdapEditionVo();
		company.setName("New-Ax-1-z:Z 0");
		company.setType(typeLdap.getId());
		resource.create(company);
	}

	/**
	 * Create, then delete an empty company.
	 */
	@Test
	public void createDelete() {
		createInternal();
		Assert.assertEquals(1, resource.findAll(newUriInfoSearch("New-Ax-1-z:Z 0")).getRecordsTotal());
		resource.delete("New-Ax-1-z:Z 0");
		Assert.assertEquals(0, resource.findAll(newUriInfoSearch("New-Ax-1-z:Z 0")).getRecordsTotal());
	}

	private void createInternal() {
		final ContainerScope typeLdap = containerTypeLdapRepository.findByName("France");
		final ContainerLdapEditionVo company = new ContainerLdapEditionVo();
		company.setName("New-Ax-1-z:Z 0");
		company.setType(typeLdap.getId());
		resource.create(company);

		// Check the creation from cache
		final TableItem<ContainerLdapCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "ew-Ax"));
		Assert.assertEquals(1, groups.getRecordsTotal());

		final ContainerLdapCountVo group0 = groups.getData().get(0);
		Assert.assertEquals("New-Ax-1-z:Z 0", group0.getName());
		Assert.assertEquals(0, group0.getCount());
		Assert.assertEquals(0, group0.getCountVisible());
		Assert.assertTrue(group0.isCanAdmin());
		Assert.assertTrue(group0.isCanWrite());
		Assert.assertEquals("France", group0.getType());

		// Check the creation from LDAP
		final CompanyOrg companyLdap = getCompany().findAllNoCache().get("new-ax-1-z:z 0");
		Assert.assertNotNull(companyLdap);
		Assert.assertEquals("new-ax-1-z:z 0", companyLdap.getId());
		Assert.assertEquals("New-Ax-1-z:Z 0", companyLdap.getName());
		Assert.assertEquals("ou=New-Ax-1-z:Z 0,ou=france,ou=people,dc=sample,dc=com", companyLdap.getDn());

		// For coverage
		Assert.assertEquals("new-ax-1-z:z 0".hashCode(), companyLdap.hashCode());
		Assert.assertEquals(companyLdap, companyLdap);
		Assert.assertEquals(companyLdap, new CompanyOrg("any", "New-AX-1-Z:Z 0"));
		Assert.assertNotEquals(companyLdap, new CompanyOrg("any", "some"));
		Assert.assertNotEquals(companyLdap, new GroupOrg("any", "some", null));
	}

	@Test
	public void findAllOnlyMyCompany() {
		initSpringSecurityContext("mmartin");
		final TableItem<ContainerLdapCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "gfi"));
		Assert.assertEquals(1, groups.getRecordsTotal());
		final ContainerLdapCountVo group0 = groups.getData().get(0);
		Assert.assertEquals("gfi", group0.getName());
		Assert.assertEquals(7, group0.getCount());
		Assert.assertEquals(7, group0.getCountVisible());
		Assert.assertFalse(group0.isCanAdmin());
		Assert.assertFalse(group0.isCanWrite());
		Assert.assertEquals("France", group0.getType());
		Assert.assertEquals("gfi", group0.getId());
		Assert.assertEquals(ContainerType.COMPANY, group0.getContainerType());
	}

	@Test
	public void findAllOnlyMyCompanyDesc() {
		initSpringSecurityContext("mmartin");
		final UriInfo uriInfo = newUriInfo("name", "desc");
		uriInfo.getQueryParameters().add(DataTableAttributes.SEARCH, "gfi");
		final TableItem<ContainerLdapCountVo> groups = resource.findAll(uriInfo);
		Assert.assertEquals(1, groups.getRecordsTotal());
		final ContainerLdapCountVo group0 = groups.getData().get(0);
		Assert.assertEquals("gfi", group0.getName());
		Assert.assertEquals(7, group0.getCount());
		Assert.assertEquals(7, group0.getCountVisible());
		Assert.assertFalse(group0.isCanAdmin());
		Assert.assertFalse(group0.isCanWrite());
		Assert.assertEquals("France", group0.getType());
		Assert.assertEquals("gfi", group0.getId());
		Assert.assertEquals(ContainerType.COMPANY, group0.getContainerType());
	}

	@Test
	public void findAllExternal() {
		initSpringSecurityContext("mtuyer");
		final TableItem<ContainerLdapCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "gfi"));
		Assert.assertEquals(0, groups.getRecordsTotal());
	}

	@Test
	public void findAllNoCriteriaNoType() {
		initSpringSecurityContext("mlavoine");
		containerTypeLdapRepository.deleteAllBy("name", "Root");
		final TableItem<ContainerLdapCountVo> groups = resource.findAll(newUriInfoAsc("name"));
		Assert.assertEquals(2, groups.getRecordsTotal());
		final ContainerLdapCountVo group0 = groups.getData().get(0);
		Assert.assertEquals("ing", group0.getName());
		Assert.assertEquals(7, group0.getCount());
		Assert.assertEquals(7, group0.getCountVisible());
		Assert.assertFalse(group0.isCanAdmin());
		Assert.assertFalse(group0.isCanWrite());
		Assert.assertNull(group0.getType());
		Assert.assertEquals("ing", group0.getId());
		Assert.assertEquals(ContainerType.COMPANY, group0.getContainerType());
		Assert.assertEquals("ing-internal", groups.getData().get(1).getName());
	}

	@Test
	public void findAll() {
		final TableItem<ContainerLdapCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "g"));
		Assert.assertEquals(5, groups.getRecordsTotal());
		final ContainerLdapCountVo group0 = groups.getData().get(0);
		Assert.assertEquals("gfi", group0.getName());
		Assert.assertEquals(7, group0.getCount());
		Assert.assertEquals(7, group0.getCountVisible());
		Assert.assertTrue(group0.isCanAdmin());
		Assert.assertTrue(group0.isCanWrite());
		Assert.assertFalse(group0.isLocked());
		Assert.assertEquals("France", group0.getType());
		Assert.assertEquals("gfi", group0.getId());
		Assert.assertEquals(ContainerType.COMPANY, group0.getContainerType());

		// No group type case
		final ContainerLdapCountVo group2 = groups.getData().get(2);
		Assert.assertEquals("ing-internal", group2.getName());
		Assert.assertEquals(1, group2.getCount());
		Assert.assertEquals(1, group2.getCountVisible());
		Assert.assertTrue(group2.isCanAdmin());
		Assert.assertTrue(group2.isCanWrite());
		Assert.assertTrue(group2.isLocked());
		Assert.assertEquals("Root", group2.getType());
		Assert.assertEquals("ing-internal", group2.getId());
		Assert.assertEquals(ContainerType.COMPANY, group2.getContainerType());
	}

	@Test
	public void findAllLocked() {
		final TableItem<ContainerLdapCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "quarantine"));
		Assert.assertEquals(1, groups.getRecordsTotal());
		final ContainerLdapCountVo group0 = groups.getData().get(0);
		Assert.assertEquals("quarantine", group0.getName());
		Assert.assertEquals(0, group0.getCount());
		Assert.assertEquals(0, group0.getCountVisible());
		Assert.assertTrue(group0.isCanAdmin());
		Assert.assertTrue(group0.isCanWrite());
		Assert.assertTrue(group0.isLocked());
		Assert.assertNull(group0.getType());
		Assert.assertEquals("quarantine", group0.getId());
		Assert.assertEquals(ContainerType.COMPANY, group0.getContainerType());
	}

	@Test
	public void isUserInternalCommpanyExternal() {
		initSpringSecurityContext("mlavoine");
		Assert.assertFalse(resource.isUserInternalCommpany());
	}

	@Test
	public void isUserInternalCommpanyAny() {
		initSpringSecurityContext("any");
		Assert.assertFalse(resource.isUserInternalCommpany());
	}

	@Test
	public void isUserInternalCommpany() {
		initSpringSecurityContext("mmartin");
		Assert.assertTrue(resource.isUserInternalCommpany());
	}
	
}

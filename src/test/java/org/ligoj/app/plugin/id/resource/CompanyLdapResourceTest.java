/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.resource;

import javax.transaction.Transactional;
import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.model.ContainerType;
import org.ligoj.app.plugin.id.dao.ContainerScopeRepository;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test of {@link CompanyResource}<br>
 * Delegate are :
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
public class CompanyLdapResourceTest extends AbstractContainerLdapResourceTest {

	@Autowired
	private CompanyResource resource;

	@Autowired
	private ContainerScopeRepository containerScopeRepository;

	/**
	 * Check managed companies is filtered against available groups.
	 */
	@Test
	public void getContainers() {
		final TableItem<String> managed = resource.getContainers(newUriInfo());
		Assertions.assertEquals(9, managed.getRecordsFiltered());
		Assertions.assertEquals(9, managed.getRecordsTotal());
		Assertions.assertEquals(9, managed.getData().size());
		Assertions.assertEquals("external", managed.getData().get(0));
	}

	/**
	 * Check managed companies for write.
	 */
	@Test
	public void getContainersForWrite() {
		final TableItem<String> managed = resource.getContainersForWrite(newUriInfo());
		Assertions.assertEquals(9, managed.getRecordsFiltered());
		Assertions.assertEquals(9, managed.getRecordsTotal());
		Assertions.assertEquals(9, managed.getData().size());
		Assertions.assertEquals("external", managed.getData().get(0));
	}

	/**
	 * Check managed companies for write.
	 */
	@Test
	public void getContainersForWrite2() {
		initSpringSecurityContext("mtuyer");
		final TableItem<String> managed = resource.getContainersForWrite(newUriInfo());
		Assertions.assertEquals(0, managed.getRecordsFiltered());
		Assertions.assertEquals(0, managed.getRecordsTotal());
		Assertions.assertEquals(0, managed.getData().size());
	}

	/**
	 * Check managed company is filtered against available groups for
	 * administration.
	 */
	@Test
	public void getContainersForAdmin() {
		initSpringSecurityContext("mlavoine");
		final TableItem<String> managed = resource.getContainersForAdmin(newUriInfo());
		Assertions.assertEquals(0, managed.getRecordsFiltered());
		Assertions.assertEquals(0, managed.getRecordsTotal());
		Assertions.assertEquals(0, managed.getData().size());
	}

	@Test
	public void getContainersForAdmin2() {
		initSpringSecurityContext("fdaugan");
		final TableItem<String> managed = resource.getContainersForAdmin(newUriInfo());
		Assertions.assertEquals(2, managed.getRecordsFiltered());
		Assertions.assertEquals(2, managed.getRecordsTotal());
		Assertions.assertEquals(2, managed.getData().size());
		Assertions.assertEquals("ing", managed.getData().get(0));
		Assertions.assertEquals("ing-internal", managed.getData().get(1));
	}

	@Test
	public void findByIdExpectedNotExist() {
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.findByIdExpected("any");
		}), "company", "unknown-id");
	}

	@Test
	public void findByIdExpectedNotDelegate() {
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.findByIdExpected("socygan");
		}), "company", "unknown-id");
	}

	@Test
	public void findByIdNotExists() {
		initSpringSecurityContext("fdaugan");
		Assertions.assertNull(resource.findById("any"));
	}

	@Test
	public void findByIdExpected() {
		Assertions.assertEquals("ou=ing,ou=external,ou=people,dc=sample,dc=com", resource.findByIdExpected("ing").getDn());
	}

	@Test
	public void findByIdExpectedMyCompany() {
		initSpringSecurityContext("mmartin");
		Assertions.assertEquals("ou=ligoj,ou=france,ou=people,dc=sample,dc=com", resource.findByIdExpected("ligoj").getDn());
	}

	/**
	 * Check managed companies is filtered against available groups.
	 */
	@Test
	public void getContainersMyCompany() {
		initSpringSecurityContext("mmartin");
		final TableItem<String> managed = resource.getContainers(newUriInfo());
		Assertions.assertEquals(4, managed.getRecordsFiltered());
		Assertions.assertEquals(4, managed.getRecordsTotal());
		Assertions.assertEquals(4, managed.getData().size());

		// ligoj, ing, socygan
		Assertions.assertEquals("ing", managed.getData().get(0));
		Assertions.assertEquals("ing-internal", managed.getData().get(1));
		Assertions.assertEquals("ligoj", managed.getData().get(2));
		Assertions.assertEquals("socygan", managed.getData().get(3));
	}

	@Test
	public void createNoWriteRight() {
		final ContainerScope typeLdap = containerScopeRepository.findByName("France");
		final ContainerEditionVo group = new ContainerEditionVo();
		group.setName("New-Ax-1-z:Z 0");
		group.setScope(typeLdap.getId());
		initSpringSecurityContext("mmartin");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(group);
		}), "name", "read-only");
	}

	@Test
	public void deleteNotVisible() {
		initSpringSecurityContext("mmartin");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete("ligoj");
		}), "company", "unknown-id");
	}

	/**
	 * Container is locked itself
	 */
	@Test
	public void deleteLocked() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete("quarantine");
		}), "company", "locked");
	}

	@Test
	public void deleteNotExists() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete("any-any");
		}), "company", "unknown-id");
	}

	@Test
	public void deleteNotEmptyLeaf() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete("ligoj");
		}), "company", "not-empty-company");
	}

	@Test
	public void deleteNotEmptyParent() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete("france");
		}), "company", "not-empty-company");
	}

	@Test
	public void createAlreadyExists() {
		final ContainerScope typeLdap = containerScopeRepository.findByName("France");
		final ContainerEditionVo group = new ContainerEditionVo();
		group.setName("orange");
		group.setScope(typeLdap.getId());
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(group);
		}), "name", "already-exist");
	}

	@Test
	public void createInvalidScope() {
		final ContainerScope typeLdap = containerScopeRepository.findByName("Fonction");
		final ContainerEditionVo company = new ContainerEditionVo();
		company.setName("New-Ax-1-z:Z 0");
		company.setScope(typeLdap.getId());
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(company);
		}), "type", "container-scope-match");
	}

	/**
	 * Create, then delete an empty company.
	 */
	@Test
	public void createDelete() {
		createInternal();
		Assertions.assertEquals(1, resource.findAll(newUriInfo("New-Ax-1-z:Z 0")).getRecordsTotal());
		resource.delete("New-Ax-1-z:Z 0");
		Assertions.assertEquals(0, resource.findAll(newUriInfo("New-Ax-1-z:Z 0")).getRecordsTotal());
	}

	private void createInternal() {
		final ContainerScope typeLdap = containerScopeRepository.findByName("France");
		final ContainerEditionVo company = new ContainerEditionVo();
		company.setName("New-Ax-1-z:Z 0");
		company.setScope(typeLdap.getId());
		resource.create(company);

		// Check the creation from cache
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "ew-Ax"));
		Assertions.assertEquals(1, groups.getRecordsTotal());

		final ContainerCountVo group0 = groups.getData().get(0);
		Assertions.assertEquals("New-Ax-1-z:Z 0", group0.getName());
		Assertions.assertEquals(0, group0.getCount());
		Assertions.assertEquals(0, group0.getCountVisible());
		Assertions.assertTrue(group0.isCanAdmin());
		Assertions.assertTrue(group0.isCanWrite());
		Assertions.assertEquals("France", group0.getScope());

		// Check the creation from LDAP
		final CompanyOrg companyLdap = getCompany().findAllNoCache().get("new-ax-1-z:z 0");
		Assertions.assertNotNull(companyLdap);
		Assertions.assertEquals("new-ax-1-z:z 0", companyLdap.getId());
		Assertions.assertEquals("New-Ax-1-z:Z 0", companyLdap.getName());
		Assertions.assertEquals("ou=New-Ax-1-z:Z 0,ou=france,ou=people,dc=sample,dc=com", companyLdap.getDn());

		// For coverage
		Assertions.assertEquals("new-ax-1-z:z 0".hashCode(), companyLdap.hashCode());
		Assertions.assertEquals(companyLdap, companyLdap);
		Assertions.assertEquals(companyLdap, new CompanyOrg("any", "New-AX-1-Z:Z 0"));
		Assertions.assertNotEquals(companyLdap, new CompanyOrg("any", "some"));
		Assertions.assertNotEquals(companyLdap, new GroupOrg("any", "some", null));
	}

	@Test
	public void findAllOnlyMyCompany() {
		initSpringSecurityContext("mmartin");
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "ligoj"));
		Assertions.assertEquals(1, groups.getRecordsTotal());
		final ContainerCountVo group0 = groups.getData().get(0);
		Assertions.assertEquals("ligoj", group0.getName());
		Assertions.assertEquals(7, group0.getCount());
		Assertions.assertEquals(7, group0.getCountVisible());
		Assertions.assertFalse(group0.isCanAdmin());
		Assertions.assertFalse(group0.isCanWrite());
		Assertions.assertEquals("France", group0.getScope());
		Assertions.assertEquals("ligoj", group0.getId());
		Assertions.assertEquals(ContainerType.COMPANY, group0.getContainerType());
	}

	@Test
	public void findAllOnlyMyCompanyDesc() {
		initSpringSecurityContext("mmartin");
		final UriInfo uriInfo = newUriInfo("name", "desc");
		uriInfo.getQueryParameters().add(DataTableAttributes.SEARCH, "ligoj");
		final TableItem<ContainerCountVo> groups = resource.findAll(uriInfo);
		Assertions.assertEquals(1, groups.getRecordsTotal());
		final ContainerCountVo group0 = groups.getData().get(0);
		Assertions.assertEquals("ligoj", group0.getName());
		Assertions.assertEquals(7, group0.getCount());
		Assertions.assertEquals(7, group0.getCountVisible());
		Assertions.assertFalse(group0.isCanAdmin());
		Assertions.assertFalse(group0.isCanWrite());
		Assertions.assertEquals("France", group0.getScope());
		Assertions.assertEquals("ligoj", group0.getId());
		Assertions.assertEquals(ContainerType.COMPANY, group0.getContainerType());
	}

	@Test
	public void findAllExternal() {
		initSpringSecurityContext("mtuyer");
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "ligoj"));
		Assertions.assertEquals(0, groups.getRecordsTotal());
	}

	@Test
	public void findAllNoCriteriaNoType() {
		initSpringSecurityContext("mlavoine");
		containerScopeRepository.deleteAllBy("name", "Root");
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAsc("name"));
		Assertions.assertEquals(2, groups.getRecordsTotal());
		final ContainerCountVo group0 = groups.getData().get(0);
		Assertions.assertEquals("ing", group0.getName());
		Assertions.assertEquals(7, group0.getCount());
		Assertions.assertEquals(7, group0.getCountVisible());
		Assertions.assertFalse(group0.isCanAdmin());
		Assertions.assertFalse(group0.isCanWrite());
		Assertions.assertNull(group0.getScope());
		Assertions.assertEquals("ing", group0.getId());
		Assertions.assertEquals(ContainerType.COMPANY, group0.getContainerType());
		Assertions.assertEquals("ing-internal", groups.getData().get(1).getName());
	}

	@Test
	public void findAll() {
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "g"));
		Assertions.assertEquals(5, groups.getRecordsTotal());
		final ContainerCountVo group0 = groups.getData().get(2);
		Assertions.assertEquals("ligoj", group0.getName());
		Assertions.assertEquals(7, group0.getCount());
		Assertions.assertEquals(7, group0.getCountVisible());
		Assertions.assertTrue(group0.isCanAdmin());
		Assertions.assertTrue(group0.isCanWrite());
		Assertions.assertFalse(group0.isLocked());
		Assertions.assertEquals("France", group0.getScope());
		Assertions.assertEquals("ligoj", group0.getId());
		Assertions.assertEquals(ContainerType.COMPANY, group0.getContainerType());

		// No group type case
		final ContainerCountVo group2 = groups.getData().get(1);
		Assertions.assertEquals("ing-internal", group2.getName());
		Assertions.assertEquals(1, group2.getCount());
		Assertions.assertEquals(1, group2.getCountVisible());
		Assertions.assertTrue(group2.isCanAdmin());
		Assertions.assertTrue(group2.isCanWrite());
		Assertions.assertTrue(group2.isLocked());
		Assertions.assertEquals("Root", group2.getScope());
		Assertions.assertEquals("ing-internal", group2.getId());
		Assertions.assertEquals(ContainerType.COMPANY, group2.getContainerType());
	}

	@Test
	public void findAllLocked() {
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "quarantine"));
		Assertions.assertEquals(1, groups.getRecordsTotal());
		final ContainerCountVo group0 = groups.getData().get(0);
		Assertions.assertEquals("quarantine", group0.getName());
		Assertions.assertEquals(0, group0.getCount());
		Assertions.assertEquals(0, group0.getCountVisible());
		Assertions.assertTrue(group0.isCanAdmin());
		Assertions.assertTrue(group0.isCanWrite());
		Assertions.assertTrue(group0.isLocked());
		Assertions.assertNull(group0.getScope());
		Assertions.assertEquals("quarantine", group0.getId());
		Assertions.assertEquals(ContainerType.COMPANY, group0.getContainerType());
	}

	@Test
	public void isUserInternalCommpanyExternal() {
		initSpringSecurityContext("mlavoine");
		Assertions.assertFalse(resource.isUserInternalCommpany());
	}

	@Test
	public void isUserInternalCommpanyAny() {
		initSpringSecurityContext("any");
		Assertions.assertFalse(resource.isUserInternalCommpany());
	}

	@Test
	public void isUserInternalCommpany() {
		initSpringSecurityContext("mmartin");
		Assertions.assertTrue(resource.isUserInternalCommpany());
	}

}

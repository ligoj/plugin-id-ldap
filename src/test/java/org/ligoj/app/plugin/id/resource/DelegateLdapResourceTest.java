/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.transaction.Transactional;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.core.UriInfo;

import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.iam.SimpleUser;
import org.ligoj.app.iam.dao.DelegateOrgRepository;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.iam.model.DelegateType;
import org.ligoj.app.iam.model.ReceiverType;
import org.ligoj.app.plugin.id.ldap.resource.AbstractLdapTest;
import org.ligoj.app.resource.delegate.DelegateOrgEditionVo;
import org.ligoj.app.resource.delegate.DelegateOrgLightVo;
import org.ligoj.app.resource.delegate.DelegateOrgResource;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link DelegateOrgResource} with LDAP back-end.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class DelegateLdapResourceTest extends AbstractLdapTest {

	@Autowired
	private DelegateOrgResource resource;

	@Autowired
	private DelegateOrgRepository repository;

	private DelegateOrg expected;

	@BeforeEach
	public void setUpEntities() throws IOException {
		persistEntities("csv", new Class[] { DelegateOrg.class }, StandardCharsets.UTF_8.name());
		em.flush();
		em.clear();
		expected = repository.findByName("dig rha");
		em.clear();
		cacheManager.getCache("id-ldap-configuration").clear();
		cacheManager.getCache("id-ldap-data").clear();
	}

	@Test
	public void findAllFewVisible() {
		// create a mock URI info with pagination informations
		final UriInfo uriInfo = newFindAllParameters();
		initSpringSecurityContext("someone");

		final TableItem<DelegateOrgLightVo> result = resource.findAll(uriInfo, null);

		// THERE IS DIFFERENCE FROM "plugin-id", broken delegates are dropped ('any' for sample)
		Assertions.assertEquals(2, result.getData().size());
		Assertions.assertEquals(2, result.getRecordsTotal());

		// someone;group;dig rha;false;false;cn=dig rha,cn=dig as,cn=dig,ou=fonction,ou=groups,dc=sample,dc=com
		DelegateOrgLightVo entity = result.getData().get(0);
		Assertions.assertEquals("DIG RHA", entity.getName());
		Assertions.assertEquals(DelegateType.GROUP, entity.getType());
		Assertions.assertNotNull(entity.getCreatedDate());
		Assertions.assertNotNull(entity.getLastModifiedDate());
		Assertions.assertEquals(DEFAULT_USER, entity.getCreatedBy().getId());
		Assertions.assertEquals(DEFAULT_USER, entity.getLastModifiedBy().getId());
		Assertions.assertEquals("someone", entity.getReceiver().getId());
		Assertions.assertEquals(ReceiverType.USER, entity.getReceiverType());
		Assertions.assertFalse(entity.isCanAdmin());
		Assertions.assertFalse(entity.isCanWrite());
		Assertions.assertFalse(entity.isManaged());

		// someone;company;ing;true;false;ou=ing,ou=external,ou=people,dc=sample,dc=com
	}

	private void assertAdmin(DelegateOrgLightVo entity) {
		Assertions.assertNotNull(entity.getCreatedDate());
		Assertions.assertNotNull(entity.getLastModifiedDate());
		Assertions.assertEquals(DEFAULT_USER, entity.getCreatedBy().getId());
		Assertions.assertEquals(DEFAULT_USER, entity.getLastModifiedBy().getId());
		Assertions.assertEquals("fdaugan", entity.getReceiver().getId());
		Assertions.assertEquals(ReceiverType.USER, entity.getReceiverType());
		Assertions.assertEquals("Fabrice", ((SimpleUser) entity.getReceiver()).getFirstName());
		Assertions.assertTrue(entity.isCanAdmin());
		Assertions.assertTrue(entity.isCanWrite());
		Assertions.assertTrue(entity.isManaged());
	}

	@Test
	public void findAllSelf() {
		// create a mock URI info with pagination informations
		final UriInfo uriInfo = newFindAllParameters();
		initSpringSecurityContext("mlavoine");
		final TableItem<DelegateOrgLightVo> result = resource.findAll(uriInfo, null);
		Assertions.assertEquals(3, result.getData().size());
		Assertions.assertEquals(3, result.getRecordsTotal());

		// mlavoine;tree;cn=Biz Agency,ou=tools;false;false;cn=Biz
		// Agency,ou=tools,dc=sample,dc=com
		final DelegateOrgLightVo entity = result.getData().get(0);
		Assertions.assertEquals("cn=biz agency,ou=tools,dc=sample,dc=com", entity.getName());
		Assertions.assertEquals(DelegateType.TREE, entity.getType());
		Assertions.assertNotNull(entity.getCreatedDate());
		Assertions.assertNotNull(entity.getLastModifiedDate());
		Assertions.assertEquals(DEFAULT_USER, entity.getCreatedBy().getId());
		Assertions.assertEquals(DEFAULT_USER, entity.getLastModifiedBy().getId());
		Assertions.assertEquals("mlavoine", entity.getReceiver().getId());
		Assertions.assertEquals(ReceiverType.USER, entity.getReceiverType());
		Assertions.assertFalse(entity.isCanAdmin());
		Assertions.assertFalse(entity.isCanWrite());
		Assertions.assertFalse(entity.isManaged());

		// mlavoine;company;ing;false;false;ou=ing,ou=external,ou=people,dc=sample,dc=com
		Assertions.assertEquals(DelegateType.COMPANY, result.getData().get(2).getType());
		Assertions.assertEquals("ing", result.getData().get(2).getName());

		// mlavoine;tree;cn=biz agency,ou=tools,dc=sample,dc=com
		Assertions.assertEquals(DelegateType.TREE, result.getData().get(0).getType());
		Assertions.assertEquals("cn=biz agency,ou=tools,dc=sample,dc=com", result.getData().get(0).getName());
	}

	@Test
	public void findAll() {
		// create a mock URI info with pagination informations
		final UriInfo uriInfo = newFindAllParameters();
		uriInfo.getQueryParameters().putSingle("length", "12");

		final TableItem<DelegateOrgLightVo> result = resource.findAll(uriInfo, null);
		Assertions.assertEquals(12, result.getData().size());
		Assertions.assertEquals(18, result.getRecordsTotal());

		// THERE IS DIFFERENCE FROM "plugin-id", broken delegates are dropped ('any' for sample)
		checkDelegateGroup2(result.getData().get(6));
		checkDelegateTree(result.getData().get(3));
	}

	/**
	 * A delegate visible by user "alongchu". This delegate add visibility of
	 * company "ing" for all mambers of "ligoj-gstack". And user "alongchu" is
	 * member of group "ligoj-gstack".
	 */
	@Test
	public void findAllReceiverGroup() {
		final UriInfo uriInfo = newFindAllParameters();
		initSpringSecurityContext("alongchu");
		final TableItem<DelegateOrgLightVo> result = resource.findAll(uriInfo, null);
		Assertions.assertEquals(1, result.getData().size());
		Assertions.assertEquals(1, result.getRecordsTotal());

		final DelegateOrgLightVo vo = result.getData().get(0);
		Assertions.assertEquals("ing", vo.getName());
		Assertions.assertEquals(DelegateType.COMPANY, vo.getType());
		Assertions.assertEquals("ligoj-gstack", vo.getReceiver().getId());
		Assertions.assertEquals(ReceiverType.GROUP, vo.getReceiverType());
	}

	@Test
	public void findAllReceiverCompany() {
		// create a mock URI info with pagination informations
		final UriInfo uriInfo = newFindAllParameters();
		initSpringSecurityContext("jdoe5");
		final TableItem<DelegateOrgLightVo> result = resource.findAll(uriInfo, null);
		Assertions.assertEquals(1, result.getData().size());
		Assertions.assertEquals(1, result.getRecordsTotal());

		final DelegateOrgLightVo entity = result.getData().get(0);
		Assertions.assertEquals("Business Solution", entity.getName());
		Assertions.assertEquals(DelegateType.GROUP, entity.getType());
		Assertions.assertEquals("ing", entity.getReceiver().getId());
		Assertions.assertEquals(ReceiverType.COMPANY, entity.getReceiverType());
	}

	@Test
	public void findAllGlobalSearch() {
		// create a mock URI info with pagination informations
		final UriInfo uriInfo = newFindAllParameters();
		uriInfo.getQueryParameters().add(DataTableAttributes.SEARCH, "dig");

		final TableItem<DelegateOrgLightVo> result = resource.findAll(uriInfo, null);
		Assertions.assertEquals(3, result.getData().size());

		checkDelegateGroup(result.getData().get(1));
	}

	@Test
	public void findAllGlobalSearchGroup() {
		// create a mock URI info with pagination informations
		final UriInfo uriInfo = newFindAllParameters();
		uriInfo.getQueryParameters().add(DataTableAttributes.SEARCH, "dig");

		final TableItem<DelegateOrgLightVo> result = resource.findAll(uriInfo, DelegateType.GROUP);
		Assertions.assertEquals(3, result.getData().size());

		checkDelegateGroup(result.getData().get(1));
	}

	@Test
	public void findAllGlobalSearchCompany() {
		// create a mock URI info with pagination informations
		final UriInfo uriInfo = newFindAllParameters();
		uriInfo.getQueryParameters().add(DataTableAttributes.SEARCH, "dig");

		final TableItem<DelegateOrgLightVo> result = resource.findAll(uriInfo, DelegateType.COMPANY);
		Assertions.assertEquals(0, result.getData().size());
	}

	private void checkDelegateGroup(final DelegateOrgLightVo entity) {
		Assertions.assertEquals("DIG RHA", entity.getName());
		Assertions.assertEquals(DelegateType.GROUP, entity.getType());
		assertAdmin(entity);
	}

	private void checkDelegateGroup2(final DelegateOrgLightVo entity) {
		Assertions.assertEquals("DIG AS", entity.getName());
		Assertions.assertEquals(DelegateType.GROUP, entity.getType());
		Assertions.assertNotNull(entity.getCreatedDate());
		Assertions.assertNotNull(entity.getLastModifiedDate());
		Assertions.assertEquals(DEFAULT_USER, entity.getCreatedBy().getId());
		Assertions.assertEquals(DEFAULT_USER, entity.getLastModifiedBy().getId());
		Assertions.assertEquals("mmartin", entity.getReceiver().getId());
		Assertions.assertEquals(ReceiverType.USER, entity.getReceiverType());
		Assertions.assertEquals("Marc", ((SimpleUser) entity.getReceiver()).getFirstName());
		Assertions.assertFalse(entity.isCanAdmin());
		Assertions.assertFalse(entity.isCanWrite());
		Assertions.assertTrue(entity.isManaged());
	}

	private void checkDelegateTree(final DelegateOrgLightVo entity) {
		Assertions.assertEquals("dc=sample,dc=com", entity.getName());
		Assertions.assertEquals(DelegateType.TREE, entity.getType());
		Assertions.assertNotNull(entity.getCreatedDate());
		Assertions.assertNotNull(entity.getLastModifiedDate());
		Assertions.assertEquals(DEFAULT_USER, entity.getCreatedBy().getId());
		Assertions.assertEquals(DEFAULT_USER, entity.getLastModifiedBy().getId());
		Assertions.assertEquals(DEFAULT_USER, entity.getReceiver().getId());
		Assertions.assertEquals(ReceiverType.USER, entity.getReceiverType());
		Assertions.assertTrue(entity.isCanAdmin());
		Assertions.assertTrue(entity.isCanWrite());
		Assertions.assertTrue(entity.isManaged());
	}

	private UriInfo newFindAllParameters() {
		final UriInfo uriInfo = Mockito.mock(UriInfo.class);
		Mockito.when(uriInfo.getQueryParameters()).thenReturn(new MetadataMap<>());
		uriInfo.getQueryParameters().add("draw", "1");
		uriInfo.getQueryParameters().add("start", "0");
		uriInfo.getQueryParameters().add("length", "10");
		uriInfo.getQueryParameters().add("columns[0][data]", "name");
		uriInfo.getQueryParameters().add("order[0][column]", "0");
		uriInfo.getQueryParameters().add("order[0][dir]", "asc");
		return uriInfo;
	}

	@Test
	public void createOnGroup() {
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setName("hUb Paris");
		vo.setType(DelegateType.GROUP);
		vo.setReceiver("fdaugan");
		final int id = resource.create(vo);
		em.flush();
		em.clear();

		final DelegateOrg entity = repository.findOneExpected(id);

		// Check the stored name is normalized
		Assertions.assertEquals("hub paris", entity.getName());
		Assertions.assertEquals("cn=hub paris,cn=hub france,cn=production,ou=branche,ou=groups,dc=sample,dc=com", entity.getDn());
		Assertions.assertNull(entity.getReceiverDn());
		Assertions.assertEquals(DelegateType.GROUP, entity.getType());
		Assertions.assertEquals(DEFAULT_USER, entity.getCreatedBy());
		Assertions.assertEquals("fdaugan", entity.getReceiver());
		Assertions.assertEquals(ReceiverType.USER, entity.getReceiverType());
		Assertions.assertFalse(entity.isCanAdmin());
		Assertions.assertFalse(entity.isCanWrite());
	}

	@Test
	public void createDelegateCompany() {
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setName("socygan");
		vo.setType(DelegateType.COMPANY);
		vo.setReceiver("fdaugan");
		vo.setCanAdmin(true);
		vo.setCanWrite(true);
		final int id = resource.create(vo);
		em.flush();
		em.clear();

		final DelegateOrg entity = repository.findOneExpected(id);
		Assertions.assertEquals("socygan", entity.getName());
		Assertions.assertEquals("ou=socygan,ou=external,ou=people,dc=sample,dc=com", entity.getDn());
		Assertions.assertNull(entity.getReceiverDn());
		Assertions.assertEquals(DelegateType.COMPANY, entity.getType());
		Assertions.assertEquals(DEFAULT_USER, entity.getCreatedBy());
		Assertions.assertEquals("fdaugan", entity.getReceiver());
		Assertions.assertEquals(ReceiverType.USER, entity.getReceiverType());
		Assertions.assertTrue(entity.isCanWrite());
		Assertions.assertTrue(entity.isCanAdmin());
	}

	@Test
	public void createDelegateCompanyReceiverCompany() {
		initSpringSecurityContext("mtuyer");

		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setName("InG");
		vo.setType(DelegateType.COMPANY);
		vo.setReceiver("ing");
		vo.setReceiverType(ReceiverType.COMPANY);
		vo.setCanAdmin(true);
		final int id = resource.create(vo);
		em.flush();
		em.clear();

		final DelegateOrg entity = repository.findOneExpected(id);
		Assertions.assertEquals("ing", entity.getName());
		Assertions.assertEquals("ou=ing,ou=external,ou=people,dc=sample,dc=com", entity.getDn());
		Assertions.assertEquals(DelegateType.COMPANY, entity.getType());
		Assertions.assertEquals("mtuyer", entity.getCreatedBy());
		Assertions.assertEquals("ing", entity.getReceiver());
		Assertions.assertEquals(ReceiverType.COMPANY, entity.getReceiverType());
		Assertions.assertEquals("ou=ing,ou=external,ou=people,dc=sample,dc=com", entity.getReceiverDn());
		Assertions.assertTrue(entity.isCanAdmin());
	}

	@Test
	public void createDelegateCompanyReceiverGroup() {
		initSpringSecurityContext("mtuyer");

		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setName("ing");
		vo.setType(DelegateType.COMPANY);
		vo.setReceiver("DIG");
		vo.setReceiverType(ReceiverType.GROUP);
		vo.setCanAdmin(true);
		final int id = resource.create(vo);
		em.flush();
		em.clear();

		final DelegateOrg entity = repository.findOneExpected(id);
		Assertions.assertEquals("ing", entity.getName());
		Assertions.assertEquals("ou=ing,ou=external,ou=people,dc=sample,dc=com", entity.getDn());
		Assertions.assertEquals(DelegateType.COMPANY, entity.getType());
		Assertions.assertEquals("mtuyer", entity.getCreatedBy());
		Assertions.assertEquals("dig", entity.getReceiver());
		Assertions.assertEquals(ReceiverType.GROUP, entity.getReceiverType());
		Assertions.assertEquals("cn=dig,ou=fonction,ou=groups,dc=sample,dc=com", entity.getReceiverDn());
		Assertions.assertTrue(entity.isCanAdmin());
	}

	@Test
	public void createOnTreePartialDn() {
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setName("cn=myDn");
		vo.setReceiver("fdaugan");
		vo.setType(DelegateType.TREE);
		Assertions.assertThrows(ForbiddenException.class, () -> {
			resource.create(vo);
		});
	}

	@Test
	public void createInvalidDn() {
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setName("cn=my,invalidDn,dc=sample,dc=com");
		vo.setName("myDn*Partial");
		vo.setReceiver("fdaugan");
		vo.setType(DelegateType.TREE);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(vo);
		}), "tree", "DistinguishName");
	}

	@Test
	public void createOnUnkownCompany() {
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setName("any");
		vo.setType(DelegateType.COMPANY);
		vo.setReceiver("fdaugan");
		Assertions.assertThrows(ForbiddenException.class, () -> {
			resource.create(vo);
		});
	}

	@Test
	public void createOnSubTree() {
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setName("cn=Any,dc=sample,dc=com");
		vo.setReceiver("fdaugan");
		vo.setType(DelegateType.TREE);
		final int id = resource.create(vo);
		em.flush();
		em.clear();

		final DelegateOrg entity = repository.findOneExpected(id);
		Assertions.assertEquals("-", entity.getName());
		Assertions.assertEquals("cn=any,dc=sample,dc=com", entity.getDn());
		Assertions.assertEquals(DelegateType.TREE, entity.getType());
		Assertions.assertEquals(DEFAULT_USER, entity.getCreatedBy());
		Assertions.assertEquals("fdaugan", entity.getReceiver());
		Assertions.assertEquals(ReceiverType.USER, entity.getReceiverType());
		Assertions.assertFalse(entity.isCanWrite());
		Assertions.assertFalse(entity.isCanWrite());
	}

	@Test
	public void createOnSubTreeInvalidDn() {
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setName("myDn,dc=sample,dc=com");
		vo.setReceiver("fdaugan");
		vo.setType(DelegateType.TREE);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(vo);
		}), "tree", "DistinguishName");
	}

	@Test
	public void updateForbiddenNotAdminDn() {
		initSpringSecurityContext("mlavoine");
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setId(expected.getId());
		vo.setName("Biz Agency");
		vo.setReceiver("mlavoine");
		vo.setType(DelegateType.GROUP);
		Assertions.assertThrows(ForbiddenException.class, () -> {
			resource.update(vo);
		});
	}

	@Test
	public void updateInvisibleDelegateUser() {
		initSpringSecurityContext("mlavoine");
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setId(expected.getId());
		vo.setName("Biz Agency");
		vo.setReceiver("any");
		vo.setType(DelegateType.GROUP);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.update(vo);
		}), "id", "unknown-id");
	}

	@Test
	public void updateInvisibleDelegateCompany() {
		initSpringSecurityContext("mtuyer");
		final int id = em.createQuery("SELECT id FROM DelegateOrg WHERE receiver=:user AND dn=:dn", Integer.class)
				.setParameter("user", "mtuyer").setParameter("dn", "ou=fonction,ou=groups,dc=sample,dc=com").getSingleResult();
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setId(id);
		vo.setName("socygan");
		vo.setReceiver("mtuyer");
		vo.setType(DelegateType.COMPANY);
		Assertions.assertThrows(ForbiddenException.class, () -> {
			resource.update(vo);
		});
	}

	@Test
	public void updateInvisibleReceiverUser() {
		initSpringSecurityContext("mtuyer");
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setId(expected.getId());
		vo.setName("ing");
		vo.setReceiver("fdaugan");
		vo.setType(DelegateType.COMPANY);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.update(vo);
		}), "id", "unknown-id");
	}

	@Test
	public void updateInvisibleReceiverCompany() {
		initSpringSecurityContext("mtuyer");
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setId(expected.getId());
		vo.setName("ing");
		vo.setReceiver("socygan");
		vo.setReceiverType(ReceiverType.COMPANY);
		vo.setType(DelegateType.COMPANY);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.update(vo);
		}), "company", "unknown-id");
	}

	@Test
	public void updateInvisibleReceiverGroup() {
		initSpringSecurityContext("mtuyer");
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setId(expected.getId());
		vo.setName("ing");
		vo.setReceiver("biz agency");
		vo.setReceiverType(ReceiverType.GROUP);
		vo.setType(DelegateType.COMPANY);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.update(vo);
		}), "group", "unknown-id");
	}

	@Test
	public void updateForbiddenInvalidDelegateType() {
		initSpringSecurityContext("mtuyer");
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setId(expected.getId());
		vo.setName("ing");
		vo.setReceiver("mtuyer");
		vo.setType(DelegateType.GROUP);
		Assertions.assertThrows(ForbiddenException.class, () -> {
			resource.update(vo);
		});
	}

	@Test
	public void updateForbiddenInvalidDelegateTree() {
		initSpringSecurityContext("mtuyer");
		final int id = em.createQuery("SELECT id FROM DelegateOrg WHERE receiver=:user AND dn=:dn", Integer.class)
				.setParameter("user", "mtuyer").setParameter("dn", "ou=fonction,ou=groups,dc=sample,dc=com").getSingleResult();
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setId(id);
		vo.setName("ou=z,ou=groups,dc=sample,dc=com");
		vo.setReceiver("mtuyer");
		vo.setType(DelegateType.TREE);
		Assertions.assertThrows(ForbiddenException.class, () -> {
			resource.update(vo);
		});
	}

	@Test
	public void updateType() {
		initSpringSecurityContext("mtuyer");
		final int id = em.createQuery("SELECT id FROM DelegateOrg WHERE receiver=:user AND type=:type", Integer.class)
				.setParameter("type", DelegateType.COMPANY).setParameter("user", "mtuyer").getSingleResult();
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setId(id);
		vo.setName("cn=any,ou=fonction,ou=groups,dc=sample,dc=com");
		vo.setReceiver("mtuyer");
		vo.setType(DelegateType.TREE);
		vo.setCanAdmin(true);
		vo.setCanWrite(false);
		resource.update(vo);
		em.flush();
		em.clear();

		final DelegateOrg entity = repository.findOne(id);
		Assertions.assertEquals("-", entity.getName());
		Assertions.assertEquals("cn=any,ou=fonction,ou=groups,dc=sample,dc=com", entity.getDn());
		Assertions.assertEquals(DelegateType.TREE, entity.getType());
		Assertions.assertEquals("mtuyer", entity.getReceiver());
		Assertions.assertEquals(ReceiverType.USER, entity.getReceiverType());
		Assertions.assertTrue(entity.isCanAdmin());
		Assertions.assertFalse(entity.isCanWrite());
	}

	/**
	 * Try to update a delegate does not exist
	 */
	@Test
	public void updateNotExist() {
		initSpringSecurityContext("mtuyer");
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setId(-5);
		vo.setName("ing");
		vo.setReceiver("mtuyer");
		vo.setType(DelegateType.COMPANY);
		Assertions.assertThrows(ObjectRetrievalFailureException.class, () -> {
			resource.update(vo);
		});
	}

	@Test
	public void updateToSubTree() {
		initSpringSecurityContext("mtuyer");
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setName("cn=any,ou=fonction,ou=groups,dc=sample,dc=com");
		vo.setReceiver("mtuyer");
		final DelegateOrg entity = updateNoChangeBase("mtuyer", vo);
		Assertions.assertTrue(entity.isCanAdmin());
		Assertions.assertFalse(entity.isCanWrite());
	}

	private DelegateOrg updateNoChangeBase(final String user, final DelegateOrgEditionVo vo) {
		final int id = em.createQuery("SELECT id FROM DelegateOrg WHERE receiver=:user AND dn=:dn", Integer.class)
				.setParameter("user", "mtuyer").setParameter("dn", "ou=fonction,ou=groups,dc=sample,dc=com").getSingleResult();
		vo.setId(id);
		vo.setType(DelegateType.TREE);
		vo.setCanAdmin(true);
		resource.update(vo);
		em.flush();
		em.clear();

		final DelegateOrg entity = repository.findOne(id);
		Assertions.assertEquals("-", entity.getName());
		Assertions.assertEquals(vo.getName().trim(), entity.getDn());
		Assertions.assertEquals(DelegateType.TREE, entity.getType());
		Assertions.assertEquals(user, entity.getReceiver());
		Assertions.assertEquals(ReceiverType.USER, entity.getReceiverType());
		return entity;
	}

	@Test
	public void updateNoChange() {
		initSpringSecurityContext("mtuyer");
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();

		// Add space that would be trimmed
		vo.setName("ou=fonction,ou=groups,dc=sample,dc=com  ");
		vo.setReceiver("mtuyer");
		vo.setCanWrite(true);
		final DelegateOrg entity = updateNoChangeBase("mtuyer", vo);
		Assertions.assertTrue(entity.isCanWrite());
		Assertions.assertTrue(entity.isCanAdmin());
	}

	@Test
	public void updateNoChangeFromAnother() {
		final DelegateOrgEditionVo vo = new DelegateOrgEditionVo();
		vo.setName("ou=fonction,ou=groups,dc=sample,dc=com");
		vo.setReceiver("fdaugan");
		vo.setCanWrite(true);
		final DelegateOrg entity = updateNoChangeBase("fdaugan", vo);
		Assertions.assertTrue(entity.isCanWrite());
		Assertions.assertTrue(entity.isCanAdmin());
	}

	@Test
	public void deleteFromBaseDn() {
		final long initCount = repository.count();
		em.clear();
		resource.delete(expected.getId());
		em.flush();
		em.clear();
		Assertions.assertEquals(initCount - 1, repository.count());
	}

	@Test
	public void deleteSubTreeGroup() {
		initSpringSecurityContext("fdaugan");
		final int id = em.createQuery("SELECT id FROM DelegateOrg WHERE receiver=:user AND name=:name", Integer.class)
				.setParameter("user", "someone").setParameter("name", "dig rha").getSingleResult();
		final long initCount = repository.count();
		em.clear();
		resource.delete(id);
		em.flush();
		em.clear();
		Assertions.assertEquals(initCount - 1, repository.count());
	}

	@Test
	public void delete() {
		final long initCount = repository.count();
		em.clear();
		resource.delete(expected.getId());
		em.flush();
		em.clear();
		Assertions.assertEquals(initCount - 1, repository.count());
	}

	@Test
	public void deleteNotAdmin() {
		initSpringSecurityContext("someone");
		final int id = em.createQuery("SELECT id FROM DelegateOrg WHERE receiver=:user AND name=:name", Integer.class)
				.setParameter("user", "someone").setParameter("name", "dig rha").getSingleResult();
		Assertions.assertThrows(ForbiddenException.class, () -> {
			resource.delete(id);
		});
	}

	@Test
	public void deleteUnknown() {
		Assertions.assertThrows(ObjectRetrievalFailureException.class, () -> {
			resource.delete(-5);
		});
	}
}

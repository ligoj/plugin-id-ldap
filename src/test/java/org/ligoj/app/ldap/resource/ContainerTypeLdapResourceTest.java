package org.ligoj.app.ldap.resource;

import java.io.IOException;
import java.util.List;

import javax.transaction.Transactional;
import javax.ws.rs.core.UriInfo;

import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.ligoj.bootstrap.AbstractJpaTest;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.app.ldap.dao.ContainerTypeLdapRepository;
import org.ligoj.app.ldap.model.ContainerType;
import org.ligoj.app.ldap.model.ContainerTypeLdap;
import org.ligoj.app.ldap.resource.ContainerTypeLdapResource;

/**
 * Test class of {@link ContainerTypeLdapResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class ContainerTypeLdapResourceTest extends AbstractJpaTest {

	@Autowired
	private ContainerTypeLdapResource resource;

	@Autowired
	private ContainerTypeLdapRepository repository;

	@Before
	public void setUpEntities() throws IOException {
		persistEntities("csv/app-test", new Class[] { ContainerTypeLdap.class }, "UTF-8");
	}

	@Test
	public void testFindAll() {
		// create a mock URI info with pagination informations
		final UriInfo uriInfo = newFindAllParameters();

		final TableItem<ContainerTypeLdap> result = resource.findAll(ContainerType.GROUP, uriInfo, null);
		Assert.assertEquals(4, result.getData().size());

		final ContainerTypeLdap type = result.getData().get(1);
		checkType(type);
	}

	@Test
	public void testFindAll2() {
		final List<ContainerTypeLdap> result = resource.findAllDescOrder(ContainerType.GROUP);
		Assert.assertEquals(4, result.size());
		final ContainerTypeLdap type = result.get(2);
		Assert.assertEquals("Project", type.getName());
		Assert.assertEquals("ou=project,dc=sample,dc=com", type.getDn());
	}

	@Test
	public void testFindAllCompany() {
		final List<ContainerTypeLdap> result = resource.findAllDescOrder(ContainerType.COMPANY);
		Assert.assertEquals(2, result.size());
		final ContainerTypeLdap type = result.get(0);
		Assert.assertEquals("France", type.getName());
		Assert.assertEquals("ou=france,ou=people,dc=sample,dc=com", type.getDn());
		Assert.assertEquals(ContainerType.COMPANY, type.getType());
	}

	@Test
	public void testFindAllGlobalSearch() {
		// create a mock URI info with pagination informations
		final UriInfo uriInfo = newFindAllParameters();

		final TableItem<ContainerTypeLdap> result = resource.findAll(ContainerType.GROUP, uriInfo, "j");
		Assert.assertEquals(1, result.getData().size());

		final ContainerTypeLdap type = result.getData().get(0);
		checkType(type);
	}

	private UriInfo newFindAllParameters() {
		final UriInfo uriInfo = Mockito.mock(UriInfo.class);
		Mockito.when(uriInfo.getQueryParameters()).thenReturn(new MetadataMap<>());
		uriInfo.getQueryParameters().add("draw", "1");
		uriInfo.getQueryParameters().add("start", "0");
		uriInfo.getQueryParameters().add("length", "10");
		uriInfo.getQueryParameters().add("columns[0][data]", "name");
		uriInfo.getQueryParameters().add("order[0][column]", "0");
		uriInfo.getQueryParameters().add("order[0][dir]", "desc");
		return uriInfo;
	}

	/**
	 * test {@link ContainerTypeLdapResource#findById(int)}
	 */
	@Test(expected = JpaObjectRetrievalFailureException.class)
	public void testFindByIdInvalid() {
		Assert.assertNull(resource.findById(0));
	}

	/**
	 * test {@link ContainerTypeLdapResource#findById(int)}
	 */
	@Test
	public void testFindById() {
		final Integer id = repository.findAll(new Sort("name")).get(3).getId();
		checkType(resource.findById(id));
	}

	private void checkType(final ContainerTypeLdap type) {
		Assert.assertEquals("Project", type.getName());
		Assert.assertTrue(type.isLocked());
		Assert.assertEquals("ou=project,dc=sample,dc=com", type.getDn());
		Assert.assertEquals(ContainerType.GROUP, type.getType());
	}

	/**
	 * test create
	 */
	@Test
	public void testCreate() {
		final ContainerTypeLdap vo = new ContainerTypeLdap();
		vo.setName("Name");
		vo.setDn("dc=sample,dc=com");
		vo.setType(ContainerType.GROUP);
		final int id = resource.create(vo);
		em.flush();
		em.clear();

		final ContainerTypeLdap entity = repository.findOneExpected(id);
		Assert.assertEquals("Name", entity.getName());
		Assert.assertEquals("dc=sample,dc=com", entity.getDn());
		Assert.assertFalse(entity.isLocked());
		Assert.assertEquals(id, entity.getId().intValue());
	}

	/**
	 * test create duplicate DN
	 */
	@Test(expected = DataIntegrityViolationException.class)
	public void testCreateDuplicateDn() {
		final ContainerTypeLdap vo = new ContainerTypeLdap();
		vo.setName("Name");
		vo.setType(ContainerType.GROUP);
		vo.setDn("ou=project,dc=sample,dc=com");
		resource.create(vo);
	}

	/**
	 * test create duplicate name
	 */
	@Test(expected = DataIntegrityViolationException.class)
	public void testCreateDuplicateName() {
		final ContainerTypeLdap vo = new ContainerTypeLdap();
		vo.setName("Project");
		vo.setDn("dc=sample,dc=com");
		vo.setType(ContainerType.GROUP);
		resource.create(vo);
	}

	/**
	 * test update
	 */
	@Test
	public void testUpdate() {
		final int id = repository.findAll(new Sort("name")).get(0).getId();

		final ContainerTypeLdap vo = new ContainerTypeLdap();
		vo.setId(id);
		vo.setName("Name");
		vo.setDn("dc=sample,dc=com");
		vo.setType(ContainerType.GROUP);
		resource.update(vo);
		em.flush();
		em.clear();

		final ContainerTypeLdap entity = repository.findOneExpected(id);
		Assert.assertEquals("Name", entity.getName());
		Assert.assertEquals("dc=sample,dc=com", entity.getDn());
		Assert.assertEquals(id, entity.getId().intValue());
	}

	/**
	 * test delete locked group
	 */
	@Test
	public void testDeleteProtected() {
		final ContainerTypeLdap typeLdap = repository.findAll(new Sort("name")).get(3);
		final int id = typeLdap.getId();
		Assert.assertTrue(typeLdap.isLocked());
		final long initCount = repository.count();
		em.clear();
		resource.delete(id);
		em.flush();
		em.clear();

		// Check is not deleted
		Assert.assertEquals(initCount, repository.count());
	}

	/**
	 * test delete
	 */
	@Test
	public void testDelete() {
		final ContainerTypeLdap typeLdap = repository.findAll(new Sort("name")).get(0);
		final int id = typeLdap.getId();
		Assert.assertFalse(typeLdap.isLocked());
		final long initCount = repository.count();
		em.clear();
		resource.delete(id);
		em.flush();
		em.clear();
		Assert.assertEquals(initCount - 1, repository.count());
	}
}

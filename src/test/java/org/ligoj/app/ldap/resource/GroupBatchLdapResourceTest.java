package org.ligoj.app.ldap.resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.transaction.Transactional;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.ligoj.app.DefaultVerificationMode;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.model.ContainerType;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.app.plugin.id.resource.ContainerScopeResource;
import org.ligoj.app.plugin.id.resource.GroupEditionVo;
import org.ligoj.app.plugin.id.resource.GroupResource;
import org.ligoj.bootstrap.core.SpringUtils;
import org.ligoj.bootstrap.resource.system.session.SessionSettings;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Test of {@link GroupBatchLdapResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Transactional
public class GroupBatchLdapResourceTest extends AbstractLdapBatchTest {

	@Autowired
	protected GroupBatchLdapResource resource;

	private GroupResource mockLdapResource;

	@SuppressWarnings("unchecked")
	@Before
	public void mockApplicationContext() {
		final ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
		SpringUtils.setSharedApplicationContext(applicationContext);
		mockLdapResource = Mockito.mock(GroupResource.class);
		final GroupFullLdapTask mockTask = new GroupFullLdapTask();
		mockTask.resource = mockLdapResource;
		mockTask.securityHelper = securityHelper;
		mockTask.containerTypeLdapResource = Mockito.mock(ContainerScopeResource.class);
		Mockito.when(applicationContext.getBean(SessionSettings.class)).thenReturn(new SessionSettings());
		Mockito.when(applicationContext.getBean((Class<?>) ArgumentMatchers.any(Class.class))).thenAnswer((Answer<Object>) invocation -> {
			final Class<?> requiredType = (Class<Object>) invocation.getArguments()[0];
			if (requiredType == GroupFullLdapTask.class) {
				return mockTask;
			}
			return GroupBatchLdapResourceTest.super.applicationContext.getBean(requiredType);
		});

		final ContainerScope container = new ContainerScope();
		container.setId(1);
		container.setName("Fonction");
		container.setType(ContainerType.GROUP);
		Mockito.when(mockTask.containerTypeLdapResource.findByName("Fonction")).thenReturn(container);
	}

	@After
	public void unmockApplicationContext() {
		SpringUtils.setSharedApplicationContext(super.applicationContext);
	}

	@Before
	public void prepareData() throws IOException {
		persistEntities("csv/app-test", new Class[] { DelegateOrg.class }, StandardCharsets.UTF_8.name());
	}

	@Test
	public void full() throws IOException, InterruptedException {
		final BatchTaskVo<GroupImportEntry> importTask = full("Gfi France;Fonction");

		// Check the result
		final GroupImportEntry importEntry = checkImportTask(importTask);
		Assert.assertEquals("Gfi France", importEntry.getName());
		Assert.assertEquals("Fonction", importEntry.getType());
		Assert.assertNull(importEntry.getDepartment());
		Assert.assertNull(importEntry.getOwner());
		Assert.assertNull(importEntry.getAssistant());
		Assert.assertNull(importEntry.getParent());
		Assert.assertTrue(importEntry.getStatus());
		Assert.assertNull(importEntry.getStatusText());

		// Check LDAP
		Mockito.verify(mockLdapResource, new DefaultVerificationMode(data -> {
			if (data.getAllInvocations().size() != 1) {
				throw new MockitoException("Expect one call");
			}
			final GroupEditionVo group = (GroupEditionVo) data.getAllInvocations().get(0).getArguments()[0];
			Assert.assertNotNull(group);
			Assert.assertEquals("Gfi France", group.getName());
			Assert.assertNotNull(group.getType());
			Assert.assertTrue(group.getDepartments().isEmpty());
			Assert.assertTrue(group.getOwners().isEmpty());
			Assert.assertTrue(group.getAssistants().isEmpty());
			Assert.assertNull(group.getParent());
		})).create(null);
	}

	// "name", "type", "parent", "owner", "seealso", "department"
	@Test
	public void fullFull() throws IOException, InterruptedException {
		final BatchTaskVo<GroupImportEntry> importTask = full("Opérations Spéciales;Fonction;Operations;fdaugan,alongchu;jdoe5,wuser;700301,700302");

		// Check the result
		final GroupImportEntry importEntry = checkImportTask(importTask);
		Assert.assertEquals("Opérations Spéciales", importEntry.getName());
		Assert.assertEquals("Fonction", importEntry.getType());
		Assert.assertEquals("Operations", importEntry.getParent());
		Assert.assertEquals("fdaugan,alongchu", importEntry.getOwner());
		Assert.assertEquals("jdoe5,wuser", importEntry.getAssistant());
		Assert.assertEquals("700301,700302", importEntry.getDepartment());
		Assert.assertTrue(importEntry.getStatus());
		Assert.assertNull(importEntry.getStatusText());

		// Check LDAP
		Mockito.verify(mockLdapResource, new DefaultVerificationMode(data -> {
			if (data.getAllInvocations().size() != 1) {
				throw new MockitoException("Expect one call");
			}
			final GroupEditionVo group = (GroupEditionVo) data.getAllInvocations().get(0).getArguments()[0];
			Assert.assertNotNull(group);
			Assert.assertEquals("Opérations Spéciales", group.getName());
			Assert.assertNotNull(group.getType());
			Assert.assertEquals(2, group.getOwners().size());
			Assert.assertEquals("fdaugan", group.getOwners().get(0));
			Assert.assertEquals(2, group.getAssistants().size());
			Assert.assertEquals("jdoe5", group.getAssistants().get(0));
			Assert.assertEquals(2, group.getDepartments().size());
			Assert.assertEquals("700301", group.getDepartments().get(0));
			Assert.assertEquals("Operations", group.getParent());
		})).create(null);
	}

	protected <U extends BatchElement> BatchTaskVo<U> full(final InputStream input, final String[] headers) throws IOException, InterruptedException {
		return full(input, headers, "cp1252");
	}

	protected <U extends BatchElement> BatchTaskVo<U> full(final InputStream input, final String[] headers, final String encoding)
			throws IOException, InterruptedException {
		initSpringSecurityContext(DEFAULT_USER);
		final long id = resource.full(input, headers, encoding);
		Assert.assertNotNull(id);
		@SuppressWarnings("unchecked")
		final BatchTaskVo<U> importTask = (BatchTaskVo<U>) resource.getImportTask(id);
		Assert.assertEquals(id, importTask.getId());
		return waitImport(importTask);
	}

	protected <U extends BatchElement> BatchTaskVo<U> full(final String csvData) throws IOException, InterruptedException {
		return full(csvData, "cp1252");
	}

	protected <U extends BatchElement> BatchTaskVo<U> full(final String csvData, final String encoding) throws IOException, InterruptedException {
		return full(new ByteArrayInputStream(csvData.getBytes(encoding)),
				new String[] { "name", "type", "parent", "owner", "assistant", "department" }, encoding);
	}
}

package org.ligoj.app.ldap.resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;

import javax.transaction.Transactional;
import javax.validation.ConstraintViolationException;

import org.apache.cxf.jaxrs.provider.ServerProviderFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.ligoj.bootstrap.core.SpringUtils;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.resource.system.session.SessionSettings;
import org.ligoj.app.DefaultVerificationMode;
import org.ligoj.app.api.UserLdap;
import org.ligoj.app.ldap.resource.BatchElement;
import org.ligoj.app.ldap.resource.BatchTaskVo;
import org.ligoj.app.ldap.resource.UserAtomicLdapTask;
import org.ligoj.app.ldap.resource.UserBatchLdapResource;
import org.ligoj.app.ldap.resource.UserFullLdapTask;
import org.ligoj.app.ldap.resource.UserImportEntry;
import org.ligoj.app.ldap.resource.UserLdapEdition;
import org.ligoj.app.ldap.resource.UserLdapResource;
import org.ligoj.app.ldap.resource.UserUpdateEntry;
import org.ligoj.app.model.DelegateLdap;

/**
 * Test of {@link UserBatchLdapResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Transactional
public class UserBatchLdapResourceTest extends AbstractLdapBatchTest {

	@Autowired
	protected UserBatchLdapResource resource;

	private UserLdapResource mockLdapResource;

	@SuppressWarnings("unchecked")
	@Before
	public void mockApplicationContext() {
		final ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
		SpringUtils.setSharedApplicationContext(applicationContext);
		mockLdapResource = Mockito.mock(UserLdapResource.class);
		final UserFullLdapTask mockTask = new UserFullLdapTask();
		mockTask.resource = mockLdapResource;
		mockTask.securityHelper = securityHelper;
		final UserAtomicLdapTask mockTaskUpdate = new UserAtomicLdapTask();
		mockTaskUpdate.resource = mockLdapResource;
		mockTaskUpdate.securityHelper = securityHelper;
		Mockito.when(applicationContext.getBean(SessionSettings.class)).thenReturn(new SessionSettings());
		Mockito.when(applicationContext.getBean((Class<?>) ArgumentMatchers.any(Class.class))).thenAnswer((Answer<Object>) invocation -> {
			final Class<?> requiredType = (Class<Object>) invocation.getArguments()[0];
			if (requiredType == UserFullLdapTask.class) {
				return mockTask;
			}
			if (requiredType == UserAtomicLdapTask.class) {
				return mockTaskUpdate;
			}
			return UserBatchLdapResourceTest.super.applicationContext.getBean(requiredType);
		});

		mockTaskUpdate.jaxrsFactory = ServerProviderFactory.createInstance(null);
	}

	@After
	public void unmockApplicationContext() {
		SpringUtils.setSharedApplicationContext(super.applicationContext);
	}

	@Before
	public void prepareData() throws IOException {
		persistEntities("csv/app-test", new Class[] { DelegateLdap.class }, StandardCharsets.UTF_8.name());
	}

	@Test
	public void full() throws IOException, InterruptedException {
		final BatchTaskVo<UserImportEntry> importTask = full("Loubli;Sébastien;kloubli;my.address@sample.com;gfi;jira");

		// Check the result
		final UserImportEntry importEntry = checkImportTask(importTask);
		Assert.assertEquals("gfi", importEntry.getCompany());
		Assert.assertEquals("Sébastien", importEntry.getFirstName());
		Assert.assertEquals("Loubli", importEntry.getLastName());
		Assert.assertEquals("kloubli", importEntry.getId());
		Assert.assertEquals("jira", importEntry.getGroups());
		Assert.assertEquals("my.address@sample.com", importEntry.getMail());
		Assert.assertTrue(importEntry.getStatus());
		Assert.assertNull(importEntry.getStatusText());

		// Check LDAP
		Mockito.verify(mockLdapResource, new DefaultVerificationMode(data -> {
			if (data.getAllInvocations().size() != 1) {
				throw new MockitoException("Expect one call");
			}
			final UserLdapEdition userLdap = (UserLdapEdition) data.getAllInvocations().get(0).getArguments()[0];
			Assert.assertNotNull(userLdap);
			Assert.assertEquals("Sébastien", userLdap.getFirstName());
			Assert.assertEquals("Loubli", userLdap.getLastName());
			Assert.assertEquals("kloubli", userLdap.getId());
			Assert.assertEquals("gfi", userLdap.getCompany());
			Assert.assertEquals("my.address@sample.com", userLdap.getMail());
		})).create(null);
	}

	@Test
	public void atomic() throws IOException, InterruptedException {
		initSpringSecurityContext(DEFAULT_USER);

		final UserLdap user = new UserLdap();
		user.setCompany("untouched");
		user.setDepartment("untouched");
		user.setFirstName("untouched");
		user.setLastName("untouched");
		user.setName("fdaugan");
		user.setLocalId("untouched");
		user.setMails(new ArrayList<>());
		user.setGroups(new ArrayList<>());
		Mockito.when(mockLdapResource.findById("fdaugan")).thenReturn(user);

		final long id = resource.atomic(new ByteArrayInputStream("fdaugan;mail;any.daugan@sample.com".getBytes("cp1252")),
				new String[] { "user", "operation", "value" }, "cp1252");
		Assert.assertNotNull(id);
		@SuppressWarnings("unchecked")
		BatchTaskVo<UserUpdateEntry> importTask = (BatchTaskVo<UserUpdateEntry>) resource.getImportTask(id);
		Assert.assertEquals(id, importTask.getId());
		importTask = waitImport(importTask);

		// Check the result
		final UserUpdateEntry importEntry = checkImportTask(importTask);
		Assert.assertEquals("mail", importEntry.getOperation());
		Assert.assertEquals("any.daugan@sample.com", importEntry.getValue());
		Assert.assertEquals("fdaugan", importEntry.getUser());
		Assert.assertTrue(importEntry.getStatus());
		Assert.assertNull(importEntry.getStatusText());

		// Check LDAP
		Mockito.verify(mockLdapResource, new DefaultVerificationMode(data -> {
			if (data.getAllInvocations().size() != 2) {
				throw new MockitoException("Expect two calls");
			}

			// "findBy" call
			Assert.assertEquals("fdaugan", data.getAllInvocations().get(0).getArguments()[0]);

			// "update" call
			final UserLdapEdition userLdap = (UserLdapEdition) data.getAllInvocations().get(1).getArguments()[0];
			Assert.assertNotNull(userLdap);
			Assert.assertEquals("untouched", userLdap.getFirstName());
			Assert.assertEquals("untouched", userLdap.getLastName());
			Assert.assertEquals("fdaugan", userLdap.getId());
			Assert.assertEquals("untouched", userLdap.getCompany());
			Assert.assertEquals("untouched", userLdap.getDepartment());
			Assert.assertEquals("untouched", userLdap.getLocalId());
			Assert.assertEquals("any.daugan@sample.com", userLdap.getMail());
		})).update(null);
	}

	@Test
	public void fullEmptyGroups() throws IOException, InterruptedException {
		final BatchTaskVo<UserImportEntry> importTask = full("Loubli;Sébastien;kloubli9;my.address@sample.com;gfi;,jira,");

		// Check the result
		final UserImportEntry importEntry = checkImportTask(importTask);
		Assert.assertEquals("kloubli9", importEntry.getId());
		Assert.assertTrue(importEntry.getStatus());
		Assert.assertNull(importEntry.getStatusText());

		// Check LDAP
		Mockito.verify(mockLdapResource, new DefaultVerificationMode(data -> {
			if (data.getAllInvocations().size() != 1) {
				throw new MockitoException("Expect one call");
			}
			final UserLdapEdition userLdap = (UserLdapEdition) data.getAllInvocations().get(0).getArguments()[0];
			Assert.assertNotNull(userLdap);
			Assert.assertEquals("kloubli9", userLdap.getId());
			Assert.assertEquals(1, userLdap.getGroups().size());
			Assert.assertEquals("jira", userLdap.getGroups().iterator().next());
		})).create(null);
	}

	@Test
	public void fullInvalidHeaders() throws IOException {
		thrown.expect(BusinessException.class);
		thrown.expectMessage("Invalid header");
		final InputStream input = new ByteArrayInputStream("Loubli;Sébastien;kloubli4;my.address@sample.com;gfi;jira".getBytes("cp1250"));
		initSpringSecurityContext(DEFAULT_USER);
		resource.full(input, new String[] { "lastName", "firstName", "id", "mail8", "company", "groups" }, "cp1250");
	}

	@Test
	public void fullDefaultHeader() throws IOException, InterruptedException {
		final InputStream input = new ByteArrayInputStream("Loubli;Sébastien;kloubli5;my.address@sample.com;gfi;jira".getBytes("cp1250"));
		initSpringSecurityContext(DEFAULT_USER);
		@SuppressWarnings("unchecked")
		final BatchTaskVo<UserImportEntry> importTask = (BatchTaskVo<UserImportEntry>) waitImport(
				resource.getImportTask(resource.full(input, new String[0], "cp1250")));
		Assert.assertEquals(Boolean.TRUE, importTask.getEntries().get(0).getStatus());
		Assert.assertNull(importTask.getEntries().get(0).getStatusText());

		// Check LDAP
		Mockito.verify(mockLdapResource, new DefaultVerificationMode(data -> {
			if (data.getAllInvocations().size() != 1) {
				throw new MockitoException("Expect one call");
			}
			final UserLdapEdition userLdap = (UserLdapEdition) data.getAllInvocations().get(0).getArguments()[0];
			Assert.assertNotNull(userLdap);
			Assert.assertNotNull(userLdap);
			Assert.assertEquals("Sébastien", userLdap.getFirstName());
			Assert.assertEquals("Loubli", userLdap.getLastName());
			Assert.assertEquals("kloubli5", userLdap.getId());
			Assert.assertEquals("gfi", userLdap.getCompany());
			Assert.assertEquals("my.address@sample.com", userLdap.getMail());
		})).create(null);
	}

	@Test(expected = ConstraintViolationException.class)
	public void fullMisingLogin() throws IOException {
		final InputStream input = new ByteArrayInputStream("Loubli;Sébastien;;my.address@sample.com;gfi;jira".getBytes("cp1250"));
		initSpringSecurityContext(DEFAULT_USER);
		resource.full(input, new String[0], "cp1250");
	}

	@Test
	public void fullFailed() throws IOException, InterruptedException {
		Mockito.doThrow(new BusinessException("message")).when(this.mockLdapResource).create(ArgumentMatchers.any(UserLdapEdition.class));
		final InputStream input = new ByteArrayInputStream("Loubli;Sébastien;fdaugan;my.address@sample.com;gfi;jira".getBytes("cp1250"));
		initSpringSecurityContext(DEFAULT_USER);
		@SuppressWarnings("unchecked")
		final BatchTaskVo<UserImportEntry> importTask = (BatchTaskVo<UserImportEntry>) waitImport(
				resource.getImportTask(resource.full(input, new String[0], "cp1250")));
		Assert.assertEquals("message", importTask.getEntries().get(0).getStatusText());
		Assert.assertEquals(Boolean.FALSE, importTask.getEntries().get(0).getStatus());
	}

	@Test
	public void getImportTaskFailed() {
		Assert.assertNull(resource.getImportTask(-1));
	}

	@Test
	public void getImportStatusFailed() {
		Assert.assertNull(resource.getImportStatus(-1));
	}

	@Test
	public void getImportStatus() throws InterruptedException, IOException {
		final BatchTaskVo<UserImportEntry> importTask = full("Loubli;Sébastien;kloubli7;my.address@sample.com;gfi;,jira,");
		Assert.assertSame(importTask, resource.getImportTask(importTask.getId()));
		Assert.assertSame(importTask.getStatus(), resource.getImportStatus(importTask.getId()));
	}

	@Test
	public void getImportStatusPreviousFinished() throws InterruptedException, IOException {
		final BatchTaskVo<UserImportEntry> oldTask = full("Loubli;Sébastien;kloubli5a;my.address@sample.com;gfi;,jira,");
		oldTask.getStatus().setEnd(getDate(1980, 1, 1));
		final BatchTaskVo<UserImportEntry> importTask = full("Loubli;Sébastien;kloubli5b;my.address@sample.com;gfi;,jira,");
		Assert.assertSame(importTask, resource.getImportTask(importTask.getId()));
		Assert.assertSame(importTask.getStatus(), resource.getImportStatus(importTask.getId()));
	}

	@Test
	public void getImportStatusPreviousNotFinished() throws InterruptedException, IOException {
		final BatchTaskVo<UserImportEntry> oldTask = full("Loubli;Sébastien;kloubli6a;my.address@sample.com;gfi;,jira,");
		oldTask.getStatus().setEnd(null);
		final BatchTaskVo<UserImportEntry> importTask = full("Loubli;Sébastien;kloubli6b;my.address@sample.com;gfi;,jira,");
		Assert.assertSame(importTask, resource.getImportTask(importTask.getId()));
		Assert.assertSame(importTask.getStatus(), resource.getImportStatus(importTask.getId()));
		oldTask.getStatus().setEnd(new Date());
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
		return full(new ByteArrayInputStream(csvData.getBytes(encoding)), new String[] { "lastName", "firstName", "id", "mail", "company", "groups" },
				encoding);
	}

}

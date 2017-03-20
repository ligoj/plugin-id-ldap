package org.ligoj.app.ldap.resource;

import java.util.Collections;
import java.util.List;

import javax.ws.rs.ext.ExceptionMapper;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.jaxrs.model.ProviderInfo;
import org.apache.cxf.jaxrs.provider.ServerProviderFactory;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import org.ligoj.app.ldap.resource.BatchTaskVo;
import org.ligoj.app.ldap.resource.UserFullLdapTask;
import org.ligoj.app.ldap.resource.UserImportEntry;
import org.ligoj.app.ldap.resource.UserLdapResource;
import org.ligoj.bootstrap.AbstractSecurityTest;
import org.ligoj.bootstrap.core.resource.mapper.FailSafeExceptionMapper;
import org.ligoj.bootstrap.core.security.SecurityHelper;

/**
 * Test of {@link UserFullLdapTask}
 */
public class UserFullLdapTaskTest extends AbstractSecurityTest {

	private UserFullLdapTask task;

	@Before
	public void setup() {
		task = new UserFullLdapTask();
		task.resource = Mockito.mock(UserLdapResource.class);
		task.securityHelper = new SecurityHelper();
		task.jaxrsFactory = ServerProviderFactory.getInstance();
		initSpringSecurityContext(DEFAULT_USER);
	}

	@Test
	public void runInvalidLdapStatus() {
		final UserImportEntry entry = Mockito.mock(UserImportEntry.class);
		Mockito.when(entry.getId()).thenThrow(new RuntimeException());
		final BatchTaskVo<UserImportEntry> importTask = new BatchTaskVo<>();
		importTask.setEntries(Collections.singletonList(entry));
		task.configure(importTask);
		task.run();
		Assert.assertEquals(Boolean.TRUE, importTask.getStatus().getStatus());
		Mockito.verify(entry, Mockito.atLeastOnce()).setStatus(Boolean.FALSE);
		Assert.assertEquals(1, importTask.getStatus().getDone());
		Assert.assertEquals(1, importTask.getStatus().getEntries());
	}

	@Test
	public void run() {
		final BatchTaskVo<UserImportEntry> importTask = new BatchTaskVo<>();
		final UserImportEntry entry = new UserImportEntry();
		entry.setGroups(",group,");
		importTask.setEntries(Collections.singletonList(entry));
		task.configure(importTask);
		task.run();
		Assert.assertEquals(Boolean.TRUE, importTask.getStatus().getStatus());
		Assert.assertEquals(1, importTask.getStatus().getDone());
		Assert.assertEquals(1, importTask.getStatus().getEntries());
	}

	@Test
	public void configureMessage() throws IllegalArgumentException, IllegalAccessException {
		final ServerProviderFactory instance = ServerProviderFactory.getInstance();
		@SuppressWarnings("unchecked")
		final List<ProviderInfo<ExceptionMapper<?>>> object = (List<ProviderInfo<ExceptionMapper<?>>>) FieldUtils
				.getField(ServerProviderFactory.class, "exceptionMappers", true).get(instance);
		final FailSafeExceptionMapper provider = new FailSafeExceptionMapper();
		object.add(new ProviderInfo<>(provider, null, true));
		final JacksonJsonProvider jacksonJsonProvider = new JacksonJsonProvider();
		FieldUtils.getField(FailSafeExceptionMapper.class, "jacksonJsonProvider", true).set(provider, jacksonJsonProvider);

		final UserImportEntry entry = Mockito.mock(UserImportEntry.class);
		Mockito.when(entry.getId()).thenThrow(new RuntimeException());
		final BatchTaskVo<UserImportEntry> importTask = new BatchTaskVo<>();
		importTask.setEntries(Collections.singletonList(entry));
		task.configure(importTask);
		task.jaxrsFactory = instance;
		task.run();
		Assert.assertEquals(Boolean.TRUE, importTask.getStatus().getStatus());
		Assert.assertEquals(1, importTask.getStatus().getDone());
		Assert.assertEquals(1, importTask.getStatus().getEntries());
	}

	@Test
	public void configureMessage2() throws IllegalArgumentException {
		final UserImportEntry entry = Mockito.mock(UserImportEntry.class);
		Mockito.when(entry.getId()).thenThrow(new RuntimeException());
		final BatchTaskVo<UserImportEntry> importTask = new BatchTaskVo<>();
		importTask.setEntries(Collections.singletonList(entry));

		final Message message = Mockito.mock(Message.class);
		final UserFullLdapTask task = new UserFullLdapTask() {
			@Override
			protected Message getMessage() {
				return message;
			}

		};
		final Exchange exchange = Mockito.mock(Exchange.class);
		Mockito.when(message.getExchange()).thenReturn(exchange);
		final Endpoint endpoint = Mockito.mock(Endpoint.class);
		Mockito.when(exchange.getEndpoint()).thenReturn(endpoint);
		Mockito.when(endpoint.get("org.apache.cxf.jaxrs.provider.ServerProviderFactory")).thenReturn(ServerProviderFactory.getInstance());

		task.configure(importTask);
	}

}

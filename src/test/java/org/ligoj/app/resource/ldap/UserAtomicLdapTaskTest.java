package org.ligoj.app.resource.ldap;

import java.util.ArrayList;
import java.util.function.Function;

import javax.validation.ValidationException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.exceptions.base.MockitoException;

import org.ligoj.bootstrap.AbstractSecurityTest;
import org.ligoj.bootstrap.core.security.SecurityHelper;
import org.ligoj.app.DefaultVerificationMode;
import org.ligoj.app.model.ldap.UserLdap;

/**
 * Test of {@link UserAtomicLdapTask}
 */
public class UserAtomicLdapTaskTest extends AbstractSecurityTest {

	private UserAtomicLdapTask task;

	@Before
	public void setup() {
		task = new UserAtomicLdapTask();
		task.resource = Mockito.mock(UserLdapResource.class);
		task.securityHelper = new SecurityHelper();
		initSpringSecurityContext(DEFAULT_USER);

		final UserLdap user = new UserLdap();
		user.setCompany("untouched");
		user.setDepartment("untouched");
		user.setFirstName("untouched");
		user.setLastName("untouched");
		user.setName(DEFAULT_USER);
		user.setLocalId("untouched");
		user.setMails(new ArrayList<>());
		user.setGroups(new ArrayList<>());
		Mockito.when(task.resource.findById(DEFAULT_USER)).thenReturn(user);
	}

	@Test(expected = ValidationException.class)
	public void doBatchInvalidOperation() throws Exception {
		final UserUpdateEntry entry = new UserUpdateEntry();
		entry.setOperation("any");
		task.doBatch(entry);
	}

	@Test(expected = ValidationException.class)
	public void doBatchExtraValue() throws Exception {
		final UserUpdateEntry entry = new UserUpdateEntry();
		entry.setOperation("lock");
		entry.setValue("any");
		task.doBatch(entry);
	}

	@Test
	public void doBatchDepartment() throws Exception {
		final UserUpdateEntry entry = new UserUpdateEntry();
		entry.setOperation("department");
		entry.setValue("value");
		entry.setUser(DEFAULT_USER);
		task.doBatch(entry);
		checkAttribute(UserLdapEdition::getDepartment, "value");
	}

	@Test
	public void doBatchLastName() throws Exception {
		final UserUpdateEntry entry = new UserUpdateEntry();
		entry.setOperation("lastname");
		entry.setValue("value");
		entry.setUser(DEFAULT_USER);
		task.doBatch(entry);
		checkAttribute(UserLdapEdition::getLastName, "value");
	}

	@Test
	public void doBatchCompany() throws Exception {
		final UserUpdateEntry entry = new UserUpdateEntry();
		entry.setOperation("company");
		entry.setValue("value");
		entry.setUser(DEFAULT_USER);
		task.doBatch(entry);
		checkAttribute(UserLdapEdition::getCompany, "value");
	}

	@Test
	public void doBatchLocalId() throws Exception {
		final UserUpdateEntry entry = new UserUpdateEntry();
		entry.setOperation("localid");
		entry.setValue("value");
		entry.setUser(DEFAULT_USER);
		task.doBatch(entry);
		checkAttribute(UserLdapEdition::getLocalId, "value");
	}

	@Test
	public void doBatchMail() throws Exception {
		final UserUpdateEntry entry = new UserUpdateEntry();
		entry.setOperation("mail");
		entry.setValue("value");
		entry.setUser(DEFAULT_USER);
		task.doBatch(entry);
		checkAttribute(UserLdapEdition::getMail, "value");
	}

	@Test
	public void doBatchFirstName() throws Exception {
		final UserUpdateEntry entry = new UserUpdateEntry();
		entry.setOperation("firstname");
		entry.setValue("value");
		entry.setUser(DEFAULT_USER);
		task.doBatch(entry);
		checkAttribute(UserLdapEdition::getFirstName, "value");
	}

	@Test
	public void doBatchIsolate() throws Exception {
		final UserUpdateEntry entry = new UserUpdateEntry();
		entry.setOperation("isolate");
		entry.setUser(DEFAULT_USER);
		task.doBatch(entry);

		// Check LDAP
		Mockito.verify(task.resource).isolate(DEFAULT_USER);
	}

	@Test
	public void doBatchRestore() throws Exception {
		final UserUpdateEntry entry = new UserUpdateEntry();
		entry.setOperation("restore");
		entry.setUser(DEFAULT_USER);
		task.doBatch(entry);

		// Check LDAP
		Mockito.verify(task.resource).restore(DEFAULT_USER);
	}

	@Test
	public void doBatchLock() throws Exception {
		final UserUpdateEntry entry = new UserUpdateEntry();
		entry.setOperation("lock");
		entry.setUser(DEFAULT_USER);
		task.doBatch(entry);

		// Check LDAP
		Mockito.verify(task.resource).lock(DEFAULT_USER);
	}

	@Test
	public void doBatchDelete() throws Exception {
		final UserUpdateEntry entry = new UserUpdateEntry();
		entry.setOperation("delete");
		entry.setUser(DEFAULT_USER);
		task.doBatch(entry);

		// Check LDAP
		Mockito.verify(task.resource).delete(DEFAULT_USER);
	}

	private void checkAttribute(final Function<UserLdapEdition, String> function, final String value) {

		// Check LDAP
		Mockito.verify(task.resource, new DefaultVerificationMode(data -> {
			if (data.getAllInvocations().size() != 2) {
				throw new MockitoException("Expect two calls");
			}

			// "findBy" call
			Assert.assertEquals(DEFAULT_USER, data.getAllInvocations().get(0).getArguments()[0]);

			// "update" call
			final UserLdapEdition userLdap = (UserLdapEdition) data.getAllInvocations().get(1).getArguments()[0];
			Assert.assertNotNull(userLdap);
			Assert.assertEquals(value, function.apply(userLdap));
		})).update(null);

	}

}

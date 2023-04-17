/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.dao;

import org.apache.commons.collections4.MapUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.ldap.NameAlreadyBoundException;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.*;
import org.springframework.ldap.core.support.AbstractContextMapper;

import javax.naming.AuthenticationException;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.directory.InvalidAttributeValueException;
import javax.naming.directory.ModificationItem;
import javax.naming.ldap.LdapContext;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

/**
 * Test class of {@link UserLdapRepository}
 */
class UserLdapRepositoryTest {
	private static final long LOCKED_DATE = 1517908964000L;
	private static final long ONE_HOUR_MS = 60 * 60 * 1000;

	private UserLdapRepository repository;

	@BeforeEach
	void init() {
		repository = new UserLdapRepository();
	}

	@Test
	void toCompanyNoMatch() {
		repository.setCompanyPattern("[^,]+,ou=([^,]+),.*");
		Assertions.assertNull(repository.toCompany("any"));
	}

	@Test
	void toCompanyNoCaptureNoMatch() {
		repository.setCompanyPattern("[^,]+,ou=[^,]+,.*");
		Assertions.assertEquals("[^,]+,ou=[^,]+,.*", repository.toCompany("company"));
	}

	@Test
	void toCompanyNoCaptureMatch() {
		repository.setCompanyPattern("[^,]+,ou=[^,]+,.*");
		Assertions.assertNull(repository.toCompany("uid=some,ou=company,ou=fr"));
	}

	@Test
	void toCompanyConstant() {
		repository.setCompanyPattern("const");
		Assertions.assertEquals("const", repository.toCompany("uid=some,ou=company,dc=ex,dc=fr"));
	}

	@Test
	void toCompany() {
		repository.setCompanyPattern("[^,]+,ou=([^,]+),.*");
		Assertions.assertEquals("company", repository.toCompany("uid=some,ou=company,dc=ex,dc=fr"));
		Assertions.assertEquals("company", repository.toCompany("uid=some,ou=company,dc=ex"));
	}

	@Test
	void getAuthenticateProperty() {
		repository.setUidAttribute("my-uid");
		Assertions.assertEquals("my-uid", repository.getAuthenticateProperty("some"));
	}

	@Test
	void getAuthenticatePropertyMail() {
		Assertions.assertEquals("mail", repository.getAuthenticateProperty("my@mail.com"));
	}

	@Test
	void clearPassword() {
		var user = new UserOrg();
		user.setDn("dc=sample,dc=com");
		new UserLdapRepository() {
			@Override
			public boolean isClearPassword() {
				return true;
			}

			@Override
			public void set(final Name dn, final String attribute, final String value) {
				Assertions.assertEquals("test", value);
			}

		}.setPassword(user, "test");
	}

	@Test
	void digest() {
		UserOrg user = new UserOrg();
		user.setDn("dc=sample,dc=com");
		new UserLdapRepository() {
			@Override
			public boolean isClearPassword() {
				return false;
			}

			@Override
			public void set(final Name dn, final String attribute, final String value) {
				Assertions.assertTrue(value.startsWith("{SSHA}"));
			}

		}.setPassword(user, "test");
	}

	@Test
	void toUser() {
		var repository = new UserLdapRepository() {
			@Override
			public UserOrg findById(final String login) {
				final var userLdap = new UserOrg();
				userLdap.setId(login);
				userLdap.setFirstName("First");
				return userLdap;
			}

		};
		Assertions.assertEquals("user1", repository.toUser("user1").getId());
		Assertions.assertEquals("First", repository.toUser("user1").getFirstName());
	}

	@Test
	void toUserNotExist() {
		var repository = new UserLdapRepository() {
			@Override
			public UserOrg findById(final String login) {
				return null;
			}

		};
		Assertions.assertEquals("user1", repository.toUser("user1").getId());
		Assertions.assertNull(repository.toUser("user1").getFirstName());
	}

	@Test
	void toUserNull() {
		Assertions.assertNull(repository.toUser(null));
	}

	@SuppressWarnings("unchecked")
	@Test
	void getToken() {
		final var mock = Mockito.mock(LdapTemplate.class);
		final var dirCtx = Mockito.mock(DirContextOperations.class);
		Mockito.when(mock.search((String) ArgumentMatchers.any(), ArgumentMatchers.any(),
				(ContextMapper<String>) ArgumentMatchers.any())).thenAnswer(i -> {
			((AbstractContextMapper<DirContextOperations>) i.getArgument(2)).mapFromContext(dirCtx);
			return Collections.singletonList("token");
		});
		repository.setTemplate(mock);
		Assertions.assertEquals("token", repository.getToken("user1"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void getTokenNotExists() {
		final var mock = Mockito.mock(LdapTemplate.class);
		Mockito.when(mock.search((String) ArgumentMatchers.any(), ArgumentMatchers.any(),
				ArgumentMatchers.any(ContextMapper.class))).thenReturn(Collections.emptyList());
		repository.setTemplate(mock);
		Assertions.assertNull(repository.getToken("any"));
	}

	@Test
	void findByIdExpectedNotVisibleCompany() {
		var repository = new UserLdapRepository() {
			@Override
			public UserOrg findById(final String login) {
				UserOrg user = new UserOrg();
				user.setId(login);
				return user;
			}
		};
		repository.setCompanyRepository(Mockito.mock(CompanyLdapRepository.class));
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class,
						() -> repository.findByIdExpected("user1", "user2")),
				"id", "unknown-id");
	}

	@Test
	void findByIdExpected() {
		var repository = new UserLdapRepository() {
			@Override
			public UserOrg findById(final String login) {
				UserOrg user = new UserOrg();
				user.setId(login);
				user.setCompany("company");
				return user;
			}
		};
		var mock = Mockito.mock(CompanyLdapRepository.class);
		Mockito.when(mock.findById("user1", "company")).thenReturn(new CompanyOrg("", ""));
		repository.setCompanyRepository(mock);
		repository.findByIdExpected("user1", "user2");
	}

	@Test
	void setPassword() throws NamingException {
		final var mockCtx = setPassword("old-password", "new-password");
		Mockito.verify(mockCtx).modifyAttributes(ArgumentMatchers.eq("cn=Any"),
				ArgumentMatchers.any(ModificationItem[].class));
		Mockito.verify(mockCtx).addToEnvironment("java.naming.security.credentials", "old-password");
	}

	private LdapContext setPassword(final String password, final String newPassword) {
		final var user = new UserOrg();
		user.setDn("cn=Any");
		final LdapContext mockCtx = newLdapContext();
		repository.setPassword(user, password, newPassword);
		return mockCtx;
	}

	@Test
	void setPasswordNullOldPassword() throws NamingException {
		final var mockCtx = setPassword(null, "new-password");
		Mockito.verify(mockCtx).modifyAttributes(ArgumentMatchers.eq("cn=Any"),
				ArgumentMatchers.any(ModificationItem[].class));
		Mockito.verify(mockCtx).addToEnvironment(ArgumentMatchers.eq("java.naming.security.credentials"),
				ArgumentMatchers.anyString());
	}

	@Test
	void setPasswordBadPassword() throws NamingException {
		final var user = new UserOrg();
		user.setDn("cn=Any");
		final LdapContext mockCtx = newLdapContext();
		Mockito.doThrow(new AuthenticationException()).when(mockCtx).modifyAttributes(ArgumentMatchers.eq("cn=Any"),
				ArgumentMatchers.any(ModificationItem[].class));
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class,
				() -> repository.setPassword(user, "wrong-old-password", "new-password")), "password", "login");
	}

	@Test
	void setPasswordPolicyFail() throws NamingException {
		final var user = new UserOrg();
		user.setDn("cn=Any");
		final var mockCtx = newLdapContext();
		Mockito.doThrow(new InvalidAttributeValueException()).when(mockCtx)
				.modifyAttributes(ArgumentMatchers.eq("cn=Any"), ArgumentMatchers.any(ModificationItem[].class));
		MatcherUtil.assertThrows(
				Assertions.assertThrows(ValidationJsonException.class,
						() -> repository.setPassword(user, "old-password", "weak-password")),
				"password", "password-policy");
	}

	@Test
	void newUser() {
		var user = new UserOrg();
		final var lastContext = new ThreadLocal<DirContextOperations>();
		user.setDn("uid=sample,dc=com");
		final var repository = new UserLdapRepository() {
			@Override
			public Name buildDn(final UserOrg entry) {
				return org.springframework.ldap.support.LdapUtils.newLdapName("uid=sample,dc=com");
			}

			@Override
			protected void mapToContext(final UserOrg entry, final DirContextOperations context) {
				lastContext.set(context);
			}

			@Override
			public void set(final Name dn, final String attribute, final String value) {
				Assertions.assertEquals("test", value);
			}

		};
		repository.className = "posixAccount";
		repository.setTemplate(Mockito.mock(LdapTemplate.class));
		repository.cacheRepository = Mockito.mock(CacheLdapRepository.class);
		repository.create(user);
		Assertions.assertEquals("200", lastContext.get().getObjectAttribute("gidNumber"));
		Assertions.assertEquals("200", lastContext.get().getObjectAttribute("uidNumber"));
		Assertions.assertEquals("/dev/null", lastContext.get().getObjectAttribute("homeDirectory"));
	}

	@SuppressWarnings("unchecked")
	private LdapContext newLdapContext() {
		final var mock = Mockito.mock(LdapTemplate.class);
		repository.setTemplate(mock);

		final var mockCtx = Mockito.mock(LdapContext.class);
		Mockito.when(mock.executeReadWrite((ContextExecutor<Object>) ArgumentMatchers.any(ContextExecutor.class)))
				.then((invocation) -> ((ContextExecutor<Object>) invocation.getArgument(0)).executeWithContext(mockCtx));
		return mockCtx;
	}

	@Test
	void validLdapDate() {// <1517908964000> but was: <1517912564000>
		final var ldapDate = "20180206102244Z";
		final var date = repository.parseLdapDate(ldapDate);
		Assertions.assertTrue(date.compareTo(new Date(LOCKED_DATE)) <= ONE_HOUR_MS, "Was :" + date);
	}

	@Test
	void notValidLdapDate() {
		final var ldapDate = "20180206102244";
		Assertions.assertThrows(BusinessException.class, () -> repository.parseLdapDate(ldapDate));
	}

	@SuppressWarnings("unchecked")
	@Test
	void checkUserStatus() {
		final var user = new UserOrg();
		final var mock = Mockito.mock(LdapTemplate.class);
		final var dirCtx = Mockito.mock(DirContextOperations.class);
		Mockito.when(mock.search((String) ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq(2),
				ArgumentMatchers.any(), (ContextMapper<UserOrg>) ArgumentMatchers.any())).thenAnswer(i -> {
			((AbstractContextMapper<DirContextOperations>) i.getArgument(4)).mapFromContext(dirCtx);
			user.setLocked(new Date(LOCKED_DATE));
			user.setLockedBy("_ppolicy");
			return null;
		});
		Mockito.when(dirCtx.attributeExists(ArgumentMatchers.any())).thenReturn(true);
		Mockito.when(dirCtx.getStringAttribute(ArgumentMatchers.any())).thenReturn("20180206102244Z");
		repository.setTemplate(mock);
		repository.checkLockStatus(user);

		Assertions.assertTrue(user.getLocked().compareTo(new Date(LOCKED_DATE)) <= ONE_HOUR_MS, "Was :" + user.getLocked());
	}

	@SuppressWarnings("unchecked")
	@Test
	void blockedUserByPpolicy() {
		final var user = new UserOrg();
		final var mock = Mockito.mock(LdapTemplate.class);
		final var dirCtx = Mockito.mock(DirContextOperations.class);
		Mockito.when(mock.search((String) ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
				(ContextMapper<UserOrg>) ArgumentMatchers.any(), ArgumentMatchers.any())).thenAnswer(i -> {
			((AbstractContextMapper<DirContextOperations>) i.getArgument(3)).mapFromContext(dirCtx);
			user.setLocked(new Date(LOCKED_DATE));
			user.setLockedBy("_ppolicy");
			return Collections.singletonList(user);
		});

		Mockito.when(dirCtx.getDn()).thenReturn(org.springframework.ldap.support.LdapUtils.newLdapName("cn=Any"));
		Mockito.when(dirCtx.attributeExists(ArgumentMatchers.any())).thenReturn(true);
		Mockito.when(dirCtx.getStringAttribute(ArgumentMatchers.any())).thenReturn("20180206102244Z");
		repository.setTemplate(mock);
		final Map<String, GroupOrg> groups = MapUtils.EMPTY_SORTED_MAP;
		repository.findAllNoCache(groups);

		Assertions.assertEquals(LOCKED_DATE, user.getLocked().getTime());
	}

	@Test
	void bindExistingUser() {
		final var mock = Mockito.mock(LdapTemplate.class);
		final var dirCtx = Mockito.mock(DirContextAdapter.class);
		Mockito.doThrow(NameAlreadyBoundException.class).when(mock).bind(dirCtx);
		Mockito.when(dirCtx.getDn()).thenReturn(org.springframework.ldap.support.LdapUtils.newLdapName("cn=Any"));
		repository.setTemplate(mock);
		Assertions.assertThrows(ValidationJsonException.class, () -> repository.bind(dirCtx));
	}

	@Test
	void unbindDeletedUser() {
		final var mock = Mockito.mock(LdapTemplate.class);
		Mockito.doThrow(NameNotFoundException.class).when(mock).unbind("cn=Any", true);
		repository.setTemplate(mock);
		repository.unbind("cn=Any");
	}

}

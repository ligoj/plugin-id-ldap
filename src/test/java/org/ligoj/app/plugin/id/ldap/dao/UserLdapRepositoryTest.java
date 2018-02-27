package org.ligoj.app.plugin.id.ldap.dao;

import java.util.Collections;
import java.util.Date;
import java.util.Map;

import javax.naming.AuthenticationException;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.directory.InvalidAttributeValueException;
import javax.naming.directory.ModificationItem;
import javax.naming.ldap.LdapContext;

import org.apache.commons.collections4.MapUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.ldap.core.ContextExecutor;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.AbstractContextMapper;

/**
 * Test class of {@link UserLdapRepository}
 */
public class UserLdapRepositoryTest {

	private UserLdapRepository repository;

	@BeforeEach
	public void init() {
		repository = new UserLdapRepository();
	}

	@Test
	public void toCompanyNoMatch() {
		repository.setCompanyPattern("[^,]+,ou=([^,]+),.*");
		Assertions.assertNull(repository.toCompany("any"));
	}

	@Test
	public void toCompanyNoCaptureNoMatch() {
		repository.setCompanyPattern("[^,]+,ou=[^,]+,.*");
		Assertions.assertEquals("[^,]+,ou=[^,]+,.*", repository.toCompany("company"));
	}

	@Test
	public void toCompanyNoCaptureMatch() {
		repository.setCompanyPattern("[^,]+,ou=[^,]+,.*");
		Assertions.assertNull(repository.toCompany("uid=some,ou=company,ou=fr"));
	}

	@Test
	public void toCompanyContant() {
		repository.setCompanyPattern("const");
		Assertions.assertEquals("const", repository.toCompany("uid=some,ou=company,dc=ex,dc=fr"));
	}

	@Test
	public void toCompany() {
		repository.setCompanyPattern("[^,]+,ou=([^,]+),.*");
		Assertions.assertEquals("company", repository.toCompany("uid=some,ou=company,dc=ex,dc=fr"));
		Assertions.assertEquals("company", repository.toCompany("uid=some,ou=company,dc=ex"));
	}

	@Test
	public void getAuthenticateProperty() {
		repository.setUidAttribute("my-uid");
		Assertions.assertEquals("my-uid", repository.getAuthenticateProperty("some"));
	}

	@Test
	public void getAuthenticatePropertyMail() {
		Assertions.assertEquals("mail", repository.getAuthenticateProperty("my@mail.com"));
	}

	@Test
	public void digest() {
		UserOrg user = new UserOrg();
		user.setDn("dc=sample,dc=com");
		new UserLdapRepository() {
			@Override
			public void set(final Name dn, final String attribute, final String value) {
				Assertions.assertTrue(value.startsWith("{SSHA}"));
			}

		}.setPassword(user, "test");
	}

	@Test
	public void toUser() {
		UserLdapRepository repository = new UserLdapRepository() {
			@Override
			public UserOrg findById(final String login) {
				final UserOrg userLdap = new UserOrg();
				userLdap.setId(login);
				userLdap.setFirstName("First");
				return userLdap;
			}

		};
		Assertions.assertEquals("user1", repository.toUser("user1").getId());
		Assertions.assertEquals("First", repository.toUser("user1").getFirstName());
	}

	@Test
	public void toUserNotExist() {
		UserLdapRepository repository = new UserLdapRepository() {
			@Override
			public UserOrg findById(final String login) {
				return null;
			}

		};
		Assertions.assertEquals("user1", repository.toUser("user1").getId());
		Assertions.assertNull(repository.toUser("user1").getFirstName());
	}

	@Test
	public void toUserNull() {
		Assertions.assertNull(repository.toUser(null));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void getToken() {
		final LdapTemplate mock = Mockito.mock(LdapTemplate.class);
		final DirContextOperations dirCtx = Mockito.mock(DirContextOperations.class);
		Mockito.when(mock.search((String) ArgumentMatchers.any(), ArgumentMatchers.any(),
				(AbstractContextMapper<String>) ArgumentMatchers.any())).thenAnswer(i -> {
					((AbstractContextMapper<DirContextOperations>) i.getArgument(2)).mapFromContext(dirCtx);
					return Collections.singletonList("token");
				});
		repository.setTemplate(mock);
		Assertions.assertEquals("token", repository.getToken("user1"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void getTokenNotExists() {
		final LdapTemplate mock = Mockito.mock(LdapTemplate.class);
		Mockito.when(mock.search((String) ArgumentMatchers.any(), ArgumentMatchers.any(),
				ArgumentMatchers.any(ContextMapper.class))).thenReturn(Collections.emptyList());
		repository.setTemplate(mock);
		Assertions.assertNull(repository.getToken("any"));
	}

	@Test
	public void findByIdExpectedNotVisibleCompany() {

		UserLdapRepository repository = new UserLdapRepository() {
			@Override
			public UserOrg findById(final String login) {
				UserOrg user = new UserOrg();
				user.setId(login);
				return user;
			}
		};
		repository.setCompanyRepository(Mockito.mock(CompanyLdapRepository.class));
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			repository.findByIdExpected("user1", "user2");
		}), "id", "unknown-id");
	}

	@Test
	public void findByIdExpected() {

		UserLdapRepository repository = new UserLdapRepository() {
			@Override
			public UserOrg findById(final String login) {
				UserOrg user = new UserOrg();
				user.setId(login);
				user.setCompany("company");
				return user;
			}
		};
		CompanyLdapRepository mock = Mockito.mock(CompanyLdapRepository.class);
		Mockito.when(mock.findById("user1", "company")).thenReturn(new CompanyOrg("", ""));
		repository.setCompanyRepository(mock);
		repository.findByIdExpected("user1", "user2");
	}

	@Test
	public void setPassword() throws NamingException {
		final LdapContext mockCtx = setPassword("old-password", "new-password");
		Mockito.verify(mockCtx).modifyAttributes(ArgumentMatchers.eq("cn=Any"),
				ArgumentMatchers.any(ModificationItem[].class));
		Mockito.verify(mockCtx).addToEnvironment("java.naming.security.credentials", "old-password");
	}

	@Test
	public void setPasswordNullOldPassword() throws NamingException {
		final LdapContext mockCtx = setPassword(null, "new-password");
		Mockito.verify(mockCtx).modifyAttributes(ArgumentMatchers.eq("cn=Any"),
				ArgumentMatchers.any(ModificationItem[].class));
		Mockito.verify(mockCtx).addToEnvironment(ArgumentMatchers.eq("java.naming.security.credentials"),
				ArgumentMatchers.anyString());
	}

	@Test
	public void setPasswordBadPassword() throws NamingException {
		final UserOrg user = new UserOrg();
		user.setDn("cn=Any");
		final LdapContext mockCtx = newLdapContext();
		Mockito.doThrow(new AuthenticationException()).when(mockCtx).modifyAttributes(ArgumentMatchers.eq("cn=Any"),
				ArgumentMatchers.any(ModificationItem[].class));
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class,
				() -> repository.setPassword(user, "wrong-old-password", "new-password")), "password", "login");
	}

	@Test
	public void setPasswordPolicyFail() throws NamingException {
		final UserOrg user = new UserOrg();
		user.setDn("cn=Any");
		final LdapContext mockCtx = newLdapContext();
		Mockito.doThrow(new InvalidAttributeValueException()).when(mockCtx)
				.modifyAttributes(ArgumentMatchers.eq("cn=Any"), ArgumentMatchers.any(ModificationItem[].class));
		MatcherUtil.assertThrows(
				Assertions.assertThrows(ValidationJsonException.class,
						() -> repository.setPassword(user, "old-password", "weak-password")),
				"new-password", "password-policy");
	}

	private LdapContext setPassword(final String password, final String newPassword) {
		final UserOrg user = new UserOrg();
		user.setDn("cn=Any");
		final LdapContext mockCtx = newLdapContext();
		repository.setPassword(user, password, newPassword);
		return mockCtx;
	}

	@SuppressWarnings("unchecked")
	private LdapContext newLdapContext() {
		final LdapTemplate mock = Mockito.mock(LdapTemplate.class);
		repository.setTemplate(mock);

		final LdapContext mockCtx = Mockito.mock(LdapContext.class);
		Mockito.when(mock.executeReadWrite((ContextExecutor<Object>) ArgumentMatchers.any(ContextExecutor.class)))
				.then(new Answer<Object>() {
					@Override
					public Object answer(final InvocationOnMock invocation) throws Throwable {
						return ((ContextExecutor<Object>) invocation.getArgument(0)).executeWithContext(mockCtx);
					}
				});
		return mockCtx;
	}

	@Test
	public void testValidLdapDate() {
		final String ldapDate = "20180206102244Z";
		Assertions.assertEquals(1517908964000L, repository.parseLdapDate(ldapDate).getTime());
	}

	@Test
	public void testNotValidLdapDate() {
		final String ldapDate = "20180206102244";
		Assertions.assertThrows(BusinessException.class, () -> repository.parseLdapDate(ldapDate).getTime());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void checkUserStatus() {
		final UserOrg user = new UserOrg();
		final LdapTemplate mock = Mockito.mock(LdapTemplate.class);
		final DirContextOperations dirCtx = Mockito.mock(DirContextOperations.class);
		Mockito.when(mock.search((String) ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq(2),
				ArgumentMatchers.any(), (AbstractContextMapper<UserOrg>) ArgumentMatchers.any())).thenAnswer(i -> {
					((AbstractContextMapper<DirContextOperations>) i.getArgument(4)).mapFromContext(dirCtx);
					user.setLocked(new Date(1517908964000L));
					user.setLockedBy("_ppolicy");
					return null;
				});
		Mockito.when(dirCtx.attributeExists(ArgumentMatchers.any())).thenReturn(true);
		Mockito.when(dirCtx.getStringAttribute(ArgumentMatchers.any())).thenReturn("20180206102244Z");
		repository.setTemplate(mock);
		repository.checkLockStatus(user);

		Assertions.assertEquals(1517908964000L, user.getLocked().getTime());
	}
	
	@Test
	public void checkUserStatus1() {
		final UserOrg user = new UserOrg();
		final LdapTemplate mock = Mockito.mock(LdapTemplate.class);
		final DirContextOperations dirCtx = Mockito.mock(DirContextOperations.class);
		Mockito.when(mock.search((String) ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq(2),
				ArgumentMatchers.any(), (AbstractContextMapper<UserOrg>) ArgumentMatchers.any())).thenAnswer(i -> {
					((AbstractContextMapper<DirContextOperations>) i.getArgument(4)).mapFromContext(dirCtx);
					user.setLocked(new Date(1517908964000L));
					user.setLockedBy("_ppolicy");
					return  Collections.singletonList(user);
				});
		Mockito.when(dirCtx.getDn()).thenReturn(org.springframework.ldap.support.LdapUtils.newLdapName("cn=Any"));
		Mockito.when(dirCtx.attributeExists(ArgumentMatchers.any())).thenReturn(true);
		Mockito.when(dirCtx.getStringAttribute(ArgumentMatchers.any())).thenReturn("20180206102244Z");
		repository.setTemplate(mock);
		final Map<String, GroupOrg> groups = MapUtils.EMPTY_SORTED_MAP;
		repository.findAllNoCache(groups);

		Assertions.assertEquals(1517908964000L, user.getLocked().getTime());
	}

}

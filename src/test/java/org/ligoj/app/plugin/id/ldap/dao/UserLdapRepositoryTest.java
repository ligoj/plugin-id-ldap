package org.ligoj.app.plugin.id.ldap.dao;

import java.util.Collections;

import javax.naming.Name;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.plugin.id.ldap.dao.CompanyLdapRepository;
import org.ligoj.app.plugin.id.ldap.dao.UserLdapRepository;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.AbstractContextMapper;

/**
 * Test class of {@link UserLdapRepository}
 */
public class UserLdapRepositoryTest {

	private UserLdapRepository repository;

	@Before
	public void init() {
		repository = new UserLdapRepository();
	}

	@Test
	public void toCompanyNoMatch() {
		repository.setCompanyPattern("[^,]+,ou=([^,]+),.*");
		Assert.assertNull(repository.toCompany("any"));
	}

	@Test
	public void toCompanyNoCaptureNoMatch() {
		repository.setCompanyPattern("[^,]+,ou=[^,]+,.*");
		Assert.assertEquals("[^,]+,ou=[^,]+,.*", repository.toCompany("company"));
	}

	@Test
	public void toCompanyNoCaptureMatch() {
		repository.setCompanyPattern("[^,]+,ou=[^,]+,.*");
		Assert.assertNull(repository.toCompany("uid=some,ou=company,ou=fr"));
	}

	@Test
	public void toCompanyContant() {
		repository.setCompanyPattern("const");
		Assert.assertEquals("const", repository.toCompany("uid=some,ou=company,dc=ex,dc=fr"));
	}

	@Test
	public void toCompany() {
		repository.setCompanyPattern("[^,]+,ou=([^,]+),.*");
		Assert.assertEquals("company", repository.toCompany("uid=some,ou=company,dc=ex,dc=fr"));
		Assert.assertEquals("company", repository.toCompany("uid=some,ou=company,dc=ex"));
	}

	@Test
	public void getAuthenticateProperty() {
		repository.setUidAttribute("my-uid");
		Assert.assertEquals("my-uid", repository.getAuthenticateProperty("some"));
	}

	@Test
	public void getAuthenticatePropertyMail() {
		Assert.assertEquals("mail", repository.getAuthenticateProperty("my@mail.com"));
	}

	@Test
	public void digest() {
		UserOrg user = new UserOrg();
		user.setDn("dc=sample,dc=com");
		new UserLdapRepository() {
			@Override
			public void set(final Name dn, final String attribute, final String value) {
				Assert.assertTrue(value.startsWith("{SSHA}"));
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
		Assert.assertEquals("user1", repository.toUser("user1").getId());
		Assert.assertEquals("First", repository.toUser("user1").getFirstName());
	}

	@Test
	public void toUserNotExist() {
		UserLdapRepository repository = new UserLdapRepository() {
			@Override
			public UserOrg findById(final String login) {
				return null;
			}

		};
		Assert.assertEquals("user1", repository.toUser("user1").getId());
		Assert.assertNull(repository.toUser("user1").getFirstName());
	}

	@Test
	public void toUserNull() {
		Assert.assertNull(repository.toUser(null));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void getToken() {
		final LdapTemplate mock = Mockito.mock(LdapTemplate.class);
		final DirContextOperations dirCtx = Mockito.mock(DirContextOperations.class);
		Mockito.when(mock.search((String) ArgumentMatchers.any(), ArgumentMatchers.any(), (AbstractContextMapper<String>) ArgumentMatchers.any()))
				.thenAnswer(i -> {
					((AbstractContextMapper<DirContextOperations>) i.getArgument(2)).mapFromContext(dirCtx);
					return Collections.singletonList("token");
				});
		repository.setTemplate(mock);
		Assert.assertEquals("token", repository.getToken("user1"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void getTokenNotExists() {
		final LdapTemplate mock = Mockito.mock(LdapTemplate.class);
		Mockito.when(mock.search((String) ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(ContextMapper.class)))
				.thenReturn(Collections.emptyList());
		repository.setTemplate(mock);
		Assert.assertNull(repository.getToken("any"));
	}

	@Test(expected = ValidationJsonException.class)
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
		repository.findByIdExpected("user1", "user2");
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
}

package org.ligoj.app.ldap.dao;

import javax.naming.Name;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ligoj.app.api.UserLdap;
import org.ligoj.app.ldap.dao.UserLdapRepository;

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
		repository.setUid("my-uid");
		Assert.assertEquals("my-uid", repository.getAuthenticateProperty("some"));
	}

	@Test
	public void getAuthenticatePropertyMail() {
		Assert.assertEquals("mail", repository.getAuthenticateProperty("my@mail.com"));
	}

	@Test
	public void digest() {
		UserLdap user = new UserLdap();
		user.setDn("dc=sample,dc=com");
		new UserLdapRepository() {
			@Override
			public void set(final Name dn, final String attribute, final String value) {
				Assert.assertTrue(value.startsWith("{SSHA}"));
			}

		}.setPassword(user, "test");
	}
}

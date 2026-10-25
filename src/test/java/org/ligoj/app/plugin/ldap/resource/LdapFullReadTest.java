/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.ldap.resource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;

import javax.naming.directory.SearchControls;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.IamConfiguration;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.plugin.ldap.dao.AbstractManagedLdapRepository;
import org.ligoj.app.plugin.ldap.dao.CompanyLdapRepository;
import org.ligoj.app.plugin.ldap.dao.GroupLdapRepository;
import org.ligoj.app.plugin.ldap.dao.UserLdapRepository;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.ContextMapper;

/**
 * The full read of LDAP: every page of users, the password presence without the password hashes, and only the
 * attributes Ligoj maps for the groups and the companies.
 */
class LdapFullReadTest extends AbstractLdapPluginResourceTest {

	@Autowired
	private IamProvider[] iamProviders;

	private IamConfiguration configuration() {
		return iamProviders[0].getConfiguration();
	}

	private UserLdapRepository users() {
		return (UserLdapRepository) configuration().getUserRepository();
	}

	private GroupLdapRepository groups() {
		return (GroupLdapRepository) configuration().getGroupRepository();
	}

	@Test
	void findAllNoCachePages() {
		final var all = users().findAllNoCache(groups().findAllNoCache()).keySet();
		Assertions.assertTrue(all.size() > 2);
		users().setPageSize(2);
		try {
			Assertions.assertEquals(all, users().findAllNoCache(groups().findAllNoCache()).keySet());
		} finally {
			users().setPageSize(500);
		}
	}

	@Test
	void findAllNoCacheSecured() {
		final var users = users().findAllNoCache(groups().findAllNoCache());
		Assertions.assertTrue(users.get("fdaugan").isSecured());
		Assertions.assertTrue(users.get("jdoe5").isSecured());
		Assertions.assertTrue(users.get("mmartin").isSecured());
		// The users of the sample without password
		Assertions.assertFalse(users.get("jdoe4").isSecured());
		Assertions.assertFalse(users.get("mlavoine").isSecured());
		Assertions.assertFalse(users.get("mtuyer").isSecured());
	}

	@Test
	void findAllNoCacheNoPasswordHash() {
		Assertions.assertFalse(Arrays.asList(users().getFullReadAttributes()).contains("userPassword"));
	}

	@Test
	void findAllNoCacheGroupAttributes() {
		Assertions.assertEquals(Set.of("cn", "uniqueMember"), returnedAttributes(groups(), groups()::findAllNoCache));
	}

	@Test
	void findAllNoCacheCompanyAttributes() {
		final var companies = (CompanyLdapRepository) configuration().getCompanyRepository();
		Assertions.assertEquals(Set.of("ou"), returnedAttributes(companies, companies::findAllNoCache));
	}

	/**
	 * The attributes requested by the search of a full read, through a spied template.
	 */
	@SuppressWarnings("unchecked")
	private Set<String> returnedAttributes(final AbstractManagedLdapRepository<?> repository, final Supplier<?> fullRead) {
		final var template = repository.getTemplate();
		final var spied = spy(template);
		repository.setTemplate(spied);
		try {
			fullRead.get();
			final var controls = ArgumentCaptor.forClass(SearchControls.class);
			verify(spied, atLeastOnce()).search(anyString(), anyString(), controls.capture(), any(ContextMapper.class));
			return Set.of(controls.getValue().getReturningAttributes());
		} finally {
			repository.setTemplate(template);
		}
	}
}

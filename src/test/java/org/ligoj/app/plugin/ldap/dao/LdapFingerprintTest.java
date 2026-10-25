/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.ldap.dao;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.IamConfiguration;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.plugin.ldap.resource.AbstractLdapPluginResourceTest;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The fingerprint of an LDAP configuration: another node, server or setting shaping the data means another copy.
 */
class LdapFingerprintTest extends AbstractLdapPluginResourceTest {

	@Autowired
	private IamProvider[] iamProviders;

	private IamConfiguration configuration() {
		return iamProviders[0].getConfiguration();
	}

	private String fingerprint(final IamConfiguration configuration) {
		return AopTestUtils.<CacheLdapRepository>getTargetObject(cache).fingerprint(configuration);
	}

	@Test
	void fingerprintFollowsConfiguration() {
		final var configuration = configuration();
		final var before = fingerprint(configuration);
		Assertions.assertEquals(64, before.length());
		Assertions.assertEquals(before, fingerprint(configuration));

		// Another node
		final var other = new IamConfiguration();
		other.setNode("service:id:ldap:other");
		other.setUserRepository(configuration.getUserRepository());
		other.setGroupRepository(configuration.getGroupRepository());
		other.setCompanyRepository(configuration.getCompanyRepository());
		Assertions.assertNotEquals(before, fingerprint(other));

		final var users = (UserLdapRepository) configuration.getUserRepository();
		final var groups = (GroupLdapRepository) configuration.getGroupRepository();
		final var customAttributes = users.getCustomAttributes();
		final var memberAttribute = ReflectionTestUtils.getField(groups, "memberAttribute");
		try {
			// An added custom attribute name
			users.setCustomAttributes(new String[] { "added" });
			Assertions.assertNotEquals(before, fingerprint(configuration));
			users.setCustomAttributes(customAttributes);
			Assertions.assertEquals(before, fingerprint(configuration));

			// Another group member attribute
			groups.setMemberAttribute("member");
			Assertions.assertNotEquals(before, fingerprint(configuration));
		} finally {
			users.setCustomAttributes(customAttributes);
			groups.setMemberAttribute((String) memberAttribute);
		}
	}

	/**
	 * A context source of another kind, such as a pooling one: the fingerprint has no server part, and stays stable.
	 */
	@Test
	void fingerprintWithAnotherContextSource() {
		final var users = (UserLdapRepository) configuration().getUserRepository();
		final var template = users.getTemplate();
		final var before = fingerprint(configuration());
		try {
			final var other = Mockito.mock(LdapTemplate.class);
			Mockito.when(other.getContextSource()).thenReturn(Mockito.mock(ContextSource.class));
			users.setTemplate(other);
			Assertions.assertNotEquals(before, fingerprint(configuration()));
			Assertions.assertEquals(fingerprint(configuration()), fingerprint(configuration()));
		} finally {
			users.setTemplate(template);
		}
	}
}

package org.ligoj.app.plugin.id.resource;

import javax.cache.annotation.CacheResult;

import org.ligoj.app.iam.IamConfiguration;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.plugin.id.ldap.resource.LdapPluginResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/**
 * LDAP IAM provider.
 */
public class LdapIamProvider implements IamProvider {

	@Autowired
	protected LdapPluginResource resource;

	@Override
	public Authentication authenticate(final Authentication authentication) {

		// Primary authentication
		return resource.authenticate(authentication, "service:id:ldap:dig", true);
	}

	@Override
	@CacheResult(cacheName = "iam-ldap-configuration")
	public IamConfiguration getConfiguration() {
		return resource.getConfiguration("service:id:ldap:dig");
	}

}

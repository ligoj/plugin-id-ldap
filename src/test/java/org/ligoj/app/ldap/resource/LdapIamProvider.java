package org.ligoj.app.ldap.resource;

import javax.cache.annotation.CacheResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import org.ligoj.app.iam.IamConfiguration;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.plugin.id.ldap.resource.LdapPluginResource;

/**
 * LDAP IAM provider.
 */
@Component
public class LdapIamProvider implements IamProvider {

	@Autowired
	protected LdapPluginResource resource;

	@Override
	public Authentication authenticate(final Authentication authentication) throws Exception {

		// Primary authentication
		return resource.authenticate(authentication, "service:id:ldap:dig", true);
	}

	@Override
	@CacheResult(cacheName = "iam-node-configuration")
	public IamConfiguration getConfiguration() {
		return resource.getConfiguration("service:id:ldap:dig");
	}

}

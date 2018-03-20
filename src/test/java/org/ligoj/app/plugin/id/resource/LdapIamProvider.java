package org.ligoj.app.plugin.id.resource;

import java.util.Optional;

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

	private IamConfiguration iamConfiguration;

	@Autowired
	private LdapIamProvider self;

	@Override
	public Authentication authenticate(final Authentication authentication) {
		// Primary authentication
		return resource.authenticate(authentication, "service:id:ldap:dig", true);
	}

	@Override
	public IamConfiguration getConfiguration() {
		self.ensureCachedConfiguration();
		return Optional.ofNullable(iamConfiguration).orElseGet(this::refreshConfiguration);
	}

	@CacheResult(cacheName = "iam-ldap-configuration")
	public boolean ensureCachedConfiguration() {
		refreshConfiguration();
		return true;
	}

	private IamConfiguration refreshConfiguration() {
		this.iamConfiguration = resource.getConfiguration("service:id:ldap:dig");
		return this.iamConfiguration;
	}

}

package org.ligoj.app.plugin.id.ldap.resource;

import java.util.function.Function;

import org.ligoj.bootstrap.resource.system.cache.CacheManagerAware;
import org.springframework.stereotype.Component;

import com.hazelcast.cache.HazelcastCacheManager;
import com.hazelcast.config.CacheConfig;

/**
 * Cache configuration for LDAP.
 */
@Component
public class IdLdapCache implements CacheManagerAware {

	@Override
	public void onCreate(final HazelcastCacheManager cacheManager, final Function<String, CacheConfig<?, ?>> provider) {
		cacheManager.createCache("ldap", provider.apply("ldap"));
		cacheManager.createCache("ldap-user-repository", provider.apply("ldap-user-repository"));
		cacheManager.createCache("customers", provider.apply("customers"));
		cacheManager.createCache("customers-by-id", provider.apply("customers"));
	}

}

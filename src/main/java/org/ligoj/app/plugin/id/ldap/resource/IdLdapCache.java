/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.resource;

import com.hazelcast.cache.HazelcastCacheManager;
import com.hazelcast.config.CacheConfig;
import org.ligoj.bootstrap.resource.system.cache.CacheManagerAware;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Cache configuration for LDAP.
 */
@Component
public class IdLdapCache implements CacheManagerAware {

	@Override
	public void onCreate(final HazelcastCacheManager cacheManager, final Function<String, CacheConfig<?, ?>> provider) {
		cacheManager.createCache("id-ldap-data", provider.apply("id-ldap-data"));
		cacheManager.createCache("customers", provider.apply("customers"));
		cacheManager.createCache("customers-by-id", provider.apply("customers-by-id"));
	}

}

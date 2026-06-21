/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.resource;

import com.hazelcast.cache.HazelcastCacheManager;
import org.ligoj.bootstrap.resource.system.cache.CacheConfigurer;
import org.ligoj.bootstrap.resource.system.cache.CacheManagerAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.stereotype.Component;

import javax.cache.expiry.Duration;

/**
 * Cache configuration for LDAP.
 */
@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class IdLdapCache implements CacheManagerAware {

	@Override
	public void onCreate(final HazelcastCacheManager cacheManager, final CacheConfigurer configurer) {
		cacheManager.createCache("id-ldap-data", configurer.newCacheConfig("id-ldap-data", Duration.ONE_DAY));
		cacheManager.createCache("customers", configurer.newCacheConfig("customers", Duration.ONE_HOUR));
		cacheManager.createCache("customers-by-id", configurer.newCacheConfig("customers-by-id", Duration.ONE_HOUR));
	}

}

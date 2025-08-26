/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.dao;

import java.util.Map;
import java.util.Optional;

import javax.cache.annotation.CacheResult;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.ResourceOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.plugin.id.dao.AbstractMemCacheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * LDAP in-memory cache with JPA back-end cache.
 */
@Component
@Slf4j
public class CacheLdapRepository extends AbstractMemCacheRepository {

	@Autowired
	protected CacheLdapRepository self = this;

	/**
	 * Reset the database cache with the LDAP data. Note there is no synchronization for this method. Initial first
	 * concurrent calls may not involve the cache.
	 *
	 * @return The cached LDAP data..
	 */
	@Override
	public Map<CacheDataType, Map<String, ? extends ResourceOrg>> getData() {
		self.ensureCachedData();
		return Optional.ofNullable(data).orElseGet(this::refreshData);
	}

	/**
	 * Ensure the fresh data computed when there is no cached LDAP data.
	 *
	 * @return <code>true</code>, required by JSR-107.
	 */
	@CacheResult(cacheName = "id-ldap-data")
	public boolean ensureCachedData() {
		refreshData();
		return true;
	}

	private final Object cacheLock = new Object();

	@SuppressWarnings("unchecked")
	@Override
	protected Map<CacheDataType, Map<String, ? extends ResourceOrg>> refreshData() {
		final var result = super.refreshData();
		final var refreshTime = cache.getCacheRefreshTime();
		log.info("Refresh cache requested, age is {}",
				DurationFormatUtils.formatDurationHMS(System.currentTimeMillis() - refreshTime));
		synchronized (cacheLock) {
			if (refreshTime < cache.getCacheRefreshTime()) {
				// Ignore subsequent refresh
				log.info("Another refresh just finished, new age is {}",
						DurationFormatUtils.formatDurationHMS(System.currentTimeMillis() - cache.getCacheRefreshTime()));
			} else {
				cache.reset((Map<String, CompanyOrg>) result.get(CacheDataType.COMPANY),
						(Map<String, GroupOrg>) result.get(CacheDataType.GROUP),
						(Map<String, UserOrg>) result.get(CacheDataType.USER));
			}
		}
		return result;
	}
}

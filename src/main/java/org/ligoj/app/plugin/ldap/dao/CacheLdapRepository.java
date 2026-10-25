/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.ldap.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.cache.annotation.CachePut;
import javax.cache.annotation.CacheResult;
import javax.cache.annotation.CacheValue;

import org.apache.commons.codec.digest.DigestUtils;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.IamConfiguration;
import org.ligoj.app.iam.model.CacheCompany;
import org.ligoj.app.plugin.id.dao.AbstractCopyMemCacheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.support.AbstractContextSource;
import org.springframework.stereotype.Component;

/**
 * LDAP in-memory cache with JPA back-end cache. After a restart, the memory is rebuilt from the database copy of the
 * last full read while it is younger than the TTL of the {@value #CACHE} cache, without reading LDAP.
 */
@Component
public class CacheLdapRepository extends AbstractCopyMemCacheRepository {

	/**
	 * The cache whose single entry holds the time of the last full read, and whose TTL is the period of the full reads.
	 */
	public static final String CACHE = "id-ldap-data";

	@Autowired
	protected CacheLdapRepository self = this;

	@Override
	@CacheResult(cacheName = CACHE)
	public long ensureCachedData() {
		return ensureData();
	}

	@Override
	@CachePut(cacheName = CACHE)
	public void arm(@CacheValue final long lastFullRead) {
		// Only the cache entry is updated
	}

	@Override
	protected AbstractCopyMemCacheRepository self() {
		return self;
	}

	@Override
	protected String getCacheName() {
		return CACHE;
	}

	/**
	 * Only an LDAP primary node has its copy here: another identity provider keeps its own cache.
	 */
	@Override
	protected boolean isProvider(final IamConfiguration configuration) {
		return configuration.getUserRepository() instanceof UserLdapRepository;
	}

	/**
	 * Return the fingerprint of the configuration shaping the data: the node, the LDAP server, and the settings of the
	 * repositories.
	 *
	 * @return The SHA-256 of these values, in hexadecimal.
	 */
	@Override
	protected String fingerprint(final IamConfiguration configuration) {
		final var users = (UserLdapRepository) configuration.getUserRepository();
		final var values = new ArrayList<String>();
		values.add(configuration.getNode());
		if (users.getTemplate().getContextSource() instanceof AbstractContextSource source) {
			values.add(String.join(",", source.getUrls()));
			values.add(source.getBaseLdapPathAsString());
		}
		values.addAll(users.getSettings());
		values.addAll(((GroupLdapRepository) configuration.getGroupRepository()).getSettings());
		values.addAll(((CompanyLdapRepository) configuration.getCompanyRepository()).getSettings());
		return DigestUtils.sha256Hex(values.stream().map(Objects::toString).collect(Collectors.joining("\n")));
	}

	/**
	 * Rebuild the companies as the LDAP full read builds them: the locked quarantine company, the LDAP names and the
	 * hierarchy.
	 */
	@Override
	protected Map<String, CompanyOrg> toCompanies(final IamConfiguration configuration, final Collection<CacheCompany> rows) {
		return ((CompanyLdapRepository) configuration.getCompanyRepository()).toCompanies(rows);
	}
}

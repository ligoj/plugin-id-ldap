/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.dao;

import lombok.extern.slf4j.Slf4j;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.plugin.id.DnUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.stereotype.Component;

import javax.cache.annotation.*;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ligoj.app.plugin.id.ldap.dao.AbstractManagedLdapRepository.OBJECT_CLASS;

/**
 * Repository for Customers of Project management in LDAP. Are OU of GROUP of type Project.
 */
@Component
@Slf4j
public class ProjectCustomerLdapRepository {

	private static final String CUSTOMER_OF_PROJECT = "organizationalUnit";

	@Autowired
	private IamProvider[] iamProvider;

	@Autowired
	protected CacheManager cacheManager;

	/**
	 * Fetch and return all normalized customers for projects. Note the result use cache, so does not reflect the LDAP.
	 * current state of LDAP.
	 *
	 * @param baseDn Base DN.
	 * @return all normalized customers for projects. Note the result use cache, so does not reflect the LDAP. current
	 * state of LDAP. Key is the normalized name, Value is the DN.
	 */
	@CacheResult(cacheName = "customers")
	public Set<String> findAll(@CacheKey final String baseDn) {
		return getUser().getTemplate()
				.search(baseDn, new EqualsFilter(OBJECT_CLASS, CUSTOMER_OF_PROJECT).encode(),
						(Object ctx) -> (DirContextAdapter) ctx)
				.stream().map(g -> DnUtils.toRdn(g.getDn().toString())).collect(Collectors.toSet());
	}

	/**
	 * Fetch the DN of the customer having the requested identifier.
	 *
	 * @param baseDn Base DN.
	 * @param id     The request customer identifier.
	 * @return all normalized customers for projects. Key is the DN, Value is the normalized name.
	 */
	@CacheResult(cacheName = "customers-by-id")
	public String findById(@CacheKey final String baseDn, @CacheKey final String id) {
		final var filter = new AndFilter().and(new EqualsFilter(OBJECT_CLASS, CUSTOMER_OF_PROJECT))
				.and(new EqualsFilter("ou", id));
		return getUser().getTemplate().search(baseDn, filter.encode(), (Object ctx) -> (DirContextAdapter) ctx).stream()
				.findAny().map(g -> g.getDn().toString()).orElse(null);
	}

	/**
	 * Create a new organizational unit. There is no synchronized block, so error could occur; this is assumed for performance
	 * purpose.
	 *
	 * @param baseDn Base DN.
	 * @param ou     The formatted OU.
	 * @param dn     The DN of new customer. Must ends with the OU.
	 */
	@CachePut(cacheName = "customers-by-id")
	public void create(@CacheKey final String baseDn, @CacheKey final String ou, @CacheValue final String dn) {
		// Invalidate the customers set
		cacheManager.getCache("customers").evict(baseDn);

		// First create the LDAP entry
		log.info("Customer (OU) {} will be created as {}", ou, dn);
		final var context = new DirContextAdapter(dn);
		context.setAttributeValues(OBJECT_CLASS, new String[]{CUSTOMER_OF_PROJECT});
		mapToContext(ou, context);
		getUser().getTemplate().bind(context);
	}

	/**
	 * Delete the given organizational unit.
	 *
	 * @param baseDn Base DN.
	 * @param ou     The formatted OU.
	 * @param dn     The DN of new customer. Must ends with the OU.
	 */
	@CacheRemove(cacheName = "customers-by-id")
	public void delete(@CacheKey final String baseDn, @CacheKey final String ou, final String dn) {
		// Invalidate the customers set
		cacheManager.getCache("customers").evict(baseDn);

		// First create the LDAP entry
		log.info("Customer (OU) {} will be deleted from {}", ou, dn);
		getUser().getTemplate().unbind(dn);
	}

	/**
	 * Map a customer to LDAP.
	 *
	 * @param ou      The OU to map.
	 * @param context The target context to fill.
	 */
	protected void mapToContext(final String ou, final DirContextOperations context) {
		context.setAttributeValue("ou", ou);
	}

	/**
	 * User repository provider.
	 *
	 * @return User repository provider.
	 */
	private UserLdapRepository getUser() {
		return (UserLdapRepository) iamProvider[0].getConfiguration().getUserRepository();
	}
}

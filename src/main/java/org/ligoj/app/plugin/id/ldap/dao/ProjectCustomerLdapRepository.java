package org.ligoj.app.plugin.id.ldap.dao;

import java.util.HashMap;
import java.util.Map;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;

import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.plugin.id.DnUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Repository for Customers of Project management in LDAP. Are OU of GROUP of type Project.
 */
@Component
@Slf4j
public class ProjectCustomerLdapRepository {

	private static final String CUSTOMER_OF_PROJECT = "organizationalUnit";

	@Autowired
	private IamProvider iamProvider;

	/**
	 * Fetch and return all normalized customers for projects. Note the result use cache, so does not reflect the LDAP.
	 * current state of LDAP.
	 * 
	 * @param baseDn
	 *            Base DN.
	 * @return all normalized customers for projects. Note the result use cache, so does not reflect the LDAP.
	 *         current state of LDAP. Key is the normalized name, Value is the DN.
	 */
	@CacheResult(cacheName = "customers")
	public Map<String, String> findAll(@CacheKey final String baseDn) {
		return findAllNoCache(baseDn);
	}

	/**
	 * Fetch and return all normalized customers for projects.
	 * 
	 * @param baseDn
	 *            Base DN.
	 * @return all normalized customers for projects. Key is the DN, Value is the normalized name.
	 */
	public Map<String, String> findAllNoCache(final String baseDn) {
		final Map<String, String> result = new HashMap<>();
		for (final DirContextAdapter groupRaw : getUser().getTemplate().search(baseDn, new EqualsFilter("objectClass", CUSTOMER_OF_PROJECT).encode(),
				(Object ctx) -> (DirContextAdapter) ctx)) {
			final String dn = groupRaw.getDn().toString();
			result.put(DnUtils.toRdn(dn), dn);
		}
		return result;
	}

	/**
	 * Create a new group. There is no synchronized block, so error could occur; this is assumed for performance
	 * purpose.
	 * 
	 * @param dn
	 *            The DN of new customer. Must ends with the OU.
	 * @param ou
	 *            The formatted OU.
	 */
	public void create(final String dn, final String ou) {

		// First create the LDAP entry
		log.info("Customer (OU) {} will be created as {}", ou, dn);
		final DirContextAdapter context = new DirContextAdapter(dn);
		context.setAttributeValues("objectClass", new String[] { CUSTOMER_OF_PROJECT });
		mapToContext(ou, context);
		getUser().getTemplate().bind(context);
	}

	/**
	 * Map a customer to LDAP
	 */
	protected void mapToContext(final String customer, final DirContextOperations context) {
		context.setAttributeValue("ou", customer);
	}

	/**
	 * User repository provider.
	 * 
	 * @return User repository provider.
	 */
	private UserLdapRepository getUser() {
		return (UserLdapRepository) iamProvider.getConfiguration().getUserRepository();
	}
}

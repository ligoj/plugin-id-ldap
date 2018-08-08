/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.dao;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.naming.ldap.LdapName;

import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.ICompanyRepository;
import org.ligoj.app.iam.dao.CacheCompanyRepository;
import org.ligoj.app.iam.model.CacheCompany;
import org.ligoj.app.model.ContainerType;
import org.ligoj.app.plugin.id.DnUtils;
import org.ligoj.app.plugin.id.dao.AbstractMemCacheRepository.CacheDataType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;

import lombok.Setter;

/**
 * Company LDAP repository
 */
public class CompanyLdapRepository extends AbstractContainerLdapRepository<CompanyOrg, CacheCompany>
		implements ICompanyRepository {

	private static final String ORGANIZATIONAL_UNIT = "organizationalUnit";

	/**
	 * Companies location. May be equals to the people DN or more often a subtree of people OU.
	 */
	@Setter
	private String companyBaseDn;

	/**
	 * Special company that will contains the isolated accounts.
	 */
	@Setter
	private String quarantineBaseDn;

	@Autowired
	private CacheCompanyRepository cacheCompanyRepository;

	/**
	 * Default constructor for a container of type {@link ContainerType#COMPANY}
	 */
	public CompanyLdapRepository() {
		super(ContainerType.COMPANY, ORGANIZATIONAL_UNIT);
	}

	@Override
	public CacheCompanyRepository getCacheRepository() {
		return cacheCompanyRepository;
	}

	/**
	 * Fetch and return all normalized companies. Note the result uses cache, so does not reflect the current state of
	 * LDAP. Cache manager is involved.
	 *
	 * @return the companies. Key is the normalized name.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Map<String, CompanyOrg> findAll() {
		return (Map<String, CompanyOrg>) cacheRepository.getData().get(CacheDataType.COMPANY);
	}

	/**
	 * Fetch and return all normalized companies. Note the result use cache, so does not reflect the current state of
	 * LDAP.
	 *
	 * @return the companies. Key is the normalized name.
	 */
	@Override
	public Map<String, CompanyOrg> findAllNoCache() {
		final Map<String, CompanyOrg> companiesNameToDn = new HashMap<>();
		for (final DirContextAdapter ldap : template.search(companyBaseDn, "objectClass=" + ORGANIZATIONAL_UNIT,
				(Object ctx) -> (DirContextAdapter) ctx)) {
			final CompanyOrg company = new CompanyOrg(ldap.getDn().toString(), ldap.getStringAttributes("ou")[0]);
			companiesNameToDn.put(company.getId(), company);
		}

		// Also add/replace the quarantine zone
		final CompanyOrg quarantine = new CompanyOrg(quarantineBaseDn, getQuarantineCompany());
		quarantine.setLocked(true);
		companiesNameToDn.put(quarantine.getId(), quarantine);

		// The complete the hierarchy of companies
		companiesNameToDn.values().forEach(this::buildLdapName);
		companiesNameToDn.values().forEach(c -> this.buildHierarchy(companiesNameToDn, c));
		return companiesNameToDn;
	}

	/**
	 * Build the {@link LdapName} instance from the DN. This also requires a valid DN for the given {@link CompanyOrg}
	 */
	private void buildLdapName(final CompanyOrg company) {
		company.setLdapName(org.springframework.ldap.support.LdapUtils.newLdapName(company.getDn()));
	}

	/**
	 * Build the company hierarchy from the given {@link CompanyOrg}
	 */
	private void buildHierarchy(final Map<String, CompanyOrg> companies, final CompanyOrg company) {
		// Collect all parents and sorted from parent to the leaf
		company.setCompanyTree(
				companies.values().stream().filter(c -> DnUtils.equalsOrParentOf(c.getDn(), company.getDn()))
						.sorted(Comparator.comparing(CompanyOrg::getLdapName)).collect(Collectors.toList()));
	}

	/**
	 * Return the quarantine/isolated company identifier.
	 *
	 * @return The quarantine/isolated company identifier.
	 */
	public String getQuarantineCompany() {
		return DnUtils.toRdn(quarantineBaseDn);
	}

	@Override
	public CompanyOrg create(final String dn, final String cn) {
		return cacheRepository.create(super.create(dn, cn));
	}

	@Override
	protected CompanyOrg newContainer(final String dn, final String name) {
		return new CompanyOrg(dn.toLowerCase(Locale.ENGLISH), name);
	}

	/**
	 * Map {@link CompanyOrg} to LDAP
	 */
	@Override
	protected void mapToContext(final CompanyOrg entry, final DirContextOperations context) {
		context.setAttributeValue("ou", entry.getName());
	}

	/**
	 * Remove the company from the memory cache.
	 *
	 * @param company
	 *            The company to remove.
	 */
	private void removeFromJavaCache(final CompanyOrg company) {
		// Update the raw cache
		findAll().remove(company.getId());
	}

	@Override
	public void delete(final CompanyOrg container) {

		/*
		 * Remove from this company, all companies within (sub LDAP DN) this company. This operation is needed since we
		 * are not rebuilding the cache from the LDAP. This save a lot of computations.
		 */
		findAll().values().stream().filter(g -> DnUtils.equalsOrParentOf(container.getDn(), g.getDn()))
				.collect(Collectors.toList()).forEach(this::removeFromJavaCache);

		// Remove from LDAP the recursively the company. Anything that was not nicely cleaned will be deleted there.
		template.unbind(container.getDn(), true);

		// Also, update the SQL cache
		cacheRepository.delete(container);
	}

}

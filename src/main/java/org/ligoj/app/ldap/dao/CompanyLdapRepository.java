package org.ligoj.app.ldap.dao;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.naming.ldap.LdapName;

import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.ligoj.app.api.CompanyLdap;
import org.ligoj.app.iam.ICompanyRepository;
import org.ligoj.app.ldap.LdapUtils;
import org.ligoj.app.ldap.dao.LdapCacheRepository.LdapData;
import org.ligoj.app.ldap.model.ContainerType;

import lombok.Setter;

/**
 * Company LDAP repository
 */
public class CompanyLdapRepository extends AbstractContainerLdaRepository<CompanyLdap> implements ICompanyRepository {

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

	/**
	 * Default constructor for a container of type {@link ContainerType#COMPANY}
	 */
	public CompanyLdapRepository() {
		super(ContainerType.COMPANY, ORGANIZATIONAL_UNIT);
	}

	/**
	 * Fetch and return all normalized companies. Note the result uses cache, so does not reflect the current state of
	 * LDAP. Cache manager is involved.
	 * 
	 * @return the companies. Key is the normalized name.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Map<String, CompanyLdap> findAll() {
		return (Map<String, CompanyLdap>) ldapCacheRepository.getLdapData().get(LdapData.COMPANY);
	}

	/**
	 * Fetch and return all normalized companies. Note the result use cache, so does not reflect the current state of
	 * LDAP.
	 * 
	 * @return the companies. Key is the normalized name.
	 */
	public Map<String, CompanyLdap> findAllNoCache() {
		final Map<String, CompanyLdap> companiesNameToDn = new HashMap<>();
		for (final DirContextAdapter company : template.search(companyBaseDn, "objectClass=" + ORGANIZATIONAL_UNIT,
				(Object ctx) -> (DirContextAdapter) ctx)) {
			final CompanyLdap companyLdap = new CompanyLdap(company.getDn().toString(), company.getStringAttributes("ou")[0]);
			companiesNameToDn.put(companyLdap.getId(), companyLdap);
		}

		// Also add/replace the quarantine zone
		final CompanyLdap quarantine = new CompanyLdap(quarantineBaseDn, getQuarantineCompany());
		quarantine.setLocked(true);
		companiesNameToDn.put(quarantine.getId(), quarantine);

		// The complete the hierarchy of companies
		companiesNameToDn.values().forEach(this::buildLdapName);
		companiesNameToDn.values().forEach(c -> this.buildHierarchy(companiesNameToDn, c));
		return companiesNameToDn;
	}

	/**
	 * Build the {@link LdapName} instance from the DN. This also requires a valid DN for the given {@link CompanyLdap}
	 */
	private void buildLdapName(final CompanyLdap company) {
		company.setLdapName(org.springframework.ldap.support.LdapUtils.newLdapName(company.getDn()));
	}

	/**
	 * Build the company hierarchy from the given {@link CompanyLdap}
	 */
	private void buildHierarchy(final Map<String, CompanyLdap> companies, final CompanyLdap company) {
		// Collect all parents and sorted from parent to the leaf
		company.setCompanyTree(companies.values().stream().filter(c -> LdapUtils.equalsOrParentOf(c.getDn(), company.getDn()))
				.sorted(Comparator.comparing(CompanyLdap::getLdapName)).collect(Collectors.toList()));
	}

	/**
	 * Return the quarantine/isolated company identifier.
	 * 
	 * @return The quarantine/isolated company identifier.
	 */
	public String getQuarantineCompany() {
		return LdapUtils.toRdn(quarantineBaseDn);
	}

	@Override
	public CompanyLdap create(final String dn, final String cn) {
		final CompanyLdap company = super.create(dn, cn);

		// Also, update the cache
		ldapCacheRepository.create(company);

		// Return the new group
		return company;
	}

	@Override
	protected CompanyLdap newContainer(final String dn, final String name) {
		return new CompanyLdap(LdapUtils.normalize(dn), name);
	}

	/**
	 * Map {@link CompanyLdap} to LDAP
	 */
	@Override
	protected void mapToContext(final CompanyLdap entry, final DirContextOperations context) {
		context.setAttributeValue("ou", entry.getName());
	}

	/**
	 * Remove the company from the memory cache.
	 * 
	 * @param company
	 *            The company to remove.
	 */
	private void removeFromJavaCache(final CompanyLdap company) {
		// Update the raw cache
		findAll().remove(company.getId());
	}

	@Override
	public void delete(final CompanyLdap container) {

		/*
		 * Remove from the managed companies, all companies within (sub LDAP DN) this company. This operation is needed
		 * since we
		 * are not rebuilding the cache from the LDAP. This save a lot of computations.
		 */
		findAll().values().stream().filter(g -> LdapUtils.equalsOrParentOf(container.getDn(), g.getDn())).collect(Collectors.toList())
				.forEach(this::removeFromJavaCache);

		// Remove from LDAP the recursively the node. Anything that was not nicely cleaned will be deleted there.
		template.unbind(container.getDn(), true);

		// Also, update the SQL cache
		ldapCacheRepository.delete(container);
	}

}

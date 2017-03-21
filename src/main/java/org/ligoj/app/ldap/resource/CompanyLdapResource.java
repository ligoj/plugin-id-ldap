package org.ligoj.app.ldap.resource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.ObjectUtils;
import org.ligoj.app.api.CompanyLdap;
import org.ligoj.app.api.ContainerLdap;
import org.ligoj.app.api.UserLdap;
import org.ligoj.app.dao.CacheCompanyRepository;
import org.ligoj.app.ldap.LdapUtils;
import org.ligoj.app.ldap.dao.CompanyLdapRepository;
import org.ligoj.app.model.CacheCompany;
import org.ligoj.app.model.ContainerType;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * LDAP resource for companies.
 */
@Path("/ldap/company")
@Service
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class CompanyLdapResource extends AbstractContainerLdapResource<CompanyLdap, ContainerLdapEditionVo, CacheCompany> {

	/**
	 * Attribute name used as filter and path.
	 */
	public static final String COMPANY_ATTRIBUTE = "company";

	@Autowired
	private CacheCompanyRepository cacheCompanyRepository;

	/**
	 * Default constructor specifying the type as {@link ContainerType#COMPANY}
	 */
	protected CompanyLdapResource() {
		super(ContainerType.COMPANY);
	}

	@Override
	public CacheCompanyRepository getCacheRepository() {
		return cacheCompanyRepository;
	}

	@Override
	public CompanyLdapRepository getRepository() {
		return getCompany();
	}

	/**
	 * Return the company name of current user.
	 * 
	 * @return The company name of current user or <code>null</code> if the current user is not in LDAP.
	 */
	public CompanyLdap getUserCompany() {
		final UserLdap user = getUser().findById(securityHelper.getLogin());
		if (user == null) {
			return null;
		}
		return getRepository().findById(ObjectUtils.defaultIfNull(user.getCompany(), ""));
	}

	/**
	 * Return the company DN of current user.
	 * 
	 * @return the company DN of current user or <code>null</code> if the current user is not in LDAP.
	 */
	private String getUserCompanyDn() {
		final CompanyLdap company = getUserCompany();
		if (company == null) {
			return null;
		}
		return company.getDn();
	}

	/**
	 * Indicates the current user is inside the internal scope of people.
	 * 
	 * @return <code>true</code> when the current user is inside the internal scope of people.
	 */
	public boolean isUserInternalCommpany() {
		return ObjectUtils.defaultIfNull(getUserCompanyDn(), "").endsWith(ObjectUtils.defaultIfNull(getUser().getPeopleInternalBaseDn(), ""));
	}

	/**
	 * Return groups matching to given criteria. The managed groups, trees and companies are checked. The returned
	 * groups of each user depends on the groups the user can see/write in CN form.
	 * 
	 * @param uriInfo
	 *            filter data.
	 * @return found groups.
	 */
	@GET
	public TableItem<ContainerLdapCountVo> findAll(@Context final UriInfo uriInfo) {
		final PageRequest pageRequest = paginationJson.getPageRequest(uriInfo, ORDERED_COLUMNS);

		final List<ContainerScope> types = containerScopeResource.findAllDescOrder(ContainerType.COMPANY);
		final Set<CompanyLdap> managedCompanies = getContainers();
		final Set<String> managedCompaniesAsString = managedCompanies.stream().map(CompanyLdap::getId).collect(Collectors.toSet());
		final Set<CompanyLdap> managedCompaniesWrite = getContainersForWrite();
		final Set<CompanyLdap> managedCompaniesAdmin = getContainersForAdmin();
		final Map<String, UserLdap> ldapUsers = getUser().findAll();

		// Search the companies
		final Page<CompanyLdap> findAll = getRepository().findAll(managedCompanies, DataTableAttributes.getSearch(uriInfo), pageRequest,
				Collections.singletonMap(TYPE_ATTRIBUTE, new TypeComparator(types)));

		// Apply pagination and secure the users data
		return paginationJson.applyPagination(uriInfo, findAll, rawCompanyLdap -> {
			// Build the secured company with counter
			final ContainerLdapCountVo securedUserLdap = newContainerLdapCountVo(rawCompanyLdap, managedCompaniesWrite, managedCompaniesAdmin, types);

			// Computed the total members, unrestricted visibility
			securedUserLdap.setCount((int) ldapUsers.values().stream().filter(user -> rawCompanyLdap.getId().equals(user.getCompany())).count());

			// Computed the visible members : same company and visible company
			securedUserLdap.setCountVisible((int) ldapUsers.values().stream().filter(user -> rawCompanyLdap.getId().equals(user.getCompany()))
					.filter(user -> managedCompaniesAsString.contains(user.getCompany())).count());
			return securedUserLdap;
		});
	}

	@Override
	protected void checkForDeletion(final ContainerLdap container) {
		super.checkForDeletion(container);

		// Company deletion is only possible where there is no user inside this company, or inside any sub-company
		final Map<String, UserLdap> ldapUsers = getUser().findAll();
		if (getRepository().findAll().values().stream().filter(g -> LdapUtils.equalsOrParentOf(container.getDn(), g.getDn()))
				.anyMatch(c -> ldapUsers.values().stream().map(UserLdap::getCompany).anyMatch(c.getId()::equals))) {
			// Locked container is inside the container to delete
			throw new ValidationJsonException(getTypeName(), "not-empty-company", "0", getTypeName(), "1",
					container.getId());
		}
	}

	@Override
	protected String toDn(final ContainerLdapEditionVo container, final ContainerScope type) {
		return "ou=" + container.getName() + "," + type.getDn();
	}

}
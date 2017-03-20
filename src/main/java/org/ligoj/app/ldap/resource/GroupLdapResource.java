package org.ligoj.app.ldap.resource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.app.api.CompanyLdap;
import org.ligoj.app.api.GroupLdap;
import org.ligoj.app.api.UserLdap;
import org.ligoj.app.dao.CacheGroupRepository;
import org.ligoj.app.ldap.LdapUtils;
import org.ligoj.app.ldap.dao.GroupLdapRepository;
import org.ligoj.app.ldap.model.ContainerType;
import org.ligoj.app.ldap.model.ContainerTypeLdap;
import org.ligoj.app.model.CacheGroup;

/**
 * LDAP Group resource.
 */
@Path("/ldap/group")
@Service
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class GroupLdapResource extends AbstractContainerLdapResource<GroupLdap, GroupLdapEditionVo, CacheGroup> {

	/**
	 * Attribute name used as filter and path.
	 */
	public static final String GROUP_ATTRIBUTE = "group";

	@Autowired
	private CompanyLdapResource organizationResource;

	@Autowired
	private CacheGroupRepository cacheGroupRepository;

	/**
	 * Default constructor specifying the type as {@link ContainerType#GROUP}
	 */
	public GroupLdapResource() {
		super(ContainerType.GROUP);
	}

	@Override
	public CacheGroupRepository getCacheRepository() {
		return cacheGroupRepository;
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
		final List<ContainerTypeLdap> types = containerTypeLdapResource.findAllDescOrder(ContainerType.GROUP);
		final Map<String, CompanyLdap> companies = organizationResource.getRepository().findAll();
		final Collection<CompanyLdap> managedCompanies = organizationResource.getContainers();
		final Set<GroupLdap> managedGroupsWrite = getContainersForWrite();
		final Set<GroupLdap> managedGroupsAdmin = getContainersForAdmin();
		final Map<String, UserLdap> ldapUsers = getUser().findAll();

		// Search the groups
		final Page<GroupLdap> findAll = getContainers(DataTableAttributes.getSearch(uriInfo),
				paginationJson.getPageRequest(uriInfo, ORDERED_COLUMNS));

		// Apply pagination and secure the users data
		return paginationJson.applyPagination(uriInfo, findAll, rawGroupLdap -> {
			final ContainerLdapCountVo securedUserLdap = newContainerLdapCountVo(rawGroupLdap, managedGroupsWrite, managedGroupsAdmin, types);
			securedUserLdap.setCount(rawGroupLdap.getMembers().size());
			// [jdoe4, jdoe5, fdoe2, jlast3] // companies.get(ldapUsers.get("jdoe4").getCompany()).getCompanyTree()
			// Computed the visible members
			securedUserLdap.setCountVisible((int) rawGroupLdap.getMembers().stream().map(ldapUsers::get).map(UserLdap::getCompany).map(companies::get)
					.map(CompanyLdap::getCompanyTree).filter(c -> CollectionUtils.containsAny(managedCompanies, c)).count());
			return securedUserLdap;
		});
	}

	/**
	 * Indicates a group exists or not.
	 * 
	 * @param group
	 *            the group name. Exact match is required, so a normalized version.
	 * @return <code>true</code> if the group exists.
	 */
	@GET
	@Path("{group}/exists")
	public boolean exists(@PathParam(GROUP_ATTRIBUTE) final String group) {
		return findById(group) != null;
	}

	@Override
	protected String toDn(final GroupLdapEditionVo container, final ContainerTypeLdap type) {
		String parentDn = type.getDn();
		container.setParent(StringUtils.trimToNull(container.getParent()));
		if (container.getParent() != null) {
			// Check the parent is also inside the type, a new DN will be built
			final GroupLdap parent = findByIdExpected(container.getParent());
			if (!LdapUtils.equalsOrParentOf(type.getDn(), parent.getDn())) {
				throw new ValidationJsonException("parent", "container-parent-type-match", TYPE_ATTRIBUTE, this.type, "provided", type.getType());
			}
			parentDn = parent.getDn();
		}

		return "cn=" + container.getName() + "," + parentDn;
	}

	/**
	 * Empty this group by removing all members if supported by the LDAP schema<br>
	 * 
	 * @param id
	 *            The group to empty.
	 */
	@POST
	@Path("empty/{id}")
	public void empty(@PathParam("id") final String id) {
		// Check the group exists
		final GroupLdap container = findByIdExpected(id);

		// Check the group can be updated by the current user
		if (!getContainersForWrite().contains(container)) {
			throw new ValidationJsonException(typeName, BusinessException.KEY_UNKNOW_ID, "0", typeName, "1", id);
		}

		// Perform the update
		getRepository().empty(container, getUser().findAll());
	}

	@Override
	public GroupLdapRepository getRepository() {
		return getGroup();
	}

	@Override
	protected GroupLdap create(final GroupLdapEditionVo container, final ContainerTypeLdap type, final String newDn) {
		// Check the related objects
		final List<String> assistants = toDn(container.getAssistants());
		final List<String> owners = toDn(container.getOwners());

		// Create the group
		final GroupLdap groupLdap = super.create(container, type, newDn);

		// Nesting management
		if (container.getParent() != null) {
			// This group will be added as "uniqueMember" of its parent
			getRepository().addGroup(groupLdap, LdapUtils.normalize(container.getParent()));
		}

		// Assistant/Owner/Department management
		getRepository().addAttributes(newDn, "seeAlso", assistants);
		getRepository().addAttributes(newDn, "owner", owners);
		getRepository().addAttributes(newDn, "businessCategory", CollectionUtils.emptyIfNull(container.getDepartments()));

		return groupLdap;
	}

	/**
	 * Convert the given user UIDs to a the corresponding DN. The users must exists.
	 * 
	 * @param uids
	 *            The UIDs to convert.
	 * @return The corresponding DN.
	 */
	private List<String> toDn(final List<String> uids) {
		return CollectionUtils.emptyIfNull(uids).stream().map(getUser()::findByIdExpected).map(UserLdap::getDn).collect(Collectors.toList());
	}
}

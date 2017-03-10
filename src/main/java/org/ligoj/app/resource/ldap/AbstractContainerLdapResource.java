package org.ligoj.app.resource.ldap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.bootstrap.core.json.PaginationJson;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.resource.OnNullReturn404;
import org.ligoj.bootstrap.core.security.SecurityHelper;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.app.dao.CacheRepository;
import org.ligoj.app.dao.ldap.DelegateLdapRepository;
import org.ligoj.app.iam.ContainerLdapRepository;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.ldap.LdapUtils;
import org.ligoj.app.iam.ldap.dao.CompanyLdapRepository;
import org.ligoj.app.iam.ldap.dao.GroupLdapRepository;
import org.ligoj.app.iam.ldap.dao.UserLdapRepository;
import org.ligoj.app.model.ldap.CacheContainer;
import org.ligoj.app.model.ldap.ContainerLdap;
import org.ligoj.app.model.ldap.ContainerType;
import org.ligoj.app.model.ldap.ContainerTypeLdap;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Basic container operations.
 * @param <T> The container type.
 * @param <V> The container edition bean type.
 * @param <C> The container cache type.
 * 
 */
@Slf4j
public abstract class AbstractContainerLdapResource<T extends ContainerLdap, V extends ContainerLdapEditionVo, C extends CacheContainer> {

	@Autowired
	protected ContainerTypeLdapResource containerTypeLdapResource;

	/**
	 * The container type manager by this instance.
	 */
	protected final ContainerType type;

	@Autowired
	protected DelegateLdapRepository delegateRepository;

	/**
	 * IAM provider.
	 */
	@Autowired
	protected IamProvider iamProvider;

	@Autowired
	protected PaginationJson paginationJson;

	@Autowired
	protected SecurityHelper securityHelper;

	/**
	 * Human readable type name.
	 */
	protected final String typeName;

	protected static final String TYPE_ATTRIBUTE = "type";

	/**
	 * Ordered columns.
	 */
	protected static final Map<String, String> ORDERED_COLUMNS = new HashMap<>();

	static {
		ORDERED_COLUMNS.put("name", "name");
	}

	protected AbstractContainerLdapResource(final ContainerType type) {
		this.type = type;
		this.typeName = this.type.name().toLowerCase(Locale.ENGLISH);
	}

	/**
	 * Return the repository managing the container as cache.
	 * 
	 * @return the repository managing the container as cache.
	 */
	protected abstract CacheRepository<C> getCacheRepository();

	/**
	 * Return the repository managing the container.
	 * 
	 * @return the repository managing the container.
	 */
	protected abstract ContainerLdapRepository<T> getRepository();

	/**
	 * Return the DN from the container and the computed type.
	 */
	protected abstract String toDn(V container, ContainerTypeLdap type);

	/**
	 * Simple transformer, securing sensible date. DN is not forwarded.
	 */
	protected ContainerLdapWithTypeVo toVo(final T rawGroupLdap) {
		// Find the closest type
		final ContainerLdapWithTypeVo securedUserLdap = new ContainerLdapWithTypeVo();
		final List<ContainerTypeLdap> types = containerTypeLdapResource.findAllDescOrder(type);
		final ContainerTypeLdap type = toType(types, rawGroupLdap);
		NamedBean.copy(rawGroupLdap, securedUserLdap);
		if (type != null) {
			securedUserLdap.setType(type.getName());
		}
		return securedUserLdap;
	}

	/**
	 * Return the container matching to given name. Case is sensitive. Visibility is checked against security context.
	 * DN is not exposed.
	 * 
	 * @param name
	 *            the container name. Exact match is required, so a normalized version.
	 * @return Container (CN) with its type.
	 */
	@GET
	@Path("{container:" + ContainerLdap.NAME_PATTERN + "}")
	@OnNullReturn404
	public ContainerLdapWithTypeVo findByName(@PathParam("container") final String name) {
		return Optional.ofNullable(findById(name)).map(this::toVo).orElse(null);
	}

	/**
	 * Create the given container.<br>
	 * The delegation system is involved for this operation and requires administration privilege on the parent tree or
	 * group/company.
	 * 
	 * @param container
	 *            The container to create.
	 * @return The identifier of created {@link org.ligoj.app.model.ldap.ContainerLdap}.
	 */
	@POST
	public String create(final V container) {
		return createInternal(container).getId();
	}

	/**
	 * Create the given container.<br>
	 * The delegation system is involved for this operation and requires administration privilege on the parent tree or
	 * group/company.<br>
	 * Note this is for internal use since the returned object corresponds to the internal representation.
	 * 
	 * @param container
	 *            The container to create.
	 * @return The created {@link org.ligoj.app.model.ldap.ContainerLdap} internal identifier.
	 */
	public T createInternal(final V container) {

		// Check the unlocked type exists
		final ContainerTypeLdap type = containerTypeLdapResource.findById(container.getType());
		if (type.isLocked()) {
			throw new ValidationJsonException(TYPE_ATTRIBUTE, "locked");
		}

		// Check the type matches with this class' container type
		if (this.type != type.getType()) {
			throw new ValidationJsonException(TYPE_ATTRIBUTE, "container-type-match", TYPE_ATTRIBUTE, this.type, "provided", type.getType());
		}

		// Build the new DN, keeping the case
		final String newDn = toDn(container, type);

		// Check the container can be created by the current user. Used DN will be FQN to match the delegates
		if (!delegateRepository.isAdmin(securityHelper.getLogin(), LdapUtils.normalize(newDn), this.type.getDelegateType())) {
			// Not managed container, report this attempt and act as if this container already exists
			log.warn("Attempt to create a {} '{}' out of scope", type, container.getName());
			throw new ValidationJsonException("name", "already-exist", "0", typeName, "1", container.getName());
		}

		// Check the group does not exists
		if (getRepository().findById(LdapUtils.normalize(container.getName())) != null) {
			throw new ValidationJsonException("name", "already-exist", "0", typeName, "1", container.getName());
		}

		// Create the new group
		return create(container, type, newDn);
	}

	protected T create(final V container, final ContainerTypeLdap type, final String newDn) {
		log.info("Creating a {}@{}-{} '{}'", this.type, type.getName(), type.getId(), container.getName());
		return getRepository().create(newDn, container.getName());
	}

	/**
	 * Delete an existing container.<br>
	 * The delegation system is involved for this operation and requires administration privilege on this container.
	 * 
	 * @param id
	 *            The container's identifier.
	 */
	@DELETE
	@Path("{id}")
	public void delete(@PathParam("id") final String id) {
		// Check the container exists
		final T container = findByIdExpected(id);

		// Check the container can be deleted by current user
		checkForDeletion(container);

		// Perform the deletion when checked
		getRepository().delete(container);
	}

	/**
	 * Return containers the current user can manage with write access.
	 * 
	 * @param uriInfo
	 *            filter data.
	 * @return containers the current user can manage.
	 */
	@GET
	@Path("filter/write")
	public TableItem<String> getContainersForWrite(@Context final UriInfo uriInfo) {
		return paginationJson.applyPagination(uriInfo, getCacheRepository().findAllWrite(securityHelper.getLogin(),
				DataTableAttributes.getSearch(uriInfo), paginationJson.getPageRequest(uriInfo, ORDERED_COLUMNS)), CacheContainer::getName);
	}

	/**
	 * Return containers the current user can manage with administration access.
	 * 
	 * @param uriInfo
	 *            filter data.
	 * @return containers the current user can manage.
	 */
	@GET
	@Path("filter/admin")
	public TableItem<String> getContainersForAdmin(@Context final UriInfo uriInfo) {
		return paginationJson.applyPagination(uriInfo, getCacheRepository().findAllAdmin(securityHelper.getLogin(),
				DataTableAttributes.getSearch(uriInfo), paginationJson.getPageRequest(uriInfo, ORDERED_COLUMNS)), CacheContainer::getName);
	}

	/**
	 * Return containers the current user can see. A user always sees his company, as if he had a company
	 * delegation to see it.
	 * 
	 * @param uriInfo
	 *            filter data.
	 * @return containers the current user can see.
	 */
	@GET
	@Path("filter/read")
	public TableItem<String> getContainers(@Context final UriInfo uriInfo) {
		return paginationJson.applyPagination(uriInfo, getCacheRepository().findAll(securityHelper.getLogin(), DataTableAttributes.getSearch(uriInfo),
				paginationJson.getPageRequest(uriInfo, ORDERED_COLUMNS)), CacheContainer::getName);
	}

	/**
	 * Find a container from its identifier. If the container is not found or cannot be seen by the current user, the
	 * error code {@link org.ligoj.bootstrap.core.resource.BusinessException#KEY_UNKNOW_ID} will be returned.
	 * 
	 * @param id
	 *            The container's identifier. Will be normalized.
	 * @return The container from its identifier.
	 */
	public T findByIdExpected(final String id) {
		return Optional.ofNullable(findById(id))
				.orElseThrow(() -> new ValidationJsonException(typeName, BusinessException.KEY_UNKNOW_ID, "0", typeName, "1", id));
	}

	/**
	 * Find a container from its identifier.
	 * 
	 * @param id
	 *            The container's identifier. Will be normalized.
	 * @return The container from its identifier. <code>null</code> if the container is not found or cannot be seen by
	 *         the current user
	 */
	public T findById(final String id) {
		// Check the container exists and return the in memory object.
		return Optional.ofNullable(getCacheRepository().findById(securityHelper.getLogin(), LdapUtils.normalize(id))).map(CacheContainer::getId)
				.map(getRepository()::findById).orElse(null);
	}

	/**
	 * Check the container can be deleted by the current user.
	 * 
	 * @param container
	 *            The container to delete.
	 */
	protected void checkForDeletion(final ContainerLdap container) {

		// Check the container can be deleted by the current user. Used DN will be FQN to match the delegates
		if (!delegateRepository.isAdmin(securityHelper.getLogin(), LdapUtils.normalize(container.getDn()), this.type.getDelegateType())) {
			// Not managed container, report this attempt and act as if this company did not exist
			log.warn("Attempt to delete a {} '{}' out of scope", type, container.getName());
			throw new ValidationJsonException(typeName, BusinessException.KEY_UNKNOW_ID, "0", typeName, "1", container.getId());
		}

		// Check this container is not locked
		if (container.isLocked()) {
			throw new ValidationJsonException("company", "locked", "0", container.getName());
		}

		// Check the associated type is not locked
		final List<ContainerTypeLdap> types = containerTypeLdapResource.findAllDescOrder(ContainerType.GROUP);
		final ContainerTypeLdap type = toType(types, container);
		if (type != null && type.isLocked()) {
			throw new ValidationJsonException(typeName, "locked", "0", TYPE_ATTRIBUTE, "1", type.getName());
		}
	}

	/**
	 * Return the closest {@link ContainerTypeLdap} name associated to the given group. Order of types is important
	 * since the first matching item from this list is returned.
	 * 
	 * @param types
	 *            LDAP types.
	 * @param group
	 *            the group to check.
	 * @return The closest {@link ContainerTypeLdap} or <code>null</code> if not found.
	 */
	public ContainerTypeLdap toType(final List<ContainerTypeLdap> types, final ContainerLdap group) {
		return toType(types, group.getDn());
	}

	/**
	 * Return the closest {@link ContainerTypeLdap} name associated to the given group. Order of types is important
	 * since
	 * the first matching item from this list is returned.
	 * 
	 * @param types
	 *            LDAP types.
	 * @param dn
	 *            the DN of the group to check.
	 * @return The closest {@link ContainerTypeLdap} or <code>null</code> if not found.
	 */
	public ContainerTypeLdap toType(final List<ContainerTypeLdap> types, final String dn) {
		for (final ContainerTypeLdap type : types) {
			if (LdapUtils.normalize(dn).endsWith("," + LdapUtils.normalize(type.getDn()))) {
				// This type matches
				return type;
			}
		}

		// No category found
		return null;
	}

	/**
	 * Order {@link ContainerTypeLdap} by group type.
	 */
	@AllArgsConstructor
	public class TypeComparator implements Comparator<T> {

		/**
		 * group types
		 */
		private List<ContainerTypeLdap> types;

		@Override
		public int compare(final T group1, final T group2) {
			final int result;

			// First compare the type
			final ContainerTypeLdap type1 = toType(types, group1);
			final ContainerTypeLdap type2 = toType(types, group2);
			if (Objects.equals(type1, type2)) {
				result = 0;
			} else if (type1 == null) {
				result = 1;
			} else if (type2 == null) {
				result = -1;
			} else {
				result = type1.getName().compareToIgnoreCase(type2.getName());
			}

			// Then the compare the group name
			if (result == 0) {
				return group1.getName().compareToIgnoreCase(group2.getName());
			}
			return result;
		}
	}

	/**
	 * Build a new secured container managing the effective visibility and rights.
	 * 
	 * @param rawContainer
	 *            the raw container contained sensitive data.
	 * @param managedWrite
	 *            The containers the current user can write.
	 * @param managedAdmin
	 *            The containers the current user can administer.
	 * @param types
	 *            The defined type with locking information.
	 * @return A secured container with right and lock information the current user has.
	 */
	protected ContainerLdapCountVo newContainerLdapCountVo(final ContainerLdap rawContainer, final Set<T> managedWrite, final Set<T> managedAdmin,
			final List<ContainerTypeLdap> types) {
		final ContainerLdapCountVo securedUserLdap = new ContainerLdapCountVo();
		NamedBean.copy(rawContainer, securedUserLdap);
		securedUserLdap.setCanWrite(managedWrite.contains(rawContainer));
		securedUserLdap.setCanAdmin(managedAdmin.contains(rawContainer));
		securedUserLdap.setContainerType(type);

		// Find the closest type
		final ContainerTypeLdap type = toType(types, rawContainer);
		if (type != null) {
			securedUserLdap.setType(type.getName());
			securedUserLdap.setLocked(type.isLocked());
		}
		securedUserLdap.setLocked(securedUserLdap.isLocked() || rawContainer.isLocked());
		return securedUserLdap;
	}

	/**
	 * User repository provider.
	 * 
	 * @return User repository provider.
	 */
	protected UserLdapRepository getUser() {
		return (UserLdapRepository) iamProvider.getConfiguration().getUserLdapRepository();
	}

	/**
	 * Company repository provider.
	 * 
	 * @return Company repository provider.
	 */
	protected CompanyLdapRepository getCompany() {
		return (CompanyLdapRepository) iamProvider.getConfiguration().getCompanyLdapRepository();
	}

	/**
	 * Group repository provider.
	 * 
	 * @return Group repository provider.
	 */
	protected GroupLdapRepository getGroup() {
		return (GroupLdapRepository) iamProvider.getConfiguration().getGroupLdapRepository();
	}

	/**
	 * Return containers the given user can manage with write access.
	 * 
	 * @return ordered containers the given user can manage with write access.
	 */
	public Set<T> getContainersForWrite() {
		return toInternal(getCacheRepository().findAllWrite(securityHelper.getLogin()));
	}

	/**
	 * Return containers the given user can manage with administration access.
	 * 
	 * @return ordered companies the given user can manage with administration access.
	 */
	protected Set<T> getContainersForAdmin() {
		return toInternal(getCacheRepository().findAllAdmin(securityHelper.getLogin()));
	}

	/**
	 * Return containers the current user can see.
	 * 
	 * @return ordered containers the current user can see.
	 */
	public Set<T> getContainers() {
		return toInternal(getCacheRepository().findAll(securityHelper.getLogin()));
	}

	/**
	 * Return containers the current user can see.
	 * 
	 * @param criteria
	 *            Optional criteria, can be <code>null</code>.
	 * @param pageRequest
	 *            Optional {@link Pageable}, can be <code>null</code>.
	 * @return ordered containers the current user can see.
	 */
	public Page<T> getContainers(final String criteria, final Pageable pageRequest) {
		return toInternal(getCacheRepository().findAll(securityHelper.getLogin(), criteria, pageRequest));
	}

	/**
	 * Return the internal representation of the container set.
	 * 
	 * @param cacheItems
	 *            The database base cache containers to convert.
	 * @return The internal representation of container set. Ordered is kept.
	 */
	protected Set<T> toInternal(final Collection<C> cacheItems) {
		final Map<String, T> groups = getRepository().findAll();
		return cacheItems.stream().map(CacheContainer::getId).map(groups::get).filter(Objects::nonNull)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/**
	 * Return the internal representation of the container set as a {@link Page}.
	 * 
	 * @param cacheItems
	 *            The database base page cache containers to convert.
	 * @return The internal representation of {@link org.ligoj.app.model.ldap.CacheCompany} set. Ordered by the name.
	 */
	protected Page<T> toInternal(final Page<C> cacheItems) {
		return new PageImpl<>(new ArrayList<>(toInternal(cacheItems.getContent())), null, cacheItems.getTotalElements());
	}
}

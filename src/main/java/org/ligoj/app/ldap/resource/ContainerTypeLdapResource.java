package org.ligoj.app.ldap.resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheRemoveAll;
import javax.cache.annotation.CacheResult;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import org.ligoj.bootstrap.core.json.PaginationJson;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.app.ldap.dao.ContainerTypeLdapRepository;
import org.ligoj.app.ldap.model.ContainerType;
import org.ligoj.app.ldap.model.ContainerTypeLdap;

/**
 * LDAP Group type resource.
 */
@Path("/ldap/container-type")
@Service
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ContainerTypeLdapResource {

	@Autowired
	private ContainerTypeLdapRepository repository;

	@Autowired
	private PaginationJson paginationJson;

	/**
	 * Ordered columns.
	 */
	private static final Map<String, String> ORDERED_COLUMNS = new HashMap<>();
	static {
		ORDERED_COLUMNS.put("name", "name");
		ORDERED_COLUMNS.put("dn", "dn");
		ORDERED_COLUMNS.put("locked", "locked");
	}

	/**
	 * Return all {@link ContainerTypeLdap} in descendant order by DN in order to match the finest associations first.
	 * 
	 * @param type
	 *            The {@link ContainerType} to filter. Required.
	 * @return all {@link ContainerTypeLdap}.
	 */
	@CacheResult(cacheName = "container-types")
	public List<ContainerTypeLdap> findAllDescOrder(@CacheKey final ContainerType type) {
		return repository.findAllOrderByDnDesc(type);
	}

	/**
	 * Return all types matching to given criteria.
	 * 
	 * @param type
	 *            filtered {@link ContainerType}.
	 * @param uriInfo
	 *            filter data.
	 * @param criteria
	 *            the optional criteria to match.
	 * @return found group types.
	 */
	@GET
	@Path("{type}")
	public TableItem<ContainerTypeLdap> findAll(@PathParam("type") final ContainerType type, @Context final UriInfo uriInfo,
			@QueryParam(DataTableAttributes.SEARCH) final String criteria) {

		final Page<ContainerTypeLdap> findAll;
		if (StringUtils.isBlank(criteria)) {
			findAll = repository.findAllByType(type, paginationJson.getPageRequest(uriInfo, ORDERED_COLUMNS));
		} else {
			// Global search support
			findAll = repository.findAll(type, criteria, paginationJson.getPageRequest(uriInfo, ORDERED_COLUMNS));
		}

		// apply pagination and prevent lazy initialization issue
		return paginationJson.applyPagination(uriInfo, findAll, Function.identity());
	}

	/**
	 * Update a {@link ContainerTypeLdap}.
	 * 
	 * @param bean
	 *            new {@link ContainerTypeLdap} to update.
	 */
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@CacheRemoveAll(cacheName = "container-types")
	public void update(final ContainerTypeLdap bean) {
		repository.saveAndFlush(check(bean));
	}

	/**
	 * Create a new {@link ContainerTypeLdap}.
	 * 
	 * @param bean
	 *            new {@link ContainerTypeLdap} to persist.
	 * @return new identifier.
	 */
	@POST
	@CacheRemoveAll(cacheName = "container-types")
	@Consumes(MediaType.APPLICATION_JSON)
	public int create(final ContainerTypeLdap bean) {
		return repository.saveAndFlush(check(bean)).getId();
	}

	/**
	 * Validate and clean the type.
	 */
	protected ContainerTypeLdap check(final ContainerTypeLdap entity) {
		entity.setDn(StringUtils.trimToNull(entity.getDn()));
		entity.setName(StringUtils.trimToNull(entity.getName()));
		return entity;
	}

	/**
	 * Retrieve a type by its identifier.
	 * 
	 * @param id
	 *            type identifier.
	 * @return corresponding {@link ContainerTypeLdap}.
	 */
	@GET
	@Path("{id:\\d+}")
	public ContainerTypeLdap findById(@PathParam("id") final int id) {
		return repository.findOneExpected(id);
	}

	/**
	 * Retrieve a type by its name.
	 * 
	 * @param name
	 *            type name.
	 * @return corresponding {@link ContainerTypeLdap}.
	 */
	public ContainerTypeLdap findByName(final String name) {
		return repository.findByNameExpected(name);
	}

	/**
	 * Delete {@link ContainerTypeLdap} from its identifier.
	 * 
	 * @param id
	 *            Identifier of {@link ContainerTypeLdap} to delete.
	 */
	@DELETE
	@Path("{id}")
	@CacheRemoveAll(cacheName = "container-types")
	public void delete(@PathParam("id") final int id) {
		repository.delete(id);
	}

}

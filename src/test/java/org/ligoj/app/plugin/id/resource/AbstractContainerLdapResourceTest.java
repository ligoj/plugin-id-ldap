/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.BeforeEach;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.plugin.id.dao.ContainerScopeRepository;
import org.ligoj.app.plugin.id.ldap.resource.AbstractLdapTest;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Common test class for LDAP
 */
public abstract class AbstractContainerLdapResourceTest extends AbstractLdapTest {

	@Autowired
	protected ContainerScopeRepository containerScopeRepository;

	protected UriInfo newUriInfoAsc(final String orderedProperty) {
		return newUriInfo(orderedProperty, "asc");
	}

	protected UriInfo newUriInfoAscSearch(final String orderedProperty, final String search) {
		final UriInfo uriInfo = newUriInfo(orderedProperty, "asc");
		uriInfo.getQueryParameters().add(DataTableAttributes.SEARCH, search);
		return uriInfo;
	}

	protected UriInfo newUriInfoSearch(final String search) {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.SEARCH, search);
		return uriInfo;
	}

	protected UriInfo newUriInfoDesc(final String property) {
		return newUriInfo(property, "desc");
	}

	protected UriInfo newUriInfo(final String orderedProperty, final String order) {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "100");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add("columns[2][data]", orderedProperty);
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, order);
		return uriInfo;
	}

	@BeforeEach
	public void prepareData() throws IOException {
		persistEntities("csv", new Class[] { DelegateOrg.class, ContainerScope.class }, StandardCharsets.UTF_8.name());
		cacheManager.getCache("container-scopes").clear();
		cacheManager.getCache("ldap").clear();
		cacheManager.getCache("ldap-user-repository").clear();

		// For the cache to be created
		getUser().findAll();
	}
}

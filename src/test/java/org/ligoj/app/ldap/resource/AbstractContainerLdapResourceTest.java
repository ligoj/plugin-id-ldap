package org.ligoj.app.ldap.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.core.UriInfo;

import org.junit.Before;
import org.ligoj.app.model.DelegateOrg;
import org.ligoj.app.plugin.id.dao.ContainerScopeRepository;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.springframework.beans.factory.annotation.Autowired;

import net.sf.ehcache.CacheManager;

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

	@Before
	public void prepareData() throws IOException {
		persistEntities("csv/app-test", new Class[] { DelegateOrg.class, ContainerScope.class }, StandardCharsets.UTF_8.name());
		CacheManager.getInstance().getCache("container-scopes").removeAll();
		CacheManager.getInstance().getCache("ldap").removeAll();
		CacheManager.getInstance().getCache("ldap-user-repository").removeAll();

		// For the cache to be created
		getUser().findAll();
	}
}

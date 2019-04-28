/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.plugin.id.dao.ContainerScopeRepository;
import org.ligoj.app.plugin.id.ldap.resource.AbstractLdapTest;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Common test class for LDAP
 */
public abstract class AbstractContainerLdapResourceTest extends AbstractLdapTest {

	@Autowired
	protected ContainerScopeRepository containerScopeRepository;

	@BeforeEach
	public void prepareData() throws IOException {
		persistEntities("csv", new Class[] { DelegateOrg.class, ContainerScope.class }, StandardCharsets.UTF_8.name());
		cacheManager.getCache("container-scopes").clear();
		cacheManager.getCache("id-ldap-data").clear();
		cacheManager.getCache("id-configuration").clear();

		// For the cache to be created
		getUser().findAll();
	}
}

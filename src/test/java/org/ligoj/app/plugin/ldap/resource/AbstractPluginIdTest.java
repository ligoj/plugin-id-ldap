/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.ldap.resource;

import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.plugin.id.resource.UserOrgResource;

/**
 * Test placed in a package level visible for plugin-id.
 */
public abstract class AbstractPluginIdTest extends AbstractAppTest {

	protected String toApplicationUser(LdapPluginResource resource, final UserOrg user) {
		return resource.toApplicationUser(user);
	}
	
	protected String toLogin(LdapPluginResource resource, final UserOrg user) {
		return resource.toApplicationUser(user);
	}
	
	protected void setUserResource(LdapPluginResource resource, UserOrgResource userResource) {
		resource.setUserResource(userResource);
	}

}

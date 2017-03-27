package org.ligoj.app.plugin.id.ldap.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.ldap.dao.CompanyLdapRepository;
import org.ligoj.app.ldap.dao.GroupLdapRepository;
import org.ligoj.app.ldap.dao.UserLdapRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.springframework.ldap.core.LdapTemplate;

/**
 * Test for LDAP resources.
 */
public abstract class AbstractLdapTest extends AbstractAppTest {

	/**
	 * Prepare the Spring Security in the context, not the REST one.
	 */
	@Before
	public void setUp2() throws IOException {
		persistEntities("csv/app-test", new Class[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class },
				StandardCharsets.UTF_8.name());
	}

	/**
	 * User repository provider.
	 * 
	 * @return User repository provider.
	 */
	@Override
	protected UserLdapRepository getUser() {
		return (UserLdapRepository) super.getUser();
	}

	/**
	 * Company repository provider.
	 * 
	 * @return Company repository provider.
	 */
	@Override
	protected CompanyLdapRepository getCompany() {
		return (CompanyLdapRepository) super.getCompany();
	}

	/**
	 * Group repository provider.
	 * 
	 * @return Group repository provider.
	 */
	@Override
	protected GroupLdapRepository getGroup() {
		return (GroupLdapRepository) super.getGroup();
	}

	/**
	 * Group repository provider.
	 * 
	 * @return Group repository provider.
	 */
	protected LdapTemplate getTemplate() {
		return getUser().getTemplate();
	}
}

package org.ligoj.app.resource.ldap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.LdapTemplate;

import org.ligoj.bootstrap.AbstractJpaTest;
import org.ligoj.bootstrap.model.system.SystemAuthorization;
import org.ligoj.bootstrap.model.system.SystemAuthorization.AuthorizationType;
import org.ligoj.bootstrap.model.system.SystemRole;
import org.ligoj.bootstrap.model.system.SystemRoleAssignment;
import org.ligoj.bootstrap.model.system.SystemUser;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.ldap.dao.CompanyLdapRepository;
import org.ligoj.app.iam.ldap.dao.GroupLdapRepository;
import org.ligoj.app.iam.ldap.dao.UserLdapRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;

/**
 * Test for LDAP resources.
 */
public abstract class AbstractLdapTest extends AbstractJpaTest {

	@Autowired
	protected IamProvider iamProvider;

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
	 * Group repository provider.
	 * 
	 * @return Group repository provider.
	 */
	protected LdapTemplate getTemplate() {
		return getUser().getTemplate();
	}

	/**
	 * Persist system user, role and assignment for user DEFAULT_USER.
	 */
	protected void persistSystemEntities() {
		final SystemRole role = new SystemRole();
		role.setName("some");
		em.persist(role);
		final SystemUser user = new SystemUser();
		user.setLogin(DEFAULT_USER);
		em.persist(user);
		final SystemAuthorization authorization = new SystemAuthorization();
		authorization.setType(AuthorizationType.BUSINESS);
		authorization.setPattern(".*");
		authorization.setRole(role);
		em.persist(authorization);
		final SystemRoleAssignment assignment = new SystemRoleAssignment();
		assignment.setRole(role);
		assignment.setUser(user);
		em.persist(assignment);
	}
}

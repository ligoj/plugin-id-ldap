/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.resource;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.dao.ParameterRepository;
import org.ligoj.app.dao.ParameterValueRepository;
import org.ligoj.app.dao.ProjectRepository;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.model.*;
import org.ligoj.app.model.*;
import org.ligoj.app.plugin.id.ldap.dao.CacheLdapRepository;
import org.ligoj.app.plugin.id.ldap.dao.ProjectCustomerLdapRepository;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.app.plugin.id.resource.AbstractPluginIdTest;
import org.ligoj.app.plugin.id.resource.IdentityResource;
import org.ligoj.app.plugin.id.resource.UserOrgResource;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.node.ParameterValueResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Test class of {@link LdapPluginResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public abstract class AbstractLdapPluginResourceTest extends AbstractPluginIdTest {
	@Autowired
	protected LdapPluginResource resource;

	@Autowired
	protected ParameterValueResource pvResource;

	@Autowired
	protected ParameterRepository parameterRepository;

	@Autowired
	protected ParameterValueRepository parameterValueRepository;

	@Autowired
	protected NodeRepository nodeRepository;

	@Autowired
	protected UserOrgResource userResource;

	@Autowired
	protected ProjectCustomerLdapRepository projectCustomerLdapRepository;

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	protected ProjectRepository projectRepository;

	@Autowired
	protected ServicePluginLocator servicePluginLocator;

	@Autowired
	protected CacheLdapRepository cache;

	protected int subscription;

	@BeforeEach
	public void prepareData() throws IOException {
		persistEntities("csv",
				new Class<?>[]{DelegateOrg.class, ContainerScope.class, CacheCompany.class, CacheUser.class,
						CacheGroup.class, CacheMembership.class, Project.class, Node.class, Parameter.class,
						Subscription.class, ParameterValue.class, CacheProjectGroup.class},
				StandardCharsets.UTF_8);
		cacheManager.getCache("container-scopes").clear();

		// Only with Spring context
		this.subscription = getSubscription("Jupiter", IdentityResource.SERVICE_KEY);

		// Coverage only
		resource.getKey();
	}

	/**
	 * Create a group in an existing OU "sea". Most Simple case. Group matches exactly to the pkey of the project.
	 *
	 * @return the created subscription.
	 */
	protected Subscription create(final String groupAndProject) {
		// Preconditions
		Assertions.assertNull(getGroup().findById(groupAndProject));
		Assertions.assertTrue(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").contains("sea"));
		Assertions.assertNotNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "sea"));

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		final Project newProject = newProject(groupAndProject);
		subscription2.setProject(newProject);
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, groupAndProject);
		setOu(subscription2, "sea");

		basicCreate(subscription2);

		// Checks
		final GroupOrg groupLdap = getGroup().findById(groupAndProject);
		Assertions.assertNotNull(groupLdap);
		Assertions.assertEquals(groupAndProject, groupLdap.getName());
		Assertions.assertEquals(groupAndProject, groupLdap.getId());
		Assertions.assertEquals("cn=" + groupAndProject + ",ou=sea,ou=project,dc=sample,dc=com", groupLdap.getDn());
		Assertions.assertNotNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "sea"));
		Assertions.assertTrue(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").contains("sea"));

		return subscription2;
	}

	/**
	 * Reload the LDAP cache
	 */
	protected void reloadLdapCache() {
		// Ensure LDAP cache is loaded
		cacheManager.getCache("id-ldap-data").clear();
		cache.getData();
		em.flush();
		em.clear();
	}

	/**
	 * Create a new project
	 */
	protected Project newProject(final String pkey) {
		final Project project = new Project();
		project.setPkey(pkey);
		project.setName("ANY - " + pkey);
		project.setTeamLeader(DEFAULT_USER);
		em.persist(project);
		return project;
	}

	protected void setGroup(final Subscription subscription, final String group) {
		setData(subscription, IdentityResource.PARAMETER_GROUP, group);
	}

	protected ParameterValue setData(final Subscription subscription, final String parameter, String data) {
		final Parameter groupParameter = parameterRepository.findOneExpected(parameter);
		ParameterValue value = parameterValueRepository
				.findAllBy("subscription.id", subscription.isNew() ? 0 : subscription.getId()).stream()
				.filter(v -> v.getParameter().getId().equals(parameter)).findFirst().orElseGet(() -> {
					final ParameterValue pv = new ParameterValue();
					pv.setParameter(groupParameter);
					pv.setSubscription(subscription);
					pv.setData(data);
					return pv;
				});
		value.setData(data);
		if (value.isNew()) {
			em.persist(value);
		}
		em.flush();
		return value;
	}

	protected void setOu(final Subscription subscription, final String ou) {
		setData(subscription, IdentityResource.PARAMETER_OU, ou);
	}

	protected void setParentGroup(final Subscription subscription, final String parentGroup) {
		setData(subscription, IdentityResource.PARAMETER_PARENT_GROUP, parentGroup);
	}

	protected void basicCreate(final Subscription subscription2) {
		initSpringSecurityContext(DEFAULT_USER);
		resource.create(subscription2.getId());
		em.flush();
		em.clear();
	}

	protected void basicLink(final Subscription subscription2) {
		initSpringSecurityContext(DEFAULT_USER);
		resource.link(subscription2.getId());
		em.flush();
		em.clear();
	}

	/**
	 * Create a group inside another group/ Both are created inside "sea" OU.
	 *
	 * @return the created {@link Subscription}.
	 */
	protected Subscription createSubGroup(final Project newProject, final String parentGroup, final String subGroup) {

		// Preconditions
		Assertions.assertNotNull(getGroup().findById(parentGroup));
		Assertions.assertNull(getGroup().findById(subGroup));
		Assertions.assertNotNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "sea"));

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject);
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, subGroup);
		setParentGroup(subscription2, parentGroup);
		setOu(subscription2, "sea");

		basicCreate(subscription2);

		// Checks
		final GroupOrg groupLdap = getGroup().findById(subGroup);
		Assertions.assertNotNull(groupLdap);
		Assertions.assertEquals(subGroup, groupLdap.getName());
		Assertions.assertEquals("cn=" + subGroup + ",cn=" + parentGroup + ",ou=sea,ou=project,dc=sample,dc=com",
				groupLdap.getDn());
		Assertions.assertEquals(subGroup, groupLdap.getId());
		Assertions.assertEquals(parentGroup, groupLdap.getParent());
		final GroupOrg groupLdapParent = getGroup().findById(parentGroup);
		Assertions.assertEquals(1, groupLdapParent.getSubGroups().size());
		Assertions.assertTrue(groupLdapParent.getSubGroups().contains(subGroup));
		return subscription2;
	}

	protected void newLdap() {
		final Node ldap = new Node();
		ldap.setId("service:id:ldap:secondary");
		ldap.setRefined(nodeRepository.findOneExpected("service:id:ldap"));
		ldap.setName("LDAP Test");
		nodeRepository.saveAndFlush(ldap);
		persistParameter(ldap, LdapPluginResource.PARAMETER_URL, "ldap://localhost:34389/");
		persistParameter(ldap, LdapPluginResource.PARAMETER_USER, "uid=admin,ou=system");
		persistParameter(ldap, LdapPluginResource.PARAMETER_PASSWORD, "secret");
		persistParameter(ldap, LdapPluginResource.PARAMETER_BASE_BN, "");
		persistParameter(ldap, LdapPluginResource.PARAMETER_UID_ATTRIBUTE, "uid");
		persistParameter(ldap, LdapPluginResource.PARAMETER_LOGIN_ATTRIBUTES, "uid, mail");
		persistParameter(ldap, LdapPluginResource.PARAMETER_SELF_SEARCH, "true");
		persistParameter(ldap, LdapPluginResource.PARAMETER_PEOPLE_DN, "dc=sample,dc=com");
		persistParameter(ldap, LdapPluginResource.PARAMETER_DEPARTMENT_ATTRIBUTE, "departmentNumber");
		persistParameter(ldap, LdapPluginResource.PARAMETER_LOCAL_ID_ATTRIBUTE, "employeeNumber");
		persistParameter(ldap, LdapPluginResource.PARAMETER_PEOPLE_CLASS, "inetOrgPerson");
		persistParameter(ldap, LdapPluginResource.PARAMETER_PEOPLE_CLASS_CREATE, "inetOrgPerson");
		persistParameter(ldap, LdapPluginResource.PARAMETER_COMPANY_PATTERN, "ligoj");
		persistParameter(ldap, LdapPluginResource.PARAMETER_GROUPS_CLASS, "groupOfUniqueNames");
		persistParameter(ldap, LdapPluginResource.PARAMETER_GROUPS_CLASS_CREATE, "groupOfUniqueNames,groupOfUniqueNames");
		persistParameter(ldap, LdapPluginResource.PARAMETER_COMPANIES_CLASS, "organizationalUnit");
		persistParameter(ldap, LdapPluginResource.PARAMETER_COMPANIES_CLASS_CREATE, "organizationalUnit");
	}

	protected void persistParameter(final Node node, final String id, final String value) {
		final ParameterValue parameterValue = new ParameterValue();
		parameterValue.setNode(node);
		parameterValue.setParameter(parameterRepository.findOneExpected(id));
		parameterValue.setData(value);
		em.persist(parameterValue);
	}
}

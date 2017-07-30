package org.ligoj.app.plugin.id.ldap.resource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.dao.ParameterRepository;
import org.ligoj.app.dao.ProjectRepository;
import org.ligoj.app.iam.Activity;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.ICompanyRepository;
import org.ligoj.app.iam.IGroupRepository;
import org.ligoj.app.iam.IUserRepository;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.model.CacheCompany;
import org.ligoj.app.iam.model.CacheGroup;
import org.ligoj.app.iam.model.CacheMembership;
import org.ligoj.app.iam.model.CacheUser;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.model.CacheProjectGroup;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.id.ldap.dao.GroupLdapRepository;
import org.ligoj.app.plugin.id.ldap.dao.LdapCacheRepository;
import org.ligoj.app.plugin.id.ldap.dao.ProjectCustomerLdapRepository;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.app.plugin.id.resource.IdentityResource;
import org.ligoj.app.plugin.id.resource.UserOrgEditionVo;
import org.ligoj.app.plugin.id.resource.UserOrgResource;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.node.ParameterValueResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.UncategorizedLdapException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import net.sf.ehcache.CacheManager;

/**
 * Test class of {@link LdapPluginResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
@org.junit.FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LdapPluginResourceTest extends AbstractAppTest {
	@Autowired
	private LdapPluginResource resource;

	@Autowired
	private ParameterValueResource pvResource;

	@Autowired
	private ParameterRepository parameterRepository;

	@Autowired
	private NodeRepository nodeRepository;

	@Autowired
	private UserOrgResource userResource;

	@Autowired
	private ProjectCustomerLdapRepository projectCustomerLdapRepository;

	@Autowired
	private SubscriptionResource subscriptionResource;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ServicePluginLocator servicePluginLocator;

	@Autowired
	private LdapCacheRepository cache;

	protected IUserRepository userRepository;
	protected IGroupRepository groupRepository;
	protected ICompanyRepository companyRepository;

	private int subscription;

	@Before
	public void prepareData() throws IOException {
		persistEntities(
				"csv", new Class[] { DelegateOrg.class, ContainerScope.class, CacheCompany.class, CacheUser.class, CacheGroup.class,
						CacheMembership.class, Project.class, Node.class, Parameter.class, Subscription.class, ParameterValue.class, 
						CacheProjectGroup.class },
				StandardCharsets.UTF_8.name());
		CacheManager.getInstance().getCache("container-scopes").removeAll();

		// Only with Spring context
		this.subscription = getSubscription("gStack", IdentityResource.SERVICE_KEY);

		// Coverage only
		resource.getKey();
	}

	@Test
	public void deleteNoMoreGroup() throws Exception {
		final Subscription subscription = new Subscription();
		subscription.setProject(projectRepository.findByName("gStack"));
		subscription.setNode(nodeRepository.findOneExpected("service:id:ldap:dig"));
		em.persist(subscription);

		// Attach the wrong group
		setGroup(subscription, "any");

		initSpringSecurityContext("fdaugan");
		final Map<String, String> parameters = subscriptionResource.getParametersNoCheck(subscription.getId());
		Assert.assertFalse(resource.checkSubscriptionStatus(parameters).getStatus().isUp());

		resource.delete(subscription.getId(), true);
		em.flush();
		em.clear();
		Assert.assertFalse(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
		subscriptionResource.getParametersNoCheck(subscription.getId()).isEmpty();
	}

	/**
	 * The unsubscription without deletion has no effect
	 */
	@Test
	public void delete() throws Exception {
		initSpringSecurityContext("fdaugan");
		final Map<String, String> parameters = subscriptionResource.getParameters(subscription);
		Assert.assertTrue(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
		resource.delete(subscription, false);
		em.flush();
		em.clear();
		Assert.assertTrue(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
	}

	@Test
	public void zzdeleteWithSubGroup() throws Exception {
		// Create the data
		initSpringSecurityContext("fdaugan");

		// Create the parent group
		final Subscription parentSubscription = create("sea-parent-for-1deletion");
		createSubGroup(parentSubscription.getProject(), "sea-parent-for-1deletion", "sea-parent-for-1deletion-sub");

		// Check the subgroups are there
		Assert.assertEquals(2, resource.findGroupsByName("sea-parent-for-1deletion").size());
		final Map<String, String> parameters = subscriptionResource.getParameters(parentSubscription.getId());
		Assert.assertTrue(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
		Assert.assertEquals(1, getGroup().findAll().get("sea-parent-for-1deletion").getSubGroups().size());
		Assert.assertEquals("sea-parent-for-1deletion-sub", getGroup().findAll().get("sea-parent-for-1deletion").getSubGroups().iterator().next());

		// Delete the parent group
		resource.delete(parentSubscription.getId(), true);
		em.flush();
		em.clear();

		// Check the new status
		Assert.assertNull(getGroup().findAll().get("sea-parent-for-1deletion"));
		Assert.assertNull(getGroup().findAll().get("sea-parent-for-1deletion-sub"));
		Assert.assertFalse(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
		Assert.assertEquals(0, resource.findGroupsByName("sea-parent-for-1deletion").size());
	}

	/**
	 * Delete a group that is also member from another group.
	 */
	@Test
	public void zzdeleteFromParentGroup() throws Exception {
		// Create the data
		initSpringSecurityContext("fdaugan");

		// Create the parent group
		final Subscription parentSubscription = create("sea-parent-for-2deletion");
		final Subscription childSubscription = createSubGroup(parentSubscription.getProject(), "sea-parent-for-2deletion",
				"sea-parent-for-2deletion-sub");

		// Check the sub-group and the parent are there
		Assert.assertEquals(2, resource.findGroupsByName("sea-parent-for-2deletion").size());
		final Map<String, String> parentParameters = subscriptionResource.getParameters(parentSubscription.getId());
		Assert.assertTrue(resource.checkSubscriptionStatus(parentParameters).getStatus().isUp());
		final Map<String, String> childParameters = subscriptionResource.getParameters(childSubscription.getId());
		Assert.assertTrue(resource.checkSubscriptionStatus(childParameters).getStatus().isUp());
		Assert.assertEquals(1, getGroup().findAll().get("sea-parent-for-2deletion").getSubGroups().size());
		Assert.assertEquals("sea-parent-for-2deletion-sub", getGroup().findAll().get("sea-parent-for-2deletion").getSubGroups().iterator().next());

		// Delete the child group
		resource.delete(childSubscription.getId(), true);
		em.flush();
		em.clear();

		// Check the new status of the parent
		Assert.assertTrue(resource.checkSubscriptionStatus(parentParameters).getStatus().isUp());
		Assert.assertFalse(subscriptionResource.getParameters(parentSubscription.getId()).isEmpty());
		Assert.assertEquals(1, resource.findGroupsByName("sea-parent-for-2deletion").size());
		Assert.assertTrue(getGroup().findAll().get("sea-parent-for-2deletion").getSubGroups().isEmpty());
		Assert.assertNull(getGroup().findAll().get("sea-parent-for-2deletion-sub"));
		Assert.assertEquals("sea-parent-for-2deletion", resource.findGroupsByName("sea-parent-for-2deletion").get(0).getId());
		Assert.assertNull(((GroupLdapRepository) getGroup()).findAllNoCache().get("sea-parent-for-2deletion-sub"));

		// Check the new status of the deleted child
		Assert.assertFalse(resource.checkSubscriptionStatus(childParameters).getStatus().isUp());

		// Rollback the creation of the parent
		resource.delete(parentSubscription.getId(), true);
		Assert.assertEquals(0, resource.findGroupsByName("sea-parent-for-2deletion").size());
		Assert.assertNull(getGroup().findAll().get("sea-parent-for-2deletion"));

		// Check the LDAP content
		Assert.assertNull(((GroupLdapRepository) getGroup()).findAllNoCache().get("sea-parent-for-2deletion"));
	}

	@Test
	public void getVersion() throws Exception {
		final String version = resource.getVersion(null);
		Assert.assertEquals("3", version);
	}

	@Test
	public void getLastVersion() throws Exception {
		final String lastVersion = resource.getLastVersion();
		Assert.assertEquals("3", lastVersion);
	}

	@Test
	public void validateGroupNotExists() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(IdentityResource.PARAMETER_GROUP, BusinessException.KEY_UNKNOW_ID));

		final Map<String, String> parameters = pvResource.getNodeParameters("service:id:ldap:dig");
		parameters.put(IdentityResource.PARAMETER_GROUP, "broken");
		resource.validateGroup(parameters);
	}

	@Test
	public void validateGroupNotProject() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(IdentityResource.PARAMETER_GROUP, "group-type"));

		final Map<String, String> parameters = pvResource.getNodeParameters("service:id:ldap:dig");
		parameters.put(IdentityResource.PARAMETER_GROUP, "vigireport");
		resource.validateGroup(parameters);
	}

	@Test
	public void validateGroup() {
		final Map<String, String> parameters = pvResource.getNodeParameters("service:id:ldap:dig");
		parameters.put(IdentityResource.PARAMETER_GROUP, "gfi-gstack");

		final INamableBean<String> group = resource.validateGroup(parameters);
		Assert.assertNotNull(group);
		Assert.assertEquals("gfi-gstack", group.getId());
		Assert.assertEquals("gfi-gStack", group.getName());
	}

	/**
	 * Create a group in a existing OU "sea". Most Simple case. Group matches exactly to the pkey of the project.
	 * 
	 * @return the created subscription.
	 */
	private Subscription create(final String groupAndProject) throws Exception {
		// Preconditions
		Assert.assertNull(getGroup().findById(groupAndProject));
		Assert.assertNotNull(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").get("sea"));

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
		Assert.assertNotNull(groupLdap);
		Assert.assertEquals(groupAndProject, groupLdap.getName());
		Assert.assertEquals(groupAndProject, groupLdap.getId());
		Assert.assertEquals("cn=" + groupAndProject + ",ou=sea,ou=project,dc=sample,dc=com", groupLdap.getDn());
		Assert.assertNotNull(projectCustomerLdapRepository.findAllNoCache("ou=project,dc=sample,dc=com").get("sea"));
		Assert.assertNotNull(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").get("sea"));

		return subscription2;
	}

	/**
	 * Create a group in a existing OU. Most Simple case. Group matches exactly to the pkey of the project.
	 */
	@Test
	public void create() throws Exception {
		resource.delete(create("sea-new-project").getId(), true);
	}

	/**
	 * Create a group with the same name.
	 */
	@Test
	public void createAlreadyExist() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(IdentityResource.PARAMETER_GROUP, "already-exist"));

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-octopus"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-octopus");
		setOu(subscription2, "sea");

		basicCreate(subscription2);
	}

	/**
	 * Create a group inside an existing group. Parent group matches exactly to the pkey of the project.
	 */
	@Test
	public void createSubGroup() throws Exception {
		// Create the parent group
		final Project newProject = create("sea-parent").getProject();
		createSubGroup(newProject, "sea-parent", "sea-parent-client");
	}

	/**
	 * Create a group inside an existing group without reusing the name of the parent group.
	 */
	@Test
	public void createNotCompliantGroupForParent() throws Exception {
		// Create the parent group
		final Project newProject = create("sea-parent2").getProject();
		createSubGroup(newProject, "sea-parent2", "sea-parent2-client");

		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(IdentityResource.PARAMETER_GROUP, "pattern"));

		createSubGroup(newProject, "sea-parent2-client", "sea-parent2-dev");
	}

	/**
	 * Create a group for an existing project, but without reusing the pkey of this project.
	 */
	@Test
	public void createNotCompliantGroupForProject() throws Exception {

		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(IdentityResource.PARAMETER_GROUP, "pattern"));

		// Preconditions
		Assert.assertNotNull(getGroup().findById("sea-octopus"));
		Assert.assertNull(getGroup().findById("sea-octopusZZ"));
		Assert.assertNotNull(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").get("sea"));

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-octopus"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-octopusZZ");
		setOu(subscription2, "sea");

		// Invoke link for an already linked entity, since for now
		basicCreate(subscription2);
	}

	/**
	 * Create a group for an existing project, reusing the pkey of this project and without suffix.
	 */
	@Test
	public void createNotCompliantGroupForProject2() throws Exception {

		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(IdentityResource.PARAMETER_GROUP, "pattern"));

		// Preconditions
		Assert.assertNotNull(getGroup().findById("sea-octopus"));
		Assert.assertNull(getGroup().findById("sea-octopus-"));
		Assert.assertNotNull(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").get("sea"));

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-octopus"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-octopus-");
		setOu(subscription2, "sea");

		// Invoke link for an already linked entity, since for now
		basicCreate(subscription2);
	}

	/**
	 * Create a group for an existing project, perfect match with the pkey, but without reusing the OU of this project.
	 */
	@Test
	public void createNotCompliantGroupForOu() throws Exception {

		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(IdentityResource.PARAMETER_GROUP, "pattern"));

		// Preconditions
		Assert.assertNull(getGroup().findById("sea-invalid-ou"));
		Assert.assertNotNull(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").get("sea"));

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-invalid-ou"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-invalid-ou");
		setOu(subscription2, "gfi");

		// Invoke link for an already linked entity, since for now
		basicCreate(subscription2);
	}

	/**
	 * Create a group inside an existing group without reusing the name of the parent.
	 */
	@Test
	public void createNotExistingParentGroup() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(IdentityResource.PARAMETER_PARENT_GROUP, "unknown-id"));

		// Preconditions
		Assert.assertNotNull(getGroup().findById("sea-octopus"));
		Assert.assertNull(getGroup().findById("sea-octopus-client"));
		Assert.assertNotNull(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").get("sea"));

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-orpahn"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-orpahn-any");
		setParentGroup(subscription2, "sea-orpahn");
		setOu(subscription2, "sea");

		// Invoke link for an already linked entity, since for now
		basicCreate(subscription2);
	}

	/**
	 * Create a group inside a new organizational unit. Not an error, lazy creation. Exact match for group and pkey.
	 */
	@Test
	public void createOuNotExists() throws Exception {

		// Preconditions
		Assert.assertNull(getGroup().findById("some-new-project"));
		Assert.assertNull(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").get("some"));

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject("some-new-project"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "some-new-project");
		setOu(subscription2, "some");

		basicCreate(subscription2);

		// Checks
		final GroupOrg groupLdap = getGroup().findById("some-new-project");
		Assert.assertNotNull(groupLdap);
		Assert.assertEquals("some-new-project", groupLdap.getName());
		Assert.assertEquals("cn=some-new-project,ou=some,ou=project,dc=sample,dc=com", groupLdap.getDn());
		Assert.assertEquals("some-new-project", groupLdap.getId());
		Assert.assertNotNull(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").get("some"));

		resource.delete(subscription2.getId(), true);
	}

	@Test
	public void link() throws Exception {

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(subscription.getProject());
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);
		em.flush();
		em.clear();

		// Add parameters
		setGroup(subscription2, "sea-octopus");

		final CacheCompany company = new CacheCompany();
		company.setDescription("ou=c,dc=sample,dc=com");
		company.setId("c");
		company.setName("C");
		em.persist(company);

		final CacheUser user = new CacheUser();
		user.setId(DEFAULT_USER);
		user.setCompany(company);
		em.persist(user);

		final CacheGroup group = new CacheGroup();
		group.setDescription("cn=g,dc=sample,dc=com");
		group.setId("gfi-gstack");
		group.setName("gfi-gstack");
		// em.persist(group);

		final CacheMembership membership = new CacheMembership();
		membership.setUser(user);
		membership.setGroup(group);
		em.persist(membership);

		// Invoke link for an already linkd entity, since for now
		basicLink(subscription2);
		// Nothing to validate for now...
		resource.delete(subscription2.getId(), false);
	}

	@Test(expected = EntityNotFoundException.class)
	public void linkNotVisibleProject() throws Exception {

		// Invoke link for an already created entity, since for now
		initSpringSecurityContext("any");
		resource.link(this.subscription);
	}

	/**
	 * Visible project, but not visible target group
	 */
	@Test
	public void linkNotVisibleGroup() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(IdentityResource.PARAMETER_GROUP, BusinessException.KEY_UNKNOW_ID));

		// Attach the wrong group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		setGroup(subscription, "sea-octopus");

		// Invoke link for an already created entity, since for now
		initSpringSecurityContext("fdaugan");
		resource.link(this.subscription);
	}

	/**
	 * Visible project, but target group does not exist
	 */
	@Test
	public void linkNotExistingGroup() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(IdentityResource.PARAMETER_GROUP, BusinessException.KEY_UNKNOW_ID));

		// Attach the wrong group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		setGroup(subscription, "any-g");

		initSpringSecurityContext("fdaugan");
		resource.link(this.subscription);
	}

	@Test
	public void checkStatus() throws Exception {
		Assert.assertTrue(resource.checkStatus("service:id:ldap:dig", subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	public void checkSubscriptionStatus() throws Exception {
		Assert.assertTrue(resource.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription)).getStatus().isUp());
	}

	@Test
	public void findGroupsByNameNoRight() throws Exception {
		initSpringSecurityContext("any");
		final List<INamableBean<String>> jobs = resource.findGroupsByName("StAck");
		Assert.assertEquals(0, jobs.size());
	}

	@Test
	public void findGroupsByName() throws Exception {
		final List<INamableBean<String>> jobs = resource.findGroupsByName("StAck");
		Assert.assertTrue(jobs.size() >= 1);
		Assert.assertEquals("gfi-gStack", jobs.get(0).getName());
		Assert.assertEquals("gfi-gstack", jobs.get(0).getId());
	}

	@Test
	public void getProjectActivitiesCsv() throws Exception {
		// Reload the LDAP cache
		reloadLdapCache();

		// Stub JIRA
		final SampleActivityProvider activitiesProvider = Mockito.mock(SampleActivityProvider.class);
		final Map<String, Activity> activities = new HashMap<>();
		final Activity activity = new Activity();
		activity.setLastConnection(getDate(2015, 1, 1));
		activities.put("alongchu", activity);
		Mockito.when(activitiesProvider.getActivities(ArgumentMatchers.anyInt(), ArgumentMatchers.any())).thenReturn(activities);

		// Stub service locator
		final ServicePluginLocator servicePluginLocator = Mockito.mock(ServicePluginLocator.class);
		final ServicePluginLocator realServicePluginLocator = this.servicePluginLocator;
		Mockito.when(servicePluginLocator.getResource(ArgumentMatchers.anyString())).then(invocation -> {
			final String resource = (String) invocation.getArguments()[0];
			if (resource.equals("service:bt:jira:6")) {
				return activitiesProvider;
			}
			return realServicePluginLocator.getResource(resource);
		});
		initSpringSecurityContext(DEFAULT_USER);
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		final LdapPluginResource resource = new LdapPluginResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		resource.servicePluginLocator = servicePluginLocator;

		// Call
		((StreamingOutput) resource.getProjectActivitiesCsv(subscription, "file1").getEntity()).write(output);

		final List<String> csvLines = IOUtils.readLines(new ByteArrayInputStream(output.toByteArray()), StandardCharsets.UTF_8);
		Assert.assertEquals(17, csvLines.size());
		Assert.assertEquals("user;firstName;lastName;mail;JIRA 6", csvLines.get(0));
		Assert.assertEquals("alongchu;Arnaud;Longchu;arnaud.longchu@sample.com;2015/01/01 00:00:00", csvLines.get(9));
	}

	@Test
	public void getGroupActivitiesCsv() throws Exception {
		// Reload the LDAP cache
		reloadLdapCache();

		// Stub JIRA
		final SampleActivityProvider activitiesProvider = Mockito.mock(SampleActivityProvider.class);
		final Map<String, Activity> activities = new HashMap<>();
		final Activity activity = new Activity();
		activity.setLastConnection(getDate(2015, 1, 1));
		activities.put("alongchu", activity);
		Mockito.when(activitiesProvider.getActivities(ArgumentMatchers.anyInt(), ArgumentMatchers.any())).thenReturn(activities);

		// Stub service locator
		final ServicePluginLocator servicePluginLocator = Mockito.mock(ServicePluginLocator.class);
		final ServicePluginLocator realServicePluginLocator = this.servicePluginLocator;
		Mockito.when(servicePluginLocator.getResource(ArgumentMatchers.anyString())).then(invocation -> {
			final String resource = (String) invocation.getArguments()[0];
			if (resource.equals("service:bt:jira:6")) {
				return activitiesProvider;
			}
			return realServicePluginLocator.getResource(resource);
		});
		initSpringSecurityContext(DEFAULT_USER);
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		final LdapPluginResource resource = new LdapPluginResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		resource.servicePluginLocator = servicePluginLocator;

		// Call
		((StreamingOutput) resource.getGroupActivitiesCsv(subscription, "file1").getEntity()).write(output);

		final List<String> csvLines = IOUtils.readLines(new ByteArrayInputStream(output.toByteArray()), StandardCharsets.UTF_8);
		Assert.assertEquals(2, csvLines.size());
		Assert.assertEquals("user;firstName;lastName;mail;JIRA 6", csvLines.get(0));
		Assert.assertEquals("alongchu;Arnaud;Longchu;arnaud.longchu@sample.com;2015/01/01 00:00:00", csvLines.get(1));
	}

	@Test
	public void getGroupActivitiesCsvEmpty() throws Exception {
		// Reload the LDAP cache
		reloadLdapCache();

		// Stub JIRA
		final SampleActivityProvider activitiesProvider = Mockito.mock(SampleActivityProvider.class);
		final Map<String, Activity> activities = new HashMap<>();
		final Activity activity = new Activity();
		activity.setLastConnection(getDate(2015, 1, 1));
		activities.put("alongchu", activity);
		Mockito.when(activitiesProvider.getActivities(ArgumentMatchers.anyInt(), ArgumentMatchers.any())).thenReturn(activities);

		// Stub service locator
		final ServicePluginLocator servicePluginLocator = Mockito.mock(ServicePluginLocator.class);
		final ServicePluginLocator realServicePluginLocator = this.servicePluginLocator;
		Mockito.when(servicePluginLocator.getResource(ArgumentMatchers.anyString())).then(invocation -> {
			final String resource = (String) invocation.getArguments()[0];
			if (resource.equals("service:bt:jira:6")) {
				return activitiesProvider;
			}
			return realServicePluginLocator.getResource(resource);
		});
		initSpringSecurityContext(DEFAULT_USER);
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		final LdapPluginResource resource = new LdapPluginResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		resource.servicePluginLocator = servicePluginLocator;

		// Call
		final int otherSubscription = em
				.createQuery("SELECT s.id FROM Subscription s WHERE s.project.name = ?1 AND s.node.id LIKE CONCAT(?2,'%')", Integer.class)
				.setParameter(1, "gStack").setParameter(2, IdentityResource.SERVICE_KEY).setMaxResults(2).getResultList().get(1);
		((StreamingOutput) resource.getGroupActivitiesCsv(otherSubscription, "file1").getEntity()).write(output);

		final List<String> csvLines = IOUtils.readLines(new ByteArrayInputStream(output.toByteArray()), StandardCharsets.UTF_8);
		Assert.assertEquals("user;firstName;lastName;mail;JIRA 6", csvLines.get(0));
		Assert.assertEquals(17, csvLines.size());
	}

	/**
	 * Reload the LDAP cache
	 */
	private void reloadLdapCache() {
		// Ensure LDAP cache is loaded
		CacheManager.getInstance().getCache("ldap").removeAll();
		cache.getLdapData();
		em.flush();
		em.clear();
	}

	@Test
	public void addSubscriptionActivitiesNotProvider() throws Exception {
		final Map<String, Map<String, Activity>> activities = new HashMap<>();
		new LdapPluginResource().addSubscriptionActivities(activities, null, null, null, null);
		Assert.assertTrue(activities.isEmpty());
	}

	@Test
	public void addSubscriptionActivitiesDuplicateNode() throws Exception {
		final Map<String, Map<String, Activity>> activities = new HashMap<>();
		final LdapPluginResource resource = new LdapPluginResource();
		final Subscription susbscription = new Subscription();
		final Node service = new Node();
		service.setId("J");
		susbscription.setNode(service);
		final SampleActivityProvider plugin = Mockito.mock(SampleActivityProvider.class);
		final Set<INamableBean<String>> nodes = new HashSet<>();
		nodes.add(service);
		resource.addSubscriptionActivities(activities, null, susbscription, plugin, nodes);
		Assert.assertTrue(activities.isEmpty());
	}

	@Test
	public void addSubscriptionActivities() throws Exception {
		final LdapPluginResource resource = new LdapPluginResource();
		final Map<String, Map<String, Activity>> activities = new HashMap<>();
		final Subscription susbscription = new Subscription();
		final Node service = new Node();
		service.setId("J1");
		susbscription.setNode(service);
		susbscription.setId(1);
		final SampleActivityProvider plugin = Mockito.mock(SampleActivityProvider.class);
		final Map<String, Activity> activities1 = new HashMap<>();
		final Activity activity2 = new Activity();
		activities1.put(DEFAULT_USER, activity2);
		Mockito.when(plugin.getActivities(1, null)).thenReturn(activities1);
		final Set<INamableBean<String>> nodes = new HashSet<>();
		resource.addSubscriptionActivities(activities, null, susbscription, plugin, nodes);
		Assert.assertEquals(1, activities.size());
		Assert.assertTrue(activities.containsKey(DEFAULT_USER));
		Assert.assertEquals(1, activities.get(DEFAULT_USER).size());
		Assert.assertTrue(activities.get(DEFAULT_USER).containsKey("J1"));
		Assert.assertEquals(activity2, activities.get(DEFAULT_USER).get("J1"));
	}

	@Test
	public void addSubscriptionActivitiesDuplicateUser() throws Exception {
		final LdapPluginResource resource = new LdapPluginResource();
		final Map<String, Map<String, Activity>> activities = new HashMap<>();
		activities.put(DEFAULT_USER, new HashMap<>());
		final Activity activity1 = new Activity();
		activities.get(DEFAULT_USER).put("J0", activity1);
		final Subscription susbscription = new Subscription();
		final Node service = new Node();
		service.setId("J1");
		susbscription.setNode(service);
		susbscription.setId(1);
		final SampleActivityProvider plugin = Mockito.mock(SampleActivityProvider.class);
		final Map<String, Activity> activities1 = new HashMap<>();
		final Activity activity2 = new Activity();
		activities1.put(DEFAULT_USER, activity2);
		Mockito.when(plugin.getActivities(1, null)).thenReturn(activities1);
		final Set<INamableBean<String>> nodes = new HashSet<>();
		resource.addSubscriptionActivities(activities, null, susbscription, plugin, nodes);
		Assert.assertEquals(1, activities.size());
		Assert.assertTrue(activities.containsKey(DEFAULT_USER));
		Assert.assertEquals(2, activities.get(DEFAULT_USER).size());
		Assert.assertTrue(activities.get(DEFAULT_USER).containsKey("J1"));
		Assert.assertEquals(activity1, activities.get(DEFAULT_USER).get("J0"));
		Assert.assertEquals(activity2, activities.get(DEFAULT_USER).get("J1"));
	}

	@Test
	public void findCustomersByName() throws Exception {
		final Collection<INamableBean<String>> customers = resource.findCustomersByName("ea");
		Assert.assertEquals(1, customers.size());
		Assert.assertEquals("sea", customers.iterator().next().getName());
		Assert.assertEquals("sea", customers.iterator().next().getId());
	}

	/**
	 * Create a new project
	 */
	private Project newProject(final String pkey) {
		final Project project = new Project();
		project.setPkey(pkey);
		project.setName("ANY - " + pkey);
		project.setTeamLeader(DEFAULT_USER);
		em.persist(project);
		return project;
	}

	private void setGroup(final Subscription subscription, final String group) {
		final Parameter groupParameter = new Parameter();
		groupParameter.setId(IdentityResource.PARAMETER_GROUP);
		final ParameterValue groupParameterValue = new ParameterValue();
		groupParameterValue.setParameter(groupParameter);
		groupParameterValue.setData(group);
		groupParameterValue.setSubscription(subscription);
		em.persist(groupParameterValue);
		em.flush();
	}

	private void setOu(final Subscription subscription, final String ou) {
		final Parameter customerParameter = new Parameter();
		customerParameter.setId(IdentityResource.PARAMETER_OU);
		final ParameterValue customerParameterValue = new ParameterValue();
		customerParameterValue.setParameter(customerParameter);
		customerParameterValue.setData(ou);
		customerParameterValue.setSubscription(subscription);
		em.persist(customerParameterValue);
		em.flush();
	}

	private void setParentGroup(final Subscription subscription, final String parentGroup) {
		final Parameter parentGroupParameter = new Parameter();
		parentGroupParameter.setId(IdentityResource.PARAMETER_PARENT_GROUP);
		final ParameterValue parentGroupParameterValue = new ParameterValue();
		parentGroupParameterValue.setParameter(parentGroupParameter);
		parentGroupParameterValue.setData(parentGroup);
		parentGroupParameterValue.setSubscription(subscription);
		em.persist(parentGroupParameterValue);
	}

	private void basicCreate(final Subscription subscription2) throws Exception {
		initSpringSecurityContext(DEFAULT_USER);
		resource.create(subscription2.getId());
		em.flush();
		em.clear();
	}

	private void basicLink(final Subscription subscription2) throws Exception {
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
	private Subscription createSubGroup(final Project newProject, final String parentGroup, final String subGroup) throws Exception {

		// Preconditions
		Assert.assertNotNull(getGroup().findById(parentGroup));
		Assert.assertNull(getGroup().findById(subGroup));
		Assert.assertNotNull(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").get("sea"));

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
		Assert.assertNotNull(groupLdap);
		Assert.assertEquals(subGroup, groupLdap.getName());
		Assert.assertEquals("cn=" + subGroup + ",cn=" + parentGroup + ",ou=sea,ou=project,dc=sample,dc=com", groupLdap.getDn());
		Assert.assertEquals(subGroup, groupLdap.getId());
		Assert.assertEquals(1, groupLdap.getGroups().size());
		Assert.assertTrue(groupLdap.getGroups().contains(parentGroup));
		final GroupOrg groupLdapParent = getGroup().findById(parentGroup);
		Assert.assertEquals(1, groupLdapParent.getSubGroups().size());
		Assert.assertTrue(groupLdapParent.getSubGroups().contains(subGroup));
		return subscription2;
	}

	@Test
	public void acceptNoParameters() {
		Assert.assertFalse(resource.accept(null, "service:any"));
	}

	@Test
	public void acceptNotMatch() {
		final Node ldap = new Node();
		ldap.setId("service:id:ldap:test");
		ldap.setRefined(nodeRepository.findOneExpected("service:id:ldap"));
		ldap.setName("LDAP Test");
		nodeRepository.saveAndFlush(ldap);
		final ParameterValue parameterValue = new ParameterValue();
		parameterValue.setNode(ldap);
		parameterValue.setParameter(parameterRepository.findOneExpected("service:id:uid-pattern"));
		parameterValue.setData("-nomatch-");
		em.persist(parameterValue);
		Assert.assertFalse(resource.accept(new UsernamePasswordAuthenticationToken("some", ""), "service:id:ldap:test"));
	}

	@Test
	public void accept() {
		final Node ldap = new Node();
		ldap.setId("service:id:ldap:test");
		ldap.setRefined(nodeRepository.findOneExpected("service:id:ldap"));
		ldap.setName("LDAP Test");
		nodeRepository.saveAndFlush(ldap);
		persistParameter(ldap, IdentityResource.PARAMETER_UID_PATTERN, "some-.*-text");
		Assert.assertTrue(resource.accept(new UsernamePasswordAuthenticationToken("some-awesome-text", ""), "service:id:ldap:test"));
	}

	@Test
	public void authenticatePrimary() throws Exception {
		final Authentication authentication = new UsernamePasswordAuthenticationToken("fdaugan", "Azerty01");
		Assert.assertSame(authentication, resource.authenticate(authentication, "service:id:ldap:dig", true));
	}

	@Test(expected = BadCredentialsException.class)
	public void authenticateFail() throws Exception {
		final Authentication authentication = new UsernamePasswordAuthenticationToken("fdaugan", "any");
		resource.authenticate(authentication, "service:id:ldap:dig", true);
	}

	@Test
	public void authenticateSecondaryMock() throws Exception {
		// Create a new LDAP node pluged to the primary node
		newLdap();

		final Authentication authentication = new UsernamePasswordAuthenticationToken("mmartin", "complexOne");
		final Authentication localAuthentication = resource.authenticate(authentication, "service:id:ldap:secondary", false);
		Assert.assertEquals("mmartin", localAuthentication.getName());
	}

	@Test
	public void toApplicationUserExists() {
		// Create a new LDAP node plugged to the primary node
		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("marc.martin@sample.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setName("secondarylogin");
		user.setCompany("gfi");
		Assert.assertEquals("mmartin", resource.toApplicationUser(user));

		final UserOrg userLdap = userResource.findByIdNoCache("mmartin");
		Assert.assertEquals("mmartin", userLdap.getName());
		Assert.assertEquals("Marc", userLdap.getFirstName());
		Assert.assertEquals("Martin", userLdap.getLastName());
		Assert.assertEquals("marc.martin@sample.com", userLdap.getMails().get(0));
	}

	@Test
	public void toApplicationUserNew() {
		// Create a new LDAP node plugged to the primary node
		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("some@where.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setCompany("gfi");
		user.setName("secondarylogin");
		Assert.assertEquals("flast123", resource.toApplicationUser(user));

		final UserOrg userLdap = userResource.findByIdNoCache("flast123");
		Assert.assertEquals("flast123", userLdap.getName());
		Assert.assertEquals("First", userLdap.getFirstName());
		Assert.assertEquals("Last123", userLdap.getLastName());
		Assert.assertEquals("gfi", userLdap.getCompany());
		Assert.assertEquals("some@where.com", userLdap.getMails().get(0));
		userResource.delete("flast123");
	}

	@Test
	public void toApplicationUserNewWithCollision() {
		// Create a new LDAP node plugged to the primary node
		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("some@where.com"));
		user.setFirstName("Marc");
		user.setLastName("Martin");
		user.setCompany("gfi");
		user.setName("secondarylogin");
		Assert.assertEquals("mmartin1", resource.toApplicationUser(user));

		final UserOrg userLdap = userResource.findByIdNoCache("mmartin1");
		Assert.assertEquals("mmartin1", userLdap.getName());
		Assert.assertEquals("Marc", userLdap.getFirstName());
		Assert.assertEquals("Martin", userLdap.getLastName());
		Assert.assertEquals("gfi", userLdap.getCompany());
		Assert.assertEquals("some@where.com", userLdap.getMails().get(0));
		userResource.delete("mmartin1");
	}

	@Test(expected = NotAuthorizedException.class)
	public void toApplicationUserTooManyMail() {
		// Create a new LDAP node pluged to the primary node
		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("fabrice.daugan@sample.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setName("secondarylogin");
		resource.toApplicationUser(user);
	}

	@Test
	public void toLogin() throws Exception {
		final UserOrg user = new UserOrg();
		user.setFirstName("First");
		user.setLastName("Last123");
		Assert.assertEquals("flast123", resource.toLogin(user));
	}

	@Test(expected = NotAuthorizedException.class)
	public void toLoginNoFirstName() {
		final UserOrg user = new UserOrg();
		user.setLastName("Last123");
		resource.toLogin(user);
	}

	@Test(expected = NotAuthorizedException.class)
	public void toLoginNoLastName() {
		final UserOrg user = new UserOrg();
		user.setFirstName("First");
		resource.toLogin(user);
	}

	@Test(expected = NotAuthorizedException.class)
	public void authenticateSecondaryNoMail() {
		// Create a new LDAP node pluged to the primary node

		newLdap();

		final Authentication authentication = new UsernamePasswordAuthenticationToken("jdupont", "Azerty01");
		resource.authenticate(authentication, "service:id:ldap:secondary", false);
	}

	@Test(expected = BadCredentialsException.class)
	public void authenticateSecondaryFail() {
		// Create a new LDAP node pluged to the primary node
		newLdap();

		final Authentication authentication = new UsernamePasswordAuthenticationToken("fdaugan", "any");
		final Authentication localAuthentication = resource.authenticate(authentication, "service:id:ldap:secondary", false);
		Assert.assertEquals("fdaugan", localAuthentication.getName());
	}

	@Test(expected = UncategorizedLdapException.class)
	public void newApplicationUserSaveFail() {
		final LdapPluginResource resource = new LdapPluginResource();
		resource.userResource = Mockito.mock(UserOrgResource.class);
		Mockito.when(resource.userResource.findByIdNoCache("flast123")).thenReturn(null);
		Mockito.doThrow(new UncategorizedLdapException("")).when(resource.userResource).saveOrUpdate(ArgumentMatchers.any(UserOrgEditionVo.class));

		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("fabrice.daugan@sample.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setName("secondarylogin");
		user.setCompany("gfi");
		resource.newApplicationUser(user);
	}

	@Test(expected = RuntimeException.class)
	public void newApplicationUserNextLoginFail() {
		final LdapPluginResource resource = new LdapPluginResource();
		resource.userResource = Mockito.mock(UserOrgResource.class);
		Mockito.doThrow(new RuntimeException()).when(resource.userResource).findByIdNoCache("flast123");

		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("fabrice.daugan@sample.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setName("secondarylogin");
		user.setCompany("gfi");
		resource.newApplicationUser(user);
	}

	private void newLdap() {
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
		persistParameter(ldap, LdapPluginResource.PARAMETER_PEOPLE_DN, "dc=sample,dc=com");
		persistParameter(ldap, LdapPluginResource.PARAMETER_DEPARTMENT_ATTRIBUTE, "departmentNumber");
		persistParameter(ldap, LdapPluginResource.PARAMETER_LOCAL_ID_ATTRIBUTE, "employeeNumber");
		persistParameter(ldap, LdapPluginResource.PARAMETER_PEOPLE_CLASS, "inetOrgPerson");
		persistParameter(ldap, LdapPluginResource.PARAMETER_COMPANY_PATTERN, "gfi");
	}

	private void persistParameter(final Node node, final String id, final String value) {
		final ParameterValue parameterValue = new ParameterValue();
		parameterValue.setNode(node);
		parameterValue.setParameter(parameterRepository.findOneExpected(id));
		parameterValue.setData(value);
		em.persist(parameterValue);
	}
}

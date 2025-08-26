/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.resource;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.Activity;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.model.CacheCompany;
import org.ligoj.app.iam.model.CacheGroup;
import org.ligoj.app.iam.model.CacheMembership;
import org.ligoj.app.iam.model.CacheUser;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.id.ldap.dao.UserLdapRepository;
import org.ligoj.app.plugin.id.resource.IdentityResource;
import org.ligoj.app.plugin.id.resource.UserOrgEditionVo;
import org.ligoj.app.plugin.id.resource.UserOrgResource;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.ldap.UncategorizedLdapException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.Rollback;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Test class of {@link LdapPluginResource}
 */
@Rollback
@Transactional
class LdapPluginResourceTest extends AbstractLdapPluginResourceTest {

	@Test
	void deleteNoMoreGroup() {
		final var subscription = new Subscription();
		subscription.setProject(projectRepository.findByName("Jupiter"));
		subscription.setNode(nodeRepository.findOneExpected("service:id:ldap:dig"));
		em.persist(subscription);

		// Attach the wrong group
		setGroup(subscription, "any");

		initSpringSecurityContext("fdaugan");
		final var parameters = subscriptionResource.getParametersNoCheck(subscription.getId());
		Assertions.assertFalse(resource.checkSubscriptionStatus(parameters).getStatus().isUp());

		resource.delete(subscription.getId(), true);
		em.flush();
		em.clear();
		Assertions.assertFalse(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
	}

	/**
	 * The un-subscription without deletion has no effect
	 */
	@Test
	void delete() {
		initSpringSecurityContext("fdaugan");
		final var parameters = subscriptionResource.getParameters(subscription);
		Assertions.assertTrue(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
		resource.delete(subscription, false);
		em.flush();
		em.clear();
		Assertions.assertTrue(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
	}

	@Test
	void getVersion() {
		final var version = resource.getVersion(null);
		Assertions.assertEquals("3", version);
	}

	@Test
	void getLastVersion() {
		final var lastVersion = resource.getLastVersion();
		Assertions.assertEquals("3", lastVersion);
	}

	@Test
	void validateGroupNotExists() {
		final var parameters = pvResource.getNodeParameters("service:id:ldap:dig");
		parameters.put(IdentityResource.PARAMETER_GROUP, "broken");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.validateGroup(parameters)), IdentityResource.PARAMETER_GROUP, BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void validateGroupNotProject() {
		final var parameters = pvResource.getNodeParameters("service:id:ldap:dig");
		parameters.put(IdentityResource.PARAMETER_GROUP, "VigiReport");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.validateGroup(parameters)), IdentityResource.PARAMETER_GROUP, "group-type");
	}

	@Test
	void validateGroup() {
		final var parameters = pvResource.getNodeParameters("service:id:ldap:dig");
		parameters.put(IdentityResource.PARAMETER_GROUP, "ligoj-jupiter");

		final var group = resource.validateGroup(parameters);
		Assertions.assertNotNull(group);
		Assertions.assertEquals("ligoj-jupiter", group.getId());
		Assertions.assertEquals("ligoj-Jupiter", group.getName());
	}

	/**
	 * Create a group in an existing OU. Most Simple case. Group matches exactly to the pkey of the project.
	 */
	@Test
	void create() {
		resource.delete(create("sea-new-project").getId(), true);
	}

	/**
	 * Create a group with the same name.
	 */
	@Test
	void createAlreadyExist() {
		// Attach the new group
		final var subscription = em.find(Subscription.class, this.subscription);
		final var subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-octopus"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-octopus");
		setOu(subscription2, "sea");

		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> basicCreate(subscription2)), IdentityResource.PARAMETER_GROUP, "already-exist");
	}

	/**
	 * Create a group inside an existing group. Parent group matches exactly to the pkey of the project.
	 */
	@Test
	void createSubGroup() {
		// Create the parent group
		final var newProject = create("sea-parent").getProject();
		createSubGroup(newProject, "sea-parent", "sea-parent-client");
	}

	/**
	 * Create a group inside an existing group without reusing the name of the parent group.
	 */
	@Test
	void createNotCompliantGroupForParent() {
		// Create the parent group
		final var newProject = create("sea-parent2").getProject();
		createSubGroup(newProject, "sea-parent2", "sea-parent2-client");

		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> createSubGroup(newProject, "sea-parent2-client", "sea-parent2-dev")), IdentityResource.PARAMETER_GROUP, "pattern");
	}

	/**
	 * Create a group for an existing project, but without reusing the pkey of this project.
	 */
	@Test
	void createNotCompliantGroupForProject() {
		// Preconditions
		Assertions.assertNotNull(getGroup().findById("sea-octopus"));
		Assertions.assertNull(getGroup().findById("sea-octopusZZ"));
		Assertions.assertNotNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "sea"));

		// Attach the new group
		final var subscription = em.find(Subscription.class, this.subscription);
		final var subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-octopus"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-octopusZZ");
		setOu(subscription2, "sea");

		// Create a link for an already linked entity, since for now
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> basicCreate(subscription2)), IdentityResource.PARAMETER_GROUP, "pattern");
	}

	/**
	 * Create a group for an existing project, reusing the pkey of this project and without suffix.
	 */
	@Test
	void createNotCompliantGroupForProject2() {

		// Preconditions
		Assertions.assertNotNull(getGroup().findById("sea-octopus"));
		Assertions.assertNull(getGroup().findById("sea-octopus-"));
		Assertions.assertNotNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "sea"));

		// Attach the new group
		final var subscription = em.find(Subscription.class, this.subscription);
		final var subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-octopus"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-octopus-");
		setOu(subscription2, "sea");

		// Create a link for an already linked entity, since for now
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> basicCreate(subscription2)), IdentityResource.PARAMETER_GROUP, "pattern");
	}

	/**
	 * Create a group for an existing project with a perfect match with the pkey, but without reusing the OU of this project.
	 */
	@Test
	void createNotCompliantGroupForOu() {
		// Preconditions
		Assertions.assertNull(getGroup().findById("sea-invalid-ou"));
		Assertions.assertNotNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "sea"));

		// Attach the new group
		final var subscription = em.find(Subscription.class, this.subscription);
		final var subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-invalid-ou"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-invalid-ou");
		setOu(subscription2, "ligoj");

		// Create a link for an already linked entity, since for now
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> basicCreate(subscription2)), IdentityResource.PARAMETER_GROUP, "pattern");
	}

	/**
	 * Create a group inside an existing group without reusing the name of the parent.
	 */
	@Test
	void createNotExistingParentGroup() {
		// Preconditions
		Assertions.assertNotNull(getGroup().findById("sea-octopus"));
		Assertions.assertNull(getGroup().findById("sea-octopus-client"));
		Assertions.assertNotNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "sea"));

		// Attach the new group
		final var subscription = em.find(Subscription.class, this.subscription);
		final var subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-orphan"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-orphan-any");
		setParentGroup(subscription2, "sea-orphan");
		setOu(subscription2, "sea");

		// Create a link for an already linked entity, since for now
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> basicCreate(subscription2)), IdentityResource.PARAMETER_PARENT_GROUP, "unknown-id");
	}

	/**
	 * Create a group inside a new organizational unit. Not an error, lazy creation. Exact match for the group and pkey.
	 */
	@Test
	void createOuNotExists() {

		// Preconditions
		Assertions.assertNull(getGroup().findById("some-new-project"));
		Assertions.assertNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "some"));
		Assertions.assertFalse(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").contains("some"));

		// Attach the new group
		final var subscription = em.find(Subscription.class, this.subscription);
		final var subscription2 = new Subscription();
		subscription2.setProject(newProject("some-new-project"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "some-new-project");
		setOu(subscription2, "some");

		basicCreate(subscription2);

		// Checks
		final var groupLdap = getGroup().findById("some-new-project");
		Assertions.assertNotNull(groupLdap);
		Assertions.assertEquals("some-new-project", groupLdap.getName());
		Assertions.assertEquals("cn=some-new-project,ou=some,ou=project,dc=sample,dc=com", groupLdap.getDn());
		Assertions.assertEquals("some-new-project", groupLdap.getId());
		Assertions.assertNotNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "some"));
		Assertions.assertTrue(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").contains("some"));

		// Attach the new group
		final var subscription3 = new Subscription();
		subscription3.setProject(subscription2.getProject());
		subscription3.setNode(subscription.getNode());
		em.persist(subscription3);

		// Add parameters
		setGroup(subscription3, "some-new-project-sub");
		setOu(subscription3, "some");
		basicCreate(subscription3);

		// Delete the group and also try to delete the parent OU. This last step fails silently
		resource.delete(subscription2.getId(), true);

		// Delete the group and also successfully delete the parent
		resource.delete(subscription3.getId(), true);
	}

	@Test
	void link() {

		// Attach the new group
		final var subscription = em.find(Subscription.class, this.subscription);
		final var subscription2 = new Subscription();
		subscription2.setProject(subscription.getProject());
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);
		em.flush();
		em.clear();

		// Add parameters
		setGroup(subscription2, "sea-octopus");

		final var company = new CacheCompany();
		company.setDescription("ou=c,dc=sample,dc=com");
		company.setId("c");
		company.setName("C");
		em.persist(company);

		final var user = new CacheUser();
		user.setId(DEFAULT_USER);
		user.setCompany(company);
		em.persist(user);

		final var group = new CacheGroup();
		group.setDescription("cn=g,dc=sample,dc=com");
		group.setId("ligoj-jupiter");
		group.setName("ligoj-jupiter");
		// em.persist(group);

		final var membership = new CacheMembership();
		membership.setUser(user);
		membership.setGroup(group);
		em.persist(membership);

		// Create a link for an already linked entity, since for now
		basicLink(subscription2);
		// Nothing to validate for now...
		resource.delete(subscription2.getId(), false);
	}

	@Test
	void linkNotVisibleProject() {

		// Create a link for an already created entity, since for now
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class, () -> resource.link(this.subscription));
	}

	/**
	 * Visible project, but not visible target group
	 */
	@Test
	void linkNotVisibleGroup() {
		// Attach the wrong group
		final var subscription = em.find(Subscription.class, this.subscription);
		setGroup(subscription, "sea-octopus");

		// Create a link for an already created entity, since for now
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.link(this.subscription)), IdentityResource.PARAMETER_GROUP, BusinessException.KEY_UNKNOWN_ID);
	}

	/**
	 * Visible project, but the target group does not exist
	 */
	@Test
	void linkNotExistingGroup() {
		// Attach the wrong group
		final var subscription = em.find(Subscription.class, this.subscription);
		setGroup(subscription, "any-g");

		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.link(this.subscription)), IdentityResource.PARAMETER_GROUP, BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void checkStatus() {
		Assertions.assertTrue(resource.checkStatus("service:id:ldap:dig", subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	void checkSubscriptionStatus() {
		Assertions.assertTrue(resource.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription)).getStatus().isUp());
	}

	@Test
	void findGroupsByNameNoRight() {
		initSpringSecurityContext("any");
		final var jobs = resource.findGroupsByName("piTer");
		Assertions.assertEquals(0, jobs.size());
	}

	@Test
	void findGroupsByName() {
		final var jobs = resource.findGroupsByName("piTer");
		Assertions.assertFalse(jobs.isEmpty());
		Assertions.assertEquals("ligoj-Jupiter", jobs.getFirst().getName());
		Assertions.assertEquals("ligoj-jupiter", jobs.getFirst().getId());
	}

	@Test
	void getProjectActivitiesCsv() throws Exception {
		// Reload the LDAP cache
		reloadLdapCache();

		// Stub JIRA
		final var activitiesProvider = Mockito.mock(SampleActivityProvider.class);
		final var activities = new HashMap<String, Activity>();
		final var activity = new Activity();
		activity.setLastConnection(getDate(2015, 1, 1));
		activities.put("admin-test", activity);
		Mockito.when(activitiesProvider.getActivities(ArgumentMatchers.anyInt(), ArgumentMatchers.any())).thenReturn(activities);

		// Stub service locator
		final var servicePluginLocator = Mockito.mock(ServicePluginLocator.class);
		final var realServicePluginLocator = this.servicePluginLocator;
		Mockito.when(servicePluginLocator.getResource(ArgumentMatchers.anyString())).then(invocation -> {
			final var resource = (String) invocation.getArguments()[0];
			if (resource.equals("service:bt:jira:6")) {
				return activitiesProvider;
			}
			return realServicePluginLocator.getResource(resource);
		});
		initSpringSecurityContext(DEFAULT_USER);
		final var output = new ByteArrayOutputStream();
		final var resource = new LdapPluginResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		resource.servicePluginLocator = servicePluginLocator;

		// Call
		((StreamingOutput) resource.getProjectActivitiesCsv(subscription, "file1").getEntity()).write(output);

		final var csvLines = IOUtils.readLines(new ByteArrayInputStream(output.toByteArray()), StandardCharsets.UTF_8);
		Assertions.assertEquals(2, csvLines.size());
		Assertions.assertEquals("user;firstName;lastName;mail;JIRA 6", csvLines.getFirst());
		Assertions.assertEquals("admin-test;Arnaud;Test;arnaud.test@sample.com;2015/01/01 00:00:00", csvLines.get(1));
	}

	@Test
	void getGroupActivitiesCsv() throws Exception {
		// Reload the LDAP cache
		reloadLdapCache();

		// Stub JIRA
		final var activitiesProvider = Mockito.mock(SampleActivityProvider.class);
		final var activities = new HashMap<String, Activity>();
		final var activity = new Activity();
		activity.setLastConnection(getDate(2015, 1, 1));
		activities.put("admin-test", activity);
		Mockito.when(activitiesProvider.getActivities(ArgumentMatchers.anyInt(), ArgumentMatchers.any())).thenReturn(activities);

		// Stub service locator
		final var servicePluginLocator = Mockito.mock(ServicePluginLocator.class);
		final var realServicePluginLocator = this.servicePluginLocator;
		Mockito.when(servicePluginLocator.getResource(ArgumentMatchers.anyString())).then(invocation -> {
			final var resource = (String) invocation.getArguments()[0];
			if (resource.equals("service:bt:jira:6")) {
				return activitiesProvider;
			}
			return realServicePluginLocator.getResource(resource);
		});
		initSpringSecurityContext(DEFAULT_USER);
		final var output = new ByteArrayOutputStream();
		final var resource = new LdapPluginResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		resource.servicePluginLocator = servicePluginLocator;

		// Call
		((StreamingOutput) resource.getGroupActivitiesCsv(subscription, "file1").getEntity()).write(output);

		final var csvLines = IOUtils.readLines(new ByteArrayInputStream(output.toByteArray()), StandardCharsets.UTF_8);
		Assertions.assertEquals(2, csvLines.size());
		Assertions.assertEquals("user;firstName;lastName;mail;JIRA 6", csvLines.getFirst());
		Assertions.assertEquals("admin-test;Arnaud;Test;arnaud.test@sample.com;2015/01/01 00:00:00", csvLines.get(1));
	}

	@Test
	void getGroupActivitiesCsvEmpty() throws Exception {
		// Reload the LDAP cache
		reloadLdapCache();

		// Stub JIRA
		final var activitiesProvider = Mockito.mock(SampleActivityProvider.class);
		final var activities = new HashMap<String, Activity>();
		final var activity = new Activity();
		activity.setLastConnection(getDate(2015, 1, 1));
		activities.put("admin-test", activity);
		Mockito.when(activitiesProvider.getActivities(ArgumentMatchers.anyInt(), ArgumentMatchers.any())).thenReturn(activities);

		// Stub service locator
		final var servicePluginLocator = Mockito.mock(ServicePluginLocator.class);
		final var realServicePluginLocator = this.servicePluginLocator;
		Mockito.when(servicePluginLocator.getResource(ArgumentMatchers.anyString())).then(invocation -> {
			final var resource = (String) invocation.getArguments()[0];
			if (resource.equals("service:bt:jira:6")) {
				return activitiesProvider;
			}
			return realServicePluginLocator.getResource(resource);
		});
		initSpringSecurityContext(DEFAULT_USER);
		final var output = new ByteArrayOutputStream();
		final var resource = new LdapPluginResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		resource.servicePluginLocator = servicePluginLocator;

		// Get the subscription using a subscribed broken group
		final int otherSubscription = em.createQuery("SELECT s.id FROM Subscription s WHERE s.project.name = ?1 AND s.node.id LIKE CONCAT(?2,'%')", Integer.class).setParameter(1, "Jupiter").setParameter(2, IdentityResource.SERVICE_KEY).setMaxResults(2).getResultList().get(1);
		((StreamingOutput) resource.getGroupActivitiesCsv(otherSubscription, "file1").getEntity()).write(output);

		final var csvLines = IOUtils.readLines(new ByteArrayInputStream(output.toByteArray()), StandardCharsets.UTF_8);
		Assertions.assertEquals("user;firstName;lastName;mail;JIRA 6", csvLines.getFirst());
		Assertions.assertEquals(1, csvLines.size());
	}

	@Test
	void addSubscriptionActivitiesNotProvider() throws Exception {
		final Map<String, Map<String, Activity>> activities = new HashMap<>();
		new LdapPluginResource().addSubscriptionActivities(activities, null, null, null, null);
		Assertions.assertTrue(activities.isEmpty());
	}

	@Test
	void addSubscriptionActivitiesDuplicateNode() throws Exception {
		final var activities = new HashMap<String, Map<String, Activity>>();
		final var resource = new LdapPluginResource();
		final var subscription = new Subscription();
		final var service = new Node();
		service.setId("J");
		subscription.setNode(service);
		final var plugin = Mockito.mock(SampleActivityProvider.class);
		final var nodes = new HashSet<INamableBean<String>>();
		nodes.add(service);
		resource.addSubscriptionActivities(activities, null, subscription, plugin, nodes);
		Assertions.assertTrue(activities.isEmpty());
	}

	@Test
	void addSubscriptionActivities() throws Exception {
		final var resource = new LdapPluginResource();
		final var activities = new HashMap<String, Map<String, Activity>>();
		final var subscription = new Subscription();
		final var service = new Node();
		service.setId("J1");
		subscription.setNode(service);
		subscription.setId(1);
		final var plugin = Mockito.mock(SampleActivityProvider.class);
		final var activities1 = new HashMap<String, Activity>();
		final var activity2 = new Activity();
		activities1.put(DEFAULT_USER, activity2);
		Mockito.when(plugin.getActivities(1, null)).thenReturn(activities1);
		final Set<INamableBean<String>> nodes = new HashSet<>();
		resource.addSubscriptionActivities(activities, null, subscription, plugin, nodes);
		Assertions.assertEquals(1, activities.size());
		Assertions.assertTrue(activities.containsKey(DEFAULT_USER));
		Assertions.assertEquals(1, activities.get(DEFAULT_USER).size());
		Assertions.assertTrue(activities.get(DEFAULT_USER).containsKey("J1"));
		Assertions.assertEquals(activity2, activities.get(DEFAULT_USER).get("J1"));
	}

	@Test
	void addSubscriptionActivitiesDuplicateUser() throws Exception {
		final var resource = new LdapPluginResource();
		final var activities = new HashMap<String, Map<String, Activity>>();
		activities.put(DEFAULT_USER, new HashMap<>());
		final var activity1 = new Activity();
		activities.get(DEFAULT_USER).put("J0", activity1);
		final var subscription = new Subscription();
		final var service = new Node();
		service.setId("J1");
		subscription.setNode(service);
		subscription.setId(1);
		final var plugin = Mockito.mock(SampleActivityProvider.class);
		final var activities1 = new HashMap<String, Activity>();
		final var activity2 = new Activity();
		activities1.put(DEFAULT_USER, activity2);
		Mockito.when(plugin.getActivities(1, null)).thenReturn(activities1);
		final Set<INamableBean<String>> nodes = new HashSet<>();
		resource.addSubscriptionActivities(activities, null, subscription, plugin, nodes);
		Assertions.assertEquals(1, activities.size());
		Assertions.assertTrue(activities.containsKey(DEFAULT_USER));
		Assertions.assertEquals(2, activities.get(DEFAULT_USER).size());
		Assertions.assertTrue(activities.get(DEFAULT_USER).containsKey("J1"));
		Assertions.assertEquals(activity1, activities.get(DEFAULT_USER).get("J0"));
		Assertions.assertEquals(activity2, activities.get(DEFAULT_USER).get("J1"));
	}

	@Test
	void findCustomersByName() {
		final var customers = resource.findCustomersByName("ea");
		Assertions.assertEquals(1, customers.size());
		Assertions.assertEquals("sea", customers.iterator().next().getName());
		Assertions.assertEquals("sea", customers.iterator().next().getId());
	}

	@Test
	void acceptNoParameters() {
		Assertions.assertFalse(resource.accept(null, "service:any"));
	}

	@Test
	void acceptNotMatch() {
		final var ldap = new Node();
		ldap.setId("service:id:ldap:test");
		ldap.setRefined(nodeRepository.findOneExpected("service:id:ldap"));
		ldap.setName("LDAP Test");
		nodeRepository.saveAndFlush(ldap);
		final var parameterValue = new ParameterValue();
		parameterValue.setNode(ldap);
		parameterValue.setParameter(parameterRepository.findOneExpected("service:id:uid-pattern"));
		parameterValue.setData("-no-match-");
		em.persist(parameterValue);
		Assertions.assertFalse(resource.accept(new UsernamePasswordAuthenticationToken("some", ""), "service:id:ldap:test"));
	}

	@Test
	void accept() {
		final var ldap = new Node();
		ldap.setId("service:id:ldap:test");
		ldap.setRefined(nodeRepository.findOneExpected("service:id:ldap"));
		ldap.setName("LDAP Test");
		nodeRepository.saveAndFlush(ldap);
		persistParameter(ldap, IdentityResource.PARAMETER_UID_PATTERN, "some-.*-text");
		Assertions.assertTrue(resource.accept(new UsernamePasswordAuthenticationToken("some-awesome-text", ""), "service:id:ldap:test"));
	}

	@Test
	void authenticateSelfSearch() {
		authenticatePrimary();
	}

	@Test
	void authenticateNoSelfSearch() {
		final var repository = (UserLdapRepository) resource.getConfiguration("service:id:ldap:dig").getUserRepository();
		try {
			repository.setSelfSearch(false);
			authenticatePrimary();
		} finally {
			repository.setSelfSearch(true);
		}
	}

	private void authenticatePrimary() {
		final var authentication = new UsernamePasswordAuthenticationToken("fdaugan", "Secret01");
		Assertions.assertSame(authentication, resource.authenticate(authentication, "service:id:ldap:dig", true));
	}

	@Test
	void authenticateByCN() {
		parameterValueRepository.findAllBy("parameter.id", "service:id:ldap:login-attributes").getFirst().setData("cn,uid");
		clearAllCache();
		em.flush();
		final var authentication = new UsernamePasswordAuthenticationToken("Fabrice Daugan", "Secret01");
		Assertions.assertEquals("fdaugan", resource.authenticate(authentication, "service:id:ldap:dig", true).getName());
	}

	@Test
	void authenticateSelfSearchFail() {
		authenticateFail();
	}

	@Test
	void authenticateNoSelfSearchFail() {
		final var repository = (UserLdapRepository) resource.getConfiguration("service:id:ldap:dig").getUserRepository();
		try {
			repository.setSelfSearch(false);
			authenticateFail();
		} finally {
			repository.setSelfSearch(true);
		}
	}

	@Test
	void authenticateNoSelfSearchUnknownUser() {
		final var repository = (UserLdapRepository) resource.getConfiguration("service:id:ldap:dig").getUserRepository();
		try {
			repository.setSelfSearch(false);
			final var authentication = new UsernamePasswordAuthenticationToken("any", "any");
			Assertions.assertThrows(BadCredentialsException.class, () -> resource.authenticate(authentication, "service:id:ldap:dig", true));
		} finally {
			repository.setSelfSearch(true);
		}
	}

	private void authenticateFail() {
		final var authentication = new UsernamePasswordAuthenticationToken("fdaugan", "any");
		Assertions.assertThrows(BadCredentialsException.class, () -> resource.authenticate(authentication, "service:id:ldap:dig", true));
	}

	@Test
	void authenticateSecondaryMock() {
		// Create a new LDAP node plugged to the primary node
		newLdap();

		final var authentication = new UsernamePasswordAuthenticationToken("mmartin", "complexOne");
		final var localAuthentication = resource.authenticate(authentication, "service:id:ldap:secondary", false);
		Assertions.assertEquals("mmartin", localAuthentication.getName());
	}

	@Test
	void toApplicationUserExists() {
		// Create a new LDAP node plugged to the primary node
		final var user = new UserOrg();
		user.setMails(Collections.singletonList("marc.martin@sample.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setName("secondaryLogin");
		user.setCompany("ligoj");
		user.setDepartment("3890");
		user.setLocalId("8234");
		Assertions.assertEquals("mmartin", toApplicationUser(resource, user));

		final var userLdap = userResource.findByIdNoCache("mmartin");
		Assertions.assertEquals("mmartin", userLdap.getName());
		Assertions.assertEquals("Marc", userLdap.getFirstName());
		Assertions.assertEquals("Martin", userLdap.getLastName());
		Assertions.assertEquals("marc.martin@sample.com", userLdap.getMails().getFirst());
	}

	@Test
	void toApplicationUserNew() {
		// Create a new LDAP node plugged to the primary node
		final var user = new UserOrg();
		user.setMails(Collections.singletonList("some@where.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setCompany("ligoj");
		user.setName("secondaryLogin");
		Assertions.assertEquals("flast123", toApplicationUser(resource, user));

		final var userLdap = userResource.findByIdNoCache("flast123");
		Assertions.assertEquals("flast123", userLdap.getName());
		Assertions.assertEquals("First", userLdap.getFirstName());
		Assertions.assertEquals("Last123", userLdap.getLastName());
		Assertions.assertEquals("ligoj", userLdap.getCompany());
		Assertions.assertEquals("some@where.com", userLdap.getMails().getFirst());
		userResource.delete("flast123");
	}

	@Test
	void toApplicationUserNewWithCollision() {
		// Create a new LDAP node plugged to the primary node
		final var user = new UserOrg();
		user.setMails(Collections.singletonList("some@where.com"));
		user.setFirstName("Marc");
		user.setLastName("Martin");
		user.setCompany("ligoj");
		user.setName("secondaryLogin");
		Assertions.assertEquals("mmartin1", toApplicationUser(resource, user));

		final var userLdap = userResource.findByIdNoCache("mmartin1");
		Assertions.assertEquals("mmartin1", userLdap.getName());
		Assertions.assertEquals("Marc", userLdap.getFirstName());
		Assertions.assertEquals("Martin", userLdap.getLastName());
		Assertions.assertEquals("ligoj", userLdap.getCompany());
		Assertions.assertEquals("some@where.com", userLdap.getMails().getFirst());
		userResource.delete("mmartin1");
	}

	@Test
	void toApplicationUserTooManyMail() {
		// Create a new LDAP node plugged to the primary node
		final var user = new UserOrg();
		user.setMails(Collections.singletonList("fabrice.daugan@sample.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setName("secondaryLogin");
		Assertions.assertThrows(NotAuthorizedException.class, () -> toApplicationUser(resource, user));
	}

	@Test
	void authenticateSecondaryNoMail() {
		// Create a new LDAP node plugged to the primary node
		newLdap();

		final var authentication = new UsernamePasswordAuthenticationToken("jdupont", "Secret01");
		Assertions.assertThrows(NotAuthorizedException.class, () -> resource.authenticate(authentication, "service:id:ldap:secondary", false));
	}

	@Test
	void authenticateSecondaryFail() {
		// Create a new LDAP node plugged to the primary node
		newLdap();

		final var authentication = new UsernamePasswordAuthenticationToken("fdaugan", "any");
		Assertions.assertThrows(BadCredentialsException.class, () -> resource.authenticate(authentication, "service:id:ldap:secondary", false));
	}

	@Test
	void newApplicationUserSaveFail() {
		final var resource = new LdapPluginResource();
		final var userResource = Mockito.mock(UserOrgResource.class);
		setUserResource(resource, userResource);
		Mockito.when(userResource.findByIdNoCache("flast123")).thenReturn(null);
		Mockito.doThrow(new UncategorizedLdapException("")).when(userResource).saveOrUpdate(ArgumentMatchers.any(UserOrgEditionVo.class), ArgumentMatchers.eq(true));

		final var user = new UserOrg();
		user.setMails(Collections.singletonList("fabrice.daugan@sample.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setName("secondaryLogin");
		user.setCompany("ligoj");
		Assertions.assertThrows(UncategorizedLdapException.class, () -> resource.newApplicationUser(user));
	}

	@Test
	void newApplicationUserNextLoginFail() {
		final var resource = new LdapPluginResource();
		final var userResource = Mockito.mock(UserOrgResource.class);
		setUserResource(resource, userResource);
		Mockito.doThrow(new RuntimeException()).when(userResource).findByIdNoCache("flast123");

		final var user = new UserOrg();
		user.setMails(Collections.singletonList("fabrice.daugan@sample.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setName("secondaryLogin");
		user.setCompany("ligoj");
		Assertions.assertThrows(RuntimeException.class, () -> resource.newApplicationUser(user));
	}
}

/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.resource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.Activity;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.model.CacheCompany;
import org.ligoj.app.iam.model.CacheGroup;
import org.ligoj.app.iam.model.CacheMembership;
import org.ligoj.app.iam.model.CacheUser;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
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
import org.springframework.security.core.Authentication;
import org.springframework.test.annotation.Rollback;

/**
 * Test class of {@link LdapPluginResource}
 */
@Rollback
@Transactional
public class LdapPluginResourceTest extends AbstractLdapPluginResourceTest {

	@Test
	public void deleteNoMoreGroup() {
		final Subscription subscription = new Subscription();
		subscription.setProject(projectRepository.findByName("gStack"));
		subscription.setNode(nodeRepository.findOneExpected("service:id:ldap:dig"));
		em.persist(subscription);

		// Attach the wrong group
		setGroup(subscription, "any");

		initSpringSecurityContext("fdaugan");
		final Map<String, String> parameters = subscriptionResource.getParametersNoCheck(subscription.getId());
		Assertions.assertFalse(resource.checkSubscriptionStatus(parameters).getStatus().isUp());

		resource.delete(subscription.getId(), true);
		em.flush();
		em.clear();
		Assertions.assertFalse(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
		subscriptionResource.getParametersNoCheck(subscription.getId()).isEmpty();
	}

	/**
	 * The unsubscription without deletion has no effect
	 */
	@Test
	public void delete() {
		initSpringSecurityContext("fdaugan");
		final Map<String, String> parameters = subscriptionResource.getParameters(subscription);
		Assertions.assertTrue(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
		resource.delete(subscription, false);
		em.flush();
		em.clear();
		Assertions.assertTrue(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
	}

	@Test
	public void getVersion() {
		final String version = resource.getVersion(null);
		Assertions.assertEquals("3", version);
	}

	@Test
	public void getLastVersion() {
		final String lastVersion = resource.getLastVersion();
		Assertions.assertEquals("3", lastVersion);
	}

	@Test
	public void validateGroupNotExists() {
		final Map<String, String> parameters = pvResource.getNodeParameters("service:id:ldap:dig");
		parameters.put(IdentityResource.PARAMETER_GROUP, "broken");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.validateGroup(parameters);
		}), IdentityResource.PARAMETER_GROUP, BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	public void validateGroupNotProject() {
		final Map<String, String> parameters = pvResource.getNodeParameters("service:id:ldap:dig");
		parameters.put(IdentityResource.PARAMETER_GROUP, "vigireport");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.validateGroup(parameters);
		}), IdentityResource.PARAMETER_GROUP, "group-type");
	}

	@Test
	public void validateGroup() {
		final Map<String, String> parameters = pvResource.getNodeParameters("service:id:ldap:dig");
		parameters.put(IdentityResource.PARAMETER_GROUP, "ligoj-gstack");

		final INamableBean<String> group = resource.validateGroup(parameters);
		Assertions.assertNotNull(group);
		Assertions.assertEquals("ligoj-gstack", group.getId());
		Assertions.assertEquals("ligoj-gStack", group.getName());
	}

	/**
	 * Create a group in a existing OU. Most Simple case. Group matches exactly to the pkey of the project.
	 */
	@Test
	public void create() {
		resource.delete(create("sea-new-project").getId(), true);
	}

	/**
	 * Create a group with the same name.
	 */
	@Test
	public void createAlreadyExist() {
		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-octopus"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-octopus");
		setOu(subscription2, "sea");

		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			basicCreate(subscription2);
		}), IdentityResource.PARAMETER_GROUP, "already-exist");
	}

	/**
	 * Create a group inside an existing group. Parent group matches exactly to the pkey of the project.
	 */
	@Test
	public void createSubGroup() {
		// Create the parent group
		final Project newProject = create("sea-parent").getProject();
		createSubGroup(newProject, "sea-parent", "sea-parent-client");
	}

	/**
	 * Create a group inside an existing group without reusing the name of the parent group.
	 */
	@Test
	public void createNotCompliantGroupForParent() {
		// Create the parent group
		final Project newProject = create("sea-parent2").getProject();
		createSubGroup(newProject, "sea-parent2", "sea-parent2-client");

		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			createSubGroup(newProject, "sea-parent2-client", "sea-parent2-dev");
		}), IdentityResource.PARAMETER_GROUP, "pattern");
	}

	/**
	 * Create a group for an existing project, but without reusing the pkey of this project.
	 */
	@Test
	public void createNotCompliantGroupForProject() {
		// Preconditions
		Assertions.assertNotNull(getGroup().findById("sea-octopus"));
		Assertions.assertNull(getGroup().findById("sea-octopusZZ"));
		Assertions.assertNotNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "sea"));

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
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			basicCreate(subscription2);
		}), IdentityResource.PARAMETER_GROUP, "pattern");
	}

	/**
	 * Create a group for an existing project, reusing the pkey of this project and without suffix.
	 */
	@Test
	public void createNotCompliantGroupForProject2() {

		// Preconditions
		Assertions.assertNotNull(getGroup().findById("sea-octopus"));
		Assertions.assertNull(getGroup().findById("sea-octopus-"));
		Assertions.assertNotNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "sea"));

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
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			basicCreate(subscription2);
		}), IdentityResource.PARAMETER_GROUP, "pattern");
	}

	/**
	 * Create a group for an existing project, perfect match with the pkey, but without reusing the OU of this project.
	 */
	@Test
	public void createNotCompliantGroupForOu() {
		// Preconditions
		Assertions.assertNull(getGroup().findById("sea-invalid-ou"));
		Assertions.assertNotNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "sea"));

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-invalid-ou"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-invalid-ou");
		setOu(subscription2, "ligoj");

		// Invoke link for an already linked entity, since for now
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			basicCreate(subscription2);
		}), IdentityResource.PARAMETER_GROUP, "pattern");
	}

	/**
	 * Create a group inside an existing group without reusing the name of the parent.
	 */
	@Test
	public void createNotExistingParentGroup() {
		// Preconditions
		Assertions.assertNotNull(getGroup().findById("sea-octopus"));
		Assertions.assertNull(getGroup().findById("sea-octopus-client"));
		Assertions.assertNotNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "sea"));

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
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			basicCreate(subscription2);
		}), IdentityResource.PARAMETER_PARENT_GROUP, "unknown-id");
	}

	/**
	 * Create a group inside a new organizational unit. Not an error, lazy creation. Exact match for group and pkey.
	 */
	@Test
	public void createOuNotExists() {

		// Preconditions
		Assertions.assertNull(getGroup().findById("some-new-project"));
		Assertions.assertNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "some"));
		Assertions.assertFalse(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").contains("some"));

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
		Assertions.assertNotNull(groupLdap);
		Assertions.assertEquals("some-new-project", groupLdap.getName());
		Assertions.assertEquals("cn=some-new-project,ou=some,ou=project,dc=sample,dc=com", groupLdap.getDn());
		Assertions.assertEquals("some-new-project", groupLdap.getId());
		Assertions.assertNotNull(projectCustomerLdapRepository.findById("ou=project,dc=sample,dc=com", "some"));
		Assertions.assertTrue(projectCustomerLdapRepository.findAll("ou=project,dc=sample,dc=com").contains("some"));

		resource.delete(subscription2.getId(), true);
	}

	@Test
	public void link() {

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
		group.setId("ligoj-gstack");
		group.setName("ligoj-gstack");
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

	@Test
	public void linkNotVisibleProject() {

		// Invoke link for an already created entity, since for now
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			resource.link(this.subscription);
		});
	}

	/**
	 * Visible project, but not visible target group
	 */
	@Test
	public void linkNotVisibleGroup() {
		// Attach the wrong group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		setGroup(subscription, "sea-octopus");

		// Invoke link for an already created entity, since for now
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.link(this.subscription);
		}), IdentityResource.PARAMETER_GROUP, BusinessException.KEY_UNKNOWN_ID);
	}

	/**
	 * Visible project, but target group does not exist
	 */
	@Test
	public void linkNotExistingGroup() {
		// Attach the wrong group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		setGroup(subscription, "any-g");

		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.link(this.subscription);
		}), IdentityResource.PARAMETER_GROUP, BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	public void checkStatus() {
		Assertions.assertTrue(
				resource.checkStatus("service:id:ldap:dig", subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	public void checkSubscriptionStatus() {
		Assertions.assertTrue(resource.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription))
				.getStatus().isUp());
	}

	@Test
	public void findGroupsByNameNoRight() {
		initSpringSecurityContext("any");
		final List<INamableBean<String>> jobs = resource.findGroupsByName("StAck");
		Assertions.assertEquals(0, jobs.size());
	}

	@Test
	public void findGroupsByName() {
		final List<INamableBean<String>> jobs = resource.findGroupsByName("StAck");
		Assertions.assertTrue(jobs.size() >= 1);
		Assertions.assertEquals("ligoj-gStack", jobs.get(0).getName());
		Assertions.assertEquals("ligoj-gstack", jobs.get(0).getId());
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
		Mockito.when(activitiesProvider.getActivities(ArgumentMatchers.anyInt(), ArgumentMatchers.any()))
				.thenReturn(activities);

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

		final List<String> csvLines = IOUtils.readLines(new ByteArrayInputStream(output.toByteArray()),
				StandardCharsets.UTF_8);
		Assertions.assertEquals(2, csvLines.size());
		Assertions.assertEquals("user;firstName;lastName;mail;JIRA 6", csvLines.get(0));
		Assertions.assertEquals("alongchu;Arnaud;Longchu;arnaud.longchu@sample.com;2015/01/01 00:00:00",
				csvLines.get(1));
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
		Mockito.when(activitiesProvider.getActivities(ArgumentMatchers.anyInt(), ArgumentMatchers.any()))
				.thenReturn(activities);

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

		final List<String> csvLines = IOUtils.readLines(new ByteArrayInputStream(output.toByteArray()),
				StandardCharsets.UTF_8);
		Assertions.assertEquals(2, csvLines.size());
		Assertions.assertEquals("user;firstName;lastName;mail;JIRA 6", csvLines.get(0));
		Assertions.assertEquals("alongchu;Arnaud;Longchu;arnaud.longchu@sample.com;2015/01/01 00:00:00",
				csvLines.get(1));
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
		Mockito.when(activitiesProvider.getActivities(ArgumentMatchers.anyInt(), ArgumentMatchers.any()))
				.thenReturn(activities);

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

		// Get the subscription using a subscribed broken group
		final int otherSubscription = em
				.createQuery(
						"SELECT s.id FROM Subscription s WHERE s.project.name = ?1 AND s.node.id LIKE CONCAT(?2,'%')",
						Integer.class)
				.setParameter(1, "gStack").setParameter(2, IdentityResource.SERVICE_KEY).setMaxResults(2)
				.getResultList().get(1);
		((StreamingOutput) resource.getGroupActivitiesCsv(otherSubscription, "file1").getEntity()).write(output);

		final List<String> csvLines = IOUtils.readLines(new ByteArrayInputStream(output.toByteArray()),
				StandardCharsets.UTF_8);
		Assertions.assertEquals("user;firstName;lastName;mail;JIRA 6", csvLines.get(0));
		Assertions.assertEquals(1, csvLines.size());
	}

	@Test
	public void addSubscriptionActivitiesNotProvider() throws Exception {
		final Map<String, Map<String, Activity>> activities = new HashMap<>();
		new LdapPluginResource().addSubscriptionActivities(activities, null, null, null, null);
		Assertions.assertTrue(activities.isEmpty());
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
		Assertions.assertTrue(activities.isEmpty());
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
		Assertions.assertEquals(1, activities.size());
		Assertions.assertTrue(activities.containsKey(DEFAULT_USER));
		Assertions.assertEquals(1, activities.get(DEFAULT_USER).size());
		Assertions.assertTrue(activities.get(DEFAULT_USER).containsKey("J1"));
		Assertions.assertEquals(activity2, activities.get(DEFAULT_USER).get("J1"));
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
		Assertions.assertEquals(1, activities.size());
		Assertions.assertTrue(activities.containsKey(DEFAULT_USER));
		Assertions.assertEquals(2, activities.get(DEFAULT_USER).size());
		Assertions.assertTrue(activities.get(DEFAULT_USER).containsKey("J1"));
		Assertions.assertEquals(activity1, activities.get(DEFAULT_USER).get("J0"));
		Assertions.assertEquals(activity2, activities.get(DEFAULT_USER).get("J1"));
	}

	@Test
	public void findCustomersByName() {
		final Collection<INamableBean<String>> customers = resource.findCustomersByName("ea");
		Assertions.assertEquals(1, customers.size());
		Assertions.assertEquals("sea", customers.iterator().next().getName());
		Assertions.assertEquals("sea", customers.iterator().next().getId());
	}

	@Test
	public void acceptNoParameters() {
		Assertions.assertFalse(resource.accept(null, "service:any"));
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
		Assertions.assertFalse(
				resource.accept(new UsernamePasswordAuthenticationToken("some", ""), "service:id:ldap:test"));
	}

	@Test
	public void accept() {
		final Node ldap = new Node();
		ldap.setId("service:id:ldap:test");
		ldap.setRefined(nodeRepository.findOneExpected("service:id:ldap"));
		ldap.setName("LDAP Test");
		nodeRepository.saveAndFlush(ldap);
		persistParameter(ldap, IdentityResource.PARAMETER_UID_PATTERN, "some-.*-text");
		Assertions.assertTrue(resource.accept(new UsernamePasswordAuthenticationToken("some-awesome-text", ""),
				"service:id:ldap:test"));
	}

	@Test
	public void authenticatePrimary() {
		final Authentication authentication = new UsernamePasswordAuthenticationToken("fdaugan", "Azerty01");
		Assertions.assertSame(authentication, resource.authenticate(authentication, "service:id:ldap:dig", true));
	}

	@Test
	public void authenticateFail() {
		final Authentication authentication = new UsernamePasswordAuthenticationToken("fdaugan", "any");
		Assertions.assertThrows(BadCredentialsException.class, () -> {
			resource.authenticate(authentication, "service:id:ldap:dig", true);
		});
	}

	@Test
	public void authenticateSecondaryMock() {
		// Create a new LDAP node pluged to the primary node
		newLdap();

		final Authentication authentication = new UsernamePasswordAuthenticationToken("mmartin", "complexOne");
		final Authentication localAuthentication = resource.authenticate(authentication, "service:id:ldap:secondary",
				false);
		Assertions.assertEquals("mmartin", localAuthentication.getName());
	}

	@Test
	public void toApplicationUserExists() {
		// Create a new LDAP node plugged to the primary node
		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("marc.martin@sample.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setName("secondarylogin");
		user.setCompany("ligoj");
		user.setDepartment("3890");
		user.setLocalId("8234");
		Assertions.assertEquals("mmartin", toApplicationUser(resource, user));

		final UserOrg userLdap = userResource.findByIdNoCache("mmartin");
		Assertions.assertEquals("mmartin", userLdap.getName());
		Assertions.assertEquals("Marc", userLdap.getFirstName());
		Assertions.assertEquals("Martin", userLdap.getLastName());
		Assertions.assertEquals("marc.martin@sample.com", userLdap.getMails().get(0));
	}

	@Test
	public void toApplicationUserNew() {
		// Create a new LDAP node plugged to the primary node
		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("some@where.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setCompany("ligoj");
		user.setName("secondarylogin");
		Assertions.assertEquals("flast123", toApplicationUser(resource, user));

		final UserOrg userLdap = userResource.findByIdNoCache("flast123");
		Assertions.assertEquals("flast123", userLdap.getName());
		Assertions.assertEquals("First", userLdap.getFirstName());
		Assertions.assertEquals("Last123", userLdap.getLastName());
		Assertions.assertEquals("ligoj", userLdap.getCompany());
		Assertions.assertEquals("some@where.com", userLdap.getMails().get(0));
		userResource.delete("flast123");
	}

	@Test
	public void toApplicationUserNewWithCollision() {
		// Create a new LDAP node plugged to the primary node
		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("some@where.com"));
		user.setFirstName("Marc");
		user.setLastName("Martin");
		user.setCompany("ligoj");
		user.setName("secondarylogin");
		Assertions.assertEquals("mmartin1", toApplicationUser(resource, user));

		final UserOrg userLdap = userResource.findByIdNoCache("mmartin1");
		Assertions.assertEquals("mmartin1", userLdap.getName());
		Assertions.assertEquals("Marc", userLdap.getFirstName());
		Assertions.assertEquals("Martin", userLdap.getLastName());
		Assertions.assertEquals("ligoj", userLdap.getCompany());
		Assertions.assertEquals("some@where.com", userLdap.getMails().get(0));
		userResource.delete("mmartin1");
	}

	@Test
	public void toApplicationUserTooManyMail() {
		// Create a new LDAP node pluged to the primary node
		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("fabrice.daugan@sample.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setName("secondarylogin");
		Assertions.assertThrows(NotAuthorizedException.class, () -> {
			toApplicationUser(resource, user);
		});
	}

	@Test
	public void authenticateSecondaryNoMail() {
		// Create a new LDAP node pluged to the primary node

		newLdap();

		final Authentication authentication = new UsernamePasswordAuthenticationToken("jdupont", "Azerty01");
		Assertions.assertThrows(NotAuthorizedException.class, () -> {
			resource.authenticate(authentication, "service:id:ldap:secondary", false);
		});
	}

	@Test
	public void authenticateSecondaryFail() {
		// Create a new LDAP node pluged to the primary node
		newLdap();

		final Authentication authentication = new UsernamePasswordAuthenticationToken("fdaugan", "any");
		Assertions.assertThrows(BadCredentialsException.class, () -> {
			resource.authenticate(authentication, "service:id:ldap:secondary", false);
		});
	}

	@Test
	public void newApplicationUserSaveFail() {
		final LdapPluginResource resource = new LdapPluginResource();
		final UserOrgResource userResource = Mockito.mock(UserOrgResource.class);
		setUserResource(resource, userResource);
		Mockito.when(userResource.findByIdNoCache("flast123")).thenReturn(null);
		Mockito.doThrow(new UncategorizedLdapException("")).when(userResource)
				.saveOrUpdate(ArgumentMatchers.any(UserOrgEditionVo.class));

		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("fabrice.daugan@sample.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setName("secondarylogin");
		user.setCompany("ligoj");
		Assertions.assertThrows(UncategorizedLdapException.class, () -> {
			resource.newApplicationUser(user);
		});
	}

	@Test
	public void newApplicationUserNextLoginFail() {
		final LdapPluginResource resource = new LdapPluginResource();
		final UserOrgResource userResource = Mockito.mock(UserOrgResource.class);
		setUserResource(resource, userResource);
		Mockito.doThrow(new RuntimeException()).when(userResource).findByIdNoCache("flast123");

		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("fabrice.daugan@sample.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setName("secondarylogin");
		user.setCompany("ligoj");
		Assertions.assertThrows(RuntimeException.class, () -> {
			resource.newApplicationUser(user);
		});
	}
}

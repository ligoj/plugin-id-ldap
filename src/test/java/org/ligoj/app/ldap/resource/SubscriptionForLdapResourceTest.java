package org.ligoj.app.ldap.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.persistence.EntityNotFoundException;
import javax.sql.DataSource;
import javax.transaction.Transactional;
import javax.ws.rs.ForbiddenException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ligoj.app.api.GroupOrg;
import org.ligoj.app.api.SubscriptionMode;
import org.ligoj.app.dao.ParameterValueRepository;
import org.ligoj.app.dao.ProjectRepository;
import org.ligoj.app.dao.SubscriptionRepository;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.ldap.dao.LdapCacheRepository;
import org.ligoj.app.model.DelegateNode;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.id.ldap.resource.AbstractLdapTest;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.app.plugin.id.resource.IdentityResource;
import org.ligoj.app.resource.node.ParameterValueEditionVo;
import org.ligoj.app.resource.node.sample.BugTrackerResource;
import org.ligoj.app.resource.node.sample.JiraPluginResource;
import org.ligoj.app.resource.subscription.SubscriptionEditionVo;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import net.sf.ehcache.CacheManager;

/**
 * Test class of {@link SubscriptionResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class SubscriptionForLdapResourceTest extends AbstractLdapTest {

	@Autowired
	protected ProjectRepository projectRepository;

	protected static DataSource datasource;

	protected int subscription;

	@Autowired
	private LdapCacheRepository cache;

	@Autowired
	private SubscriptionResource resource;

	@Autowired
	private SubscriptionRepository repository;

	@Autowired
	private ParameterValueRepository parameterValueRepository;

	/**
	 * Return the subscription identifier of MDA. Assumes there is only one subscription for a service.
	 */
	protected int getSubscription(final String project) {
		return getSubscription(project, BugTrackerResource.SERVICE_KEY);
	}

	/**
	 * Return the subscription identifier of MDA. Assumes there is only one subscription for a service.
	 */
	protected int getSubscription(final String project, final String service) {
		return em.createQuery("SELECT s.id FROM Subscription s WHERE s.project.name = ?1 AND s.node.id LIKE CONCAT(?2,'%')", Integer.class)
				.setParameter(1, project).setParameter(2, service).getSingleResult();
	}

	@Before
	public void prepareSubscription() throws IOException {
		this.subscription = getSubscription("MDA");
		persistEntities("csv/app-test", new Class[] { DelegateOrg.class, ContainerScope.class, DelegateNode.class }, StandardCharsets.UTF_8.name());
		initSpringSecurityContext("fdaugan");
	}

	@Test
	public void delete() throws Exception {
		final Subscription one = repository.findOne(subscription);
		final int project = one.getProject().getId();
		Assert.assertEquals(1, repository.findAllByProject(project).size());
		em.clear();
		resource.delete(subscription);
		em.flush();
		em.clear();

		Assert.assertTrue(repository.findAllByProject(project).isEmpty());
		Assert.assertNull(repository.findOne(subscription));
	}

	@Test(expected = EntityNotFoundException.class)
	public void deleteNotVisibleProject() throws Exception {
		final Subscription one = repository.findOne(subscription);
		final int project = one.getProject().getId();
		Assert.assertEquals(1, repository.findAllByProject(project).size());
		em.clear();
		initSpringSecurityContext("any");
		resource.delete(subscription);
	}

	@Test(expected = ForbiddenException.class)
	public void deleteNotManagedProject() throws Exception {
		final Subscription one = repository.findOne(getSubscription("gStack"));
		final int project = one.getProject().getId();
		Assert.assertTrue(repository.findAllByProject(project).size() >= 6);

		// Ensure LDAP cache is loaded
		CacheManager.getInstance().getCache("ldap").removeAll();
		cache.getLdapData();
		em.flush();
		em.clear();
		initSpringSecurityContext("alongchu");
		resource.delete(one.getId());
	}

	@Test(expected = EntityNotFoundException.class)
	public void createNotVisibleProject() throws Exception {

		// Test a creation by another user than the team leader and a manager
		initSpringSecurityContext("any");
		create();
	}

	@Test
	public void createByAnotherManager() throws Exception {

		// Test a creation by another user than the team leader
		initSpringSecurityContext(DEFAULT_USER);
		create();
	}

	@Test
	public void create() throws Exception {
		em.createQuery("DELETE Parameter WHERE id LIKE ?1").setParameter(1, "c_%").executeUpdate();

		final SubscriptionEditionVo vo = new SubscriptionEditionVo();
		final List<ParameterValueEditionVo> parameters = new ArrayList<>();
		final ParameterValueEditionVo parameterValueEditionVo = new ParameterValueEditionVo();
		parameterValueEditionVo.setParameter(JiraPluginResource.PARAMETER_PROJECT);
		parameterValueEditionVo.setInteger(10074);
		parameters.add(parameterValueEditionVo);
		final ParameterValueEditionVo parameterValueEditionVo2 = new ParameterValueEditionVo();
		parameterValueEditionVo2.setParameter(JiraPluginResource.PARAMETER_PKEY);
		parameterValueEditionVo2.setText("MDA");
		parameters.add(parameterValueEditionVo2);

		vo.setParameters(parameters);
		vo.setNode("service:bt:jira:4");
		vo.setProject(em.createQuery("SELECT id FROM Project WHERE name='gStack'", Integer.class).getSingleResult());

		// Ensure LDAP cache is loaded
		CacheManager.getInstance().getCache("ldap").removeAll();
		cache.getLdapData();
		em.flush();
		em.clear();

		final int subscription = resource.create(vo);
		em.flush();
		em.clear();

		Assert.assertEquals("10074", parameterValueRepository.getSubscriptionParameterValue(subscription, JiraPluginResource.PARAMETER_PROJECT));
		Assert.assertEquals("MDA", parameterValueRepository.getSubscriptionParameterValue(subscription, JiraPluginResource.PARAMETER_PKEY));

		// Rollback the creation in LDAP
		resource.delete(subscription, true);
	}

	@Test
	public void createCreateMode() throws Exception {
		// Prepare data
		em.createQuery("DELETE Parameter WHERE id LIKE ?1").setParameter(1, "c_%").executeUpdate();
		final String dn = "cn=gfi-gstack-client,cn=gfi-gstack,ou=gfi,ou=project,dc=sample,dc=com";
		try {
			getGroup().delete(new GroupOrg(dn, "gfi-gstack-client", new HashSet<>()));
		} catch (final Exception e) {
			// Ignore no group
		}

		final SubscriptionEditionVo vo = new SubscriptionEditionVo();
		final List<ParameterValueEditionVo> parameters = new ArrayList<>();
		final ParameterValueEditionVo parameterValueEditionVo = new ParameterValueEditionVo();
		parameterValueEditionVo.setParameter(IdentityResource.PARAMETER_OU);
		parameterValueEditionVo.setText("gfi");
		parameters.add(parameterValueEditionVo);
		final ParameterValueEditionVo parameterValueEditionVo2 = new ParameterValueEditionVo();
		parameterValueEditionVo2.setParameter(IdentityResource.PARAMETER_PARENT_GROUP);
		parameterValueEditionVo2.setText("gfi-gstack");
		parameters.add(parameterValueEditionVo2);
		final ParameterValueEditionVo parameterValueEditionVo3 = new ParameterValueEditionVo();
		parameterValueEditionVo3.setParameter(IdentityResource.PARAMETER_GROUP);
		parameterValueEditionVo3.setText("gfi-gstack-client");
		parameters.add(parameterValueEditionVo3);

		vo.setMode(SubscriptionMode.CREATE);
		vo.setParameters(parameters);
		vo.setNode("service:id:ldap:dig");
		vo.setProject(em.createQuery("SELECT id FROM Project WHERE name='gStack'", Integer.class).getSingleResult());

		// Ensure LDAP cache is loaded
		CacheManager.getInstance().getCache("ldap").removeAll();
		cache.getLdapData();
		em.flush();
		em.clear();

		initSpringSecurityContext(DEFAULT_USER);
		final int subscription = resource.create(vo);
		em.flush();
		em.clear();

		Assert.assertEquals("gfi-gstack-client",
				parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_GROUP));
		Assert.assertEquals("gfi", parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_OU));
		Assert.assertEquals("gfi-gstack",
				parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_PARENT_GROUP));

		// Check the creation in LDAP
		final GroupOrg group = getGroup().findById("gfi-gstack-client");
		Assert.assertNotNull(group);
		Assert.assertEquals("gfi-gstack-client", group.getName());
		Assert.assertEquals("gfi-gstack-client", group.getId());
		Assert.assertEquals(dn, group.getDn());

		// Rollback the creation in LDAP
		resource.delete(subscription, true);
		Assert.assertEquals(1, getMembers().length);
	}

	private String[] getMembers() {
		final DirContextAdapter groupContext = getTemplate().search("cn=gfi-gstack,ou=gfi,ou=project,dc=sample,dc=com",
				new EqualsFilter("cn", "gfi-gStack").encode(), (Object ctx) -> (DirContextAdapter) ctx).get(0);
		final String[] members = groupContext.getStringAttributes("uniqueMember");
		return members;
	}

	/**
	 * Create mode, blank optional parameter
	 */
	@Test
	public void createCreateModeBlank() throws Exception {
		// Prepare data
		em.createQuery("DELETE Parameter WHERE id LIKE ?1").setParameter(1, "c_%").executeUpdate();
		final String dn = "cn=gfi-gstack-client2,ou=gfi,ou=project,dc=sample,dc=com";
		try {
			getGroup().delete(new GroupOrg(dn, "gfi-gstack-client", new HashSet<>()));
		} catch (final Exception e) {
			// Ignore no group
		}

		final SubscriptionEditionVo vo = new SubscriptionEditionVo();
		final List<ParameterValueEditionVo> parameters = new ArrayList<>();
		final ParameterValueEditionVo parameterValueEditionVo = new ParameterValueEditionVo();
		parameterValueEditionVo.setParameter(IdentityResource.PARAMETER_OU);
		parameterValueEditionVo.setText("gfi");
		parameters.add(parameterValueEditionVo);
		final ParameterValueEditionVo parameterValueEditionVo2 = new ParameterValueEditionVo();
		parameterValueEditionVo2.setParameter(IdentityResource.PARAMETER_PARENT_GROUP);
		parameterValueEditionVo2.setText("");
		parameters.add(parameterValueEditionVo2);
		final ParameterValueEditionVo parameterValueEditionVo3 = new ParameterValueEditionVo();
		parameterValueEditionVo3.setParameter(IdentityResource.PARAMETER_GROUP);
		parameterValueEditionVo3.setText("gfi-gstack-client2");
		parameters.add(parameterValueEditionVo3);

		vo.setMode(SubscriptionMode.CREATE);
		vo.setParameters(parameters);
		vo.setNode("service:id:ldap:dig");
		vo.setProject(em.createQuery("SELECT id FROM Project WHERE name='gStack'", Integer.class).getSingleResult());

		// Ensure LDAP cache is loaded
		CacheManager.getInstance().getCache("ldap").removeAll();
		cache.getLdapData();
		em.flush();
		em.clear();

		initSpringSecurityContext(DEFAULT_USER);
		final int subscription = resource.create(vo);
		em.flush();
		em.clear();

		Assert.assertEquals("gfi-gstack-client2",
				parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_GROUP));
		Assert.assertEquals("gfi", parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_OU));
		Assert.assertNull(parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_PARENT_GROUP));

		// Check the creation in LDAP
		final GroupOrg group = getGroup().findById("gfi-gstack-client2");
		Assert.assertNotNull(group);
		Assert.assertEquals("gfi-gstack-client2", group.getId());
		Assert.assertEquals("gfi-gstack-client2", group.getName());
		Assert.assertEquals(dn, group.getDn());
	}
}

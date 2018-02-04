package org.ligoj.app.plugin.id.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.transaction.Transactional;
import javax.ws.rs.ForbiddenException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.api.SubscriptionMode;
import org.ligoj.app.dao.ParameterValueRepository;
import org.ligoj.app.dao.SubscriptionRepository;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.model.DelegateNode;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.id.ldap.dao.LdapCacheRepository;
import org.ligoj.app.plugin.id.ldap.resource.AbstractLdapTest;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.app.resource.node.ParameterValueCreateVo;
import org.ligoj.app.resource.subscription.SubscriptionEditionVo;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.sf.ehcache.CacheManager;

/**
 * Test class of {@link SubscriptionResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class SubscriptionForLdapResourceTest extends AbstractLdapTest {

	@Autowired
	private LdapCacheRepository cache;

	@Autowired
	private SubscriptionResource resource;

	@Autowired
	private SubscriptionRepository repository;

	@Autowired
	private ParameterValueRepository parameterValueRepository;

	@BeforeEach
	public void prepareSubscription() throws IOException {
		persistEntities("csv", new Class[] { DelegateOrg.class, ContainerScope.class, DelegateNode.class }, StandardCharsets.UTF_8.name());
		initSpringSecurityContext("fdaugan");
	}

	@Test
	public void deleteNotManagedProject() throws Exception {
		final Subscription one = repository.findOne(getSubscription("gStack", IdentityResource.SERVICE_KEY));
		final int project = one.getProject().getId();
		Assertions.assertEquals(3, repository.findAllByProject(project).size());

		// Ensure LDAP cache is loaded
		CacheManager.getInstance().getCache("ldap").removeAll();
		cache.getLdapData();
		em.flush();
		em.clear();
		initSpringSecurityContext("alongchu");
		Assertions.assertThrows(ForbiddenException.class, () -> {
			resource.delete(one.getId());
		});
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
		final List<ParameterValueCreateVo> parameters = new ArrayList<>();
		final ParameterValueCreateVo parameterValueEditionVo = new ParameterValueCreateVo();
		parameterValueEditionVo.setParameter(IdentityResource.PARAMETER_OU);
		parameterValueEditionVo.setText("gfi");
		parameters.add(parameterValueEditionVo);
		final ParameterValueCreateVo parameterValueEditionVo2 = new ParameterValueCreateVo();
		parameterValueEditionVo2.setParameter(IdentityResource.PARAMETER_PARENT_GROUP);
		parameterValueEditionVo2.setText("gfi-gstack");
		parameters.add(parameterValueEditionVo2);
		final ParameterValueCreateVo parameterValueEditionVo3 = new ParameterValueCreateVo();
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

		Assertions.assertEquals("gfi-gstack-client",
				parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_GROUP));
		Assertions.assertEquals("gfi", parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_OU));
		Assertions.assertEquals("gfi-gstack",
				parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_PARENT_GROUP));

		// Check the creation in LDAP
		final GroupOrg group = getGroup().findById("gfi-gstack-client");
		Assertions.assertNotNull(group);
		Assertions.assertEquals("gfi-gstack-client", group.getName());
		Assertions.assertEquals("gfi-gstack-client", group.getId());
		Assertions.assertEquals(dn, group.getDn());

		// Rollback the creation in LDAP
		resource.delete(subscription, true);
		Assertions.assertEquals(1, getMembers().length);
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
		final List<ParameterValueCreateVo> parameters = new ArrayList<>();
		final ParameterValueCreateVo parameterValueEditionVo = new ParameterValueCreateVo();
		parameterValueEditionVo.setParameter(IdentityResource.PARAMETER_OU);
		parameterValueEditionVo.setText("gfi");
		parameters.add(parameterValueEditionVo);
		final ParameterValueCreateVo parameterValueEditionVo2 = new ParameterValueCreateVo();
		parameterValueEditionVo2.setParameter(IdentityResource.PARAMETER_PARENT_GROUP);
		parameterValueEditionVo2.setText("");
		parameters.add(parameterValueEditionVo2);
		final ParameterValueCreateVo parameterValueEditionVo3 = new ParameterValueCreateVo();
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

		Assertions.assertEquals("gfi-gstack-client2",
				parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_GROUP));
		Assertions.assertEquals("gfi", parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_OU));
		Assertions
				.assertNull(parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_PARENT_GROUP));

		// Check the creation in LDAP
		final GroupOrg group = getGroup().findById("gfi-gstack-client2");
		Assertions.assertNotNull(group);
		Assertions.assertEquals("gfi-gstack-client2", group.getId());
		Assertions.assertEquals("gfi-gstack-client2", group.getName());
		Assertions.assertEquals(dn, group.getDn());
	}
}

/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.resource;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import lombok.extern.slf4j.Slf4j;
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
import org.ligoj.app.plugin.id.ldap.dao.CacheLdapRepository;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Test class of {@link SubscriptionResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
@Slf4j
class SubscriptionForLdapResourceTest extends AbstractLdapTest {

	@Autowired
	private CacheLdapRepository cache;

	@Autowired
	private SubscriptionResource resource;

	@Autowired
	private SubscriptionRepository repository;

	@Autowired
	private ParameterValueRepository parameterValueRepository;

	@BeforeEach
	void prepareSubscription() throws IOException {
		persistEntities("csv", new Class<?>[] { DelegateOrg.class, ContainerScope.class, DelegateNode.class },
				StandardCharsets.UTF_8);
		initSpringSecurityContext("fdaugan");
	}

	@Test
	void deleteNotManagedProject() {
		final var one = repository.findOne(getSubscription("Jupiter", IdentityResource.SERVICE_KEY));
		final var project = one.getProject().getId();
		Assertions.assertEquals(3, repository.findAllByProject(project).size());

		// Ensure LDAP cache is loaded
		cacheManager.getCache("id-ldap-data").clear();
		cache.getData();
		em.flush();
		em.clear();
		initSpringSecurityContext("admin-test");
		Assertions.assertThrows(ForbiddenException.class, () -> resource.delete(one.getId()));
	}

	@Test
	void createCreateMode() throws Exception {
		// Prepare data
		em.createQuery("DELETE Parameter WHERE id LIKE ?1").setParameter(1, "c_%").executeUpdate();
		final var dn = "cn=ligoj-jupiter-client,cn=ligoj-jupiter,ou=ligoj,ou=project,dc=sample,dc=com";
		cleanSubGroup(dn);

		final var vo = new SubscriptionEditionVo();
		final var parameters = new ArrayList<ParameterValueCreateVo>();
		final var parameterValueEditionVo = new ParameterValueCreateVo();
		parameterValueEditionVo.setParameter(IdentityResource.PARAMETER_OU);
		parameterValueEditionVo.setText("ligoj");
		parameters.add(parameterValueEditionVo);
		final var parameterValueEditionVo2 = new ParameterValueCreateVo();
		parameterValueEditionVo2.setParameter(IdentityResource.PARAMETER_PARENT_GROUP);
		parameterValueEditionVo2.setText("ligoj-jupiter");
		parameters.add(parameterValueEditionVo2);
		final var parameterValueEditionVo3 = new ParameterValueCreateVo();
		parameterValueEditionVo3.setParameter(IdentityResource.PARAMETER_GROUP);
		parameterValueEditionVo3.setText("ligoj-jupiter-client");
		parameters.add(parameterValueEditionVo3);

		vo.setMode(SubscriptionMode.CREATE);
		vo.setParameters(parameters);
		vo.setNode("service:id:ldap:dig");
		vo.setProject(em.createQuery("SELECT id FROM Project WHERE name='Jupiter'", Integer.class).getSingleResult());

		// Ensure LDAP cache is loaded
		cacheManager.getCache("id-ldap-data").clear();
		cache.getData();
		em.flush();
		em.clear();

		initSpringSecurityContext(DEFAULT_USER);
		final int subscription = resource.create(vo);
		em.flush();
		em.clear();

		Assertions.assertEquals("ligoj-jupiter-client",
				parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_GROUP));
		Assertions.assertEquals("ligoj",
				parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_OU));
		Assertions.assertEquals("ligoj-jupiter", parameterValueRepository.getSubscriptionParameterValue(subscription,
				IdentityResource.PARAMETER_PARENT_GROUP));

		// Check the creation in LDAP
		final var group = getGroup().findById("ligoj-jupiter-client");
		Assertions.assertNotNull(group);
		Assertions.assertEquals("ligoj-jupiter-client", group.getName());
		Assertions.assertEquals("ligoj-jupiter-client", group.getId());
		Assertions.assertEquals(dn, group.getDn());

		// Rollback the creation in LDAP
		resource.delete(subscription, true);
		Assertions.assertEquals(1, getMembers().length);
	}

	private void cleanSubGroup(final String dn) {
		try {
			getGroup().delete(new GroupOrg(dn, "ligoj-jupiter-client", new HashSet<>()));
		} catch (final Exception e) {
			// Ignore no group
			log.debug("No group to delete", e);
		}
	}

	private String[] getMembers() {
		final var groupContext = getTemplate().search("cn=ligoj-jupiter,ou=ligoj,ou=project,dc=sample,dc=com",
				new EqualsFilter("cn", "ligoj-jupiter").encode(), (Object ctx) -> (DirContextAdapter) ctx).get(0);
		return groupContext.getStringAttributes("uniqueMember");
	}

	/**
	 * Create mode, blank optional parameter
	 */
	@Test
	void createCreateModeBlank() throws Exception {
		// Prepare data
		em.createQuery("DELETE Parameter WHERE id LIKE ?1").setParameter(1, "c_%").executeUpdate();
		final var dn = "cn=ligoj-jupiter-client2,ou=ligoj,ou=project,dc=sample,dc=com";
		cleanSubGroup(dn);

		final var vo = new SubscriptionEditionVo();
		final var parameters = new ArrayList<ParameterValueCreateVo>();
		final var parameterValueEditionVo = new ParameterValueCreateVo();
		parameterValueEditionVo.setParameter(IdentityResource.PARAMETER_OU);
		parameterValueEditionVo.setText("ligoj");
		parameters.add(parameterValueEditionVo);
		final var parameterValueEditionVo2 = new ParameterValueCreateVo();
		parameterValueEditionVo2.setParameter(IdentityResource.PARAMETER_PARENT_GROUP);
		parameterValueEditionVo2.setText("");
		parameters.add(parameterValueEditionVo2);
		final var parameterValueEditionVo3 = new ParameterValueCreateVo();
		parameterValueEditionVo3.setParameter(IdentityResource.PARAMETER_GROUP);
		parameterValueEditionVo3.setText("ligoj-jupiter-client2");
		parameters.add(parameterValueEditionVo3);

		vo.setMode(SubscriptionMode.CREATE);
		vo.setParameters(parameters);
		vo.setNode("service:id:ldap:dig");
		vo.setProject(em.createQuery("SELECT id FROM Project WHERE name='Jupiter'", Integer.class).getSingleResult());

		// Ensure LDAP cache is loaded
		cacheManager.getCache("id-ldap-data").clear();
		cache.getData();
		em.flush();
		em.clear();

		initSpringSecurityContext(DEFAULT_USER);
		final var subscription = resource.create(vo);
		em.flush();
		em.clear();

		Assertions.assertEquals("ligoj-jupiter-client2",
				parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_GROUP));
		Assertions.assertEquals("ligoj",
				parameterValueRepository.getSubscriptionParameterValue(subscription, IdentityResource.PARAMETER_OU));
		Assertions.assertNull(parameterValueRepository.getSubscriptionParameterValue(subscription,
				IdentityResource.PARAMETER_PARENT_GROUP));

		// Check the creation in LDAP
		final var group = getGroup().findById("ligoj-jupiter-client2");
		Assertions.assertNotNull(group);
		Assertions.assertEquals("ligoj-jupiter-client2", group.getId());
		Assertions.assertEquals("ligoj-jupiter-client2", group.getName());
		Assertions.assertEquals(dn, group.getDn());
	}
}

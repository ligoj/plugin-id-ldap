/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.resource;

import java.util.Map;

import javax.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.id.ldap.dao.GroupLdapRepository;
import org.springframework.test.annotation.Rollback;

/**
 * Test class of {@link LdapPluginResource}
 */
@Rollback
@Transactional
public class LdapPluginResourceZDeleteTest extends AbstractLdapPluginResourceTest {
	@Test
	public void zzdeleteWithSubGroup() {
		// Create the data
		initSpringSecurityContext("fdaugan");

		// Create the parent group
		final Subscription parentSubscription = create("sea-parent-for-1deletion");
		createSubGroup(parentSubscription.getProject(), "sea-parent-for-1deletion", "sea-parent-for-1deletion-sub");

		// Check the subgroups are there
		Assertions.assertEquals(2, resource.findGroupsByName("sea-parent-for-1deletion").size());
		final Map<String, String> parameters = subscriptionResource.getParameters(parentSubscription.getId());
		Assertions.assertTrue(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
		Assertions.assertEquals(1, getGroup().findAll().get("sea-parent-for-1deletion").getSubGroups().size());
		Assertions.assertEquals("sea-parent-for-1deletion-sub",
				getGroup().findAll().get("sea-parent-for-1deletion").getSubGroups().iterator().next());

		// Delete the parent group
		resource.delete(parentSubscription.getId(), true);
		em.flush();
		em.clear();

		// Check the new status
		Assertions.assertNull(getGroup().findAll().get("sea-parent-for-1deletion"));
		Assertions.assertNull(getGroup().findAll().get("sea-parent-for-1deletion-sub"));
		Assertions.assertFalse(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
		Assertions.assertEquals(0, resource.findGroupsByName("sea-parent-for-1deletion").size());
	}

	/**
	 * Delete a group that is also member from another group.
	 */
	@Test
	public void zzdeleteFromParentGroup() {
		// Create the data
		initSpringSecurityContext("fdaugan");

		// Create the parent group
		final Subscription parentSubscription = create("sea-parent-for-2deletion");
		final Subscription childSubscription = createSubGroup(parentSubscription.getProject(), "sea-parent-for-2deletion",
				"sea-parent-for-2deletion-sub");

		// Check the sub-group and the parent are there
		Assertions.assertEquals(2, resource.findGroupsByName("sea-parent-for-2deletion").size());
		final Map<String, String> parentParameters = subscriptionResource.getParameters(parentSubscription.getId());
		Assertions.assertTrue(resource.checkSubscriptionStatus(parentParameters).getStatus().isUp());
		final Map<String, String> childParameters = subscriptionResource.getParameters(childSubscription.getId());
		Assertions.assertTrue(resource.checkSubscriptionStatus(childParameters).getStatus().isUp());
		Assertions.assertEquals(1, getGroup().findAll().get("sea-parent-for-2deletion").getSubGroups().size());
		Assertions.assertEquals("sea-parent-for-2deletion-sub",
				getGroup().findAll().get("sea-parent-for-2deletion").getSubGroups().iterator().next());

		// Delete the child group
		resource.delete(childSubscription.getId(), true);
		em.flush();
		em.clear();

		// Check the new status of the parent
		Assertions.assertTrue(resource.checkSubscriptionStatus(parentParameters).getStatus().isUp());
		Assertions.assertFalse(subscriptionResource.getParameters(parentSubscription.getId()).isEmpty());
		Assertions.assertEquals(1, resource.findGroupsByName("sea-parent-for-2deletion").size());
		Assertions.assertTrue(getGroup().findAll().get("sea-parent-for-2deletion").getSubGroups().isEmpty());
		Assertions.assertNull(getGroup().findAll().get("sea-parent-for-2deletion-sub"));
		Assertions.assertEquals("sea-parent-for-2deletion", resource.findGroupsByName("sea-parent-for-2deletion").get(0).getId());
		Assertions.assertNull(((GroupLdapRepository) getGroup()).findAllNoCache().get("sea-parent-for-2deletion-sub"));

		// Check the new status of the deleted child
		Assertions.assertFalse(resource.checkSubscriptionStatus(childParameters).getStatus().isUp());

		// Rollback the creation of the parent
		resource.delete(parentSubscription.getId(), true);
		Assertions.assertEquals(0, resource.findGroupsByName("sea-parent-for-2deletion").size());
		Assertions.assertNull(getGroup().findAll().get("sea-parent-for-2deletion"));

		// Check the LDAP content
		Assertions.assertNull(((GroupLdapRepository) getGroup()).findAllNoCache().get("sea-parent-for-2deletion"));
	}
}

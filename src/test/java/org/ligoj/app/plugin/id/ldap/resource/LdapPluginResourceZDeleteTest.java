/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.resource;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.Rollback;

/**
 * Test class of {@link LdapPluginResource}
 */
@Rollback
@Transactional
class LdapPluginResourceZDeleteTest extends AbstractLdapPluginResourceTest {
	@Test
	void zzDeleteWithSubGroup() {
		// Create the data
		initSpringSecurityContext("fdaugan");

		// Create the parent group
		final var parentSubscription = create("sea-parent-for-1deletion");
		createSubGroup(parentSubscription.getProject(), "sea-parent-for-1deletion", "sea-parent-for-1deletion-sub");

		// Check the subgroups are there
		Assertions.assertEquals(2, resource.findGroupsByName("sea-parent-for-1deletion").size());
		final var parameters = subscriptionResource.getParameters(parentSubscription.getId());
		Assertions.assertTrue(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
		Assertions.assertEquals(1, getGroup().findAll().get("sea-parent-for-1deletion").getSubGroups().size());
		Assertions.assertEquals("sea-parent-for-1deletion-sub",
				getGroup().findAll().get("sea-parent-for-1deletion").getSubGroups().iterator().next());

		// Delete the parent group
		resource.delete(parentSubscription.getId(), true);
		em.flush();
		em.clear();

		// Check the new status after refresh
		reloadLdapCache();
		Assertions.assertNull(getGroup().findAll().get("sea-parent-for-1deletion"));
		Assertions.assertNull(getGroup().findAll().get("sea-parent-for-1deletion-sub"));
		Assertions.assertFalse(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
		Assertions.assertEquals(0, resource.findGroupsByName("sea-parent-for-1deletion").size());
	}

	/**
	 * Delete a group that is also a member from another group.
	 */
	@Test
	void zzDeleteFromParentGroup() {
		// Create the data
		initSpringSecurityContext("fdaugan");

		// Create the parent group
		final var parentSubscription = create("sea-parent-for-2deletion");
		final var childSubscription = createSubGroup(parentSubscription.getProject(), "sea-parent-for-2deletion",
				"sea-parent-for-2deletion-sub");

		// Check the subgroup and the parents are there
		Assertions.assertEquals(2, resource.findGroupsByName("sea-parent-for-2deletion").size());
		final var parentParameters = subscriptionResource.getParameters(parentSubscription.getId());
		Assertions.assertTrue(resource.checkSubscriptionStatus(parentParameters).getStatus().isUp());
		final var childParameters = subscriptionResource.getParameters(childSubscription.getId());
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
		Assertions.assertEquals("sea-parent-for-2deletion", resource.findGroupsByName("sea-parent-for-2deletion").getFirst().getId());
		Assertions.assertNull(getGroup().findAllNoCache().get("sea-parent-for-2deletion-sub"));

		// Check the new status of the deleted child
		Assertions.assertFalse(resource.checkSubscriptionStatus(childParameters).getStatus().isUp());

		// Rollback the creation of the parent
		resource.delete(parentSubscription.getId(), true);
		Assertions.assertEquals(0, resource.findGroupsByName("sea-parent-for-2deletion").size());
		Assertions.assertNull(getGroup().findAll().get("sea-parent-for-2deletion"));

		// Check the LDAP content
		Assertions.assertNull((getGroup()).findAllNoCache().get("sea-parent-for-2deletion"));
	}
}

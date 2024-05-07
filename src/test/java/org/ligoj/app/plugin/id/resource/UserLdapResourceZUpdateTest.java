/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.resource;

import java.util.ArrayList;
import java.util.List;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.bootstrap.core.json.TableItem;
import org.springframework.test.annotation.Rollback;

/**
 * Test of {@link UserOrgResource} : only update<br>
 */
@Rollback
@Transactional
class UserLdapResourceZUpdateTest extends AbstractUserLdapResourceTest {

	@Test
	void zupdateUserHadNoMail() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("jdoe5");
		user.setFirstName("John5");
		user.setLastName("Doe5");
		user.setCompany("ing");
		user.setMail("first5.last5@ing.fr");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(user);
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "jdoe5", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		final UserOrgVo userLdap = tableItem.getData().getFirst();
		Assertions.assertEquals("jdoe5", userLdap.getId());
		Assertions.assertEquals("John5", userLdap.getFirstName());
		Assertions.assertEquals("Doe5", userLdap.getLastName());
		Assertions.assertEquals("ing", userLdap.getCompany());
		Assertions.assertEquals("first5.last5@ing.fr", userLdap.getMails().getFirst());
		Assertions.assertEquals(1, userLdap.getGroups().size());
		Assertions.assertEquals("DIG RHA", userLdap.getGroups().getFirst().getName());
	}

	@Test
	void zupdateUserHasNoMail() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("jdoe5");
		user.setFirstName("John5");
		user.setLastName("Doe5");
		user.setCompany("ing");
		user.setMail(null);
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(user);
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "jdoe5", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		final UserOrgVo userLdap = tableItem.getData().getFirst();
		Assertions.assertEquals("jdoe5", userLdap.getId());
		Assertions.assertEquals("John5", userLdap.getFirstName());
		Assertions.assertEquals("Doe5", userLdap.getLastName());
		Assertions.assertEquals("ing", userLdap.getCompany());
		Assertions.assertTrue(userLdap.getMails().isEmpty());
		Assertions.assertEquals(1, userLdap.getGroups().size());
		Assertions.assertEquals("DIG RHA", userLdap.getGroups().getFirst().getName());
	}

	@Test
	void zupdateUserNoPassword() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("jdoe4");
		user.setFirstName("John4");
		user.setLastName("Doe4");
		user.setCompany("ing");
		user.setMail("fohn4.doe4@ing.fr");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(user);
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "jdoe4", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		final UserOrgVo userLdap = tableItem.getData().getFirst();
		Assertions.assertEquals("jdoe4", userLdap.getId());
		Assertions.assertEquals("John4", userLdap.getFirstName());
		Assertions.assertEquals("Doe4", userLdap.getLastName());
		Assertions.assertEquals("ing", userLdap.getCompany());
		Assertions.assertEquals("fohn4.doe4@ing.fr", userLdap.getMails().getFirst());
		Assertions.assertEquals(1, userLdap.getGroups().size());
		Assertions.assertEquals("DIG RHA", userLdap.getGroups().getFirst().getName());
	}

	@Test
	void zupdateUserRemoveGroup() {
		// Pre-condition
		initSpringSecurityContext("fdaugan");
		final TableItem<UserOrgVo> initialResult = resource.findAll(null, null, "fdoe2", newUriInfoAsc("id"));
		Assertions.assertEquals(1, initialResult.getData().size());
		Assertions.assertEquals(2, initialResult.getData().getFirst().getGroups().size());
		Assertions.assertEquals("Biz Agency", initialResult.getData().getFirst().getGroups().getFirst().getName());
		Assertions.assertTrue(initialResult.getData().getFirst().getGroups().getFirst().isCanWrite());
		Assertions.assertEquals("DIG RHA", initialResult.getData().getFirst().getGroups().get(1).getName());
		Assertions.assertTrue(initialResult.getData().getFirst().getGroups().get(1).isCanWrite());

		// Remove group "Biz Agency"
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("fdoe2");
		user.setFirstName("First2");
		user.setLastName("Doe2");
		user.setCompany("ing");
		user.setMail("fdoe2@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("DIG RHA");
		user.setGroups(groups);
		resource.update(user);
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "fdoe2", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		final UserOrgVo userLdap = tableItem.getData().getFirst();
		Assertions.assertEquals("fdoe2", userLdap.getId());
		Assertions.assertEquals("First2", userLdap.getFirstName());
		Assertions.assertEquals("Doe2", userLdap.getLastName());
		Assertions.assertEquals("ing", userLdap.getCompany());
		Assertions.assertEquals("fdoe2@ing.com", userLdap.getMails().getFirst());
		Assertions.assertEquals(1, userLdap.getGroups().size());
		Assertions.assertEquals("DIG RHA", userLdap.getGroups().getFirst().getName());

		// Remove all groups
		user.setGroups(null);
		resource.update(user);
		final TableItem<UserOrgVo> tableItemNoGroup = resource.findAll(null, null, "fdoe2", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItemNoGroup.getData().size());
		Assertions.assertEquals(0, tableItemNoGroup.getData().getFirst().getGroups().size());

	}

}

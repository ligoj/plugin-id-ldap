/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.ldap.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.plugin.id.dao.AbstractMemCacheRepository.CacheDataType;
import org.ligoj.app.plugin.ldap.dao.CompanyLdapRepository;
import org.ligoj.app.plugin.ldap.dao.GroupLdapRepository;
import org.ligoj.app.plugin.ldap.dao.UserLdapRepository;

/**
 * Ligoj's own user writes keep the database copy current: they survive a restart without any LDAP read. Each test works
 * on a user it creates and deletes, so the shared test LDAP is left as found.
 */
class LdapCopyWritesTest extends AbstractLdapRestartTest {

	private static final String USER = "copy-writes";

	private UserOrg user;

	private boolean deleted;

	private UserLdapRepository users() {
		return (UserLdapRepository) configuration().getUserRepository();
	}

	@BeforeEach
	void createUser() {
		reloadLdapCache();
		final var newUser = new UserOrg();
		newUser.setId(USER);
		newUser.setFirstName("Copy");
		newUser.setLastName("Writes");
		newUser.setCompany("ing");
		newUser.setMails(new ArrayList<>(List.of("copy-writes@sample.com")));
		newUser.setGroups(new ArrayList<>());
		newUser.setCustomAttributes(new HashMap<>());
		user = users().create(newUser);
		deleted = false;
	}

	@AfterEach
	void deleteUser() {
		restoreLdap();
		if (!deleted) {
			// As the user resource does: the memberships are revoked before the deletion
			final var toDelete = users().findById(USER);
			users().updateMembership(new ArrayList<>(), toDelete);
			users().delete(toDelete);
		}
	}

	/**
	 * The user once restarted, with LDAP failing on any read.
	 */
	private UserOrg restarted() {
		restart();
		failLdap();
		return (UserOrg) cache.getData().get(CacheDataType.USER).get(USER);
	}

	@Test
	void createSurvivesRestart() {
		final var dn = user.getDn();
		final var restarted = restarted();
		Assertions.assertEquals(dn, restarted.getDn());
		Assertions.assertEquals("ing", restarted.getCompany());
		Assertions.assertEquals(List.of("copy-writes@sample.com"), restarted.getMails());
		Assertions.assertFalse(restarted.isSecured());
	}

	/**
	 * A password set after the creation: the user is secured after a restart, as a full read would tell.
	 */
	@Test
	void passwordSurvivesRestart() {
		users().setPassword(user, "Secret-2026!");
		Assertions.assertTrue(restarted().isSecured());
	}

	@Test
	void lockSurvivesRestart() {
		users().lock("admin", user);
		final var restarted = restarted();
		Assertions.assertEquals("admin", restarted.getLockedBy());
		Assertions.assertNotNull(restarted.getLocked());
	}

	@Test
	void unlockSurvivesRestart() {
		users().lock("admin", user);
		users().unlock(user);
		final var restarted = restarted();
		Assertions.assertNull(restarted.getLockedBy());
		Assertions.assertNull(restarted.getLocked());
	}

	@Test
	void isolateSurvivesRestart() {
		users().isolate("admin", user);
		final var dn = user.getDn();
		final var restarted = restarted();
		Assertions.assertEquals("ing", restarted.getIsolated());
		Assertions.assertEquals(((CompanyLdapRepository) configuration().getCompanyRepository()).getQuarantineCompany(),
				restarted.getCompany());
		Assertions.assertEquals(dn, restarted.getDn());
		Assertions.assertEquals("admin", restarted.getLockedBy());
	}

	@Test
	void restoreSurvivesRestart() {
		final var dn = user.getDn();
		users().isolate("admin", user);
		users().restore(user);
		final var restarted = restarted();
		Assertions.assertNull(restarted.getIsolated());
		Assertions.assertNull(restarted.getLockedBy());
		Assertions.assertEquals("ing", restarted.getCompany());
		Assertions.assertEquals(dn, restarted.getDn());
	}

	/**
	 * An update gives only the edited attributes: the lock state and the DN of the user stay in the copy.
	 */
	@Test
	void updateSurvivesRestart() {
		users().lock("admin", user);
		final var edited = new UserOrg();
		edited.setId(USER);
		edited.setFirstName("Copied");
		edited.setLastName("Writes");
		edited.setCompany("ing");
		edited.setDn(user.getDn());
		edited.setMails(new ArrayList<>(List.of("copy-writes@sample.com", "other@sample.com")));
		edited.setCustomAttributes(new HashMap<>());
		users().updateUser(edited);
		final var restarted = restarted();
		Assertions.assertEquals("Copied", restarted.getFirstName());
		Assertions.assertEquals(List.of("copy-writes@sample.com", "other@sample.com"), restarted.getMails());
		Assertions.assertEquals("admin", restarted.getLockedBy());
		Assertions.assertEquals(user.getDn(), restarted.getDn());
	}

	@Test
	void deleteSurvivesRestart() {
		users().delete(user);
		deleted = true;
		restart();
		failLdap();
		Assertions.assertNull(cache.getData().get(CacheDataType.USER).get(USER));
	}

	@Test
	void membershipSurvivesRestart() {
		@SuppressWarnings("unchecked")
		final var groups = (Map<String, GroupOrg>) cache.getData().get(CacheDataType.GROUP);
		final var group = groups.keySet().iterator().next();
		((GroupLdapRepository) configuration().getGroupRepository()).addUser(user, group);
		final var restarted = restarted();
		Assertions.assertTrue(restarted.getGroups().contains(group));
		@SuppressWarnings("unchecked")
		final var restartedGroups = (Map<String, GroupOrg>) cache.getData().get(CacheDataType.GROUP);
		Assertions.assertTrue(restartedGroups.get(group).getMembers().contains(USER));
	}
}

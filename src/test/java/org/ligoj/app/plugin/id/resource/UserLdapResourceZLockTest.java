/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.resource;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.Rollback;

/**
 * Test of {@link UserOrgResource}<br>
 * Delegate
 * <ul>
 * <li>user;type;name;write;admin;dn</li>
 * <li>fdaugan;group;dig rha;true;true;cn=DIG RHA,cn=DIG
 * AS,cn=DIG,ou=fonction,ou=groups</li>
 * <li>fdaugan;group;any;true;true;cn=any,ou=groups</li>
 * <li>fdaugan;company;ing;true;true;ou=ing,ou=external,ou=people</li>
 * <li>fdaugan;company;any;true;true;cn=any,ou=groups</li>
 * <li>fdaugan;tree;ou=tools;true;true;ou=tools</li>
 * <li>someone;company;ing;true;true;ou=ing,ou=external,ou=people</li>
 * <li>someone;company;any;true;true;cn=any,ou=groups</li>
 * <li>someone;group;dig rha;true;true;cn=DIG RHA,cn=DIG
 * AS,cn=DIG,ou=fonction,ou=groups</li>
 * <li>junit;tree;dc=sample,dc=com;true;true;dc=sample,dc=com</li>
 * <li>assist;company;socygan;true;true;ou=socygan,ou=external,ou=people</li>
 * <li>assist;company;ing;true;true;ou=ing,ou=external,ou=people</li>
 * <li>mmartin;group;dig sud ouest;true;true;cn=DIG Sud Ouest,cn=DIG
 * AS,cn=DIG,ou=fonction,ou=groups</li>
 * <li>mmartin;group;any;true;true;cn=any,ou=groups</li>
 * <li>mmartin;company;socygan;true;true;ou=socygan,ou=external,ou=people</li>
 * <li>mmartin;company;any;true;true;cn=any,ou=groups</li>
 * <li>mtuyer;tree;ou=fonction,ou=groups;true;true;ou=fonction,ou=groups</li>
 * <li>mtuyer;company;ing;false;true;ou=ing,ou=external,ou=people</li>
 * <li>mlavoine;tree;cn=Biz Agency,ou=tools;false;false;cn=Biz
 * Agency,ou=tools</li>
 * <li>ligoj-jupiter
 * (group);company;ing;false;false;ou=ing,ou=external,ou=people,dc=sample,dc=com</li>
 * <li>ing (company);group;business solution;false;false;cn=business
 * solution,ou=groups,dc=sample,dc=com</li>
 * </ul>
 * LDAP
 * <ul>
 * <li>ING : flast1(First1 Last1), fdoe2(First2 Doe2),jlast3(John3
 * Last3),jdoe4(John4 Doe4),jdoe5(First5 Last5)</li>
 * <li>socygan : flast0</li>
 * <li>DIG RHA : fdoe2,jlast3,jdoe4,jdoe5</li>
 * <li>DIG SUD OUEST : jlast3,pgenais</li>
 * </ul>
 */
@Rollback
@Transactional
class UserLdapResourceZLockTest extends AbstractUserLdapResourceTest {

	@Test
	void zlockUnlockUser() {
		checkUnlockedBefore();
		resource.lock("admin-tesT");
		check("ligoj", "ou=ligoj,ou=france,ou=people,dc=sample,dc=com", "LOCKED\\|\\d+\\|junit\\|\\|", this::assertLocked);

		// Another lock
		resource.lock("admin-test");
		check("ligoj", "ou=ligoj,ou=france,ou=people,dc=sample,dc=com", "LOCKED\\|\\d+\\|junit\\|\\|", this::assertLocked);

		resource.unlock("admin-test");
		checkUnlockedAfter();

		// Another unlock
		resource.unlock("admin-test");
		checkUnlockedAfter();
	}

	@Test
	void zisolateRestoreUser() {
		checkDnAndMember(checkUnlockedBefore(), "uid=admin-test,ou=ligoj,ou=france,ou=people,dc=sample,dc=com");

		// Isolate
		resource.isolate("admin-Test");
		check("quarantine", "ou=quarantine,dc=sample,dc=com", "LOCKED\\|\\d+\\|junit\\|ligoj\\|", this::assertLocked);

		// Isolate again
		resource.isolate("admin-test");
		check("quarantine", "ou=quarantine,dc=sample,dc=com", "LOCKED\\|\\d+\\|junit\\|ligoj\\|", this::assertLocked);

		// Lock the user (useless)
		resource.lock("admin-test");
		check("quarantine", "ou=quarantine,dc=sample,dc=com", "LOCKED\\|\\d+\\|junit\\|ligoj\\|", this::assertLocked);

		// Unlock the user (useless)
		resource.unlock("admin-test");
		check("quarantine", "ou=quarantine,dc=sample,dc=com", "LOCKED\\|\\d+\\|junit\\|ligoj\\|", this::assertLocked);

		checkDnAndMember(check("quarantine", "ou=quarantine,dc=sample,dc=com", "LOCKED\\|\\d+\\|junit\\|ligoj\\|", userLdap -> {
			assertLocked(userLdap);
			Assertions.assertEquals("ligoj", userLdap.getIsolated());
		}), "uid=admin-test,ou=quarantine,dc=sample,dc=com");

		// Restore
		resource.restore("admin-test");

		// Check the uniqueMember is restored for the related groups
		checkDnAndMember(checkUnlockedAfter(), "uid=admin-test,ou=ligoj,ou=france,ou=people,dc=sample,dc=com");

		// Restore again
		resource.restore("admin-test");

		// Check the uniqueMember is restored for the related groups
		checkDnAndMember(checkUnlockedAfter(), "uid=admin-test,ou=ligoj,ou=france,ou=people,dc=sample,dc=com");
	}
}

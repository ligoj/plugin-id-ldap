/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.ldap.resource;


import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.plugin.ldap.dao.UserLdapRepository;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Restart of the API: the identity data comes from the database copy while it is fresh, and the daily full read keeps
 * its time.
 */
class CacheLdapRestartTest extends AbstractLdapRestartTest {

	@Test
	void restartFromCopy() {
		reloadLdapCache();
		final var before = cache.getData();
		restart();
		failLdap();
		LdapDataAssertions.assertSameData(before, cache.getData());
	}

	@Test
	void restartWithTheMarkerKept() {
		reloadLdapCache();
		final var before = cache.getData();
		ReflectionTestUtils.setField(target(), "data", null);
		failLdap();
		LdapDataAssertions.assertSameData(before, cache.getData());
	}

	@Test
	void restartKeepsTheFullReadTime() {
		reloadLdapCache();
		final var lastFullRead = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS);
		setLastFullRead(lastFullRead);
		restart();
		failLdap();
		Assertions.assertEquals(lastFullRead.toEpochMilli(), cache.ensureCachedData());
	}

	@Test
	void restartAfterTtlReadsLdap() {
		reloadLdapCache();
		setLastFullRead(Instant.now().minus(Duration.ofDays(1)).minusSeconds(1));
		restart();
		final var start = Instant.now().minusSeconds(1);
		cache.getData();
		Assertions.assertFalse(state().getLastFullRead().isBefore(start));
	}

	@Test
	void restartWithOtherConfigurationReadsLdap() {
		reloadLdapCache();
		final var users = (UserLdapRepository) configuration().getUserRepository();
		final var customAttributes = users.getCustomAttributes();
		try {
			users.setCustomAttributes(new String[] { "added" });
			restart();
			failLdap();
			Assertions.assertThrows(IllegalStateException.class, cache::getData);
		} finally {
			users.setCustomAttributes(customAttributes);
		}
	}

	@Test
	void dueFullReadRearmsTheMarker() {
		reloadLdapCache();
		cache.arm(Instant.now().minus(Duration.ofDays(1)).minusSeconds(1).toEpochMilli());
		final var start = Instant.now().minusSeconds(1);
		cache.getData();
		Assertions.assertFalse(state().getLastFullRead().isBefore(start));

		// The marker holds the new time: no other read
		failLdap();
		Assertions.assertNotNull(cache.getData());
	}

	@Test
	void manualFlushReadsLdap() {
		reloadLdapCache();
		failLdap();
		cacheManager.getCache("id-ldap-data").clear();
		Assertions.assertThrows(IllegalStateException.class, cache::getData);
	}

	/**
	 * A full read failing on LDAP never reaches the reset: the memory and the copy stay as they were, the next restart
	 * still uses the copy.
	 */
	@Test
	void failedFullReadKeepsTheCopy() {
		reloadLdapCache();
		final var before = ReflectionTestUtils.getField(target(), "data");
		final var lastFullRead = state().getLastFullRead();
		failLdap();
		cacheManager.getCache("id-ldap-data").clear();
		Assertions.assertThrows(IllegalStateException.class, cache::getData);
		Assertions.assertSame(before, ReflectionTestUtils.getField(target(), "data"));
		Assertions.assertEquals(lastFullRead, state().getLastFullRead());
	}

	/**
	 * Without a CRON expression, the next full read is due one day, the TTL of the cache, after the last one.
	 */
	@Test
	void nextFullReadFromTheCache() {
		final var lastFullRead = Instant.parse("2026-11-10T08:00:00Z");
		Assertions.assertEquals(lastFullRead.plus(Duration.ofDays(1)), cache.getNextFullRead(lastFullRead));
	}
}

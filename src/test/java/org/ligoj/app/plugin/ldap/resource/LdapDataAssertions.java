/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.ldap.resource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.ResourceOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.plugin.id.dao.AbstractMemCacheRepository.CacheDataType;

/**
 * Field by field comparison of the LDAP in-memory data, as a full read builds it.
 */
public final class LdapDataAssertions {

	private LdapDataAssertions() {
		// Utility class
	}

	/**
	 * Compare the users, the groups and the companies.
	 *
	 * @param expected The expected data.
	 * @param actual   The actual data.
	 */
	@SuppressWarnings("unchecked")
	public static void assertSameData(final Map<CacheDataType, Map<String, ? extends ResourceOrg>> expected,
			final Map<CacheDataType, Map<String, ? extends ResourceOrg>> actual) {
		assertSameUsers((Map<String, UserOrg>) expected.get(CacheDataType.USER), (Map<String, UserOrg>) actual.get(CacheDataType.USER));
		assertSameGroups((Map<String, GroupOrg>) expected.get(CacheDataType.GROUP), (Map<String, GroupOrg>) actual.get(CacheDataType.GROUP));
		assertSameCompanies((Map<String, CompanyOrg>) expected.get(CacheDataType.COMPANY),
				(Map<String, CompanyOrg>) actual.get(CacheDataType.COMPANY));
	}

	public static void assertSameUsers(final Map<String, UserOrg> expected, final Map<String, UserOrg> actual) {
		Assertions.assertEquals(expected.keySet(), actual.keySet());
		expected.values().forEach(e -> {
			final var a = actual.get(e.getId());
			final var id = e.getId();
			Assertions.assertEquals(e.getDn(), a.getDn(), id);
			Assertions.assertEquals(e.getFirstName(), a.getFirstName(), id);
			Assertions.assertEquals(e.getLastName(), a.getLastName(), id);
			Assertions.assertEquals(e.getMails(), a.getMails(), id);
			Assertions.assertEquals(e.getCompany(), a.getCompany(), id);
			Assertions.assertEquals(new HashSet<>(e.getGroups()), new HashSet<>(a.getGroups()), id);
			Assertions.assertEquals(e.isSecured(), a.isSecured(), id);
			Assertions.assertEquals(e.getLocked(), a.getLocked(), id);
			Assertions.assertEquals(e.getLockedBy(), a.getLockedBy(), id);
			Assertions.assertEquals(e.getIsolated(), a.getIsolated(), id);
			Assertions.assertEquals(e.getDepartment(), a.getDepartment(), id);
			Assertions.assertEquals(e.getLocalId(), a.getLocalId(), id);
			Assertions.assertEquals(e.getCustomAttributes(), a.getCustomAttributes(), id);
		});
	}

	public static void assertSameGroups(final Map<String, GroupOrg> expected, final Map<String, GroupOrg> actual) {
		Assertions.assertEquals(expected.keySet(), actual.keySet());
		expected.values().forEach(e -> {
			final var a = actual.get(e.getId());
			final var id = e.getId();
			Assertions.assertEquals(e.getDn(), a.getDn(), id);
			Assertions.assertEquals(e.getName(), a.getName(), id);
			Assertions.assertEquals(e.getMembers(), a.getMembers(), id);
			Assertions.assertEquals(e.getSubGroups(), a.getSubGroups(), id);
			Assertions.assertEquals(e.getParent(), a.getParent(), id);
		});
	}

	public static void assertSameCompanies(final Map<String, CompanyOrg> expected, final Map<String, CompanyOrg> actual) {
		Assertions.assertEquals(expected.keySet(), actual.keySet());
		final Function<CompanyOrg, List<String>> tree = c -> c.getCompanyTree().stream().map(CompanyOrg::getId).toList();
		expected.values().forEach(e -> {
			final var a = actual.get(e.getId());
			final var id = e.getId();
			Assertions.assertEquals(e.getDn(), a.getDn(), id);
			Assertions.assertEquals(e.getName(), a.getName(), id);
			Assertions.assertEquals(e.isLocked(), a.isLocked(), id);
			Assertions.assertEquals(e.getLdapName(), a.getLdapName(), id);
			Assertions.assertEquals(tree.apply(e), tree.apply(a), id);
		});
	}
}

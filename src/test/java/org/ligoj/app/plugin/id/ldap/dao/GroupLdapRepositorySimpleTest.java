/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.dao;

import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.plugin.id.dao.AbstractMemCacheRepository;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Test class of {@link GroupLdapRepository}
 */
class GroupLdapRepositorySimpleTest {

	@Test
	void delete() {
		final var groupRepository = new MyGroupLdapRepository();
		var group = new GroupOrg("dc=test", "test", new HashSet<>());
		var broken = new GroupOrg("dc=broken", "broken", new HashSet<>());
		var sub1 = new GroupOrg("dc=sub1", "sub1", new HashSet<>());
		group.setSubGroups(Set.of(sub1.getName(), "broken"));
		sub1.setParent(group.getName());
		broken.setParent("broken");
		var cachedData = Map.of(AbstractMemCacheRepository.CacheDataType.GROUP, Map.of(group.getName(), group, sub1.getName(), sub1, broken.getName(), broken));
		groupRepository.cacheRepository = Mockito.mock(CacheLdapRepository.class);
		Mockito.doReturn(cachedData).when(groupRepository.cacheRepository).getData();
		groupRepository.delete(sub1);
		groupRepository.delete(group);
		groupRepository.delete(broken);
	}

	private static class MyGroupLdapRepository extends GroupLdapRepository {
		@Override
		public GroupOrg findById(final String id) {
			if ("broken".equals(id)) {
				return null;
			}
			return findAll().get(id);
		}

		@Override
		public void removeGroup(final GroupOrg subGroup, final String group) {
			// Ignore
		}

		@Override
		protected void unbind(final String dn) {
			// Ignore
		}

	}
}

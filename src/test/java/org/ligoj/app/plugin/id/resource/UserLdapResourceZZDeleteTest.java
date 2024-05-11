/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.resource;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.test.annotation.Rollback;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Test of {@link UserOrgResource} : only deletion<br>
 */
@Rollback
@Transactional
class UserLdapResourceZZDeleteTest extends AbstractUserLdapResourceTest {

	@Test
	void zzDeleteUser() {
		initSpringSecurityContext("assist");
		Assertions.assertEquals(1, resource.findAll("ing", null, "jdoe5", newUriInfo()).getData().size());
		Assertions.assertNotNull(getUser().findByIdNoCache("jdoE5"));
		Assertions.assertTrue(getGroup().findAll().get("dig rha").getMembers().contains("jdoe5"));
		resource.delete("jDOE5");
		Assertions.assertEquals(0, resource.findAll("ing", null, "jdoe5", newUriInfo()).getData().size());
		Assertions.assertNull(getUser().findByIdNoCache("jdoe5"));
		Assertions.assertFalse(getGroup().findAll().get("dig rha").getMembers().contains("jdoe5"));

		final var filter = new AndFilter()
				.and(new EqualsFilter("objectClass", "groupOfUniqueNames"))
				.and(new EqualsFilter("cn", "dig rha"));
		final var groups = getTemplate().search("ou=groups,dc=sample,dc=com", filter.encode(),
				(Object ctx) -> (DirContextAdapter) ctx);
		Assertions.assertEquals(1, groups.size());
		final var group = groups.getFirst();
		final String[] stringAttributes = group.getStringAttributes("uniqueMember");
		Assertions.assertNotEquals(0, stringAttributes.length);
		Arrays.stream(stringAttributes).forEach(memberDN -> Assertions.assertFalse(memberDN.startsWith("uid=jdoe5")));

		// Restore the state, create back the user
		initSpringSecurityContext(DEFAULT_USER);
		final var user = new UserOrgEditionVo();
		user.setId("jdoe5");
		user.setFirstName("First5");
		user.setLastName("Last5");
		user.setCompany("ing-internal");
		final var groups2 = new ArrayList<String>();
		groups2.add("DIG RHA");
		user.setGroups(groups2);
		resource.create(user);
	}
}

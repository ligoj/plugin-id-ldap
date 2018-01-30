package org.ligoj.app.plugin.id.resource;

import java.util.ArrayList;
import java.util.List;

import javax.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.test.annotation.Rollback;

/**
 * Test of {@link UserOrgResource} : only deletion<br>
 */
@Rollback
@Transactional
public class UserLdapResourceZZDeleteTest extends AbstractUserLdapResourceTest {

	@Test
	public void zzdeleteUser() {
		initSpringSecurityContext("assist");
		Assertions.assertEquals(1, resource.findAll("ing", null, "jdoe5", newUriInfo()).getData().size());
		Assertions.assertNotNull(getUser().findByIdNoCache("jdoE5"));
		Assertions.assertTrue(getGroup().findAll().get("dig rha").getMembers().contains("jdoe5"));
		resource.delete("jDOE5");
		Assertions.assertEquals(0, resource.findAll("ing", null, "jdoe5", newUriInfo()).getData().size());
		Assertions.assertNull(getUser().findByIdNoCache("jdoe5"));
		Assertions.assertFalse(getGroup().findAll().get("dig rha").getMembers().contains("jdoe5"));

		final AndFilter filter = new AndFilter();
		filter.and(new EqualsFilter("objectclass", "groupOfUniqueNames"));
		filter.and(new EqualsFilter("cn", "dig rha"));
		final List<DirContextAdapter> groups = getTemplate().search("ou=groups,dc=sample,dc=com", filter.encode(),
				(Object ctx) -> (DirContextAdapter) ctx);
		Assertions.assertEquals(1, groups.size());
		final DirContextAdapter group = groups.get(0);
		final String[] stringAttributes = group.getStringAttributes("uniqueMember");
		Assertions.assertFalse(stringAttributes.length == 0);
		for (final String memberDN : stringAttributes) {
			Assertions.assertFalse(memberDN.startsWith("uid=jdoe5"));
		}

		// Restore the state, create back the user
		initSpringSecurityContext(DEFAULT_USER);
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("jdoe5");
		user.setFirstName("First5");
		user.setLastName("Last5");
		user.setCompany("ing-internal");
		final List<String> groups2 = new ArrayList<>();
		groups2.add("DIG RHA");
		user.setGroups(groups2);
		resource.create(user);
	}
}

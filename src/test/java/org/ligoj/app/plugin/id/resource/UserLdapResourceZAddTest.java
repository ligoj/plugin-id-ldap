/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.resource;

import java.util.ArrayList;
import java.util.List;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
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
 * <li>ligoj-gstack
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
class UserLdapResourceZAddTest extends AbstractUserLdapResourceTest {

	@Test
	void zcreateUser() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flasta");
		user.setFirstName("FirstA ");
		user.setLastName(" LASTA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rHA");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.create(user);

		// Check the result, using the cache
		checkResult(resource.findAll(null, null, "flasta", newUriInfoAsc("id")));

		// Check the result, using a fresh new cache
		cacheManager.getCache("id-ldap-data").clear();
		checkResult(resource.findAll(null, null, "flasta", newUriInfoAsc("id")));

		// Restore the state, delete this new user
		resource.delete("flasta");
	}

	@Test
	void zcreateUserDelegateCompanyNotExist() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flastc");
		user.setFirstName("FirstC");
		user.setLastName("LastC");
		user.setCompany("any");
		user.setMail("flastc@ing.com");
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(user);
		}), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void zcreateUserNoDelegate() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flastd");
		user.setFirstName("FirstD");
		user.setLastName("LastD");
		user.setCompany("ing");
		user.setMail("flastd@ing.com");
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(user);
		}), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void zcreateUserNoDelegateCompany() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flastc");
		user.setFirstName("FirstC");
		user.setLastName("LastC");
		user.setCompany("socygan");
		user.setMail("flastc@ing.com");
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(user);
		}), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void zcreateUserNoDelegateGroup() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flastg");
		user.setFirstName("FirstG");
		user.setLastName("LastG");
		user.setCompany("ing");
		user.setMail("flastg@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig sud ouest");
		user.setGroups(groups);
		initSpringSecurityContext("someone");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(user);
		}), "group", BusinessException.KEY_UNKNOWN_ID);
	}

	/**
	 * Test user addition to a group.
	 */
	@Test
	void zaddRemoveUser() {
		// Pre condition
		Assertions.assertFalse(resource.findById("wuser").getGroups().contains("DIG RHA"));
		Assertions.assertFalse(getGroup().findById("dig rha").getMembers().contains("wuser"));

		resource.addUserToGroup("wuser", "dig rha");

		// Post condition
		Assertions.assertTrue(resource.findById("wuser").getGroups().contains("DIG RHA"));
		Assertions.assertTrue(getGroup().findById("dig rha").getMembers().contains("wuser"));

		resource.removeUserFromGroup("wuser", "dig rha");

		// Post condition 2
		Assertions.assertFalse(resource.findById("wuser").getGroups().contains("DIG RHA"));
		Assertions.assertFalse(getGroup().findById("dig rha").getMembers().contains("wuser"));
	}
}

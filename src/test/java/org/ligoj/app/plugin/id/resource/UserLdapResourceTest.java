/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.resource;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.plugin.id.ldap.dao.GroupLdapRepository;
import org.ligoj.app.plugin.id.ldap.dao.UserLdapRepository;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.Mockito;
import org.springframework.test.annotation.Rollback;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Test of {@link UserOrgResource}<br>
 * Delegate
 */
@Rollback
@Transactional
class UserLdapResourceTest extends AbstractUserLdapResourceTest {

	@Test
	void findById() {
		final UserOrg userLdap = resource.findById("fdaugan");
		findById(userLdap);
	}

	@Test
	void findByIdNoCache() {
		final UserOrg userLdap = resource.findByIdNoCache("fdaugan");
		Assertions.assertNotNull(userLdap);
		Assertions.assertEquals("fdaugan", userLdap.getId());
		Assertions.assertEquals("Fabrice", userLdap.getFirstName());
		Assertions.assertEquals("Daugan", userLdap.getLastName());
		Assertions.assertEquals("ligoj", userLdap.getCompany());
		Assertions.assertEquals("fabrice.daugan@sample.com", userLdap.getMails().getFirst());
	}

	@Test
	void findByIdCaseInsensitive() {
		final UserOrg userLdap = resource.findById("fdaugan");
		findById(userLdap);
	}

	@Test
	void findBy() {
		final List<UserOrg> users = resource.findAllBy("mail", "marc.martin@sample.com");
		Assertions.assertEquals(1, users.size());
		final UserOrg userLdap = users.getFirst();
		Assertions.assertEquals("mmartin", userLdap.getName());
		Assertions.assertEquals("3890", userLdap.getDepartment());
		Assertions.assertEquals("8234", userLdap.getLocalId());
	}

	@Test
	void findByIdNotExists() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.findById("any")), "id", "unknown-id");
	}

	@Test
	void findByIdNotManagedUser() {
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.findById("fdaugan")), "id", "unknown-id");
	}

	/**
	 * Show users inside the company "ing" (or sub company), and members of
	 * group "dig rha", and matching to criteria "iRsT"
	 */
	@Test
	void findAllAllFiltersAllRights() {

		final var tableItem = resource.findAll("ing", "dig rha", "iRsT", newUriInfoAsc("id"));
		Assertions.assertEquals(2, tableItem.getRecordsTotal());
		Assertions.assertEquals(2, tableItem.getRecordsFiltered());

		// Check the users
		final UserOrgVo userLdap = tableItem.getData().getFirst();
		Assertions.assertEquals("fdoe2", userLdap.getId());
		Assertions.assertEquals("jdoe5", tableItem.getData().get(1).getId());

		// Check the other attributes
		Assertions.assertEquals("ing", userLdap.getCompany());
		Assertions.assertEquals("First2", userLdap.getFirstName());
		Assertions.assertEquals("Doe2", userLdap.getLastName());
		Assertions.assertEquals("first2.doe2@ing.fr", userLdap.getMails().getFirst());
		Assertions.assertTrue(userLdap.isCanWrite());
		final List<GroupVo> groups = new ArrayList<>(userLdap.getGroups());
		Assertions.assertEquals(2, groups.size());
		Assertions.assertEquals("Biz Agency", groups.getFirst().getName());
		Assertions.assertEquals("DIG RHA", groups.get(1).getName());
	}

	@Test
	void findAllAllFiltersReducesGroupsAscLogin() {
		initSpringSecurityContext("fdaugan");
		final var tableItem = resource.findAll("ing", "dig rha", "iRsT", newUriInfoAsc("id"));
		Assertions.assertEquals(2, tableItem.getRecordsTotal());
		Assertions.assertEquals(2, tableItem.getRecordsFiltered());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().getFirst().getId());
		Assertions.assertEquals("jdoe5", tableItem.getData().get(1).getId());

		// Check the other attributes
		Assertions.assertEquals("ing", tableItem.getData().getFirst().getCompany());
		Assertions.assertEquals("First2", tableItem.getData().getFirst().getFirstName());
		Assertions.assertEquals("Doe2", tableItem.getData().getFirst().getLastName());
		Assertions.assertEquals("first2.doe2@ing.fr", tableItem.getData().getFirst().getMails().getFirst());
		final List<GroupVo> groups = new ArrayList<>(tableItem.getData().getFirst().getGroups());
		Assertions.assertEquals(2, groups.size());
		Assertions.assertEquals("Biz Agency", groups.getFirst().getName());
		Assertions.assertEquals("DIG RHA", groups.get(1).getName());
	}

	@Test
	void findAllNotSecure() {
		initSpringSecurityContext("fdaugan");
		final List<UserOrg> tableItem = resource.findAllNotSecure("ing", "dig rha");
		Assertions.assertEquals(4, tableItem.size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getFirst().getId());
		Assertions.assertEquals("jdoe4", tableItem.get(1).getId());
		Assertions.assertEquals("jdoe5", tableItem.get(2).getId());

		// Check the other attributes
		Assertions.assertEquals("ing", tableItem.getFirst().getCompany());
		Assertions.assertEquals("First2", tableItem.getFirst().getFirstName());
		Assertions.assertEquals("Doe2", tableItem.getFirst().getLastName());
		Assertions.assertEquals("first2.doe2@ing.fr", tableItem.getFirst().getMails().getFirst());
		Assertions.assertEquals(2, tableItem.getFirst().getGroups().size());
		Assertions.assertTrue(tableItem.getFirst().getGroups().contains("biz agency"));
		Assertions.assertTrue(tableItem.getFirst().getGroups().contains("dig rha"));
	}

	@Test
	void findAllDefaultDescFirstName() {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "5");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add(DataTableAttributes.START, "6");
		uriInfo.getQueryParameters().add("columns[2][data]", "firstName");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "desc");

		initSpringSecurityContext("fdaugan");
		final var tableItem = resource.findAll(null, null, "e", uriInfo);
		Assertions.assertEquals(13, tableItem.getRecordsTotal());
		Assertions.assertEquals(13, tableItem.getRecordsFiltered());
		Assertions.assertEquals(5, tableItem.getData().size());

		// Check the users

		// My company
		// [SimpleUser(id=jdoe4), SimpleUser(id=hdurant), SimpleUser(id=fdoe2),
		// SimpleUser(id=fdauganb)]
		Assertions.assertEquals("jdoe4", tableItem.getData().getFirst().getId());
		Assertions.assertEquals("hdurant", tableItem.getData().get(1).getId());
		Assertions.assertEquals("fdoe2", tableItem.getData().get(3).getId());

		// Not my company, brought by delegation
		Assertions.assertEquals("jdoe5", tableItem.getData().get(2).getId()); //
	}

	@Test
	void findAllDefaultDescMail() {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "5");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add(DataTableAttributes.START, "2");
		uriInfo.getQueryParameters().add("columns[2][data]", "mail");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "desc");

		initSpringSecurityContext("fdaugan");
		final var tableItem = resource.findAll(null, null, "@sample.com", uriInfo);
		Assertions.assertEquals(6, tableItem.getRecordsTotal());
		Assertions.assertEquals(6, tableItem.getRecordsFiltered());
		Assertions.assertEquals(5, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdaugan", tableItem.getData().get(1).getId());
	}

	/**
	 * One delegation to members of group "ligoj-jupiter" to see the company "ing"
	 */
	@Test
	void findAllUsingDelegateReceiverGroup() {
		initSpringSecurityContext("admin-test");
		final var tableItem = resource.findAll(null, null, null, newUriInfoAsc("id"));

		// Counts : 8 from ing, + 7 from the same company
		Assertions.assertEquals(15, tableItem.getRecordsTotal());
		Assertions.assertEquals(15, tableItem.getRecordsFiltered());
		Assertions.assertEquals(15, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("admin-test", tableItem.getData().getFirst().getId());
		Assertions.assertFalse(tableItem.getData().getFirst().isCanWrite());

		// Check the groups
		Assertions.assertEquals(1, tableItem.getData().getFirst().getGroups().size());
		Assertions.assertEquals("ligoj-Jupiter", tableItem.getData().getFirst().getGroups().getFirst().getName());
	}

	/**
	 * No delegation for any group, but only for a company. So see only users
	 * within these company : ing(5) + socygan(1)
	 */
	@Test
	void findAllForMyCompany() {
		initSpringSecurityContext("assist");
		final var tableItem = resource.findAll(null, null, null, newUriInfoAsc("id"));
		Assertions.assertEquals(9, tableItem.getRecordsTotal());
		Assertions.assertEquals(9, tableItem.getRecordsFiltered());
		Assertions.assertEquals(9, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().getFirst().getId());
		Assertions.assertTrue(tableItem.getData().getFirst().isCanWrite());

		// Check the groups
		Assertions.assertEquals(0, tableItem.getData().getFirst().getGroups().size());
	}

	/**
	 * No delegation for any group, but only for a company. So see only users
	 * within this company : ing(5)
	 */
	@Test
	void findAllForMyCompanyFilter() {
		initSpringSecurityContext("assist");

		final var tableItem = resource.findAll("ing", null, null, newUriInfoAsc("id"));
		Assertions.assertEquals(8, tableItem.getRecordsTotal());
		Assertions.assertEquals(8, tableItem.getRecordsFiltered());
		Assertions.assertEquals(8, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().getFirst().getId());
		Assertions.assertTrue(tableItem.getData().getFirst().isCanWrite());

		// Check the groups
		Assertions.assertEquals(0, tableItem.getData().getFirst().getGroups().size());
	}

	/**
	 * Whatever the managed company, if the current user sees a group, can
	 * search any user even in a different company this user can manage. <br>
	 */
	@Test
	void findAllForMyGroup() {
		initSpringSecurityContext("mmartin");
		final var tableItem = resource.findAll(null, "dig as", null, newUriInfoAsc("id"));

		// 4 users from delegate and 1 from my company
		Assertions.assertEquals(5, tableItem.getRecordsTotal());
		Assertions.assertEquals(5, tableItem.getRecordsFiltered());
		Assertions.assertEquals(5, tableItem.getData().size());

		// Check the users (from delegate)
		Assertions.assertEquals("fdoe2", tableItem.getData().getFirst().getId());
		Assertions.assertFalse(tableItem.getData().getFirst().isCanWrite());
		Assertions.assertTrue(tableItem.getData().getFirst().isCanWriteGroups());
	}

	/**
	 * Whatever the managed company, if the current user sees a group, then he
	 * can search any user even in a different company this user can manage.
	 * <br>
	 */
	@Test
	void findAllForMySubGroup() {
		initSpringSecurityContext("mmartin");
		final var tableItem = resource.findAll(null, "biz agency", "fdoe2", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().getFirst().getId());
		Assertions.assertFalse(tableItem.getData().getFirst().isCanWrite());
		Assertions.assertTrue(tableItem.getData().getFirst().isCanWriteGroups());

		// Check the groups
		// "Biz Agency" is visible since "mmartin" is in the parent group "
		Assertions.assertEquals(2, tableItem.getData().getFirst().getGroups().size());
		Assertions.assertEquals("Biz Agency", tableItem.getData().getFirst().getGroups().getFirst().getName());
		Assertions.assertTrue(tableItem.getData().getFirst().getGroups().getFirst().isCanWrite());
		Assertions.assertEquals("DIG RHA", tableItem.getData().getFirst().getGroups().get(1).getName());
		Assertions.assertFalse(tableItem.getData().getFirst().getGroups().get(1).isCanWrite());
	}

	@Test
	void findAllFullAscCompany() {
		initSpringSecurityContext("fdaugan");
		final var tableItem = resource.findAll(null, null, null, newUriInfoAsc("company"));

		// 8 from delegate, 7 from my company
		Assertions.assertEquals(15, tableItem.getRecordsTotal());
		Assertions.assertEquals(15, tableItem.getRecordsFiltered());
		Assertions.assertEquals(15, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().getFirst().getId());
	}

	@Test
	void findAllFullDescCompany() {
		final var tableItem = resource.findAll(null, null, null, newUriInfoDesc("company"));
		Assertions.assertEquals(16, tableItem.getRecordsTotal());
		Assertions.assertEquals(16, tableItem.getRecordsFiltered());
		Assertions.assertEquals(16, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("flast0", tableItem.getData().getFirst().getId());
		Assertions.assertEquals("socygan", tableItem.getData().getFirst().getCompany());
		Assertions.assertEquals("fdaugan", tableItem.getData().get(6).getId());
		Assertions.assertEquals("ligoj", tableItem.getData().get(6).getCompany());
	}

	@Test
	void findAllFullAscLastName() {
		initSpringSecurityContext("fdaugan");
		final var tableItem = resource.findAll(null, null, null, newUriInfoAsc("lastName"));

		// 8 from delegate, 7 from my company
		Assertions.assertEquals(15, tableItem.getRecordsTotal());
		Assertions.assertEquals(15, tableItem.getRecordsFiltered());
		Assertions.assertEquals(15, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().get(3).getId());
	}

	@Test
	void findAllMemberDifferentCase() {
		final var tableItem = resource.findAll("LigoJ", "ProductioN", "mmarTIN", newUriInfoAsc("lastName"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("mmartin", tableItem.getData().getFirst().getId());
	}

	/**
	 * No available delegate for the current user -> 0
	 */
	@Test
	void findAllNoRight() {
		initSpringSecurityContext("any");

		final var tableItem = resource.findAll(null, null, null, newUriInfoAsc("id"));
		Assertions.assertEquals(0, tableItem.getRecordsTotal());
		Assertions.assertEquals(0, tableItem.getRecordsFiltered());
		Assertions.assertEquals(0, tableItem.getData().size());
	}

	/**
	 * Whatever the managed company, if the current user sees a group, can
	 * search any user even in a different company this user can manage. <br>
	 */
	@Test
	void findAllNoWrite() {
		initSpringSecurityContext("mlavoine");
		final var tableItem = resource.findAll(null, null, "fdoe2", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().getFirst().getId());
		Assertions.assertFalse(tableItem.getData().getFirst().isCanWrite());

		// Check the groups
		Assertions.assertEquals(1, tableItem.getData().getFirst().getGroups().size());
		Assertions.assertEquals("Biz Agency", tableItem.getData().getFirst().getGroups().getFirst().getName());
		Assertions.assertFalse(tableItem.getData().getFirst().getGroups().getFirst().isCanWrite());
	}

	/**
	 * Add filter by group, but this group does not exist/not visible. No match.
	 */
	@Test
	void findAllFilteredNonExistingGroup() {
		initSpringSecurityContext("fdaugan");
		final var tableItem = resource.findAll(null, "any", null, newUriInfoAsc("id"));
		Assertions.assertEquals(0, tableItem.getRecordsTotal());
	}

	@Test
	void createUserAlreadyExists() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast1");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flast12@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.create(user)), "id", "already-exist");
	}

	@Test
	void deleteUserNoDelegateCompany() {
		initSpringSecurityContext("mmartin");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.delete("flast1")), "id", "read-only");
	}

	@Test
	void deleteLastMember() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.delete("mmartin")), "id", "last-member-of-group");
	}

	@Test
	void deleteUserNoDelegateWriteCompany() {
		initSpringSecurityContext("mtuyer");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.delete("flast1")), "id", "read-only");
	}

	@Test
	void mergeUserNoChange() {
		final UserOrg userLdap2 = getUser().findById("flast1");
		Assertions.assertNull(userLdap2.getDepartment());
		Assertions.assertNull(userLdap2.getLocalId());

		resource.mergeUser(userLdap2, new UserOrg());
		Assertions.assertNull(userLdap2.getDepartment());
		Assertions.assertNull(userLdap2.getLocalId());
	}

	@Test
	void mergeUser() {
		final UserOrg userLdap2 = getUser().findById("flast1");
		Assertions.assertNull(userLdap2.getDepartment());
		Assertions.assertNull(userLdap2.getLocalId());

		final UserOrg newUser = new UserOrg();
		newUser.setDepartment("any");
		newUser.setLocalId("some");
		resource.mergeUser(userLdap2, newUser);
		Assertions.assertEquals("any", userLdap2.getDepartment());
		Assertions.assertEquals("some", userLdap2.getLocalId());

		// Revert to previous state (null)
		resource.mergeUser(userLdap2, new UserOrg());
		Assertions.assertNull(userLdap2.getDepartment());
		Assertions.assertNull(userLdap2.getLocalId());
	}

	/**
	 * Update everything : attributes and mails
	 */
	@Test
	void update() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast1");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(user);
		final var tableItem = resource.findAll(null, null, "flast1", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		final UserOrgVo userLdap = tableItem.getData().getFirst();
		Assertions.assertEquals("flast1", userLdap.getId());
		Assertions.assertEquals("Firsta", userLdap.getFirstName());
		Assertions.assertEquals("Lasta", userLdap.getLastName());
		Assertions.assertEquals("ing", userLdap.getCompany());
		Assertions.assertEquals("flasta@ing.com", userLdap.getMails().getFirst());
		Assertions.assertEquals(1, userLdap.getGroups().size());
		Assertions.assertEquals("DIG RHA", userLdap.getGroups().getFirst().getName());

		// Rollback attributes
		user.setId("flast1");
		user.setFirstName("First1");
		user.setLastName("Last1");
		user.setCompany("ing");
		user.setMail("first1.last1@ing.fr");
		user.setGroups(null);
		resource.update(user);
	}

	@Test
	void updateFirstName() {
		// First name change only
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("jlast3");
		user.setFirstName("John31");
		user.setLastName("Last3");
		user.setCompany("ing");
		user.setMail("john3.last3@ing.com");
		user.setGroups(null);
		initSpringSecurityContext("assist");
		resource.update(user);
		var tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		UserOrgVo userLdap = tableItem.getData().getFirst();
		Assertions.assertEquals("jlast3", userLdap.getId());
		Assertions.assertEquals("John31", userLdap.getFirstName());
		Assertions.assertEquals("Last3", userLdap.getLastName());
		Assertions.assertEquals("ing", userLdap.getCompany());
		Assertions.assertEquals("john3.last3@ing.com", userLdap.getMails().getFirst());
		Assertions.assertEquals(0, userLdap.getGroups().size());
		rollbackUser();
	}

	@Test
	void updateLastName() {
		// Last name change only
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("jlast3");
		user.setFirstName("John31");
		user.setLastName("Last31");
		user.setCompany("ing");
		user.setMail("john3.last3@ing.com");
		user.setGroups(Collections.singleton("DIG RHA"));
		resource.update(user);
		var tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		UserOrgVo userLdap = tableItem.getData().getFirst();
		Assertions.assertEquals("jlast3", userLdap.getId());
		Assertions.assertEquals("John31", userLdap.getFirstName());
		Assertions.assertEquals("Last31", userLdap.getLastName());
		Assertions.assertEquals("ing", userLdap.getCompany());
		Assertions.assertEquals("john3.last3@ing.com", userLdap.getMails().getFirst());
		Assertions.assertEquals(1, userLdap.getGroups().size());
		rollbackUser();
	}

	@Test
	void updateMail() {
		// Mail change only
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("jlast3");
		user.setFirstName("John31");
		user.setLastName("Last31");
		user.setCompany("ing");
		user.setMail("john31.last31@ing.com");
		user.setGroups(Collections.singleton("DIG RHA"));
		resource.update(user);
		var tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		UserOrgVo userLdap = tableItem.getData().getFirst();
		user.setGroups(null);
		Assertions.assertEquals("jlast3", userLdap.getId());
		Assertions.assertEquals("John31", userLdap.getFirstName());
		Assertions.assertEquals("Last31", userLdap.getLastName());
		Assertions.assertEquals("ing", userLdap.getCompany());
		Assertions.assertEquals("john31.last31@ing.com", userLdap.getMails().getFirst());
		Assertions.assertEquals(1, userLdap.getGroups().size());
		rollbackUser();
	}

	@Test
	void updateUserChangeCompanyAndBackAgain() {
		Assertions.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());

		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("First0"); // Unchanged
		user.setLastName("Last0"); // Unchanged
		user.setCompany("ing"); // Previous is "socygan"
		user.setMail("first0.last0@socygan.fr"); // Unchanged
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		initSpringSecurityContext("assist");
		resource.update(user);

		// Check the new DN and company everywhere
		Assertions.assertEquals("uid=flast0,ou=ing,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());
		Assertions.assertEquals("ing", resource.findAll(null, null, "flast0", newUriInfo()).getData().getFirst().getCompany());
		Assertions.assertEquals("ing", getUser().findByIdNoCache("flast0").getCompany());
		Assertions.assertEquals("ing", getUser().findById("flast0").getCompany());

		user.setCompany("socygan"); // Previous is "socygan"
		resource.update(user);

		// Check the old DN and company everywhere
		Assertions.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());
		Assertions.assertEquals("socygan", resource.findAll(null, null, "flast0", newUriInfo()).getData().getFirst().getCompany());
		Assertions.assertEquals("socygan", getUser().findByIdNoCache("flast0").getCompany());
		Assertions.assertEquals("socygan", getUser().findById("flast0").getCompany());
	}

	@Test
	void updateUserChangeDepartmentNotVisible() {
		initSpringSecurityContext("assist");
		Assertions.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());

		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("First0"); // Unchanged
		user.setLastName("Last0"); // Unchanged
		user.setCompany("socygan"); // Unchanged
		user.setDepartment("456987"); // Previous is null -> "DIG AS" (not
										// visible)
		user.setMail("first0.last0@socygan.fr"); // Unchanged
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "group", "unknown-id");
	}

	@Test
	void updateUserChangeDepartmentAndBackAgain() {
		Assertions.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());

		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("First0"); // Unchanged
		user.setLastName("Last0"); // Unchanged
		user.setCompany("socygan"); // Unchanged
		user.setDepartment("456987"); // Previous is null -> "DIG AS"
		user.setMail("first0.last0@socygan.fr"); // Unchanged
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		resource.update(user);

		// Check the new DN and department and group everywhere
		Assertions.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());
		Assertions.assertEquals("456987", resource.findAll(null, null, "flast0", newUriInfo()).getData().getFirst().getDepartment());
		Assertions.assertEquals("456987", getUser().findByIdNoCache("flast0").getDepartment());
		Assertions.assertEquals("456987", getUser().findById("flast0").getDepartment());
		Assertions.assertTrue(getUser().findById("flast0").getGroups().contains("dig as"));
		Assertions.assertTrue(getGroup().findByDepartment("456987").getMembers().contains("flast0"));
		Assertions.assertEquals("DIG AS", getGroup().findByDepartment("456987").getName());
		Assertions.assertTrue(getGroup().findById("dig as").getMembers().contains("flast0"));

		user.setDepartment(null); // Previous is "DIG AS"
		resource.update(user);

		// Check the old DN and department everywhere
		Assertions.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());
		Assertions.assertNull(resource.findAll(null, null, "flast0", newUriInfo()).getData().getFirst().getDepartment());
		Assertions.assertNull(getUser().findByIdNoCache("flast0").getDepartment());
		Assertions.assertNull(getUser().findById("flast0").getDepartment());
		Assertions.assertFalse(getUser().findById("flast0").getGroups().contains("dig as"));
		Assertions.assertFalse(getGroup().findByDepartment("456987").getMembers().contains("flast0"));
		Assertions.assertEquals("DIG AS", getGroup().findByDepartment("456987").getName());
		Assertions.assertFalse(getGroup().findById("dig as").getMembers().contains("flast0"));
	}

	@Test
	void updateUserChangeDepartmentNotExists() {
		Assertions.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());

		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("First0"); // Unchanged
		user.setLastName("Last0"); // Unchanged
		user.setCompany("socygan"); // Unchanged
		user.setDepartment("any"); // Previous is null -> No linked group exists
		user.setMail("first0.last0@socygan.fr"); // Unchanged
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		initSpringSecurityContext("assist");
		resource.update(user);

		// Check the new DN and department and group everywhere
		Assertions.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());
		Assertions.assertEquals("any", resource.findAll(null, null, "flast0", newUriInfo()).getData().getFirst().getDepartment());
		Assertions.assertEquals("any", getUser().findByIdNoCache("flast0").getDepartment());
		Assertions.assertEquals("any", getUser().findById("flast0").getDepartment());
		Assertions.assertNull(getGroup().findByDepartment("any"));

		user.setDepartment(null); // Previous is "any"
		resource.update(user);

		// Check the old DN and department everywhere
		Assertions.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com", getContext("flast0").getDn().toString());
		Assertions.assertNull(resource.findAll(null, null, "flast0", newUriInfo()).getData().getFirst().getDepartment());
		Assertions.assertNull(getUser().findByIdNoCache("flast0").getDepartment());
	}

	@Test
	void updateUserCompanyNotExists() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("any");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserGroupNotExists() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast1");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("any");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "group", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoChange() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("jlast3");
		user.setFirstName("John3");
		user.setLastName("Last3");
		user.setCompany("ing");
		user.setMail("jlast3@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(user);
		final var tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		final UserOrgVo userLdap = tableItem.getData().getFirst();
		Assertions.assertEquals("jlast3", userLdap.getId());
		Assertions.assertEquals("John3", userLdap.getFirstName());
		Assertions.assertEquals("Last3", userLdap.getLastName());
		Assertions.assertEquals("ing", userLdap.getCompany());
		Assertions.assertEquals("jlast3@ing.com", userLdap.getMails().getFirst());
		Assertions.assertEquals(1, userLdap.getGroups().size());
		Assertions.assertEquals("DIG RHA", userLdap.getGroups().getFirst().getName());
	}

	@Test
	void updateUserNoDelegate() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast1");
		user.setFirstName("FirstW");
		user.setLastName("LastW");
		user.setCompany("ing");
		user.setMail("flastw@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateNotVisibleTargetCompany() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("First0");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.last0@socygan.fr");
		final List<String> groups = new ArrayList<>();
		groups.add("Biz Agency");
		user.setGroups(groups);
		initSpringSecurityContext("mlavoine");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoDelegateCompany() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("socygan");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoDelegateCompanyChangeFirstName() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("FirstA");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.last0@socygan.fr");
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoDelegateCompanyChangeMail() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("First0");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.lastA@socygan.fr");
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoDelegateCompanyNoChange() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("First0");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.last0@socygan.fr");
		initSpringSecurityContext("assist");
		resource.update(user);
	}

	@Test
	void updateUserNoDelegateGroupForTarget() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast1");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig sud ouest"); // no right on this group
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "group", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNotExists() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast11");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		initSpringSecurityContext("assist");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "id", BusinessException.KEY_UNKNOWN_ID);
	}

	/**
	 * Add a group to user having already some groups but not visible from the
	 * current user.
	 */
	@Test
	void updateUserAddGroup() {
		// Pre-condition, check the user "wuser", has not yet the group "DIG
		// RHA" we want to be added by "fdaugan"
		initSpringSecurityContext("fdaugan");
		final var initialResultsFromUpdater = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assertions.assertEquals(1, initialResultsFromUpdater.getRecordsTotal());
		Assertions.assertEquals(1, initialResultsFromUpdater.getData().getFirst().getGroups().size());
		Assertions.assertEquals("Biz Agency Manager", initialResultsFromUpdater.getData().getFirst().getGroups().getFirst().getName());

		// Pre-condition, check the user "wuser", has no group visible by
		// "assist"
		initSpringSecurityContext("assist");
		final var assisteResult = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assertions.assertEquals(1, assisteResult.getRecordsTotal());
		Assertions.assertEquals(0, assisteResult.getData().getFirst().getGroups().size());

		// Pre-condition, check the user "wuser", "Biz Agency Manager" is not
		// visible by "mtuyer"
		initSpringSecurityContext("mtuyer");
		final var usersFromOtherGroupManager = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assertions.assertEquals(1, usersFromOtherGroupManager.getRecordsTotal());
		Assertions.assertEquals(0, usersFromOtherGroupManager.getData().getFirst().getGroups().size());

		// Add a new valid group "DIG RHA" to "wuser" by "fdaugan"
		initSpringSecurityContext("fdaugan");
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("wuser");
		user.setFirstName("William");
		user.setLastName("User");
		user.setCompany("ing");
		user.setMail("wuser.wuser@ing.fr");
		final List<String> groups = new ArrayList<>();
		groups.add("DIG RHA");
		groups.add("Biz Agency Manager");
		user.setGroups(groups);
		resource.update(user);

		// Check the group "DIG RHA" is added and
		final var tableItem = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());
		Assertions.assertEquals(2, tableItem.getData().getFirst().getGroups().size());
		Assertions.assertEquals("Biz Agency Manager", tableItem.getData().getFirst().getGroups().getFirst().getName());
		Assertions.assertEquals("DIG RHA", tableItem.getData().getFirst().getGroups().get(1).getName());

		// Check the user "wuser", still has no group visible by "assist"
		initSpringSecurityContext("assist");
		final var assisteResult2 = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assertions.assertEquals(1, assisteResult2.getRecordsTotal());
		Assertions.assertEquals(0, assisteResult2.getData().getFirst().getGroups().size());

		// Check the user "wuser", still has the group "DIG RHA" visible by
		// "mtuyer"
		initSpringSecurityContext("mtuyer");
		final var usersFromOtherGroupManager2 = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assertions.assertEquals(1, usersFromOtherGroupManager2.getRecordsTotal());
		Assertions.assertEquals("DIG RHA", usersFromOtherGroupManager2.getData().getFirst().getGroups().getFirst().getName());

		// Restore the old state
		initSpringSecurityContext("fdaugan");
		final UserOrgEditionVo user2 = new UserOrgEditionVo();
		user2.setId("wuser");
		user2.setFirstName("William");
		user2.setLastName("User");
		user2.setCompany("ing");
		user2.setMail("wuser.wuser@ing.fr");
		final List<String> groups2 = new ArrayList<>();
		groups2.add("Biz Agency Manager");
		user.setGroups(groups2);
		resource.update(user);
		final var initialResultsFromUpdater2 = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assertions.assertEquals(1, initialResultsFromUpdater2.getRecordsTotal());
		Assertions.assertEquals(1, initialResultsFromUpdater2.getData().getFirst().getGroups().size());
		Assertions.assertEquals("Biz Agency Manager", initialResultsFromUpdater2.getData().getFirst().getGroups().getFirst().getName());
	}

	/**
	 * Test user addition to a group this user is already member.
	 */
	@Test
	void addUserToGroup() {
		// Pre condition
		Assertions.assertTrue(resource.findById("wuser").getGroups().contains("Biz Agency Manager"));

		resource.addUserToGroup("wuser", "biz agency manager");

		// Post condition -> no change
		Assertions.assertTrue(resource.findById("wuser").getGroups().contains("Biz Agency Manager"));
	}

	@Test
	void deleteUserNoWriteRight() {
		initSpringSecurityContext("mmartin");
		Assertions.assertEquals(1, resource.findAll(null, null, "wuser", newUriInfo()).getData().size());
		Assertions.assertNotNull(getUser().findByIdNoCache("wuser"));
		Assertions.assertTrue(getGroup().findAll().get("biz agency manager").getMembers().contains("wuser"));
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.delete("wuser")), "id", "read-only");
	}

	@Test
	void deleteUserNotExists() {
		initSpringSecurityContext("assist");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.delete("any")), "id", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateMembership() {
		final UserLdapRepository repository = new UserLdapRepository();
		repository.setGroupLdapRepository(Mockito.mock(GroupLdapRepository.class));
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		final UserOrg user = new UserOrg();
		final Collection<String> oldGroups = new ArrayList<>();
		user.setGroups(oldGroups);
		user.setId("flast1");
		user.setCompany("ing");
		repository.updateMembership(groups, user);
	}

	@Test
	void convertUserRaw() {
		final UserOrg userLdap = getUser().toUser("jdoe5");
		checkRawUser(userLdap);
		Assertions.assertNotNull(userLdap.getGroups());
		Assertions.assertEquals(1, userLdap.getGroups().size());
	}

	@Test
	void convertUserNotExist() {
		final UserOrg userLdap = getUser().toUser("any");
		Assertions.assertNotNull(userLdap);
		Assertions.assertEquals("any", userLdap.getId());
		Assertions.assertNull(userLdap.getCompany());
		Assertions.assertNull(userLdap.getGroups());
		Assertions.assertNull(userLdap.getFirstName());
		Assertions.assertNull(userLdap.getLastName());
		Assertions.assertNull(userLdap.getMails());
	}

	/**
	 * Check a user can see all users from the same company
	 */
	@Test
	void findAllMyCompany() {
		initSpringSecurityContext("mmartin");

		final var tableItem = resource.findAll("ligoj", null, null, newUriInfoAsc("id"));

		// 7 users from company 'ligoj', 0 from delegate
		Assertions.assertEquals(7, tableItem.getRecordsTotal());
		Assertions.assertEquals(7, tableItem.getRecordsFiltered());

		// Check the users
		Assertions.assertEquals("admin-test", tableItem.getData().getFirst().getId());
	}

	/**
	 * When the requested company does not exist, return an empty set.
	 */
	@Test
	void findAllUnknownFilteredCompany() {
		final var tableItem = resource.findAll("any", null, null, newUriInfoAsc("id"));
		Assertions.assertEquals(0, tableItem.getRecordsTotal());
		Assertions.assertEquals(0, tableItem.getRecordsFiltered());
	}

	@Test
	void setIamProviderForTest() {
		// There, for test by other plugin/application
		new UserOrgResource().setIamProvider(new IamProvider[] { Mockito.mock(IamProvider.class) });
	}
}

/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.resource;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.model.ContainerType;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;

/**
 * Test class of {@link GroupResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class GroupLdapResourceTest extends AbstractContainerLdapResourceTest {

	@Autowired
	private GroupResource resource;

	@Autowired
	private UserOrgResource userResource;

	@Test
	void findAll() {
		final var groups = resource.findAll(newUriInfoAscSearch("name", "d"));
		Assertions.assertEquals(5, groups.getRecordsTotal());

		final ContainerCountVo group0 = groups.getData().getFirst();
		Assertions.assertEquals("DIG", group0.getName());
		Assertions.assertEquals(0, group0.getCount());
		Assertions.assertEquals(0, group0.getCountVisible());
		Assertions.assertTrue(group0.isCanAdmin());
		Assertions.assertTrue(group0.isCanWrite());
		Assertions.assertEquals("Fonction", group0.getScope());
		Assertions.assertEquals("dig", group0.getId());
		Assertions.assertFalse(group0.isLocked());

		final ContainerCountVo group10 = groups.getData().get(3);
		Assertions.assertEquals("DIG RHA", group10.getName());
		Assertions.assertEquals(4, group10.getCount());
		Assertions.assertEquals(4, group10.getCountVisible());
		Assertions.assertTrue(group10.isCanAdmin());
		Assertions.assertTrue(group10.isCanWrite());
		Assertions.assertEquals("Fonction", group10.getScope());
		Assertions.assertEquals(ContainerType.GROUP, group10.getContainerType());
		Assertions.assertFalse(group10.isLocked());

		// No group type case
		final ContainerCountVo group20 = groups.getData().get(4);
		Assertions.assertEquals("Production", group20.getName());
		Assertions.assertEquals(1, group20.getCount());
		Assertions.assertEquals(1, group20.getCountVisible());
		Assertions.assertTrue(group20.isCanAdmin());
		Assertions.assertTrue(group20.isCanWrite());
		Assertions.assertNull(group20.getScope());
		Assertions.assertEquals(ContainerType.GROUP, group20.getContainerType());
		Assertions.assertFalse(group20.isLocked());
	}

	@Test
	void findAll2() {
		final var groups = resource.findAll(newUriInfoAscSearch("name", "sea-octopus"));
		Assertions.assertEquals(1, groups.getRecordsTotal());

		final ContainerCountVo group0 = groups.getData().getFirst();
		Assertions.assertEquals("sea-octopus", group0.getName());
		Assertions.assertEquals(0, group0.getCount());
		Assertions.assertEquals(0, group0.getCountVisible());
		Assertions.assertTrue(group0.isCanAdmin());
		Assertions.assertTrue(group0.isCanWrite());
		Assertions.assertEquals("Project", group0.getScope());
		Assertions.assertEquals("sea-octopus", group0.getId());
		Assertions.assertTrue(group0.isLocked());
	}

	@Test
	void findAllDescNoCriteria() {
		final var groups = resource.findAll(newUriInfoDesc("name"));
		Assertions.assertTrue(groups.getRecordsTotal() >= 16);

		// No group type case
		final ContainerCountVo group0 = groups.getData().getFirst();
		Assertions.assertEquals("VigiReport", group0.getName());
	}

	@Test
	void findAllNotExistingGroup() {
		initSpringSecurityContext("any");
		final var groups = resource.findAll(newUriInfoAscSearch("name", "d"));
		Assertions.assertEquals(0, groups.getRecordsTotal());
	}

	/**
	 * User "mmartin" is member of group "Production", so can see this group and
	 * all subgroups. Including the 7 groups "Hub *". These groups are only
	 * visible because of the membership of group "Production" and does not
	 * involve delegates.
	 */
	@Test
	void findAllFromMembership() {
		initSpringSecurityContext("mmartin");
		final var groups = resource.findAll(newUriInfoAscSearch("name", "hub"));
		Assertions.assertEquals(2, groups.getRecordsTotal());
		Assertions.assertEquals("Hub France", groups.getData().getFirst().getName());
		Assertions.assertEquals("Hub Paris", groups.getData().get(1).getName());
	}

	/**
	 * User "jlast3" has no delegate and is not member of a group owning any
	 * "Hub *" group.
	 */
	@Test
	void findAllNoRight() {
		initSpringSecurityContext("jlast3");
		final var groups = resource.findAll(newUriInfoAscSearch("name", "hub"));
		Assertions.assertEquals(0, groups.getRecordsTotal());
	}

	@Test
	void findAllLimitedRights() {
		initSpringSecurityContext("mmartin");
		final var groups = resource.findAll(newUriInfoAscSearch("name", "dig as"));
		Assertions.assertEquals(1, groups.getRecordsTotal());

		final ContainerCountVo group0 = groups.getData().getFirst();
		Assertions.assertEquals("DIG AS", group0.getName());
		Assertions.assertEquals(1, group0.getCount());
		Assertions.assertEquals(1, group0.getCountVisible());
		Assertions.assertFalse(group0.isCanAdmin());
		Assertions.assertFalse(group0.isCanWrite());
		Assertions.assertEquals("Fonction", group0.getScope());
	}

	@Test
	void findByNameNoType() {
		final ContainerWithScopeVo group = resource.findByName("business solution");
		Assertions.assertEquals("Business Solution", group.getName());
		Assertions.assertNull(group.getScope());
	}

	@Test
	void findByNameNotExistingGroup() {
		Assertions.assertNull(resource.findByName("any"));
	}

	@Test
	void findById() {
		final ContainerWithScopeVo group = resource.findByName("dig as");
		Assertions.assertEquals("DIG AS", group.getName());
		Assertions.assertEquals("Fonction", group.getScope());
	}

	@Test
	void findByIdNotExists() {
		Assertions.assertNull(resource.findById("any"));
	}

	/**
	 * There is a delegate of "business solution" for this user, but the user
	 * does not exist anymore.
	 */
	@Test
	void findByIdUserNoRight() {
		initSpringSecurityContext("assist");
		Assertions.assertNull(resource.findById("business solution"));
	}

	@Test
	void findByNameNoRight() {
		initSpringSecurityContext("any");
		Assertions.assertNull(resource.findByName("dig as"));
	}

	@Test
	void findByNameLimitedRights() {
		initSpringSecurityContext("mmartin");
		final ContainerWithScopeVo group = resource.findByName("dig as");
		Assertions.assertEquals("DIG AS", group.getName());
		Assertions.assertEquals("Fonction", group.getScope());
	}

	@Test
	void exists() {
		initSpringSecurityContext("mmartin");
		Assertions.assertTrue(resource.exists("dig as"));
	}

	@Test
	void existsNot() {
		Assertions.assertFalse(resource.exists("any"));
	}

	@Test
	void createNoWriteRight() {
		final ContainerScope typeLdap = containerScopeRepository.findByName("Fonction");
		final GroupEditionVo group = new GroupEditionVo();
		group.setName("New-Ax-1-z:Z 0");
		group.setScope(typeLdap.getId());
		initSpringSecurityContext("mmartin");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.create(group)), "name", "read-only");
	}

	@Test
	void createAlreadyExists() {
		final ContainerScope scope = containerScopeRepository.findByName("Fonction");
		final GroupEditionVo group = new GroupEditionVo();
		group.setName("DIG");
		group.setScope(scope.getId());
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.create(group)), "name", "already-exist");
	}

	@Test
	void createInvalidParent() {
		final GroupEditionVo group = new GroupEditionVo();
		group.setParent("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> createInternal(group, null)), "group", "unknown-id");
	}

	@Test
	void createInvalidAssistant() {
		final GroupEditionVo group = new GroupEditionVo();
		group.setAssistants(Collections.singletonList("any"));
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> createInternal(group, null)), "id", "unknown-id");
	}

	@Test
	void createInvalidOwner() {
		final GroupEditionVo group = new GroupEditionVo();
		group.setOwners(Collections.singletonList("any"));
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> createInternal(group, null)), "id", "unknown-id");
	}

	@Test
	void createInvalidTypeOfParent() {
		final GroupEditionVo group = new GroupEditionVo();
		group.setDepartments(Collections.singletonList("SOME"));
		group.setOwners(Collections.singletonList("fdaugan"));
		group.setAssistants(Collections.singletonList("wuser"));
		group.setParent("Jira");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> createInternal(group, null)), "parent", "container-parent-type-match");
	}

	@Test
	void createEmptyDeleteWithParent() {
		final GroupEditionVo group = new GroupEditionVo();
		group.setDepartments(Collections.singletonList("SOME"));
		group.setOwners(Collections.singletonList("fdaugan"));
		group.setAssistants(Collections.singletonList("wuser"));
		group.setParent("DIG");

		createInternal(group, "cn=new-ax-1-z:z 0,cn=dig,ou=fonction,ou=groups,dc=sample,dc=com");

		// Check the group and its attributes
		final AndFilter filter = new AndFilter();
		filter.and(new EqualsFilter("objectClass", "groupOfUniqueNames"));
		filter.and(new EqualsFilter("cn", "New-Ax-1-z:Z 0"));
		final DirContextAdapter contextAdapter = getTemplate()
				.search("cn=DIG,ou=fonction,ou=groups,dc=sample,dc=com", filter.encode(), (Object ctx) -> (DirContextAdapter) ctx).getFirst();
		Assertions.assertEquals("uid=wuser,ou=ing,ou=external,ou=people,dc=sample,dc=com", contextAdapter.getObjectAttribute("seeAlso"));
		Assertions.assertEquals("SOME", contextAdapter.getStringAttribute("businessCategory"));
		Assertions.assertEquals("uid=fdaugan,ou=ligoj,ou=france,ou=people,dc=sample,dc=com", contextAdapter.getStringAttribute("owner"));

		userResource.addUserToGroup("wuser", "New-Ax-1-z:Z 0");

		// Pre check
		final var groups = resource.findAll(newUriInfoAscSearch("name", "New-Ax-1-z:Z 0"));
		Assertions.assertEquals(1, groups.getRecordsTotal());
		Assertions.assertEquals(1, groups.getData().getFirst().getCount());

		resource.empty("New-Ax-1-z:Z 0");

		// Post check
		final var groupsEmpty = resource.findAll(newUriInfoAscSearch("name", "New-Ax-1-z:Z 0"));
		Assertions.assertEquals(1, groupsEmpty.getRecordsTotal());
		Assertions.assertEquals(0, groupsEmpty.getData().getFirst().getCount());

		resource.delete("New-Ax-1-z:Z 0");

		// Post check
		final var groupsDelete = resource.findAll(newUriInfoAscSearch("name", "New-Ax-1-z:Z 0"));
		Assertions.assertEquals(0, groupsDelete.getRecordsTotal());
	}

	@Test
	void createEmptyDelete() {
		final GroupEditionVo group = new GroupEditionVo();
		createInternal(group, "cn=new-ax-1-z:z 0,ou=fonction,ou=groups,dc=sample,dc=com");

		userResource.addUserToGroup("wuser", "New-Ax-1-z:Z 0");

		// Pre check
		final var groups = resource.findAll(newUriInfoAscSearch("name", "New-Ax-1-z:Z 0"));
		Assertions.assertEquals(1, groups.getRecordsTotal());
		Assertions.assertEquals(1, groups.getData().getFirst().getCount());

		resource.empty("New-Ax-1-z:Z 0");

		// Post check
		final var groupsEmpty = resource.findAll(newUriInfoAscSearch("name", "New-Ax-1-z:Z 0"));
		Assertions.assertEquals(1, groupsEmpty.getRecordsTotal());
		Assertions.assertEquals(0, groupsEmpty.getData().getFirst().getCount());

		resource.delete("New-Ax-1-z:Z 0");

		// Post check
		final var groupsDelete = resource.findAll(newUriInfoAscSearch("name", "New-Ax-1-z:Z 0"));
		Assertions.assertEquals(0, groupsDelete.getRecordsTotal());
	}

	private void createInternal(final GroupEditionVo group, final String expected) {
		final ContainerScope typeLdap = containerScopeRepository.findByName("Fonction");
		group.setName("New-Ax-1-z:Z 0");
		group.setScope(typeLdap.getId());
		resource.create(group);

		// Check the creation from cache
		final var groups = resource.findAll(newUriInfoAscSearch("name", "ew-Ax"));
		Assertions.assertEquals(1, groups.getRecordsTotal());

		final ContainerCountVo group0 = groups.getData().getFirst();
		Assertions.assertEquals("New-Ax-1-z:Z 0", group0.getName());
		Assertions.assertEquals(0, group0.getCount());
		Assertions.assertEquals(0, group0.getCountVisible());
		Assertions.assertTrue(group0.isCanAdmin());
		Assertions.assertTrue(group0.isCanWrite());
		Assertions.assertEquals("Fonction", group0.getScope());

		// Check the creation from LDAP
		final GroupOrg groupLdap = getGroup().findAllNoCache().get("new-ax-1-z:z 0");
		Assertions.assertNotNull(groupLdap);
		Assertions.assertEquals("new-ax-1-z:z 0", groupLdap.getId());
		Assertions.assertEquals("New-Ax-1-z:Z 0", groupLdap.getName());
		Assertions.assertEquals(expected, groupLdap.getDn());

		// Dummy group is there
		Assertions.assertEquals(1, groupLdap.getMembers().size());

		// For coverage
		Assertions.assertEquals("new-ax-1-z:z 0".hashCode(), groupLdap.hashCode());
		Assertions.assertEquals(groupLdap, groupLdap);
		Assertions.assertEquals(groupLdap, new GroupOrg("any", "New-AX-1-Z:Z 0", null));
		Assertions.assertNotEquals(groupLdap, new GroupOrg("any", "some", null));
		Assertions.assertNotEquals(groupLdap, new CompanyOrg("any", "some"));
	}

	@Test
	void deleteNotExists() {
		initSpringSecurityContext("mmartin");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.delete("Any")), "group", "unknown-id");
	}

	@Test
	void deleteNoRight() {
		initSpringSecurityContext("mmartin");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.delete("dig rha")), "group", "unknown-id");
	}

	@Test
	void emptyNotExists() {
		initSpringSecurityContext("mmartin");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.empty("Any")), "group", "unknown-id");
	}

	@Test
	void emptyNoRight() {
		initSpringSecurityContext("mmartin");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.empty("dig rha")), "group", "unknown-id");
	}

	/**
	 * Check managed group is filtered against available groups for write.
	 */
	@Test
	void getContainersForWrite() {
		initSpringSecurityContext("mlavoine");
		final TableItem<String> managed = resource.getContainersForWrite(newUriInfo());
		Assertions.assertEquals(0, managed.getRecordsFiltered());
		Assertions.assertEquals(0, managed.getRecordsTotal());
		Assertions.assertEquals(0, managed.getData().size());
	}

	/**
	 * Check managed group is filtered against available groups for
	 * administration.
	 */
	@Test
	void getContainersForAdmin() {
		initSpringSecurityContext("mtuyer");
		final TableItem<String> managed = resource.getContainersForAdmin(newUriInfo());

		// This user can see 4 groups from the direct admin delegates to him
		Assertions.assertEquals(4, managed.getRecordsFiltered());
		Assertions.assertEquals(4, managed.getRecordsTotal());
		Assertions.assertEquals(4, managed.getData().size());
	}

	/**
	 * Check managed group is filtered against available groups for
	 * administration.
	 */
	@Test
	void getContainersForAdminNoRight() {
		initSpringSecurityContext("mlavoine");
		final TableItem<String> managed = resource.getContainersForAdmin(newUriInfo());
		Assertions.assertEquals(0, managed.getRecordsFiltered());
		Assertions.assertEquals(0, managed.getRecordsTotal());
		Assertions.assertEquals(0, managed.getData().size());
	}

	@Test
	void getContainersDelegateTreeExactParentDn() {
		initSpringSecurityContext("mlavoine");
		final TableItem<String> managed = resource.getContainers(newUriInfo());
		Assertions.assertEquals(4, managed.getRecordsFiltered());
		Assertions.assertEquals(4, managed.getRecordsTotal());
		Assertions.assertEquals(4, managed.getData().size());

		// Brought by a delegate of "cn=biz agency,ou=tools,dc=sample,dc=com" to
		// company user "mlavoine"
		Assertions.assertTrue(managed.getData().contains("Biz Agency"));
		Assertions.assertTrue(managed.getData().contains("Biz Agency Manager"));

		// Brought by a delegate of "Business Solution" to company "ing"
		Assertions.assertTrue(managed.getData().contains("Business Solution"));
		Assertions.assertTrue(managed.getData().contains("Sub Business Solution"));
	}

	@Test
	void getContainersDelegateTreeSubParentDn() {
		initSpringSecurityContext("mtuyer");
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "100");
		final TableItem<String> managed = resource.getContainers(uriInfo);
		Assertions.assertEquals(6, managed.getRecordsFiltered());
		Assertions.assertEquals(6, managed.getRecordsTotal());
		Assertions.assertEquals(6, managed.getData().size());

		// Brought by a delegate of "ou=fonction,ou=groups,dc=sample,dc=com" to
		// company user "mtuyer"
		Assertions.assertTrue(managed.getData().contains("DIG AS"));
		Assertions.assertTrue(managed.getData().contains("DIG"));

		// Brought by a delegate of "Business Solution" to company "ing"
		Assertions.assertTrue(managed.getData().contains("Business Solution"));
		Assertions.assertTrue(managed.getData().contains("Sub Business Solution"));
	}

	@Test
	void getContainersNoDelegate() {
		initSpringSecurityContext("any");
		final TableItem<String> managed = resource.getContainers(newUriInfo());
		Assertions.assertEquals(0, managed.getRecordsFiltered());
		Assertions.assertEquals(0, managed.getRecordsTotal());
		Assertions.assertEquals(0, managed.getData().size());
	}

	@Test
	void getContainersDelegateGroup() {
		initSpringSecurityContext("someone");
		final TableItem<String> managed = resource.getContainers(newUriInfo());
		Assertions.assertEquals(1, managed.getRecordsFiltered());
		Assertions.assertEquals(1, managed.getRecordsTotal());
		Assertions.assertEquals(1, managed.getData().size());
		Assertions.assertTrue(managed.getData().contains("DIG RHA"));
	}

	/**
	 * flast1 is member of company "ing".<br>
	 * There is a delegation to members of company "ing" to see the group
	 * "business solution", and its subgroup "Sub Business Solution"
	 */
	@Test
	void findAllUsingDelegateReceiverCompany() {
		initSpringSecurityContext("flast1");
		final var tableItem = resource.findAll(newUriInfoAsc("id"));
		Assertions.assertEquals(2, tableItem.getRecordsTotal());
		Assertions.assertEquals(2, tableItem.getRecordsFiltered());
		Assertions.assertEquals(2, tableItem.getData().size());

		// Check the groups "Business Solution"
		Assertions.assertEquals("Business Solution", tableItem.getData().getFirst().getName());
		Assertions.assertEquals("Sub Business Solution", tableItem.getData().get(1).getName());

		// Check the groups
		Assertions.assertEquals(0, tableItem.getData().getFirst().getCountVisible());
		Assertions.assertEquals(1, tableItem.getData().getFirst().getCount());
	}

}

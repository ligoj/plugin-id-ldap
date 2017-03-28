package org.ligoj.app.plugin.id.resource;

import java.util.Collections;

import javax.transaction.Transactional;
import javax.ws.rs.core.UriInfo;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.ligoj.app.api.CompanyOrg;
import org.ligoj.app.api.GroupOrg;
import org.ligoj.app.model.ContainerType;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Test class of {@link GroupResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
@org.junit.FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GroupLdapResourceTest extends AbstractContainerLdapResourceTest {

	@Autowired
	private GroupResource resource;

	@Autowired
	private UserOrgResource userResource;

	@Test
	public void findAll() {
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "d"));
		Assert.assertEquals(6, groups.getRecordsTotal());

		final ContainerCountVo group0 = groups.getData().get(0);
		Assert.assertEquals("DIG", group0.getName());
		Assert.assertEquals(0, group0.getCount());
		Assert.assertEquals(0, group0.getCountVisible());
		Assert.assertTrue(group0.isCanAdmin());
		Assert.assertTrue(group0.isCanWrite());
		Assert.assertEquals("Fonction", group0.getScope());
		Assert.assertEquals("dig", group0.getId());
		Assert.assertFalse(group0.isLocked());

		final ContainerCountVo group10 = groups.getData().get(3);
		Assert.assertEquals("DIG RHA", group10.getName());
		Assert.assertEquals(4, group10.getCount());
		Assert.assertEquals(4, group10.getCountVisible());
		Assert.assertTrue(group10.isCanAdmin());
		Assert.assertTrue(group10.isCanWrite());
		Assert.assertEquals("Fonction", group10.getScope());
		Assert.assertEquals(ContainerType.GROUP, group10.getContainerType());
		Assert.assertFalse(group10.isLocked());

		// No group type case
		final ContainerCountVo group20 = groups.getData().get(4);
		Assert.assertEquals("Production", group20.getName());
		Assert.assertEquals(1, group20.getCount());
		Assert.assertEquals(1, group20.getCountVisible());
		Assert.assertTrue(group20.isCanAdmin());
		Assert.assertTrue(group20.isCanWrite());
		Assert.assertNull(group20.getScope());
		Assert.assertEquals(ContainerType.GROUP, group20.getContainerType());
		Assert.assertFalse(group20.isLocked());
	}

	@Test
	public void findAll2() {
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "sea-octopus"));
		Assert.assertEquals(1, groups.getRecordsTotal());

		final ContainerCountVo group0 = groups.getData().get(0);
		Assert.assertEquals("sea-octopus", group0.getName());
		Assert.assertEquals(0, group0.getCount());
		Assert.assertEquals(0, group0.getCountVisible());
		Assert.assertTrue(group0.isCanAdmin());
		Assert.assertTrue(group0.isCanWrite());
		Assert.assertEquals("Project", group0.getScope());
		Assert.assertEquals("sea-octopus", group0.getId());
		Assert.assertTrue(group0.isLocked());
	}

	@Test
	public void findAllDescNoCriteria() {
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoDesc("name"));
		Assert.assertTrue(groups.getRecordsTotal() >= 16);

		// No group type case
		final ContainerCountVo group0 = groups.getData().get(0);
		Assert.assertEquals("VigiReport", group0.getName());
	}

	@Test
	public void findAllNotExistingGroup() {
		initSpringSecurityContext("any");
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "d"));
		Assert.assertEquals(0, groups.getRecordsTotal());
	}

	/**
	 * User "mmartin" is member of group "Production", so can see this group and all sub-groups. Including the 7 groups
	 * "Hub *".
	 * These groups are only visible because of the membership of group "Production" and does not involve delegates.
	 */
	@Test
	public void findAllFromMembership() {
		initSpringSecurityContext("mmartin");
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "hub"));
		Assert.assertEquals(2, groups.getRecordsTotal());
		Assert.assertEquals("Hub France", groups.getData().get(0).getName());
		Assert.assertEquals("Hub Paris", groups.getData().get(1).getName());
	}

	/**
	 * User "jlast3" has no delegate and is not member of a group owning any "Hub *" group.
	 */
	@Test
	public void findAllNoRight() {
		initSpringSecurityContext("jlast3");
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "hub"));
		Assert.assertEquals(0, groups.getRecordsTotal());
	}

	@Test
	public void findAllLimitedRights() {
		initSpringSecurityContext("mmartin");
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "dig as"));
		Assert.assertEquals(1, groups.getRecordsTotal());

		final ContainerCountVo group0 = groups.getData().get(0);
		Assert.assertEquals("DIG AS", group0.getName());
		Assert.assertEquals(1, group0.getCount());
		Assert.assertEquals(1, group0.getCountVisible());
		Assert.assertFalse(group0.isCanAdmin());
		Assert.assertFalse(group0.isCanWrite());
		Assert.assertEquals("Fonction", group0.getScope());
	}

	@Test
	public void findByNameNoType() {
		final ContainerWithScopeVo group = resource.findByName("business solution");
		Assert.assertEquals("Business Solution", group.getName());
		Assert.assertNull(group.getScope());
	}

	@Test
	public void findByNameNotExistingGroup() {
		Assert.assertNull(resource.findByName("any"));
	}

	@Test
	public void findById() {
		final ContainerWithScopeVo group = resource.findByName("dig as");
		Assert.assertEquals("DIG AS", group.getName());
		Assert.assertEquals("Fonction", group.getScope());
	}

	@Test
	public void findByIdNotExists() {
		Assert.assertNull(resource.findById("any"));
	}

	/**
	 * There is a delegate of "business solution" for this user, but the user does not exist anymore.
	 */
	@Test
	public void findByIdUserNoRight() {
		initSpringSecurityContext("assist");
		Assert.assertNull(resource.findById("business solution"));
	}

	@Test
	public void findByNameNoRight() {
		initSpringSecurityContext("any");
		Assert.assertNull(resource.findByName("dig as"));
	}

	@Test
	public void findByNameLimitedRights() {
		initSpringSecurityContext("mmartin");
		final ContainerWithScopeVo group = resource.findByName("dig as");
		Assert.assertEquals("DIG AS", group.getName());
		Assert.assertEquals("Fonction", group.getScope());
	}

	@Test
	public void exists() {
		initSpringSecurityContext("mmartin");
		Assert.assertTrue(resource.exists("dig as"));
	}

	@Test
	public void existsNot() {
		Assert.assertFalse(resource.exists("any"));
	}

	@Test(expected = ValidationJsonException.class)
	public void createNoRight() {
		final ContainerScope typeLdap = containerScopeRepository.findByName("Fonction");
		final GroupEditionVo group = new GroupEditionVo();
		group.setName("New-Ax-1-z:Z 0");
		group.setType(typeLdap.getId());
		initSpringSecurityContext("mmartin");
		resource.create(group);
	}

	@Test(expected = ValidationJsonException.class)
	public void createAlreadyExists() {
		final ContainerScope scope = containerScopeRepository.findByName("Fonction");
		final GroupEditionVo group = new GroupEditionVo();
		group.setName("DIG");
		group.setType(scope.getId());
		resource.create(group);
	}

	@Test(expected = ValidationJsonException.class)
	public void createInvalidParent() {
		final GroupEditionVo group = new GroupEditionVo();
		group.setParent("any");
		createInternal(group, null);
	}

	@Test(expected = ValidationJsonException.class)
	public void createInvalidAssistant() {
		final GroupEditionVo group = new GroupEditionVo();
		group.setAssistants(Collections.singletonList("any"));
		createInternal(group, null);
	}

	@Test(expected = ValidationJsonException.class)
	public void createInvalidOwner() {
		final GroupEditionVo group = new GroupEditionVo();
		group.setOwners(Collections.singletonList("any"));
		createInternal(group, null);
	}

	@Test(expected = ValidationJsonException.class)
	public void createInvalidTypeOfParent() {
		final GroupEditionVo group = new GroupEditionVo();
		group.setDepartments(Collections.singletonList("SOME"));
		group.setOwners(Collections.singletonList("fdaugan"));
		group.setAssistants(Collections.singletonList("wuser"));
		group.setParent("Jira");

		createInternal(group, null);
	}

	@Test
	public void createEmptyDeleteWithParent() {
		final GroupEditionVo group = new GroupEditionVo();
		group.setDepartments(Collections.singletonList("SOME"));
		group.setOwners(Collections.singletonList("fdaugan"));
		group.setAssistants(Collections.singletonList("wuser"));
		group.setParent("DIG");

		createInternal(group, "cn=new-ax-1-z:z 0,cn=dig,ou=fonction,ou=groups,dc=sample,dc=com");

		// Check the group and its attributes
		final AndFilter filter = new AndFilter();
		filter.and(new EqualsFilter("objectclass", "groupOfUniqueNames"));
		filter.and(new EqualsFilter("cn", "New-Ax-1-z:Z 0"));
		final DirContextAdapter contextAdapter = getTemplate()
				.search("cn=DIG,ou=fonction,ou=groups,dc=sample,dc=com", filter.encode(), (Object ctx) -> (DirContextAdapter) ctx).get(0);
		Assert.assertEquals("uid=wuser,ou=ing,ou=external,ou=people,dc=sample,dc=com", contextAdapter.getObjectAttribute("seeAlso"));
		Assert.assertEquals("SOME", contextAdapter.getStringAttribute("businessCategory"));
		Assert.assertEquals("uid=fdaugan,ou=gfi,ou=france,ou=people,dc=sample,dc=com", contextAdapter.getStringAttribute("owner"));

		userResource.addUserToGroup("wuser", "New-Ax-1-z:Z 0");

		// Pre check
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "New-Ax-1-z:Z 0"));
		Assert.assertEquals(1, groups.getRecordsTotal());
		Assert.assertEquals(1, groups.getData().get(0).getCount());

		resource.empty("New-Ax-1-z:Z 0");

		// Post check
		final TableItem<ContainerCountVo> groupsEmpty = resource.findAll(newUriInfoAscSearch("name", "New-Ax-1-z:Z 0"));
		Assert.assertEquals(1, groupsEmpty.getRecordsTotal());
		Assert.assertEquals(0, groupsEmpty.getData().get(0).getCount());

		resource.delete("New-Ax-1-z:Z 0");

		// Post check
		final TableItem<ContainerCountVo> groupsDelete = resource.findAll(newUriInfoAscSearch("name", "New-Ax-1-z:Z 0"));
		Assert.assertEquals(0, groupsDelete.getRecordsTotal());
	}

	@Test
	public void createEmptyDelete() {
		final GroupEditionVo group = new GroupEditionVo();
		createInternal(group, "cn=new-ax-1-z:z 0,ou=fonction,ou=groups,dc=sample,dc=com");

		userResource.addUserToGroup("wuser", "New-Ax-1-z:Z 0");

		// Pre check
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "New-Ax-1-z:Z 0"));
		Assert.assertEquals(1, groups.getRecordsTotal());
		Assert.assertEquals(1, groups.getData().get(0).getCount());

		resource.empty("New-Ax-1-z:Z 0");

		// Post check
		final TableItem<ContainerCountVo> groupsEmpty = resource.findAll(newUriInfoAscSearch("name", "New-Ax-1-z:Z 0"));
		Assert.assertEquals(1, groupsEmpty.getRecordsTotal());
		Assert.assertEquals(0, groupsEmpty.getData().get(0).getCount());

		resource.delete("New-Ax-1-z:Z 0");

		// Post check
		final TableItem<ContainerCountVo> groupsDelete = resource.findAll(newUriInfoAscSearch("name", "New-Ax-1-z:Z 0"));
		Assert.assertEquals(0, groupsDelete.getRecordsTotal());
	}

	private void createInternal(final GroupEditionVo group, final String expected) {
		final ContainerScope typeLdap = containerScopeRepository.findByName("Fonction");
		group.setName("New-Ax-1-z:Z 0");
		group.setType(typeLdap.getId());
		resource.create(group);

		// Check the creation from cache
		final TableItem<ContainerCountVo> groups = resource.findAll(newUriInfoAscSearch("name", "ew-Ax"));
		Assert.assertEquals(1, groups.getRecordsTotal());

		final ContainerCountVo group0 = groups.getData().get(0);
		Assert.assertEquals("New-Ax-1-z:Z 0", group0.getName());
		Assert.assertEquals(0, group0.getCount());
		Assert.assertEquals(0, group0.getCountVisible());
		Assert.assertTrue(group0.isCanAdmin());
		Assert.assertTrue(group0.isCanWrite());
		Assert.assertEquals("Fonction", group0.getScope());

		// Check the creation from LDAP
		final GroupOrg groupLdap = getGroup().findAllNoCache().get("new-ax-1-z:z 0");
		Assert.assertNotNull(groupLdap);
		Assert.assertEquals("new-ax-1-z:z 0", groupLdap.getId());
		Assert.assertEquals("New-Ax-1-z:Z 0", groupLdap.getName());
		Assert.assertEquals(expected, groupLdap.getDn());

		// Dummy group is there
		Assert.assertEquals(1, groupLdap.getMembers().size());

		// For coverage
		Assert.assertEquals("new-ax-1-z:z 0".hashCode(), groupLdap.hashCode());
		Assert.assertEquals(groupLdap, groupLdap);
		Assert.assertEquals(groupLdap, new GroupOrg("any", "New-AX-1-Z:Z 0", null));
		Assert.assertNotEquals(groupLdap, new GroupOrg("any", "some", null));
		Assert.assertNotEquals(groupLdap, new CompanyOrg("any", "some"));
	}

	@Test(expected = ValidationJsonException.class)
	public void deleteNotExists() {
		initSpringSecurityContext("mmartin");
		resource.delete("Any");
	}

	@Test(expected = ValidationJsonException.class)
	public void deleteNoRight() {
		initSpringSecurityContext("mmartin");
		resource.delete("dig rha");
	}

	@Test(expected = ValidationJsonException.class)
	public void emptyNotExists() {
		initSpringSecurityContext("mmartin");
		resource.empty("Any");
	}

	@Test(expected = ValidationJsonException.class)
	public void emptyNoRight() {
		initSpringSecurityContext("mmartin");
		resource.empty("dig rha");
	}

	/**
	 * Check managed group is filtered against available groups for write.
	 */
	@Test
	public void getContainersForWrite() {
		initSpringSecurityContext("mlavoine");
		final TableItem<String> managed = resource.getContainersForWrite(newUriInfo());
		Assert.assertEquals(0, managed.getRecordsFiltered());
		Assert.assertEquals(0, managed.getRecordsTotal());
		Assert.assertEquals(0, managed.getData().size());
	}

	/**
	 * Check managed group is filtered against available groups for administration.
	 */
	@Test
	public void getContainersForAdmin() {
		initSpringSecurityContext("mtuyer");
		final TableItem<String> managed = resource.getContainersForAdmin(newUriInfo());

		// This user can see 4 groups from the direct admin delegates to him
		Assert.assertEquals(4, managed.getRecordsFiltered());
		Assert.assertEquals(4, managed.getRecordsTotal());
		Assert.assertEquals(4, managed.getData().size());
	}

	/**
	 * Check managed group is filtered against available groups for administration.
	 */
	@Test
	public void getContainersForAdminNoRight() {
		initSpringSecurityContext("mlavoine");
		final TableItem<String> managed = resource.getContainersForAdmin(newUriInfo());
		Assert.assertEquals(0, managed.getRecordsFiltered());
		Assert.assertEquals(0, managed.getRecordsTotal());
		Assert.assertEquals(0, managed.getData().size());
	}

	@Test
	public void getContainersDelegateTreeExactParentDn() {
		initSpringSecurityContext("mlavoine");
		final TableItem<String> managed = resource.getContainers(newUriInfo());
		Assert.assertEquals(4, managed.getRecordsFiltered());
		Assert.assertEquals(4, managed.getRecordsTotal());
		Assert.assertEquals(4, managed.getData().size());

		// Brought by a delegate of "cn=biz agency,ou=tools,dc=sample,dc=com" to company user "mlavoine"
		Assert.assertTrue(managed.getData().contains("Biz Agency"));
		Assert.assertTrue(managed.getData().contains("Biz Agency Manager"));

		// Brought by a delegate of "Business Solution" to company "ing"
		Assert.assertTrue(managed.getData().contains("Business Solution"));
		Assert.assertTrue(managed.getData().contains("Sub Business Solution"));
	}

	@Test
	public void getContainersDelegateTreeSubParentDn() {
		initSpringSecurityContext("mtuyer");
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "100");
		final TableItem<String> managed = resource.getContainers(uriInfo);
		Assert.assertEquals(6, managed.getRecordsFiltered());
		Assert.assertEquals(6, managed.getRecordsTotal());
		Assert.assertEquals(6, managed.getData().size());

		// Brought by a delegate of "ou=fonction,ou=groups,dc=sample,dc=com" to company user "mtuyer"
		Assert.assertTrue(managed.getData().contains("DIG AS"));
		Assert.assertTrue(managed.getData().contains("DIG"));

		// Brought by a delegate of "Business Solution" to company "ing"
		Assert.assertTrue(managed.getData().contains("Business Solution"));
		Assert.assertTrue(managed.getData().contains("Sub Business Solution"));
	}

	@Test
	public void getContainersNoDelegate() {
		initSpringSecurityContext("any");
		final TableItem<String> managed = resource.getContainers(newUriInfo());
		Assert.assertEquals(0, managed.getRecordsFiltered());
		Assert.assertEquals(0, managed.getRecordsTotal());
		Assert.assertEquals(0, managed.getData().size());
	}

	@Test
	public void getContainersDelegateGroup() {
		initSpringSecurityContext("someone");
		final TableItem<String> managed = resource.getContainers(newUriInfo());
		Assert.assertEquals(1, managed.getRecordsFiltered());
		Assert.assertEquals(1, managed.getRecordsTotal());
		Assert.assertEquals(1, managed.getData().size());
		Assert.assertTrue(managed.getData().contains("DIG RHA"));
	}

	/**
	 * flast1 is member of company "ing".<br>
	 * There is a delegation to members of company "ing" to see the group "business solution",
	 * and its sub group "Sub Business Solution"
	 */
	@Test
	public void findAllUsingDelegateReceiverCompany() {
		initSpringSecurityContext("flast1");
		final TableItem<ContainerCountVo> tableItem = resource.findAll(newUriInfoAsc("id"));
		Assert.assertEquals(2, tableItem.getRecordsTotal());
		Assert.assertEquals(2, tableItem.getRecordsFiltered());
		Assert.assertEquals(2, tableItem.getData().size());

		// Check the groups "Business Solution"
		Assert.assertEquals("Business Solution", tableItem.getData().get(0).getName());
		Assert.assertEquals("Sub Business Solution", tableItem.getData().get(1).getName());

		// Check the groups
		Assert.assertEquals(0, tableItem.getData().get(0).getCountVisible());
		Assert.assertEquals(1, tableItem.getData().get(0).getCount());
	}

}

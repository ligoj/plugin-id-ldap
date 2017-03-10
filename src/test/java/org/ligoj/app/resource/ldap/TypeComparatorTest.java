package org.ligoj.app.resource.ldap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.ligoj.app.dao.CacheRepository;
import org.ligoj.app.iam.ldap.dao.GroupLdapRepository;
import org.ligoj.app.model.ldap.CacheGroup;
import org.ligoj.app.model.ldap.ContainerType;
import org.ligoj.app.model.ldap.ContainerTypeLdap;
import org.ligoj.app.model.ldap.GroupLdap;
import org.ligoj.app.resource.ldap.AbstractContainerLdapResource.TypeComparator;

/**
 * Test class of {@link TypeComparator}
 */
public class TypeComparatorTest {

	@Test
	public void compareNullType() {
		final Comparator<GroupLdap> comparator = newComparator(new ArrayList<>());
		final List<GroupLdap> groups = new ArrayList<>();
		groups.add(new GroupLdap("cn=NameA,ou=Client1,ou=Project,ou=External,dc=sample,dc=com", "NameA", null));
		groups.add(new GroupLdap("cn=NameC,ou=Client1,ou=Project,ou=External,dc=sample,dc=com", "NameC", null));
		groups.add(new GroupLdap("cn=NameB,ou=Client1,ou=Project,ou=External,dc=sample,dc=com", "NameB", null));
		groups.sort(comparator);
		Assert.assertEquals("NameA", groups.get(0).getName());
		Assert.assertEquals("NameB", groups.get(1).getName());
		Assert.assertEquals("NameC", groups.get(2).getName());
	}

	@Test
	public void compareNullLeft() {
		final ContainerTypeLdap containerType = new ContainerTypeLdap();
		containerType.setDn("ou=Internal,dc=sample,dc=com");
		containerType.setName("name1");
		containerType.setType(ContainerType.GROUP);

		final Comparator<GroupLdap> comparator = newComparator(Collections.singletonList(containerType));
		final List<GroupLdap> groups = new ArrayList<>();
		groups.add(new GroupLdap("cn=NameA,ou=Client1,ou=Project,ou=External,dc=sample,dc=com", "NameA", null));
		groups.add(new GroupLdap("cn=NameC,ou=Client1,ou=Project,ou=Internal,dc=sample,dc=com", "NameC", null));
		groups.add(new GroupLdap("cn=NameB,ou=Client1,ou=Project,ou=External,dc=sample,dc=com", "NameB", null));
		groups.sort(comparator);
		Assert.assertEquals("NameC", groups.get(0).getName());
		Assert.assertEquals("NameA", groups.get(1).getName());
		Assert.assertEquals("NameB", groups.get(2).getName());
	}

	@Test
	public void compareNullRight() {
		final ContainerTypeLdap containerType = new ContainerTypeLdap();
		containerType.setDn("ou=External,dc=sample,dc=com");
		containerType.setName("name1");
		containerType.setType(ContainerType.GROUP);

		final Comparator<GroupLdap> comparator = newComparator(Collections.singletonList(containerType));
		final List<GroupLdap> groups = new ArrayList<>();
		groups.add(new GroupLdap("cn=NameA,ou=Client1,ou=Project,ou=External,dc=sample,dc=com", "NameA", null));
		groups.add(new GroupLdap("cn=NameC,ou=Client1,ou=Project,ou=Internal,dc=sample,dc=com", "NameC", null));
		groups.add(new GroupLdap("cn=NameB,ou=Client1,ou=Project,ou=External,dc=sample,dc=com", "NameB", null));
		groups.sort(comparator);
		Assert.assertEquals("NameA", groups.get(0).getName());
		Assert.assertEquals("NameB", groups.get(1).getName());
		Assert.assertEquals("NameC", groups.get(2).getName());
	}

	@Test
	public void compare() {
		final ContainerTypeLdap containerType1 = new ContainerTypeLdap();
		containerType1.setDn("ou=External,dc=sample,dc=com");
		containerType1.setName("name2");
		containerType1.setType(ContainerType.GROUP);
		final ContainerTypeLdap containerType2 = new ContainerTypeLdap();
		containerType2.setDn("ou=Internal,dc=sample,dc=com");
		containerType2.setName("name1");
		containerType2.setType(ContainerType.GROUP);

		final Comparator<GroupLdap> comparator = newComparator(Arrays.asList(containerType1, containerType2));
		final List<GroupLdap> groups = new ArrayList<>();
		groups.add(new GroupLdap("cn=NameB,ou=Client1,ou=Project,ou=External,dc=sample,dc=com", "NameB", null));
		groups.add(new GroupLdap("cn=NameC,ou=Client1,ou=Project,ou=Internal,dc=sample,dc=com", "NameC", null));
		groups.add(new GroupLdap("cn=NameA,ou=Client1,ou=Project,ou=External,dc=sample,dc=com", "NameA", null));
		groups.sort(comparator);
		Assert.assertEquals("NameC", groups.get(0).getName());
		Assert.assertEquals("NameA", groups.get(1).getName());
		Assert.assertEquals("NameB", groups.get(2).getName());
	}

	private Comparator<GroupLdap> newComparator(final List<ContainerTypeLdap> containerTypes) {
		return new AbstractContainerLdapResource<GroupLdap, GroupLdapEditionVo, CacheGroup>(ContainerType.GROUP) {

			@Override
			protected GroupLdapRepository getRepository() {
				return null;
			}

			@Override
			protected String toDn(final GroupLdapEditionVo container, final ContainerTypeLdap type) {
				return null;
			}

			@Override
			protected CacheRepository<CacheGroup> getCacheRepository() {
				return null;
			}
		}.new TypeComparator(containerTypes);
	}
}

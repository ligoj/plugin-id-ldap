package org.ligoj.app.iam.ldap;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test class of {@link LdapUtils}
 */
public class LdapUtilsTest {
	@Test
	public void equalsOrParentOf() throws Exception {
		// For coverage only
		final Constructor<?> constructor = LdapUtils.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		constructor.newInstance();

		Assert.assertTrue(LdapUtils.equalsOrParentOf("a", "a"));
	}

	@Test
	public void equalsOrParentOfParent() {
		Assert.assertTrue(LdapUtils.equalsOrParentOf("a", "b,a"));
	}

	@Test
	public void equalsOrParentOfCollectionMultiple() {
		final List<String> strings = new ArrayList<>();
		strings.add("dummy");
		strings.add("ou=p2,ou=p1,ou=base");
		Assert.assertTrue(LdapUtils.equalsOrParentOf(strings, "ou=p2,ou=p1,ou=base"));
		Assert.assertTrue(LdapUtils.equalsOrParentOf(strings, "ou=p3,ou=p2,ou=p1,ou=base"));
		Assert.assertFalse(LdapUtils.equalsOrParentOf(strings, "ou=px,ou=p1,ou=base"));
	}

	@Test
	public void equalsOrParentOfNullParent() {
		Assert.assertFalse(LdapUtils.equalsOrParentOf("a", null));
	}

	@Test
	public void equalsOrParentOfNullChild() {
		Assert.assertFalse(LdapUtils.equalsOrParentOf((String) null, "a"));
		Assert.assertFalse(LdapUtils.equalsOrParentOf((String) null, null));
	}

	@Test
	public void toRdn() {
		Assert.assertEquals("b", LdapUtils.toRdn("a=b"));
		Assert.assertEquals("b", LdapUtils.toRdn("a=B"));
		Assert.assertEquals("b", LdapUtils.toRdn("a=b,c=d"));
	}

	@Test
	public void toParentRdn() {
		Assert.assertEquals("d", LdapUtils.toParentRdn("a=b,c=d"));
		Assert.assertEquals("d", LdapUtils.toParentRdn(" a = b , c = D "));
		Assert.assertEquals("d", LdapUtils.toParentRdn("a=b,c=d,e=f"));
	}

	@Test
	public void normalizeSet() {
		final List<String> strings = new ArrayList<>();
		strings.add("c");
		strings.add("C");
		strings.add(" c ");
		final Set<String> result = LdapUtils.normalize(strings);
		Assert.assertEquals(1, result.size());
		Assert.assertTrue(result.contains("c"));
	}

	@Test
	public void normalizeSetNull() {
		final Set<String> result = LdapUtils.normalize((Collection<String>) null);
		Assert.assertEquals(0, result.size());
	}

	@Test
	public void normalize() {
		Assert.assertEquals("c", LdapUtils.normalize(" C "));
		Assert.assertEquals("c", LdapUtils.normalize("c"));
	}

}

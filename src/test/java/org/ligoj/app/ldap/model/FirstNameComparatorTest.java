package org.ligoj.app.ldap.model;

import org.apache.commons.lang3.ObjectUtils;
import org.junit.Assert;
import org.junit.Test;
import org.ligoj.app.api.UserLdap;
import org.ligoj.app.ldap.model.FirstNameComparator;

/**
 * Test class of {@link FirstNameComparator}
 */
public class FirstNameComparatorTest {

	private UserLdap newSimpleUser(final String firstName, final String login) {
		final UserLdap simpleUser = new UserLdap();
		simpleUser.setFirstName(firstName);
		simpleUser.setName(ObjectUtils.defaultIfNull(login, "l"));
		return simpleUser;
	}

	@Test
	public void compareNull() {
		Assert.assertEquals(0, new FirstNameComparator().compare(newSimpleUser(null, null), newSimpleUser(null, null)));
	}

	@Test
	public void compareNull0() {
		final UserLdap o1 = newSimpleUser("a", null);
		Assert.assertEquals(1, new FirstNameComparator().compare(o1, newSimpleUser(null, null)));
	}

	@Test
	public void compareNull1() {
		final UserLdap o2 = newSimpleUser("a", null);
		Assert.assertEquals(-1, new FirstNameComparator().compare(newSimpleUser(null, null), o2));
	}

	@Test
	public void compare() {
		final UserLdap o1 = newSimpleUser("a", null);
		final UserLdap o2 = newSimpleUser("c", null);
		Assert.assertEquals(-2, new FirstNameComparator().compare(o1, o2));
	}

}

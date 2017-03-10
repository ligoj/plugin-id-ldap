package org.ligoj.app.resource.ldap;

import org.junit.Assert;
import org.junit.Test;

import org.ligoj.app.model.ldap.ContainerLdap;

/**
 * Test class of {@link ContainerLdap}
 */
public class ContainerLdapTest {

	@Test
	public void valid() {
		Assert.assertTrue("namE-er:az 12".matches(ContainerLdap.NAME_PATTERN_WRAPPER));
		Assert.assertTrue("Name".matches(ContainerLdap.NAME_PATTERN_WRAPPER));
		Assert.assertTrue("Name 2".matches(ContainerLdap.NAME_PATTERN_WRAPPER));
		Assert.assertTrue("3 Name".matches(ContainerLdap.NAME_PATTERN_WRAPPER));
	}

	@Test
	public void invalid() {
		Assert.assertFalse(" name".matches(ContainerLdap.NAME_PATTERN_WRAPPER));
		Assert.assertFalse("-name".matches(ContainerLdap.NAME_PATTERN_WRAPPER));
		Assert.assertFalse("name--er".matches(ContainerLdap.NAME_PATTERN_WRAPPER));
		Assert.assertFalse("name-".matches(ContainerLdap.NAME_PATTERN_WRAPPER));
		Assert.assertFalse("name:".matches(ContainerLdap.NAME_PATTERN_WRAPPER));
		Assert.assertFalse("name ".matches(ContainerLdap.NAME_PATTERN_WRAPPER));
	}
}

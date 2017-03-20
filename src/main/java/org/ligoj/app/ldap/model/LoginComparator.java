package org.ligoj.app.ldap.model;

import java.util.Comparator;

import org.ligoj.app.api.UserLdap;

/**
 * Order by UID.
 */
public class LoginComparator implements Comparator<UserLdap> {

	@Override
	public int compare(final UserLdap o1, final UserLdap o2) {
		return o1.compareTo(o2);
	}

}

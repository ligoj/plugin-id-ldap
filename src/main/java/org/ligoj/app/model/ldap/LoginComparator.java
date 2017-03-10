package org.ligoj.app.model.ldap;

import java.util.Comparator;

/**
 * Order by UID.
 */
public class LoginComparator implements Comparator<UserLdap> {

	@Override
	public int compare(final UserLdap o1, final UserLdap o2) {
		return o1.compareTo(o2);
	}

}

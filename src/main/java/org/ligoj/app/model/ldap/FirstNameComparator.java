package org.ligoj.app.model.ldap;

import org.apache.commons.lang3.ObjectUtils;

/**
 * Order by first name.
 */
public class FirstNameComparator extends LoginComparator {

	@Override
	public int compare(final UserLdap o1, final UserLdap o2) {
		final int compareTo = ObjectUtils.defaultIfNull(o1.getFirstName(), "").compareToIgnoreCase(ObjectUtils.defaultIfNull(o2.getFirstName(), ""));
		if (compareTo == 0) {
			return super.compare(o1, o2);
		}
		return compareTo;
	}

}

package org.ligoj.app.model.ldap;

/**
 * Order by Last name.
 */
public class LastNameComparator extends LoginComparator {

	@Override
	public int compare(final UserLdap o1, final UserLdap o2) {
		final int compareTo = o1.getLastName().compareToIgnoreCase(o2.getLastName());
		if (compareTo == 0) {
			return super.compare(o1, o2);
		}
		return compareTo;
	}

}

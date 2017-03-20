package org.ligoj.app.ldap.model;

import org.ligoj.app.api.UserLdap;

/**
 * Order by company.
 */
public class CompanyComparator extends LoginComparator {

	@Override
	public int compare(final UserLdap o1, final UserLdap o2) {
		final int compareTo = o1.getCompany().compareTo(o2.getCompany());
		if (compareTo == 0) {
			return super.compare(o1, o2);
		}
		return compareTo;
	}

}

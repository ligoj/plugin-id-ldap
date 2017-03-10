package org.ligoj.app.model.ldap;

/**
 * Order by mail.
 */
public class MailComparator extends LoginComparator {

	@Override
	public int compare(final UserLdap o1, final UserLdap o2) {
		final int compareTo = toSafeString(o1).compareToIgnoreCase(toSafeString(o2));
		if (compareTo == 0) {
			return super.compare(o1, o2);
		}
		return compareTo;
	}

	/**
	 * Return a safe string representation of the mail of a user.
	 */
	private String toSafeString(final UserLdap o1) {
		return o1.getMails().isEmpty() ? "" : o1.getMails().get(0);
	}

}

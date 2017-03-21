package org.ligoj.app.ldap;

import java.util.Collection;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.api.Normalizer;

/**
 * LDAP utilities.
 */
public final class LdapUtils {

	private LdapUtils() {
		// Factory pattern
	}

	/**
	 * Return the RDN of the DN.
	 * 
	 * @param dn
	 *            The DN to extract RDN.
	 * @return the RDN of given DN.
	 */
	public static String toRdn(@NotNull final String dn) {
		return Normalizer.normalize(StringUtils.split(dn, "=,")[1]);
	}

	/**
	 * Return the RDN of the parent DN.
	 * 
	 * @param dn
	 *            The DN to extract RDN.
	 * @return the normalized RDN of the parent of given DN.
	 */
	public static String toParentRdn(@NotNull final String dn) {
		return Normalizer.normalize(StringUtils.split(dn, "=,")[3]);
	}

	/**
	 * Indicates the given parent DN is equals or contains the other DN
	 * 
	 * @param parentDn
	 *            Parent DN.
	 * @param childDn
	 *            Child DN
	 * @return <code>true</code> when <code>child=parent</code> or <code>child=.*,parent</code>
	 */
	public static boolean equalsOrParentOf(@NotNull final String parentDn, final String childDn) {
		return childDn != null && (childDn.endsWith("," + parentDn) || childDn.equals(parentDn));
	}

	/**
	 * Indicates the given parent DN collection contains the exact exact DN or sub DN
	 * 
	 * @param parentDns
	 *            Collection of parent DNs.
	 * @param childDn
	 *            Child DN
	 * @return <code>true</code> when <code>child=parent</code> or <code>child=.*,parent</code>
	 * @see LdapUtils#equalsOrParentOf(String, String)
	 */
	public static boolean equalsOrParentOf(@NotNull final Collection<String> parentDns, final String childDn) {
		for (final String dn : parentDns) {
			if (equalsOrParentOf(dn, childDn)) {
				return true;
			}
		}
		return false;
	}

}

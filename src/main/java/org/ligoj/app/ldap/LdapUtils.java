package org.ligoj.app.ldap;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

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
		return normalize(StringUtils.split(dn, "=,")[1]);
	}

	/**
	 * Return the RDN of the parent DN.
	 * 
	 * @param dn
	 *            The DN to extract RDN.
	 * @return the normalized RDN of the parent of given DN.
	 */
	public static String toParentRdn(@NotNull final String dn) {
		return normalize(StringUtils.split(dn, "=,")[3]);
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

	/**
	 * Normalize a collection of string. Order is respected (LinkedHashSet) but not by function contract (Set).
	 * 
	 * @param items
	 *            The human readable strings
	 * @return the normalized items.
	 */
	public static Set<String> normalize(final Collection<String> items) {
		return normalize(CollectionUtils.emptyIfNull(items).stream());
	}

	/**
	 * Normalize a collection of string. Order is respected (LinkedHashSet) but not by function contract (Set).
	 * 
	 * @param items
	 *            The human readable strings
	 * @return the normalized items.
	 */
	public static Set<String> normalize(final Stream<String> items) {
		final Set<String> result = new LinkedHashSet<>();
		items.map(LdapUtils::normalize).forEach(result::add);
		return result;
	}

	/**
	 * Normalize and trim a string.
	 * 
	 * @param item
	 *            The human readable string. A DN or any LDAP attribute.
	 * @return the normalized and trimmed item.
	 */
	public static String normalize(@NotNull final String item) {
		return StringUtils.trimToEmpty(item).toLowerCase(Locale.ENGLISH);
	}

}

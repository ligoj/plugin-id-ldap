package org.ligoj.app.ldap.resource;

import org.ligoj.app.model.ContainerType;

import lombok.Getter;
import lombok.Setter;

/**
 * LDAP container where visible users are counted.<br>
 * DN is not exposed.
 */
@Getter
@Setter
public class ContainerLdapCountVo extends ContainerLdapWithTypeVo {

	/**
	 * Unique visible members count.
	 */
	private int countVisible;

	/**
	 * Unique visible or not members count.
	 */
	private int count;

	/**
	 * Can manage the members of this group.
	 */
	private boolean canWrite;

	/**
	 * Can delete this group.
	 */
	private boolean canAdmin;

	/**
	 * Container type.
	 */
	private ContainerType containerType;
}

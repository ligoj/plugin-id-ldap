package org.ligoj.app.ldap.model;

import org.ligoj.app.model.DelegateLdapType;

import lombok.Getter;

/**
 * Container type.
 */
public enum ContainerType {
	GROUP(DelegateLdapType.GROUP), COMPANY(DelegateLdapType.COMPANY);

	/**
	 * Corresponding {@link DelegateLdapType}
	 */
	@Getter
	private final DelegateLdapType delegateType;

	ContainerType(final DelegateLdapType delegateType) {
		this.delegateType = delegateType;
	}
}

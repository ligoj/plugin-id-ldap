package org.ligoj.app.model.ldap;

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

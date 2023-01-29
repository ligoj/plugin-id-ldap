/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.dao;

import lombok.Getter;
import lombok.Setter;
import org.springframework.ldap.core.LdapTemplate;

public class AbstractManagedLdapRepository {

	/**
	 * LDAP class filter.
	 */
	public static final String OBJECT_CLASS = "objectClass";

	@Setter
	protected LdapTemplate template;

	/**
	 * LDAP class name of this container.
	 */
	@Setter
	protected String className;

	/**
	 * LDAP base DN where all objects of this class are located. May be different from the generic base DN of server.
	 */
	@Setter
	protected String baseDn;

	/**
	 * Human-readable type name.
	 */
	@Getter
	protected final String typeName;

	protected AbstractManagedLdapRepository(final String typeName) {
		this.typeName = typeName;
	}

}

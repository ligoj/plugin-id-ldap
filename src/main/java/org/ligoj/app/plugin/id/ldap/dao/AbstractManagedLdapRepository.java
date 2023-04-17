/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.dao;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.ldap.NameAlreadyBoundException;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;


@Slf4j
public class AbstractManagedLdapRepository {

	/**
	 * LDAP class filter.
	 */
	public static final String OBJECT_CLASS = "objectClass";

	@Setter
	@Getter
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

	/**
	 * Unbind the given DN, ignoring its failure.
	 *
	 * @param dn DN to unbind.
	 */
	protected void unbind(final String dn) {
		try {
			template.unbind(dn, true);
		} catch (final NameNotFoundException nne) {
			// Already deleted user, ignore this error
			log.warn("{} LDAP entry {} seems te have been deleted from the LDAP repository", typeName, dn);
		}
	}

	/**
	 * Bind the given context. If the bind fail, a validation exception is generated.
	 *
	 * @param context to bind.
	 */
	protected void bind(final DirContextAdapter context) {
		final var dn = context.getDn().toString();
		try {
			template.bind(context);
		} catch (final NameAlreadyBoundException e) {
			log.info("{} LDAP entry {} seems to have been created in the LDAP from outside", typeName, dn);
			throw new ValidationJsonException(dn, "integrity-unicity");
		}
	}


}

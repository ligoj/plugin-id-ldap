/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.dao;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.ligoj.app.iam.ResourceOrg;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.ldap.NameAlreadyBoundException;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.OrFilter;

import java.util.stream.Stream;

/**
 * Base LDAP repository for groups, users and companies.
 */
@Slf4j
public abstract class AbstractManagedLdapRepository<T extends ResourceOrg> {

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
	protected String[] classNames = ArrayUtils.EMPTY_STRING_ARRAY;

	/**
	 * LDAP class name of this container for creation operations. When null or empty, the container class name is used.
	 */
	@Setter
	protected String[] classNamesCreate = ArrayUtils.EMPTY_STRING_ARRAY;

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
	 * Bind the given entry into a context. If the bind fail, a validation exception is generated.
	 *
	 * @param entry The container entry to map.
	 * @param dn    The DN of entry to bind.
	 */
	protected void bind(final T entry, final String dn) {
		final var context = new DirContextAdapter(dn);
		context.setAttributeValues(OBJECT_CLASS, classNamesCreate);
		mapToContext(entry, context);
		bind(context);
	}


	/**
	 * Bind the given context. If the bind fail, a validation exception is generated.
	 *
	 * @param context to bind.
	 */
	protected void bind(final DirContextOperations context) {
		final var dn = context.getDn().toString();
		try {
			template.bind(context);
		} catch (final NameAlreadyBoundException e) {
			log.info("{} LDAP entry {} seems to have been created in the LDAP from outside", typeName, dn);
			throw new ValidationJsonException(dn, "integrity-unicity");
		}
	}


	/**
	 * Map a container to LDAP.
	 *
	 * @param entry   The container entry to map.
	 * @param context The target context to fill.
	 */
	abstract void mapToContext(final T entry, final DirContextOperations context);

	/**
	 * Return an LDAP filter based on this container's classes.
	 *
	 * @return An LDAP filter based on this container's classes.
	 */
	protected OrFilter newClassesFilter() {
		return Stream.of(classNames).reduce(new OrFilter(), (f, a) -> f.or(new EqualsFilter(OBJECT_CLASS, a)), (f, a) -> a);
	}

}

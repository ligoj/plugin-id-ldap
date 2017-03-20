package org.ligoj.app.ldap.model;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.NotBlank;

import org.ligoj.bootstrap.core.model.AbstractNamedEntity;
import org.ligoj.app.validation.DistinguishName;
import lombok.Getter;
import lombok.Setter;

/**
 * Type of LDAP group define the DN of parent LDAP owner. Name attribute is the name of the type.
 */
@Getter
@Setter
@Entity
@Table(name = "SAAS_CONTAINER_TYPE_LDAP", uniqueConstraints = { @UniqueConstraint(columnNames = { "name", "type" }),
		@UniqueConstraint(columnNames = "dn") })
public class ContainerTypeLdap extends AbstractNamedEntity<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Special name for project for {@link ContainerType#GROUP} type.
	 */
	public static final String TYPE_PROJECT = "Project";

	/**
	 * The "Distinguished Name" of the LDAP parent owning the container of this type. The base DN is not included into
	 * this
	 * String.
	 */
	@NotNull
	@NotBlank
	@Length(max = 255)
	@DistinguishName
	private String dn;

	@NotNull
	private ContainerType type;

	/**
	 * When a type is locked, there is no way to create, update or delete a group of this type.
	 */
	private boolean locked;
}

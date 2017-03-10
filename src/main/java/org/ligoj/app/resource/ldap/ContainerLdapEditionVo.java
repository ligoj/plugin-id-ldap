package org.ligoj.app.resource.ldap;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

import org.ligoj.app.model.ldap.ContainerLdap;
import lombok.Getter;
import lombok.Setter;

/**
 * LDAP Group/company for edition.
 */
@Getter
@Setter
public class ContainerLdapEditionVo {

	/**
	 * Group name, original CN.
	 */
	@NotNull
	@NotEmpty
	@NotBlank
	@Size(max = 255)
	@Pattern(regexp = ContainerLdap.NAME_PATTERN)
	private String name;

	/**
	 * The type of this container.
	 */
	@NotNull
	private Integer type;

}

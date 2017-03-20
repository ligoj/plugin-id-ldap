package org.ligoj.app.ldap.resource;

import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotBlank;

import org.ligoj.app.api.SimpleUser;
import lombok.Getter;
import lombok.Setter;

/**
 * Import entry
 */
@Getter
@Setter
public class UserImportEntry extends SimpleUser implements BatchElement {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * User mail address.
	 */
	@NotNull
	@NotBlank
	@Email
	private String mail;

	/**
	 * Import status. <code>null</code> when not proceeded.
	 */
	private Boolean status;

	/**
	 * Import status text. <code>null</code> when not proceeded.
	 */
	private String statusText;

	/**
	 * Groups aliases.
	 */
	private String groups;

}

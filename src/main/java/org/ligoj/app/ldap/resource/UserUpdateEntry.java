package org.ligoj.app.ldap.resource;

import javax.persistence.Transient;
import javax.validation.constraints.NotNull;

import org.ligoj.app.plugin.id.resource.UserLdapEdition;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * Entry update for batch.
 */
@Getter
@Setter
public class UserUpdateEntry implements BatchElement {

	/**
	 * The related user
	 */
	@NotNull
	private String user;

	/**
	 * The operation to execute for this user.
	 */
	@NotNull
	private String operation;

	/**
	 * The optional parameter for the operation. Some operation such as deletion does not require additional
	 * parameters.
	 */
	private String value;

	/**
	 * Import status. <code>null</code> when not proceeded.
	 */
	private Boolean status;

	/**
	 * Import status text. <code>null</code> when not proceeded.
	 */
	private String statusText;

	/**
	 * The related resolved user
	 */
	@Transient
	@JsonIgnore
	private UserLdapEdition userLdap;

}

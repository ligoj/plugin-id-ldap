package org.ligoj.app.resource.ldap;

import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

/**
 * Group Import entry
 */
@Getter
@Setter
public class GroupImportEntry implements BatchElement {

	@NotNull
	@NotBlank
	private String name;
	private String parent;

	// Special attributes

	/**
	 * Department number or name, multi-valued. May be used to link user department attribute. Must be UID of the related users.
	 */
	private String department;

	/**
	 * Assistant of this group, multi-valued. Must be UID of the related users.
	 */
	private String assistant;

	/**
	 * Owner of this group, multi-valued. Must be UID of the related users.
	 */
	private String owner;

	/**
	 * The type of this container.
	 */
	@NotNull
	private String type;

	/**
	 * Import status. <code>null</code> when not proceeded.
	 */
	private Boolean status;

	/**
	 * Import status text. <code>null</code> when not proceeded.
	 */
	private String statusText;

}

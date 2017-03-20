package org.ligoj.app.ldap.resource;

import java.util.List;

import org.ligoj.app.api.SimpleUserLdap;

import lombok.Getter;
import lombok.Setter;

/**
 * User details with additional business details about rights.
 */
@Getter
@Setter
public class UserLdapVo extends SimpleUserLdap {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Is this entry can be managed by current user : delete and update all data but groups.
	 */
	private boolean managed;

	/**
	 * Membership, CN of groups.
	 */
	private List<GroupLdapVo> groups;

}

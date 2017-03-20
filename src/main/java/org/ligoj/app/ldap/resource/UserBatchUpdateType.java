package org.ligoj.app.ldap.resource;

/**
 * LDAP action type.
 */
public enum UserBatchUpdateType {

	/**
	 * Isolate the user.
	 */
	ISOLATE,
	
	/**
	 * Restore the user.
	 */
	RESTORE,

	/**
	 * Lock the user.
	 */
	LOCK,

	/**
	 * Change an attribute from the user
	 */
	ATTRIBUTE,

	/**
	 * Delete a user.
	 */
	DELETE
}

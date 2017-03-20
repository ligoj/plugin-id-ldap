package org.ligoj.app.ldap.resource;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * Import status.
 */
@Getter
@Setter
public class ImportStatus {

	/**
	 * Transaction start. <code>null</code> while not started.
	 */
	private Date start;

	/**
	 * Transaction end. <code>null</code> when not finished.
	 */
	private Date end;

	/**
	 * Amount of proceeded entries.
	 */
	private int done;

	/**
	 * Total amount of entries to proceed.
	 */
	private int entries;

	/**
	 * Import status. <code>null</code> when not proceeded.
	 */
	private Boolean status;
	
	/**
	 * Import status text. <code>null</code> when not proceeded.
	 */
	private String statusText;

}

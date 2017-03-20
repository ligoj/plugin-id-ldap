package org.ligoj.app.ldap.resource;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * LDAP import bean
 */
@Getter
@Setter
@ToString(of = "id")
public class BatchTaskVo<B extends BatchElement> {

	/**
	 * Transaction identifier.
	 */
	private long id;

	/**
	 * Entries to persist.
	 */
	private List<B> entries;

	/**
	 * Import status.
	 */
	private ImportStatus status = new ImportStatus();

	/**
	 * User principal requesting the import.
	 */
	private String principal;

}

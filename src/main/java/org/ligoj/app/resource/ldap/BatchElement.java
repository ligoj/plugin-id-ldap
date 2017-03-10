package org.ligoj.app.resource.ldap;

/**
 * A batch element with status.
 */
public interface BatchElement {

	/**
	 * Return batch status for this element.
	 * 
	 * @return Batch status for this element. <code>null</code> when not proceeded.
	 */
	Boolean getStatus();

	/**
	 * Import status text.
	 * 
	 * @return Import status text. <code>null</code> when not proceeded.
	 */
	String getStatusText();

	/**
	 * Set batch status for this element.
	 * 
	 * @param status
	 *            Batch status for this element. <code>null</code> when not proceeded.
	 */
	void setStatus(Boolean status);

	/**
	 * Set status text.
	 * 
	 * @param text
	 *            Import status text. <code>null</code> when not proceeded.
	 */
	void setStatusText(String text);

}

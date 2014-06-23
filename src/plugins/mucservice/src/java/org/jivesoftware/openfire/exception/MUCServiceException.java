package org.jivesoftware.openfire.exception;

/**
 * The Class MUCServiceException.
 */
public class MUCServiceException extends Exception {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 4351720088030656859L;

	/** The ressource. */
	private String ressource;

	/** The exception. */
	private String exception;

	/**
	 * Instantiates a new mUC service exception.
	 * 
	 * @param msg
	 *            the msg
	 * @param ressource
	 *            the ressource
	 * @param exception
	 *            the exception
	 */
	public MUCServiceException(String msg, String ressource, String exception) {
		super(msg);
		this.ressource = ressource;
		this.exception = exception;
	}

	/**
	 * Instantiates a new mUC service exception.
	 * 
	 * @param msg
	 *            the msg
	 * @param cause
	 *            the cause
	 */
	public MUCServiceException(String msg, Throwable cause) {
		super(msg, cause);
	}

	/**
	 * Gets the ressource.
	 * 
	 * @return the ressource
	 */
	public String getRessource() {
		return ressource;
	}

	/**
	 * Sets the ressource.
	 * 
	 * @param ressource
	 *            the new ressource
	 */
	public void setRessource(String ressource) {
		this.ressource = ressource;
	}

	/**
	 * Gets the exception.
	 * 
	 * @return the exception
	 */
	public String getException() {
		return exception;
	}

	/**
	 * Sets the exception.
	 * 
	 * @param exception
	 *            the new exception
	 */
	public void setException(String exception) {
		this.exception = exception;
	}
}
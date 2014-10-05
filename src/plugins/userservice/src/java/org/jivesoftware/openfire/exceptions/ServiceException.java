package org.jivesoftware.openfire.exceptions;

import javax.ws.rs.core.Response.Status;

/**
 * The Class MUCServiceException.
 */
public class ServiceException extends Exception {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 4351720088030656859L;

	/** The ressource. */
	private String ressource;

	/** The exception. */
	private String exception;

	/** The status. */
	private Status status;

	/**
	 * Instantiates a new mUC service exception.
	 *
	 * @param msg
	 *            the msg
	 * @param ressource
	 *            the ressource
	 * @param exception
	 *            the exception
	 * @param status
	 *            the status
	 */
	public ServiceException(String msg, String ressource, String exception, Status status) {
		super(msg);
		this.ressource = ressource;
		this.exception = exception;
		this.status = status;
	}

	/**
	 * Instantiates a new service exception.
	 *
	 * @param msg
	 *            the msg
	 * @param ressource
	 *            the ressource
	 * @param exception
	 *            the exception
	 * @param status
	 *            the status
	 * @param cause
	 *            the cause
	 */
	public ServiceException(String msg, String ressource, String exception, Status status, Throwable cause) {
		super(msg, cause);
		this.ressource = ressource;
		this.exception = exception;
		this.status = status;
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

	/**
	 * Gets the status.
	 *
	 * @return the status
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * Sets the status.
	 *
	 * @param status
	 *            the new status
	 */
	public void setStatus(Status status) {
		this.status = status;
	}
}
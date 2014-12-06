package org.jivesoftware.openfire.plugin.rest.exceptions;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * The Class ErrorResponse.
 */
@XmlRootElement(name = "error")
public class ErrorResponse {

	/** The ressource. */
	private String ressource;

	/** The message. */
	private String message;

	/** The exception. */
	private String exception;

	/** The exception stack. */
	private String exceptionStack;

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
	 * Gets the message.
	 *
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Sets the message.
	 *
	 * @param message
	 *            the new message
	 */
	public void setMessage(String message) {
		this.message = message;
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
	 * Gets the exception stack.
	 *
	 * @return the exception stack
	 */
	public String getExceptionStack() {
		return exceptionStack;
	}

	/**
	 * Sets the exception stack.
	 *
	 * @param exceptionStack
	 *            the new exception stack
	 */
	public void setExceptionStack(String exceptionStack) {
		this.exceptionStack = exceptionStack;
	}
}
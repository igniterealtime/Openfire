package org.jivesoftware.openfire.plugin.rest.exceptions;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The Class ErrorResponse.
 */
@XmlRootElement(name = "error")
public class ErrorResponse {

	/** The resource. */
	private String resource;

	/** The message. */
	private String message;

	/** The exception. */
	private String exception;

	/** The exception stack. */
	private String exceptionStack;

	/**
	 * Gets the resource.
	 *
	 * @return the resource
	 */
	@XmlElement(name = "resource")
	public String getResource() {
		return resource;
	}

	/**
	 * Sets the resource.
	 *
	 * @param ressource the new resource
	 */
	public void setResource(String resource) {
		this.resource = resource;
	}

	/**
	 * Gets the message.
	 *
	 * @return the message
	 */
	@XmlElement(name = "message")
	public String getMessage() {
		return message;
	}

	/**
	 * Sets the message.
	 *
	 * @param message the new message
	 */
	public void setMessage(String message) {
		this.message = message;
	}

	/**
	 * Gets the exception.
	 *
	 * @return the exception
	 */
	@XmlElement(name = "exception")
	public String getException() {
		return exception;
	}

	/**
	 * Sets the exception.
	 *
	 * @param exception the new exception
	 */
	public void setException(String exception) {
		this.exception = exception;
	}

	/**
	 * Gets the exception stack.
	 *
	 * @return the exception stack
	 */
	@XmlElement(name = "exceptionStack")
	public String getExceptionStack() {
		return exceptionStack;
	}

	/**
	 * Sets the exception stack.
	 *
	 * @param exceptionStack the new exception stack
	 */
	public void setExceptionStack(String exceptionStack) {
		this.exceptionStack = exceptionStack;
	}
}
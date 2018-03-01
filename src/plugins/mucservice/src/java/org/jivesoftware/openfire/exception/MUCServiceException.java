package org.jivesoftware.openfire.exception;

/**
 * The Class MUCServiceException.
 */
public class MUCServiceException extends Exception {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 4351720088030656859L;

    /** The resource. */
    private String resource;

    /** The exception. */
    private String exception;

    /**
     * Instantiates a new mUC service exception.
     * 
     * @param msg
     *            the msg
     * @param resource
     *            the resource
     * @param exception
     *            the exception
     */
    public MUCServiceException(String msg, String resource, String exception) {
        super(msg);
        this.resource = resource;
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
     * Gets the resource.
     * 
     * @return the resource
     */
    public String getResource() {
        return resource;
    }

    /**
     * Sets the resource.
     * 
     * @param resource
     *            the new resource
     */
    public void setResource(String resource) {
        this.resource = resource;
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

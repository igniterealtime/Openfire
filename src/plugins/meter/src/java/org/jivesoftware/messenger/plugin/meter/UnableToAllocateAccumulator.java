/**
 * 
 */
package org.jivesoftware.messenger.plugin.meter;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public class UnableToAllocateAccumulator extends Exception {

    
    /** The serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /**
     * Construct a new <code>UnableToAllocateAccumulator</code>.
     *
     */
    public UnableToAllocateAccumulator() {
        super();
        // TODO Auto-generated constructor stub
    }

    /**
     * Construct a new <code>UnableToAllocateAccumulator</code>.
     *
     * @param message
     * @param cause
     */
    public UnableToAllocateAccumulator(String message, Throwable cause) {
        super(message, cause);
        // TODO Auto-generated constructor stub
    }

    /**
     * Construct a new <code>UnableToAllocateAccumulator</code>.
     *
     * @param message
     */
    public UnableToAllocateAccumulator(String message) {
        super(message);
        // TODO Auto-generated constructor stub
    }

    /**
     * Construct a new <code>UnableToAllocateAccumulator</code>.
     *
     * @param cause
     */
    public UnableToAllocateAccumulator(Throwable cause) {
        super(cause);
        // TODO Auto-generated constructor stub
    }

}

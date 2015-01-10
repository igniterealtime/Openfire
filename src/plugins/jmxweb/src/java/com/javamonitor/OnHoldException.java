package com.javamonitor;

/**
 * An exception to indicate that we're on hold.
 * 
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
class OnHoldException extends Exception {
    private static final long serialVersionUID = -2479956387836662887L;

    private final String onHoldBecause;

    /**
     * @param onHoldBecause
     */
    public OnHoldException(final String onHoldBecause) {
        super("On hold because: " + onHoldBecause);
        this.onHoldBecause = onHoldBecause;
    }

    /**
     * FInd out why we're on hold.
     * 
     * @return The reason why we're being put on hold.
     */
    public String getOnHoldBecause() {
        return onHoldBecause;
    }
}

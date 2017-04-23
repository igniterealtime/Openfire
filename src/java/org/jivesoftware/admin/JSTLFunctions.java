package org.jivesoftware.admin;

/**
 * Utility functions that are exposed through a taglib.
 *
 * @author Guus der Kinderen
 */
public class JSTLFunctions
{
    /**
     * JSTL delegate for {@link String#replaceAll(String, String)}. The first argument is the value on which the
     * replacement has to occur. The other arguments are passed to {@link String#replaceAll(String, String)} directly.
     *
     * @see String#replaceAll(String, String)
     */
    public static String replaceAll(String string, String regex, String replacement)
    {
        return string.replaceAll(regex, replacement);
    }

    /**
     * JSTL delegate for {@link String#split(String)}. The first argument is the value on which the replacement has to
     * occur. The other argument is used as the argument for the invocation of {@link String#split(String)}.
     *
     * @see String#split(String)
     */
    public static String[] split(String string, String regex)
    {
        return string.split(regex);
    }
}

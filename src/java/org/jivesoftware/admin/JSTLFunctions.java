package org.jivesoftware.admin;

import org.jivesoftware.util.ByteFormat;
import org.jivesoftware.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

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

    /**
     * A formatter for formatting byte sizes. For example, formatting 12345 byes results in
     * "12.1 K" and 1234567 results in "1.18 MB".
     *
     * @see ByteFormat
     */
    public static String byteFormat( long bytes )
    {
        return new ByteFormat().format( bytes );
    }

    /**
     * Translates a string into {@code application/x-www-form-urlencoded} format using a specific encoding scheme. This
     * method uses the UTF-8 encoding scheme to obtain the bytes for unsafe characters.
     *
     * @see URLEncoder
     */
    public static String urlEncode( String string )
    {
        try
        {
            return URLEncoder.encode( string, "UTF-8" );
        }
        catch ( UnsupportedEncodingException e )
        {
            // Should never occur, as UTF-8 encoding is mantdatory to implement for any JRE.
            throw new IllegalStateException( "Unable to URL-encode string: " + string, e );
        }
    }

    /**
     * Decodes a {@code application/x-www-form-urlencoded} string using the UTF-8 encoding scheme. The encoding is used
     * to determine what characters are represented by any consecutive sequences of the form "<i>{@code %xy}</i>".
     *
     * @see URLDecoder
     */
    public static String urlDecode( String string )
    {
        try
        {
            return URLDecoder.decode( string, "UTF-8" );
        }
        catch ( UnsupportedEncodingException e )
        {
            // Should never occur, as UTF-8 encoding is mantdatory to implement for any JRE.
            throw new IllegalStateException( "Unable to URL-decode string: " + string, e );
        }
    }

    /**
     * This method takes a string which may contain HTML tags (ie, &lt7;b&gt;,
     * &lt;table&gt;, etc) and converts the '&lt;' and '&gt;' characters to
     * their HTML escape sequences. It will also replace LF  with &lt;br&gt;.
     *
     * @see StringUtils#escapeHTMLTags(String)
     */
    public static String escapeHTMLTags( String string )
    {
        return StringUtils.escapeHTMLTags( string );
    }
}

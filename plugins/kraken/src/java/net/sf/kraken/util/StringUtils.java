/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A simple class to perform various string related functions.
 *
 * @author Daniel Henninger
 */
public class StringUtils {

    /**
     * Convenience routine to perform a string join for groups in the database.
     * @param array Array of strings to join together.
     * @param delim Delimiter to separate strings with.
     * @return Joined string
     */
    public static String join( List<String> array, String delim ) {
        StringBuffer sb = join(array, delim, new StringBuffer());
        return sb.toString();
    }

    /**
     * Helper function for primary use join function.
     * @param array Array of strings to join together.
     * @param delim Delimiter to separate strings with.
     * @param sb String buffer instance to work from.
     * @return String buffer instance.
     */
    static StringBuffer join( List<String> array, String delim, StringBuffer sb ) {
        Boolean first = true;
        for (String s : array) {
            if (!first) {
                sb.append(delim);
            }
            else {
                first = false;
            }
            sb.append(s);
        }
        return sb;
    }

    /**
     *  Regular Expressions
     */

    /* HTML looking tags */
    private static final Pattern htmlRE = Pattern.compile("<[^>]*>");

    /* Newlines */
    private static final Pattern newlineRE = Pattern.compile("<br/?>", Pattern.CASE_INSENSITIVE);

    /**
     * Strips HTML tags fairly loosely, trusting that html tags will look like
     * &lt;whatever&gt;.  Before stripping these tags, it tries to convert known tags
     * to text versions, such as newlines.
     *
     * @param str the string from which to strip HTML tags
     * @return the given string with HTML tags removed
     */
    public static String convertFromHtml(String str) {
        str = newlineRE.matcher(str).replaceAll("\\\n");
        str = htmlRE.matcher(str).replaceAll("");
        str = org.jivesoftware.util.StringUtils.unescapeFromXML(str);
        return str;
    }
    
    /**
     * This method ensures that the output String has only valid XML unicode characters as specified by the
     * XML 1.0 standard. For reference, please see the
     * standard. This method will return an empty String if the input is null or empty.
     *
     * @author Donoiu Cristian, GPL
     * @param  s The String whose non-valid characters we want to remove.
     * @return The in String, stripped of non-valid characters.
     */
    public static String removeInvalidXMLCharacters(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();                // Used to hold the output.
    	int codePoint;                                          // Used to reference the current character.
		int i=0;
    	while(i<s.length()) {
    		codePoint = s.codePointAt(i);                       // This is the unicode code of the character.
			if ((codePoint == 0x9) ||          				    // Consider testing larger ranges first to improve speed. 
					(codePoint == 0xA) ||
					(codePoint == 0xD) ||
					((codePoint >= 0x20) && (codePoint <= 0xD7FF)) ||
					((codePoint >= 0xE000) && (codePoint <= 0xFFFD)) ||
					((codePoint >= 0x10000) && (codePoint <= 0x10FFFF))) {
				out.append(Character.toChars(codePoint));
			}				
			i+= Character.charCount(codePoint);                 // Increment with the number of code units(java chars) needed to represent a Unicode char.  
    	}
    	return out.toString();
    } 

}

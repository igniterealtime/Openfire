/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.util;

import org.apache.commons.codec.binary.Base32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.xml.bind.DatatypeConverter;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.BreakIterator;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class to perform common String manipulation algorithms.
 */
public final class StringUtils {

    private static final Logger Log = LoggerFactory.getLogger(StringUtils.class);

    // Constants used by escapeHTMLTags
    private static final char[] QUOTE_ENCODE = "&quot;".toCharArray();
    private static final char[] AMP_ENCODE = "&amp;".toCharArray();
    private static final char[] LT_ENCODE = "&lt;".toCharArray();
    private static final char[] GT_ENCODE = "&gt;".toCharArray();
    
    // docs indicate this class is thread safe
    private static Base32 Base32Hex = new Base32(true);
    
    private StringUtils() {
        // Not instantiable.
    }

    /**
     * Replaces all instances of oldString with newString in string.
     *
     * @param string the String to search to perform replacements on.
     * @param oldString the String that should be replaced by newString.
     * @param newString the String that will replace all instances of oldString.
     * @return a String will all instances of oldString replaced by newString.
     * @deprecated Use {@link String#replaceAll(String, String)}}
     */
    @Deprecated
    public static String replace(String string, String oldString, String newString) {
        return replace(string, oldString, newString, new int[1]);
    }

    /**
     * Replaces all instances of oldString with newString in line with the
     * added feature that matches of newString in oldString ignore case.
     *
     * @param line      the String to search to perform replacements on
     * @param oldString the String that should be replaced by newString
     * @param newString the String that will replace all instances of oldString
     * @return a String will all instances of oldString replaced by newString
     */
    public static String replaceIgnoreCase(String line, String oldString,
                                                 String newString) {
        return replaceIgnoreCase(line, oldString, newString, new int[1]);
    }

    /**
     * Replaces all instances of oldString with newString in line with the
     * added feature that matches of newString in oldString ignore case.
     * The count paramater is set to the number of replaces performed.
     *
     * @param line      the String to search to perform replacements on
     * @param oldString the String that should be replaced by newString
     * @param newString the String that will replace all instances of oldString
     * @param count     a value that will be updated with the number of replaces
     *                  performed.
     * @return a String will all instances of oldString replaced by newString
     */
    public static String replaceIgnoreCase(String line, String oldString,
            String newString, int[] count) {
        return replace(line, oldString, newString, true, count);
    }

    /**
     * Replaces all instances of oldString with newString in line.
     * The count Integer is updated with number of replaces.
     *
     * @param line the String to search to perform replacements on.
     * @param oldString the String that should be replaced by newString.
     * @param newString the String that will replace all instances of oldString.
     * @param count a single element array that, after running, will contain the number of matching items
     * @return a String will all instances of oldString replaced by newString.
     */
    public static String replace(String line, String oldString,
            String newString, int[] count) {
        return replace(line, oldString, newString, false, count);
    }

    private static String replace(String line, String oldString,
                                 String newString, boolean ignoreCase, int[] count) {
        if (line == null) {
            return null;
        }
        String lcLine = ignoreCase ? line.toLowerCase() : line;
        String lcOldString = ignoreCase ? oldString.toLowerCase() : oldString;
        int i = 0;
        if ((i = lcLine.indexOf(lcOldString, i)) >= 0) {
            int counter = 1;
            char[] line2 = line.toCharArray();
            char[] newString2 = newString.toCharArray();
            int oLength = oldString.length();
            StringBuilder buf = new StringBuilder(line2.length);
            buf.append(line2, 0, i).append(newString2);
            i += oLength;
            int j = i;
            while ((i = lcLine.indexOf(lcOldString, i)) > 0) {
                counter++;
                buf.append(line2, j, i - j).append(newString2);
                i += oLength;
                j = i;
            }
            buf.append(line2, j, line2.length - j);
            count[0] = counter;
            return buf.toString();
        }
        return line;
    }

    /**
     * This method takes a string and strips out all tags except <br> tags while still leaving
     * the tag body intact.
     *
     * @param in the text to be converted.
     * @return the input string with all tags removed.
     */
    public static String stripTags(String in) {
        if (in == null) {
            return null;
        }
        char ch;
        int i = 0;
        int last = 0;
        char[] input = in.toCharArray();
        int len = input.length;
        StringBuilder out = new StringBuilder((int)(len * 1.3));
        for (; i < len; i++) {
            ch = input[i];
            if (ch > '>') {
            }
            else if (ch == '<') {
                if (i + 3 < len && input[i + 1] == 'b' && input[i + 2] == 'r' && input[i + 3] == '>') {
                    i += 3;
                    continue;
                }
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
            }
            else if (ch == '>') {
                last = i + 1;
            }
        }
        if (last == 0) {
            return in;
        }
        if (i > last) {
            out.append(input, last, i - last);
        }
        return out.toString();
    }

    /**
     * This method takes a string which may contain HTML tags (ie, &lt;b&gt;,
     * &lt;table&gt;, etc) and converts the '&lt;' and '&gt;' characters to
     * their HTML escape sequences. It will also replace LF  with &lt;br&gt;.
     *
     * @param in the text to be converted.
     * @return the input string with the characters '&lt;' and '&gt;' replaced
     *         with their HTML escape sequences.
     */
    public static String escapeHTMLTags(String in) {
        return escapeHTMLTags(in, true);
    }

    /**
     * This method takes a string which may contain HTML tags (ie, &lt;b&gt;,
     * &lt;table&gt;, etc) and converts the '&lt;' and '&gt;' characters to
     * their HTML escape sequences.
     *
     * @param in the text to be converted.
     * @param includeLF set to true to replace \n with <br>.
     * @return the input string with the characters '&lt;' and '&gt;' replaced
     *         with their HTML escape sequences.
     */
    public static String escapeHTMLTags(String in, boolean includeLF) {
        if (in == null) {
            return null;
        }
        char ch;
        int i = 0;
        int last = 0;
        char[] input = in.toCharArray();
        int len = input.length;
        StringBuilder out = new StringBuilder((int)(len * 1.3));
        for (; i < len; i++) {
            ch = input[i];
            if (ch > '>') {
            }
            else if (ch == '<') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append(LT_ENCODE);
            }
            else if (ch == '>') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append(GT_ENCODE);
            }
            else if (ch == '\n' && includeLF == true) {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append("<br>");
            }
        }
        if (last == 0) {
            return in;
        }
        if (i > last) {
            out.append(input, last, i - last);
        }
        return out.toString();
    }

    /**
     * Used by the hash method.
     */
    private static Map<String, MessageDigest> digests =
            new ConcurrentHashMap<>();

    /**
     * Hashes a String using the Md5 algorithm and returns the result as a
     * String of hexadecimal numbers. This method is synchronized to avoid
     * excessive MessageDigest object creation. If calling this method becomes
     * a bottleneck in your code, you may wish to maintain a pool of
     * MessageDigest objects instead of using this method.
     * <p>
     * A hash is a one-way function -- that is, given an
     * input, an output is easily computed. However, given the output, the
     * input is almost impossible to compute. This is useful for passwords
     * since we can store the hash and a hacker will then have a very hard time
     * determining the original password.</p>
     * <p>
     * In Jive, every time a user logs in, we simply
     * take their plain text password, compute the hash, and compare the
     * generated hash to the stored hash. Since it is almost impossible that
     * two passwords will generate the same hash, we know if the user gave us
     * the correct password or not. The only negative to this system is that
     * password recovery is basically impossible. Therefore, a reset password
     * method is used instead.</p>
     *
     * @param data the String to compute the hash of.
     * @return a hashed version of the passed-in String
     */
    public static String hash(String data) {
        return hash(data, "MD5");
    }

    /**
     * Hashes a String using the specified algorithm and returns the result as a
     * String of hexadecimal numbers. This method is synchronized to avoid
     * excessive MessageDigest object creation. If calling this method becomes
     * a bottleneck in your code, you may wish to maintain a pool of
     * MessageDigest objects instead of using this method.
     * <p>
     * A hash is a one-way function -- that is, given an
     * input, an output is easily computed. However, given the output, the
     * input is almost impossible to compute. This is useful for passwords
     * since we can store the hash and a hacker will then have a very hard time
     * determining the original password.</p>
     * <p>
     * In Jive, every time a user logs in, we simply
     * take their plain text password, compute the hash, and compare the
     * generated hash to the stored hash. Since it is almost impossible that
     * two passwords will generate the same hash, we know if the user gave us
     * the correct password or not. The only negative to this system is that
     * password recovery is basically impossible. Therefore, a reset password
     * method is used instead.</p>
     *
     * @param data the String to compute the hash of.
     * @param algorithm the name of the algorithm requested.
     * @return a hashed version of the passed-in String
     */
    public static String hash(String data, String algorithm) {
        return hash(data.getBytes(StandardCharsets.UTF_8), algorithm);
    }

    /**
     * Hashes a byte array using the specified algorithm and returns the result as a
     * String of hexadecimal numbers. This method is synchronized to avoid
     * excessive MessageDigest object creation. If calling this method becomes
     * a bottleneck in your code, you may wish to maintain a pool of
     * MessageDigest objects instead of using this method.
     * <p>
     * A hash is a one-way function -- that is, given an
     * input, an output is easily computed. However, given the output, the
     * input is almost impossible to compute. This is useful for passwords
     * since we can store the hash and a hacker will then have a very hard time
     * determining the original password.
     * </p>
     * <p>In Jive, every time a user logs in, we simply
     * take their plain text password, compute the hash, and compare the
     * generated hash to the stored hash. Since it is almost impossible that
     * two passwords will generate the same hash, we know if the user gave us
     * the correct password or not. The only negative to this system is that
     * password recovery is basically impossible. Therefore, a reset password
     * method is used instead.</p>
     *
     * @param bytes the byte array to compute the hash of.
     * @param algorithm the name of the algorithm requested.
     * @return a hashed version of the passed-in String
     */
    public static String hash(byte[] bytes, String algorithm) {
        synchronized (algorithm.intern()) {
            MessageDigest digest = digests.get(algorithm);
            if (digest == null) {
                try {
                    digest = MessageDigest.getInstance(algorithm);
                    digests.put(algorithm, digest);
                }
                catch (NoSuchAlgorithmException nsae) {
                    Log.error("Failed to load the " + algorithm + " MessageDigest. " +
                            "Jive will be unable to function normally.", nsae);
                    return null;
                }
            }
            // Now, compute hash.
            digest.update(bytes);
            return encodeHex(digest.digest());
        }
    }

    /**
     * Turns an array of bytes into a String representing each byte as an
     * unsigned hex number.
     *
     * @param bytes an array of bytes to convert to a hex-string
     * @return generated hex string
     */
    public static String encodeHex(byte[] bytes) {
        return DatatypeConverter.printHexBinary(bytes).toLowerCase();
    }

    /**
     * Turns a hex encoded string into a byte array. It is specifically meant
     * to "reverse" the toHex(byte[]) method.
     *
     * @param hex a hex encoded String to transform into a byte array.
     * @return a byte array representing the hex String[
     */
    public static byte[] decodeHex(String hex) {
        return DatatypeConverter.parseHexBinary(hex);
    }

    /**
     * Encodes a String as a base64 String.
     *
     * @param data a String to encode.
     * @return a base64 encoded String.
     */
    public static String encodeBase64(String data) {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        return encodeBase64(bytes);
    }

    /**
     * Encodes a byte array into a base64 String.
     *
     * @param data a byte array to encode.
     * @return a base64 encode String.
     */
    public static String encodeBase64(byte[] data) {
        // Encode the String. We pass in a flag to specify that line
        // breaks not be added. This is consistent with our previous base64
        // implementation. Section 2.1 of 3548 (base64 spec) also specifies
        // no line breaks by default.
        return Base64.encodeBytes(data, Base64.DONT_BREAK_LINES);
    }

    /**
     * Decodes a base64 String.
     *
     * @param data a base64 encoded String to decode.
     * @return the decoded String.
     */
    public static byte[] decodeBase64(String data) {
        return Base64.decode(data);
    }

    /**
     * Encodes a String as a base32 String using the base32hex profile.
     *
     * @param data a String to encode.
     * @return a base32 encoded String.
     */
    public static String encodeBase32(String data) {
        byte[] bytes = data == null ? null : data.getBytes(StandardCharsets.UTF_8);
        return encodeBase32(bytes);
    }

    /**
     * Encodes a byte array into a base32 String using the base32hex profile.
     * Implementation is case-insensitive and returns encoded strings in lower case.
     *
     * @param data a byte array to encode.
     * @return a base32 encode String.
     */
    public static String encodeBase32(byte[] data) {
        return data == null ? null : Base32Hex.encodeAsString(data).toLowerCase();
    }

    /**
     * Decodes a base32 String using the base32hex profile. Implementation
     * is case-insensitive and converts the given string to upper case before
     * decoding.
     *
     * @param data a base32 encoded String to decode.
     * @return the decoded String.
     */
    public static byte[] decodeBase32(String data) {
        return data == null ? null : Base32Hex.decode(data.toUpperCase());
    }
    
    /**
     * Validates a string to ensure all its bytes are in the Base32 alphabet.
     * Implementation is case-insensitive and converts the given string to 
     * upper case before evaluating.
     * 
     * @param data the string to test
     * @return True if the given string can be decoded using Base32
     */
    public static boolean isBase32(String data) {
        return data == null ? false : Base32Hex.isInAlphabet(data.toUpperCase());
    }

    /**
     * Converts a line of text into an array of lower case words using a
     * BreakIterator.wordInstance().<p>
     *
     * This method is under the Jive Open Source Software License and was
     * written by Mark Imbriaco.
     *
     * @param text a String of text to convert into an array of words
     * @return text broken up into an array of words.
     */
    public static String[] toLowerCaseWordArray(String text) {
        if (text == null || text.length() == 0) {
            return new String[0];
        }

        List<String> wordList = new ArrayList<>();
        BreakIterator boundary = BreakIterator.getWordInstance();
        boundary.setText(text);
        int start = 0;

        for (int end = boundary.next(); end != BreakIterator.DONE;
             start = end, end = boundary.next()) {
            String tmp = text.substring(start, end).trim();
            // Remove characters that are not needed.
            tmp = replace(tmp, "+", "");
            tmp = replace(tmp, "/", "");
            tmp = replace(tmp, "\\", "");
            tmp = replace(tmp, "#", "");
            tmp = replace(tmp, "*", "");
            tmp = replace(tmp, ")", "");
            tmp = replace(tmp, "(", "");
            tmp = replace(tmp, "&", "");
            if (tmp.length() > 0) {
                wordList.add(tmp);
            }
        }
        return wordList.toArray(new String[wordList.size()]);
    }

    /**
     * A cryptographically strong random number generator object for use with randomString().
     */
    private static Random randGen = new SecureRandom();

    /**
     * Array of numbers and letters of mixed case. Numbers appear in the list
     * twice so that there is a more equal chance that a number will be picked.
     * We can use the array to get a random number or letter by picking a random
     * array index.
     */
    private static char[] numbersAndLetters = ("0123456789abcdefghijklmnopqrstuvwxyz" +
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ").toCharArray();

    /**
     * Returns a random String of numbers and letters (lower and upper case)
     * of the specified length. The method uses a cryptographically strong
     * random number generator as provided by {@link SecureRandom}
     * <p>
     * The specified length must be at least one. If not, the method will return
     * null.</p>
     *
     * @param length the desired length of the random String to return.
     * @return a random String of numbers and letters of the specified length.
     */
    public static String randomString(int length) {
        if (length < 1) {
            return null;
        }
        // Create a char buffer to put random letters and numbers in.
        char[] randBuffer = new char[length];
        for (int i = 0; i < randBuffer.length; i++) {
            randBuffer[i] = numbersAndLetters[randGen.nextInt(numbersAndLetters.length)];
        }
        return new String(randBuffer);
    }

    /**
     * Intelligently chops a String at a word boundary (whitespace) that occurs
     * at the specified index in the argument or before. However, if there is a
     * newline character before <code>length</code>, the String will be chopped
     * there. If no newline or whitespace is found in <code>string</code> up to
     * the index <code>length</code>, the String will chopped at <code>length</code>.
     * <p>
     * For example, chopAtWord("This is a nice String", 10) will return
     * "This is a" which is the first word boundary less than or equal to 10
     * characters into the original String.</p>
     *
     * @param string the String to chop.
     * @param length the index in <code>string</code> to start looking for a
     *               whitespace boundary at.
     * @return a substring of <code>string</code> whose length is less than or
     *         equal to <code>length</code>, and that is chopped at whitespace.
     */
    public static String chopAtWord(String string, int length) {
        if (string == null || string.length() == 0) {
            return string;
        }

        char[] charArray = string.toCharArray();
        int sLength = string.length();
        if (length < sLength) {
            sLength = length;
        }

        // First check if there is a newline character before length; if so,
        // chop word there.
        for (int i = 0; i < sLength - 1; i++) {
            // Windows
            if (charArray[i] == '\r' && charArray[i + 1] == '\n') {
                return string.substring(0, i + 1);
            }
            // Unix
            else if (charArray[i] == '\n') {
                return string.substring(0, i);
            }
        }
        // Also check boundary case of Unix newline
        if (charArray[sLength - 1] == '\n') {
            return string.substring(0, sLength - 1);
        }

        // Done checking for newline, now see if the total string is less than
        // the specified chop point.
        if (string.length() < length) {
            return string;
        }

        // No newline, so chop at the first whitespace.
        for (int i = length - 1; i > 0; i--) {
            if (charArray[i] == ' ') {
                return string.substring(0, i).trim();
            }
        }

        // Did not find word boundary so return original String chopped at
        // specified length.
        return string.substring(0, length);
    }

    /**
     * Reformats a string where lines that are longer than {@code width}
     * are split apart at the earliest wordbreak or at maxLength, whichever is
     * sooner. If the width specified is less than 5 or greater than the input
     * Strings length the string will be returned as is.
     * <p>
     * Please note that this method can be lossy - trailing spaces on wrapped
     * lines may be trimmed.</p>
     *
     * @param input the String to reformat.
     * @param width the maximum length of any one line.
     * @param locale the local
     * @return a new String with reformatted as needed.
     */
    public static String wordWrap(String input, int width, Locale locale) {
        // protect ourselves
        if (input == null) {
            return "";
        }
        else if (width < 5) {
            return input;
        }
        else if (width >= input.length()) {
            return input;
        }

        // default locale
        if (locale == null) {
            locale = JiveGlobals.getLocale();
        }

        StringBuilder buf = new StringBuilder(input);
        boolean endOfLine = false;
        int lineStart = 0;

        for (int i = 0; i < buf.length(); i++) {
            if (buf.charAt(i) == '\n') {
                lineStart = i + 1;
                endOfLine = true;
            }

            // handle splitting at width character
            if (i > lineStart + width - 1) {
                if (!endOfLine) {
                    int limit = i - lineStart - 1;
                    BreakIterator breaks = BreakIterator.getLineInstance(locale);
                    breaks.setText(buf.substring(lineStart, i));
                    int end = breaks.last();

                    // if the last character in the search string isn't a space,
                    // we can't split on it (looks bad). Search for a previous
                    // break character
                    if (end == limit + 1) {
                        if (!Character.isWhitespace(buf.charAt(lineStart + end))) {
                            end = breaks.preceding(end - 1);
                        }
                    }

                    // if the last character is a space, replace it with a \n
                    if (end != BreakIterator.DONE && end == limit + 1) {
                        buf.replace(lineStart + end, lineStart + end + 1, "\n");
                        lineStart = lineStart + end;
                    }
                    // otherwise, just insert a \n
                    else if (end != BreakIterator.DONE && end != 0) {
                        buf.insert(lineStart + end, '\n');
                        lineStart = lineStart + end + 1;
                    }
                    else {
                        buf.insert(i, '\n');
                        lineStart = i + 1;
                    }
                }
                else {
                    buf.insert(i, '\n');
                    lineStart = i + 1;
                    endOfLine = false;
                }
            }
        }

        return buf.toString();
    }

    /**
     * Escapes all necessary characters in the String so that it can be used in SQL
     *
     * @param string the string to escape.
     * @return the string with appropriate characters escaped.
     */
    public static String escapeForSQL(String string) {
        if (string == null) {
            return null;
        }
        else if (string.length() == 0) {
            return string;
        }

        char ch;
        char[] input = string.toCharArray();
        int i = 0;
        int last = 0;
        int len = input.length;
        StringBuilder out = null;
        for (; i < len; i++) {
            ch = input[i];

            if (ch == '\'') {
                if (out == null) {
                     out = new StringBuilder(len + 2);
                }
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append('\'').append('\'');
            }
        }

        if (out == null) {
            return string;
        }
        else if (i > last) {
            out.append(input, last, i - last);
        }

        return out.toString();
    }

    /**
     * Escapes all necessary characters in the String so that it can be used
     * in an XML doc.
     *
     * @param string the string to escape.
     * @return the string with appropriate characters escaped.
     */
    public static String escapeForXML(String string) {
        if (string == null) {
            return null;
        }
        char ch;
        int i = 0;
        int last = 0;
        char[] input = string.toCharArray();
        int len = input.length;
        StringBuilder out = new StringBuilder((int)(len * 1.3));
        for (; i < len; i++) {
            ch = input[i];
            if (ch > '>') {
            }
            else if (ch == '<') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append(LT_ENCODE);
            }
            else if (ch == '&') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append(AMP_ENCODE);
            }
            else if (ch == '"') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append(QUOTE_ENCODE);
            }
        }
        if (last == 0) {
            return string;
        }
        if (i > last) {
            out.append(input, last, i - last);
        }
        return out.toString();
    }

    /**
     * Unescapes the String by converting XML escape sequences back into normal
     * characters.
     *
     * @param string the string to unescape.
     * @return the string with appropriate characters unescaped.
     */
    public static String unescapeFromXML(String string) {
        string = replace(string, "&lt;", "<");
        string = replace(string, "&gt;", ">");
        string = replace(string, "&quot;", "\"");
        return replace(string, "&amp;", "&");
    }

    private static final char[] zeroArray =
            "0000000000000000000000000000000000000000000000000000000000000000".toCharArray();

    /**
     * Pads the supplied String with 0's to the specified length and returns
     * the result as a new String. For example, if the initial String is
     * "9999" and the desired length is 8, the result would be "00009999".
     * This type of padding is useful for creating numerical values that need
     * to be stored and sorted as character data. Note: the current
     * implementation of this method allows for a maximum {@code length} of
     * 64.
     *
     * @param string the original String to pad.
     * @param length the desired length of the new padded String.
     * @return a new String padded with the required number of 0's.
     */
    public static String zeroPadString(String string, int length) {
        if (string == null || string.length() > length) {
            return string;
        }
        StringBuilder buf = new StringBuilder(length);
        buf.append(zeroArray, 0, length - string.length()).append(string);
        return buf.toString();
    }

    /**
     * Formats a Date as a fifteen character long String made up of the Date's
     * padded millisecond value.
     * @param date the date to encode
     * @return a Date encoded as a String.
     */
    public static String dateToMillis(Date date) {
        return zeroPadString(Long.toString(date.getTime()), 15);
    }

    /**
     * Returns a textual representation for the time that has elapsed.
     *
     * @param delta the elapsed time in milliseconds
     * @return textual representation for the time that has elapsed.
     */
    public static String getFullElapsedTime(final long delta) {
        if (delta < JiveConstants.SECOND) {
            return String.format("%d %s", delta, delta == 1 ? LocaleUtils.getLocalizedString("global.millisecond") : LocaleUtils.getLocalizedString("global.milliseconds"));
        } else if (delta < JiveConstants.MINUTE) {
            final long millis = delta % JiveConstants.SECOND;
            final long seconds = delta / JiveConstants.SECOND;
            final String secondsString = String.format("%d %s", seconds, seconds == 1 ? LocaleUtils.getLocalizedString("global.second") : LocaleUtils.getLocalizedString("global.seconds"));
            if (millis > 0) {
                return secondsString + ", " + getFullElapsedTime(millis);
            } else {
                return secondsString;
            }
        } else if (delta < JiveConstants.HOUR) {
            final long millis = delta % JiveConstants.MINUTE;
            final long minutes = delta / JiveConstants.MINUTE;
            final String minutesString = String.format("%d %s", minutes, minutes == 1 ? LocaleUtils.getLocalizedString("global.minute") : LocaleUtils.getLocalizedString("global.minutes"));
            if (millis > 0) {
                return minutesString + ", " + getFullElapsedTime(millis);
            } else {
                return minutesString;
            }
        } else if (delta < JiveConstants.DAY) {
            final long millis = delta % JiveConstants.HOUR;
            final long hours = delta / JiveConstants.HOUR;
            final String daysString = String.format("%d %s", hours, hours == 1 ? LocaleUtils.getLocalizedString("global.hour") : LocaleUtils.getLocalizedString("global.hours"));
            if (millis > 0) {
                return daysString + ", " + getFullElapsedTime(millis);
            } else {
                return daysString;
            }
        } else {
            final long millis = delta % JiveConstants.DAY;
            final long days = delta / JiveConstants.DAY;
            final String daysString = String.format("%d %s", days, days == 1 ? LocaleUtils.getLocalizedString("global.day") : LocaleUtils.getLocalizedString("global.days"));
            if (millis > 0) {
                return daysString + ", " + getFullElapsedTime(millis);
            } else {
                return daysString;
            }
        }
    }

    /**
     * Returns a textual representation for the time that has elapsed.
     *
     * @param delta the elapsed time.
     * @return textual representation for the time that has elapsed.
     */
    public static String getFullElapsedTime(final Duration delta) {
        return getFullElapsedTime(delta.toMillis());
    }

    /**
     * Returns a textual representation for the time that has elapsed.
     *
     * @param delta the elapsed time in milliseconds.
     * @return textual representation for the time that has elapsed.
     */
    public static String getElapsedTime(long delta) {
        if (delta < JiveConstants.MINUTE) {
            return LocaleUtils.getLocalizedString("global.less-minute");
        }
        else if (delta < JiveConstants.HOUR) {
            long mins = delta / JiveConstants.MINUTE;
            StringBuilder sb = new StringBuilder();
            sb.append(mins).append(' ');
            sb.append((mins==1) ? LocaleUtils.getLocalizedString("global.minute") : LocaleUtils.getLocalizedString("global.minutes"));
            return sb.toString();
        }
        else if (delta < JiveConstants.DAY) {
            long hours = delta / JiveConstants.HOUR;
            delta -= hours * JiveConstants.HOUR;
            long mins = delta / JiveConstants.MINUTE;
            StringBuilder sb = new StringBuilder();
            sb.append(hours).append(' ');
            sb.append((hours == 1) ? LocaleUtils.getLocalizedString("global.hour") : LocaleUtils.getLocalizedString("global.hours"));
            sb.append(", ");
            sb.append(mins).append(' ');
            sb.append((mins == 1) ? LocaleUtils.getLocalizedString("global.minute") : LocaleUtils.getLocalizedString("global.minutes"));
            return sb.toString();
        } else {
            long days = delta / JiveConstants.DAY;
            delta -= days * JiveConstants.DAY;
            long hours = delta / JiveConstants.HOUR;
            delta -= hours * JiveConstants.HOUR;
            long mins = delta / JiveConstants.MINUTE;
            StringBuilder sb = new StringBuilder();
            sb.append(days).append(' ');
            sb.append((days == 1) ? LocaleUtils.getLocalizedString("global.day") : LocaleUtils.getLocalizedString("global.days"));
            sb.append(", ");
            sb.append(hours).append(' ');
            sb.append((hours == 1) ? LocaleUtils.getLocalizedString("global.hour") : LocaleUtils.getLocalizedString("global.hours"));
            sb.append(", ");
            sb.append(mins).append(' ');
            sb.append((mins == 1) ? LocaleUtils.getLocalizedString("global.minute") : LocaleUtils.getLocalizedString("global.minutes"));
            return sb.toString();
        }
    }

    /**
     * Returns a formatted String from time.
     *
     * @param diff the amount of elapsed time.
     * @return the formatte String.
     */
    public static String getTimeFromLong(long diff) {
        final String HOURS = "h";
        final String MINUTES = "min";
        //final String SECONDS = "sec";

        final long MS_IN_A_DAY = 1000 * 60 * 60 * 24;
        final long MS_IN_AN_HOUR = 1000 * 60 * 60;
        final long MS_IN_A_MINUTE = 1000 * 60;
        final long MS_IN_A_SECOND = 1000;
        //Date currentTime = new Date();
        //long numDays = diff / MS_IN_A_DAY;
        diff = diff % MS_IN_A_DAY;
        long numHours = diff / MS_IN_AN_HOUR;
        diff = diff % MS_IN_AN_HOUR;
        long numMinutes = diff / MS_IN_A_MINUTE;
        diff = diff % MS_IN_A_MINUTE;
        //long numSeconds = diff / MS_IN_A_SECOND;
        diff = diff % MS_IN_A_SECOND;
        //long numMilliseconds = diff;

        StringBuilder buf = new StringBuilder();
        if (numHours > 0) {
            buf.append(numHours).append(' ').append(HOURS).append(", ");
        }

        if (numMinutes > 0) {
            buf.append(numMinutes).append(' ').append(MINUTES);
        }

        //buf.append(numSeconds + " " + SECONDS);

        String result = buf.toString();

        if (numMinutes < 1) {
            result = "< 1 minute";
        }

        return result;
    }

    /**
     * Returns a collection of Strings as a comma-delimitted list of strings.
     * @param collection the collection of strings
     * @return a String representing the Collection.
     */
    public static String collectionToString(Collection<String> collection) {
        if (collection == null || collection.isEmpty()) {
            return "";
        }
        StringBuilder buf = new StringBuilder();
        String delim = "";
        for (String element : collection) {
            buf.append(delim);
            buf.append(element);
            delim = ",";
        }
        return buf.toString();
    }

    /**
     * Returns a comma-delimitted list of Strings as a Collection.
     * @param string the string to split
     * @return a Collection representing the String.
     */
    public static Collection<String> stringToCollection(String string) {
        if (string == null || string.trim().length() == 0) {
            return Collections.emptyList();
        }
        Collection<String> collection = new ArrayList<>();
        StringTokenizer tokens = new StringTokenizer(string, ",");
        while (tokens.hasMoreTokens()) {
            collection.add(tokens.nextToken().trim());
        }
        return collection;
    }

    /**
     * Returns true if the given string is in the given array.
     * 
     * @param array an array of Strings to check
     * @param item the item to look for
     * @return true if the array contains the item
     */
    public static boolean contains(String[] array, String item) {
        if (array == null || array.length == 0 || item == null) {
            return false;
        }
        for (String anArray : array) {
            if (item.equals(anArray)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Abbreviates a string to a specified length and then adds an ellipsis
     * if the input is greater than the maxWidth. Example input:
     * <pre>
     *      user1@jivesoftware.com/home
     * </pre>
     * and a maximum length of 20 characters, the abbreviate method will return:
     * <pre>
     *      user1@jivesoftware.c...
     * </pre>
     * @param str the String to abbreviate.
     * @param maxWidth the maximum size of the string, minus the ellipsis.
     * @return the abbreviated String, or {@code null} if the string was {@code null}.
     */
    public static String abbreviate(String str, int maxWidth) {
        if (null == str) {
            return null;
        }

        if (str.length() <= maxWidth) {
            return str;
        }
        
        return str.substring(0, maxWidth) + "...";
    }

    /**
     * Returns true if the string passed in is a valid Email address.
     *
     * @param address Email address to test for validity.
     * @return true if the string passed in is a valid email address.
     */
    public static boolean isValidEmailAddress(String address) {
        if (address == null) {
            return false;
        }

        if (!address.contains("@")) {
            return false;
        }

        try {
            InternetAddress.parse(address);
            return true;
        }
        catch (AddressException e) {
            return false;
        }
    }
    
    /**
     * Returns a valid domain name, possibly as an ACE-encoded IDN 
     * (per <a href="http://www.ietf.org/rfc/rfc3490.txt">RFC 3490</a>).
     * 
     * @param domain Proposed domain name
     * @return The validated domain name, possibly ACE-encoded
     * @throws IllegalArgumentException The given domain name is not valid
     */
    public static String validateDomainName(String domain) {
        if (domain == null || domain.trim().length() == 0) {
            throw new IllegalArgumentException("Domain name cannot be null or empty");
        }
        String result = IDN.toASCII(domain);
        if (result.equals(domain)) {
            // no conversion; validate again via USE_STD3_ASCII_RULES
            IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES);
        } else {
            Log.info(MessageFormat.format("Converted domain name: from '{0}' to '{1}'",  domain, result));
        }
        return result;
    }
    
    /**
     * Removes characters likely to enable Cross Site Scripting attacks from the
     * provided input string. The characters that are removed from the input
     * string, if present, are:
     * 
     * <pre>
     * &lt; &gt; &quot; ' % ; ) ( &amp; + -
     * </pre>
     * 
     * @param input the string to be scrubbed
     * @return Input without certain characters;
     */
    public static String removeXSSCharacters(String input) {
        final String[] xss = { "<", ">", "\"", "'", "%", ";", ")", "(", "&",
                "+", "-" };
        for (int i = 0; i < xss.length; i++) {
            input = input.replace(xss[i], "");
        }
        return input;
    }
    
    /**
     * Returns the UTF-8 bytes for the given String.
     * 
     * @param input The source string
     * @return The UTF-8 encoding for the given string
     * @deprecated Use {@code input.getBytes(StandardCharsets.UTF_8)}
     */
    @Deprecated
    public static byte[] getBytes(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * Returns the UTF-8 String for the given byte array.
     * 
     * @param input The source byte array
     * @return The UTF-8 encoded String for the given byte array
     * @deprecated Use {@code new String(input, StandardCharsets.UTF_8)}
     */
    @Deprecated
    public static String getString(byte[] input) {
        return new String(input, StandardCharsets.UTF_8);
    }


    /**
     * @param nullableString the string to check
     * @param value          the string to match against
     * @return {@code true} is nullableString is not null and contains the supplied value, otherwise {@code false}
     */
    public static boolean containsIgnoringCase(final String nullableString, final String value) {
        if (nullableString != null) {
            return nullableString.toLowerCase().contains(value.toLowerCase());
        } else {
            return false;
        }
    }

    public static Optional<Integer> parseInteger(final String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.valueOf(value));
        } catch (final NumberFormatException ex) {
            Log.trace("An exception occurred while trying to parse value '{}' as an integer. Will return empty.", value, ex);
            return Optional.empty();
        }
    }

    public static Optional<Long> parseLong(final String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(value));
        } catch (final NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<Double> parseDouble(final String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.valueOf(value));
        } catch (final NumberFormatException ex) {
            Log.trace("An exception occurred while trying to parse value '{}' as long. Will return empty.", value, ex);
            return Optional.empty();
        }
    }

    /**
     * Parses a boolean. Subtly different from Boolean.parseBoolean in that it returns {@code Optional.empty()}
     * instead of {@code false} if the supplied value is not "true" or "false" (ignoring case)
     * @param value Any string value
     * @return {@code true}, {@code false} or {@code Optional.empty()}
     */
    public static Optional<Boolean> parseBoolean(final String value) {
        if("true".equalsIgnoreCase(value)) {
            return Optional.of(Boolean.TRUE);
        } else if("false".equalsIgnoreCase(value)) {
            return Optional.of(Boolean.FALSE);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Simple Java program to tokenize string as a shell would - similar to shlex in Python
     *
     * @param string The string to be split.
     * @return The splitted string.
     * @see <a href="https://gist.github.com/raymyers/8077031">https://gist.github.com/raymyers/8077031</a>
     */
    public static List<String> shellSplit(CharSequence string) {
        List<String> tokens = new ArrayList<>();
        if ( string == null ) {
            return tokens;
        }
        boolean escaping = false;
        char quoteChar = ' ';
        boolean quoting = false;
        StringBuilder current = new StringBuilder() ;
        for (int i = 0; i<string.length(); i++) {
            char c = string.charAt(i);
            if (escaping) {
                current.append(c);
                escaping = false;
            } else if (c == '\\' && !(quoting && quoteChar == '\'')) {
                escaping = true;
            } else if (quoting && c == quoteChar) {
                quoting = false;
            } else if (!quoting && (c == '\'' || c == '"')) {
                quoting = true;
                quoteChar = c;
            } else if (!quoting && Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}

/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.nio;

import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a Light-Weight XML Parser.
 * It read data from a channel and collect data until data are available in
 * the channel.
 * When a message is complete you can retrieve messages invoking the method
 * getMsgs() and you can invoke the method areThereMsgs() to know if at least
 * an message is presents.
 *
 * @author Daniele Piras
 * @author Gaston Dombiak
 */
public class XMLLightweightParser {

    private static final Logger Log = LoggerFactory.getLogger(XMLLightweightParser.class);

    public static final SystemProperty<Long> XMPP_PARSER_BUFFER_SIZE = SystemProperty.Builder.ofType(Long.class)
        .setKey("xmpp.parser.buffer.size")
        .setDynamic(true)
        .setDefaultValue(1048576L)
        .setMinValue(32L)
        .build();

    // Chars that represent CDATA section start
    protected static char[] CDATA_START = {'<', '!', '[', 'C', 'D', 'A', 'T', 'A', '['};
    // Chars that represent CDATA section end
    protected static char[] CDATA_END = {']', ']', '>'};

    // Buffer with all data retrieved
    protected StringBuilder buffer = new StringBuilder();

    // ---- INTERNAL STATUS -------
    // Initial status
    protected static final int INIT = 0;
    // Status used when the first tag name is retrieved
    protected static final int HEAD = 2;
    // Status used when robot is inside the xml and it looking for the tag conclusion
    protected static final int INSIDE = 3;
    // Status used when a '<' is found and try to find the conclusion tag.
    protected static final int PRETAIL = 4;
    // Status used when the ending tag is equal to the head tag
    protected static final int TAIL = 5;
    // Status used when robot is inside the main tag and found an '/' to check '/>'.
    protected static final int VERIFY_CLOSE_TAG = 6;
    //  Status used when you are inside a parameter
    protected static final int INSIDE_PARAM_VALUE = 7;
    //  Status used when you are inside a cdata section
    protected static final int INSIDE_CDATA = 8;
    // Status used when you are outside a tag/reading text
    protected static final int OUTSIDE = 9;
    
    final String[] sstatus = {"INIT", "", "HEAD", "INSIDE", "PRETAIL", "TAIL", "VERIFY", "INSIDE_PARAM", "INSIDE_CDATA", "OUTSIDE"};


    // Current robot status
    protected int status = XMLLightweightParser.INIT;

    // Index to looking for a CDATA section start or end.
    protected int cdataOffset = 0;

    // Number of chars that machs with the head tag. If the tailCount is equal to
    // the head length so a close tag is found.
    protected int tailCount = 0;
    // Indicate the starting point in the buffer for the next message.
    protected int startLastMsg = 0;
    // Flag used to discover tag in the form <tag />.
    protected boolean insideRootTag = false;
    // Object containing the head tag
    protected StringBuilder head = new StringBuilder();
    // List with all finished messages found.
    protected List<String> msgs = new ArrayList<>();
    private int depth = 0;
    private boolean maxBufferSizeExceeded = false;

    protected boolean insideChildrenTag = false;

    /*
    * true if the parser has found some complete xml message.
    */
    public boolean areThereMsgs() {
        return (msgs.size() > 0);
    }

    /*
    * @return an array with all messages found
    */
    public String[] getMsgs() {
        String[] res = new String[msgs.size()];
        for (int i = 0; i < res.length; i++) {
            res[i] = msgs.get(i);
        }
        msgs.clear();
        invalidateBuffer();
        return res;
    }

    /*
    * Method use to re-initialize the buffer
    */
    protected void invalidateBuffer() {
        if (buffer.length() > 0) {
            String str = buffer.substring(startLastMsg);
            buffer.delete(0, buffer.length());
            buffer.append(str);
            buffer.trimToSize();
        }
        startLastMsg = 0;
    }


    /*
    * Method that add a message to the list and reinit parser.
    */
    protected void foundMsg(String msg) throws XMLNotWellFormedException {
        // Add message to the complete message list
        if (msg != null) {
            if (hasIllegalCharacterReferences(msg)) {
                buffer = null;
                throw new XMLNotWellFormedException("Illegal character reference found in: " + msg);
            }
            msgs.add(msg);
        }
        // Move the position into the buffer
        status = XMLLightweightParser.INIT;
        tailCount = 0;
        cdataOffset = 0;
        head.setLength(0);
        insideRootTag = false;
        insideChildrenTag = false;
        depth = 0;
    }

    public boolean isMaxBufferSizeExceeded() {
        return maxBufferSizeExceeded;
    }

    public void read(char[] buf) throws Exception {
        invalidateBuffer();
        // Check that the buffer is not bigger than 1 Megabyte. For security reasons
        // we will abort parsing when 1 Mega of queued chars was found.
        if (buffer.length() > XMPP_PARSER_BUFFER_SIZE.getValue()) {
            Log.debug("Stanza that has filled the XML parser buffer:\n" + buffer.substring(0, (int) Math.min(XMPP_PARSER_BUFFER_SIZE.getValue(), 1024) ) + "...(truncated)");
            // set flag to inform higher level network decoders to stop reading more data
            maxBufferSizeExceeded = true;
            // purge the local buffer / free memory
            buffer = null;
            // processing the exception takes quite long
            throw new InboundBufferSizeException("Stopped parsing never ending stanza");
        }

        int readChar = buf.length;

        // Just return if nothing was read
        if (readChar == 0) {
            return;
        }

        buffer.append(buf);

        // Robot.
        char ch;
        boolean isHighSurrogate = false;
        for (int i = 0; i < readChar; i++) {
            ch = buf[i];
            if (ch < 0x20 && ch != 0x9 && ch != 0xA && ch != 0xD) {
                 //Unicode characters in the range 0x0000-0x001F other than 9, A, and D are not allowed in XML
                buffer = null;
                throw new XMLNotWellFormedException("Character is invalid in: " + ch);
            }
            if (isHighSurrogate) {
                if (Character.isLowSurrogate(ch)) {
                    // Everything is fine. Clean up traces for surrogates
                    isHighSurrogate = false;
                }
                else {
                    // Trigger error. Found high surrogate not followed by low surrogate
                    buffer = null;
                    throw new Exception("Found high surrogate not followed by low surrogate");
                }
            }
            else if (Character.isHighSurrogate(ch)) {
                isHighSurrogate = true;
            }
            else if (Character.isLowSurrogate(ch)) {
                // Trigger error. Found low surrogate char without a preceding high surrogate
                buffer = null;
                throw new Exception("Found low surrogate char without a preceding high surrogate");
            }
            if (status == XMLLightweightParser.TAIL) {
                // Looking for the close tag
                if (depth < 1 && ch == head.charAt(tailCount)) {
                    tailCount++;
                    if (tailCount == head.length()) {
                        // Close stanza found!
                        // Calculate the correct start,end position of the message into the buffer
                        int end = buffer.length() - readChar + (i + 1);
                        String msg = buffer.substring(startLastMsg, end);
                        // Add message to the list
                        foundMsg(msg);
                        startLastMsg = end;
                    }
                } else {
                    tailCount = 0;
                    status = XMLLightweightParser.INSIDE;
                }
            } else if (status == XMLLightweightParser.PRETAIL) {
                if (ch == XMLLightweightParser.CDATA_START[cdataOffset]) {
                    cdataOffset++;
                    if (cdataOffset == XMLLightweightParser.CDATA_START.length) {
                        status = XMLLightweightParser.INSIDE_CDATA;
                        cdataOffset = 0;
                        continue;
                    }
                } else {
                    cdataOffset = 0;
                    status = XMLLightweightParser.INSIDE;
                }
                if (ch == '/') {
                    status = XMLLightweightParser.TAIL;
                    depth--;
                }
                else if (ch == '!') {
                    // This is a <! (comment) so ignore it
                    status = XMLLightweightParser.INSIDE;
                }
                else {
                    depth++;
                }
            } else if (status == XMLLightweightParser.VERIFY_CLOSE_TAG) {
                if (ch == '>') {
                    depth--;
                    status = XMLLightweightParser.OUTSIDE;
                    if (depth < 1) {
                        // Found a tag in the form <tag />
                        int end = buffer.length() - readChar + (i + 1);
                        String msg = buffer.substring(startLastMsg, end);
                        // Add message to the list
                        foundMsg(msg);
                        startLastMsg = end;
                    }
                } else if (ch == '<') {
                    status = XMLLightweightParser.PRETAIL;
                    insideChildrenTag = true;
                } else {
                    status = XMLLightweightParser.INSIDE;
                }
            } else if (status == XMLLightweightParser.INSIDE_PARAM_VALUE) {

                if (ch == '"') {
                    status = XMLLightweightParser.INSIDE;
                }
            } else if (status == XMLLightweightParser.INSIDE_CDATA) {
                if (ch == XMLLightweightParser.CDATA_END[cdataOffset]) {
                    cdataOffset++;
                    if (cdataOffset == XMLLightweightParser.CDATA_END.length) {
                        status = XMLLightweightParser.OUTSIDE;
                        cdataOffset = 0;
                    }
                } else if (cdataOffset == XMLLightweightParser.CDATA_END.length-1 && ch == XMLLightweightParser.CDATA_END[cdataOffset - 1]) {
                    // if we are looking for the last CDATA_END char, and we instead found an extra ']'
                    // char, leave cdataOffset as is and proceed to the next char. This could be a case
                    // where the XML character data ends with multiple square braces. For Example ]]]>
                } else {
                    cdataOffset = 0;
                }
            } else if (status == XMLLightweightParser.INSIDE) {
                if (ch == XMLLightweightParser.CDATA_START[cdataOffset]) {
                    cdataOffset++;
                    if (cdataOffset == XMLLightweightParser.CDATA_START.length) {
                        status = XMLLightweightParser.INSIDE_CDATA;
                        cdataOffset = 0;
                        continue;
                    }
                } else {
                    cdataOffset = 0;
                    status = XMLLightweightParser.INSIDE;
                }
                if (ch == '"') {
                    status = XMLLightweightParser.INSIDE_PARAM_VALUE;
                } else if (ch == '>') {
                    status = XMLLightweightParser.OUTSIDE;
                    if (insideRootTag && (head.length() == 14 || head.length() == 5 || head.length() == 13)) {
                        final String headString = head.toString();
                        if ("stream:stream>".equals(headString)
                            || "?xml>".equals(headString)) {
                            // Found closing stream:stream
                            int end = buffer.length() - readChar + (i + 1);
                            // Skip LF, CR and other "weird" characters that could appear
                            while (startLastMsg < end && '<' != buffer.charAt(startLastMsg)) {
                                startLastMsg++;
                            }
                            String msg = buffer.substring(startLastMsg, end);
                            foundMsg(msg);
                            startLastMsg = end;
                        }
                    }
                    insideRootTag = false;
                } else if (ch == '/') {
                    status = XMLLightweightParser.VERIFY_CLOSE_TAG;
                }
            } else if (status == XMLLightweightParser.HEAD) {
                if (Character.isWhitespace(ch) || ch == '>') {
                    // Append > to head to allow searching </tag>
                    head.append('>');
                    if(ch == '>')
                        status = XMLLightweightParser.OUTSIDE;
                    else
                        status = XMLLightweightParser.INSIDE;
                    insideRootTag = true;
                    insideChildrenTag = false;
                    continue;
                }
                else if (ch == '/' && head.length() > 0) {
                    status = XMLLightweightParser.VERIFY_CLOSE_TAG;
                    depth--;
                }
                head.append(ch);

            } else if (status == XMLLightweightParser.INIT) {
                if (ch == '<') {
                    status = XMLLightweightParser.HEAD;
                    depth = 1;
                }
                else {
                    startLastMsg++;
                }
            } else if (status == XMLLightweightParser.OUTSIDE) {
                if (ch == '<') {
                    status = XMLLightweightParser.PRETAIL;
                    cdataOffset = 1;
                    insideChildrenTag = true;
                }
            }
        }
        if (head.length() == 15 || head.length() == 14) {
            final String headString = head.toString();
            if ("/stream:stream>".equals(headString)) {
                foundMsg("</stream:stream>");
            }
        }
    }

    /**
     * This method verifies if the provided argument contains at least one numeric character reference (
     * <code>CharRef	   ::=   	'&#' [0-9]+ ';' | '&#x' [0-9a-fA-F]+ ';</code>) for which the decimal or hexidecimal
     * character value refers to an invalid XML 1.0 character.
     * 
     * @param string
     *            The input string
     * @return {@code true} if the input string contains an invalid numeric character reference, {@code false}
     *         otherwise.
     * @see <a href="https://www.w3.org/TR/2008/REC-xml-20081126/#dt-charref">Definition of a character reference</a>
     */
    public static boolean hasIllegalCharacterReferences(String string) {
        int needle = 0;
        while (needle < string.length()) {
            final int start = string.indexOf("&#", needle);
            if (start == -1) {
                return false;
            }
            final int end = string.indexOf(";", start + 2);
            if (end == -1) {
                return false;
            }
            needle = end;

            final boolean isHex = string.charAt(start + 2) == 'x' || string.charAt(start + 2) == 'X';

            final String candidate;
            final int radix;
            if (isHex) {
                candidate = string.substring(start + 3, end);
                radix = 16;
            } else {
                candidate = string.substring(start + 2, end);
                radix = 10;
            }

            try {
                final int value = Integer.parseInt(candidate, radix);
                if (!isLegalXmlCharacter(value)) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // The 'candidate' value wasn't a numeric character reference.
            }
        }

        return false;
    }

    /**
     * Verifies if the codepoint value represents a valid character as defined in paragraph 2.2 of
     * "Extensible Markup Language (XML) 1.0 (Fifth Edition)"
     * 
     * @param value
     *            the codepoint
     * @return {@code true} if the codepoint is a valid character per XML 1.0 definition, {@code false} otherwise.
     *
     * @see <a href="https://www.w3.org/TR/2008/REC-xml-20081126/#NT-Char">Definition of a characters range</a>
     */
    public static boolean isLegalXmlCharacter(int value) {
        return value == 0x9 || value == 0xA || value == 0xD || (value >= 0x20 && value <= 0xD7FF)
                || (value >= 0xE000 && value <= 0xFFFD) || (value >= 0x10000 && value <= 0x10FFFF);
    }
}

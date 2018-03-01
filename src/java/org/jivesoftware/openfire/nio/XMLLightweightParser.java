/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.filter.codec.ProtocolDecoderException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;

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
class XMLLightweightParser {

    private static final Pattern XML_HAS_CHARREF = Pattern.compile("&#(0*([0-9]+)|[xX]0*([0-9a-fA-F]+));");

    private static final String MAX_PROPERTY_NAME = "xmpp.parser.buffer.size";
    private static int maxBufferSize;
    // Chars that rappresent CDATA section start
    protected static char[] CDATA_START = {'<', '!', '[', 'C', 'D', 'A', 'T', 'A', '['};
    // Chars that rappresent CDATA section end
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
    // Object conteining the head tag
    protected StringBuilder head = new StringBuilder(5);
    // List with all finished messages found.
    protected List<String> msgs = new ArrayList<>();
    private int depth = 0;

    protected boolean insideChildrenTag = false;

    CharsetDecoder encoder;

    static {
        // Set default max buffer size to 1MB. If limit is reached then close connection
        maxBufferSize = JiveGlobals.getIntProperty(MAX_PROPERTY_NAME, 1048576);
        // Listen for changes to this property
        PropertyEventDispatcher.addListener(new PropertyListener());
    }

    public XMLLightweightParser(Charset charset) {
        encoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

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

    /*
    * Main reading method
    */
    public void read(IoBuffer byteBuffer) throws Exception {
        if (buffer == null) {
            // exception was thrown before, avoid duplicate exception(s)
            // "read" and discard remaining data
            byteBuffer.position(byteBuffer.limit());
            return;
        }
        invalidateBuffer();
        // Check that the buffer is not bigger than 1 Megabyte. For security reasons
        // we will abort parsing when 1 Mega of queued chars was found.
        if (buffer.length() > maxBufferSize) {
            // purge the local buffer / free memory
            buffer = null;
            // processing the exception takes quite long
            final ProtocolDecoderException ex = new ProtocolDecoderException("Stopped parsing never ending stanza");
            ex.setHexdump("(redacted hex dump of never ending stanza)");
            throw ex;
        }
        CharBuffer charBuffer = CharBuffer.allocate(byteBuffer.capacity());
        encoder.reset();
        encoder.decode(byteBuffer.buf(), charBuffer, false);
        char[] buf = new char[charBuffer.position()];
        charBuffer.flip();
        charBuffer.get(buf);
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
            if (ch < 0x20 && ch != 0x9 && ch != 0xA && ch != 0xD && ch != 0x0) {
                 //Unicode characters in the range 0x0000-0x001F other than 9, A, and D are not allowed in XML
                 //We need to allow the NULL character, however, for Flash XMLSocket clients to work.
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
                    if (insideRootTag && ("stream:stream>".equals(head.toString()) ||
                            ("?xml>".equals(head.toString())) || ("flash:stream>".equals(head.toString())))) {
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
                    insideRootTag = false;
                } else if (ch == '/') {
                    status = XMLLightweightParser.VERIFY_CLOSE_TAG;
                }
            } else if (status == XMLLightweightParser.HEAD) {
                if (ch == ' ' || ch == '>') {
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
        if (head.length() > 0 &&
                ("/stream:stream>".equals(head.toString()) || ("/flash:stream>".equals(head.toString())))) {
            // Found closing stream:stream
            foundMsg("</stream:stream>");
        }
    }

    /**
     * This method verifies if the provided argument contains at least one numeric character reference (
     * <code>CharRef	   ::=   	'&#' [0-9]+ ';' | '&#x' [0-9a-fA-F]+ ';</code>) for which the decimal or hexidecimal
     * character value refers to an invalid XML 1.0 character.
     * 
     * @param string
     *            The input string
     * @return <tt>true</tt> if the input string contains an invalid numeric character reference, <tt>false</tt>
     *         otherwise.
     * @see http://www.w3.org/TR/2008/REC-xml-20081126/#dt-charref
     */
    public static boolean hasIllegalCharacterReferences(String string) {
        // If there's no character reference, don't bother to do more specific checking.
        final Matcher matcher = XML_HAS_CHARREF.matcher(string);

        while (matcher.find()) {
            final String decValue = matcher.group(2);
            if (decValue != null) {
                final int value = Integer.parseInt(decValue);
                if (!isLegalXmlCharacter(value)) {
                    return true;
                } else {
                    continue;
                }
            }

            final String hexValue = matcher.group(3);
            if (hexValue != null) {
                final int value = Integer.parseInt(hexValue, 16);
                if (!isLegalXmlCharacter(value)) {
                    return true;
                } else {
                    continue;
                }
            }

            // This is bad. The XML_HAS_CHARREF expression should have a hit for either the decimal
            // or the heximal notation.
            throw new IllegalStateException(
                    "An error occurred while searching for illegal character references in the value [" + string + "].");
        }

        return false;
    }

    /**
     * Verifies if the codepoint value represents a valid character as defined in paragraph 2.2 of
     * "Extensible Markup Language (XML) 1.0 (Fifth Edition)"
     * 
     * @param value
     *            the codepoint
     * @return <tt>true</tt> if the codepoint is a valid charater per XML 1.0 definition, <tt>false</tt> otherwise.
     * @see http://www.w3.org/TR/2008/REC-xml-20081126/#NT-Char
     */
    public static boolean isLegalXmlCharacter(int value) {
        return value == 0x9 || value == 0xA || value == 0xD || (value >= 0x20 && value <= 0xD7FF)
                || (value >= 0xE000 && value <= 0xFFFD) || (value >= 0x10000 && value <= 0x10FFFF);
    }
    
    private static class PropertyListener implements PropertyEventListener {
        @Override
        public void propertySet(String property, Map<String, Object> params) {
            if (MAX_PROPERTY_NAME.equals(property)) {
                String value = (String) params.get("value");
                if (value != null) {
                    maxBufferSize = Integer.parseInt(value);
                }
            }
        }

        @Override
        public void propertyDeleted(String property, Map<String, Object> params) {
            if (MAX_PROPERTY_NAME.equals(property)) {
                // Use default value when none was specified
                maxBufferSize = 1048576;
            }
        }

        @Override
        public void xmlPropertySet(String property, Map<String, Object> params) {
            // Do nothing
        }

        @Override
        public void xmlPropertyDeleted(String property, Map<String, Object> params) {
            // Do nothing
        }
    }
}

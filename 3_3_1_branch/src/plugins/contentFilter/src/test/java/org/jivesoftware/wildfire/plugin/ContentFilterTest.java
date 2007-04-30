package org.jivesoftware.openfire.plugin;

import junit.framework.TestCase;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.XPPReader;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.io.StringReader;
import java.util.regex.PatternSyntaxException;

/**
 * Basic unit tests for ContentFilter.
 * 
 * @author chayes
 */
public class ContentFilterTest extends TestCase {
    private ContentFilter filter;

    public static void main(String[] args) {
        junit.textui.TestRunner.run(ContentFilterTest.class);
    }

    /*
     * @see TestCase#setUp()
     */
    protected void setUp() throws Exception {
        filter = new ContentFilter();
        
    }

    /*
     * @see TestCase#tearDown()
     */
    protected void tearDown() throws Exception {
        filter.clearMask();
        filter.clearPatterns();
        filter = null;
    }

    public void testSetInvalidPatterns() {
        try {
            filter.setPatterns("$*[");
            fail("expected PatternSyntaxException");
        } catch (PatternSyntaxException e) {
        }
    }

    public void testSetContentMask() {
        assertFalse(filter.isMaskingContent());
        filter.setMask("dummy");
        assertTrue(filter.isMaskingContent());
        filter.clearMask();
        assertFalse(filter.isMaskingContent());
    }

    public void testFilterWithEmptyMessage() {
        Message message = new Message();
        boolean matched = filter.filter(message);

        // no matches should be found
        assertFalse(matched);

        // message should not have changed
        assertEquals(new Message().toXML(), message.toXML());
    }

    public void testFilterMessageSubject() {
        // filter on the word fox
        filter.setPatterns("fox");

        // test message
        Message message = new Message();
        message.setSubject("the quick brown fox jumped over the lazy dog");
        boolean matched = filter.filter(message);

        // matches should be found
        assertTrue(matched);

        // content has not changed as there is no content mask
        assertEquals("the quick brown fox jumped over the lazy dog", message
                .getSubject());
        assertNull(message.getBody());
    }

    public void testFilterMessageSubjectWithMask() {

        // filter on the word fox
        filter.setPatterns("fox");

        // set a content mask
        filter.setMask("**");

        // test message
        Message message = new Message();
        message.setSubject("the quick brown fox jumped over the lazy dog");
        boolean matched = filter.filter(message);

        // matches should be found
        assertTrue(matched);

        // content has changed
        assertEquals("the quick brown ** jumped over the lazy dog", message
                .getSubject());
        assertNull(message.getBody());

    }

    public void testFilterMessageBody() {

        // filter on the word fox
        filter.setPatterns("fox");

        // test message
        Message message = new Message();
        message.setBody("the quick brown fox jumped over the lazy dog");
        boolean matched = filter.filter(message);

        // matches should be found
        assertTrue(matched);

        // content has not changed as there is no content mask
        assertEquals("the quick brown fox jumped over the lazy dog", message
                .getBody());
        assertNull(message.getSubject());
    }

    public void testFilterMessageBodyWithMask() {

        // filter on the word "fox" and "dog"
        filter.setPatterns("fox,dog");
        filter.setMask("**");

        // test message
        Message message = new Message();
        message.setBody("the quick brown fox jumped over the lazy dog");
        boolean matched = filter.filter(message);

        // matches should not be found
        assertTrue(matched);

        // content has changed
        assertEquals("the quick brown ** jumped over the lazy **", message
                .getBody());
        assertNull(message.getSubject());

    }

    public void testFilterWholeWords() {
        filter.setPatterns("at"); //match every instance of "at" in string
        filter.setMask("**");

        Message message = new Message();
        message.setBody("At noon the fat cats ate lunch at Rizzos");
        boolean matched = filter.filter(message);        
        assertTrue(matched);
        assertEquals("At noon the f** c**s **e lunch ** Rizzos", message.getBody());
        
        filter.setPatterns("(?i)\\bat\\b"); //match only whole word instances of "at" ignoring case 
        message.setBody("At noon the fat cats ate lunch at Rizzos");
        matched = filter.filter(message);        
        assertTrue(matched);
        assertEquals("** noon the fat cats ate lunch ** Rizzos", message.getBody());
    }
    
    public void testFilterChatMessage() throws DocumentException, IOException, XmlPullParserException {
        String chatXML = 
            "<message to=\"doe@127.0.0.1/Adium\" type=\"chat\" id=\"iChat_E8B5ED64\" from=\"bob@127.0.0.1/frodo\">" +
            "<body>fox</body>" +
            "<html xmlns=\"http://jabber.org/protocol/xhtml-im\">" +
            "<body xmlns=\"http://www.w3.org/1999/xhtml\" style=\"background-color:#E8A630;color:#000000\">fox</body>" +
            "</html>" +
            "<x xmlns=\"jabber:x:event\">" +
            "<composing/>" +
            "</x>" +
            "</message>";
        
        
        
        XPPReader packetReader = new XPPReader();
        Document doc = packetReader.read(new StringReader(chatXML));
        Message m = new Message(doc.getRootElement());
        
        // filter on the word "fox" and "dog"
        filter.setPatterns("fox,dog,message");
        filter.setMask("**");
        
        String expectedXML = chatXML.replaceAll("fox", filter.getMask());
        // do filter
        boolean matched = filter.filter(m);        
        assertTrue(matched);
        assertEquals(expectedXML, expectedXML, m.toXML());
        
    }
    
    public void testFilterAvailablePresence() throws Exception {
        
        // setup available presence
        Presence presence = new Presence();
        presence.setStatus("fox is now online!");
        System.out.println(presence.toXML());
        
        // filter on the word "fox" and "dog"
        filter.setPatterns("fox,dog");
        filter.setMask("**");
        
        boolean matched = filter.filter(presence);

        // matches should not be found
        assertTrue(matched);

        // content has changed
        assertEquals("** is now online!", presence.getStatus());
        
    }
    
    public void testFilterPresenceXML() throws Exception {
        String presenceXML = 
            "<presence from=\"bob@127.0.0.1/frodo\">" +
            "<show>away</show>" +
            "<status>fox</status>" +
            "<priority>0</priority>" +
            "<x xmlns=\"vcard-temp:x:update\">" +
            "<photo>f9a514f112c0bcb988d5aa12bc1a9a6f22de5262</photo>" +
            "</x>" +
            "<c xmlns=\"http://jabber.org/protocol/caps\" node=\"apple:ichat:caps\" ver=\"392\" ext=\"avavail maudio mvideo avcap audio\"/>" +
            "<x xmlns=\"http://jabber.org/protocol/tune\"/>" +
            "</presence>";
        
        
        XPPReader packetReader = new XPPReader();
        Document doc = packetReader.read(new StringReader(presenceXML));
        Presence p = new Presence(doc.getRootElement());
        
        // filter on the word "fox" and "dog"
        filter.setPatterns("fox,dog,message");
        filter.setMask("**");
        
        String expectedXML = presenceXML.replaceAll("fox", filter.getMask());
        // do filter
        boolean matched = filter.filter(p);        
        assertTrue(matched);
        assertEquals(expectedXML, expectedXML, p.toXML());
        
    }
}
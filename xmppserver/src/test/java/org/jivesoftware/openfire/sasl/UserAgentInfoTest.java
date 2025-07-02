package org.jivesoftware.net;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.net.UserAgentInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserAgentInfoTest {

    @Test
    public void testNullElement() {
        assertNull(UserAgentInfo.extract(null));
    }

    @Test
    public void testEmptyUserAgent() {
        Element userAgent = DocumentHelper.createElement("user-agent");
        UserAgentInfo info = UserAgentInfo.extract(userAgent);
        
        assertNotNull(info);
        assertNull(info.getId());
        assertNull(info.getSoftware());
        assertNull(info.getDevice());
    }

    @Test
    public void testCompleteValidUserAgent() {
        Element userAgent = DocumentHelper.createElement("user-agent");
        userAgent.addAttribute("id", "123e4567-e89b-42d3-a456-556642440000"); // Valid UUID v4
        
        Element software = userAgent.addElement("software");
        software.setText("My XMPP Client v1.0");
        
        Element device = userAgent.addElement("device");
        device.setText("Android Phone Model X");

        UserAgentInfo info = UserAgentInfo.extract(userAgent);
        
        assertNotNull(info);
        assertEquals("123e4567-e89b-42d3-a456-556642440000", info.getId());
        assertEquals("My XMPP Client v1.0", info.getSoftware());
        assertEquals("Android Phone Model X", info.getDevice());
    }

    @Test
    public void testInvalidUUIDFormat() {
        Element userAgent = DocumentHelper.createElement("user-agent");
        userAgent.addAttribute("id", "not-a-uuid");

        UserAgentInfo info = UserAgentInfo.extract(userAgent);
        
        assertNotNull(info);
        assertNull(info.getId()); // Invalid UUID should be ignored
    }

    @Test
    public void testNonV4UUID() {
        // This is a valid UUID v1
        Element userAgent = DocumentHelper.createElement("user-agent");
        userAgent.addAttribute("id", "123e4567-e89b-12d3-a456-556642440000");

        UserAgentInfo info = UserAgentInfo.extract(userAgent);
        
        assertNotNull(info);
        assertNull(info.getId()); // Non-v4 UUID should be ignored
    }

    @Test
    public void testOnlySoftware() {
        Element userAgent = DocumentHelper.createElement("user-agent");
        Element software = userAgent.addElement("software");
        software.setText("My XMPP Client v1.0");

        UserAgentInfo info = UserAgentInfo.extract(userAgent);
        
        assertNotNull(info);
        assertNull(info.getId());
        assertEquals("My XMPP Client v1.0", info.getSoftware());
        assertNull(info.getDevice());
    }

    @Test
    public void testOnlyDevice() {
        Element userAgent = DocumentHelper.createElement("user-agent");
        Element device = userAgent.addElement("device");
        device.setText("Android Phone Model X");

        UserAgentInfo info = UserAgentInfo.extract(userAgent);
        
        assertNotNull(info);
        assertNull(info.getId());
        assertNull(info.getSoftware());
        assertEquals("Android Phone Model X", info.getDevice());
    }

    @Test
    public void testEmptyElements() {
        Element userAgent = DocumentHelper.createElement("user-agent");
        userAgent.addElement("software");
        userAgent.addElement("device");

        UserAgentInfo info = UserAgentInfo.extract(userAgent);
        
        assertNotNull(info);
        assertNull(info.getId());
        assertEquals("", info.getSoftware());
        assertEquals("", info.getDevice());
    }

    @Test
    public void testWhitespaceHandling() {
        Element userAgent = DocumentHelper.createElement("user-agent");
        
        Element software = userAgent.addElement("software");
        software.setText("  My XMPP Client v1.0  ");
        
        Element device = userAgent.addElement("device");
        device.setText("\tAndroid Phone Model X\n");

        UserAgentInfo info = UserAgentInfo.extract(userAgent);
        
        assertNotNull(info);
        assertEquals("My XMPP Client v1.0", info.getSoftware());
        assertEquals("Android Phone Model X", info.getDevice());
    }
}

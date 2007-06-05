/**
 * $Revision:$
 * $Date:$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.openfire.handler;

import org.jmock.Mockery;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.junit.runner.RunWith;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.xmpp.packet.IQ;

import static org.junit.Assert.*;
import org.dom4j.Element;

/**
 * Test for the IQvCardHandler.
 */
@RunWith(JMock.class)
public class IQvCardHandlerTest {
    private static Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private static XMPPServer xmppServer;
    private static UserManager userManager;
    private static PacketDeliverer packetDeliverer;
    private static SessionManager sessionManager;

    @BeforeClass
    public static void doBeforeClass() {
        xmppServer = context.mock(XMPPServer.class);
        userManager = context.mock(UserManager.class);
        packetDeliverer = context.mock(PacketDeliverer.class);
        sessionManager = context.mock(SessionManager.class);

        context.checking(new Expectations() {{
            allowing(xmppServer).getUserManager();
            will(returnValue(userManager));

            allowing(xmppServer).getPacketDeliverer();
            will(returnValue(packetDeliverer));

            allowing(xmppServer).getSessionManager();
            will(returnValue(sessionManager));
        }});
    }

    @Test
    public void testVCardHandlerSetReadOnly() throws UnauthorizedException {
        IQ vCardSet = new IQ(IQ.Type.set);
        vCardSet.setChildElement("vCard", "vcard-temp");

        final VCardManager vCardManager = context.mock(VCardManager.class);

        IQvCardHandler vCardHandler = new IQvCardHandler();
        context.checking(new Expectations() {{
            one(xmppServer).getVCardManager();
            will(returnValue(vCardManager));

            one(vCardManager).isReadOnly();
            will(returnValue(true));
        }});
        vCardHandler.initialize(xmppServer);

        IQ result = vCardHandler.handleIQ(vCardSet);
        assertEquals(result.getType(), IQ.Type.error);
    }

    @Test
    public void testVCardHandlerSet() throws UnauthorizedException {
        IQ vCardSet = new IQ(IQ.Type.set);
        vCardSet.setFrom("test@test.com");
        vCardSet.setChildElement("vCard", "vcard-temp");
        final Element vCardElement = vCardSet.getChildElement();

        final VCardManager vCardManager = context.mock(VCardManager.class);
        final User user = context.mock(User.class);
        final IQvCardHandler vCardHandler = new IQvCardHandler();
        context.checking(new Expectations() {{
            one(xmppServer).getVCardManager();
            will(returnValue(vCardManager));

            one(vCardManager).isReadOnly();
            will(returnValue(false));

            try {
                one(userManager).getUser(with(a(String.class)));
            }
            catch (UserNotFoundException e) {
                fail("User not found");
            }
            will(returnValue(user));

            one(user).getUsername();
            will(returnValue("test"));

            try {
                one(vCardManager).setVCard("test", vCardElement);
            }
            catch (Exception e) {
                fail("Vcard exception");
            }
        }});
        vCardHandler.initialize(xmppServer);
        IQ result = vCardHandler.handleIQ(vCardSet);
        assertEquals(result.getType(), IQ.Type.result);
    }
}

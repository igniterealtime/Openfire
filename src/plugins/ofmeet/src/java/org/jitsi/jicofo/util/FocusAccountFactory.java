/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.util;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.jabber.*;

import java.util.*;

/**
 * Class contains factory methods for creating <tt>JabberAccountID</tt> used by
 * the focus of Jitsi Meet conference.
 *
 * @author Pawel Domas
 */
public class FocusAccountFactory
{
    private FocusAccountFactory(){ }

    /**
     * Creates focus XMPP account properties configured for given
     * <tt>domain</tt> which uses anonymous login method.
     *
     * @param serverAddress XMPP server address.
     * @param domain name of the XMPP domain on which the focus will register.
     * @param userName user name used by the focus user.
     *
     * @return the map of new focus account properties for given domain.
     */
    public static Map<String, String> createFocusAccountProperties(
            String serverAddress,
            String domain,
            String userName)
    {
        HashMap<String, String> properties = new HashMap<String, String>();

        String resource = userName + System.nanoTime();

        String userID = userName + "@" + domain + "/" + resource;

        properties.put(ProtocolProviderFactory.USER_ID, userID);
        properties.put(ProtocolProviderFactory.SERVER_ADDRESS, serverAddress);
        properties.put(ProtocolProviderFactory.SERVER_PORT, "5222");

        // This is used as the multi user chat nick when joining the room
        properties.put(
            ProtocolProviderFactory.ACCOUNT_DISPLAY_NAME,
            userName);

        properties.put(ProtocolProviderFactory.RESOURCE,
                       resource);
        properties.put(ProtocolProviderFactory.RESOURCE_PRIORITY, "30");

        properties.put(JabberAccountID.ANONYMOUS_AUTH, "true");
        properties.put(ProtocolProviderFactory.IS_CARBON_DISABLED, "true");
        properties.put(ProtocolProviderFactory.DEFAULT_ENCRYPTION, "true");
        properties.put(ProtocolProviderFactory.DEFAULT_SIPZRTP_ATTRIBUTE,
                       "false");
        properties.put("ENCRYPTION_PROTOCOL_STATUS.DTLS-SRTP", "true");

        properties.put(ProtocolProviderFactory.IS_USE_ICE, "true");
        //properties.put(ProtocolProviderFactory.IS_USE_GOOGLE_ICE, "true");
        properties.put(ProtocolProviderFactory.IS_ACCOUNT_DISABLED, "false");
        properties.put(ProtocolProviderFactory.IS_PREFERRED_PROTOCOL, "false");
        properties.put(ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, "false");
        properties.put(ProtocolProviderFactory.AUTO_DISCOVER_JINGLE_NODES,
                       "false");
        properties.put(ProtocolProviderFactory.PROTOCOL, ProtocolNames.JABBER);
        properties.put(ProtocolProviderFactory.IS_USE_UPNP, "false");
        properties.put(ProtocolProviderFactory.USE_DEFAULT_STUN_SERVER, "true");

        //FIXME: this is not used, but have to check before remove
        properties.put("Encodings.G722/8000","700");
        properties.put("Encodings.GSM/8000","0");
        properties.put("Encodings.H263-1998/90000","0");
        properties.put("Encodings.H264/90000","0");
        properties.put("Encodings.PCMA/8000","600");
        properties.put("Encodings.PCMU/8000","650");
        properties.put("Encodings.SILK/12000","0");
        properties.put("Encodings.SILK/16000","0");
        properties.put("Encodings.SILK/24000","0");
        properties.put("Encodings.SILK/8000","0");
        properties.put("Encodings.VP8/90000","100");
        properties.put("Encodings.iLBC/8000","10");
        properties.put("Encodings.opus/48000","1000");
        properties.put("Encodings.red/90000","0");
        properties.put("Encodings.speex/16000","0");
        properties.put("Encodings.speex/32000","0");
        properties.put("Encodings.speex/8000","0");
        properties.put("Encodings.telephone-event/8000","0");
        properties.put("Encodings.ulpfec/90000","0");
        properties.put("G722/8000","0");
        properties.put("GSM/8000","0");
        properties.put("H263-1998/90000","0");
        properties.put("H264/90000","0");
        properties.put("OVERRIDE_ENCODINGS","true");

        return properties;
    }

    /**
     * Creates focus XMPP account properties configured for given
     * <tt>domain</tt> which login as authenticated admin user
     * (we expect it to be admin or otherwise focus will refuse to join
     *  the room).
     *
     * @param serverAddress XMPP server address.
     * @param domain name of the XMPP domain on which the focus will register.
     * @param userName the nickname used by the focus in MUC room
     *                 (also used as login name).
     * @param password focus user admin password.
     *
     * @return the map of new focus account properties for given domain.
     */
    public static Map<String, String> createFocusAccountProperties(
            String serverAddress,
            String domain,
            String userName,
            String password)
    {
        Map<String, String> properties
            = createFocusAccountProperties(
                    serverAddress, domain, userName);

        properties.put(ProtocolProviderFactory.AUTHORIZATION_NAME, userName);

        /*String pass = new String(Base64.encode(password.getBytes()));
        properties.put(ProtocolProviderFactory.PASSWORD,
                       pass);*/

        properties.put(ProtocolProviderFactory.PASSWORD, password);

        properties.put(JabberAccountID.ANONYMOUS_AUTH, "false");

        return properties;
    }
}

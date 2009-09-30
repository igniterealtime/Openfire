/**
 * $RCSfile: ,v $
 * $Revision: 1.0 $
 * $Date: 2005/05/25 04:20:03 $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.plugin.spark.manager;

import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.XMPPServer;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

import java.io.File;

/**
 * Provides support for server administrators to control the global updating of the Jive Spark IM client.
 * (<a href="http://www.igniterealtime.org/projects/spark/index.jsp">Spark</a>).<p>
 * <p/>
 * The basic functionality is to query the server for the latest client
 * version and return that information. The version comparison is left to
 * the client itself, so as to keep the SparkVersionManager simple.
 * <p/>
 *
 * @author Derek DeMoro
 */
public class SparkVersionManager implements Component {
    
    private ComponentManager componentManager;
    public static String SERVICE_NAME = "updater";

    /**
     * Empty constructor for initializing.
     */
    public SparkVersionManager() {
        // Initialize ComponentManager
        componentManager = ComponentManagerFactory.getComponentManager();
    }

    /**
     * Returns the name of this plugin.
     *
     * @return the name of this plugin.
     */
    public String getName() {
        return "Spark Version Manager";
    }

    /**
     * Returns a brief description of this plugin.
     *
     * @return a brief description of this plugin.
     */
    public String getDescription() {
        return "Allow admins to control the updating of the Spark IM Client.";
    }

    public void processPacket(Packet packet) {
        if (packet instanceof IQ) {
            IQ iqPacket = (IQ)packet;

            if (IQ.Type.get == iqPacket.getType()) {
                Element childElement = (iqPacket).getChildElement();
                String namespace = null;
                if (childElement != null) {
                    namespace = childElement.getNamespaceURI();
                }

                // Handle any disco info requests.
                if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
                    handleDiscoInfo(iqPacket);
                }

                // Handle any disco item requests.
                else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
                    handleDiscoItems(iqPacket);
                }
                // Handle a jabber spark request.
                else if ("jabber:iq:spark".equals(namespace)) {
                    handleSparkIQ(iqPacket);
                }
            }
            else if (IQ.Type.error == iqPacket.getType() || IQ.Type.result == iqPacket.getType()) {
                // Ignore these packets
            }
            else {
                // Return error since this is an unknown service request
                IQ reply = IQ.createResultIQ(iqPacket);
                reply.setError(PacketError.Condition.service_unavailable);
                sendPacket(reply);
            }
        }
    }

    private void handleSparkIQ(IQ packet) {
        IQ reply;
        Element iq = packet.getChildElement();

        // Define default values
        String os = iq.element("os").getText();

        reply = IQ.createResultIQ(packet);

        // Handle Invalid Requests
        if (os == null || (!os.equals("windows") && !os.equals("mac") && !os.equals("linux"))) {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.not_acceptable));
            sendPacket(reply);
            return;
        }

        Element sparkElement = reply.setChildElement("query", "jabber:iq:spark");
        String client = null;

        // Handle Windows clients
        if (os.equals("windows")) {
            client = JiveGlobals.getProperty("spark.windows.client");
        }
        // Handle Mac clients.
        else if (os.equals("mac")) {
            client = JiveGlobals.getProperty("spark.mac.client");
        }

        // Handle Linux Client.
        else if (os.equals("linux")) {
            client = JiveGlobals.getProperty("spark.linux.client");
        }

        if (client != null) {
            int index = client.indexOf("_");

            // Add version number
            String versionNumber = client.substring(index + 1);
            int indexOfPeriod = versionNumber.indexOf(".");

            versionNumber = versionNumber.substring(0, indexOfPeriod);
            versionNumber = versionNumber.replaceAll("_", ".");

            sparkElement.addElement("version").setText(versionNumber);

            // Add updated time.
            File clientFile = new File(JiveGlobals.getHomeDirectory(), "enterprise/spark/" + client);
            if (!clientFile.exists()) {
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.item_not_found));
                sendPacket(reply);
                return;
            }
            long updatedTime = clientFile.lastModified();
            sparkElement.addElement("updatedTime").setText(Long.toString(updatedTime));

            // Add download url
            String downloadURL = JiveGlobals.getProperty("spark.client.downloadURL");
            String server = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
            downloadURL = downloadURL.replace("127.0.0.1", server);

            sparkElement.addElement("downloadURL").setText(downloadURL + "?client=" + client);

            String displayMessage = JiveGlobals.getProperty("spark.client.displayMessage");
            if (displayMessage != null && displayMessage.trim().length() > 0) {
                sparkElement.addElement("displayMessage").setText(displayMessage);
            }
        }
        else {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            sendPacket(reply);
            return;
        }

        sendPacket(reply);
    }

    private void handleDiscoItems(IQ packet) {
        IQ replyPacket = IQ.createResultIQ(packet);
        replyPacket.setChildElement("query", "http://jabber.org/protocol/disco#items");
        sendPacket(replyPacket);
    }

    private void handleDiscoInfo(IQ packet) {
        IQ replyPacket = IQ.createResultIQ(packet);
        Element responseElement =
                replyPacket.setChildElement("query", "http://jabber.org/protocol/disco#info");

        Element identity = responseElement.addElement("identity");
        identity.addAttribute("category", "updater");
        identity.addAttribute("type", "text");
        identity.addAttribute("name", "Spark Updater");
        
        responseElement.addElement("feature").addAttribute("var", "jabber:iq:updater");

        sendPacket(replyPacket);
    }

    public void initialize(JID jid, ComponentManager componentManager) throws ComponentException {
        // Do nothing.
    }

    public void start() {
        // Do nothing
    }

    public void shutdown() {
        // Do nothing.
    }

    private void sendPacket(Packet packet) {
        try {
            componentManager.sendPacket(this, packet);
        }
        catch (ComponentException e) {
            Log.error(e);
        }
    }
}

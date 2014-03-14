/**
 * $Revision $
 * $Date $
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
package com.rayo.core.xml.providers;

import java.net.URISyntaxException;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.xmpp.packet.*;

import com.rayo.core.verb.*;

public class ColibriProvider extends BaseProvider {

    // XML -> Object
    // ================================================================================

    private static final Namespace NAMESPACE = new Namespace("", "urn:xmpp:rayo:colibri:1");
    private static final QName COLIBRI_QNAME = new QName("colibri", NAMESPACE);

    @Override
    protected Object processElement(Element element) throws Exception
    {
        if (COLIBRI_QNAME.equals(element.getQName())) {
            return buildColibriCommand(element);
        }
        return null;
    }

    private Object buildColibriCommand(Element element) throws URISyntaxException
    {
		String action = element.attributeValue("action");
		Object command = null;

		if ("register".equals(action)) {
			command = new RegisterCommand();

		} else if ("unregister".equals(action)) {
			command = new UnRegisterCommand();

		} else if ("offer".equals(action)) {
			command = new ColibriOfferCommand(new JID(element.attributeValue("muc")));

		} else if ("expire".equals(action)) {
			command = new ColibriExpireCommand(new JID(element.attributeValue("muc")));

		} else if ("invite".equals(action)) {
			command = new InviteCommand(new JID(element.attributeValue("muc")), toURI(element.attributeValue("from")), toURI(element.attributeValue("to")));

		} else if ("uninvite".equals(action)) {
			command = new UnInviteCommand(new JID(element.attributeValue("muc")), element.attributeValue("callid"));

		}

        return command;
    }

    // Object -> XML
    // ================================================================================

    @Override
    protected void generateDocument(Object object, Document document) throws Exception {

		if (object instanceof ColibriOfferEvent) {
            createColibriOfferEvent((ColibriOfferEvent) object, document);

        } else if (object instanceof AddSourceEvent) {
            createAddSourceEvent((AddSourceEvent) object, document);

        } else if (object instanceof RemoveSourceEvent) {
            createRemoveSourceEvent((RemoveSourceEvent) object, document);

        } else if (object instanceof JoinBridgeEvent) {
            createJoinBridgeEvent((JoinBridgeEvent) object, document);

        } else if (object instanceof LeaveBridgeEvent) {
            createLeaveBridgeEvent((LeaveBridgeEvent) object, document);

        } else if (object instanceof MutedEvent) {
            createMutedEvent((MutedEvent) object, document);

        } else if (object instanceof UnmutedEvent) {
            createUnmutedEvent((UnmutedEvent) object, document);

        } else if (object instanceof InviteAcceptedEvent) {
            createInviteAcceptedEvent((InviteAcceptedEvent) object, document);

        } else if (object instanceof InviteCompletedEvent) {
            createInviteCompletedEvent((InviteCompletedEvent) object, document);
        }
    }

    private void createColibriOfferEvent(ColibriOfferEvent event, Document document)
    {
        Element root = document.addElement(new QName("offer", NAMESPACE));
		root.addAttribute("muc", event.getMuc().toString());
		root.addAttribute("videobridge", event.getMuc().getNode());
		root.addAttribute("nickname", event.getNickname());
		root.addAttribute("participant", event.getParticipant().toString());
        root.add(event.getConference().createCopy());
    }

    private void createAddSourceEvent(AddSourceEvent event, Document document)
    {
        Element root = document.addElement(new QName("addsource", NAMESPACE));
		root.addAttribute("muc", event.getMuc().toString());
		root.addAttribute("videobridge", event.getMuc().getNode());
		root.addAttribute("nickname", event.getNickname());
		root.addAttribute("participant", event.getParticipant().toString());
        root.add(event.getConference().createCopy());
    }

    private void createRemoveSourceEvent(RemoveSourceEvent event, Document document)
    {
        Element root = document.addElement(new QName("removesource", NAMESPACE));
		root.addAttribute("muc", event.getMuc().toString());
		root.addAttribute("videobridge", event.getMuc().getNode());
		root.addAttribute("nickname", event.getNickname());
		root.addAttribute("participant", event.getParticipant().toString());
		root.addAttribute("active", event.isActive() ? "true" : "false");
        root.add(event.getConference().createCopy());
    }

    private void createJoinBridgeEvent(JoinBridgeEvent joined, Document document)
    {
        Element root = document.addElement(new QName("joined", NAMESPACE));
		root.addAttribute("mixer-name", joined.getMixer());
		root.addAttribute("nickname", joined.getNickname());
		root.addAttribute("participant", joined.getParticipant().toString());
    }

    private void createLeaveBridgeEvent(LeaveBridgeEvent unjoined, Document document)
    {
        Element root = document.addElement(new QName("unjoined", NAMESPACE));
		root.addAttribute("mixer-name", unjoined.getMixer());
		root.addAttribute("nickname", unjoined.getNickname());
		root.addAttribute("participant", unjoined.getParticipant().toString());
    }

    private void createMutedEvent(MutedEvent muted, Document document)
    {
        document.addElement(new QName("onmute", NAMESPACE));
    }

    private void createUnmutedEvent(UnmutedEvent unmuted, Document document)
    {
        document.addElement(new QName("offmute", NAMESPACE));
    }

    private void createInviteAcceptedEvent(InviteAcceptedEvent accepted, Document document)
    {
        Element root = document.addElement(new QName("inviteaccepted", NAMESPACE));
		root.addAttribute("callid", accepted.getCallId());
    }

    private void createInviteCompletedEvent(InviteCompletedEvent completed, Document document)
    {
        Element root = document.addElement(new QName("invitecompleted", NAMESPACE));
		root.addAttribute("callid", completed.getCallId());
    }
}

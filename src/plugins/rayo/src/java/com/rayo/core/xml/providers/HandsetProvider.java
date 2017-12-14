/**
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

import com.rayo.core.verb.*;
import com.rayo.core.verb.SayCompleteEvent.Reason;

public class HandsetProvider extends BaseProvider {

    // XML -> Object
    // ================================================================================

    private static final Namespace NAMESPACE = new Namespace("", "urn:xmpp:rayo:handset:1");
    private static final Namespace COMPLETE_NAMESPACE = new Namespace("", "urn:xmpp:rayo:handset:complete:1");

    private static final QName ONHOOK_QNAME = new QName("onhook", NAMESPACE);
    private static final QName OFFHOOK_QNAME = new QName("offhook", NAMESPACE);
    private static final QName PRIVATE_QNAME = new QName("private", NAMESPACE);
    private static final QName PUBLIC_QNAME = new QName("public", NAMESPACE);
    private static final QName MUTE_QNAME = new QName("mute", NAMESPACE);
    private static final QName UNMUTE_QNAME = new QName("unmute", NAMESPACE);
    private static final QName HOLD_QNAME = new QName("hold", NAMESPACE);
    private static final QName TALK_QNAME = new QName("talk", NAMESPACE);
    private static final QName UNTALK_QNAME = new QName("untalk", NAMESPACE);
    private static final QName ONSPEAKER_QNAME = new QName("onspeaker", NAMESPACE);
    private static final QName OFFSPEAKER_QNAME = new QName("offspeaker", NAMESPACE);
    private static final QName CREATE_SPEAKER_QNAME = new QName("createspeaker", NAMESPACE);
    private static final QName DESTROY_SPEAKER_QNAME = new QName("destroyspeaker", NAMESPACE);

    @Override
    protected Object processElement(Element element) throws Exception
    {
        if (ONHOOK_QNAME.equals(element.getQName())) {
            return buildOnHookCommand(element);

        } else if (OFFHOOK_QNAME.equals(element.getQName())) {
            return buildOffHookCommand(element);

        } else if (PRIVATE_QNAME.equals(element.getQName())) {
            return buildPrivateCommand(element);

        } else if (PUBLIC_QNAME.equals(element.getQName())) {
            return buildPublicCommand(element);

        } else if (MUTE_QNAME.equals(element.getQName())) {
            return buildMuteCommand(element);

        } else if (UNMUTE_QNAME.equals(element.getQName())) {
            return buildUnmuteCommand(element);

        } else if (HOLD_QNAME.equals(element.getQName())) {
            return buildHoldCommand(element);

        } else if (TALK_QNAME.equals(element.getQName())) {
            return buildTalkCommand(element);

        } else if (UNTALK_QNAME.equals(element.getQName())) {
            return buildUntalkCommand(element);

        } else if (ONSPEAKER_QNAME.equals(element.getQName())) {
            return buildOnSpeakerCommand(element);

        } else if (OFFSPEAKER_QNAME.equals(element.getQName())) {
            return buildOffSpeakerCommand(element);

        } else if (CREATE_SPEAKER_QNAME.equals(element.getQName())) {
            return buildCreateSpeakerCommand(element);

        } else if (DESTROY_SPEAKER_QNAME.equals(element.getQName())) {
            return buildDestroySpeakerCommand(element);

        } else if (element.getNamespace().equals(RAYO_COMPONENT_NAMESPACE)) {
            return buildCompleteCommand(element);
        }
        return null;
    }

    private Object buildPrivateCommand(Element element)
    {
        return new PrivateCommand();
    }

    private Object buildPublicCommand(Element element)
    {
        return new PublicCommand();
    }

    private Object buildMuteCommand(Element element)
    {
        return new MuteCommand();
    }

    private Object buildUnmuteCommand(Element element)
    {
        return new UnmuteCommand();
    }

    private Object buildHoldCommand(Element element)
    {
        return new HoldCommand();
    }

    private Object buildCompleteCommand(Element element)
    {
        Element reasonElement = (Element)element.elements().get(0);
        String reasonValue = reasonElement.getName().toUpperCase();
        Reason reason = Reason.valueOf(reasonValue);

        SayCompleteEvent complete = new SayCompleteEvent();
        complete.setReason(reason);
        return complete;
    }


    private Object buildOffHookCommand(Element element) throws URISyntaxException {

        Handset handset;
        String sipuri = element.attributeValue("sipuri");

        if (sipuri != null && "".equals(sipuri) == false)
        {
            handset = new  Handset(	element.attributeValue("sipuri"), element.attributeValue("mixer"), element.attributeValue("codec"));

        } else {

            handset = new  Handset(	element.attributeValue("cryptosuite"),
                                            element.attributeValue("localcrypto"),
                                            element.attributeValue("remotecrypto"),
                                            element.attributeValue("codec"),
                                            element.attributeValue("stereo"),
                                            element.attributeValue("mixer"));
        }

        handset.group = element.attributeValue("group");
        handset.callId = element.attributeValue("callid");

        OffHookCommand command = new OffHookCommand();
        command.setHandset(handset);

        return command;
    }

    private Object buildOnHookCommand(Element element) throws URISyntaxException {
        return new OnHookCommand();
    }

    private Object buildTalkCommand(Element element)
    {
        return new TalkCommand();
    }

    private Object buildUntalkCommand(Element element)
    {
        return new UntalkCommand();
    }

    private Object buildOnSpeakerCommand(Element element)
    {
        return new PutOnSpeakerCommand();
    }

    private Object buildOffSpeakerCommand(Element element)
    {
        return new TakeOffSpeakerCommand();
    }

    private Object buildCreateSpeakerCommand(Element element)
    {
        return new CreateSpeakerCommand(element.attributeValue("sipuri"), element.attributeValue("mixer"), element.attributeValue("codec"));
    }

    private Object buildDestroySpeakerCommand(Element element)
    {
        return new DestroySpeakerCommand();
    }

    // Object -> XML
    // ================================================================================

    @Override
    protected void generateDocument(Object object, Document document) throws Exception {

        if (object instanceof OnHookCommand) {
            createOnHookCommand((OnHookCommand) object, document);

        } else if (object instanceof OffHookCommand) {
            createOffHookCommand((OffHookCommand) object, document);

        } else if (object instanceof SayCompleteEvent) {
            createHandsetCompleteEvent((SayCompleteEvent) object, document);

        } else if (object instanceof OnHoldEvent) {
            createOnHoldEvent((OnHoldEvent) object, document);

        } else if (object instanceof OffHoldEvent) {
            createOffHoldEvent((OffHoldEvent) object, document);

        } else if (object instanceof MutedEvent) {
            createMutedEvent((MutedEvent) object, document);

        } else if (object instanceof UnmutedEvent) {
            createUnmutedEvent((UnmutedEvent) object, document);

        } else if (object instanceof PrivateEvent) {
            createPrivateEvent((PrivateEvent) object, document);

        } else if (object instanceof PublicEvent) {
            createPublicEvent((PublicEvent) object, document);

        } else if (object instanceof TransferredEvent) {
            createTransferredEvent((TransferredEvent) object, document);

        } else if (object instanceof TransferringEvent) {
            createTransferringEvent((TransferringEvent) object, document);
        }
    }

    private void createOffHookCommand(OffHookCommand command, Document document) throws Exception {
        Handset handset = command.getHandset();

        Element root = document.addElement(new QName("offhook", NAMESPACE));
        root.addAttribute("cryptoSuite", handset.cryptoSuite);
        root.addAttribute("localCrypto", handset.localCrypto);
        root.addAttribute("remoteCrypto", handset.cryptoSuite);
        root.addAttribute("codec", handset.codec);
        root.addAttribute("stereo", handset.stereo);
        root.addAttribute("mixer", handset.mixer);
    }


    private void createOnHookCommand(OnHookCommand command, Document document) throws Exception
    {
        document.addElement(new QName("onhook", NAMESPACE));
    }

    private void createHandsetCompleteEvent(SayCompleteEvent event, Document document) throws Exception
    {
        addCompleteElement(document, event, COMPLETE_NAMESPACE);
    }

    private void createOnHoldEvent(OnHoldEvent onHold, Document document)
    {
        document.addElement(new QName("onhold", NAMESPACE));
    }

    private void createOffHoldEvent(OffHoldEvent offHold, Document document)
    {
        document.addElement(new QName("offhold", NAMESPACE));
    }

    private void createMutedEvent(MutedEvent muted, Document document)
    {
        document.addElement(new QName("onmute", NAMESPACE));
    }

    private void createUnmutedEvent(UnmutedEvent unmuted, Document document)
    {
        document.addElement(new QName("offmute", NAMESPACE));
    }

    private void createPrivateEvent(PrivateEvent event, Document document)
    {
        document.addElement(new QName("private", NAMESPACE));
    }

    private void createPublicEvent(PublicEvent event, Document document)
    {
        document.addElement(new QName("public", NAMESPACE));
    }

    private void createTransferredEvent(TransferredEvent event, Document document)
    {
        document.addElement(new QName("transferred", NAMESPACE));
    }

    private void createTransferringEvent(TransferringEvent event, Document document)
    {
        document.addElement(new QName("transferring", NAMESPACE));
    }
}

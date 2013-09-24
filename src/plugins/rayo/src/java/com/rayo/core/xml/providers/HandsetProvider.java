package com.rayo.core.xml.providers;

import java.net.URISyntaxException;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;

import com.rayo.core.verb.OnHookCommand;
import com.rayo.core.verb.OffHookCommand;
import com.rayo.core.verb.Handset;
import com.rayo.core.verb.SayCompleteEvent;
import com.rayo.core.verb.SayCompleteEvent.Reason;

public class HandsetProvider extends BaseProvider {

    // XML -> Object
    // ================================================================================

    private static final Namespace NAMESPACE = new Namespace("", "urn:xmpp:rayo:handset:1");
    private static final Namespace COMPLETE_NAMESPACE = new Namespace("", "urn:xmpp:rayo:handset:complete:1");

    private static final QName ONHOOK_QNAME = new QName("onhook", NAMESPACE);
    private static final QName OFFHOOK_QNAME = new QName("offhook", NAMESPACE);

    @Override
    protected Object processElement(Element element) throws Exception
    {
        if (element.getName().equals("handset")) {
            return buildHandset(element);

        } else if (ONHOOK_QNAME.equals(element.getQName())) {
            return buildOnHookCommand(element);

        } else if (OFFHOOK_QNAME.equals(element.getQName())) {
            return buildOffHookCommand(element);

        } else if (element.getNamespace().equals(RAYO_COMPONENT_NAMESPACE)) {
            return buildCompleteCommand(element);
        }
        return null;
    }

    private Object buildCompleteCommand(Element element) {

        Element reasonElement = (Element)element.elements().get(0);
    	String reasonValue = reasonElement.getName().toUpperCase();
        Reason reason = Reason.valueOf(reasonValue);

        SayCompleteEvent complete = new SayCompleteEvent();
        complete.setReason(reason);
        return complete;
    }

    private Object buildHandset(Element element) throws URISyntaxException
    {
        Handset handset = new  Handset(	element.attributeValue("cryptoSuite"),
        								element.attributeValue("localCrypto"),
        								element.attributeValue("remoteCrypto"),
        								element.attributeValue("mixer"),
        								element.attributeValue("codec"),
        								element.attributeValue("stereo"));
        return handset;
    }

    private Object buildOnHookCommand(Element element) throws URISyntaxException {
        return new OnHookCommand();
    }

    private Object buildOffHookCommand(Element element) throws URISyntaxException {
        return new OffHookCommand();
    }

    // Object -> XML
    // ================================================================================

    @Override
    protected void generateDocument(Object object, Document document) throws Exception {

        if (object instanceof Handset) {
            createHandset((Handset) object, document);

        } else if (object instanceof OnHookCommand) {
            createOnHookCommand((OnHookCommand) object, document);

        } else if (object instanceof OffHookCommand) {
            createOffHookCommand((OffHookCommand) object, document);

        } else if (object instanceof SayCompleteEvent) {
            createHandsetCompleteEvent((SayCompleteEvent) object, document);
        }
    }

    private void createHandset(Handset handset, Document document) throws Exception {
        Element root = document.addElement(new QName("handset", NAMESPACE));

		root.addAttribute("cryptoSuite", handset.cryptoSuite);
		root.addAttribute("localCrypto", handset.localCrypto);
		root.addAttribute("remoteCrypto", handset.cryptoSuite);
		root.addAttribute("mixer", handset.mixer);
		root.addAttribute("codec", handset.codec);
		root.addAttribute("stereo", handset.stereo);
    }

    private void createOnHookCommand(OnHookCommand command, Document document) throws Exception {
        document.addElement(new QName("onhook", NAMESPACE));
    }

    private void createOffHookCommand(OffHookCommand command, Document document) throws Exception {
        document.addElement(new QName("offhook", NAMESPACE));
    }

    private void createHandsetCompleteEvent(SayCompleteEvent event, Document document) throws Exception {
        addCompleteElement(document, event, COMPLETE_NAMESPACE);
    }
}

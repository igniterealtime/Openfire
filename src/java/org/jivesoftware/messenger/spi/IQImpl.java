/* RCSFile: $
 * Revision: $
 * Date: $
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.spi;

import org.jivesoftware.util.XPPReader;
import org.jivesoftware.messenger.*;
import java.util.Iterator;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;

public class IQImpl extends AbstractPacket implements IQ {

    String childNamespace = "";
    String childName = "";
    XMPPFragment childFragment;

    public IQImpl() {
    }

    public String getChildNamespace() {
        return childNamespace;
    }

    public IQ createResult(Element body) {
        IQ result = createResult();
        XMPPFragment fragment = new XMPPDOMFragment(body);
        result.setChildFragment(fragment);
        result.addFragment(fragment);
        return result;
    }

    public IQ createResult() {
        IQ result = (IQ)createDeepCopy();
        result.setType(RESULT);
        result.setChildFragment(null);
        result.clearFragments();
        result.setSender(recipient);
        result.setRecipient(sender);
        return result;
    }

    public XMPPPacket.Type typeFromString(String type) {
        XMPPPacket.Type typeResult = super.typeFromString(type);
        if (typeResult == null) {
            if ("set".equals(type)) {
                return SET;
            }
            else if ("get".equals(type)) {
                return GET;
            }
            else if ("result".equals(type)) {
                return RESULT;
            }
        }
        return typeResult;
    }

    /**
     * Quick hack to get around parsing for the short term
     *
     * @param doc The document holding the entire iq packet
     */
    public void parse(Document doc) {
        Element root = doc.getRootElement();
        setRecipient(XMPPAddress.parseJID(root.attributeValue("to")));
        setType(typeFromString(root.attributeValue("type")));
        setID(root.attributeValue("id"));

        Iterator elements = root.elements().iterator();
        while (elements.hasNext()) {
            Element element = (Element)elements.next();
            setChildNamespace(element.getNamespaceURI());
            setChildName(element.getName());
            XMPPFragment fragment = new XMPPDOMFragment(element);
            addFragment(fragment);
            setChildFragment(fragment);
        }
    }

    /**
     * <p>The parsing occurs one past the root element start tag event.</p>
     *
     * @param xpp The parser to pull from (set to one past the root iq element).
     * @throws XMLStreamException If there is trouble parsing
     */
    public void parse(XMLStreamReader xpp) throws XMLStreamException {
        // we're already one past the root iq-element
        int event = xpp.getEventType();
        Document doc = null;
        if (event == XMLStreamConstants.START_ELEMENT) {
            try {
                doc = XPPReader.parseDocument(xpp);
                setChildNamespace(doc.getRootElement().getNamespaceURI());
                setChildName(doc.getRootElement().getName());
                XMPPFragment fragment = new XMPPDOMFragment(doc.getRootElement());
                addFragment(fragment);
                setChildFragment(fragment);
            }
            catch (DocumentException e) {
                throw new XMLStreamException();
            }
        }
        while (event != XMLStreamConstants.END_ELEMENT) {
            event = xpp.next();
        }
    }

    public void setChildNamespace(String namespace) {
        if (namespace == null) {
            childNamespace = "";
        }
        else {
            childNamespace = namespace;
        }
    }

    public String getChildName() {
        return childName;
    }

    public void setChildName(String name) {
        if (name == null) {
            childName = "";
        }
        else {
            childName = name;
        }
    }

    public XMPPFragment getChildFragment() {
        return childFragment;
    }

    public void setChildFragment(XMPPFragment fragment) {
        childFragment = fragment;
        if (childFragment != null) {
            childNamespace = childFragment.getNamespace();
            childName = childFragment.getName();
        }
    }

    public void send(XMLStreamWriter xmlSerializer, int version) throws
            XMLStreamException {
        super.sendRoot(xmlSerializer, version, "iq", null);
        if (childFragment != null) {
            childFragment.send(xmlSerializer, version);
        }
        xmlSerializer.writeEndElement();
    }

    public XMPPFragment createDeepCopy() {
        IQImpl iq = new IQImpl();
        copyAttributes(iq);
        iq.childFragment = childFragment;
        iq.childName = childName;
        iq.childNamespace = childNamespace;
        return iq;
    }

    public Iterator getFragments() {
        Iterator frags;
        if (childFragment == null) {
            frags = Collections.EMPTY_LIST.iterator();
        }
        else {
            List list = new ArrayList(1);
            list.add(childFragment);
            frags = list.iterator();
        }
        return frags;
    }

    public void clearFragments() {
        childFragment = null;
    }

    public void addFragment(XMPPFragment fragment) {
        if (fragment.equals(this)) {
            throw new IllegalArgumentException("Circular parent-child relationship");
        }
        setChildFragment(fragment);
    }

    /* send
    if (getSender() == null){
        Attribute fromAttribute = document.getRootElement().attribute("from");
        if (fromAttribute != null){
            document.getRootElement().remove(fromAttribute);
        }
    } else {
        document.getRootElement().addAttribute("from",getSender().toString());
    }
    XPPWriter.write(getDocument(),writer);


    public XMPPDocumentPacket createResult(Element body) {
        XMPPDocumentPacket resultPacket = createResult();
        Element root = resultPacket.getDocument().getRootElement();
        body.detach();
        if ("query".equals(body.getName())){
            root.add(body);
        } else {
            root.addElement("query",getIQNamespace()).add(body);
        }
        return resultPacket;
    }

    public XMPPDocumentPacket createQueryResult() {
        XMPPDocumentPacket resultPacket = createResult();
        resultPacket.getDocument().getRootElement().addElement("query",getIQNamespace());
        return resultPacket;
    }

    public XMPPDocumentPacket createResult() {
        XMPPDocumentPacket resultPacket = new XMPPDocumentPacket(DocumentHelper.createDocument(),localServerName);
        tagType = TYPE_IQ;
        Element root = DocumentHelper.createElement("iq");
        resultPacket.getDocument().setRootElement(root);

        if (getRecipient() != null){
            root.addAttribute("from",getRecipient().toString());
            resultPacket.setSender(getRecipient());
        }
        if (getSender() != null){
            root.addAttribute("to",getSender().toString());
            resultPacket.setRecipient(getSender());
        }
        root.addAttribute("type","result");

        if (getID() != null){
            root.addAttribute("id",getID());
        }
        resultPacket.setSender(null);
        resultPacket.setRecipient(getSender());
        resultPacket.setOriginatingSession(getOriginatingSession());
        return resultPacket;
    }

    */
    public String toString() {
        return "IQ " + Integer.toHexString(hashCode()) + " " + getChildName() + ">" + getChildNamespace() + " t: " + (type == null ? "no type" : type.toString()) + " S: " + sender + " R: " + recipient;
    }

}

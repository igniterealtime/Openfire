/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.forms.spi;

import org.jivesoftware.messenger.forms.FormField;
import org.jivesoftware.messenger.forms.XDataForm;
import org.jivesoftware.messenger.XMPPFragment;
import org.jivesoftware.util.ConcurrentHashSet;

import java.util.*;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;

/**
 * A concrete DataForm capable of sending itself to a writer and recover its state from an XMPP
 * stanza. XDataForms are packets of the form:
 * <code><pre>
 * &lt;x xmlns='jabber:x:data' type='{form-type}'&gt;
 * &lt;title/&gt;
 * &lt;instructions/&gt;
 * &lt;field var='field-name'
 *       type='{field-type}'
 *       label='description'&gt;
 *   &lt;desc/&gt;
 *   &lt;required/&gt;
 *   &lt;value&gt;field-value&lt;/value&gt;
 *   &lt;option label='option-label'&gt;&lt;value&gt;option-value&lt;/value&gt;&lt;/option&gt;
 *   &lt;option label='option-label'&gt;&lt;value&gt;option-value&lt;/value&gt;&lt;/option&gt;
 * &lt;/field&gt;
 * &lt;/x&gt;
 * </pre></code>
 * <p/>
 * An XDataFormImpl can contain zero or more XFormFieldImpl 'field' fragments.<p>
 * <p/>
 * To learn more follow this link: <a href="http://www.jabber.org/jeps/jep-0004.html">JEP-04</a>.
 *
 * @author gdombiak
 */
public class XDataFormImpl implements XDataForm {

    private String type;
    private String title;
    private List instructions = new ArrayList();
    private List fields = new ArrayList();
    private List reportedFields = new ArrayList();
    private List reportedItems = new ArrayList();
    private Set fragments = new ConcurrentHashSet();

    public XDataFormImpl() {
        super();
    }

    public XDataFormImpl(String type) {
        this.type = type;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setInstructions(List instructions) {
        this.instructions = instructions;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public Iterator getInstructions() {
        synchronized (instructions) {
            return Collections.unmodifiableList(new ArrayList(instructions)).iterator();
        }
    }

    public FormField getField(String variable) {
        if (variable == null || variable.equals("")) {
            throw new IllegalArgumentException("Variable must not be null or blank.");
        }
        // Look for the field whose variable matches the requested variable
        FormField field;
        for (Iterator it = getFields(); it.hasNext();) {
            field = (FormField)it.next();
            if (variable.equals(field.getVariable())) {
                return field;
            }
        }
        return null;
    }

    public Iterator getFields() {
        synchronized (fields) {
            return Collections.unmodifiableList(new ArrayList(fields)).iterator();
        }
    }

    public int getFieldsSize() {
        return fields.size();
    }

    public void addInstruction(String instruction) {
        synchronized (instructions) {
            instructions.add(instruction);
        }
    }

    public void addField(FormField field) {
        synchronized (fields) {
            fields.add(field);
        }
    }

    public void addReportedField(FormField field) {
        synchronized (reportedFields) {
            reportedFields.add(field);
        }
    }

    public void addItemFields(ArrayList itemFields) {
        synchronized (reportedItems) {
            // We are nesting a List (of fields) inside of the List of items
            reportedItems.add(itemFields);
        }
    }

    public String getNamespace() {
        // Is someone sending this message?
        return "jabber:x:data";
    }

    public void setNamespace(String namespace) {
        // Is someone sending this message?
        // Do nothing
    }

    public String getName() {
        // Is someone sending this message?
        return "x";
    }

    public void setName(String name) {
        // Is someone sending this message?
        // Do nothing
    }

    public void send(XMLStreamWriter xmlSerializer, int version) throws XMLStreamException {
        xmlSerializer.writeStartElement("jabber:x:data", "x");
        xmlSerializer.writeNamespace("", "jabber:x:data");
        if (getType() != null) {
            xmlSerializer.writeAttribute("type", getType());
        }
        if (getTitle() != null) {
            xmlSerializer.writeStartElement("jabber:x:data", "title");
            xmlSerializer.writeCharacters(getTitle());
            xmlSerializer.writeEndElement();
        }
        if (instructions.size() > 0) {
            Iterator instrItr = getInstructions();
            while (instrItr.hasNext()) {
                xmlSerializer.writeStartElement("jabber:x:data", "instructions");
                xmlSerializer.writeCharacters((String)instrItr.next());
                xmlSerializer.writeEndElement();
            }
        }
        // Append the list of fields returned from a search
        if (reportedFields.size() > 0) {
            xmlSerializer.writeStartElement("jabber:x:data", "reported");
            Iterator fieldsItr = reportedFields.iterator();
            while (fieldsItr.hasNext()) {
                XFormFieldImpl field = (XFormFieldImpl)fieldsItr.next();
                field.send(xmlSerializer, version);
            }
            xmlSerializer.writeEndElement();
        }

        // Append the list of items returned from a search
        // Note: each item contains a List of XFormFieldImpls
        if (reportedItems.size() > 0) {
            xmlSerializer.writeStartElement("jabber:x:data", "item");
            Iterator itemsItr = reportedItems.iterator();
            while (itemsItr.hasNext()) {
                List fields = (List)itemsItr.next();
                Iterator fieldsItr = fields.iterator();
                while (fieldsItr.hasNext()) {
                    XFormFieldImpl field = (XFormFieldImpl)fieldsItr.next();
                    field.send(xmlSerializer, version);
                }
            }
            xmlSerializer.writeEndElement();
        }

        if (fields.size() > 0) {
            Iterator fieldsItr = getFields();
            while (fieldsItr.hasNext()) {
                XFormFieldImpl field = (XFormFieldImpl)fieldsItr.next();
                field.send(xmlSerializer, version);
            }
        }

        // Loop through all the values and append them to the stream writer

        Iterator frags = fragments.iterator();
        while (frags.hasNext()) {
            XMPPFragment frag = (XMPPFragment)frags.next();
            frag.send(xmlSerializer, version);
        }
        xmlSerializer.writeEndElement();
    }

    public Element asXMLElement() {
        Element x = DocumentHelper.createElement(QName.get("x", "jabber:x:data"));
        if (getType() != null) {
            x.addAttribute("type", getType());
        }
        if (getTitle() != null) {
            x.addElement("title").addText(getTitle());
        }
        if (instructions.size() > 0) {
            Iterator instrItr = getInstructions();
            while (instrItr.hasNext()) {
                x.addElement("instructions").addText((String)instrItr.next());
            }
        }
        // Append the list of fields returned from a search
        if (reportedFields.size() > 0) {
            Element reportedElement = x.addElement("reported");
            Iterator fieldsItr = reportedFields.iterator();
            while (fieldsItr.hasNext()) {
                XFormFieldImpl field = (XFormFieldImpl)fieldsItr.next();
                reportedElement.add(field.asXMLElement());
            }
        }

        // Append the list of items returned from a search
        // Note: each item contains a List of XFormFieldImpls
        if (reportedItems.size() > 0) {
            Element itemElement = x.addElement("item");
            Iterator itemsItr = reportedItems.iterator();
            while (itemsItr.hasNext()) {
                List fields = (List)itemsItr.next();
                Iterator fieldsItr = fields.iterator();
                while (fieldsItr.hasNext()) {
                    XFormFieldImpl field = (XFormFieldImpl)fieldsItr.next();
                    itemElement.add(field.asXMLElement());
                }
            }
        }

        if (fields.size() > 0) {
            Iterator fieldsItr = getFields();
            while (fieldsItr.hasNext()) {
                XFormFieldImpl field = (XFormFieldImpl)fieldsItr.next();
                x.add(field.asXMLElement());
            }
        }

        // Loop through all the values and append them to the stream writer
        /*Iterator frags = fragments.iterator();
        while (frags.hasNext()){
            XMPPFragment frag = (XMPPFragment) frags.next();
            frag.send(xmlSerializer,version);
        }*/

        return x;
    }

    public XMPPFragment createDeepCopy() {
        XDataFormImpl copy = new XDataFormImpl(type);
        copy.title = this.title;
        copy.instructions = (List)((ArrayList)this.instructions).clone();

        Iterator fieldsIter = getFields();
        while (fieldsIter.hasNext()) {
            copy.addField((XFormFieldImpl) ((XFormFieldImpl) fieldsIter.next()).createDeepCopy());
        }
        
        Iterator fragmentIter = getFragments();
        while (fragmentIter.hasNext()) {
            copy.addFragment(((XMPPFragment)fragmentIter.next()).createDeepCopy());
        }
        return copy;
    }

    public void addFragment(XMPPFragment fragment) {
        fragments.add(fragment);
    }

    public Iterator getFragments() {
        return fragments.iterator();
    }

    public XMPPFragment getFragment(String name, String namespace) {
        if (fragments == null) {
            return null;
        }
        XMPPFragment frag;
        for (Iterator frags = fragments.iterator(); frags.hasNext();) {
            frag = (XMPPFragment)frags.next();
            if (name.equals(frag.getName()) && namespace.equals(frag.getNamespace())) {
                return frag;
            }
        }
        return null;
    }

    public void clearFragments() {
        fragments.clear();
    }

    public int getSize() {
        // TODO Is this OK? Shouldn't we need to consider the instance variables?
        return fragments.size();
    }

    public void parse(Element formElement) {
        type = formElement.attributeValue("type");
        Element titleElement = formElement.element("title");
        if (titleElement != null) {
            setTitle(titleElement.getTextTrim());
        }
        Iterator instructionElements = formElement.elementIterator("instructions");
        while (instructionElements.hasNext()) {
            addInstruction(((Element)instructionElements.next()).getTextTrim());
        }
        Iterator fieldElements = formElement.elementIterator("field");
        while (fieldElements.hasNext()) {
            XFormFieldImpl field = new XFormFieldImpl();
            field.parse((Element)fieldElements.next());
            addField(field);
        }

        Element reportedElement = formElement.element("reported");
        if (reportedElement != null) {
            Iterator reportedFieldElements = reportedElement.elementIterator("field");
            while (reportedFieldElements.hasNext()) {
                XFormFieldImpl field = new XFormFieldImpl();
                field.parse((Element)reportedFieldElements.next());
                addReportedField(field);
            }
        }

        Iterator itemElements = formElement.elementIterator("item");
        while (itemElements.hasNext()) {
            Element itemElement = (Element)itemElements.next();
            Iterator itemFieldElements = itemElement.elementIterator("field");
            ArrayList itemFields = new ArrayList();
            while (itemFieldElements.hasNext()) {
                XFormFieldImpl field = new XFormFieldImpl();
                field.parse((Element)itemFieldElements.next());
                itemFields.add(field);
            }
            addItemFields(itemFields);
        }
    }
}
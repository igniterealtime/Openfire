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
import org.jivesoftware.util.ConcurrentHashSet;
import org.jivesoftware.messenger.XMPPFragment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

/**
 * A concrete FormField capable of sending itself to a writer and recover its state from an XMPP
 * stanza.
 *
 * @author gdombiak
 */
public class XFormFieldImpl implements XMPPFragment, FormField {

    private String description;
    private boolean required = false;
    private String label;
    private String variable;
    private String type;
    private List options = new ArrayList();
    private List values = new ArrayList();
    private ConcurrentHashSet fragments = new ConcurrentHashSet();

    public XFormFieldImpl() {
        super();
    }

    public XFormFieldImpl(String variable) {
        this.variable = variable;
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
        xmlSerializer.writeStartElement("jabber:x:data", "field");
        if (getLabel() != null) {
            xmlSerializer.writeAttribute("label", getLabel());
        }
        if (getVariable() != null) {
            xmlSerializer.writeAttribute("var", getVariable());
        }
        if (getType() != null) {
            xmlSerializer.writeAttribute("type", getType());
        }
        if (getDescription() != null) {
            xmlSerializer.writeStartElement("jabber:x:data", "desc");
            xmlSerializer.writeCharacters(getDescription());
            xmlSerializer.writeEndElement();
        }
        if (isRequired()) {
            xmlSerializer.writeStartElement("jabber:x:data", "required");
            xmlSerializer.writeEndElement();
        }
        // Loop through all the values and append them to the stream writer
        if (values.size() > 0) {
            Iterator valuesItr = getValues();
            while (valuesItr.hasNext()) {
                xmlSerializer.writeStartElement("jabber:x:data", "value");
                xmlSerializer.writeCharacters((String)valuesItr.next());
                xmlSerializer.writeEndElement();
            }
        }
        // Loop through all the options and append them to the stream writer
        if (options.size() > 0) {
            Iterator optionsItr = getOptions();
            while (optionsItr.hasNext()) {
                ((Option)optionsItr.next()).send(xmlSerializer, version);
            }
        }
        Iterator frags = fragments.iterator();
        while (frags.hasNext()) {
            XMPPFragment frag = (XMPPFragment)frags.next();
            frag.send(xmlSerializer, version);
        }
        xmlSerializer.writeEndElement();
    }

    public Element asXMLElement() {
        Element field = DocumentHelper.createElement("field");
        if (getLabel() != null) {
            field.addAttribute("label", getLabel());
        }
        if (getVariable() != null) {
            field.addAttribute("var", getVariable());
        }
        if (getType() != null) {
            field.addAttribute("type", getType());
        }

        if (getDescription() != null) {
            field.addElement("desc").addText(getDescription());
        }
        if (isRequired()) {
            field.addElement("required");
        }
        // Loop through all the values and append them to the stream writer
        if (values.size() > 0) {
            Iterator valuesItr = getValues();
            while (valuesItr.hasNext()) {
                field.addElement("value").addText((String)valuesItr.next());
            }
        }
        // Loop through all the options and append them to the stream writer
        if (options.size() > 0) {
            Iterator optionsItr = getOptions();
            while (optionsItr.hasNext()) {
                field.add(((Option)optionsItr.next()).asXMLElement());
            }
        }

        // Loop through all the values and append them to the stream writer
        /*Iterator frags = fragments.iterator();
        while (frags.hasNext()){
            XMPPFragment frag = (XMPPFragment) frags.next();
            frag.send(xmlSerializer,version);
        }*/

        return field;
    }

    public XMPPFragment createDeepCopy() {
        XFormFieldImpl copy = new XFormFieldImpl(variable);
        copy.description = this.description;
        copy.required = this.required;
        copy.label = this.label;
        copy.type = this.type;
        copy.options = (List)((ArrayList)this.options).clone();
        copy.values = (List)((ArrayList)this.values).clone();

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

    public void addValue(String value) {
        synchronized (values) {
            values.add(value);
        }
    }

    public void clearValues() {
        synchronized (values) {
            values.clear();
        }
    }

    public void addOption(String label, String value) {
        synchronized (options) {
            options.add(new Option(label, value));
        }
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRequired() {
        return required;
    }

    public String getVariable() {
        return variable;
    }

    public Iterator getValues() {
        synchronized (values) {
            return Collections.unmodifiableList(new ArrayList(values)).iterator();
        }
    }

    public String getType() {
        return type;
    }

    /**
     * Returns an Iterator for the available options that the user has in order to answer
     * the question.
     *
     * @return Iterator for the available options.
     */
    private Iterator getOptions() {
        synchronized (options) {
            return Collections.unmodifiableList(new ArrayList(options)).iterator();
        }
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public void parse(Element formElement) {
        variable = formElement.attributeValue("var");
        setLabel(formElement.attributeValue("label"));
        setType(formElement.attributeValue("type"));

        Element descElement = formElement.element("desc");
        if (descElement != null) {
            setDescription(descElement.getTextTrim());
        }
        if (formElement.element("required") != null) {
            setRequired(true);
        }
        Iterator valueElements = formElement.elementIterator("value");
        while (valueElements.hasNext()) {
            addValue(((Element)valueElements.next()).getTextTrim());
        }
        Iterator optionElements = formElement.elementIterator("option");
        Element optionElement;
        while (optionElements.hasNext()) {
            optionElement = (Element)optionElements.next();
            addOption(optionElement.attributeValue("label"), optionElement.elementTextTrim("value"));
        }
    }

    public String toString() {
        return "XFormFieldImpl " + Integer.toHexString(hashCode()) + " " + getVariable() + ">" + values
                + " o: " + (options.isEmpty() ? "no options" : options.toString());
    }

    /**
     * Represents the available option of a given FormField.
     *
     * @author Gaston Dombiak
     */
    private static class Option {
        private String label;
        private String value;

        public Option(String label, String value) {
            this.label = label;
            this.value = value;
        }

        /**
         * Returns the label that represents the option.
         *
         * @return the label that represents the option.
         */
        public String getLabel() {
            return label;
        }

        /**
         * Returns the value of the option.
         *
         * @return the value of the option.
         */
        public String getValue() {
            return value;
        }

        public void send(XMLStreamWriter xmlSerializer, int version) throws XMLStreamException {
            xmlSerializer.writeStartElement("jabber:x:data", "option");
            if (getLabel() != null) {
                xmlSerializer.writeAttribute("label", getLabel());
            }
            if (getValue() != null) {
                xmlSerializer.writeStartElement("jabber:x:data", "value");
                xmlSerializer.writeCharacters(getValue());
                xmlSerializer.writeEndElement();
            }
            xmlSerializer.writeEndElement();
        }

        public Element asXMLElement() {
            Element option = DocumentHelper.createElement("option");
            if (getLabel() != null) {
                option.addAttribute("label", getLabel());
            }
            if (getValue() != null) {
                option.addElement("value").addText(getValue());
            }
            return option;
        }
    }
}

/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.forms.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.forms.FormField;

/**
 * A concrete FormField capable of sending itself to a writer and recover its state from an XMPP
 * stanza.
 *
 * @author gdombiak
 * @deprecated replaced by {@link org.xmpp.forms.FormField}
 */
@Deprecated
public class XFormFieldImpl implements FormField {

    private String description;
    private boolean required = false;
    private String label;
    private String variable;
    private String type;
    private List<Option> options = new ArrayList<>();
    private List<String> values = new ArrayList<>();

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

    public Element asXMLElement() {
        Element field = DocumentHelper.createElement(QName.get("field", "jabber:x:data"));
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
            Iterator<String> valuesItr = getValues();
            while (valuesItr.hasNext()) {
                field.addElement("value").addText(valuesItr.next());
            }
        }
        // Loop through all the options and append them to the stream writer
        if (options.size() > 0) {
            Iterator<Option> optionsItr = getOptions();
            while (optionsItr.hasNext()) {
                field.add((optionsItr.next()).asXMLElement());
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

    @Override
    public void addValue(String value) {
        if (value == null) {
            value = "";
        }
        synchronized (values) {
            values.add(value);
        }
    }

    @Override
    public void clearValues() {
        synchronized (values) {
            values.clear();
        }
    }

    @Override
    public void addOption(String label, String value) {
        synchronized (options) {
            options.add(new Option(label, value));
        }
    }

    @Override
    public void setType(String type) {
        this.type = type;
    }

    @Override
    public void setRequired(boolean required) {
        this.required = required;
    }

    @Override
    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    @Override
    public String getVariable() {
        return variable;
    }

    @Override
    public Iterator<String> getValues() {
        synchronized (values) {
            return Collections.unmodifiableList(new ArrayList<>(values)).iterator();
        }
    }

    @Override
    public String getType() {
        return type;
    }

    /**
     * Returns an Iterator for the available options that the user has in order to answer
     * the question.
     *
     * @return Iterator for the available options.
     */
    private Iterator<Option> getOptions() {
        synchronized (options) {
            return Collections.unmodifiableList(new ArrayList<>(options)).iterator();
        }
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
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

    @Override
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

        public Element asXMLElement() {
            Element option = DocumentHelper.createElement(QName.get("option", "jabber:x:data"));
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

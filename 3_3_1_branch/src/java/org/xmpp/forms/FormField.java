/**
 * Copyright (C) 2004-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.xmpp.forms;

import org.dom4j.Element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a field of a form. The field could be used to represent a question to complete,
 * a completed question or a data returned from a search. The exact interpretation of the field
 * depends on the context where the field is used.
 *
 * @author Gaston Dombiak
 */
public class FormField {

    private Element element;

    FormField(Element element) {
        this.element = element;
    }

    /**
     * Adds a default value to the question if the question is part of a form to fill out.
     * Otherwise, adds an answered value to the question.
     *
     * @param value a default value or an answered value of the question.
     */
    public void addValue(Object value) {
        element.addElement("value").setText(DataForm.encode(value));
    }

    /**
     * Removes all the values of the field.
     */
    public void clearValues() {
        for (Iterator it = element.elementIterator("value"); it.hasNext();) {
            it.next();
            it.remove();
        }
    }

    /**
     * Adds an available option to the question that the user has in order to answer
     * the question.
     *
     * @param label a label that represents the option.
     * @param value the value of the option.
     */
    public void addOption(String label, String value) {
        Element option = element.addElement("option");
        option.addAttribute("label", label);
        option.addElement("value").setText(value);
    }

    /**
     * Returns the available options to answer for this question. The returned options cannot
     * be modified but they will be updated if the underlying DOM object gets updated.
     *
     * @return the available options to answer for this question.
     */
    public List<Option> getOptions() {
        List<Option> answer = new ArrayList<Option>();
        for (Iterator it = element.elementIterator("option"); it.hasNext();) {
            answer.add(new Option((Element) it.next()));
        }
        return answer;
    }

    /**
     * Sets an indicative of the format for the data to answer. Valid formats are:
     * <p/>
     * <ul>
     * <li>text-single -> single line or word of text
     * <li>text-private -> instead of showing the user what they typed, you show ***** to
     * protect it
     * <li>text-multi -> multiple lines of text entry
     * <li>list-single -> given a list of choices, pick one
     * <li>list-multi -> given a list of choices, pick one or more
     * <li>boolean -> 0 or 1, true or false, yes or no. Default value is 0
     * <li>fixed -> fixed for putting in text to show sections, or just advertise your web
     * site in the middle of the form
     * <li>hidden -> is not given to the user at all, but returned with the questionnaire
     * <li>jid-single -> Jabber ID - choosing a JID from your roster, and entering one based
     * on the rules for a JID.
     * <li>jid-multi -> multiple entries for JIDs
     * </ul>
     *
     * @param type an indicative of the format for the data to answer.
     */
    public void setType(Type type) {
        element.addAttribute("type", type==null?null:type.toXMPP());
    }

    /**
     * Sets the attribute that uniquely identifies the field in the context of the form. If the
     * field is of type "fixed" then the variable is optional.
     *
     * @param var the unique identifier of the field in the context of the form.
     */
    public void setVariable(String var) {
        element.addAttribute("var", var);
    }

    /**
     * Sets the label of the question which should give enough information to the user to
     * fill out the form.
     *
     * @param label the label of the question.
     */
    public void setLabel(String label) {
        element.addAttribute("label", label);
    }

    /**
     * Sets if the question must be answered in order to complete the questionnaire.
     *
     * @param required if the question must be answered in order to complete the questionnaire.
     */
    public void setRequired(boolean required) {
        // Remove an existing desc element.
        if (element.element("required") != null) {
            element.remove(element.element("required"));
        }
        if (required) {
            element.addElement("required");
        }
    }

    /**
     * Sets a description that provides extra clarification about the question. This information
     * could be presented to the user either in tool-tip, help button, or as a section of text
     * before the question.<p>
     * <p/>
     * If the question is of type FIXED then the description should remain empty.
     *
     * @param description provides extra clarification about the question.
     */
    public void setDescription(String description) {
        // Remove an existing desc element.
        if (element.element("desc") != null) {
            element.remove(element.element("desc"));
        }
        element.addElement("desc").setText(description);
    }

    /**
     * Returns true if the question must be answered in order to complete the questionnaire.
     *
     * @return true if the question must be answered in order to complete the questionnaire.
     */
    public boolean isRequired() {
        return element.element("required") != null;
    }

    /**
     * Returns the variable name that the question is filling out.
     *
     * @return the variable name of the question.
     */
    public String getVariable() {
        return element.attributeValue("var");
    }

    /**
     * Returns an Iterator for the default values of the question if the question is part
     * of a form to fill out. Otherwise, returns an Iterator for the answered values of
     * the question.
     *
     * @return an Iterator for the default values or answered values of the question.
     */
    public List<String> getValues() {
        List<String> answer = new ArrayList<String>();
        for (Iterator it = element.elementIterator("value"); it.hasNext();) {
            answer.add(((Element) it.next()).getTextTrim());
        }
        return answer;
    }

    /**
     * Returns an indicative of the format for the data to answer. Valid formats are:
     * <p/>
     * <ul>
     * <li>text-single -> single line or word of text
     * <li>text-private -> instead of showing the user what they typed, you show ***** to
     * protect it
     * <li>text-multi -> multiple lines of text entry
     * <li>list-single -> given a list of choices, pick one
     * <li>list-multi -> given a list of choices, pick one or more
     * <li>boolean -> 0 or 1, true or false, yes or no. Default value is 0
     * <li>fixed -> fixed for putting in text to show sections, or just advertise your web
     * site in the middle of the form
     * <li>hidden -> is not given to the user at all, but returned with the questionnaire
     * <li>jid-single -> Jabber ID - choosing a JID from your roster, and entering one based
     * on the rules for a JID.
     * <li>jid-multi -> multiple entries for JIDs
     * </ul>
     *
     * @return format for the data to answer.
     */
    public Type getType() {
        String type = element.attributeValue("type");
        if (type != null) {
            Type.fromXMPP(type);
        }
        return null;
    }

    /**
     * Returns the label of the question which should give enough information to the user to
     * fill out the form.
     *
     * @return label of the question.
     */
    public String getLabel() {
        return element.attributeValue("label");
    }

    /**
     * Returns a description that provides extra clarification about the question. This information
     * could be presented to the user either in tool-tip, help button, or as a section of text
     * before the question.<p>
     * <p/>
     * If the question is of type FIXED then the description should remain empty.
     *
     * @return description that provides extra clarification about the question.
     */
    public String getDescription() {
        return element.elementTextTrim("desc");
    }

    /**
     * Represents the available option of a given FormField.
     *
     * @author Gaston Dombiak
     */
    public static class Option {
        private Element element;

        private Option(Element element) {
            this.element = element;
        }

        /**
         * Returns the label that represents the option.
         *
         * @return the label that represents the option.
         */
        public String getLabel() {
            return element.attributeValue("label");
        }

        /**
         * Returns the value of the option.
         *
         * @return the value of the option.
         */
        public String getValue() {
            return element.elementTextTrim("value");
        }
    }

    /**
     * Type-safe enumeration to represent the field type of the Data forms.<p>
     *
     * Implementation note: XMPP error conditions use "-" characters in
     * their names such as "jid-multi". Because "-" characters are not valid
     * identifier parts in Java, they have been converted to "_" characters in
     * the  enumeration names, such as <tt>jid_multi</tt>. The {@link #toXMPP()} and
     * {@link #fromXMPP(String)} methods can be used to convert between the
     * enumertation values and Type code strings.
     */
    public enum Type {
        /**
         * The field enables an entity to gather or provide an either-or choice between two
         * options. The allowable values are 1 for yes/true/assent and 0 for no/false/decline.
         * The default value is 0.
         */
        boolean_type("boolean"),

        /**
         * The field is intended for data description (e.g., human-readable text such as
         * "section" headers) rather than data gathering or provision. The <value/> child
         * SHOULD NOT contain newlines (the \n and \r characters); instead an application
         * SHOULD generate multiple fixed fields, each with one <value/> child.
         */
        fixed("fixed"),

        /**
         * The field is not shown to the entity providing information, but instead is
         * returned with the form.
         */
        hidden("hidden"),

        /**
         * The field enables an entity to gather or provide multiple Jabber IDs.
         */
        jid_multi("jid-multi"),

        /**
         * The field enables an entity to gather or provide multiple Jabber IDs.
         */
        jid_single("jid-single"),

        /**
         * The field enables an entity to gather or provide one or more options from
         * among many.
         */
        list_multi("list-multi"),

        /**
         * The field enables an entity to gather or provide one option from among many.
         */
        list_single("list-single"),

        /**
         * The field enables an entity to gather or provide multiple lines of text.
         */
        text_multi("text-multi"),

        /**
         * The field enables an entity to gather or provide a single line or word of text,
         * which shall be obscured in an interface (e.g., *****).
         */
        text_private("text-private"),

        /**
         * The field enables an entity to gather or provide a single line or word of text,
         * which may be shown in an interface. This field type is the default and MUST be
         * assumed if an entity receives a field type it does not understand.
         */
        text_single("text-single");

        /**
         * Converts a String value into its Type representation.
         *
         * @param type the String value.
         * @return the type corresponding to the String.
         */
        public static Type fromXMPP(String type) {
            if (type == null) {
                throw new NullPointerException();
            }
            type = type.toLowerCase();
            if (boolean_type.toXMPP().equals(type)) {
                return boolean_type;
            }
            else if (fixed.toXMPP().equals(type)) {
                return fixed;
            }
            else if (hidden.toXMPP().equals(type)) {
                return hidden;
            }
            else if (jid_multi.toXMPP().equals(type)) {
                return jid_multi;
            }
            else if (jid_single.toXMPP().equals(type)) {
                return jid_single;
            }
            else if (list_multi.toXMPP().equals(type)) {
                return list_multi;
            }
            else if (list_single.toXMPP().equals(type)) {
                return list_single;
            }
            else if (text_multi.toXMPP().equals(type)) {
                return text_multi;
            }
            else if (text_private.toXMPP().equals(type)) {
                return text_private;
            }
            else if (text_single.toXMPP().equals(type)) {
                return text_single;
            }
            else {
                throw new IllegalArgumentException("Type invalid:" + type);
            }
        }

        private String value;

        private Type(String value) {
            this.value = value;
        }

        /**
         * Returns the Field Type as a valid Field Type code string.
         *
         * @return the Field Type value.
         */
        public String toXMPP() {
            return value;
        }

    }
}

/**
 * $RCSfile$
 * $Revision: 223 $
 * $Date: 2004-11-07 22:52:36 -0300 (Sun, 07 Nov 2004) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.forms;

import java.util.Iterator;

/**
 * Represents a field of a form. The field could be used to represent a question to complete,
 * a completed question or a data returned from a search. The exact interpretation of the field
 * depends on the context where the field is used.
 *
 * @author Gaston Dombiak
 * @deprecated replaced by {@link org.xmpp.forms.FormField}
 */
@Deprecated
public interface FormField {

    public static final String TYPE_BOOLEAN = "boolean";
    public static final String TYPE_FIXED = "fixed";
    public static final String TYPE_HIDDEN = "hidden";
    public static final String TYPE_JID_MULTI = "jid-multi";
    public static final String TYPE_JID_SINGLE = "jid-single";
    public static final String TYPE_LIST_MULTI = "list-multi";
    public static final String TYPE_LIST_SINGLE = "list-single";
    public static final String TYPE_TEXT_MULTI = "text-multi";
    public static final String TYPE_TEXT_PRIVATE = "text-private";
    public static final String TYPE_TEXT_SINGLE = "text-single";

    /**
     * Adds a default value to the question if the question is part of a form to fill out.
     * Otherwise, adds an answered value to the question.
     *
     * @param value a default value or an answered value of the question.
     */
    public void addValue(String value);

    /**
     * Removes all the values of the field.
     */
    public void clearValues();

    /**
     * Adds an available option to the question that the user has in order to answer
     * the question.
     *
     * @param label a label that represents the option.
     * @param value the value of the option.
     */
    public void addOption(String label, String value);

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
    public abstract void setType(String type);

    /**
     * Sets if the question must be answered in order to complete the questionnaire.
     *
     * @param required if the question must be answered in order to complete the questionnaire.
     */
    public abstract void setRequired(boolean required);

    /**
     * Sets the label of the question which should give enough information to the user to
     * fill out the form.
     *
     * @param label the label of the question.
     */
    public abstract void setLabel(String label);

    /**
     * Sets a description that provides extra clarification about the question. This information
     * could be presented to the user either in tool-tip, help button, or as a section of text
     * before the question.<p>
     * <p/>
     * If the question is of type FIXED then the description should remain empty.
     *
     * @param description provides extra clarification about the question.
     */
    public abstract void setDescription(String description);

    /**
     * Returns true if the question must be answered in order to complete the questionnaire.
     *
     * @return true if the question must be answered in order to complete the questionnaire.
     */
    public abstract boolean isRequired();

    /**
     * Returns the variable name that the question is filling out.
     *
     * @return the variable name of the question.
     */
    public abstract String getVariable();

    /**
     * Returns an Iterator for the default values of the question if the question is part
     * of a form to fill out. Otherwise, returns an Iterator for the answered values of
     * the question.
     *
     * @return an Iterator for the default values or answered values of the question.
     */
    public abstract Iterator<String> getValues();

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
    public abstract String getType();

    /**
     * Returns the label of the question which should give enough information to the user to
     * fill out the form.
     *
     * @return label of the question.
     */
    public abstract String getLabel();

    /**
     * Returns a description that provides extra clarification about the question. This information
     * could be presented to the user either in tool-tip, help button, or as a section of text
     * before the question.<p>
     * <p/>
     * If the question is of type FIXED then the description should remain empty.
     *
     * @return description that provides extra clarification about the question.
     */
    public abstract String getDescription();
}

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

    String TYPE_BOOLEAN = "boolean";
    String TYPE_FIXED = "fixed";
    String TYPE_HIDDEN = "hidden";
    String TYPE_JID_MULTI = "jid-multi";
    String TYPE_JID_SINGLE = "jid-single";
    String TYPE_LIST_MULTI = "list-multi";
    String TYPE_LIST_SINGLE = "list-single";
    String TYPE_TEXT_MULTI = "text-multi";
    String TYPE_TEXT_PRIVATE = "text-private";
    String TYPE_TEXT_SINGLE = "text-single";

    /**
     * Adds a default value to the question if the question is part of a form to fill out.
     * Otherwise, adds an answered value to the question.
     *
     * @param value a default value or an answered value of the question.
     */
    void addValue( String value );

    /**
     * Removes all the values of the field.
     */
    void clearValues();

    /**
     * Adds an available option to the question that the user has in order to answer
     * the question.
     *
     * @param label a label that represents the option.
     * @param value the value of the option.
     */
    void addOption( String label, String value );

    /**
     * Sets an indicative of the format for the data to answer. Valid formats are:
     * <ul>
     * <li>text-single -&gt; single line or word of text
     * <li>text-private -&gt; instead of showing the user what they typed, you show ***** to
     * protect it
     * <li>text-multi -&gt; multiple lines of text entry
     * <li>list-single -&gt; given a list of choices, pick one
     * <li>list-multi -&gt; given a list of choices, pick one or more
     * <li>boolean -&gt; 0 or 1, true or false, yes or no. Default value is 0
     * <li>fixed -&gt; fixed for putting in text to show sections, or just advertise your web
     * site in the middle of the form
     * <li>hidden -&gt; is not given to the user at all, but returned with the questionnaire
     * <li>jid-single -&gt; Jabber ID - choosing a JID from your roster, and entering one based
     * on the rules for a JID.
     * <li>jid-multi -&gt; multiple entries for JIDs
     * </ul>
     *
     * @param type an indicative of the format for the data to answer.
     */
    void setType( String type );

    /**
     * Sets if the question must be answered in order to complete the questionnaire.
     *
     * @param required if the question must be answered in order to complete the questionnaire.
     */
    void setRequired( boolean required );

    /**
     * Sets the label of the question which should give enough information to the user to
     * fill out the form.
     *
     * @param label the label of the question.
     */
    void setLabel( String label );

    /**
     * Sets a description that provides extra clarification about the question. This information
     * could be presented to the user either in tool-tip, help button, or as a section of text
     * before the question.
     * <p>
     * If the question is of type FIXED then the description should remain empty.
     * </p>
     *
     * @param description provides extra clarification about the question.
     */
    void setDescription( String description );

    /**
     * Returns true if the question must be answered in order to complete the questionnaire.
     *
     * @return true if the question must be answered in order to complete the questionnaire.
     */
    boolean isRequired();

    /**
     * Returns the variable name that the question is filling out.
     *
     * @return the variable name of the question.
     */
    String getVariable();

    /**
     * Returns an Iterator for the default values of the question if the question is part
     * of a form to fill out. Otherwise, returns an Iterator for the answered values of
     * the question.
     *
     * @return an Iterator for the default values or answered values of the question.
     */
    Iterator<String> getValues();

    /**
     * Returns an indicative of the format for the data to answer. Valid formats are:
     * <ul>
     * <li>text-single -&gt; single line or word of text
     * <li>text-private -&gt; instead of showing the user what they typed, you show ***** to
     * protect it
     * <li>text-multi -&gt; multiple lines of text entry
     * <li>list-single -&gt; given a list of choices, pick one
     * <li>list-multi -&gt; given a list of choices, pick one or more
     * <li>boolean -&gt; 0 or 1, true or false, yes or no. Default value is 0
     * <li>fixed -&gt; fixed for putting in text to show sections, or just advertise your web
     * site in the middle of the form
     * <li>hidden -&gt; is not given to the user at all, but returned with the questionnaire
     * <li>jid-single -&gt; Jabber ID - choosing a JID from your roster, and entering one based
     * on the rules for a JID.
     * <li>jid-multi -&gt; multiple entries for JIDs
     * </ul>
     *
     * @return format for the data to answer.
     */
    String getType();

    /**
     * Returns the label of the question which should give enough information to the user to
     * fill out the form.
     *
     * @return label of the question.
     */
    String getLabel();

    /**
     * Returns a description that provides extra clarification about the question. This information
     * could be presented to the user either in tool-tip, help button, or as a section of text
     * before the question.
     * <p>
     * If the question is of type FIXED then the description should remain empty.
     * </p>
     *
     * @return description that provides extra clarification about the question.
     */
    String getDescription();
}

/**
 * $RCSfile$
 * $Revision: 128 $
 * $Date: 2004-10-25 20:42:00 -0300 (Mon, 25 Oct 2004) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.forms;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a form that could be use for gathering data as well as for reporting data
 * returned from a search.
 * <p/>
 * The form could be of the following types:
 * <ul>
 * <li>form -> Indicates a form to fill out.</li>
 * <li>submit -> The form is filled out, and this is the data that is being returned from
 * the form.</li>
 * <li>cancel -> The form was cancelled. Tell the asker that piece of information.</li>
 * <li>result -> Data results being returned from a search, or some other query.</li>
 * </ul>
 * <p/>
 * In case the form represents a search, the report will be structured in columns and rows. Use
 * {@link #addReportedField(FormField)} to set the columns of the report whilst the report's rows
 * can be configured using {@link #addItemFields(ArrayList)}.
 *
 * @author gdombiak
 * @deprecated replaced by {@link org.xmpp.forms.DataForm}
 */
@Deprecated
public interface DataForm {

    public static final String TYPE_FORM = "form";
    public static final String TYPE_SUBMIT = "submit";
    public static final String TYPE_CANCEL = "cancel";
    public static final String TYPE_RESULT = "result";

    /**
     * Sets the description of the data. It is similar to the title on a web page or an X window.
     * You can put a <title/> on either a form to fill out, or a set of data results.
     *
     * @param title description of the data.
     */
    public abstract void setTitle(String title);

    /**
     * Sets the list of instructions that explain how to fill out the form and what the form is
     * about. The dataform could include multiple instructions since each instruction could not
     * contain newlines characters.
     *
     * @param instructions list of instructions that explain how to fill out the form.
     */
    public abstract void setInstructions(List instructions);

    /**
     * Returns the meaning of the data within the context. The data could be part of a form
     * to fill out, a form submission or data results.<p>
     * <p/>
     * Possible form types are:
     * <ul>
     * <li>form -> This packet contains a form to fill out. Display it to the user (if your
     * program can).</li>
     * <li>submit -> The form is filled out, and this is the data that is being returned from
     * the form.</li>
     * <li>cancel -> The form was cancelled. Tell the asker that piece of information.</li>
     * <li>result -> Data results being returned from a search, or some other query.</li>
     * </ul>
     *
     * @return the form's type.
     */
    public abstract String getType();

    /**
     * Returns the description of the data. It is similar to the title on a web page or an X
     * window.  You can put a <title/> on either a form to fill out, or a set of data results.
     *
     * @return description of the data.
     */
    public abstract String getTitle();

    /**
     * Returns an Iterator for the list of instructions that explain how to fill out the form and
     * what the form is about. The dataform could include multiple instructions since each
     * instruction could not contain newlines characters. Join the instructions together in order
     * to show them to the user.
     *
     * @return an Iterator for the list of instructions that explain how to fill out the form.
     */
    public abstract Iterator getInstructions();

    /**
     * Returns the field of the form whose variable matches the specified variable.
     * The fields of type FIXED will never be returned since they do not specify a
     * variable.
     *
     * @param variable the variable to look for in the form fields.
     * @return the field of the form whose variable matches the specified variable.
     */
    public FormField getField(String variable);

    /**
     * Returns an Iterator for the fields that are part of the form.
     *
     * @return an Iterator for the fields that are part of the form.
     */
    public abstract Iterator getFields();

    /**
     * Returns the number of fields included in the form.
     *
     * @return the number of fields included in the form.
     */
    public abstract int getFieldsSize();

    /**
     * Adds a new instruction to the list of instructions that explain how to fill out the form
     * and what the form is about. The dataform could include multiple instructions since each
     * instruction could not contain newlines characters.
     *
     * @param instruction the new instruction that explain how to fill out the form.
     */
    public abstract void addInstruction(String instruction);

    /**
     * Adds a new field as part of the form.
     *
     * @param field the field to add to the form.
     */
    public abstract void addField(FormField field);

    /**
     * Adds a field to the list of fields that will be returned from a search. Each field represents
     * a column in the report. The order of the columns in the report will honor the sequence in
     * which they were added.
     *
     * @param field the field to add to the list of fields that will be returned from a search.
     */
    public abstract void addReportedField(FormField field);

    /**
     * Adds a new row of items of reported data. The list of items to add will be formed by
     * FormFields. Each FormField variable <b>must</b> be valid (i.e. the variable must be defined
     * by the FormFields added as ReportedField.
     *
     * @param itemFields list of FormFields to add as a row in the report.
     */
    public abstract void addItemFields(ArrayList itemFields);
}

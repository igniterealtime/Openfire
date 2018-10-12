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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a form that could be use for gathering data as well as for reporting data
 * returned from a search.
 * <p>
 * The form could be of the following types:
 * </p>
 * <ul>
 * <li>form -&gt; Indicates a form to fill out.</li>
 * <li>submit -&gt; The form is filled out, and this is the data that is being returned from
 * the form.</li>
 * <li>cancel -&gt; The form was cancelled. Tell the asker that piece of information.</li>
 * <li>result -&gt; Data results being returned from a search, or some other query.</li>
 * </ul>
 * In case the form represents a search, the report will be structured in columns and rows. Use
 * {@link #addReportedField(FormField)} to set the columns of the report whilst the report's rows
 * can be configured using {@link #addItemFields(ArrayList)}.
 *
 * @author gdombiak
 * @deprecated replaced by {@link org.xmpp.forms.DataForm}
 */
@Deprecated
public interface DataForm {

    String TYPE_FORM = "form";
    String TYPE_SUBMIT = "submit";
    String TYPE_CANCEL = "cancel";
    String TYPE_RESULT = "result";

    /**
     * Sets the description of the data. It is similar to the title on a web page or an X window.
     * You can put a {@code <title/>} on either a form to fill out, or a set of data results.
     *
     * @param title description of the data.
     */
    void setTitle( String title );

    /**
     * Sets the list of instructions that explain how to fill out the form and what the form is
     * about. The dataform could include multiple instructions since each instruction could not
     * contain newlines characters.
     *
     * @param instructions list of instructions that explain how to fill out the form.
     */
    void setInstructions( List instructions );

    /**
     * Returns the meaning of the data within the context. The data could be part of a form
     * to fill out, a form submission or data results.
     * <p>
     * Possible form types are:
     * </p>
     * <ul>
     * <li>form -&gt; This packet contains a form to fill out. Display it to the user (if your
     * program can).</li>
     * <li>submit -&gt; The form is filled out, and this is the data that is being returned from
     * the form.</li>
     * <li>cancel -&gt; The form was cancelled. Tell the asker that piece of information.</li>
     * <li>result -&gt; Data results being returned from a search, or some other query.</li>
     * </ul>
     *
     * @return the form's type.
     */
    String getType();

    /**
     * Returns the description of the data. It is similar to the title on a web page or an X
     * window.  You can put a {@code <title/>} on either a form to fill out, or a set of data results.
     *
     * @return description of the data.
     */
    String getTitle();

    /**
     * Returns an Iterator for the list of instructions that explain how to fill out the form and
     * what the form is about. The dataform could include multiple instructions since each
     * instruction could not contain newlines characters. Join the instructions together in order
     * to show them to the user.
     *
     * @return an Iterator for the list of instructions that explain how to fill out the form.
     */
    Iterator getInstructions();

    /**
     * Returns the field of the form whose variable matches the specified variable.
     * The fields of type FIXED will never be returned since they do not specify a
     * variable.
     *
     * @param variable the variable to look for in the form fields.
     * @return the field of the form whose variable matches the specified variable.
     */
    FormField getField( String variable );

    /**
     * Returns an Iterator for the fields that are part of the form.
     *
     * @return an Iterator for the fields that are part of the form.
     */
    Iterator getFields();

    /**
     * Returns the number of fields included in the form.
     *
     * @return the number of fields included in the form.
     */
    int getFieldsSize();

    /**
     * Adds a new instruction to the list of instructions that explain how to fill out the form
     * and what the form is about. The dataform could include multiple instructions since each
     * instruction could not contain newlines characters.
     *
     * @param instruction the new instruction that explain how to fill out the form.
     */
    void addInstruction( String instruction );

    /**
     * Adds a new field as part of the form.
     *
     * @param field the field to add to the form.
     */
    void addField( FormField field );

    /**
     * Adds a field to the list of fields that will be returned from a search. Each field represents
     * a column in the report. The order of the columns in the report will honor the sequence in
     * which they were added.
     *
     * @param field the field to add to the list of fields that will be returned from a search.
     */
    void addReportedField( FormField field );

    /**
     * Adds a new row of items of reported data. The list of items to add will be formed by
     * FormFields. Each FormField variable <b>must</b> be valid (i.e. the variable must be defined
     * by the FormFields added as ReportedField.
     *
     * @param itemFields list of FormFields to add as a row in the report.
     */
    void addItemFields( ArrayList itemFields );
}

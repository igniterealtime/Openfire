/*
 * Copyright (C) 1999-2006 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.fastpath.dataforms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.xmpp.workgroup.DbProperties;
import org.jivesoftware.xmpp.workgroup.UnauthorizedException;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import com.thoughtworks.xstream.XStream;


public class FormManager {
    
    private static final Logger Log = LoggerFactory.getLogger(FormManager.class);
    
    private static FormManager singleton = new FormManager();

    private Map<Workgroup, WorkgroupForm> forms = new ConcurrentHashMap<Workgroup, WorkgroupForm>();

    /**
     * Returns the singleton instance of <CODE>FormManager</CODE>,
     * creating it if necessary.
     * <p/>
     *
     * @return the singleton instance of <Code>FormManager</CODE>
     */
    public static FormManager getInstance() {
        return singleton;
    }

    private FormManager() {
        // Prevent initialization
        loadWebForms();
    }

    public void addWorkgroupForm(Workgroup workgroup, WorkgroupForm workgroupForm) {
        forms.put(workgroup, workgroupForm);
    }

    public WorkgroupForm getWebForm(Workgroup workgroup) {
        return forms.get(workgroup);
    }

    public void removeForm(Workgroup workgroup) {
        forms.remove(workgroup);
    }

    public void saveWorkgroupForm(Workgroup workgroup) {
        // Save Web Form for editing
        WorkgroupForm workgroupForm = getWebForm(workgroup);
        if (workgroupForm != null) {
            XStream xstream = new XStream();
            String xmlToSave = xstream.toXML(workgroupForm);

            DbProperties props = workgroup.getProperties();
            String context = "jive.webform.wg";
            try {
                props.deleteProperty(context);

                props.setProperty(context, xmlToSave);
            }
            catch (UnauthorizedException e) {
                Log.error(e.getMessage(), e);
            }
        }

        // Save DataForm for usage
        saveDataForm(workgroup);
    }

    private void saveDataForm(Workgroup workgroup) {
        DataForm dataForm = new DataForm(DataForm.Type.form);
        WorkgroupForm form = getWebForm(workgroup);

        if (form.getTitle() != null) {
            dataForm.setTitle(form.getTitle());
        }

        if (form.getDescription() != null) {
            dataForm.addInstruction(form.getDescription());
        }

        List<FormElement> elems = new ArrayList<FormElement>();
        // Add normal elems
        int size = form.getFormElements().size();
        for (int j = 0; j < size; j++) {
            elems.add(form.getFormElementAt(j));
        }

        size = form.getHiddenVars().size();
        for (int k = 0; k < size; k++) {
            elems.add(form.getHiddenVars().get(k));
        }

        size = elems.size();

        for (int i = 0; i < size; i++) {
            FormElement elem = elems.get(i);

            FormField field = dataForm.addField();
            field.setLabel(elem.getLabel());
            field.setVariable(elem.getVariable());
            field.setRequired(elem.isRequired());

            if (elem.getDescription() != null) {
                field.setDescription(elem.getDescription());
            }

            if (elem.getAnswerType() == WorkgroupForm.FormEnum.textarea) {
                field.setType(FormField.Type.text_multi);
            }
            else if (elem.getAnswerType() == WorkgroupForm.FormEnum.textfield) {
                field.setType(FormField.Type.text_single);
            }
            else if (elem.getAnswerType() == WorkgroupForm.FormEnum.checkbox) {
                field.setType(FormField.Type.boolean_type);
            }
            else if (elem.getAnswerType() == WorkgroupForm.FormEnum.radio_button) {
                field.setType(FormField.Type.list_multi);
            }
            else if (elem.getAnswerType() == WorkgroupForm.FormEnum.dropdown_box) {
                field.setType(FormField.Type.list_single);
            }
            else if(elem.getAnswerType() == WorkgroupForm.FormEnum.hidden){
                field.setType(FormField.Type.hidden);
            } else if (elem.getAnswerType() == WorkgroupForm.FormEnum.password) {
                field.setType(FormField.Type.text_private);
            }

            if (elem.getAnswers().size() > 0 && elem.getAnswerType() != WorkgroupForm.FormEnum.hidden) {
                for(String item : elem.getAnswers()) {
                    field.addOption(item, item);
                }
            }
            else if (elem.getAnswers().size() > 0) {
                // Add hidden element values.
                for(String item : elem.getAnswers()) {
                    field.addValue(item);
                }
            }
        }

        XStream xstream = new XStream();
        String xmlToSave = xstream.toXML(dataForm);

        DbProperties props = workgroup.getProperties();
        String context = "jive.dataform.wg";
        try {
            props.deleteProperty(context);

            props.setProperty(context, xmlToSave);
        }
        catch (UnauthorizedException e) {
            Log.error(e.getMessage(), e);
        }

    }

    public DataForm getDataForm(Workgroup workgroup) {
        DbProperties props = workgroup.getProperties();
        String context = "jive.dataform.wg";
        String form = props.getProperty(context);

        if (form != null) {
            XStream xstream = new XStream();
            xstream.setClassLoader(this.getClass().getClassLoader());

            try {
                return (DataForm) xstream.fromXML(form);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
        return null;
    }

    private void loadWebForms() {
        final WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
        for (Workgroup workgroup : workgroupManager.getWorkgroups()) {
            DbProperties props = workgroup.getProperties();
            String context = "jive.webform.wg";
            String form = props.getProperty(context);

            if (form != null) {
                XStream xstream = new XStream();
                xstream.setClassLoader(this.getClass().getClassLoader());

                try {
                    Object object = xstream.fromXML(form);
                    WorkgroupForm workgroupForm = (WorkgroupForm)object;
                    if (workgroupForm != null) {
                        addWorkgroupForm(workgroup, workgroupForm);
                    }
                }
                catch (Exception e) {
                    Log.error(e.getMessage(), e);
                }
            }
            else {
                // Create a default Web Form
                createGenericForm(workgroup);
            }
        }

    }

    /**
     * Creates a new generic dataform for the Workgroup if one does not exist.
     *
     * @param workgroup the workgroup without a dataform.
     */
    public void createGenericForm(Workgroup workgroup) {
        WorkgroupForm workgroupForm = new WorkgroupForm();

        FormElement username = new FormElement();
        username.setRequired(true);
        username.setAnswerType(WorkgroupForm.FormEnum.textfield);
        username.setVariable("username");
        username.setLabel("Name:");
        workgroupForm.addFormElement(username);

        FormElement email = new FormElement();
        email.setRequired(true);
        email.setAnswerType(WorkgroupForm.FormEnum.textfield);
        email.setVariable("email");
        email.setLabel("Email Address:");
        workgroupForm.addFormElement(email);

        FormElement question = new FormElement();
        question.setRequired(true);
        question.setAnswerType(WorkgroupForm.FormEnum.textfield);
        question.setVariable("question");
        question.setLabel("Question:");
        workgroupForm.addFormElement(question);

        addWorkgroupForm(workgroup, workgroupForm);
        saveWorkgroupForm(workgroup);
    }


}

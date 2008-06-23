/**
 * $RCSfile$
 * $Revision: 27972 $
 * $Date: 2006-03-02 11:38:43 -0800 (Thu, 02 Mar 2006) $
 *
 * Copyright (C) 1999-2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.fastpath.dataforms;

import org.jivesoftware.xmpp.workgroup.DbProperties;
import org.jivesoftware.xmpp.workgroup.UnauthorizedException;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import com.thoughtworks.xstream.XStream;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class FormManager {
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
                ComponentManagerFactory.getComponentManager().getLog().error(e);
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
                Iterator iter = elem.getAnswers().iterator();
                while (iter.hasNext()) {
                    String item = (String)iter.next();
                    field.addOption(item, item);
                }
            }
            else if (elem.getAnswers().size() > 0) {
                // Add hidden element values.
                Iterator iter = elem.getAnswers().iterator();
                while (iter.hasNext()) {
                    String item = (String)iter.next();
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
            ComponentManagerFactory.getComponentManager().getLog().error(e);
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
                ComponentManagerFactory.getComponentManager().getLog().error(e);
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
                    ComponentManagerFactory.getComponentManager().getLog().error(e);
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

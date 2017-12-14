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

public class FormElement {
    private int position;
    private String label;
    private WorkgroupForm.FormEnum answerType;
    private List<String> answers = new ArrayList<String>();
    private boolean required;
    private boolean visible;
    private String variable;
    private String description;

    public FormElement() {

    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<String> getAnswers() {
        return answers;
    }

    public void setAnswers(List<String> answers) {
        this.answers = answers;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public WorkgroupForm.FormEnum getAnswerType() {
        return answerType;
    }

    public void setAnswerType(WorkgroupForm.FormEnum answerType) {
        this.answerType = answerType;
    }

    public void setAnswerType(String type) {
        for (WorkgroupForm.FormEnum formType : WorkgroupForm.FormEnum.values()) {
            if (formType.toString().equals(type)) {
                setAnswerType(formType);
            }
        }
    }

    public String getVariable(){
        return variable;
    }

    public void setVariable(String variable){
        this.variable = variable;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


}

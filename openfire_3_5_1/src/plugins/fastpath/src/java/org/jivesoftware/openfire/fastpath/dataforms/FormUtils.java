/**
 * $RCSfile$
 * $Revision: 19158 $
 * $Date: 2005-06-27 15:15:06 -0700 (Mon, 27 Jun 2005) $
 *
 * Copyright (C) 1999-2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.fastpath.dataforms;

import java.util.Iterator;
import java.util.List;

public class FormUtils {

    private FormUtils(){}

    public static String createAnswers(FormElement formElement){
        String name = formElement.getVariable();

        return createAnswers(name, formElement.getAnswerType(), formElement.getAnswers());
    }

    public static String createAnswers(FormElement formElement, String name){
        return createAnswers(name, formElement.getAnswerType(), formElement.getAnswers());
         
    }


    public static String createAnswers(String name, WorkgroupForm.FormEnum answerType, List items) {
        final StringBuilder builder = new StringBuilder();
        if (WorkgroupForm.FormEnum.textfield == answerType) {
            builder.append("<input type=\"text\" size=\"30\" name=\"").append(name).append("\">");
        }
        else if (WorkgroupForm.FormEnum.textarea == answerType) {
            builder.append("<textarea name=\"").append(name).append("\" cols=\"30\" rows=\"3\">");
            builder.append("</textarea>");
        }
        else if (WorkgroupForm.FormEnum.dropdown_box == answerType) {
            builder.append("<select name=\"").append(name).append("\">");
            if (items != null) {
                Iterator iterator = items.iterator();
                while (iterator.hasNext()) {
                    String item = (String)iterator.next();
                    builder.append("<option value=\"").append(item).append("\">").append(item)
                            .append("</option>");
                }
            }
            builder.append("</select>");
        }
        else if (WorkgroupForm.FormEnum.checkbox == answerType) {
            if(items == null){
                return null;
            }

            Iterator iter = items.iterator();
            int counter = 0;
            while(iter.hasNext()){
                String value = (String)iter.next();
                builder.append("<input type=\"checkbox\" value=\"").append(value)
                        .append("\" name=\"").append(name).append(counter).append("\">");
                builder.append("&nbsp;");
                builder.append(value);
                builder.append("<br/>");
                counter++;
            }
        }
        else if(WorkgroupForm.FormEnum.radio_button == answerType){
              if(items == null){
                return null;
            }

            Iterator iter = items.iterator();
            while(iter.hasNext()){
                String value = (String)iter.next();
                builder.append("<input type=\"radio\" value=\"").append(value).append("\" name=\"")
                        .append(name).append("\">");
                builder.append("&nbsp;");
                builder.append(value);
                builder.append("<br/>");
            }
        }

        return builder.toString();
    }
}
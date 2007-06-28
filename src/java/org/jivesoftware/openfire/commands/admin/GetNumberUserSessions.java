package org.jivesoftware.openfire.commands.admin;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import java.util.List;

/**
 * Command that allows to retrieve the number of user sessions at any one moment. That means
 * that the result will include all connected resources of all users.
 *
 * @author Gaston Dombiak
 */
public class GetNumberUserSessions extends AdHocCommand {

    protected void addStageInformation(SessionData data, Element command) {
        //Do nothing since there are no stages
    }

    public void execute(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.result);

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setLabel(getLabel());
        field.setVariable("onlineuserssessionsnum");
        SessionManager sessionManager = SessionManager.getInstance();
        field.addValue(sessionManager.getUserSessionsCount(false));

        command.add(form.getElement());
    }

    protected List<Action> getActions(SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

    public String getCode() {
        return "http://jabber.org/protocol/admin#get-sessions-num";
    }

    public String getDefaultLabel() {
        // TODO Use i18n
        return "Number of Connected User Sessions";
    }

    protected Action getExecuteAction(SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

    public int getMaxStages(SessionData data) {
        return 0;
    }
}

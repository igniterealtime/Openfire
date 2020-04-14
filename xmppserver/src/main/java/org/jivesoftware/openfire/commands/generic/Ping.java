/*
 * Copyright (C) 2020 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.commands.generic;

import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Date;
import java.util.List;

/**
 * A (very) basic ad-hoc command that returns the server time in UTC. The ad-hoc command implemented in this class
 * differs from most others in the fact that it has no restrictions in what entity is allowed to execute/use it.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class Ping extends AdHocCommand
{
    @Override
    public String getCode()
    {
        return "ping";
    }

    @Override
    public String getDefaultLabel()
    {
        return "Request pong from server";
    }

    @Override
    public int getMaxStages( final SessionData data )
    {
        return 0;
    }

    @Override
    public void execute( final SessionData data, final Element command )
    {
        final DataForm form = new DataForm(DataForm.Type.result);
        form.setTitle("Server ping result (pong!)");

        final FormField field = form.addField();
        field.setLabel("Server Time");
        field.setVariable("timestamp");
        field.addValue( XMPPDateTimeFormat.format(new Date()) );

        command.add(form.getElement());
    }

    @Override
    public boolean hasPermission( JID requester ) {
        return true; // Everyone is allowed to use this command.
    }

    @Override
    protected void addStageInformation( final SessionData data, final Element command )
    {
        // Do nothing since there are no stages.
    }

    @Override
    protected List<Action> getActions( final SessionData data )
    {
        // Do nothing since there are no stages.
        return null;
    }

    @Override
    protected Action getExecuteAction( final SessionData data )
    {
        // Do nothing since there are no stages.
        return null;
    }
}

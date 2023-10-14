/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.server;

import org.dom4j.*;
import org.xmpp.packet.PacketError;

/**
 * Representation of an error result of the Server Dialback authentication mechanism.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class ServerDialbackErrorException extends Exception
{
    private final String from;
    private final String to;
    private final PacketError error;

    public ServerDialbackErrorException(String from, String to, PacketError error)
    {
        super();
        this.from = from;
        this.to = to;
        this.error = error;
    }

    public ServerDialbackErrorException(String from, String to, PacketError error, Throwable e)
    {
        super(e);
        this.from = from;
        this.to = to;
        this.error = error;
    }

    public String getFrom()
    {
        return from;
    }

    public String getTo()
    {
        return to;
    }

    public PacketError getError()
    {
        return error;
    }

    public Element toXML()
    {
        final Namespace ns = Namespace.get("db", "jabber:server:dialback");
        final Document outbound = DocumentHelper.createDocument();
        final Element root = outbound.addElement("root");
        root.add(ns);
        final Element result = root.addElement(QName.get("result", ns));
        result.addAttribute("from", from);
        result.addAttribute("to", to);
        result.addAttribute("type", "error");
        result.add(error.getElement());

        return result;
    }
}

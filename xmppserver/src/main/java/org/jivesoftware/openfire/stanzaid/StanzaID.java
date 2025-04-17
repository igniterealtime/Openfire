/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.stanzaid;

import org.dom4j.Element;
import org.dom4j.QName;
import org.xmpp.packet.JID;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class StanzaID
{
    public static final String ELEMENT_NAME = "stanza-id";
    public static final String NAMESPACE = "urn:xmpp:sid:0";

    private final String id;
    private final JID by;

    public static List<StanzaID> allFromChildren(final Element parentElement)
    {
        final List<StanzaID> result = new ArrayList<>();
        final List<Element> sids = parentElement.elements( QName.get(ELEMENT_NAME, NAMESPACE) );

        if (sids != null) {
            for (final Element sid : sids) {
                result.add(parse(sid));
            }
        }

        return result;
    }

    public static StanzaID parse(final Element stanzaIDElement)
    {
        return new StanzaID(stanzaIDElement.attributeValue("id"), new JID(stanzaIDElement.attributeValue("by")));
    }

    public static StanzaID generate(final JID by) {
        return new StanzaID(UUID.randomUUID().toString(), by);
    }

    public StanzaID(final String id, final JID by)
    {
        this.id = id;
        this.by = by;
    }

    public String getId()
    {
        return id;
    }

    public JID getBy()
    {
        return by;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StanzaID stanzaID = (StanzaID) o;
        return Objects.equals(id, stanzaID.id) && Objects.equals(by, stanzaID.by);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, by);
    }

    public void addAsChildElementTo(final Element parent) {
        final Element stanzaIdElement = parent.addElement(QName.get( ELEMENT_NAME, NAMESPACE ) );
        stanzaIdElement.addAttribute( "id", id );
        stanzaIdElement.addAttribute( "by", by.toString());
    }

    @Override
    public String toString()
    {
        return "StanzaID{" +
            "id='" + id + '\'' +
            ", by=" + by +
            '}';
    }
}

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
package org.jivesoftware.openfire.spamreporting;

import org.dom4j.*;
import org.jivesoftware.openfire.stanzaid.StanzaID;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.*;

/**
 * Representation of a report of spam.
 *
 * @author Guus der Kinderen, guus.der.kinderen@mgail.com
 * @see <a href="https://xmpp.org/extensions/xep-0377.html">XEP-0377: Spam Reporting</a>
 */
public class SpamReport
{
    public static final String ELEMENT_NAME = "report";
    public static final String NAMESPACE = "urn:xmpp:reporting:1";

    private final Instant timestamp;

    private final JID reportingAddress;

    private final JID reportedAddress;

    private final Element reportElement;

    private transient String reason;

    private transient Set<Text> context;

    private transient Set<StanzaID> stanzaIDs;

    public static List<SpamReport> allFromChildren(final Instant timestamp, final JID reportingAddress, final JID reportedAddress, final Element parentElement)
    {
        final List<SpamReport> result = new ArrayList<>();
        final List<Element> reports = parentElement.elements( QName.get(ELEMENT_NAME, NAMESPACE) );

        if (reports != null) {
            for (final Element report : reports) {
                result.add(new SpamReport(timestamp, reportingAddress, reportedAddress, report));
            }
        }

        return result;
    }

    public SpamReport(final Instant timestamp, final JID reportingAddress, final JID reportedAddress, final Element reportElement)
    {
        this.timestamp = timestamp;
        this.reportingAddress = reportingAddress;
        this.reportedAddress = reportedAddress;
        this.reportElement = (Element) reportElement.createCopy().detach();
    }

    public Instant getTimestamp()
    {
        return timestamp;
    }

    public JID getReportingAddress()
    {
        return reportingAddress;
    }

    public JID getReportedAddress()
    {
        return reportedAddress;
    }

    public Element getReportElement()
    {
        return (Element) reportElement.createCopy().detach();
    }

    public synchronized String getReason()
    {
        if (reason == null) {
            reason = reportElement.attributeValue("reason");
        }
        return reason;
    }

    public synchronized Set<Text> getContext()
    {
        if (context == null) {
            context = new HashSet<>(Text.allFromChildren(reportElement));
        }
        return context;
    }

    public synchronized Set<StanzaID> getStanzaIDs()
    {
        if (stanzaIDs == null) {
            stanzaIDs = new HashSet<>(StanzaID.allFromChildren(reportElement));
        }
        return stanzaIDs;
    }

    @Override
    public String toString()
    {
        return "SpamReport{" +
            "timestamp=" + timestamp +
            ", reportingAddress=" + reportingAddress +
            ", reportedAddress=" + reportedAddress +
            '}';
    }

    public static class Text
    {
        @Nonnull
        private final String value;

        @Nullable
        private final String lang;

        public static List<Text> allFromChildren(final Element parentElement)
        {
            final List<Text> result = new ArrayList<>();
            final List<Element> texts = parentElement.elements( "text" );

            if (texts != null) {
                for (final Element text : texts) {
                    result.add(parse(text));
                }
            }

            return result;
        }

        public static Text parse(final Element textElement)
        {
            return new Text(textElement.getText(), textElement.attributeValue("xml:lang"));
        }

        public Text(@Nonnull final String value, @Nullable final String lang)
        {
            this.value = value;
            this.lang = lang == null ? null : (lang.isEmpty() ? null : lang);
        }

        @Nonnull
        public String getValue()
        {
            return value;
        }

        @Nullable
        public String getLang()
        {
            return lang;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Text text = (Text) o;
            return Objects.equals(value, text.value) && Objects.equals(lang, text.lang);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(value, lang);
        }

        @Override
        public String toString()
        {
            return "Text{" +
                "lang='" + lang + '\'' +
                ", value='" + value + '\'' +
                '}';
        }
    }
}

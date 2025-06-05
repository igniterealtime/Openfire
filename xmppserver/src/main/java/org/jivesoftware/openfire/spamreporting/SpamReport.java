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
import org.jivesoftware.database.JiveID;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.stanzaid.StanzaID;
import org.jivesoftware.util.JiveConstants;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.*;

/**
 * Representation of a report of spam.
 *
 * Note that this is <em>not</em> an exact representation of a XEP-0377-defined spam report. That definition is specific
 * to IQ-Block, while the definition here is intended to be re-usable for other purposes.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
@JiveID(JiveConstants.SPAM_REPORT_ID)
public class SpamReport
{
    private final long id;

    private final Instant timestamp;

    private final JID reportingAddress;

    private final JID reportedAddress;

    private final String reason;

    private final boolean allowedToReportToOriginDomain;

    private final boolean allowedToSendToThirdParties;

    private final Set<Text> context;

    private final Map<StanzaID, Optional<Packet>> reportedStanzas;

    public static SpamReport generate(final Instant timestamp, final JID reportingAddress, final JID reportedAddress, final String reason, final boolean reportOrigin, final boolean thirdParty, final Set<Text> context, final Set<StanzaID> reportedStanzaIDs)
    {
        final Map<StanzaID, Optional<Packet>> reportedStanzas = new HashMap<>();
        for (final StanzaID stanzaID : reportedStanzaIDs) {
            // TODO query MAM for offending stanzas based on any provided stanza-id. Be careful to only include stanzas that are owned by the reporter, to prevent abuse.
            reportedStanzas.put(stanzaID, Optional.empty());
        }

        return new SpamReport(timestamp, reportingAddress, reportedAddress, reason, reportOrigin, thirdParty, context, reportedStanzas);
    }

    public SpamReport(final Instant timestamp, final JID reportingAddress, final JID reportedAddress, final String reason, final boolean allowedToReportToOriginDomain, final boolean allowedToSendToThirdParties, final Set<Text> context, final Map<StanzaID, Optional<Packet>> reportedStanzas)
    {
        this.id = SequenceManager.nextID(this);
        this.timestamp = timestamp;
        this.reportingAddress = reportingAddress;
        this.reportedAddress = reportedAddress;
        this.reason = reason;
        this.allowedToReportToOriginDomain = allowedToReportToOriginDomain;
        this.allowedToSendToThirdParties = allowedToSendToThirdParties;
        this.context = context;
        this.reportedStanzas = reportedStanzas;
    }

    public SpamReport(final long id, final Instant timestamp, final JID reportingAddress, final JID reportedAddress, final String reason, final boolean allowedToReportToOriginDomain, final boolean allowedToSendToThirdParties, final Set<Text> context, final Map<StanzaID, Optional<Packet>> reportedStanzas)
    {
        this.id = id;
        this.timestamp = timestamp;
        this.reportingAddress = reportingAddress;
        this.reportedAddress = reportedAddress;
        this.reason = reason;
        this.allowedToReportToOriginDomain = allowedToReportToOriginDomain;
        this.allowedToSendToThirdParties = allowedToSendToThirdParties;
        this.context = context;
        this.reportedStanzas = reportedStanzas;
    }

    public long getId()
    {
        return id;
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

    public String getReason()
    {
        return reason;
    }

    public boolean isAllowedToReportToOriginDomain()
    {
        return allowedToReportToOriginDomain;
    }

    public boolean isAllowedToSendToThirdParties()
    {
        return allowedToSendToThirdParties;
    }

    public Set<Text> getContext()
    {
        return context;
    }

    public Map<StanzaID, Optional<Packet>> getReportedStanzas()
    {
        return reportedStanzas;
    }

    @Override
    public String toString()
    {
        return "SpamReport{" +
            "id=" + id +
            ", timestamp=" + timestamp +
            ", reportingAddress=" + reportingAddress +
            ", reportedAddress=" + reportedAddress +
            ", reportedStanzaCount=" + reportedStanzas.size() +
            '}';
    }

    public static class Text
    {
        @Nonnull
        private final String value;

        @Nullable
        private final String lang;

        public static Set<Text> allFromChildren(final Element parentElement)
        {
            final Set<Text> result = new HashSet<>();
            final Collection<Element> texts = parentElement.elements( "text" );

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

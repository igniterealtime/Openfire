/*
 * Copyright (C) 2022 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.net;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.filter.ssl.SslFilter;

/**
 * A MINA filter that facilitates StartTLS negotiation over XMPP.
 *
 * When StartTLS is being negotiated, an instance of {@link SslFilter} is added to the MINA filter-chain. However, the
 * peer also needs to be informed that TLS negotiation can commence, as per section 5.4.2.3 of RFC-6120.
 *
 * To prevent race conditions, the SSL filter should be set before the peer is told to proceed. However, telling the
 * peer to proceed with TLS negotiation needs to happen in plain text, which won't happen if an SSL filter has been set.
 *
 * As a work-around to this chicken/egg problem, this filter evaluates the data that is sent to the peer prior to it
 * being passed to the SSL filter. If the data exactly matches the XMPP 'proceed' element (used to instruct the peer to
 * begin TLS negotiation), that data skips the SSL filter.
 *
 * Instances of this filter are best removed from the filter-chain when TLS has been negotiated, to prevent it from
 * evaluating all data that is being sent (which would waste system resources).
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see <a href="https://xmpp.org/rfcs/rfc6120.html#tls-process-initiate-proceed">Section 5.4.2.3 of RFC-6120</a>
 */
public class StartTlsFilter extends IoFilterAdapter
{
    @Override
    public void filterWrite(final NextFilter nextFilter, final IoSession session, final WriteRequest writeRequest)
    {
        final boolean isStartTlsProceed = writeRequest.getOriginalMessage() instanceof IoBuffer &&
            ((IoBuffer) writeRequest.getOriginalMessage()).hasArray() &&
            new String(((IoBuffer) writeRequest.getOriginalMessage()).array()).trim().equals("<proceed xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>");

        if (isStartTlsProceed)
        {
            // This writeRequest is an instruction to the peer for it to start TLS negotiation. This should be sent as
            // non-encrypted / plain text data. Skip the SSL filter.
            final IoFilterChain chain = session.getFilterChain();

            for (final IoFilterChain.Entry entry : chain.getAll()) {
                IoFilter filter = entry.getFilter();

                if (filter instanceof SslFilter) {
                    entry.getNextFilter().filterWrite(session, writeRequest);
                }
            }
        } else {
            nextFilter.filterWrite(session, writeRequest);
        }
    }
}

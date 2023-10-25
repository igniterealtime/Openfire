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
package org.jivesoftware.openfire.net;

import org.dom4j.Namespace;
import org.jivesoftware.openfire.Connection;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * A partial implementation of the {@link org.jivesoftware.openfire.Connection} interface, implementing functionality
 * that's commonly shared by Connection implementations.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public abstract class AbstractConnection implements Connection
{
    /**
     * The major version of XMPP being used by this connection (major_version.minor_version). In most cases, the version
     * should be "1.0". However, older clients using the "Jabber" protocol do not set a version. In that case, the
     * version is "0.0".
     */
    private int majorVersion = 1;

    /**
     * The minor version of XMPP being used by this connection (major_version.minor_version). In most cases, the version
     * should be "1.0". However, older clients using the "Jabber" protocol do not set a version. In that case, the
     * version is "0.0".
     */
    private int minorVersion = 0;

    /**
     * When a connection is used to transmit an XML data, the root element of that data can define XML namespaces other
     * than the ones that are default (eg: 'jabber:client', 'jabber:server', etc). For an XML parser to be able to parse
     * stanzas or other elements that are defined in that namespace (eg: are prefixed), these namespaces are recorded
     * here.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2556">Issue OF-2556</a>
     */
    private final Set<Namespace> additionalNamespaces = new HashSet<>();

    @Override
    public int getMajorXMPPVersion() {
        return majorVersion;
    }

    @Override
    public int getMinorXMPPVersion() {
        return minorVersion;
    }

    @Override
    public void setXMPPVersion(int majorVersion, int minorVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    @Override
    @Nonnull
    public Set<Namespace> getAdditionalNamespaces() {
        return additionalNamespaces;
    }

    @Override
    public void setAdditionalNamespaces(@Nonnull final Set<Namespace> additionalNamespaces) {
        this.additionalNamespaces.clear();
        this.additionalNamespaces.addAll(additionalNamespaces);
    }
}

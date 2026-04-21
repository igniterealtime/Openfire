/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.util.channelbinding;

/**
 * Enumerates supported channel binding types.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc5705">RFC 5705: Keying Material Exporters for Transport Layer Security (TLS)</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc5929">RFC 5929: Channel Bindings for TLS</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9266">RFC 9266: Channel Bindings for TLS 1.3</a>
 */
public enum ChannelBindingType
{
    /**
     * tls-exporter: TLS exporter-based channel binding (RFC 5705, RFC 9266).
     */
    TLS_EXPORTER("tls-exporter"),

    /**
     * tls-server-end-point: server certificate hash channel binding (RFC 5929).
     */
    TLS_SERVER_END_POINT("tls-server-end-point"),

    /**
     * tls-unique: TLS Finished message channel binding (RFC 5929, deprecated).
     */
    TLS_UNIQUE("tls-unique");

    /**
     * RFC-defined Channel-binding unique prefix
     */
    private final String prefix;

    ChannelBindingType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix()
    {
        return prefix;
    }
}

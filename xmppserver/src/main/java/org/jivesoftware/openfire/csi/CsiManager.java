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
package org.jivesoftware.openfire.csi;

import org.dom4j.Element;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * Handles Client State Indication nonzas for one particular client session.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0352.html">XEP-0352: Client State Indication</a>
 */
public class CsiManager
{
    public static final Logger Log = LoggerFactory.getLogger(CsiManager.class);

    /**
     * Controls if Client State Indication functionality is made available to clients.
     */
    public static SystemProperty<Boolean> ENABLED = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("xmpp.client.csi.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    public static final String NAMESPACE = "urn:xmpp:csi:0";

    /**
     * The client session for which this instance manages CSI state.
     */
    private final LocalClientSession session;

    /**
     * Client state of {@link #session}, either 'true' for 'active', or 'false' for 'inactive'
     */
    private boolean active;

    public CsiManager(@Nonnull final LocalClientSession session)
    {
        this.session = session;
        this.active = true;
    }

    /**
     * Processes a CSI nonza.
     *
     * @param nonza The CSI nonza to be processed.
     */
    public synchronized void process(@Nonnull final Element nonza)
    {
        switch(nonza.getName()) {
            case "active":
                activate();
                break;
            case "inactive":
                deactivate();
            default:
                Log.warn("Unable to process element that was expected to be a CSI nonza for {}: {}", session, nonza);
        }
    }

    /**
     * Switch to the client state of 'active'.
     */
    public void activate()
    {
        Log.trace("Session for '{}' to CSI 'active'", session.getAddress());
        active = true;
    }

    /**
     * Switch to the client state of 'inactive'.
     */
    public void deactivate()
    {
        Log.trace("Session for '{}' to CSI 'inactive'", session.getAddress());
        active = false;
    }

    /**
     * Returns the client state for the session that is being tracked by this instance, either 'true' for 'active',
     * or 'false' for 'inactive'
     *
     * @return a client state indication
     */
    public boolean isActive()
    {
        return active;
    }

    /**
     * Checks if an XML fragment is recognized as a CSI nonza
     *
     * @param fragment the XML to evaluate
     * @return true if the XML is recognized as a CSI nonza, otherwise false.
     */
    public static boolean isStreamManagementNonza(@Nullable final Element fragment) {
        return fragment != null
            && NAMESPACE.equals(fragment.getNamespaceURI())
            && Set.of("active", "inactive").contains(fragment.getName());
    }
}

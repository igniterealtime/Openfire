/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway;

import org.xmpp.packet.JID;
import org.jivesoftware.wildfire.user.UserNotFoundException;

import java.util.Collection;

/**
 * Handles retrieving pseudo rosters and other related tasks.
 *
 * @author Daniel Henninger
 */
public class PseudoRosterManager {

    /**
     * Manages registration information.
     * @see org.jivesoftware.wildfire.gateway.RegistrationManager
     */
    public final RegistrationManager registrationManager = new RegistrationManager();

    /**
     * Retrieves a pseudo roster based off of a registration ID.
     *
     * @param registrationID To retrieve the roster for.
     * @return A Pseudo roster
     */
    public PseudoRoster getPseudoRoster(Long registrationID) {
        return new PseudoRoster(registrationID);
    }

    /**
     * Retrieves a pseudo roster based off of a registration.
     *
     * @param registration To retrieve the roster for.
     * @return A Pseudo roster
     */
    public PseudoRoster getPseudoRoster(Registration registration) {
        return getPseudoRoster(registration.getRegistrationID());
    }

    /**
     * Retrieves a pseudo roster based off of a registration.
     *
     * @param jid To retrieve the roster for.
     * @param type TransportType the roster is for.
     * @return A Pseudo roster
     */
    public PseudoRoster getPseudoRoster(JID jid, TransportType type) throws UserNotFoundException {
        Collection<Registration> registrations = registrationManager.getRegistrations(jid, type);
        if (registrations.isEmpty()) {
            // User is not registered with us.
            throw new UserNotFoundException("Unable to find registration.");
        }
        Registration registration = registrations.iterator().next();
        return getPseudoRoster(registration);
    }

}

/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2002 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.user;

import org.jivesoftware.messenger.IQ;
import org.dom4j.Document;

/**
 * <p>An IQ packet representing an XMPP roster.</p>
 * <p>Rosters are just standard iq with a 'query' sub-element
 * containing zero or more RosterItem 'item' fragments.</p>
 *
 * @author Iain Shigeoka
 */
public interface IQRoster extends IQ, Roster {

    /**
     * <p>Parse the given XML document for Roster content.</p>
     *
     * @param doc The document to parse
     */
    public void parse(Document doc);
}
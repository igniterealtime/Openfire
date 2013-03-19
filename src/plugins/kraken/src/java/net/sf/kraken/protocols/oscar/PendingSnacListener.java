/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.oscar;

import java.util.List;

import net.kano.joscar.snac.SnacRequest;

/**
 * Handles events from pending SNAC manager.
 * 
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public interface PendingSnacListener {
    void dequeueSnacs(List<SnacRequest> pending);
}

/**
 * $Revision: 19161 $
 * $Date: 2005-06-27 16:23:31 -0700 (Mon, 27 Jun 2005) $
 *
 * Copyright (C) 2004-2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.disco;

import java.util.Collection;

/**
 * DiscoFeaturesProvider are responsible for answering features offered by the workgroup service.
 * Whenever a disco request is received IQDiscoInfoHandler will ask all the registered
 * DiscoFeaturesProvider for their features. Each DiscoFeaturesProvider may decide to return
 * a given feature based on its state.<p>
 *
 * The email service is a good example of a supported feature that may or may not be available
 * depending on whether it was properly configured or not.
 *
 * @author Gaston Dombiak
 */
public interface DiscoFeaturesProvider {

    /**
     * Returns a collection of Strings with the supported features. The list may vary in time
     * since different features may be related to valid configurations or users activating or
     * deactivating a given feature.
     *
     * @return a collection of Strings with the supported features.
     */
    public abstract Collection<String> getFeatures();
}

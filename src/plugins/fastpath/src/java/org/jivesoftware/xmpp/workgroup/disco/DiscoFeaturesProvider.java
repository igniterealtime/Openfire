/**
 * $Revision: 19161 $
 * $Date: 2005-06-27 16:23:31 -0700 (Mon, 27 Jun 2005) $
 *
 * Copyright (C) 2004-2006 Jive Software. All rights reserved.
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

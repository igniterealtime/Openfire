/*
 * Copyright (C) 2018 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.disco;

import java.util.Iterator;

/**
 * A <code>UserFeatureProvider</code> is responsible for providing the features
 * of protocols supported by users.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public interface UserFeaturesProvider
{
    /**
     * Returns an Iterator (of String) with the namespace of supported features
     * by users. The features to include are the features of protocols supported
     * by all registered users on the server. The idea is that different modules
     * may provide their features that will ultimately be included  in the list
     * user features.
     *
     * @return an Iterator (of String) with features of protocols supported by users.
     */
    Iterator<String> getFeatures();
}

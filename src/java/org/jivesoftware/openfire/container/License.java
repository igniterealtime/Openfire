/*
 * Copyright 2016 IgniteRealtime.org
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

package org.jivesoftware.openfire.container;

/**
 * An enumeration for license agreement types.
 */
public enum License
{
    /**
     * Distributed using a commercial license.
     */
    commercial,

    /**
     * Distributed using the GNU Public License (GPL).
     */
    gpl,

    /**
     * Distributed using the Apache license.
     */
    apache,

    /**
     * For internal use at an organization only and is not re-distributed.
     */
    internal,

    /**
     * Distributed under another license agreement not covered by one of the other choices. The license agreement
     * should be detailed in a Readme or License file that accompanies the code.
     */
    other
}

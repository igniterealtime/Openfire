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
package org.jivesoftware.openfire.muc;

import org.xmpp.forms.DataForm;

import java.util.Collection;
import java.util.Set;

/**
 * Allows plugins to contribute additional fields to MUC room configuration forms and room disco#info.
 */
public interface MUCRoomConfigExtension {

    /**
     * Adds extension-specific fields to the room owner configuration form.
     *
     * @param form the configuration form being built.
     * @param room the room being configured.
     */
    default void contributeConfigForm(DataForm form, MUCRoom room) {
    }

    /**
     * Populates extension-specific fields in the configuration form with current room values.
     *
     * @param form the configuration form being populated.
     * @param room the room being configured.
     */
    default void populateConfigForm(DataForm form, MUCRoom room) {
    }

    /**
     * Processes extension-specific fields from a submitted configuration form.
     *
     * @param completedForm the submitted configuration form.
     * @param room the room being configured.
     */
    default void processConfigSubmit(DataForm completedForm, MUCRoom room) {
    }

    /**
     * Contributes extension-specific disco#info features for a room. Invoked while building the room's
     * disco#info feature list.
     *
     * @param features mutable collection of disco features to augment.
     * @param room the room being described.
     */
    default void contributeRoomDiscoFeatures(Collection<String> features, MUCRoom room) {
    }

    /**
     * Contributes extension-specific disco#info extended data forms for a room. Invoked while building the
     * room's extended disco#info (XEP-0128) forms; the set already contains the room's {@code muc#roominfo}
     * form, which an extension may augment or supplement with additional forms.
     *
     * @param forms mutable set of extended data forms to augment.
     * @param room the room being described.
     */
    default void contributeRoomDiscoForms(Set<DataForm> forms, MUCRoom room) {
    }
}

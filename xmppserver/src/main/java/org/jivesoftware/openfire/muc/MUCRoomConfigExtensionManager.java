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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Registry for {@link MUCRoomConfigExtension} implementations contributed by plugins.
 */
public final class MUCRoomConfigExtensionManager {

    private static final Logger Log = LoggerFactory.getLogger(MUCRoomConfigExtensionManager.class);

    private static final MUCRoomConfigExtensionManager INSTANCE = new MUCRoomConfigExtensionManager();

    private final CopyOnWriteArrayList<MUCRoomConfigExtension> extensions = new CopyOnWriteArrayList<>();

    private MUCRoomConfigExtensionManager() {
    }

    public static MUCRoomConfigExtensionManager getInstance() {
        return INSTANCE;
    }

    public void register(MUCRoomConfigExtension extension) {
        if (extension != null) {
            extensions.addIfAbsent(extension);
        }
    }

    public void unregister(MUCRoomConfigExtension extension) {
        extensions.remove(extension);
    }

    public void contributeConfigForm(DataForm form, MUCRoom room) {
        forEachExtension("contributing to the config form", room, extension -> extension.contributeConfigForm(form, room));
    }

    public void populateConfigForm(DataForm form, MUCRoom room) {
        forEachExtension("populating the config form", room, extension -> extension.populateConfigForm(form, room));
    }

    public void processConfigSubmit(DataForm completedForm, MUCRoom room) {
        forEachExtension("processing the config submission", room, extension -> extension.processConfigSubmit(completedForm, room));
    }

    public void contributeRoomDiscoFeatures(Collection<String> features, MUCRoom room) {
        forEachExtension("contributing disco#info features", room, extension -> extension.contributeRoomDiscoFeatures(features, room));
    }

    public void contributeRoomDiscoForms(Set<DataForm> forms, MUCRoom room) {
        forEachExtension("contributing disco#info forms", room, extension -> extension.contributeRoomDiscoForms(forms, room));
    }

    /**
     * Applies an operation to every registered extension, isolating failures: an exception thrown by one
     * (third-party) extension is logged and does not prevent the remaining extensions from running, nor
     * does it break the room configuration / disco handling that invoked this manager.
     */
    private void forEachExtension(final String action, final MUCRoom room, final Consumer<MUCRoomConfigExtension> operation) {
        for (final MUCRoomConfigExtension extension : extensions) {
            try {
                operation.accept(extension);
            } catch (final RuntimeException e) {
                Log.warn("MUC room config extension {} threw an exception while {} for room {}",
                    extension, action, room == null ? null : room.getName(), e);
            }
        }
    }
}

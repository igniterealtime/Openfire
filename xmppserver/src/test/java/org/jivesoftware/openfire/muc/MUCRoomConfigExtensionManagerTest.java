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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MUCRoomConfigExtensionManagerTest {

    private DataForm processedForm;
    private MUCRoom processedRoom;

    @AfterEach
    void cleanup() {
        MUCRoomConfigExtensionManager.getInstance().unregister(testExtension);
        MUCRoomConfigExtensionManager.getInstance().unregister(throwingExtension);
    }

    private final MUCRoomConfigExtension throwingExtension = new MUCRoomConfigExtension() {
        @Override
        public void contributeConfigForm(final DataForm form, final MUCRoom room) {
            throw new RuntimeException("boom");
        }
    };

    private final MUCRoomConfigExtension testExtension = new MUCRoomConfigExtension() {
        @Override
        public void contributeConfigForm(final DataForm form, final MUCRoom room) {
            form.addField("test#field", "Test", FormField.Type.text_single);
        }

        @Override
        public void populateConfigForm(final DataForm form, final MUCRoom room) {
            form.getField("test#field").addValue("value");
        }

        @Override
        public void processConfigSubmit(final DataForm completedForm, final MUCRoom room) {
            processedForm = completedForm;
            processedRoom = room;
        }

        @Override
        public void contributeRoomDiscoFeatures(final java.util.Collection<String> features, final MUCRoom room) {
            features.add("urn:xmpp:test:0");
        }

        @Override
        public void contributeRoomDiscoForms(final java.util.Set<DataForm> forms, final MUCRoom room) {
            final DataForm form = new DataForm(DataForm.Type.result);
            form.addField("FORM_TYPE", null, FormField.Type.hidden).addValue("urn:xmpp:test:0");
            forms.add(form);
        }
    };

    @Test
    void registeredExtensionContributesConfigForm() {
        MUCRoomConfigExtensionManager.getInstance().register(testExtension);
        final DataForm form = new DataForm(DataForm.Type.form);
        final MUCRoom room = mock(MUCRoom.class);
        MUCRoomConfigExtensionManager.getInstance().contributeConfigForm(form, room);
        assertNotNull(form.getField("test#field"));

        MUCRoomConfigExtensionManager.getInstance().populateConfigForm(form, room);
        assertEquals("value", form.getField("test#field").getFirstValue());

        MUCRoomConfigExtensionManager.getInstance().processConfigSubmit(form, room);
        assertSame(form, processedForm);
        assertSame(room, processedRoom);
    }

    @Test
    void registeredExtensionContributesDisco() {
        MUCRoomConfigExtensionManager.getInstance().register(testExtension);
        final var room = mock(MUCRoom.class);
        final var features = new HashSet<String>();
        final var forms = new HashSet<DataForm>();
        MUCRoomConfigExtensionManager.getInstance().contributeRoomDiscoFeatures(features, room);
        MUCRoomConfigExtensionManager.getInstance().contributeRoomDiscoForms(forms, room);
        assertTrue(features.contains("urn:xmpp:test:0"));
        // Assert the specific form this extension contributed, rather than mere non-emptiness, so the test
        // does not depend on what else may be registered on the process-wide singleton.
        assertTrue(forms.stream().anyMatch(f -> {
            final FormField formType = f.getField("FORM_TYPE");
            return formType != null && "urn:xmpp:test:0".equals(formType.getFirstValue());
        }));
    }

    @Test
    void throwingExtensionIsIsolated() {
        // A throwing extension must neither propagate nor prevent other extensions from running.
        MUCRoomConfigExtensionManager.getInstance().register(throwingExtension);
        MUCRoomConfigExtensionManager.getInstance().register(testExtension);
        final DataForm form = new DataForm(DataForm.Type.form);
        final MUCRoom room = mock(MUCRoom.class);
        assertDoesNotThrow(() -> MUCRoomConfigExtensionManager.getInstance().contributeConfigForm(form, room));
        assertNotNull(form.getField("test#field"));
    }
}

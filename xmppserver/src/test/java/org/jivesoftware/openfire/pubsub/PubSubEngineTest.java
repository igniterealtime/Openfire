/*
 * Copyright (C) 2025-2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.pubsub;

import org.dom4j.Element;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies compliance-relevant behavior of {@link PubSubEngine} against XEP-0060.
 */
@ExtendWith(MockitoExtension.class)
public class PubSubEngineTest
{
    /**
     * XEP-0060 §8.5.3.1: Purging a collection node MUST return a feature-not-implemented
     * error with the 'purge-nodes' unsupported feature, not a ClassCastException.
     *
     * Verifies the fix for a bug where a ClassCastException was thrown when attempting to
     * purge a collection node, because the node was cast to LeafNode before checking
     * whether it was a collection node.
     */
    @Test
    public void testPurgeCollectionNodeReturnsFeatureNotImplemented() throws Exception
    {
        // Setup fixture.
        final PacketRouter mockRouter = Mockito.mock(PacketRouter.class);
        final PubSubEngine engine = new PubSubEngine(mockRouter);

        final PubSubService mockService = Mockito.mock(PubSubService.class);

        final Node mockNode = Mockito.mock(Node.class);
        Mockito.when(mockNode.isCollectionNode()).thenReturn(true);
        Mockito.when(mockNode.isAdmin(Mockito.any(JID.class))).thenReturn(true);
        Mockito.when(mockService.getNode(Mockito.anyString())).thenReturn(mockNode);

        final JID ownerJID = new JID("owner", "example.org", null);
        final IQ iq = new IQ(IQ.Type.set);
        iq.setFrom(ownerJID);
        final Element pubsubElement = iq.setChildElement("pubsub", "http://jabber.org/protocol/pubsub#owner");
        final Element purgeElement = pubsubElement.addElement("purge");
        purgeElement.addAttribute("node", "test-node");

        // Execute system under test.
        engine.process(mockService, iq);

        // Verify result: error response with feature-not-implemented and 'purge-nodes'.
        final ArgumentCaptor<IQ> packetCaptor = ArgumentCaptor.forClass(IQ.class);
        Mockito.verify(mockRouter).route(packetCaptor.capture());
        final IQ response = packetCaptor.getValue();
        assertEquals(IQ.Type.error, response.getType());
        assertEquals(PacketError.Condition.feature_not_implemented, response.getError().getCondition());

        final Element pubsubError = response.getError().getElement()
                .element("unsupported");
        assertNotNull(pubsubError, "Expected a pubsub 'unsupported' error element");
        assertEquals("purge-nodes", pubsubError.attributeValue("feature"),
                "Expected 'purge-nodes' as the unsupported feature");
    }

    /**
     * XEP-0060 §7.1.3.5: If the payload size exceeds a service-defined maximum, the service
     * MUST return a &lt;not-acceptable/&gt; error with a pubsub-specific condition of
     * &lt;payload-too-big/&gt;.
     */
    @Test
    public void testPublishOversizedPayloadReturnsPayloadTooBig() throws Exception
    {
        // Setup fixture.
        final PacketRouter mockRouter = Mockito.mock(PacketRouter.class);
        final PubSubEngine engine = new PubSubEngine(mockRouter);

        final PubSubService mockService = Mockito.mock(PubSubService.class);

        // Create a LeafNode mock configured with a very small max payload size (10 bytes).
        final LeafNode mockLeafNode = Mockito.mock(LeafNode.class);
        Mockito.when(mockLeafNode.isCollectionNode()).thenReturn(false);
        Mockito.when(mockLeafNode.getPublisherModel()).thenReturn(PublisherModel.open);
        Mockito.when(mockLeafNode.isItemRequired()).thenReturn(true);
        Mockito.when(mockLeafNode.getMaxPayloadSize()).thenReturn(10);
        Mockito.when(mockService.getNode(Mockito.anyString())).thenReturn(mockLeafNode);

        final JID publisherJID = new JID("publisher", "example.org", "res");
        final IQ iq = new IQ(IQ.Type.set);
        iq.setFrom(publisherJID);
        final Element pubsubElement = iq.setChildElement("pubsub", "http://jabber.org/protocol/pubsub");
        final Element publishElement = pubsubElement.addElement("publish");
        publishElement.addAttribute("node", "test-node");
        final Element itemElement = publishElement.addElement("item");
        // Build a payload that is clearly larger than the configured maximum of 10 bytes.
        final Element payload = itemElement.addElement("payload");
        payload.setText("this-text-is-definitely-longer-than-10-bytes");

        // Execute system under test. The publish action is submitted asynchronously; wait for it.
        engine.process(mockService, iq).get();

        // Verify result: error response with not-acceptable and payload-too-big.
        final ArgumentCaptor<IQ> packetCaptor = ArgumentCaptor.forClass(IQ.class);
        Mockito.verify(mockRouter).route(packetCaptor.capture());
        final IQ response = packetCaptor.getValue();
        assertEquals(IQ.Type.error, response.getType());
        assertEquals(PacketError.Condition.not_acceptable, response.getError().getCondition());

        final Element pubsubError = response.getError().getElement()
                .element("payload-too-big");
        assertNotNull(pubsubError, "Expected a 'payload-too-big' pubsub error element");
    }

    /**
     * Tests that a node meets preconditions when the preconditions have more than the one value that matches the node configuration.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3311">OF-3311: nodeMeetsPreconditions() ignores extra precondition values when the node configuration has only one value</a>
     */
    @Test
    public void testNodeMeetsPreconditionsFailsWhenPreconditionHasAdditionalValues()
    {
        // Setup node configuration.
        final Node node = Mockito.mock(Node.class);

        final DataForm configuration = new DataForm(DataForm.Type.form);
        final FormField configField = configuration.addField();
        configField.setVariable("pubsub#access_model");
        configField.addValue("open");

        Mockito.when(node.getConfigurationForm(null)).thenReturn(configuration);

        // Setup preconditions with TWO values.
        final DataForm preconditions = new DataForm(DataForm.Type.submit);
        final FormField preconditionField = preconditions.addField();
        preconditionField.setVariable("pubsub#access_model");
        preconditionField.addValue("open");
        preconditionField.addValue("presence");

        // Execute system under test.
        final boolean result = PubSubEngine.nodeMeetsPreconditions(node, preconditions);

        // Verify result.
        assertFalse(result);
    }

    /**
     * Order-independent equal sets match. (Note: this does not exercise the subset behavior, see
     * {@link #testNodeMeetsPreconditionsSucceedsWhenConfigHasExtraValues} for that.)
     */
    @Test
    public void testNodeMeetsPreconditionsSucceedsWhenMultiValueFieldsMatchRegardlessOfOrder()
    {
        // Setup test fixture.
        final Node node = Mockito.mock(Node.class);
        final DataForm configuration = new DataForm(DataForm.Type.form);
        final FormField configField = configuration.addField();
        configField.setVariable("pubsub#roster_groups_allowed");
        configField.addValue("admins");
        configField.addValue("moderators");
        Mockito.when(node.getConfigurationForm(null)).thenReturn(configuration);

        final DataForm preconditions = new DataForm(DataForm.Type.submit);
        final FormField preconditionField = preconditions.addField();
        preconditionField.setVariable("pubsub#roster_groups_allowed");
        preconditionField.addValue("moderators");
        preconditionField.addValue("admins");

        // Execute system under test.
        final boolean result = PubSubEngine.nodeMeetsPreconditions(node, preconditions);

        // Verify result.
        assertTrue(result);
    }

    /**
     * The node configuration may contain more values than the precondition requires; the precondition is met as long as
     * all required values are present. This pins the subset (containsAll) semantics, distinguishing them from exact set
     * equality.
     */
    @Test
    public void testNodeMeetsPreconditionsSucceedsWhenConfigHasExtraValues()
    {
        // Setup test fixture.
        final Node node = Mockito.mock(Node.class);
        final DataForm configuration = new DataForm(DataForm.Type.form);
        final FormField configField = configuration.addField();
        configField.setVariable("pubsub#roster_groups_allowed");
        configField.addValue("admins");
        configField.addValue("moderators");
        configField.addValue("guests");
        Mockito.when(node.getConfigurationForm(null)).thenReturn(configuration);

        final DataForm preconditions = new DataForm(DataForm.Type.submit);
        final FormField preconditionField = preconditions.addField();
        preconditionField.setVariable("pubsub#roster_groups_allowed");
        preconditionField.addValue("admins");
        preconditionField.addValue("moderators");

        // Execute system under test.
        final boolean result = PubSubEngine.nodeMeetsPreconditions(node, preconditions);

        // Verify result.
        assertTrue(result);
    }

    /**
     * XEP-0004 boolean equivalence: "true"/"1" and "false"/"0" are treated as equal.
     */
    @Test
    public void testNodeMeetsPreconditionsTreatsBooleanValuesAsEquivalent()
    {
        // Setup test fixture.
        final Node node = Mockito.mock(Node.class);
        final DataForm configuration = new DataForm(DataForm.Type.form);
        final FormField configField = configuration.addField();
        configField.setVariable("pubsub#persist_items");
        configField.addValue("true");
        Mockito.when(node.getConfigurationForm(null)).thenReturn(configuration);

        final DataForm preconditions = new DataForm(DataForm.Type.submit);
        final FormField preconditionField = preconditions.addField();
        preconditionField.setVariable("pubsub#persist_items");
        preconditionField.addValue("1");

        // Execute system under test.
        final boolean result = PubSubEngine.nodeMeetsPreconditions(node, preconditions);

        // Verify result.
        assertTrue(result);
    }

    /**
     * "true" and "false" (i.e. "0") are not equivalent.
     */
    @Test
    public void testNodeMeetsPreconditionsRejectsMismatchedBooleanValues()
    {
        // Setup test fixture.
        final Node node = Mockito.mock(Node.class);
        final DataForm configuration = new DataForm(DataForm.Type.form);
        final FormField configField = configuration.addField();
        configField.setVariable("pubsub#persist_items");
        configField.addValue("true");
        Mockito.when(node.getConfigurationForm(null)).thenReturn(configuration);

        final DataForm preconditions = new DataForm(DataForm.Type.submit);
        final FormField preconditionField = preconditions.addField();
        preconditionField.setVariable("pubsub#persist_items");
        preconditionField.addValue("0");

        // Execute system under test.
        final boolean result = PubSubEngine.nodeMeetsPreconditions(node, preconditions);

        // Verify result.
        assertFalse(result);
    }

    /**
     * Null preconditions are trivially satisfied.
     */
    @Test
    public void testNodeMeetsPreconditionsReturnsTrueForNullPreconditions()
    {
        // Setup test fixture.
        final Node node = Mockito.mock(Node.class);

        // Execute system under test.
        final boolean result = PubSubEngine.nodeMeetsPreconditions(node, null);

        // Verify result.
        assertTrue(result);
        Mockito.verify(node, Mockito.never()).getConfigurationForm(Mockito.any());
    }

    /**
     * A precondition naming a field absent from the node configuration is not met.
     */
    @Test
    public void testNodeMeetsPreconditionsRejectsUnknownField()
    {
        // Setup test fixture.
        final Node node = Mockito.mock(Node.class);
        final DataForm configuration = new DataForm(DataForm.Type.form);
        final FormField configField = configuration.addField();
        configField.setVariable("pubsub#access_model");
        configField.addValue("open");
        Mockito.when(node.getConfigurationForm(null)).thenReturn(configuration);

        final DataForm preconditions = new DataForm(DataForm.Type.submit);
        final FormField preconditionField = preconditions.addField();
        preconditionField.setVariable("pubsub#persist_items");
        preconditionField.addValue("1");

        // Execute system under test.
        final boolean result = PubSubEngine.nodeMeetsPreconditions(node, preconditions);

        // Verify result.
        assertFalse(result);
    }

    /**
     * The FORM_TYPE field is ignored and never compared, even if the node config omits it.
     */
    @Test
    public void testNodeMeetsPreconditionsIgnoresFormType()
    {
        // Setup test fixture.
        final Node node = Mockito.mock(Node.class);
        final DataForm configuration = new DataForm(DataForm.Type.form);
        final FormField configField = configuration.addField();
        configField.setVariable("pubsub#access_model");
        configField.addValue("open");
        Mockito.when(node.getConfigurationForm(null)).thenReturn(configuration);

        final DataForm preconditions = new DataForm(DataForm.Type.submit);
        final FormField formType = preconditions.addField();
        formType.setVariable("FORM_TYPE");
        formType.addValue("http://jabber.org/protocol/pubsub#publish-options");
        final FormField preconditionField = preconditions.addField();
        preconditionField.setVariable("pubsub#access_model");
        preconditionField.addValue("open");

        // Execute system under test.
        final boolean result = PubSubEngine.nodeMeetsPreconditions(node, preconditions);

        // Verify result.
        assertTrue(result);
    }
}

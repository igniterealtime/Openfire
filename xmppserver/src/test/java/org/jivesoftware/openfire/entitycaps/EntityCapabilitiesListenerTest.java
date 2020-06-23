/*
 * Copyright (C) 2020 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.entitycaps;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.cache.CacheFactory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.xmpp.packet.JID;

import java.util.Collections;

import static org.mockito.Mockito.*;

/**
 * Verifies the functionality of the event listening mechanism as provided by {@link EntityCapabilitiesListener}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
@RunWith( MockitoJUnitRunner.class)
public class EntityCapabilitiesListenerTest
{
    @Mock
    private XMPPServer xmppServer;

    @Mock
    private EntityCapabilitiesListener userSpecific;

    @Mock
    private EntityCapabilitiesListener otherUserSpecific;

    @Mock
    private EntityCapabilitiesListener allUsers;

    private EntityCapabilitiesManager manager;

    @BeforeClass
    public static void beforeClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        CacheFactory.initialize();
    }

    @Before
    public void setupManager() {
        Fixtures.clearExistingProperties();

        //noinspection deprecation
        XMPPServer.setInstance(xmppServer);

        manager = new EntityCapabilitiesManager();
        manager.initialize(xmppServer);
        manager.start();
        manager.clearCaches();
    }

    /**
     * Asserts that registering a user-specific listener causes that listener to receive a relevant event once that's
     * generated.
     */
    @Test
    public void testUserSpecificListener() throws Exception
    {
        // Setup fixture.
        final JID entity = new JID( "john", "example.org", "mobile" );
        final EntityCapabilities caps = new EntityCapabilities();
        caps.setVerAttribute( "test-ver" );
        caps.setHashAttribute( "test-hash" );
        caps.addFeature( "test-feature" );
        caps.addIdentity( "test-identity" );
        manager.addListener( entity, userSpecific );

        // Execute system under test.
        manager.registerCapabilities( entity, caps );

        // Verify results.
        verify( userSpecific, times(1) ).entityCapabilitiesChanged( entity, caps, caps.getFeatures(), Collections.emptySet(), caps.getIdentities(), Collections.emptySet() );
    }

    /**
     * Asserts that registering a user-specific listener causes that listener to <em>not</em> receive an event that is
     * not relevant (but relevant to another user).
     */
    @Test
    public void testUserSpecificListenerWrongUser() throws Exception
    {
        // Setup fixture.
        final JID entity = new JID( "john", "example.org", "mobile" );
        final JID otherEntity = new JID( "jane", "example.com", "desktop" );
        final EntityCapabilities caps = new EntityCapabilities();
        caps.setVerAttribute( "test-ver" );
        caps.setHashAttribute( "test-hash" );
        caps.addFeature( "test-feature" );
        caps.addIdentity( "test-identity" );
        manager.addListener( otherEntity, otherUserSpecific );

        // Execute system under test.
        manager.registerCapabilities( entity, caps );

        // Verify results.
        verify( otherUserSpecific, never() ).entityCapabilitiesChanged( entity, caps, caps.getFeatures(), Collections.emptySet(), caps.getIdentities(), Collections.emptySet() );
    }

    /**
     * Asserts that registering a all-user listener causes that listener to receive an event once that's generated.
     */
    @Test
    public void testAllUserListener() throws Exception
    {
        // Setup fixture.
        final JID entity = new JID( "john", "example.org", "mobile" );
        final EntityCapabilities caps = new EntityCapabilities();
        caps.setVerAttribute( "test-ver" );
        caps.setHashAttribute( "test-hash" );
        caps.addFeature( "test-feature" );
        caps.addIdentity( "test-identity" );
        manager.addListener( allUsers );

        // Execute system under test.
        manager.registerCapabilities( entity, caps );

        // Verify results.
        verify( allUsers, times(1) ).entityCapabilitiesChanged( entity, caps, caps.getFeatures(), Collections.emptySet(), caps.getIdentities(), Collections.emptySet() );
    }

    /**
     * Asserts that registered listener do not get duplicate events (where capabilities have not changed).
     */
    @Test
    public void testNoDuplicate() throws Exception
    {
        // Setup fixture.
        final JID entity = new JID( "john", "example.org", "mobile" );
        final EntityCapabilities caps = new EntityCapabilities();
        caps.setVerAttribute( "test-ver" );
        caps.setHashAttribute( "test-hash" );
        caps.addFeature( "test-feature" );
        caps.addIdentity( "test-identity" );
        manager.addListener( allUsers );
        manager.addListener( entity, userSpecific );

        // Execute system under test.
        manager.registerCapabilities( entity, caps ); // register twice, should trigger just once!
        manager.registerCapabilities( entity, caps );

        // Verify results.
        verify( allUsers, times(1) ).entityCapabilitiesChanged( entity, caps, caps.getFeatures(), Collections.emptySet(), caps.getIdentities(), Collections.emptySet() );
        verify( userSpecific, times(1) ).entityCapabilitiesChanged( entity, caps, caps.getFeatures(), Collections.emptySet(), caps.getIdentities(), Collections.emptySet() );
    }

    /**
     * Asserts that registered listener get more events after an exising capability has been updated (verifies the
     * case of updated capabilities).
     */
    @Test
    public void testUpdatedCaps() throws Exception
    {
        // Setup fixture.
        final JID entity = new JID( "john", "example.org", "mobile" );
        final EntityCapabilities capsA = new EntityCapabilities();
        capsA.setVerAttribute( "test-ver-a" );
        capsA.setHashAttribute( "test-hash-a" );
        capsA.addFeature( "test-feature-a" );
        capsA.addIdentity( "test-identity-a" );
        final EntityCapabilities capsB = new EntityCapabilities();
        capsB.setVerAttribute( "test-ver-b" );
        capsB.setHashAttribute( "test-hash-b" );
        capsB.addFeature( "test-feature-b" );
        capsB.addIdentity( "test-identity-b" );
        manager.addListener( allUsers );
        manager.addListener( entity, userSpecific );

        // Execute system under test.
        manager.registerCapabilities( entity, capsA );
        manager.registerCapabilities( entity, capsB );

        // Verify results.
        verify( allUsers, times(1) ).entityCapabilitiesChanged( entity, capsA, capsA.getFeatures(), Collections.emptySet(), capsA.getIdentities(), Collections.emptySet() );
        verify( allUsers, times(1) ).entityCapabilitiesChanged( entity, capsB, capsB.getFeatures(), capsA.getFeatures(), capsB.getIdentities(), capsA.getIdentities() );
        verify( userSpecific, times(1) ).entityCapabilitiesChanged( entity, capsA, capsA.getFeatures(), Collections.emptySet(), capsA.getIdentities(), Collections.emptySet() );
        verify( userSpecific, times(1) ).entityCapabilitiesChanged( entity, capsB, capsB.getFeatures(), capsA.getFeatures(), capsB.getIdentities(), capsA.getIdentities() );
    }

    /**
     * Asserts that removing the registration of an user-specific listener causes that listener to no longer receive events.
     */
    @Test
    public void testDeregisterUserSpecificListener() throws Exception
    {
        // Setup fixture.
        final JID entity = new JID( "john", "example.org", "mobile" );
        final EntityCapabilities caps = new EntityCapabilities();
        caps.setVerAttribute( "test-ver" );
        caps.setHashAttribute( "test-hash" );
        manager.addListener( entity, userSpecific );
        manager.removeListener( entity, userSpecific );

        // Execute system under test.
        manager.registerCapabilities( entity, caps );

        // Verify results.
        verify( userSpecific, never() ).entityCapabilitiesChanged( entity, caps, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet() );
    }

    /**
     * Asserts that removing the registration of all user-specific causes a previously registered listener to no longer
     * receive events.
     */
    @Test
    public void testDeregisterAllUserSpecificListener() throws Exception
    {
        // Setup fixture.
        final JID entity = new JID( "john", "example.org", "mobile" );
        final EntityCapabilities caps = new EntityCapabilities();
        caps.setVerAttribute( "test-ver" );
        caps.setHashAttribute( "test-hash" );
        manager.addListener( entity, userSpecific );
        manager.removeListeners( entity );

        // Execute system under test.
        manager.registerCapabilities( entity, caps );

        // Verify results.
        verify( userSpecific, never() ).entityCapabilitiesChanged( entity, caps, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet() );
    }

    /**
     * Asserts that removing the registration of an all-user listener causes that listener to no longer receive events.
     */
    @Test
    public void testDeregisterAllUserListener() throws Exception
    {
        // Setup fixture.
        final JID entity = new JID( "john", "example.org", "mobile" );
        final EntityCapabilities caps = new EntityCapabilities();
        caps.setVerAttribute( "test-ver" );
        caps.setHashAttribute( "test-hash" );
        manager.addListener( allUsers );
        manager.removeListener( allUsers );

        // Execute system under test.
        manager.registerCapabilities( entity, caps );

        // Verify results.
        verify( allUsers, never() ).entityCapabilitiesChanged( entity, caps, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet() );
    }
}

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
package org.jivesoftware.openfire.keystore;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.CertificateEventListener;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that, with the cert.stores.readonly setting on, every change to a certificate store is refused before
 * anything is changed in memory or on disk, and that with the setting off the same changes are still made.
 *
 * The setting is read at the moment of each change, so these tests change it on stores that already exist.
 *
 * @author Matthew Vivian
 */
public class CertificateStoreReadOnlyTest
{
    private static final String STORE_TYPE = "jks";
    private static final char[] PASSWORD = "changeit".toCharArray();
    private static final String EXISTING_ALIAS = "existing";
    private static final String NEW_ALIAS = "new";

    @TempDir
    Path tempDir;

    /**
     * Set when a store created by {@link #trustStore()} or {@link #identityStore()} reaches persist().
     */
    private boolean persistReached;

    private File identityStoreFile;
    private File trustStoreFile;

    /**
     * A certificate chain for the XMPP domain. The identity store holds its private key with only the end-entity
     * certificate, as it would after generating a signing request, so that installing the full chain as the signing
     * request reply changes the entry.
     */
    private KeystoreTestUtils.ResultHolder existingChain;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @AfterAll
    public static void tearDownClass()
    {
        Fixtures.clearExistingProperties();
    }

    @BeforeEach
    public void setUp() throws Exception
    {
        Fixtures.clearExistingProperties();
        JiveGlobals.setProperty( "xmpp.domain", Fixtures.XMPP_DOMAIN );
        XMPPServer.setInstance( Fixtures.mockXMPPServer() );
        persistReached = false;

        existingChain = KeystoreTestUtils.generateValidCertificateChain( Fixtures.XMPP_DOMAIN );

        final KeyStore identity = KeyStore.getInstance( STORE_TYPE );
        identity.load( null, PASSWORD );
        identity.setKeyEntry( EXISTING_ALIAS, existingChain.getKeyPair().getPrivate(), PASSWORD, new Certificate[] { existingChain.getCertificate() } );
        identityStoreFile = tempDir.resolve( "keystore.jks" ).toFile();
        try ( final FileOutputStream os = new FileOutputStream( identityStoreFile ) )
        {
            identity.store( os, PASSWORD );
        }

        final KeyStore trust = KeyStore.getInstance( STORE_TYPE );
        trust.load( null, PASSWORD );
        trust.setCertificateEntry( EXISTING_ALIAS, KeystoreTestUtils.generateSelfSignedCertificate().getCertificate() );
        trustStoreFile = tempDir.resolve( "truststore.jks" ).toFile();
        try ( final FileOutputStream os = new FileOutputStream( trustStoreFile ) )
        {
            trust.store( os, PASSWORD );
        }
    }

    @AfterEach
    public void tearDown()
    {
        JiveGlobals.deleteProperty( CertificateStoreManager.READ_ONLY.getKey() );
        XMPPServer.setInstance( null );
    }

    // -------------------------------------------------------------------------
    // The setting
    // -------------------------------------------------------------------------

    /**
     * Verifies that the setting is named cert.stores.readonly, is off by default, and can be changed without a restart.
     */
    @Test
    public void testSettingIsOffByDefaultAndDynamic()
    {
        assertEquals( "cert.stores.readonly", CertificateStoreManager.READ_ONLY.getKey(), "the setting must be named cert.stores.readonly" );
        assertFalse( CertificateStoreManager.READ_ONLY.getDefaultValue(), "the stores must be writable by default" );
        assertFalse( CertificateStoreManager.READ_ONLY.getValue(), "the stores must be writable when the setting has not been set" );
        assertTrue( CertificateStoreManager.READ_ONLY.isDynamic(), "a change to the setting must take effect without a restart" );
    }

    /**
     * Verifies that a store accepts a change when the setting has never been set.
     */
    @Test
    public void testStoreIsWritableWhenSettingIsNotSet() throws Exception
    {
        // Setup test fixture.
        final TrustStore store = new TrustStore( configuration( trustStoreFile ), false );

        // Execute system under test.
        store.delete( EXISTING_ALIAS );

        // Verify result.
        assertFalse( load( trustStoreFile ).containsAlias( EXISTING_ALIAS ), "the change must have been saved to disk when the setting is not set" );
    }

    /**
     * Verifies that turning the setting on applies to a store that already exists, and that turning it off again
     * allows the same store to be changed.
     */
    @Test
    public void testChangeOfSettingAppliesToExistingStore() throws Exception
    {
        // Setup test fixture: a store created while the setting is off.
        final TrustStore store = new TrustStore( configuration( trustStoreFile ), false );
        final byte[] fileBefore = Files.readAllBytes( trustStoreFile.toPath() );

        // Execute system under test: turn the setting on.
        setReadOnly( true );

        // Verify result.
        assertThrows( CertificateStoreReadOnlyException.class, () -> store.delete( EXISTING_ALIAS ), "a change must be refused once the setting is on, without recreating the store" );
        assertArrayEquals( fileBefore, Files.readAllBytes( trustStoreFile.toPath() ), "the store file must be unchanged while the setting is on" );

        // Execute system under test: turn the setting off again.
        setReadOnly( false );
        store.delete( EXISTING_ALIAS );

        // Verify result.
        assertFalse( load( trustStoreFile ).containsAlias( EXISTING_ALIAS ), "a change must be saved once the setting is off again" );
    }

    // -------------------------------------------------------------------------
    // CertificateStore constructor
    // -------------------------------------------------------------------------

    /**
     * Verifies that a missing store file is not created when the setting is on.
     */
    @Test
    public void testConstructorDoesNotCreateMissingFileWhenReadOnly()
    {
        // Setup test fixture.
        setReadOnly( true );
        final File missing = tempDir.resolve( "missing.jks" ).toFile();

        // Execute system under test.
        final CertificateStoreConfigException e = assertThrows( CertificateStoreConfigException.class, () -> new TrustStore( configuration( missing ), true ),
            "creating a store file must be refused when the setting is on" );

        // Verify result.
        assertInstanceOf( CertificateStoreReadOnlyException.class, e, "the refusal must be reported as a read-only refusal" );
        assertFalse( missing.exists(), "no file must be created when the setting is on" );
    }

    /**
     * Verifies that a missing identity store file is not created when the setting is on.
     */
    @Test
    public void testIdentityStoreConstructorDoesNotCreateMissingFileWhenReadOnly()
    {
        // Setup test fixture.
        setReadOnly( true );
        final File missing = tempDir.resolve( "missing.jks" ).toFile();

        // Execute system under test.
        assertThrows( CertificateStoreReadOnlyException.class, () -> new IdentityStore( configuration( missing ), true ),
            "creating an identity store file must be refused when the setting is on" );

        // Verify result.
        assertFalse( missing.exists(), "no file must be created when the setting is on" );
    }

    /**
     * Verifies that a missing store file is still created when the setting is off.
     */
    @Test
    public void testConstructorCreatesMissingFileWhenWritable() throws Exception
    {
        // Setup test fixture.
        setReadOnly( false );
        final File missing = tempDir.resolve( "missing.jks" ).toFile();

        // Execute system under test.
        final TrustStore store = new TrustStore( configuration( missing ), true );

        // Verify result.
        assertTrue( missing.exists(), "a missing file must be created when the setting is off" );
        assertFalse( store.getStore().aliases().hasMoreElements(), "the created store must be empty" );
    }

    /**
     * Verifies that asking for a store to be created if absent loads it when the file exists, with the setting on, as
     * nothing needs to be written.
     */
    @Test
    public void testConstructorLoadsExistingFileWhenReadOnlyAndCreateIfAbsentIsSet() throws Exception
    {
        // Setup test fixture.
        setReadOnly( true );
        final byte[] fileBefore = Files.readAllBytes( trustStoreFile.toPath() );

        // Execute system under test.
        final TrustStore store = new TrustStore( configuration( trustStoreFile ), true );

        // Verify result.
        assertTrue( store.getStore().containsAlias( EXISTING_ALIAS ), "the existing file must have been loaded" );
        assertArrayEquals( fileBefore, Files.readAllBytes( trustStoreFile.toPath() ), "the store file must be unchanged" );
    }

    // -------------------------------------------------------------------------
    // CertificateStore.persist
    // -------------------------------------------------------------------------

    /**
     * Verifies that persisting is refused when the setting is on, and that a change made directly to the in-memory
     * store is discarded by reloading the store from disk, so that memory matches the file.
     */
    @Test
    public void testPersistWhenReadOnlyIsRefusedAndStoreIsReloaded() throws Exception
    {
        // Setup test fixture: change the in-memory store directly, as a plugin using getStore() could.
        final TrustStore store = new TrustStore( configuration( trustStoreFile ), false );
        final byte[] fileBefore = Files.readAllBytes( trustStoreFile.toPath() );
        store.getStore().deleteEntry( EXISTING_ALIAS );
        setReadOnly( true );

        // Execute system under test.
        assertThrows( CertificateStoreReadOnlyException.class, store::persist, "persisting must be refused when the setting is on" );

        // Verify result.
        assertArrayEquals( fileBefore, Files.readAllBytes( trustStoreFile.toPath() ), "the store file must be unchanged" );
        assertTrue( store.getStore().containsAlias( EXISTING_ALIAS ), "the in-memory change must have been discarded by reloading the store" );
        assertInMemoryMatchesFile( store );
    }

    /**
     * Verifies that a refused persist reports the store as changed (so that listeners reload what uses it) when it
     * discards a change made in memory.
     */
    @Test
    public void testPersistWhenReadOnlyAndMemoryDiffersReportsStoreChanged() throws Exception
    {
        // Setup test fixture.
        final TrustStore store = new TrustStore( configuration( trustStoreFile ), false );
        store.getStore().deleteEntry( EXISTING_ALIAS );
        setReadOnly( true );
        final List<CertificateStore> changed = new CopyOnWriteArrayList<>();
        final CertificateEventListener listener = changed::add;
        CertificateManager.addListener( listener );
        try
        {
            // Execute system under test.
            assertThrows( CertificateStoreReadOnlyException.class, store::persist, "persisting must be refused when the setting is on" );
        }
        finally
        {
            CertificateManager.removeListener( listener );
        }

        // Verify result.
        assertEquals( List.of( store ), changed, "discarding the in-memory change must be reported once" );
    }

    /**
     * Verifies that a refused persist does not report a trust store as changed when the store in memory already matches
     * the file, as the listeners would then reload connection listeners or restart the admin console for no change.
     */
    @Test
    public void testPersistWhenReadOnlyAndTrustStoreMatchesFileDoesNotReportStoreChanged() throws Exception
    {
        assertRefusedPersistReportsNoChange( new TrustStore( configuration( trustStoreFile ), false ) );
    }

    /**
     * Verifies that a refused persist does not report an identity store (which holds a private key) as changed when the
     * store in memory already matches the file.
     */
    @Test
    public void testPersistWhenReadOnlyAndIdentityStoreMatchesFileDoesNotReportStoreChanged() throws Exception
    {
        assertRefusedPersistReportsNoChange( new IdentityStore( configuration( identityStoreFile ), false ) );
    }

    /**
     * Verifies that a refused persist reports an identity store as changed when only the private key of an entry was
     * changed in memory, with its certificate chain left as it was.
     */
    @Test
    public void testPersistWhenReadOnlyAndOnlyPrivateKeyDiffersReportsStoreChanged() throws Exception
    {
        // Setup test fixture: replace the private key, keeping the certificate.
        final IdentityStore store = new IdentityStore( configuration( identityStoreFile ), false );
        final PrivateKey otherKey = KeystoreTestUtils.generateValidCertificateChain( Fixtures.XMPP_DOMAIN ).getKeyPair().getPrivate();
        store.getStore().setKeyEntry( EXISTING_ALIAS, otherKey, PASSWORD, new Certificate[] { existingChain.getCertificate() } );
        setReadOnly( true );
        final List<CertificateStore> changed = new CopyOnWriteArrayList<>();
        final CertificateEventListener listener = changed::add;
        CertificateManager.addListener( listener );
        try
        {
            // Execute system under test.
            assertThrows( CertificateStoreReadOnlyException.class, store::persist, "persisting must be refused when the setting is on" );
        }
        finally
        {
            CertificateManager.removeListener( listener );
        }

        // Verify result.
        assertEquals( List.of( store ), changed, "discarding the changed private key must be reported once" );
        assertInMemoryMatchesFile( store );
    }

    private void assertRefusedPersistReportsNoChange( final CertificateStore store ) throws Exception
    {
        // Setup test fixture.
        setReadOnly( true );
        final List<CertificateStore> changed = new CopyOnWriteArrayList<>();
        final CertificateEventListener listener = changed::add;
        CertificateManager.addListener( listener );
        try
        {
            // Execute system under test.
            assertThrows( CertificateStoreReadOnlyException.class, store::persist, "persisting must be refused when the setting is on" );
        }
        finally
        {
            CertificateManager.removeListener( listener );
        }

        // Verify result.
        assertTrue( changed.isEmpty(), "a store that matches its file must not be reported as changed" );
        assertInMemoryMatchesFile( store );
    }

    /**
     * Verifies that when the store cannot be reloaded during a refused persist (here, as its file has gone), the
     * refusal is still what is reported, with the reload failure attached, and no file is written.
     */
    @Test
    public void testPersistWhenReadOnlyAndStoreCannotBeReloadedStillReportsRefusal() throws Exception
    {
        // Setup test fixture: the file disappears after the store was loaded.
        final TrustStore store = new TrustStore( configuration( trustStoreFile ), false );
        Files.delete( trustStoreFile.toPath() );
        setReadOnly( true );

        // Execute system under test.
        final CertificateStoreReadOnlyException refusal = assertThrows( CertificateStoreReadOnlyException.class, store::persist,
            "persisting must be refused when the setting is on, even when the store cannot be reloaded" );

        // Verify result.
        assertEquals( 1, refusal.getSuppressed().length, "the reload failure must be attached to the refusal" );
        assertInstanceOf( CertificateStoreConfigException.class, refusal.getSuppressed()[0], "the attached failure must be the reload failure" );
        assertFalse( trustStoreFile.exists(), "a refused persist must not write the store file" );
    }

    /**
     * Verifies that a store can still be persisted when the setting is off.
     */
    @Test
    public void testPersistWhenWritableSavesChange() throws Exception
    {
        // Setup test fixture.
        setReadOnly( false );
        final TrustStore store = new TrustStore( configuration( trustStoreFile ), false );
        store.getStore().deleteEntry( EXISTING_ALIAS );

        // Execute system under test.
        store.persist();

        // Verify result.
        assertFalse( load( trustStoreFile ).containsAlias( EXISTING_ALIAS ), "the change must have been saved to disk" );
    }

    // -------------------------------------------------------------------------
    // CertificateStore.delete
    // -------------------------------------------------------------------------

    /**
     * Verifies that deleting an entry is refused when the setting is on, and leaves the store unchanged.
     */
    @Test
    public void testDeleteWhenReadOnlyIsRefused() throws Exception
    {
        // Setup test fixture.
        final TrustStore store = trustStore();
        final byte[] fileBefore = Files.readAllBytes( trustStoreFile.toPath() );
        setReadOnly( true );

        // Execute system under test.
        assertThrows( CertificateStoreReadOnlyException.class, () -> store.delete( EXISTING_ALIAS ), "deleting must be refused when the setting is on" );

        // Verify result.
        assertRefusedBeforeChange();
        assertArrayEquals( fileBefore, Files.readAllBytes( trustStoreFile.toPath() ), "the store file must be unchanged" );
        assertTrue( store.getStore().containsAlias( EXISTING_ALIAS ), "the entry must still be in memory" );
        assertInMemoryMatchesFile( store );
    }

    /**
     * Verifies that deleting an alias that the store does not contain is refused when the setting is on, rather than
     * silently ignored, and leaves the store unchanged.
     */
    @Test
    public void testDeleteOfAbsentAliasWhenReadOnlyIsRefused() throws Exception
    {
        // Setup test fixture.
        final TrustStore store = trustStore();
        final byte[] fileBefore = Files.readAllBytes( trustStoreFile.toPath() );
        assertFalse( store.getStore().containsAlias( NEW_ALIAS ), "the fixture must not contain the alias that is deleted" );
        setReadOnly( true );

        // Execute system under test.
        assertThrows( CertificateStoreReadOnlyException.class, () -> store.delete( NEW_ALIAS ), "deleting an absent alias must be refused when the setting is on" );

        // Verify result.
        assertRefusedBeforeChange();
        assertArrayEquals( fileBefore, Files.readAllBytes( trustStoreFile.toPath() ), "the store file must be unchanged" );
        assertInMemoryMatchesFile( store );
    }

    /**
     * Verifies that an entry can still be deleted when the setting is off.
     */
    @Test
    public void testDeleteWhenWritableRemovesEntry() throws Exception
    {
        // Setup test fixture.
        setReadOnly( false );
        final TrustStore store = new TrustStore( configuration( trustStoreFile ), false );

        // Execute system under test.
        store.delete( EXISTING_ALIAS );

        // Verify result.
        assertFalse( store.getStore().containsAlias( EXISTING_ALIAS ), "the entry must have been removed from memory" );
        assertFalse( load( trustStoreFile ).containsAlias( EXISTING_ALIAS ), "the entry must have been removed from disk" );
    }

    /**
     * Verifies that when delete() is refused by persist() (here because the setting is turned on after the check at the
     * top of the method), the refusal reaches the caller as a {@link CertificateStoreReadOnlyException} rather than
     * wrapped in a general failure, and the store is unchanged.
     */
    @Test
    public void testDeleteReportsRefusalByPersistUnwrapped() throws Exception
    {
        // Setup test fixture.
        final TrustStore store = trustStoreThatBecomesReadOnlyOnPersist();
        final byte[] fileBefore = Files.readAllBytes( trustStoreFile.toPath() );

        // Execute system under test.
        final CertificateStoreConfigException e = assertThrows( CertificateStoreConfigException.class, () -> store.delete( EXISTING_ALIAS ), "the refusal must reach the caller" );

        // Verify result.
        assertInstanceOf( CertificateStoreReadOnlyException.class, e, "a refusal by persist() must be reported as a read-only refusal" );
        assertArrayEquals( fileBefore, Files.readAllBytes( trustStoreFile.toPath() ), "the store file must be unchanged" );
        assertInMemoryMatchesFile( store );
    }

    // -------------------------------------------------------------------------
    // TrustStore.installCertificate
    // -------------------------------------------------------------------------

    /**
     * Verifies that installing a certificate in a trust store is refused when the setting is on, and leaves the store
     * unchanged.
     */
    @Test
    public void testTrustStoreInstallCertificateWhenReadOnlyIsRefused() throws Exception
    {
        // Setup test fixture.
        final TrustStore store = trustStore();
        final byte[] fileBefore = Files.readAllBytes( trustStoreFile.toPath() );
        final String pem = KeystoreTestUtils.toPemFormat( KeystoreTestUtils.generateSelfSignedCertificate().getCertificate() );
        setReadOnly( true );

        // Execute system under test.
        assertThrows( CertificateStoreReadOnlyException.class, () -> store.installCertificate( NEW_ALIAS, pem ), "installing in a trust store must be refused when the setting is on" );

        // Verify result.
        assertRefusedBeforeChange();
        assertArrayEquals( fileBefore, Files.readAllBytes( trustStoreFile.toPath() ), "the store file must be unchanged" );
        assertFalse( store.getStore().containsAlias( NEW_ALIAS ), "the certificate must not be in memory" );
        assertInMemoryMatchesFile( store );
    }

    /**
     * Verifies that a certificate can still be installed in a trust store when the setting is off.
     */
    @Test
    public void testTrustStoreInstallCertificateWhenWritableAddsEntry() throws Exception
    {
        // Setup test fixture.
        setReadOnly( false );
        final TrustStore store = new TrustStore( configuration( trustStoreFile ), false );
        final String pem = KeystoreTestUtils.toPemFormat( KeystoreTestUtils.generateSelfSignedCertificate().getCertificate() );

        // Execute system under test.
        store.installCertificate( NEW_ALIAS, pem );

        // Verify result.
        assertTrue( load( trustStoreFile ).containsAlias( NEW_ALIAS ), "the certificate must have been saved to disk" );
        assertTrue( store.getStore().containsAlias( NEW_ALIAS ), "the certificate must be in memory" );
    }

    // -------------------------------------------------------------------------
    // IdentityStore.installCertificate (both overloads)
    // -------------------------------------------------------------------------

    /**
     * Verifies that installing a certificate under a given alias in an identity store is refused when the setting is
     * on, and leaves the store unchanged.
     */
    @Test
    public void testIdentityStoreInstallCertificateWithAliasWhenReadOnlyIsRefused() throws Exception
    {
        // Setup test fixture.
        final IdentityStore store = identityStore();
        final byte[] fileBefore = Files.readAllBytes( identityStoreFile.toPath() );
        final KeystoreTestUtils.ResultHolder chain = KeystoreTestUtils.generateValidCertificateChain( Fixtures.XMPP_DOMAIN );
        setReadOnly( true );

        // Execute system under test.
        assertThrows( CertificateStoreReadOnlyException.class, () -> store.installCertificate( NEW_ALIAS, toPem( chain.getCertificateChain() ), toPem( chain.getKeyPair().getPrivate() ), null ),
            "installing in an identity store must be refused when the setting is on" );

        // Verify result.
        assertRefusedBeforeChange();
        assertArrayEquals( fileBefore, Files.readAllBytes( identityStoreFile.toPath() ), "the store file must be unchanged" );
        assertFalse( store.getStore().containsAlias( NEW_ALIAS ), "the certificate must not be in memory" );
        assertInMemoryMatchesFile( store );
    }

    /**
     * Verifies that installing a certificate under a generated alias in an identity store is refused when the setting
     * is on, and leaves the store unchanged.
     */
    @Test
    public void testIdentityStoreInstallCertificateWithGeneratedAliasWhenReadOnlyIsRefused() throws Exception
    {
        // Setup test fixture.
        final IdentityStore store = identityStore();
        final byte[] fileBefore = Files.readAllBytes( identityStoreFile.toPath() );
        final Set<String> aliasesBefore = new HashSet<>( Collections.list( store.getStore().aliases() ) );
        final KeystoreTestUtils.ResultHolder chain = KeystoreTestUtils.generateValidCertificateChain( Fixtures.XMPP_DOMAIN );
        setReadOnly( true );

        // Execute system under test.
        assertThrows( CertificateStoreReadOnlyException.class, () -> store.installCertificate( toPem( chain.getCertificateChain() ), toPem( chain.getKeyPair().getPrivate() ), null ),
            "installing in an identity store must be refused when the setting is on" );

        // Verify result.
        assertRefusedBeforeChange();
        assertArrayEquals( fileBefore, Files.readAllBytes( identityStoreFile.toPath() ), "the store file must be unchanged" );
        assertEquals( aliasesBefore, new HashSet<>( Collections.list( store.getStore().aliases() ) ), "no entry must have been added in memory" );
        assertInMemoryMatchesFile( store );
    }

    /**
     * Verifies that a certificate can still be installed under a given alias in an identity store when the setting is
     * off.
     */
    @Test
    public void testIdentityStoreInstallCertificateWithAliasWhenWritableAddsEntry() throws Exception
    {
        // Setup test fixture.
        setReadOnly( false );
        final IdentityStore store = new IdentityStore( configuration( identityStoreFile ), false );
        final KeystoreTestUtils.ResultHolder chain = KeystoreTestUtils.generateValidCertificateChain( Fixtures.XMPP_DOMAIN );

        // Execute system under test.
        store.installCertificate( NEW_ALIAS, toPem( chain.getCertificateChain() ), toPem( chain.getKeyPair().getPrivate() ), null );

        // Verify result.
        assertTrue( load( identityStoreFile ).containsAlias( NEW_ALIAS ), "the certificate must have been saved to disk" );
        assertTrue( store.getStore().containsAlias( NEW_ALIAS ), "the certificate must be in memory" );
    }

    /**
     * Verifies that a certificate can still be installed under a generated alias in an identity store when the setting
     * is off, and that the alias returned is the one it was saved under.
     */
    @Test
    public void testIdentityStoreInstallCertificateWithGeneratedAliasWhenWritableAddsEntry() throws Exception
    {
        // Setup test fixture.
        setReadOnly( false );
        final IdentityStore store = new IdentityStore( configuration( identityStoreFile ), false );
        final KeystoreTestUtils.ResultHolder chain = KeystoreTestUtils.generateValidCertificateChain( Fixtures.XMPP_DOMAIN );

        // Execute system under test.
        final String alias = store.installCertificate( toPem( chain.getCertificateChain() ), toPem( chain.getKeyPair().getPrivate() ), null );

        // Verify result.
        assertNotEquals( EXISTING_ALIAS, alias, "the generated alias must not be one already in use" );
        assertTrue( load( identityStoreFile ).containsAlias( alias ), "the certificate must have been saved to disk under the returned alias" );
        assertTrue( store.getStore().containsAlias( alias ), "the certificate must be in memory under the returned alias" );
    }

    /**
     * Verifies that when installing a certificate is refused by persist() (here because the setting is turned on after
     * the check at the top of the method), the refusal reaches the caller as a {@link CertificateStoreReadOnlyException}
     * and the store is unchanged.
     */
    @Test
    public void testIdentityStoreInstallCertificateReportsRefusalByPersistUnwrapped() throws Exception
    {
        // Setup test fixture.
        final IdentityStore store = identityStoreThatBecomesReadOnlyOnPersist();
        final byte[] fileBefore = Files.readAllBytes( identityStoreFile.toPath() );
        final KeystoreTestUtils.ResultHolder chain = KeystoreTestUtils.generateValidCertificateChain( Fixtures.XMPP_DOMAIN );

        // Execute system under test.
        final CertificateStoreConfigException e = assertThrows( CertificateStoreConfigException.class, () -> store.installCertificate( NEW_ALIAS, toPem( chain.getCertificateChain() ), toPem( chain.getKeyPair().getPrivate() ), null ),
            "the refusal must reach the caller" );

        // Verify result.
        assertInstanceOf( CertificateStoreReadOnlyException.class, e, "a refusal by persist() must be reported as a read-only refusal" );
        assertArrayEquals( fileBefore, Files.readAllBytes( identityStoreFile.toPath() ), "the store file must be unchanged" );
        assertInMemoryMatchesFile( store );
    }

    // -------------------------------------------------------------------------
    // IdentityStore.replaceCertificate
    // -------------------------------------------------------------------------

    /**
     * Verifies that replacing the domain certificates of an identity store is refused when the setting is on, and
     * leaves the store unchanged (in particular, the existing domain entry is not removed).
     */
    @Test
    public void testIdentityStoreReplaceCertificateWhenReadOnlyIsRefused() throws Exception
    {
        // Setup test fixture.
        final IdentityStore store = identityStore();
        final byte[] fileBefore = Files.readAllBytes( identityStoreFile.toPath() );
        final KeystoreTestUtils.ResultHolder chain = KeystoreTestUtils.generateValidCertificateChain( Fixtures.XMPP_DOMAIN );
        setReadOnly( true );

        // Execute system under test.
        assertThrows( CertificateStoreReadOnlyException.class, () -> store.replaceCertificate( toPem( chain.getCertificateChain() ), toPem( chain.getKeyPair().getPrivate() ), null ),
            "replacing a certificate in an identity store must be refused when the setting is on" );

        // Verify result.
        assertRefusedBeforeChange();
        assertArrayEquals( fileBefore, Files.readAllBytes( identityStoreFile.toPath() ), "the store file must be unchanged" );
        assertTrue( store.getStore().containsAlias( EXISTING_ALIAS ), "the existing domain entry must still be in memory" );
        assertInMemoryMatchesFile( store );
    }

    /**
     * Verifies that the domain certificates of an identity store can still be replaced when the setting is off.
     */
    @Test
    public void testIdentityStoreReplaceCertificateWhenWritableReplacesEntry() throws Exception
    {
        // Setup test fixture.
        setReadOnly( false );
        final IdentityStore store = new IdentityStore( configuration( identityStoreFile ), false );
        final KeystoreTestUtils.ResultHolder chain = KeystoreTestUtils.generateValidCertificateChain( Fixtures.XMPP_DOMAIN );

        // Execute system under test.
        final String alias = store.replaceCertificate( toPem( chain.getCertificateChain() ), toPem( chain.getKeyPair().getPrivate() ), null );

        // Verify result.
        final KeyStore onDisk = load( identityStoreFile );
        assertTrue( onDisk.containsAlias( alias ), "the new certificate must have been saved to disk" );
        assertFalse( onDisk.containsAlias( EXISTING_ALIAS ), "the replaced domain entry must have been removed from disk" );
    }

    // -------------------------------------------------------------------------
    // IdentityStore.installCSRReply
    // -------------------------------------------------------------------------

    /**
     * Verifies that installing a signing request reply in an identity store is refused when the setting is on, and
     * leaves the in-memory store unchanged. This method does not persist the store itself (the admin console does so
     * afterwards), so the check in this method is what keeps memory and file in agreement.
     */
    @Test
    public void testIdentityStoreInstallCSRReplyWhenReadOnlyIsRefused() throws Exception
    {
        // Setup test fixture.
        final IdentityStore store = identityStore();
        final byte[] fileBefore = Files.readAllBytes( identityStoreFile.toPath() );
        setReadOnly( true );

        // Execute system under test.
        assertThrows( CertificateStoreReadOnlyException.class, () -> store.installCSRReply( EXISTING_ALIAS, toPem( existingChain.getCertificateChain() ) ),
            "installing a signing request reply in an identity store must be refused when the setting is on" );

        // Verify result. This method never calls persist(), so the length of the chain in memory is what shows that the
        // refusal came before the change.
        assertArrayEquals( fileBefore, Files.readAllBytes( identityStoreFile.toPath() ), "the store file must be unchanged" );
        assertEquals( 1, store.getStore().getCertificateChain( EXISTING_ALIAS ).length, "the entry in memory must still hold only its original certificate" );
        assertInMemoryMatchesFile( store );
    }

    /**
     * Verifies that a signing request reply can still be installed in an identity store when the setting is off.
     */
    @Test
    public void testIdentityStoreInstallCSRReplyWhenWritableUpdatesEntry() throws Exception
    {
        // Setup test fixture.
        setReadOnly( false );
        final IdentityStore store = new IdentityStore( configuration( identityStoreFile ), false );

        // Execute system under test.
        store.installCSRReply( EXISTING_ALIAS, toPem( existingChain.getCertificateChain() ) );

        // Verify result.
        assertEquals( KeystoreTestUtils.CHAIN_LENGTH, store.getStore().getCertificateChain( EXISTING_ALIAS ).length, "the entry in memory must hold the full chain from the reply" );
    }

    // -------------------------------------------------------------------------
    // IdentityStore.addSelfSignedDomainCertificate
    // -------------------------------------------------------------------------

    /**
     * Verifies that generating a self-signed certificate in an identity store is refused when the setting is on, and
     * leaves the store unchanged.
     */
    @Test
    public void testIdentityStoreAddSelfSignedDomainCertificateWhenReadOnlyIsRefused() throws Exception
    {
        // Setup test fixture.
        final IdentityStore store = identityStore();
        final byte[] fileBefore = Files.readAllBytes( identityStoreFile.toPath() );
        final Set<String> aliasesBefore = new HashSet<>( Collections.list( store.getStore().aliases() ) );
        setReadOnly( true );

        // Execute system under test.
        assertThrows( CertificateStoreReadOnlyException.class, store::addSelfSignedDomainCertificate,
            "generating a self-signed certificate in an identity store must be refused when the setting is on" );

        // Verify result.
        assertRefusedBeforeChange();
        assertArrayEquals( fileBefore, Files.readAllBytes( identityStoreFile.toPath() ), "the store file must be unchanged" );
        assertEquals( aliasesBefore, new HashSet<>( Collections.list( store.getStore().aliases() ) ), "no entry must have been added in memory" );
        assertInMemoryMatchesFile( store );
    }

    /**
     * Verifies that a self-signed certificate can still be generated in an identity store when the setting is off.
     */
    @Test
    public void testIdentityStoreAddSelfSignedDomainCertificateWhenWritableAddsEntry() throws Exception
    {
        // Setup test fixture.
        setReadOnly( false );
        final IdentityStore store = new IdentityStore( configuration( identityStoreFile ), false );
        final String expectedAlias = Fixtures.XMPP_DOMAIN + "_rsa";

        // Execute system under test.
        store.addSelfSignedDomainCertificate();

        // Verify result.
        assertTrue( load( identityStoreFile ).containsAlias( expectedAlias ), "the self-signed certificate must have been saved to disk" );
    }

    /**
     * Verifies that when generating a self-signed certificate is refused by persist(), the refusal reaches the caller as
     * a {@link CertificateStoreReadOnlyException} rather than wrapped in a general failure, and the store is unchanged.
     */
    @Test
    public void testAddSelfSignedDomainCertificateReportsRefusalByPersistUnwrapped() throws Exception
    {
        // Setup test fixture.
        final IdentityStore store = identityStoreThatBecomesReadOnlyOnPersist();
        final byte[] fileBefore = Files.readAllBytes( identityStoreFile.toPath() );

        // Execute system under test.
        final CertificateStoreConfigException e = assertThrows( CertificateStoreConfigException.class, store::addSelfSignedDomainCertificate, "the refusal must reach the caller" );

        // Verify result.
        assertInstanceOf( CertificateStoreReadOnlyException.class, e, "a refusal by persist() must be reported as a read-only refusal" );
        assertArrayEquals( fileBefore, Files.readAllBytes( identityStoreFile.toPath() ), "the store file must be unchanged" );
        assertInMemoryMatchesFile( store );
    }

    // -------------------------------------------------------------------------
    // IdentityStore.updateIssuerOfSelfSignedCertificates
    // -------------------------------------------------------------------------

    /**
     * Verifies that updating the issuer of the self-signed certificates in an identity store is refused when the setting
     * is on, before any certificate is re-signed in memory, and leaves the store unchanged.
     */
    @Test
    public void testIdentityStoreUpdateIssuerOfSelfSignedCertificatesWhenReadOnlyIsRefused() throws Exception
    {
        // Setup test fixture: a self-signed entry, which the update would re-sign.
        final KeystoreTestUtils.ResultHolder selfSigned = addSelfSignedEntryToIdentityStoreFile();
        final IdentityStore store = identityStore();
        final byte[] fileBefore = Files.readAllBytes( identityStoreFile.toPath() );
        setReadOnly( true );

        // Execute system under test.
        assertThrows( CertificateStoreReadOnlyException.class, () -> store.updateIssuerOfSelfSignedCertificates( "OU", "O", "City", "State", "GB" ),
            "updating the issuer of the self-signed certificates must be refused when the setting is on" );

        // Verify result.
        assertRefusedBeforeChange();
        assertArrayEquals( fileBefore, Files.readAllBytes( identityStoreFile.toPath() ), "the store file must be unchanged" );
        assertEquals( selfSigned.getCertificate(), store.getStore().getCertificate( NEW_ALIAS ), "the self-signed certificate must not have been re-signed in memory" );
        assertInMemoryMatchesFile( store );
    }

    /**
     * Verifies that the issuer of the self-signed certificates in an identity store can still be updated when the setting
     * is off.
     */
    @Test
    public void testIdentityStoreUpdateIssuerOfSelfSignedCertificatesWhenWritableReSignsEntry() throws Exception
    {
        // Setup test fixture.
        final KeystoreTestUtils.ResultHolder selfSigned = addSelfSignedEntryToIdentityStoreFile();
        setReadOnly( false );
        final IdentityStore store = new IdentityStore( configuration( identityStoreFile ), false );

        // Execute system under test.
        store.updateIssuerOfSelfSignedCertificates( "OU", "O", "City", "State", "GB" );

        // Verify result.
        assertNotEquals( selfSigned.getCertificate(), load( identityStoreFile ).getCertificate( NEW_ALIAS ), "the re-signed certificate must have been saved to disk" );
    }

    /**
     * Verifies that when updating the issuer of the self-signed certificates is refused by persist(), the refusal reaches
     * the caller as a {@link CertificateStoreReadOnlyException} rather than wrapped in a general failure, and the store
     * is unchanged.
     */
    @Test
    public void testIdentityStoreUpdateIssuerOfSelfSignedCertificatesReportsRefusalByPersistUnwrapped() throws Exception
    {
        // Setup test fixture.
        final KeystoreTestUtils.ResultHolder selfSigned = addSelfSignedEntryToIdentityStoreFile();
        final IdentityStore store = identityStoreThatBecomesReadOnlyOnPersist();
        final byte[] fileBefore = Files.readAllBytes( identityStoreFile.toPath() );

        // Execute system under test.
        final CertificateStoreConfigException e = assertThrows( CertificateStoreConfigException.class, () -> store.updateIssuerOfSelfSignedCertificates( "OU", "O", "City", "State", "GB" ),
            "the refusal must reach the caller" );

        // Verify result.
        assertInstanceOf( CertificateStoreReadOnlyException.class, e, "a refusal by persist() must be reported as a read-only refusal" );
        assertArrayEquals( fileBefore, Files.readAllBytes( identityStoreFile.toPath() ), "the store file must be unchanged" );
        assertEquals( selfSigned.getCertificate(), store.getStore().getCertificate( NEW_ALIAS ), "the re-signed certificate must have been discarded from memory" );
        assertInMemoryMatchesFile( store );
    }

    // -------------------------------------------------------------------------
    // IdentityStore.ensureDomainCertificate (used by the setup wizard)
    // -------------------------------------------------------------------------

    /**
     * Verifies that ensuring a domain certificate in an identity store that has none, when the setting is on, does not
     * generate one and does not fail (so that setup carries on), leaves the store unchanged, and logs a warning that
     * names the store file and the XMPP domain.
     */
    @Test
    public void testEnsureDomainCertificateWhenReadOnlyWithoutDomainCertificateWarnsAndSkipsGeneration() throws Exception
    {
        // Setup test fixture.
        writeEmptyIdentityStoreFile();
        final IdentityStore store = identityStore();
        final byte[] fileBefore = Files.readAllBytes( identityStoreFile.toPath() );
        assertFalse( store.containsDomainCertificate(), "precondition: the store must not contain a domain certificate" );
        setReadOnly( true );

        // Execute system under test.
        final List<LogEvent> warnings = captureWarnings( () -> assertDoesNotThrow( store::ensureDomainCertificate, "a missing domain certificate must not fail setup when the setting is on" ) );

        // Verify result.
        assertRefusedBeforeChange();
        assertArrayEquals( fileBefore, Files.readAllBytes( identityStoreFile.toPath() ), "the store file must be unchanged" );
        assertFalse( store.getStore().aliases().hasMoreElements(), "no certificate must have been generated in memory" );
        assertInMemoryMatchesFile( store );
        assertEquals( 1, warnings.size(), "exactly one warning must be logged about the missing domain certificate" );
        final String warning = warnings.get( 0 ).getMessage().getFormattedMessage();
        assertTrue( warning.contains( identityStoreFile.toString() ), "the warning must name the store file, but was: " + warning );
        assertTrue( warning.contains( Fixtures.XMPP_DOMAIN ), "the warning must name the XMPP domain, but was: " + warning );
        assertTrue( warning.contains( CertificateStoreManager.READ_ONLY.getKey() ), "the warning must name the setting that prevents generation, but was: " + warning );
    }

    /**
     * Verifies that ensuring a domain certificate in an identity store that has one, when the setting is on, changes
     * nothing and logs no warning.
     */
    @Test
    public void testEnsureDomainCertificateWhenReadOnlyWithDomainCertificateDoesNothing() throws Exception
    {
        // Setup test fixture.
        final IdentityStore store = identityStore();
        final byte[] fileBefore = Files.readAllBytes( identityStoreFile.toPath() );
        final Set<String> aliasesBefore = new HashSet<>( Collections.list( store.getStore().aliases() ) );
        assertTrue( store.containsDomainCertificate(), "precondition: the store must contain a domain certificate" );
        setReadOnly( true );

        // Execute system under test.
        final List<LogEvent> warnings = captureWarnings( () -> assertDoesNotThrow( store::ensureDomainCertificate, "a store with a domain certificate must not fail setup when the setting is on" ) );

        // Verify result.
        assertFalse( persistReached, "no change must be attempted when the store already has a domain certificate" );
        assertArrayEquals( fileBefore, Files.readAllBytes( identityStoreFile.toPath() ), "the store file must be unchanged" );
        assertEquals( aliasesBefore, new HashSet<>( Collections.list( store.getStore().aliases() ) ), "the entries in memory must be unchanged" );
        assertTrue( warnings.isEmpty(), "no warning must be logged when the store has a domain certificate" );
    }

    /**
     * Verifies that ensuring a domain certificate in an identity store that has none, when the setting is off, still
     * generates a self-signed certificate and saves it.
     */
    @Test
    public void testEnsureDomainCertificateWhenWritableWithoutDomainCertificateGeneratesOne() throws Exception
    {
        // Setup test fixture.
        writeEmptyIdentityStoreFile();
        setReadOnly( false );
        final IdentityStore store = new IdentityStore( configuration( identityStoreFile ), false );

        // Execute system under test.
        store.ensureDomainCertificate();

        // Verify result.
        assertTrue( store.containsDomainCertificate(), "a domain certificate must have been generated" );
        assertTrue( load( identityStoreFile ).containsAlias( Fixtures.XMPP_DOMAIN + "_rsa" ), "the generated certificate must have been saved to disk" );
    }

    // -------------------------------------------------------------------------
    // The exception
    // -------------------------------------------------------------------------

    /**
     * Verifies that a refusal by persist() reaches the caller of TrustStore.installCertificate unwrapped, and leaves
     * the store unchanged.
     */
    @Test
    public void testTrustStoreInstallCertificateReportsRefusalByPersistUnwrapped() throws Exception
    {
        // Setup test fixture.
        final TrustStore store = trustStoreThatBecomesReadOnlyOnPersist();
        final byte[] fileBefore = Files.readAllBytes( trustStoreFile.toPath() );
        final String pem = KeystoreTestUtils.toPemFormat( KeystoreTestUtils.generateSelfSignedCertificate().getCertificate() );

        // Execute system under test.
        final CertificateStoreConfigException e = assertThrows( CertificateStoreConfigException.class, () -> store.installCertificate( NEW_ALIAS, pem ), "the refusal must reach the caller" );

        // Verify result.
        assertInstanceOf( CertificateStoreReadOnlyException.class, e, "a refusal by persist() must be reported as a read-only refusal" );
        assertArrayEquals( fileBefore, Files.readAllBytes( trustStoreFile.toPath() ), "the store file must be unchanged" );
        assertInMemoryMatchesFile( store );
    }

    /**
     * Verifies that a refusal by persist() reaches the caller of IdentityStore.replaceCertificate unwrapped, and leaves
     * the store unchanged (in particular, the existing domain entry is not removed).
     */
    @Test
    public void testIdentityStoreReplaceCertificateReportsRefusalByPersistUnwrapped() throws Exception
    {
        // Setup test fixture.
        final IdentityStore store = identityStoreThatBecomesReadOnlyOnPersist();
        final byte[] fileBefore = Files.readAllBytes( identityStoreFile.toPath() );
        final KeystoreTestUtils.ResultHolder chain = KeystoreTestUtils.generateValidCertificateChain( Fixtures.XMPP_DOMAIN );

        // Execute system under test.
        final CertificateStoreConfigException e = assertThrows( CertificateStoreConfigException.class, () -> store.replaceCertificate( toPem( chain.getCertificateChain() ), toPem( chain.getKeyPair().getPrivate() ), null ),
            "the refusal must reach the caller" );

        // Verify result.
        assertInstanceOf( CertificateStoreReadOnlyException.class, e, "a refusal by persist() must be reported as a read-only refusal" );
        assertArrayEquals( fileBefore, Files.readAllBytes( identityStoreFile.toPath() ), "the store file must be unchanged" );
        assertTrue( store.getStore().containsAlias( EXISTING_ALIAS ), "the existing domain entry must be back in memory" );
        assertInMemoryMatchesFile( store );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void setReadOnly( final boolean readOnly )
    {
        CertificateStoreManager.READ_ONLY.setValue( readOnly );
    }

    /**
     * A trust store that records whether {@link CertificateStore#persist()} was reached. persist() refuses when the
     * setting is on and reloads the store, so the file and the in-memory store end up unchanged even when a method fails
     * to check first; that persist() is never reached shows the method refused before changing the store in memory.
     */
    private TrustStore trustStore() throws CertificateStoreConfigException
    {
        return new TrustStore( configuration( trustStoreFile ), false )
        {
            @Override
            public void persist() throws CertificateStoreConfigException
            {
                persistReached = true;
                super.persist();
            }
        };
    }

    /**
     * An identity store that records whether {@link CertificateStore#persist()} was reached (see {@link #trustStore()}).
     */
    private IdentityStore identityStore() throws CertificateStoreConfigException
    {
        return new IdentityStore( configuration( identityStoreFile ), false )
        {
            @Override
            public void persist() throws CertificateStoreConfigException
            {
                persistReached = true;
                super.persist();
            }
        };
    }

    /**
     * A trust store, created with the setting off, that turns the setting on when persist() is called. This exercises
     * the refusal by persist() that backs up the check at the top of each method (as when the setting changes while a
     * change is in progress).
     */
    private TrustStore trustStoreThatBecomesReadOnlyOnPersist() throws CertificateStoreConfigException
    {
        return new TrustStore( configuration( trustStoreFile ), false )
        {
            @Override
            public void persist() throws CertificateStoreConfigException
            {
                setReadOnly( true );
                super.persist();
            }
        };
    }

    /**
     * An identity store, created with the setting off, that turns the setting on when persist() is called (see
     * {@link #trustStoreThatBecomesReadOnlyOnPersist()}).
     */
    private IdentityStore identityStoreThatBecomesReadOnlyOnPersist() throws CertificateStoreConfigException
    {
        return new IdentityStore( configuration( identityStoreFile ), false )
        {
            @Override
            public void persist() throws CertificateStoreConfigException
            {
                setReadOnly( true );
                super.persist();
            }
        };
    }

    /**
     * Adds a self-signed private key entry for the XMPP domain, under {@link #NEW_ALIAS}, to the identity store file.
     */
    private KeystoreTestUtils.ResultHolder addSelfSignedEntryToIdentityStoreFile() throws Exception
    {
        final KeyStore keyStore = load( identityStoreFile );
        final KeystoreTestUtils.ResultHolder selfSigned = KeystoreTestUtils.generateSelfSignedCertificate( Fixtures.XMPP_DOMAIN );
        keyStore.setKeyEntry( NEW_ALIAS, selfSigned.getKeyPair().getPrivate(), PASSWORD, selfSigned.getCertificateChain() );
        try ( final FileOutputStream os = new FileOutputStream( identityStoreFile ) )
        {
            keyStore.store( os, PASSWORD );
        }
        return selfSigned;
    }

    /**
     * Replaces the identity store file with an empty store, which has no certificate for the XMPP domain.
     */
    private void writeEmptyIdentityStoreFile() throws Exception
    {
        final KeyStore empty = KeyStore.getInstance( STORE_TYPE );
        empty.load( null, PASSWORD );
        try ( final FileOutputStream os = new FileOutputStream( identityStoreFile ) )
        {
            empty.store( os, PASSWORD );
        }
    }

    /**
     * Runs the action and returns the warnings that {@link IdentityStore} logged while it ran.
     */
    private static List<LogEvent> captureWarnings( final Runnable action )
    {
        final org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger( IdentityStore.class );
        final CapturingAppender appender = new CapturingAppender();
        appender.start();
        logger.addAppender( appender );
        try
        {
            action.run();
        }
        finally
        {
            logger.removeAppender( appender );
            appender.stop();
        }
        return appender.events.stream()
            .filter( event -> event.getLevel() == Level.WARN )
            .collect( Collectors.toList() );
    }

    /**
     * A log4j appender that keeps the events it receives, so that a test can check what was logged.
     */
    private static class CapturingAppender extends AbstractAppender
    {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        CapturingAppender()
        {
            super( "CertificateStoreReadOnlyTest", null, null, true, Property.EMPTY_ARRAY );
        }

        @Override
        public void append( final LogEvent event )
        {
            events.add( event.toImmutable() );
        }
    }

    private void assertRefusedBeforeChange()
    {
        assertFalse( persistReached, "the change must be refused before the store is changed in memory, rather than left to persist()" );
    }

    private CertificateStoreConfiguration configuration( final File file )
    {
        return new CertificateStoreConfiguration( STORE_TYPE, file, PASSWORD, tempDir.toFile() );
    }

    private static KeyStore load( final File file ) throws Exception
    {
        final KeyStore result = KeyStore.getInstance( STORE_TYPE );
        try ( final FileInputStream is = new FileInputStream( file ) )
        {
            result.load( is, PASSWORD );
        }
        return result;
    }

    /**
     * Asserts that the in-memory store holds the same entries, with the same certificate chains, as the file on disk.
     */
    private static void assertInMemoryMatchesFile( final CertificateStore store ) throws Exception
    {
        final KeyStore inMemory = store.getStore();
        final KeyStore onDisk = load( store.getConfiguration().getFile() );
        final Set<String> inMemoryAliases = new HashSet<>( Collections.list( inMemory.aliases() ) );
        assertEquals( new HashSet<>( Collections.list( onDisk.aliases() ) ), inMemoryAliases, "the in-memory store must hold the same entries as the file" );
        for ( final String alias : inMemoryAliases )
        {
            final Certificate[] chain = inMemory.getCertificateChain( alias );
            if ( chain != null )
            {
                assertArrayEquals( onDisk.getCertificateChain( alias ), chain, "the in-memory chain of '" + alias + "' must match the file" );
            }
            else
            {
                assertEquals( onDisk.getCertificate( alias ), inMemory.getCertificate( alias ), "the in-memory certificate of '" + alias + "' must match the file" );
            }
        }
    }

    private static String toPem( final X509Certificate[] chain ) throws Exception
    {
        final StringBuilder sb = new StringBuilder();
        for ( final X509Certificate certificate : chain )
        {
            sb.append( KeystoreTestUtils.toPemFormat( certificate ) ).append( '\n' );
        }
        return sb.toString();
    }

    private static String toPem( final PrivateKey privateKey ) throws Exception
    {
        final StringWriter writer = new StringWriter();
        try ( final JcaPEMWriter pemWriter = new JcaPEMWriter( writer ) )
        {
            pemWriter.writeObject( privateKey );
        }
        return writer.toString();
    }
}

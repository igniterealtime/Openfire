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

package org.jivesoftware.admin;

import org.jivesoftware.openfire.keystore.CertificateStoreConfigException;
import org.jivesoftware.openfire.keystore.CertificateStoreConfiguration;
import org.jivesoftware.openfire.keystore.TrustStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CertificateStorePasswordField}.
 *
 * @author Matthew Vivian
 */
public class CertificateStorePasswordFieldTest
{
    private static final char[] CURRENT = "current-secret".toCharArray();

    @Test
    public void testEmptySubmissionKeepsCurrentPassword()
    {
        final char[] result = CertificateStorePasswordField.passwordToSave( "", CURRENT );

        assertArrayEquals( CURRENT, result, "an empty password field must keep the store's current password" );
    }

    @Test
    public void testMissingSubmissionKeepsCurrentPassword()
    {
        final char[] result = CertificateStorePasswordField.passwordToSave( null, CURRENT );

        assertArrayEquals( CURRENT, result, "a password field absent from the request must keep the store's current password" );
    }

    @Test
    public void testNonEmptySubmissionReplacesCurrentPassword()
    {
        final char[] result = CertificateStorePasswordField.passwordToSave( "new-secret", CURRENT );

        assertArrayEquals( "new-secret".toCharArray(), result, "a non-empty password field must replace the store's current password" );
    }

    @Test
    public void testSubmissionIsUsedVerbatim()
    {
        final char[] result = CertificateStorePasswordField.passwordToSave( " spaced ", CURRENT );

        assertArrayEquals( " spaced ".toCharArray(), result, "a submitted password must be saved exactly as typed, as before this change" );
    }

    @Test
    public void testMissingCurrentPasswordIsRejected()
    {
        assertThrows( NullPointerException.class, () -> CertificateStorePasswordField.passwordToSave( "", null ), "a store configuration always has a password, so a missing one is a programming error" );
    }

    @Test
    public void testEmptySubmissionKeepsEmptyCurrentPassword()
    {
        final char[] result = CertificateStorePasswordField.passwordToSave( "", new char[0] );

        assertArrayEquals( new char[0], result, "a store configured without a password must stay that way when its password is not changed" );
    }

    @Test
    public void testNonEmptyPasswordIsSet()
    {
        assertTrue( CertificateStorePasswordField.isSet( CURRENT ), "a store with a non-empty password must be shown as having one" );
    }

    @Test
    public void testEmptyPasswordIsNotSet()
    {
        assertFalse( CertificateStorePasswordField.isSet( new char[0] ), "a store with an empty password must be shown as having none" );
    }

    @Test
    public void testMissingPasswordIsNotSet()
    {
        assertFalse( CertificateStorePasswordField.isSet( null ), "a store without a password must be shown as having none" );
    }

    @Test
    public void testNonEmptySubmissionSetsPasswordOnStoreWithoutOne()
    {
        final char[] result = CertificateStorePasswordField.passwordToSave( "new-secret", new char[0] );

        assertArrayEquals( "new-secret".toCharArray(), result, "a non-empty password field must set a password on a store that has none" );
    }

    @ParameterizedTest
    @ValueSource( strings = { "JKS", "PKCS12" } )
    public void testStoreOpenedWithWrongPasswordIsReportedAsWrongPassword( final String storeType, @TempDir final Path dir ) throws Exception
    {
        // Arrange
        final File file = writeEmptyStore( dir, storeType, "changeit" );

        // Act
        final CertificateStoreConfigException failure = openFailure( storeType, file, "wrong-password", dir );

        // Assert
        assertTrue( CertificateStorePasswordField.isWrongPassword( failure ), "a " + storeType + " store opened with the wrong password must be reported as a wrong password" );
    }

    @ParameterizedTest
    @ValueSource( strings = { "JKS", "PKCS12" } )
    public void testCorruptStoreIsNotReportedAsWrongPassword( final String storeType, @TempDir final Path dir ) throws Exception
    {
        // Arrange
        final File file = dir.resolve( "corrupt" ).toFile();
        Files.write( file.toPath(), new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 } );

        // Act
        final CertificateStoreConfigException failure = openFailure( storeType, file, "changeit", dir );

        // Assert
        assertFalse( CertificateStorePasswordField.isWrongPassword( failure ), "a corrupt " + storeType + " store must not be reported as a wrong password" );
    }

    @Test
    public void testMissingStoreFileIsNotReportedAsWrongPassword( @TempDir final Path dir )
    {
        final CertificateStoreConfigException failure = openFailure( "JKS", dir.resolve( "missing" ).toFile(), "changeit", dir );

        assertFalse( CertificateStorePasswordField.isWrongPassword( failure ), "a missing store file must not be reported as a wrong password" );
    }

    @Test
    public void testKeyPasswordMismatchIsNotReportedAsWrongPassword()
    {
        // IdentityStore wraps an UnrecoverableKeyException from its key manager directly, when a key's password differs
        // from the store's password. The store password is right in that case, so it must keep its own message.
        final Exception failure = new CertificateStoreConfigException( "Unable to initialize identity store", new UnrecoverableKeyException( "Cannot recover key" ) );

        assertFalse( CertificateStorePasswordField.isWrongPassword( failure ), "a key whose password differs from the store's must not be reported as a wrong store password" );
    }

    @Test
    public void testWrongEnteredPasswordIsClassifiedAsWrongPasswordEntered( @TempDir final Path dir ) throws Exception
    {
        final CertificateStoreConfigException failure = openFailure( "JKS", writeEmptyStore( dir, "JKS", "changeit" ), "wrong-password", dir );

        assertEquals( CertificateStorePasswordField.SaveFailure.WRONG_PASSWORD_ENTERED, CertificateStorePasswordField.classifyFailure( "wrong-password", failure ), "a password the administrator entered that does not open the file must be reported as such" );
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testCurrentPasswordThatDoesNotOpenFileIsClassifiedAsSuch( final String submitted, @TempDir final Path dir ) throws Exception
    {
        final CertificateStoreConfigException failure = openFailure( "JKS", writeEmptyStore( dir, "JKS", "changeit" ), "current-password", dir );

        assertEquals( CertificateStorePasswordField.SaveFailure.CURRENT_PASSWORD_DOES_NOT_OPEN_FILE, CertificateStorePasswordField.classifyFailure( submitted, failure ), "when no password was entered, the failure must be blamed on the current password, not on one the administrator typed" );
    }

    @Test
    public void testOtherFailureIsClassifiedAsOther( @TempDir final Path dir )
    {
        final CertificateStoreConfigException failure = openFailure( "JKS", dir.resolve( "missing" ).toFile(), "changeit", dir );

        assertEquals( CertificateStorePasswordField.SaveFailure.OTHER, CertificateStorePasswordField.classifyFailure( "changeit", failure ), "a failure that is not a wrong password must keep the generic message, even when a password was entered" );
    }

    @Test
    public void testFailureWithoutCauseIsNotReportedAsWrongPassword()
    {
        assertFalse( CertificateStorePasswordField.isWrongPassword( new CertificateStoreConfigException( "Unable to load store" ) ), "a failure with no cause must not be reported as a wrong password" );
    }

    @Test
    public void testNullFailureIsNotReportedAsWrongPassword()
    {
        assertFalse( CertificateStorePasswordField.isWrongPassword( null ), "a null failure must not be reported as a wrong password" );
    }

    private static File writeEmptyStore( final Path dir, final String storeType, final String password ) throws Exception
    {
        final File file = dir.resolve( "store" ).toFile();
        final KeyStore store = KeyStore.getInstance( storeType );
        store.load( null, password.toCharArray() );
        try ( final OutputStream out = new FileOutputStream( file ) )
        {
            store.store( out, password.toCharArray() );
        }
        return file;
    }

    /**
     * Opens a store as the admin console does when it saves a store's settings, and returns the resulting failure.
     */
    private static CertificateStoreConfigException openFailure( final String storeType, final File file, final String password, final Path backupDir )
    {
        final CertificateStoreConfiguration configuration = new CertificateStoreConfiguration( storeType, file, password.toCharArray(), backupDir.toFile() );
        return assertThrows( CertificateStoreConfigException.class, () -> new TrustStore( configuration, false ), "opening the " + storeType + " store was expected to fail" );
    }
}

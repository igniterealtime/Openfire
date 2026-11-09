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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    public void testStoreOpenedWithWrongPasswordIsReportedAsWrongPassword( final String storeType ) throws Exception
    {
        final Exception failure = loadFailure( storeType, storeBytes( storeType, "changeit" ), "wrong-password" );

        assertTrue( CertificateStorePasswordField.isWrongPassword( failure ), "a " + storeType + " store opened with the wrong password must be reported as a wrong password" );
    }

    @ParameterizedTest
    @ValueSource( strings = { "JKS", "PKCS12" } )
    public void testCorruptStoreIsNotReportedAsWrongPassword( final String storeType )
    {
        final Exception failure = loadFailure( storeType, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 }, "changeit" );

        assertFalse( CertificateStorePasswordField.isWrongPassword( failure ), "a corrupt " + storeType + " store must not be reported as a wrong password" );
    }

    @Test
    public void testMissingStoreFileIsNotReportedAsWrongPassword()
    {
        final Exception failure = new CertificateStoreConfigException( "Unable to load store", new FileNotFoundException( "no such file" ) );

        assertFalse( CertificateStorePasswordField.isWrongPassword( failure ), "a missing store file must not be reported as a wrong password" );
    }

    @Test
    public void testFailureWithoutCauseIsNotReportedAsWrongPassword()
    {
        assertFalse( CertificateStorePasswordField.isWrongPassword( new CertificateStoreConfigException( "Unable to load store" ) ), "a failure with no cause must not be reported as a wrong password" );
    }

    @Test
    public void testMissingFailureIsNotReportedAsWrongPassword()
    {
        assertFalse( CertificateStorePasswordField.isWrongPassword( null ), "no failure must not be reported as a wrong password" );
    }

    private static byte[] storeBytes( final String storeType, final String password ) throws Exception
    {
        final KeyStore store = KeyStore.getInstance( storeType );
        store.load( null, password.toCharArray() );
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        store.store( out, password.toCharArray() );
        return out.toByteArray();
    }

    /**
     * Loads a store the way CertificateStore does and returns the failure wrapped as CertificateStore wraps it.
     */
    private static Exception loadFailure( final String storeType, final byte[] content, final String password )
    {
        try
        {
            KeyStore.getInstance( storeType ).load( new ByteArrayInputStream( content ), password.toCharArray() );
        }
        catch ( IOException | java.security.GeneralSecurityException ex )
        {
            return new CertificateStoreConfigException( "Unable to load store of type '" + storeType + "'", ex );
        }
        throw new AssertionError( "Loading the " + storeType + " store was expected to fail." );
    }
}

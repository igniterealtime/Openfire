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

import org.junit.jupiter.api.Test;

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
}

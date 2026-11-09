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

import java.util.Objects;

/**
 * Interprets the password field of a certificate store form in the admin console.
 *
 * The admin console never sends a store's current password to the browser. The form offers an "Update password" link
 * ("Set password" when the store has none), which reveals an empty password field. That field is submitted only once it
 * has been revealed, so a missing or empty field means that the administrator did not change the password, and the
 * store's current password is kept. A non-empty field replaces the current password. An empty field never clears it.
 *
 * @author Matthew Vivian
 */
public final class CertificateStorePasswordField
{
    private CertificateStorePasswordField()
    {
    }

    /**
     * Indicates whether a certificate store has a password, without revealing anything else about it. The admin
     * console uses this (through the admin tag library) to show "set" or "not set".
     *
     * This is a Java method rather than an EL expression because the EL 'empty' operator does not treat an empty
     * char array as empty.
     *
     * @param password the store's current password (can be null).
     * @return true when the password is not null and not empty.
     */
    public static boolean isSet( final char[] password )
    {
        return password != null && password.length > 0;
    }

    /**
     * Determines the password to save for a certificate store.
     *
     * @param submitted the value of the password field as submitted by the browser (null when it was not submitted).
     * @param current the store's current password, which is empty when the store has none (cannot be null: a store
     *                configuration always has a password, falling back to a default).
     * @return the submitted password when it is not empty, otherwise the current password (never null).
     */
    public static char[] passwordToSave( final String submitted, final char[] current )
    {
        Objects.requireNonNull( current, "Argument 'current' cannot be null." );
        if ( submitted != null && !submitted.isEmpty() )
        {
            return submitted.toCharArray();
        }
        return current;
    }
}

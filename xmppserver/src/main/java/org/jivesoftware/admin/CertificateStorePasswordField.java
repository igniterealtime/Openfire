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

import java.io.IOException;
import java.security.UnrecoverableKeyException;
import java.util.Objects;

/**
 * Interprets the password field of a certificate store form in the admin console.
 *
 * The field holds the password Openfire uses to open the store file. Saving it does not re-encrypt the file.
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
     * console uses this (through the admin tag library) to choose between "Update password" and "Set password".
     *
     * This is a Java method rather than an EL expression because the 'empty' operator of the EL implementation that
     * Openfire uses (Jasper) treats only arrays of objects as empty when they have no elements, so an empty char array
     * is not empty to it.
     *
     * @param password the store's current password (can be null).
     * @return true when the password is not null and not empty.
     */
    public static boolean isSet( final char[] password )
    {
        return password != null && password.length > 0;
    }

    /**
     * Indicates whether a certificate store failed to load because the password does not open the file, so that the
     * admin console can say so instead of showing the raw failure. Other failures, such as a missing or corrupt file,
     * are not reported as a wrong password.
     *
     * The JKS and PKCS12 implementations report a wrong password from KeyStore.load as an IOException caused by an
     * UnrecoverableKeyException, so this looks for that pair anywhere in the chain of causes. An
     * UnrecoverableKeyException on its own is not enough: IdentityStore also reports one, unwrapped, when the password
     * of a key differs from the password of the store, and the store password is right in that case.
     *
     * @param failure the failure to load the store (can be null).
     * @return true when the failure was caused by a wrong password.
     */
    public static boolean isWrongPassword( final Throwable failure )
    {
        for ( Throwable cause = failure; cause != null; cause = cause.getCause() )
        {
            if ( cause instanceof IOException && cause.getCause() instanceof UnrecoverableKeyException )
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The reasons the admin console distinguishes when saving a store's settings fails.
     */
    public enum SaveFailure
    {
        /**
         * The password the administrator entered does not open the store file.
         */
        WRONG_PASSWORD_ENTERED,

        /**
         * No password was entered, and the store's current password does not open the file (typically after the
         * store was moved to a file that has a different password).
         */
        CURRENT_PASSWORD_DOES_NOT_OPEN_FILE,

        /**
         * Any other failure, such as a missing or corrupt file.
         */
        OTHER
    }

    /**
     * Determines why saving a store's settings failed, so that the admin console can say whether the password the
     * administrator entered, or the store's current password, does not open the file.
     *
     * @param submitted the value of the store's password field as submitted by the browser (null when it was not
     *                  submitted).
     * @param failure the failure to replace the store (can be null).
     * @return the reason for the failure (never null).
     */
    public static SaveFailure classifyFailure( final String submitted, final Throwable failure )
    {
        if ( !isWrongPassword( failure ) )
        {
            return SaveFailure.OTHER;
        }
        return isEntered( submitted ) ? SaveFailure.WRONG_PASSWORD_ENTERED : SaveFailure.CURRENT_PASSWORD_DOES_NOT_OPEN_FILE;
    }

    private static boolean isEntered( final String submitted )
    {
        return submitted != null && !submitted.isEmpty();
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
        if ( isEntered( submitted ) )
        {
            return submitted.toCharArray();
        }
        return current;
    }
}

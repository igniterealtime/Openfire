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
package org.jivesoftware.openfire.auth;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Blowfish;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Re-encrypts the user passwords in {@code ofUser.encryptedPassword} from one Blowfish key derivation function (KDF) to
 * another. Only that column is ever changed.
 *
 * Decrypting with a key from the wrong KDF does not fail, but yields a wrong value. While the configured KDF is still
 * the source KDF, every stored password uses it, so {@link #reencryptAll} can re-encrypt all of them. Afterwards,
 * stored passwords may use either KDF (OF-3374), so {@link #reencryptVerified} only re-encrypts a password that matches
 * the SCRAM credentials of its user when decrypted with the source KDF.
 *
 * A row is only updated if it still holds the value that was read, so a concurrently changed password is never
 * overwritten.
 *
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3374">OF-3374</a>
 */
public final class EncryptedPasswordMigration
{
    private static final Logger Log = LoggerFactory.getLogger(EncryptedPasswordMigration.class);

    private static final String LOAD_FIRST_ENCRYPTED_PASSWORDS =
        "SELECT username, encryptedPassword FROM ofUser WHERE encryptedPassword IS NOT NULL ORDER BY username";

    private static final String LOAD_NEXT_ENCRYPTED_PASSWORDS =
        "SELECT username, encryptedPassword FROM ofUser WHERE encryptedPassword IS NOT NULL AND username > ? ORDER BY username";

    private static final String LOAD_SCRAM_CREDENTIALS =
        "SELECT mechanism, iterations, salt, storedKey, serverKey FROM ofUserScram WHERE username=?";

    private static final String ANY_ENCRYPTED_PASSWORD =
        "SELECT username FROM ofUser WHERE encryptedPassword IS NOT NULL";

    private static final String UPDATE_ENCRYPTED_PASSWORD_IF_UNCHANGED =
        "UPDATE ofUser SET encryptedPassword=? WHERE username=? AND encryptedPassword=?";

    private static final Duration PROGRESS_LOG_INTERVAL = Duration.ofSeconds(5);

    /** The number of stored passwords that is read into memory at a time. */
    private static final int PAGE_SIZE = 500;

    /** The maximum number of usernames that is written to a single log line. */
    private static final int NAMES_PER_LOG_LINE = 100;

    /** Caches that at least one encrypted password is stored, which does not change back other than by deleting users. */
    private static volatile boolean encryptedPasswordsExist = false;

    /**
     * Prevents instantiation: this class only provides static methods.
     */
    private EncryptedPasswordMigration() {}

    /**
     * The outcome of re-encrypting stored user passwords. Each user whose password was left unchanged is in exactly one
     * of the lists. {@link #unverified()} and {@link #unverifiable()} are only used by verified re-encryption.
     *
     * @param reencrypted          the number of passwords that were re-encrypted
     * @param unverified           users whose password did not match their SCRAM credentials (typically: it already uses the target KDF)
     * @param unverifiable         users without a SCRAM credential to verify their password against
     * @param undecryptable        users whose stored password did not decrypt to a usable value
     * @param modifiedConcurrently users whose password was changed while the operation ran
     */
    public record Result(int reencrypted, @Nonnull List<String> unverified, @Nonnull List<String> unverifiable, @Nonnull List<String> undecryptable, @Nonnull List<String> modifiedConcurrently) {}

    /**
     * Determines whether the admin console should offer to re-encrypt stored passwords that may still use SHA1.
     *
     * Beyond {@link JiveGlobals#isPasswordReencryptionNeeded()}, this checks that any password is stored encrypted at
     * all. If none is, that is recorded, as every password that is encrypted from then on uses the configured KDF.
     *
     * @return true if stored user passwords may need to be re-encrypted, otherwise false
     */
    public static boolean isRepairNeeded()
    {
        if (!JiveGlobals.isPasswordReencryptionNeeded()) {
            return false;
        }
        if (encryptedPasswordsExist) {
            return true;
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ANY_ENCRYPTED_PASSWORD);
            DbConnectionManager.setMaxRows(pstmt, 1);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                encryptedPasswordsExist = true;
                return true;
            }
        } catch (SQLException e) {
            // Err on the side of caution: this cannot rule out that passwords need to be re-encrypted.
            Log.warn("Unable to determine whether any user password is stored encrypted.", e);
            return true;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        Log.info("No user password is stored encrypted, so none needs to be re-encrypted.");
        JiveGlobals.setPasswordsReencrypted(true);
        return false;
    }

    /**
     * Re-encrypts every stored user password, assuming that all of them use {@code sourceKdf}. That only holds while
     * {@code sourceKdf} is the configured KDF: re-encrypting a password that already uses {@code targetKdf} destroys it.
     *
     * Invoke this, and change the configured KDF, from within {@link AuthFactory#replacePasswordCipher}. Committing is
     * left to the caller.
     *
     * @param con       the database connection to use
     * @param sourceKdf the KDF that every stored password uses
     * @param targetKdf the KDF to re-encrypt with
     * @return the outcome of the operation
     * @throws SQLException if the stored passwords could not be read or updated
     */
    @Nonnull
    public static Result reencryptAll(@Nonnull final Connection con, @Nonnull final String sourceKdf, @Nonnull final String targetKdf) throws SQLException
    {
        return reencrypt(con, sourceKdf, targetKdf, false);
    }

    /**
     * Re-encrypts the stored user passwords that, decrypted with {@code sourceKdf}, match the SCRAM credentials of their
     * user. Any other password is left unchanged. Committing is left to the caller.
     *
     * @param con       the database connection to use
     * @param sourceKdf the KDF that the passwords to re-encrypt use
     * @param targetKdf the KDF to re-encrypt with
     * @return the outcome of the operation
     * @throws SQLException if the stored passwords could not be read or updated
     */
    @Nonnull
    public static Result reencryptVerified(@Nonnull final Connection con, @Nonnull final String sourceKdf, @Nonnull final String targetKdf) throws SQLException
    {
        return reencrypt(con, sourceKdf, targetKdf, true);
    }

    /**
     * Creates the ciphers for both KDFs, checks that their keys differ, and re-encrypts stored user passwords.
     *
     * @param con       the database connection to use
     * @param sourceKdf the KDF that the passwords to re-encrypt use
     * @param targetKdf the KDF to re-encrypt with
     * @param verify    whether to only re-encrypt passwords that match the SCRAM credentials of their user
     * @return the outcome of the operation
     * @throws SQLException if the stored passwords could not be read or updated
     * @throws IllegalArgumentException if both KDFs are the same
     * @throws IllegalStateException if both KDFs yield the same key (PBKDF2 falls back to SHA1 when it fails)
     */
    @Nonnull
    private static Result reencrypt(@Nonnull final Connection con, @Nonnull final String sourceKdf, @Nonnull final String targetKdf, final boolean verify) throws SQLException
    {
        if (sourceKdf.equalsIgnoreCase(targetKdf)) {
            throw new IllegalArgumentException("The source and target KDF are the same: " + sourceKdf);
        }

        final Blowfish source = AuthFactory.createPasswordCipher(sourceKdf);
        final Blowfish target = AuthFactory.createPasswordCipher(targetKdf);
        if (source == null || target == null) {
            Log.info("No password key is configured, so there are no encrypted user passwords to re-encrypt.");
            return new Result(0, List.of(), List.of(), List.of(), List.of());
        }

        // Blowfish falls back to SHA1 when it cannot derive a key using PBKDF2 (for example, when the salt cannot be
        // obtained). Re-encrypting with such a key would silently leave passwords encrypted with the source KDF.
        final String probe = StringUtils.randomString(16);
        if (probe.equals(source.decryptString(target.encryptString(probe)))) {
            throw new IllegalStateException("The keys that are derived for KDF '" + sourceKdf + "' and KDF '" + targetKdf + "' are identical. Refusing to re-encrypt user passwords.");
        }

        return reencrypt(con, source, target, verify);
    }

    /**
     * Re-encrypts stored user passwords from the {@code source} cipher to the {@code target} cipher.
     *
     * @param con    the database connection to use
     * @param source the cipher that the passwords to re-encrypt use
     * @param target the cipher to re-encrypt with
     * @param verify whether to only re-encrypt passwords that match the SCRAM credentials of their user
     * @return the outcome of the operation
     * @throws SQLException if the stored passwords could not be read or updated
     */
    @Nonnull
    static Result reencrypt(@Nonnull final Connection con, @Nonnull final Blowfish source, @Nonnull final Blowfish target, final boolean verify) throws SQLException
    {
        Log.info("Re-encrypting stored user passwords ({}).", verify ? "only those that verify against SCRAM credentials" : "all of them");

        int reencrypted = 0;
        final List<String> unverified = new ArrayList<>();
        final List<String> unverifiable = new ArrayList<>();
        final List<String> undecryptable = new ArrayList<>();
        final List<String> modifiedConcurrently = new ArrayList<>();

        int evaluated = 0;
        Instant lastLogTime = Instant.now();

        // Walk the stored passwords in pages, ordered by username, continuing after the last user of the previous page.
        String username = null;
        List<String[]> page;
        while (!(page = loadPageOfEncryptedPasswords(con, username)).isEmpty()) {
            for (final String[] row : page) {
                username = row[0];
                final String encryptedPassword = row[1];

                // Determine the plaintext to re-encrypt, or null when this row is to be left unchanged.
                final String plaintext;
                if (!verify) {
                    plaintext = decrypt(username, encryptedPassword, source);
                    if (plaintext == null) {
                        undecryptable.add(username);
                    }
                } else {
                    final List<ScramCredentialData> credentials = loadScramCredentials(con, username);
                    if (credentials.isEmpty()) {
                        plaintext = null;
                        unverifiable.add(username);
                    } else {
                        final String candidate = decrypt(username, encryptedPassword, source);
                        if (candidate != null && DefaultAuthProvider.matchesAnyScramCredential(username, candidate, credentials)) {
                            plaintext = candidate;
                        } else {
                            plaintext = null;
                            unverified.add(username);
                        }
                    }
                }

                if (plaintext != null) {
                    if (updateIfUnchanged(con, username, encryptedPassword, target.encryptString(plaintext))) {
                        reencrypted++;
                    } else {
                        modifiedConcurrently.add(username);
                    }
                }

                evaluated++;
                final Instant now = Instant.now();
                if (Duration.between(lastLogTime, now).compareTo(PROGRESS_LOG_INTERVAL) >= 0) {
                    Log.info("Re-encryption progress: {} stored user passwords evaluated.", evaluated);
                    lastLogTime = now;
                }
            }
        }

        final Result result = new Result(reencrypted, List.copyOf(unverified), List.copyOf(unverifiable), List.copyOf(undecryptable), List.copyOf(modifiedConcurrently));
        Log.info("Re-encryption of stored user passwords complete: {} re-encrypted, {} not verified, {} without a SCRAM credential, {} that did not decrypt, {} changed while this operation ran.",
            result.reencrypted(), result.unverified().size(), result.unverifiable().size(), result.undecryptable().size(), result.modifiedConcurrently().size());
        logUsers(names -> Log.info("The stored password of these users did not verify against their SCRAM credentials, and was left unchanged. This is expected for passwords that are already encrypted with the target KDF: {}", names),
            result.unverified());
        logUsers(names -> Log.warn("The stored password of these users cannot be verified, as they have no SCRAM credential. It was left unchanged, and may need to be reset by an administrator: {}", names),
            result.unverifiable());
        logUsers(names -> Log.warn("The stored password of these users did not decrypt to a usable value, and was left unchanged. It may need to be reset by an administrator: {}", names),
            result.undecryptable());
        logUsers(names -> Log.warn("The stored password of these users was changed while this operation ran, and was therefore left unchanged: {}", names),
            result.modifiedConcurrently());
        return result;
    }

    /**
     * Decrypts a stored password. A wrong key usually yields a wrong value rather than a failure.
     *
     * @param username          the user that the password belongs to (for logging only)
     * @param encryptedPassword the stored password
     * @param cipher            the cipher to decrypt with
     * @return the decrypted value, or {@code null} if it is not usable
     */
    @Nullable
    static String decrypt(@Nonnull final String username, @Nonnull final String encryptedPassword, @Nonnull final Blowfish cipher)
    {
        final String candidate;
        try {
            candidate = cipher.decryptString(encryptedPassword);
        } catch (RuntimeException e) {
            Log.debug("Unable to decrypt the stored password of user '{}'.", username, e);
            return null;
        }

        // An empty result is what Blowfish returns for some malformed ciphertext. It is never a usable password.
        if (candidate == null || candidate.isEmpty()) {
            return null;
        }
        return candidate;
    }

    /**
     * Loads the next page of stored encrypted passwords, ordered by username.
     *
     * @param con   the database connection to use
     * @param after the last username of the previous page, or {@code null} for the first page (not an empty string,
     *              which Oracle treats as NULL)
     * @return up to {@link #PAGE_SIZE} pairs of username and encrypted password
     * @throws SQLException if the stored passwords could not be read
     */
    @Nonnull
    static List<String[]> loadPageOfEncryptedPasswords(@Nonnull final Connection con, @Nullable final String after) throws SQLException
    {
        final List<String[]> result = new ArrayList<>();
        try (final PreparedStatement pstmt = con.prepareStatement(after == null ? LOAD_FIRST_ENCRYPTED_PASSWORDS : LOAD_NEXT_ENCRYPTED_PASSWORDS)) {
            if (after != null) {
                pstmt.setString(1, after);
            }
            DbConnectionManager.setFetchSize(pstmt, PAGE_SIZE);
            DbConnectionManager.setMaxRows(pstmt, PAGE_SIZE);
            try (final ResultSet rs = pstmt.executeQuery()) {
                // Not every driver honours setMaxRows, so stop reading irrespective of what the query returns.
                while (result.size() < PAGE_SIZE && rs.next()) {
                    result.add(new String[]{rs.getString(1), rs.getString(2)});
                }
            }
        }
        return result;
    }

    /**
     * Loads the complete SCRAM credentials of a user, skipping incomplete ones as authentication does.
     *
     * @param con      the database connection to use
     * @param username the user
     * @return the credentials of the user (possibly empty)
     * @throws SQLException if the credentials could not be read
     */
    @Nonnull
    static List<ScramCredentialData> loadScramCredentials(@Nonnull final Connection con, @Nonnull final String username) throws SQLException
    {
        final List<ScramCredentialData> result = new ArrayList<>();
        try (final PreparedStatement pstmt = con.prepareStatement(LOAD_SCRAM_CREDENTIALS)) {
            pstmt.setString(1, username);
            try (final ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    final String mechanism = rs.getString(1);
                    final int iterations = rs.getInt(2);
                    final boolean iterationsIsNull = rs.wasNull();
                    final String salt = rs.getString(3);
                    final String storedKey = rs.getString(4);
                    final String serverKey = rs.getString(5);
                    if (mechanism == null || iterationsIsNull || iterations <= 0 || salt == null || storedKey == null || serverKey == null) {
                        continue;
                    }
                    try {
                        result.add(new ScramCredentialData(mechanism, salt, iterations, storedKey, serverKey));
                    } catch (IllegalArgumentException e) {
                        Log.debug("Ignoring SCRAM credential with unrecognized mechanism '{}' of user '{}'.", mechanism, username, e);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Logs usernames in batches, so that a single log line cannot grow unbounded.
     *
     * @param logger    accepts a comma-separated batch of usernames
     * @param usernames the users to log
     */
    private static void logUsers(@Nonnull final Consumer<String> logger, @Nonnull final List<String> usernames)
    {
        for (int from = 0; from < usernames.size(); from += NAMES_PER_LOG_LINE) {
            logger.accept(String.join(", ", usernames.subList(from, Math.min(from + NAMES_PER_LOG_LINE, usernames.size()))));
        }
    }

    /**
     * Replaces the stored encrypted password of a user, provided that it still holds the expected value.
     *
     * @param con         the database connection to use
     * @param username    the user
     * @param expected    the value that is expected to be stored
     * @param replacement the value to store
     * @return {@code true} if the value was replaced, {@code false} if it was no longer the expected one
     * @throws SQLException if the stored password could not be updated
     */
    static boolean updateIfUnchanged(@Nonnull final Connection con, @Nonnull final String username, @Nonnull final String expected, @Nonnull final String replacement) throws SQLException
    {
        try (final PreparedStatement pstmt = con.prepareStatement(UPDATE_ENCRYPTED_PASSWORD_IF_UNCHANGED)) {
            pstmt.setString(1, replacement);
            pstmt.setString(2, username);
            pstmt.setString(3, expected);
            return pstmt.executeUpdate() == 1;
        }
    }
}

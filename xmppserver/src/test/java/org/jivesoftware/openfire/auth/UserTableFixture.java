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

import org.jivesoftware.openfire.sasl.ScramSha1SaslServer;

import javax.xml.bind.DatatypeConverter;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Creates, fills and reads the {@code ofUser} and {@code ofUserScram} tables in a test database.
 */
final class UserTableFixture
{
    static final int ITERATIONS = 4096;

    /**
     * Prevents instantiation: this class only provides static methods.
     */
    private UserTableFixture() {}

    /**
     * Creates the user tables, with the column definitions of the database schema that ships with Openfire.
     *
     * @param con the database connection to use
     */
    static void createUserTables(final Connection con) throws Exception
    {
        try (final Statement stmt = con.createStatement()) {
            stmt.execute("CREATE TABLE ofUser (username VARCHAR(64) NOT NULL, plainPassword VARCHAR(32), encryptedPassword VARCHAR(255), CONSTRAINT ofUser_pk PRIMARY KEY (username))");
            stmt.execute("CREATE TABLE ofUserScram (username VARCHAR(64) NOT NULL, mechanism VARCHAR(32) NOT NULL, storedKey VARCHAR(255), serverKey VARCHAR(255), salt VARCHAR(255), iterations INTEGER NOT NULL, CONSTRAINT ofUserScram_pk PRIMARY KEY (username, mechanism))");
        }
    }

    /**
     * Stores a user.
     *
     * @param con               the database connection to use
     * @param username          the name of the user
     * @param plainPassword     the plain-text password to store (can be null)
     * @param encryptedPassword the encrypted password to store (can be null)
     */
    static void insertUser(final Connection con, final String username, final String plainPassword, final String encryptedPassword) throws Exception
    {
        try (final PreparedStatement pstmt = con.prepareStatement("INSERT INTO ofUser (username, plainPassword, encryptedPassword) VALUES (?, ?, ?)")) {
            pstmt.setString(1, username);
            pstmt.setString(2, plainPassword);
            pstmt.setString(3, encryptedPassword);
            pstmt.executeUpdate();
        }
    }

    /**
     * Stores a SCRAM credential for a password, derived as {@link DefaultAuthProvider#setPassword} does.
     *
     * @param con             the database connection to use
     * @param username        the user
     * @param password        the plain-text password to derive the credential from
     * @param mechanism       the name of the SCRAM mechanism
     * @param hmacAlgorithm   the HMAC algorithm of the mechanism
     * @param digestAlgorithm the digest algorithm of the mechanism
     */
    static void insertScramCredential(final Connection con, final String username, final String password, final String mechanism, final String hmacAlgorithm, final String digestAlgorithm) throws Exception
    {
        final byte[] salt = new byte[DefaultAuthProvider.SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        final ScramUtils.ScramKeys keys = ScramUtils.deriveScramKeys(salt, password, ITERATIONS, hmacAlgorithm, digestAlgorithm);
        try (final PreparedStatement pstmt = con.prepareStatement("INSERT INTO ofUserScram (username, mechanism, iterations, salt, storedKey, serverKey) VALUES (?, ?, ?, ?, ?, ?)")) {
            pstmt.setString(1, username);
            pstmt.setString(2, mechanism);
            pstmt.setInt(3, ITERATIONS);
            pstmt.setString(4, DatatypeConverter.printBase64Binary(salt));
            pstmt.setString(5, DatatypeConverter.printBase64Binary(keys.storedKey));
            pstmt.setString(6, DatatypeConverter.printBase64Binary(keys.serverKey));
            pstmt.executeUpdate();
        }
    }

    /**
     * Stores a SCRAM-SHA-1 credential for a password.
     *
     * @param con      the database connection to use
     * @param username the user
     * @param password the plain-text password to derive the credential from
     */
    static void insertScramSha1Credential(final Connection con, final String username, final String password) throws Exception
    {
        insertScramCredential(con, username, password, ScramSha1SaslServer.MECHANISM_NAME, ScramSha1SaslServer.HMAC_ALGORITHM_NAME, ScramSha1SaslServer.DIGEST_ALGORITHM_NAME);
    }

    /**
     * Reads the stored encrypted password of a user, failing the test if the user does not exist.
     *
     * @param con      the database connection to use
     * @param username the user
     * @return the stored encrypted password (can be null)
     */
    static String storedEncryptedPassword(final Connection con, final String username) throws Exception
    {
        try (final PreparedStatement pstmt = con.prepareStatement("SELECT encryptedPassword FROM ofUser WHERE username = ?")) {
            pstmt.setString(1, username);
            try (final ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next(), "Expected user to exist: " + username);
                return rs.getString(1);
            }
        }
    }

    /**
     * Reads the stored keys of the SCRAM credentials of a user.
     *
     * @param con      the database connection to use
     * @param username the user
     * @return the stored key of each SCRAM credential, by mechanism name (possibly empty)
     */
    static Map<String, String> storedKeysByMechanism(final Connection con, final String username) throws Exception
    {
        final Map<String, String> result = new HashMap<>();
        try (final PreparedStatement pstmt = con.prepareStatement("SELECT mechanism, storedKey FROM ofUserScram WHERE username = ?")) {
            pstmt.setString(1, username);
            try (final ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return result;
    }
}

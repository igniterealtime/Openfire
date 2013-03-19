/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.avatars;

import org.apache.log4j.Logger;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Base64;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.JID;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

/**
 * @author Daniel Henninger
 */
public class Avatar {

    static Logger Log = Logger.getLogger(Avatar.class);

    private static final String INSERT_AVATAR =
            "INSERT INTO ofGatewayAvatars(jid, xmppHash, legacyIdentifier, createDate, lastUpdate, imageType, imageData) " +
            "VALUES (?,?,?,?,?,?,?)";
    private static final String DELETE_AVATAR =
            "DELETE FROM ofGatewayAvatars WHERE jid=?";
    private static final String LOAD_AVATAR =
            "SELECT xmppHash, legacyIdentifier, createDate, lastUpdate, imageType " +
            "FROM ofGatewayAvatars WHERE jid=?";
    private static final String RETRIEVE_IMAGE =
            "SELECT imageData FROM ofGatewayAvatars WHERE jid=?";
    private static final String UPDATE_LEGACY_ID =
            "UPDATE ofGatewayAvatars SET legacyIdentifier=? WHERE jid=?";

    private JID jid;
    private String xmppHash;
    private String legacyIdentifier;
    private Date createDate;
    private Date lastUpdate;
    private String mimeType;

    /**
     * Creates a new avatar entry.
     *
     * @param jid JID of the avatar.
     * @param imageData Binary image data.
     * @throws IllegalArgumentException if any of the arguments are null.
     */
    public Avatar(JID jid, byte[] imageData) throws IllegalArgumentException {
        if (jid == null || imageData == null) {
            throw new IllegalArgumentException("Avatar: Passed null argument.");
        }
        this.jid = jid;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(imageData);
            this.xmppHash = StringUtils.encodeHex(md.digest());
        }
        catch (NoSuchAlgorithmException e) {
            Log.error("Avatar: Unable to find support for SHA algorithm?");
        }
        this.createDate = new Date();
        this.lastUpdate = new Date();
        ImageInfo imageInfo = new ImageInfo();
        imageInfo.setInput(new ByteArrayInputStream(imageData));
        this.mimeType = imageInfo.getMimeType();

        try {
            insertIntoDb(Base64.encodeBytes(imageData));
        }
        catch (SQLException e) {
            Log.error("Avatar: SQL exception while inserting avatar: ", e);
        }
    }

    /**
     * Creates a new avatar entry.
     *
     * @param jid JID of the avatar.
     * @param legacyIdentifier Hash or whatever is necessary to identify the avatar on the legacy network.
     * @param imageData Binary image data.
     * @throws IllegalArgumentException if any of the arguments are null.
     */
    public Avatar(JID jid, String legacyIdentifier, byte[] imageData) throws IllegalArgumentException {
        if (jid == null || legacyIdentifier == null || imageData == null) {
            throw new IllegalArgumentException("Avatar: Passed null argument.");
        }
        this.jid = jid;
        this.legacyIdentifier = legacyIdentifier;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(imageData);
            this.xmppHash = StringUtils.encodeHex(md.digest());
        }
        catch (NoSuchAlgorithmException e) {
            Log.error("Avatar: Unable to find support for SHA algorithm?");
        }
        this.createDate = new Date();
        this.lastUpdate = new Date();
        ImageInfo imageInfo = new ImageInfo();
        imageInfo.setInput(new ByteArrayInputStream(imageData));
        this.mimeType = imageInfo.getMimeType();
        try {
            insertIntoDb(Base64.encodeBytes(imageData));
        }
        catch (SQLException e) {
            Log.error("Avatar: SQL exception while inserting avatar: ", e);
        }
    }

    /**
     * Loads an existing avatar.
     *
     * @param jid JID of the contact whose avatar we are retrieving.
     * @throws NotFoundException if avatar entry was not found in database.
     */
    public Avatar(JID jid) throws NotFoundException {
        this.jid = jid;
        loadFromDb();
        Log.debug("Loaded avatar for "+this.jid+" of hash "+this.xmppHash);
    }

    /**
     * Returns the JID of the avatar.
     *
     * @return JID of avatar.
     */
    public JID getJid() {
        return jid;
    }

    /**
     * Returns the XMPP hash (sha1).
     *
     * @return SHA1 based XMPP hash.
     */
    public String getXmppHash() {
        return xmppHash;
    }

    /**
     * Returns the legacy identifier of the avatar.
     *
     * This is completely up to the transport and is generally whatever type of hash is used on their end.
     *
     * @return Legacy identifier for the avatar.
     */
    public String getLegacyIdentifier() {
        return legacyIdentifier;
    }

    /**
     * Sets the legacy identifier of the avatar.
     *
     * This is typically used for setting your own avatar information on the legacy service,
     * and called after the avatar is set to store the known legacy identifier, if there is one,
     * associated with your avatar.
     *
     * @param identifier Identifier to store.
     */
    public void setLegacyIdentifier(String identifier) {
        this.legacyIdentifier = identifier;
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(UPDATE_LEGACY_ID);
            pstmt.setString(1, jid.toString());
            pstmt.setString(2, legacyIdentifier);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            abortTransaction = true;
            Log.error("Avatar: Major SQL error while updating legacy identifier: ", sqle);
        }
        finally {
            DbConnectionManager.closeTransactionConnection(pstmt, con, abortTransaction);
        }
    }

    /**
     * Returns the creation date of the avatar in the database.
     *
     * @return Creation date of the avatar.
     */
    public Date getCreateDate() {
        return createDate;
    }

    /**
     * Returns the date the avatar was last updated.
     *
     * @return Last update date of the avatar.
     */
    public Date getLastUpdate() {
        return lastUpdate;
    }

    /**
     * Returns the mime type of the image that is stored.
     *
     * @return Mime type of the avatar image.
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Retrieves the actual image data for this avatar.
     *
     * @return The base64 encoded image data for the avatar.
     * @throws NotFoundException if avatar entry was not found in database.
     */
    public String getImageData() throws NotFoundException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String imageData = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(RETRIEVE_IMAGE);
            pstmt.setString(1, jid.toString());
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new NotFoundException("Avatar not found for " + jid);
            }
            imageData = rs.getString(1);
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return imageData;
    }

    /**
     * Inserts a new avaar into the database.
     *
     * @param imageData Base64 encoded image data to be stored in database.
     * @throws SQLException if the SQL statement is wrong for whatever reason.
     */
    private void insertIntoDb(String imageData) throws SQLException {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        synchronized ("avatar"+jid.toString()) {
            try {
                con = DbConnectionManager.getTransactionConnection();
                pstmt = con.prepareStatement(DELETE_AVATAR);
                pstmt.setString(1, jid.toString());
                pstmt.executeUpdate();
                pstmt = con.prepareStatement(INSERT_AVATAR);
                pstmt.setString(1, jid.toString());
                pstmt.setString(2, xmppHash);
                pstmt.setString(3, legacyIdentifier);
                pstmt.setLong(4, createDate.getTime());
                pstmt.setLong(5, lastUpdate.getTime());
                pstmt.setString(6, mimeType);
                pstmt.setString(7, imageData);
                pstmt.executeUpdate();
            }
            catch (SQLException sqle) {
                abortTransaction = true;
                throw sqle;
            }
            finally {
                DbConnectionManager.closeTransactionConnection(pstmt, con, abortTransaction);
            }
        }
    }

    /**
     * Load avatar from database.
     *
     * @throws NotFoundException if avatar entry was not found in database.
     */
    private void loadFromDb() throws NotFoundException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_AVATAR);
            pstmt.setString(1, jid.toString());
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new NotFoundException("Avatar not found for " + jid);
            }
            this.xmppHash = rs.getString(1);
            this.legacyIdentifier = rs.getString(2);
            this.createDate = new Date(rs.getLong(3));
            this.lastUpdate = new Date(rs.getLong(4));
            this.mimeType = rs.getString(5);
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

}

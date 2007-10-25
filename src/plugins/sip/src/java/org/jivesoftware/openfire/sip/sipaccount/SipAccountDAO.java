/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.sip.sipaccount;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 * Database persistence for SipAccount class and database methods for stored SIP Accounts
 *
 * @author Thiago Rocha Camargo
 */
public class SipAccountDAO {

	public static SipAccount getAccountByUser(String username) {

		String sql = "SELECT username, sipusername, sipauthuser, sipdisplayname, sippassword, sipserver, enabled, " +
                "status, stunserver, stunport, usestun, voicemail, outboundproxy, promptCredentials FROM sipUser " +
                "WHERE username = ? ";

		SipAccount sipAccount = null;
		Connection con = null;
		PreparedStatement psmt = null;
		ResultSet rs = null;

		try {

			con = DbConnectionManager.getConnection();
			psmt = con.prepareStatement(sql);
			psmt.setString(1, username);
			rs = psmt.executeQuery();

			if (rs.next()) {
				sipAccount = read(rs);
			}

		} catch (SQLException e) {
			Log.error(e.getMessage(), e);
		} finally {
			DbConnectionManager.closeConnection(rs, psmt, con);
		}
		return sipAccount;
	}

	private static SipAccount read(ResultSet rs) {
		SipAccount sipAccount = null;
		try {

			String username = rs.getString("username");
			String sipusername = rs.getString("sipusername");
			String authusername = rs.getString("sipauthuser");
			String displayname = rs.getString("sipdisplayname");
			String password = rs.getString("sippassword");
			String server = rs.getString("sipserver");
			String stunServer = rs.getString("stunserver");
			String stunPort = rs.getString("stunport");
			boolean useStun = rs.getBoolean("usestun");
			boolean enabled = rs.getBoolean("enabled");
            String voicemail = rs.getString("voicemail");
            String outboundproxy = rs.getString("outboundproxy");
            boolean promptCredentials = rs.getBoolean("promptCredentials");
            SipRegisterStatus status = SipRegisterStatus.valueOf(rs.getString("status"));
			sipAccount = new SipAccount(username);

			sipAccount.setSipUsername(sipusername);
			sipAccount.setAuthUsername(authusername);
			sipAccount.setDisplayName(displayname);
			sipAccount.setPassword(password);
			sipAccount.setServer(server);
			sipAccount.setEnabled(enabled);
			sipAccount.setStatus(status);
			sipAccount.setStunServer(stunServer);
			sipAccount.setStunPort(stunPort);
			sipAccount.setUseStun(useStun);
            sipAccount.setVoiceMailNumber(voicemail);
            sipAccount.setOutboundproxy(outboundproxy);
            sipAccount.setPromptCredentials(promptCredentials);

        } catch (SQLException e) {
			Log.error(e.getMessage(), e);
		}
		return sipAccount;
	}

	public static void insert(SipAccount sipAccount) throws SQLException {

		String sql = "INSERT INTO sipUser (username, sipusername, sipauthuser, sipdisplayname, sippassword, sipserver, enabled, status, stunserver, stunport, usestun, voicemail, outboundproxy, promptCredentials ) "
				+ " values  ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		Connection con = null;
		PreparedStatement psmt = null;
		ResultSet rs = null;

		try {
			con = DbConnectionManager.getConnection();
			psmt = con.prepareStatement(sql);
			psmt.setString(1, sipAccount.getUsername());
			psmt.setString(2, sipAccount.getSipUsername());
			psmt.setString(3, sipAccount.getAuthUsername());
			psmt.setString(4, sipAccount.getDisplayName());
			psmt.setString(5, sipAccount.getPassword());
			psmt.setString(6, sipAccount.getServer());
			psmt.setBoolean(7, sipAccount.isEnabled());
			psmt.setString(8, sipAccount.getStatus().name());
			psmt.setString(9, sipAccount.getStunServer());
			psmt.setString(10, sipAccount.getStunPort());
			psmt.setBoolean(11, sipAccount.isUseStun());
            psmt.setString(12, sipAccount.getVoiceMailNumber());
            psmt.setString(13, sipAccount.getOutboundproxy());
            psmt.setBoolean(14, sipAccount.isPromptCredentials());
            psmt.executeUpdate();

		} catch (SQLException e) {
			Log.error(e.getMessage(), e);
			throw new SQLException(e.getMessage());
		} finally {
			DbConnectionManager.closeConnection(rs, psmt, con);
		}

	}

	public static void update(SipAccount sipAccount) throws SQLException {

		String sql = "UPDATE sipUser SET sipusername = ?, sipauthuser = ?, sipdisplayname = ?, sippassword = ?, sipserver = ?, enabled = ?, status = ?, stunserver = ?, stunport = ?, usestun = ?, voicemail= ?, outboundproxy = ?, promptCredentials = ? "
				+ " WHERE username = ?";

		Connection con = null;
		PreparedStatement psmt = null;

		try {

			con = DbConnectionManager.getConnection();
			psmt = con.prepareStatement(sql);
			psmt.setString(1, sipAccount.getSipUsername());
			psmt.setString(2, sipAccount.getAuthUsername());
			psmt.setString(3, sipAccount.getDisplayName());
			psmt.setString(4, sipAccount.getPassword());
			psmt.setString(5, sipAccount.getServer());
			psmt.setBoolean(6, sipAccount.isEnabled());
			psmt.setString(7, sipAccount.getStatus().name());
			psmt.setString(8, sipAccount.getStunServer());
			psmt.setString(9, sipAccount.getStunPort());
			psmt.setBoolean(10, sipAccount.isUseStun());
            psmt.setString(11, sipAccount.getVoiceMailNumber());
            psmt.setString(12, sipAccount.getOutboundproxy());
            psmt.setBoolean(13, sipAccount.isPromptCredentials());
            psmt.setString(14, sipAccount.getUsername());

            psmt.executeUpdate();

		} catch (SQLException e) {
			Log.error(e.getMessage(), e);
			throw new SQLException(e.getMessage());
		} finally {
			DbConnectionManager.closeConnection(psmt, con);
		}

	}

	public static void remove(SipAccount sipAccount) {

		String sql = "DELETE FROM sipUser WHERE username = ?";

		Connection con = null;
		PreparedStatement psmt = null;

		try {

			con = DbConnectionManager.getConnection();
			psmt = con.prepareStatement(sql);
            psmt.setString(1, sipAccount.getUsername());
			psmt.executeUpdate();
            psmt.close();

        } catch (SQLException e) {
			Log.error(e.getMessage(), e);
		} finally {
			DbConnectionManager.closeConnection(psmt, con);
		}

	}

	public static Collection<SipAccount> getUsers(int startIndex, int numResults) {

		String sql = "SELECT username, sipusername, sipauthuser, sipdisplayname, sippassword, sipserver, enabled, status, stunserver, stunport, usestun, voicemail, outboundproxy, promptCredentials FROM sipUser "
				+ " ORDER BY USERNAME";

		List<SipAccount> sipAccounts = new ArrayList<SipAccount>(numResults);
		Connection con = null;
		PreparedStatement pstmt = null;
		try {
			con = DbConnectionManager.getConnection();
			pstmt = DbConnectionManager.createScrollablePreparedStatement(con,
					sql);
			ResultSet rs = pstmt.executeQuery();
			DbConnectionManager.setFetchSize(rs, startIndex + numResults);
			DbConnectionManager.scrollResultSet(rs, startIndex);
			int count = 0;
			while (rs.next() && count < numResults) {
				sipAccounts.add(read(rs));
				count++;
			}
			rs.close();
		} catch (SQLException e) {
			Log.error(e);
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (Exception e) {
				Log.error(e);
			}
			try {
				if (con != null) {
					con.close();
				}
			} catch (Exception e) {
				Log.error(e);
			}
		}
		return sipAccounts;
	}

    public static int getUserCount() {
        int count = 0;

		String sql = "SELECT count(*) FROM sipUser";

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return count;
    }

}

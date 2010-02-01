/**
 * $RCSfile$
 * $Revision: 19545 $
 * $Date: 2005-08-18 16:07:35 -0700 (Thu, 18 Aug 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.fastpath.settings.offline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieves and persists offline settings for a workgroup.
 *
 * @author Derek DeMoro
 */
public class OfflineSettingsManager {

	private static final Logger Log = LoggerFactory.getLogger(OfflineSettingsManager.class);
	
    private static final String GET_OFFLINE_SETTTINGS =
            "SELECT redirectPage, emailAddress, subject, offlineText FROM " +
            "fpOfflineSetting WHERE workgroupID=?";
    private static final String INSERT_OFFLINE_SETTINGS =
            "INSERT INTO fpOfflineSetting(workgroupID, redirectPage, emailAddress, subject, " +
            "offlineText) VALUES(?,?,?,?,?)";
    private static final String UPDATE_OFFLINE_SETTINGS =
            "UPDATE fpOfflineSetting SET redirectPage=?, emailAddress=?, subject=?, offlineText=? " +
            "WHERE workgroupID=?";

    public OfflineSettings saveOfflineSettings(Workgroup workgroup, String webPage, String email,
            String subject, String offlineText)
    {
        OfflineSettings offline;
        try {
            offline = getOfflineSettings(workgroup);
            return updateOfflineSettings(workgroup, webPage, email, subject, offlineText);
        }
        catch (OfflineSettingsNotFound osnf) {
            offline = new OfflineSettings();
        }

        offline.setRedirectURL(webPage);
        offline.setEmailAddress(email);
        offline.setOfflineText(offlineText);
        offline.setSubject(subject);

        String redirectURL = webPage != null ? webPage : "";
        String emailAddress = email != null ? email : "";
        subject = subject != null ? subject : "";
        offlineText = offlineText != null ? offlineText : "";

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_OFFLINE_SETTINGS);

            pstmt.setLong(1, workgroup.getID());
            pstmt.setString(2, redirectURL);
            pstmt.setString(3, emailAddress);
            pstmt.setString(4, subject);

            DbConnectionManager.setLargeTextField(pstmt, 5, offlineText);
            pstmt.executeUpdate();
        }
        catch (Exception ex) {
            Log.error(ex.getMessage(), ex);
            return null;
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return offline;
    }

    public OfflineSettings updateOfflineSettings(Workgroup workgroup, String webPage,
            String email, String subject, String offlineText)
    {
        final OfflineSettings offline = new OfflineSettings();
        offline.setRedirectURL(webPage);
        offline.setEmailAddress(email);
        offline.setOfflineText(offlineText);
        offline.setSubject(subject);


        String redirectURL = webPage != null ? webPage : "";
        String emailAddress = email != null ? email : "";
        subject = subject != null ? subject : "";
        offlineText = offlineText != null ? offlineText : "";

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_OFFLINE_SETTINGS);
            pstmt.setString(1, redirectURL);
            pstmt.setString(2, emailAddress);
            pstmt.setString(3, subject);

            DbConnectionManager.setLargeTextField(pstmt, 4, offlineText);
            pstmt.setLong(5, workgroup.getID());
            pstmt.executeUpdate();
        }
        catch (Exception ex) {
            Log.error(ex.getMessage(), ex);
            return null;
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return offline;
    }

    /**
     * Returns the OfflineSettings found for the specified workgroup.
     *
     * @param workgroup the owning workgroup of the settings.
     * @return the OfflineSettings
     * @throws OfflineSettingsNotFound thrown when no OfflineSettings were found. This
     *      will occur with new workgroups.
     */
    public OfflineSettings getOfflineSettings(Workgroup workgroup) throws OfflineSettingsNotFound {
        OfflineSettings offlineSettings = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_OFFLINE_SETTTINGS);
            pstmt.setLong(1, workgroup.getID());
            rs = pstmt.executeQuery();
            if (rs.next()) {
                String redirectPage = rs.getString(1);
                String emailAddress = rs.getString(2);
                String subject = rs.getString(3);
                String offlineText = DbConnectionManager.getLargeTextField(rs, 4);

                offlineSettings = new OfflineSettings();
                offlineSettings.setRedirectURL(redirectPage);
                offlineSettings.setEmailAddress(emailAddress);
                offlineSettings.setSubject(subject);
                offlineSettings.setOfflineText(offlineText);
            }
        }
        catch (Exception ex) {
            Log.error(ex.getMessage(), ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        if (offlineSettings == null) {
            throw new OfflineSettingsNotFound();
        }

        return offlineSettings;
    }
}
/**
 * $RCSfile$
 * $Revision: 19545 $
 * $Date: 2005-08-18 16:07:35 -0700 (Thu, 18 Aug 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.fastpath;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Workgroup settings for saving individual workgroup information.
 *
 * @author Derek DeMoro
 */
public class WorkgroupSettings {

	private static final Logger Log = LoggerFactory.getLogger(WorkgroupSettings.class);
	
    private static final String LOAD_SETTINGS =
            "SELECT value FROM fpSetting WHERE workgroupName=? AND namespace=?";
    private static final String INSERT_SETTINGS =
            "INSERT INTO fpSetting (value,name,workgroupName,namespace) VALUES (?,?,?,?)";
    private static final String UPDATE_SETTINGS =
            "UPDATE fpSetting SET value=?, name=? WHERE workgroupName=? AND namespace=?";

    /**
     * Constructs a new <code>WorkgroupSettings</code> instance.
     */
    public WorkgroupSettings() {
    }

    /**
     * Stores private data. If the name and namespace of the element matches another
     * stored private data XML document, then replace it with the new one.
     *
     * @param data     the data to store (XML element)
     * @param workgroupName the name of the workgroup where the data should be stored.
     */
    public void add(String workgroupName, Element data) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            StringWriter writer = new StringWriter();
            data.write(writer);
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_SETTINGS);
            pstmt.setString(1, workgroupName);
            pstmt.setString(2, data.getNamespaceURI());
            ResultSet rs = pstmt.executeQuery();
            boolean update = false;
            if (rs.next()) {
                update = true;
            }
            rs.close();
            pstmt.close();

            if (update) {
                pstmt = con.prepareStatement(UPDATE_SETTINGS);
            }
            else {
                pstmt = con.prepareStatement(INSERT_SETTINGS);
            }

            DbConnectionManager.setLargeTextField(pstmt, 1, writer.toString());
            pstmt.setString(2, data.getName());
            pstmt.setString(3, workgroupName);
            pstmt.setString(4, data.getNamespaceURI());
            pstmt.executeUpdate();
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Returns the data stored under a key corresponding to the name and namespace
     * of the given element. The Element must be in the form:<p>
     *
     * <code>&lt;name xmlns='namespace'/&gt;</code><p>
     * 
     * If no data is currently stored under the given key, an empty element will be
     * returned.
     *
     * @param data an XML document who's element name and namespace is used to
     *      match previously stored private data.
     * @param workgroupName the name of the workgroup who's data is to be stored.
     * @return the data stored under the given key or the data element.
     */
    public Element get(String workgroupName, Element data) {
        data.clearContent();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_SETTINGS);
            pstmt.setString(1, workgroupName);
            pstmt.setString(2, data.getNamespaceURI());
            rs = pstmt.executeQuery();
            if (rs.next()) {
                Document document = DocumentHelper.parseText(rs.getString(1).trim());
                data = document.getRootElement();
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return data;
    }
}
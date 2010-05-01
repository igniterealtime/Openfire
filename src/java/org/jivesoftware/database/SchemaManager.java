/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import org.jivesoftware.database.bugfix.OF33;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages database schemas for Openfire and Openfire plugins. The manager uses the
 * ofVersion database table to figure out which database schema is currently installed
 * and then attempts to automatically apply database schema changes as necessary.<p>
 *
 * Running database schemas automatically requires appropriate database permissions.
 * Without those permissions, the automatic installation/upgrade process will fail
 * and users will be prompted to apply database changes manually.
 *
 * @see DbConnectionManager#getSchemaManager()
 *
 * @author Matt Tucker
 */
public class SchemaManager {

	private static final Logger Log = LoggerFactory.getLogger(SchemaManager.class);

    private static final String CHECK_VERSION_OLD =
            "SELECT minorVersion FROM jiveVersion";
    private static final String CHECK_VERSION =
            "SELECT version FROM ofVersion WHERE name=?";
    private static final String CHECK_VERSION_JIVE =
            "SELECT version FROM jiveVersion WHERE name=?";

    /**
     * Current Openfire database schema version.
     */
    private static final int DATABASE_VERSION = 21;

    /**
     * Creates a new Schema manager.
     */
    SchemaManager() {

    }

    /**
     * Checks the Openfire database schema to ensure that it's installed and up to date.
     * If the schema isn't present or up to date, an automatic update will be attempted.
     *
     * @param con a connection to the database.
     * @return true if database schema checked out fine, or was automatically installed
     *      or updated successfully.
     */
    public boolean checkOpenfireSchema(Connection con) {
        // Change 'wildfire' to 'openfire' in ofVersion table (update to new name)
        updateToOpenfire(con);
        try {
            return checkSchema(con, "openfire", DATABASE_VERSION,
                    new ResourceLoader() {
                        @Override
						public InputStream loadResource(String resourceName) {
                            File file = new File(JiveGlobals.getHomeDirectory() + File.separator +
                                    "resources" + File.separator + "database", resourceName);
                            try {
                                return new FileInputStream(file);
                            }
                            catch (FileNotFoundException e) {
                                return null;
                            }
                        }
                    });
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("upgrade.database.failure"), e);
            System.out.println(LocaleUtils.getLocalizedString("upgrade.database.failure"));
        }
        return false;
    }

    /**
     * Checks the plugin's database schema (if one is required) to ensure that it's
     * installed and up to date. If the schema isn't present or up to date, an automatic
     * update will be attempted.
     *
     * @param plugin the plugin.
     * @return true if database schema checked out fine, or was automatically installed
     *      or updated successfully, or if it isn't needed. False will only be returned
     *      if there is an error.
     */
    public boolean checkPluginSchema(final Plugin plugin) {
        final PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        String schemaKey = pluginManager.getDatabaseKey(plugin);
        int schemaVersion = pluginManager.getDatabaseVersion(plugin);
        // If the schema key or database version aren't defined, then the plugin doesn't
        // need database tables.
        if (schemaKey == null || schemaVersion == -1) {
            return true;
        }
        Connection con = null;
        try {
            con = DbConnectionManager.getConnection();
            return checkSchema(con, schemaKey, schemaVersion, new ResourceLoader() {
                @Override
				public InputStream loadResource(String resourceName) {
                    File file = new File(pluginManager.getPluginDirectory(plugin) +
                            File.separator + "database", resourceName);
                    try {
                        return new FileInputStream(file);
                    }
                    catch (FileNotFoundException e) {
                        return null;
                    }
                }
            });
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("upgrade.database.failure"), e);
            System.out.println(LocaleUtils.getLocalizedString("upgrade.database.failure"));
        }
        finally {
            DbConnectionManager.closeConnection(con);
        }
        return false;
    }

    /**
     * Checks to see if the database needs to be upgraded. This method should be
     * called once every time the application starts up.
     *
     * @param con the database connection to use to check the schema with.
     * @param schemaKey the database schema key (name).
     * @param requiredVersion the version that the schema should be at.
     * @param resourceLoader a resource loader that knows how to load schema files.
     * @throws Exception if an error occured.
     * @return True if the schema update was successful.
     */
    private boolean checkSchema(Connection con, String schemaKey, int requiredVersion,
            ResourceLoader resourceLoader) throws Exception
    {
        int currentVersion = -1;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = con.prepareStatement(CHECK_VERSION);
            pstmt.setString(1, schemaKey);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                currentVersion = rs.getInt(1);
            }
        }
        catch (SQLException sqle) {
            // The database schema must not be installed.
            Log.debug("SchemaManager: Error verifying "+schemaKey+" version, probably ignorable.", sqle);
            DbConnectionManager.closeStatement(rs, pstmt);
            if (schemaKey.equals("openfire")) {
                try {
                    // Releases of Openfire before 3.6.0 stored the version in a jiveVersion table.
                    pstmt = con.prepareStatement(CHECK_VERSION_JIVE);
                    pstmt.setString(1, schemaKey);
                    rs = pstmt.executeQuery();
                    if (rs.next()) {
                        currentVersion = rs.getInt(1);
                    }
                }
                catch (SQLException sqlea) {
                    // The database schema must not be installed.
                    Log.debug("SchemaManager: Error verifying "+schemaKey+" version, probably ignorable.", sqlea);
                    DbConnectionManager.closeStatement(rs, pstmt);

                    // Releases of Openfire before 2.6.0 stored a major and minor version
                    // number so the normal check for version can fail. Check for the
                    // version using the old format in that case.
                    try {

                        pstmt = con.prepareStatement(CHECK_VERSION_OLD);
                        rs = pstmt.executeQuery();
                        if (rs.next()) {
                            currentVersion = rs.getInt(1);
                        }
                    }
                    catch (SQLException sqle2) {
                        // The database schema must not be installed.
                        Log.debug("SchemaManager: Error verifying "+schemaKey+" version, probably ignorable", sqle2);
                    }
                }
            }
        }
        finally {
            DbConnectionManager.closeStatement(rs, pstmt);
        }
        // If already up to date, return.
        if (currentVersion >= requiredVersion) {
            return true;
        }
        // If the database schema isn't installed at all, we need to install it.
        else if (currentVersion == -1) {
            Log.info(LocaleUtils.getLocalizedString("upgrade.database.missing_schema",
                    Arrays.asList(schemaKey)));
            System.out.println(LocaleUtils.getLocalizedString("upgrade.database.missing_schema",
                    Arrays.asList(schemaKey)));
            // Resource will be like "/database/openfire_hsqldb.sql"
            String resourceName = schemaKey + "_" +
                    DbConnectionManager.getDatabaseType() + ".sql";
            InputStream resource = resourceLoader.loadResource(resourceName);
            if (resource == null) {
                return false;
            }
            try {
                // For plugins, we will automatically convert jiveVersion to ofVersion
                executeSQLScript(con, resource, !schemaKey.equals("openfire") && !schemaKey.equals("wildfire"));
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
                return false;
            }
            finally {
                try {
                    resource.close();
                }
                catch (Exception e) {
                    // Ignore.
                }
            }
            Log.info(LocaleUtils.getLocalizedString("upgrade.database.success"));
            System.out.println(LocaleUtils.getLocalizedString("upgrade.database.success"));
            return true;
        }
        // Must have a version of the schema that needs to be upgraded.
        else {
            // The database is an old version that needs to be upgraded.
            Log.info(LocaleUtils.getLocalizedString("upgrade.database.old_schema",
                    Arrays.asList(currentVersion, schemaKey, requiredVersion)));
            System.out.println(LocaleUtils.getLocalizedString("upgrade.database.old_schema",
                    Arrays.asList(currentVersion, schemaKey, requiredVersion)));
            // If the database type is unknown, we don't know how to upgrade it.
            if (DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.unknown) {
                Log.info(LocaleUtils.getLocalizedString("upgrade.database.unknown_db"));
                System.out.println(LocaleUtils.getLocalizedString("upgrade.database.unknown_db"));
                return false;
            }
            // Upgrade scripts for interbase are not maintained.
            else if (DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.interbase) {
                Log.info(LocaleUtils.getLocalizedString("upgrade.database.interbase_db"));
                System.out.println(LocaleUtils.getLocalizedString("upgrade.database.interbase_db"));
                return false;
            }

            // Run all upgrade scripts until we're up to the latest schema.
            for (int i = currentVersion + 1; i <= requiredVersion; i++) {
                InputStream resource = getUpgradeResource(resourceLoader, i, schemaKey);
                
                // apply the 'database-patches-done-in-java'
				try {
					if (i == 21 && schemaKey.equals("openfire")) {
						OF33.executeFix(con);
					}
				} catch (Exception e) {
					Log.error(e.getMessage(), e);
					return false;
				}
                if (resource == null) {
                    continue;
                }
                try {
                    executeSQLScript(con, resource, !schemaKey.equals("openfire") && !schemaKey.equals("wildfire"));
                }
                catch (Exception e) {
                    Log.error(e.getMessage(), e);
                    return false;
                }
                finally {
                    try {
                        resource.close();
                    }
                    catch (Exception e) {
                        // Ignore.
                    }
                }
            }
            Log.info(LocaleUtils.getLocalizedString("upgrade.database.success"));
            System.out.println(LocaleUtils.getLocalizedString("upgrade.database.success"));
            return true;
        }
    }

    private InputStream getUpgradeResource(ResourceLoader resourceLoader, int upgradeVersion,
            String schemaKey)
    {
        InputStream resource = null;
        if ("openfire".equals(schemaKey)) {
            // Resource will be like "/database/upgrade/6/openfire_hsqldb.sql"
            String path = JiveGlobals.getHomeDirectory() + File.separator + "resources" +
                    File.separator + "database" + File.separator + "upgrade" + File.separator +
                    upgradeVersion;
            String filename = schemaKey + "_" + DbConnectionManager.getDatabaseType() + ".sql";
            File file = new File(path, filename);
            try {
                resource = new FileInputStream(file);
            }
            catch (FileNotFoundException e) {
                // If the resource is null, the specific upgrade number is not available.
            }
        }
        else {
            String resourceName = "upgrade/" + upgradeVersion + "/" + schemaKey + "_" +
                    DbConnectionManager.getDatabaseType() + ".sql";
            resource = resourceLoader.loadResource(resourceName);
        }
        return resource;
    }

    private void updateToOpenfire(Connection con){
        PreparedStatement pstmt = null;
        try {
            pstmt = con.prepareStatement("UPDATE jiveVersion SET name='openfire' WHERE name='wildfire'");
            pstmt.executeUpdate();
        }
        catch (Exception ex) {
//            Log.warn("Error when trying to update to new name", ex);
            // This is "scary" to see in the logs and causes more confusion than it's worth at this point.
            // So silently move on.
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
        }
    }

    /**
     * Executes a SQL script.
     *
     * @param con database connection.
     * @param resource an input stream for the script to execute.
     * @param autoreplace automatically replace jiveVersion with ofVersion
     * @throws IOException if an IOException occurs.
     * @throws SQLException if an SQLException occurs.
     */
    private static void executeSQLScript(Connection con, InputStream resource, Boolean autoreplace) throws IOException,
            SQLException
    {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(resource));
            boolean done = false;
            while (!done) {
                StringBuilder command = new StringBuilder();
                while (true) {
                    String line = in.readLine();
                    if (line == null) {
                        done = true;
                        break;
                    }
                    // Ignore comments and blank lines.
                    if (isSQLCommandPart(line)) {
                        command.append(" ").append(line);
                    }
                    if (line.trim().endsWith(";")) {
                        break;
                    }
                }
                // Send command to database.
                if (!done && !command.toString().equals("")) {
                    // Remove last semicolon when using Oracle or DB2 to prevent "invalid character error"
                    if (DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.oracle ||
                            DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.db2) {
                        command.deleteCharAt(command.length() - 1);
                    }
                    PreparedStatement pstmt = null;
                    try {
                        String cmdString = command.toString();
                        if (autoreplace)  {
                            cmdString = cmdString.replaceAll("jiveVersion", "ofVersion");
                        }
                        pstmt = con.prepareStatement(cmdString);
                        pstmt.execute();
                    }
                    catch (SQLException e) {
                        // Lets show what failed
                        Log.error("SchemaManager: Failed to execute SQL:\n"+command.toString());
                        throw e;
                    }
                    finally {
                        DbConnectionManager.closeStatement(pstmt);
                    }
                }
            }
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                }
                catch (Exception e) {
                    Log.error(e.getMessage(), e);
                }
            }
        }
    }

    private static abstract class ResourceLoader {

        public abstract InputStream loadResource(String resourceName);

    }

    /**
     * Returns true if a line from a SQL schema is a valid command part.
     *
     * @param line the line of the schema.
     * @return true if a valid command part.
     */
    private static boolean isSQLCommandPart(String line) {
        line = line.trim();
        if (line.equals("")) {
            return false;
        }
        // Check to see if the line is a comment. Valid comment types:
        //   "//" is HSQLDB
        //   "--" is DB2 and Postgres
        //   "#" is MySQL
        //   "REM" is Oracle
        //   "/*" is SQLServer
        return !(line.startsWith("//") || line.startsWith("--") || line.startsWith("#") ||
                line.startsWith("REM") || line.startsWith("/*") || line.startsWith("*"));
    }
}
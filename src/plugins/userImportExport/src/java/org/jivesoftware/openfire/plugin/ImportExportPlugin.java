/*
 * Copyright 2016 Ryan Graham
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
package org.jivesoftware.openfire.plugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang.StringEscapeUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The user import/export plugin provides a way to import and export Openfire
 * user data via the Admin Console. The user data consists of username, 
 * name, email address, password and roster list (aka "buddy list"). This plugin also 
 * can aid in the migration of users from other Jabber/XMPP based systems to Jive 
 * Openfire.
 * 
 * @author <a href="mailto:ryan@version2software.com">Ryan Graham</a>
 */
public class ImportExportPlugin implements Plugin {
    
    private static final Logger Log = LoggerFactory.getLogger(ImportExportPlugin.class);
    
    private UserProvider provider;
    
    public ImportExportPlugin() {
        provider = UserManager.getUserProvider();
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
    }

    public void destroyPlugin() {
        provider = null;
    }
    
    /**
     * Convenience method that returns true if this UserProvider is read-only.
     *
     * @return true if the user provider is read-only.
     */
    public boolean isUserProviderReadOnly() {
        return provider.isReadOnly();
    }
    
    /**
     * Converts the user data that is to be exported to a byte[]. If a read-only
     * user store is being used a user's password will be the same as their username.
     *
     * @return a byte[] of the user data.
     * @throws IOException if there's a problem writing to the XMLWriter.
     */
    public byte[] exportUsersToByteArray(boolean xep227Support) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        XMLWriter writer = new XMLWriter(out, OutputFormat.createPrettyPrint());
        writer.write(exportUsers(xep227Support));
        
        return out.toByteArray();
    }
    
    /**
     * Converts the exported user data to a String. If a read-only
     * user store is being used a user's password will be the same as their username.
     *
     * @return a formatted String representation of the user data.
     * @throws IOException if there's a problem writing to the XMLWriter.
     */
    public String exportUsersToString(boolean xep227Support) throws IOException {
        StringWriter stringWriter = new StringWriter();
        XMLWriter writer = null;
        try {
           writer = new XMLWriter(stringWriter, OutputFormat.createPrettyPrint());
           writer.write(exportUsers(xep227Support));
        } catch (IOException ioe) {
            Log.error(ioe.getMessage(), ioe);
            throw ioe;
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
        
        return StringEscapeUtils.escapeHtml(stringWriter.toString());
    }
    
    /**
     * Returns a list of usernames that were unable to be imported or whose rosters could not imported. Users are not able to be 
     * imported for the following reasons:
     * <li>Their username is not properly formatted.
     * <li>If a read-only user data store is being used and the user could not be found.
     * <li>If a writeable user data store is being used and the user already exists.
     *
     * @param file a FileItem containing the user data to be imported.
     * @param previousDomain a String an optional parameter that if supplied will replace the user roster entries domain names to 
     * server name of current Openfire installation.
     * @return True if FileItem matches the openfire user schema.
     * @throws IOException if there is a problem reading the FileItem.
     * @throws DocumentException if an error occurs during parsing.
     */
    public List<String> importUserData(FileItem file, String previousDomain, boolean xep227Support) throws DocumentException, IOException {

        InExporter exporter = XMLImportExportFactory.getExportInstance(xep227Support);

        return exporter.importUsers(file.getInputStream(), previousDomain,isUserProviderReadOnly());
    }
    
    /**
     * Returns whether or not the supplied FileItem matches the openfire user schema
     *
     * @param file a FileItem to be validated.
     * @return True if FileItem matches the openfire user schema.
     */
    public boolean validateImportFile(FileItem usersFile, boolean xep227Support) {
        try {
          InExporter exporter = XMLImportExportFactory.getExportInstance(xep227Support);
          
          return exporter.validate(usersFile.getInputStream());
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
            return false;
        }
    }
    
    private Document exportUsers(boolean xep227Support) {
      
      InExporter exporter = XMLImportExportFactory.getExportInstance(xep227Support);
      
      return exporter.exportUsers();
    }
    
}

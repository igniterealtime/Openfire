/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.web;

import net.sf.kraken.KrakenPlugin;
import net.sf.kraken.registration.Registration;
import net.sf.kraken.registration.RegistrationManager;
import net.sf.kraken.type.TransportType;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.PluginManager;
import org.xmpp.packet.JID;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * This provides the conduit through which all XMLRPC interface actions occur.
 *
 * @author Daniel Henninger
 */
public class XMLRPCConduit {

    static Logger Log = Logger.getLogger(XMLRPCConduit.class);

    ConfigManager configManager = new ConfigManager();

    public String authPassword = null;

    public XMLRPCConduit() {
        authPassword = JiveGlobals.getProperty("plugin.gateway.xmlrpc.password", null);
    }

    public void destroy() {
        authPassword = null;
    }

    private boolean verifyPassword(String password) {
        if (authPassword == null) {
            Log.error("XML-RPC authentication not set up on server.");
            return false;
        }
        if (!authPassword.equals(password)) {
            Log.warn("Unauthorized attempt on XML-RPC interface.");
            return false;
        }
        return true;
    }

    /**
     * Toggles whether a transport is enabled or disabled.
     *
     * @param password Auth password for making changes
     * @param transportName Name of the transport to be enabled or disabled (type of transport)
     * @return True or false if the transport is enabled after this call.
     */
    public boolean toggleTransport(String password, String transportName) {
        if (verifyPassword(password)) {
            return configManager.toggleTransport(transportName);
        }
        else {
            return false;
        }
    }

    /**
     * Adds a new registration
     *
     * @param password Auth password for making changes
     * @param user Username or full JID of user who is getting an account registered.
     * @param transportType Type of transport to add user to.
     * @param legacyUsername User's username on the legacy service.
     * @param legacyPassword User's password on the legacy service.
     * @param legacyNickname User's nickname on the legacy service.
     * @return Error message or "Success" on success.
     */
    public String addRegistration(String password, String user, String transportType, String legacyUsername, String legacyPassword, String legacyNickname) {
        if (!verifyPassword(password)) {
            return "Authorization failed!";
        }
        String results = configManager.addRegistration(user, transportType, legacyUsername, legacyPassword, legacyNickname);
        if (results == null) {
            return "Success";
        }
        else {
            return results;
        }
    }

    /**
     * Deletes a registration.
     *
     * @param password Auth password for making changes
     * @param user Username or full JID of user who is getting an account deleted.
     * @param transportType Type of transport to add user to.
     * @return Error message or "Success" on success.
     */
    public String deleteRegistration(String password, String user, String transportType) {
        if (!verifyPassword(password)) {
            return "Authorization failed!";
        }
        JID jid;
        if (user.contains("@")) {
            jid = new JID(user);
        }
        else {
            jid = new JID(user, XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null);
        }
        Collection<Registration> registrations = RegistrationManager.getInstance().getRegistrations(jid, TransportType.valueOf(transportType));
        if (registrations.isEmpty()) {
            // User is not registered with us.
            return "Unable to find registration to delete.";
        }
        Registration registration = registrations.iterator().next();
        String results = configManager.deleteRegistration((int)registration.getRegistrationID());
        if (results == null) {
            return "Success";
        }
        else {
            return results;
        }
    }

    /**
     * Updates a registration.
     *
     * @param password Auth password for making changes
     * @param user Username or full JID of user who is getting an account updated.
     * @param transportType Type of transport to add user to.
     * @param legacyUsername User's username on the legacy service.
     * @param legacyPassword User's password on the legacy service.
     * @param legacyNickname User's nickname on the legacy service.
     * @return Error message or "Success" on success.
     */
    public String updateRegistration(String password, String user, String transportType, String legacyUsername, String legacyPassword, String legacyNickname) {
        if (!verifyPassword(password)) {
            return "Authorization failed!";
        }
        JID jid;
        if (user.contains("@")) {
            jid = new JID(user);
        }
        else {
            jid = new JID(user, XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null);
        }
        Collection<Registration> registrations = RegistrationManager.getInstance().getRegistrations(jid, TransportType.valueOf(transportType));
        if (registrations.isEmpty()) {
            // User is not registered with us.
            return "Unable to find registration to update.";
        }
        Registration registration = registrations.iterator().next();
        String results = configManager.updateRegistration((int)registration.getRegistrationID(), legacyUsername, legacyPassword, legacyNickname);
        if (results == null) {
            return "Success";
        }
        else {
            return results;
        }
    }

    /**
     * Retrieve a list of all active/enabled transports.
     *
     * @param password Auth password for making changes
     * @return List of active transports.
     */
    public List<String> getActiveTransports(String password) {
        if (!verifyPassword(password)) {
            return Arrays.asList("Authorization failed!");
        }
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        KrakenPlugin plugin = (KrakenPlugin)pluginManager.getPlugin("kraken");
        List<String> activeTransports = new ArrayList<String>();
        for (String transport : plugin.getTransports()) {
            if (plugin.serviceEnabled(transport)) {
                activeTransports.add(transport);
            }
        }
        return activeTransports;
    }

    /**
    * Lists  the currently registered transports for a given User.
    * @param password Auth password for making changes
    * @param user Username or full JID of user whose registrations should be listed
    * @return a Collection of RegistrationBeans. In XML-RPC, this will convert into an array of structs.
    */
    public Collection<RegistrationBean> getRegistrations(String password, String user) {
         /*
          * the redstone xml-rpc library can convert collections into xml-rpc arrays and
          * load all properties from a java bean into an xml-rpc struct
          */
         if (!verifyPassword(password)) {
             return Collections.emptyList();
         }
         JID jid; 
         if (user.contains("@")) {
             jid = new JID(user);
         }
         else {
             jid = new JID(user, XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null);
         }
         Collection<Registration> registrations = RegistrationManager.getInstance().getRegistrations(jid);
         Collection<RegistrationBean> result = new LinkedList<RegistrationBean>();

         /*
          * copy the values of Registration objects
          * into RegistrationBean objects. Unfortunately the Registration bean is too complex to send
          * it directly.  There is also a bug in redstone xml-rpc, it assumes that there is a getter for
          * a property if there is a setter, too.
          */
         for (Registration r: registrations) {
                result.add(new RegistrationBean(r));
         }
         return result;
    }
    
    /**
     * Lists  the currently registered transports for all Users.
     * @param password Auth password for making changes
     * @return a Map of <user,Collection of RegistrationBeans>. In XML-RPC, this will convert into an array of structs.
     */
     public Map<String, Collection<RegistrationBean>> getAllRegistrations(String password) {
          /*
           * the redstone xml-rpc library can convert collections into xml-rpc arrays and
           * load all properties from a java bean into an xml-rpc struct
           */
          if (!verifyPassword(password)) {
              return null;
          }
          Collection<Registration> registrations = RegistrationManager.getInstance().getRegistrations();
          Map<String, Collection<RegistrationBean>> result = new HashMap<String, Collection<RegistrationBean>>();

          for(Registration reg : registrations) {
              Collection<RegistrationBean> coll = result.get(reg.getJID().getNode()); 
              if(coll == null) {
                  coll = new LinkedList<RegistrationBean>();
                  result.put(reg.getJID().getNode(), coll);
              }
              coll.add(new RegistrationBean(reg));
          }
          return result;
     }
}

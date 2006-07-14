/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.roster;

import java.io.Serializable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.jivesoftware.wildfire.gateway.Gateway;
import org.jivesoftware.wildfire.gateway.util.BackgroundThreadFactory;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.packet.JID;

/**
 * Responsible for managing:
 * 
 * - ForeignContact (legacy system identity)
 * - Registered users JID and credentials for the gateway
 * 
 * @author Noah Campbell
 *
 */
public class PersistenceManager implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The contactManager.
     *
     * @see ContactManager
     */
    private ContactManager contactManager = null;

    /**
     * The registrar.
     *
     * @see Registrar
     */
    private Registrar registrar = null;

    /**
     * The gateway.
     *
     * @see Gateway
     */
    @SuppressWarnings("unused")
    private Gateway gateway;

    /**
     * Factory for accessing the various RosterManagers.
     * 
     * @author Noah Campbell
     */
    public final static class Factory {

        /**
         * The <code>PersistanceManager</code> associated with a particular 
         * <code>Gateway</code>
         *
         * @see Factory
         */
        private final static Map<Gateway, PersistenceManager> instances = 
            new HashMap<Gateway, PersistenceManager>();

        /**
         * Given a <code>Gateway</code> return a <code>PersistenceManager</code>
         * @param gateway
         * @return persistenceManager returns a <code>PersistenceManager</code>
         * @see PersistenceManager
         */
        public static synchronized PersistenceManager get(Gateway gateway) {
            PersistenceManager rm = instances.get(gateway);
            if(rm == null) {
                rm = new PersistenceManager(gateway);
                instances.put(gateway, rm);
            }
            return rm;
        }

    }

    /** The db file. */
    private final File db;

    /**
     * Construct a new <code>PersistanceManager</code>.
     * 
     * @param gateway
     */
    private PersistenceManager(Gateway gateway) {
        this.gateway = gateway;
        db = new File("/tmp/." + this.gateway.getName().toLowerCase() + ".dat");
        load(gateway);
        timer.scheduleAtFixedRate(archiver, 5, 5, TimeUnit.SECONDS);
        
    }

    /**
     * Unregister a JID.
     * @param jid
     */
    public void remove(JID jid) {
        String bareJID = jid.toBareJID();
        try {
            NormalizedJID njid = NormalizedJID.wrap(jid);
            contactManager.remove(njid);
            registrar.remove(jid);
        }
        catch (Exception e) {
            Log.error("Unable to remove " + bareJID + ": " + e.getLocalizedMessage());
        }
    }

    /**
     * Load the roster manager (we simply read a binary file from the file system).
     * @param gateway 
     */
    private void load(Gateway gateway) {
        ContactManager contactManager = null;
        Registrar registrar = null;
        try {
            /**
             * The key is stored in the registry so the key is as secure as the
             * registry is secure.
             */
            byte[] rawKey = Preferences.systemNodeForPackage(this.getClass()).getByteArray(".key", null);
            
            if (rawKey == null) {
                Log.error(LocaleUtils.getLocalizedString("persistencemanager.nokey", "gateway"));
                return;
            }
            
            SecretKeySpec key = new SecretKeySpec(rawKey, "AES");
            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.DECRYPT_MODE, key);
            
            CipherInputStream cis = new CipherInputStream(new FileInputStream(db), c);
                        
            ObjectInputStream is = new ObjectInputStream(cis);
            contactManager = (ContactManager)is.readObject() ;
            registrar = (Registrar) is.readObject() ;
            is.close();
        }
        catch (Exception e) {
            if (db.exists()) {
                db.delete();
            }
            Log.warn(LocaleUtils.getLocalizedString("persistencemanager.loadrosterfailed", "gateway"), e);
        }
        finally {
            this.contactManager = (contactManager != null) ? contactManager : new ContactManager();
            this.registrar = (registrar != null) ? registrar : new Registrar();
            this.registrar.setGateway(gateway); // re-vitialize the registrar.
        }
    }

    /**
     * A timer for executing tasks related to PersistanceManager.
     *
     * @see java.util.concurrent.ScheduledExecutorService
     */
    private final ScheduledExecutorService timer = Executors.newScheduledThreadPool(1, new BackgroundThreadFactory());

    /**
     * Responsible for flushing the in-memory database.
     */
    private final Runnable archiver = new Runnable() {
        public void run() {
            try {
                store();
            }
            catch (Exception e) {
                Log.warn(LocaleUtils.getLocalizedString("persistencemanager.unabletoflush", "gateway"), e);
                e.printStackTrace();
            }
        }
    };

    /**
     * Write the contact manager and registrar to the file system.
     * 
     * @throws Exception
     */
    public synchronized void store() throws Exception {
        Preferences prefs = Preferences.systemNodeForPackage(this.getClass());

        byte[] rawKey = prefs.getByteArray(".key", null);
        if (rawKey == null) {
            Log.error(LocaleUtils.getLocalizedString("persistencemanager.gennewkey", "gateway"));
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            SecretKey key = kg.generateKey();
            rawKey = key.getEncoded();
            prefs.putByteArray(".key", rawKey);
        }

        SecretKeySpec key = new SecretKeySpec(rawKey, "AES");
        Cipher c = Cipher.getInstance("AES");
        c.init(Cipher.ENCRYPT_MODE, key);
        CipherOutputStream os = new CipherOutputStream(new FileOutputStream(db), c);
        ObjectOutputStream oos = new ObjectOutputStream(os);
        oos.writeObject(contactManager);
        oos.writeObject(registrar);
        oos.flush();
        oos.close();
    }

    /**
     * @return Returns the contactManager.
     */
    public ContactManager getContactManager() {
        return contactManager;
    }

    /**
     * @return Returns the registrar.
     */
    public Registrar getRegistrar() {
        return registrar;
    }

    /**
     * @see java.lang.Object#finalize()
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        this.timer.shutdownNow();
        Log.debug(LocaleUtils.getLocalizedString("persistencemanager.registrarFinalize", "gateway"));
    }

}

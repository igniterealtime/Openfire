/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken;

import net.sf.kraken.pseudoroster.PseudoRosterManager;
import net.sf.kraken.registration.RegistrationManager;
import net.sf.kraken.session.cluster.TransportSessionRouter;
import net.sf.kraken.type.TransportType;

import org.apache.log4j.PropertyConfigurator;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.io.SAXReader;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Set;

/**
 * Kraken plugin, which provides connectivity to IM networks that
 * don't support the XMPP protocol. 
 *
 * The entire plugin is referred to as the gateway, while individual
 * IM network mappings are referred to as transports.
 *
 * @author Daniel Henninger
 */
public class KrakenPlugin implements Plugin {

    private File pluginDirectory;
    private PluginManager pluginManager;
    private TransportSessionRouter sessionRouter;

    /**
     * Represents all configured transport handlers.
     */
    public Hashtable<String,TransportInstance> transports;

    public KrakenPlugin() {

    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        setLoggerProperty("log4j.appender.openfire", "net.sf.kraken.util.Log4JToOpenfireAppender");
        setLoggerProperty("log4j.appender.openfiredebug", "net.sf.kraken.util.DebugOnlyLog4JToOpenfireAppender");
        setLoggerProperty("log4j.net.sf.kraken", "TRACE, openfire");

        this.pluginDirectory = pluginDirectory;
        this.pluginManager = manager;
        
        // Check if the IM Gateway plugin is installed and stop loading this plugin if found
        File pluginDir = new File(JiveGlobals.getHomeDirectory(), "plugins");
        File[] jars = pluginDir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                String fileName = pathname.getName().toLowerCase();
                return (fileName.equalsIgnoreCase("gateway.jar"));
            }
        });
        if (jars.length > 0) {
            // Do not load this plugin since the original IM Gateway plugin is still installed
            System.out.println("IM Gateway plugin found. Stopping Kraken");
            throw new IllegalStateException("This plugin cannot run next to the IM Gateway plugin");
        }

        transports = new Hashtable<String,TransportInstance>();
        ComponentManager componentManager = ComponentManagerFactory.getComponentManager();
        sessionRouter = new TransportSessionRouter(this);

        /* Set up AIM transport. */
        transports.put("aim", new TransportInstance(TransportType.aim, LocaleUtils.getLocalizedString("gateway.aim.name", "kraken"), "net.sf.kraken.protocols.oscar.OSCARTransport", componentManager, sessionRouter));
        maybeStartService("aim");

        /* Set up ICQ transport. */
        transports.put("icq", new TransportInstance(TransportType.icq, LocaleUtils.getLocalizedString("gateway.icq.name", "kraken"), "net.sf.kraken.protocols.oscar.OSCARTransport", componentManager, sessionRouter));
        maybeStartService("icq");

        /* Set up IRC transport. */
        transports.put("irc", new TransportInstance(TransportType.irc, LocaleUtils.getLocalizedString("gateway.irc.name", "kraken"), "net.sf.kraken.protocols.irc.IRCTransport", componentManager, sessionRouter));
        maybeStartService("irc");

        /* Set up MSN transport. */
        transports.put("msn", new TransportInstance(TransportType.msn, LocaleUtils.getLocalizedString("gateway.msn.name", "kraken"), "net.sf.kraken.protocols.msn.MSNTransport", componentManager, sessionRouter));
        maybeStartService("msn");

        /* Set up Yahoo transport. */
        transports.put("yahoo", new TransportInstance(TransportType.yahoo, LocaleUtils.getLocalizedString("gateway.yahoo.name", "kraken"), "net.sf.kraken.protocols.yahoo.YahooTransport", componentManager, sessionRouter));
        maybeStartService("yahoo");

        /* Set up XMPP transport. */
        transports.put("xmpp", new TransportInstance(TransportType.xmpp, LocaleUtils.getLocalizedString("gateway.xmpp.name", "kraken"), "net.sf.kraken.protocols.xmpp.XMPPTransport", componentManager, sessionRouter));
        maybeStartService("xmpp");

        /* Set up GTalk transport. */
        transports.put("gtalk", new TransportInstance(TransportType.gtalk, LocaleUtils.getLocalizedString("gateway.gtalk.name", "kraken"), "net.sf.kraken.protocols.xmpp.XMPPTransport", componentManager, sessionRouter));
        maybeStartService("gtalk");

        /* Set up LiveJournal transport. */
        transports.put("livejournal", new TransportInstance(TransportType.livejournal, LocaleUtils.getLocalizedString("gateway.livejournal.name", "kraken"), "net.sf.kraken.protocols.xmpp.XMPPTransport", componentManager, sessionRouter));
        maybeStartService("livejournal");

        /* Set up SIMPLE transport. */
        transports.put("simple", new TransportInstance(TransportType.simple, LocaleUtils.getLocalizedString("gateway.simple.name", "kraken"), "net.sf.kraken.protocols.simple.SimpleTransport", componentManager, sessionRouter));
        maybeStartService("simple");

        /* Set up Gadu-Gadu transport. */
        transports.put("gadugadu", new TransportInstance(TransportType.gadugadu, LocaleUtils.getLocalizedString("gateway.gadugadu.name", "kraken"), "net.sf.kraken.protocols.gadugadu.GaduGaduTransport", componentManager, sessionRouter));
        maybeStartService("gadugadu");
        
        /* Set up QQ transport. */
        transports.put("qq", new TransportInstance(TransportType.qq , LocaleUtils.getLocalizedString("gateway.qq.name", "kraken"), "net.sf.kraken.protocols.qq.QQTransport", componentManager, sessionRouter));
        maybeStartService("qq");
        
        /* Set up SameTime transport. */
        transports.put("sametime", new TransportInstance(TransportType.sametime , LocaleUtils.getLocalizedString("gateway.sametime.name", "kraken"), "net.sf.kraken.protocols.sametime.SameTimeTransport", componentManager, sessionRouter));
        maybeStartService("sametime");
        
        /* Set up Facebook transport. */
        transports.put("facebook", new TransportInstance(TransportType.facebook , LocaleUtils.getLocalizedString("gateway.facebook.name", "kraken"), "net.sf.kraken.protocols.xmpp.XMPPTransport", componentManager, sessionRouter));
        maybeStartService("facebook");
        
        /* Set up MySpaceIM transport. */
        transports.put("myspaceim", new TransportInstance(TransportType.myspaceim , LocaleUtils.getLocalizedString("gateway.myspaceim.name", "kraken"), "net.sf.kraken.protocols.myspaceim.MySpaceIMTransport", componentManager, sessionRouter));
        maybeStartService("myspaceim");

        /* Set up RenRen transport. */
        transports.put("renren", new TransportInstance(TransportType.renren , LocaleUtils.getLocalizedString("gateway.renren.name", "kraken"), "net.sf.kraken.protocols.xmpp.XMPPTransport", componentManager, sessionRouter));
        maybeStartService("renren");
    }

    public void destroyPlugin() {
        for (TransportInstance trInstance : transports.values()) {
            trInstance.stopInstance();
        }
        try {
            RegistrationManager.getInstance().shutdown();
        }
        catch (NullPointerException e) {
            // Ok then, already gone?
        }
        try {
            PseudoRosterManager.getInstance().shutdown();
        }
        catch (NullPointerException e) {
            // Ok then, already gone?
        }
        try {
            sessionRouter.shutdown();
        }
        catch (NullPointerException e) {
            // Ok then, already gone?
        }
    }

    /**
     * Returns the plugin manager handling the plugin.
     *
     * @return plugin manager in question.
     */
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    /**
     * Starts a transport service, identified by subdomain.  The transport
     * service will only start if it is enabled.
     *
     * @param serviceName name of service to start.
     */
    private void maybeStartService(String serviceName) {
        TransportInstance trInstance = transports.get(serviceName);
        trInstance.startInstance();
    }

    /**
     * Enables a transport service, identified by subdomain.
     *
     * @param serviceName name of service to enable.
     */
    public void enableService(String serviceName) {
        TransportInstance trInstance = transports.get(serviceName);
        trInstance.enable();
    }

    /**
     * Disables a transport service, identified by subdomain.
     *
     * @param serviceName name of service to disable.
     */
    public void disableService(String serviceName) {
        TransportInstance trInstance = transports.get(serviceName);
        trInstance.disable();
    }

    /**
     * Returns the state of a transport service, identified by subdomain.
     *
     * @param serviceName name of service to check.
     * @return True of false if service is enabled.
     */
    public Boolean serviceEnabled(String serviceName) {
        TransportInstance trInstance = transports.get(serviceName);
        return trInstance.isEnabled();
    }

    /**
     * Returns the transport instance, identified by subdomain.
     *
     * @param serviceName name of service to get instance of.
     * @return Instance of service requested.
     */
    public TransportInstance getTransportInstance(String serviceName) {
        return transports.get(serviceName);
    }

    /**
     * Returns a list of transports (short names).
     *
     * @return Set of transports.
     */
    public Set<String> getTransports() {
        return transports.keySet();
    }

    /**
     * Returns the session router.
     *
     * @return Session router instance.
     */
    public TransportSessionRouter getSessionRouter() {
        return sessionRouter;
    }

    /**
     * Returns the web options config for the given transport, if it exists.
     *
     * @param type type of the transport we want the options config for.
     * @return XML document with the options config.
     */
    public Document getOptionsConfig(TransportType type) {
        // Load any custom-defined servlets.
        File optConf = new File(this.pluginDirectory, "web" + File.separator + "WEB-INF" +
            File.separator + "options" + File.separator + type.toString() + ".xml");
        Document optConfXML;
        try {
            FileReader reader = new FileReader(optConf);
            SAXReader xmlReader = new SAXReader();
            xmlReader.setEncoding("UTF-8");
            optConfXML = xmlReader.read(reader);
        }
        catch (FileNotFoundException e) {
            // Non-existent: Return empty config
            optConfXML = DocumentHelper.createDocument();
            optConfXML.addElement("optionsconfig");
        }
        catch (DocumentException e) {
            // Bad config: Return empty config
            optConfXML = DocumentHelper.createDocument();
            optConfXML.addElement("optionsconfig");
        }
        return optConfXML;
    }

    /**
     * Returns the web global options, if it exists.
     *
     * @return XML document with the options config.
     */
    public Document getOptionsConfig() {
        // Load any custom-defined servlets.
        File optConf = new File(this.pluginDirectory, "web" + File.separator + "WEB-INF" +
            File.separator + "options" + File.separator + "global.xml");
        Document optConfXML;
        try {
            FileReader reader = new FileReader(optConf);
            SAXReader xmlReader = new SAXReader();
            xmlReader.setEncoding("UTF-8");
            optConfXML = xmlReader.read(reader);
        }
        catch (FileNotFoundException e) {
            // Non-existent: Return empty config
            optConfXML = DocumentHelper.createDocument();
            optConfXML.addElement("optionsconfig");
        }
        catch (DocumentException e) {
            // Bad config: Return empty config
            optConfXML = DocumentHelper.createDocument();
            optConfXML.addElement("optionsconfig");
        }
        return optConfXML;
    }

    static final Properties log4jProperties = new Properties();

    public static Properties getLoggerProperties() {
        return log4jProperties;
    }

    public static void setLoggerProperty(String property, String setting) {
        log4jProperties.setProperty(property, setting);
        PropertyConfigurator.configure(log4jProperties);
    }

}

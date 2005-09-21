/**
 * 
 */
package org.jivesoftware.messenger.plugin.meter;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.jivesoftware.messenger.container.Plugin;
import org.jivesoftware.messenger.container.PluginManager;
import org.jivesoftware.messenger.plugin.meter.accumulator.Accumulator;
import org.jrobin.core.RrdDbPool;
import org.jrobin.core.RrdException;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public class MeterPlugin implements Plugin {

    
    /** The pluginDirectory. */
    @SuppressWarnings("unused")
    private File pluginDirectory;
    
    /** The manager. */
    @SuppressWarnings("unused")
    private PluginManager manager;
    
    /** The logger. */
    final static private Logger logger = Logger.getLogger("monitorplugin", "resources");
    
    
    /** The server. */
    private MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    
    /** The preferences. */
    private Preferences preferences;
    
    
    /** The rrdManager. */
    private RrdManager rrdManager;
    
    
    /** The executors. */
    ScheduledExecutorService executors = Executors.newScheduledThreadPool(10);
    
    /** The pool. */
    RrdDbPool pool = RrdDbPool.getInstance();
    
    /**
     * @see org.jivesoftware.messenger.container.Plugin#initializePlugin(org.jivesoftware.messenger.container.PluginManager, java.io.File)
     */
    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        this.manager = manager;
        this.pluginDirectory = pluginDirectory;
        
        File store = new File(pluginDirectory, "store");
        if(!store.exists()) {
            store.mkdirs();
        }
        
        try {
            rrdManager = new RrdManager(store.getCanonicalFile(), pool);
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        
        preferences = Preferences.systemNodeForPackage(MeterPlugin.class);
        String monitoredObjectNames = preferences.get("monitoredObjectNames", "java.lang:type=Threading;java.lang:type=Memory");
        for(String name : monitoredObjectNames.split("; ?")) {
            logger.log(Level.FINER, "initialize.objectname", name);
            try {
                ObjectName objectName = new ObjectName(name);
                Accumulator accum = rrdManager.create(objectName, server.getMBeanInfo(objectName));
               
                /**
                 * Schedule the accumulator.
                 */
                executors.scheduleAtFixedRate(accum, 0, 300, TimeUnit.SECONDS);
                
            } catch (MalformedObjectNameException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (NullPointerException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InstanceNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (RrdException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IntrospectionException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (ReflectionException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } 
        }
        
        
        
    }

    /**
     * @see org.jivesoftware.messenger.container.Plugin#destroyPlugin()
     */
    public void destroyPlugin() {
        
    }

}

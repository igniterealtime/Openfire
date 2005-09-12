/**
 * 
 */
package org.jivesoftware.messenger.plugin.monitor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.jrobin.annotations.Arc;
import org.jrobin.annotations.Archives;
import org.jrobin.annotations.Ds;
import org.jrobin.annotations.Rrd;
import org.jrobin.core.RrdDb;
import org.jrobin.core.RrdDbPool;
import org.jrobin.core.RrdDef;
import org.jrobin.core.RrdException;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public class RrdManager {
    
    
    /**
     * @return storeList
     */
    public static Map<String, RrdManager> listStores() {
        return Collections.unmodifiableMap(stores);
    }
    
    /** The stores. */
    private static Map<String, RrdManager> stores = new HashMap<String, RrdManager>();
    
    
    private final File store;
    private final RrdDbPool pool;
    private final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    
    /**
     * Construct a new <code>RrdManager</code>.
     *
     * @param store
     * @param pool
     * @throws IOException
     */
    public RrdManager(File store, RrdDbPool pool) throws IOException {
        this.store = store;
        this.pool = pool;
        
        if(!stores.containsKey(store.getName())) {
            stores.put(store.getName(), this);
        } else {
            logger.log(Level.WARNING, "rrdmanager.existingstore", store.getName());
        }
        
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("typeoverrides.config");
        try {
            overrides.load(is);
        } catch (Exception e) {
            logger.log(Level.WARNING, "rrdmanager.unabletoloadoverride");
        }
    }
    
    Properties overrides = new Properties();
    
    /** The logger. */
    final static private Logger logger = Logger.getLogger("RESOURCEMGR", "resources");
    
    
    /**
     * Returns a file name that is unique for the ObjectName.
     * 
     * @param name
     * @return fileName
     * @throws IOException 
     */
    public String getFileName(ObjectName name) throws IOException {
        return new File(store, "x" + digest(name.getCanonicalName()) + ".rrd").getCanonicalPath();
    }
    
    /** The key. */
    private static byte[] key = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x04, 0x03, 0x02, 0x01 };
    
    
    /**
     * @param buffer
     * @return
     */
    private static String digest(String buffer) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(buffer.getBytes());
            return new String(encodeHex(md5.digest(key)));
        } catch (NoSuchAlgorithmException e) {
        }
        return null;
    }
    
    /** 
     * Used to build output as Hex 
     */
    private static final char[] DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7',
           '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };
    
    public static char[] encodeHex(byte[] data) {

        int l = data.length;

           char[] out = new char[l << 1];

           // two characters form the hex value.
           for (int i = 0, j = 0; i < l; i++) {
               out[j++] = DIGITS[(0xF0 & data[i]) >>> 4 ];
               out[j++] = DIGITS[ 0x0F & data[i] ];
           }

           return out;
    }
    
    
    /** The accumulators. */
    public Map<ObjectName, Accumulator> accumulators = 
        new HashMap<ObjectName, Accumulator>();
    
    
    /**
     * Create an accumulator for the Rrd annotation and the ObjectName.
     * 
     * @param objectName
     * @param info 
     * @return accumulator
     * @throws Exception
     */
    public Accumulator create(ObjectName objectName, MBeanInfo info) throws Exception {
        
        if (accumulators.containsKey(objectName)) {
            return accumulators.get(objectName);
        } else {
            if (exists(objectName)) {
                Accumulator acc = new Accumulator(server, objectName, getFileName(objectName),
                        findDs(Class.forName(info.getClassName())), pool);
                accumulators.put(objectName, acc);
                return acc;
            } else {
                Accumulator acc = internalCreate(objectName, info);
                accumulators.put(objectName, acc);
                return acc;
            }
        }
        
    }



    private Accumulator internalCreate(ObjectName objectName, MBeanInfo info) throws ClassNotFoundException, RrdException, IOException {
        Class<?> forName = Class.forName(info.getClassName());
        
        if(!forName.isAnnotationPresent(Rrd.class)) {
            forName = Class.forName(overrides.getProperty(objectName.getCanonicalName()));
            logger.log(Level.INFO, "rrdmanager.createoverride", forName.getName());
        }
        
        Rrd rrd = forName.getAnnotation(Rrd.class);
        // handle when it's not there :)
        Archives archives = forName.getAnnotation(Archives.class);
        
        
        RrdDef def = new RrdDef(getFileName(objectName)); 
        def.setStep(rrd.step());
        
        if(archives != null) {
            for(Arc arc : archives.value()) {
                def.addArchive(arc.consolidationFunction().toString(), arc.xff(), arc.steps(), arc.rows());
                logger.log(Level.FINE, "rrdmanager.archiveadded", arc.consolidationFunction());
            }
        }
        
        List<Ds> anonDs = findDs(forName);
        for(Ds ds : anonDs) {
            def.addDatasource(ds.name(), ds.type().toString(), ds.heartbeat(), ds.minValue(), ds.maxValue());
            logger.log(Level.FINE, "rrdmanager.datasourceadded", new Object[]{ds.expr(), ds.name()});
        }
        
        /**
         * Store
         */
        RrdDb db = pool.requestRrdDb(def);
        pool.release(db);
        
        return new Accumulator(server, objectName, db.getPath(), anonDs, pool);
    }



    private List<Ds> findDs(Class<?> forName) {
        List<Ds> anonDs = new ArrayList<Ds>();
        Method[] methods = forName.getMethods();
        for(Method m : methods) {
            Ds ds = m.getAnnotation(Ds.class);
            if(ds != null) {
                anonDs.add(ds);
            }
        }
        return anonDs;
    }


    
    /**
     * @param objectName
     * @return boolean Does the rrd exist or not.
     * @throws IOException 
     */
    public boolean exists(ObjectName objectName) {
        
        try {
            return new File(getFileName(objectName)).exists();
        } catch (IOException e) {
           logger.log(Level.SEVERE, "rrdmanager.existsfailed");
           return false;
        }
    }
}

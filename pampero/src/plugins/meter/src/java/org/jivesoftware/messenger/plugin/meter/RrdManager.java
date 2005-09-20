/**
 * 
 */
package org.jivesoftware.messenger.plugin.meter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.relation.InvalidRelationTypeException;
import javax.management.relation.RelationService;
import javax.management.relation.RelationServiceMBean;
import javax.management.relation.RoleInfo;

import org.apache.commons.jxpath.JXPathIntrospector;
import org.jrobin.annotations.Arc;
import org.jrobin.annotations.Ds;
import org.jrobin.annotations.Rrd;
import org.jrobin.core.RrdDb;
import org.jrobin.core.RrdDbPool;
import org.jrobin.core.RrdDef;
import org.jrobin.core.RrdException;

/**
 * RrdManager is responsible for managing the RrdPools and the associated files
 * associated with that pool.
 * 
 * @author Noah Campbell
 * @version 1.0
 */
public class RrdManager {
    
    
    
    /** The JROBIN_TYPE_RELATION_SERVICE. */
    private static final String JROBIN_TYPE_RELATION_SERVICE = "jrobin:type=RelationService";

    /**
     * @return storeList
     */
    public static Map<String, RrdManager> listStores() {
        return Collections.unmodifiableMap(stores);
    }
    
    /** The stores. */
    private static Map<String, RrdManager> stores = new HashMap<String, RrdManager>();
    
    /** The store. */
    private final File store;
    
    /** The pool. */
    private final RrdDbPool pool;
    
    /** The server. */
    private static final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    
    /**
     * Construct a new <code>RrdManager</code>.
     *
     * @param store
     * @param pool
     * @throws IOException
     * @throws Exception 
     */
    public RrdManager(File store, RrdDbPool pool) throws IOException, Exception {
        this.store = store;
        this.pool = pool;
        
        if(!stores.containsKey(store.getName())) {
            stores.put(store.getName(), this);
        } else {
            logger.log(Level.WARNING, "rrdmanager.existingstore", store.getName());
        }
        
        startRelationshipService();
        
        JXPathIntrospector.registerDynamicClass(ObjectName.class, ObjectNamePropertyHandler.class);
        JXPathIntrospector.registerDynamicClass(CompositeData.class, CompositeDataPropertyHandler.class);
        
        String systemOverride = System.getProperty("org.jivesoftware.messenger.plugin.monitor.overrides");
        InputStream is = null;
        if(systemOverride != null && systemOverride.length() > 0) {
            is = new FileInputStream(systemOverride);
        } else {
            is = this.getClass().getClassLoader().getResourceAsStream("typeoverrides.config");
        }
        try {
            new Properties().load(is);
            
            Enumeration names = new Properties().propertyNames();
            for(; names.hasMoreElements(); ) {
                String name = (String)names.nextElement();
                try {
                    AccumulatorManager.registerOverride(new ObjectName(name), Class.forName(new Properties().getProperty(name)));
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Unable to register override: {0}", e.getLocalizedMessage());
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "rrdmanager.unabletoloadoverride");
        }
    }



    
    /**
     * @throws MalformedObjectNameException
     * @throws InstanceAlreadyExistsException
     * @throws MBeanRegistrationException
     * @throws NotCompliantMBeanException
     * @throws IllegalArgumentException
     * @throws ClassNotFoundException
     * @throws InvalidRelationTypeException
     */
    private void startRelationshipService() throws MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException, IllegalArgumentException, ClassNotFoundException, InvalidRelationTypeException {
        //      Create the Relation Service MBean
        ObjectName relSvcName = new ObjectName(JROBIN_TYPE_RELATION_SERVICE);
        RelationService relSvcObject = new RelationService(true);
        server.registerMBean(relSvcObject, relSvcName);
        
//      Create an MBean proxy for easier access to the Relation Service
        RelationServiceMBean relSvc = (RelationServiceMBean)
            MBeanServerInvocationHandler.newProxyInstance(server, relSvcName,
                                                          RelationServiceMBean.class,
                                                          false);
        RoleInfo[] roles = new RoleInfo[] {
                new RoleInfo("override", Object.class.getName()),
                new RoleInfo("target", Object.class.getName()),
                new RoleInfo("archive", String.class.getName())
        };
        
        relSvc.createRelationType("graph", roles);
        
    }
    
    /** The logger. */
    final static private Logger logger = Logger.getLogger("rrdmanager", "resources");
    
    
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
     * @return digest A Base64 encoded MD5 digest.
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
    
    
    /**
     * @param data
     * @return hexValue A char[].
     */
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
                Accumulator acc = new Accumulator(objectName, getFileName(objectName));
                accumulators.put(objectName, acc);
                return acc;
            } else {
                Accumulator acc = internalCreate(objectName);
                accumulators.put(objectName, acc);
                return acc;
            }
        }
        
    }

    
    /**
     * @param objectName
     * @return accumulator
     * @throws Exception 
     * @throws ClassNotFoundException 
     */
    private Accumulator internalCreate(ObjectName objectName) throws ClassNotFoundException, Exception {
        
        Class<?> forName = findClass(objectName);
        
        Rrd rrd = forName.getAnnotation(Rrd.class);
        
        RrdDef def = new RrdDef(getFileName(objectName)); 
        def.setStep(rrd.step());
        
        for(Arc arc : rrd.archives()) {
            def.addArchive(arc.consolidationFunction().toString(), arc.xff(), arc.steps(), arc.rows());
            logger.log(Level.FINE, "rrdmanager.archiveadded", arc.consolidationFunction());
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
        
        return new Accumulator(objectName, db.getPath());
    }

    
    /**
     * @param objectName The objectName to build the accumulator against.
     * @param info MBeanInfo to initialize the accumulator.
     * @return accumulator The accumulator.
     * @throws ClassNotFoundException
     * @throws RrdException
     * @throws IOException
     * @throws Exception
     */
    private Accumulator internalCreate(ObjectName objectName, String archive) throws ClassNotFoundException, RrdException, IOException, Exception {
        return new Accumulator(objectName, archive);
    }



    /**
     * @param objectName
     * @param info
     * @return cls A Class for the objectName that contains a the graphing information.
     * @throws ClassNotFoundException
     * @throws Exception
     */
    private Class<?> findClass(ObjectName objectName) throws ClassNotFoundException, Exception {
        Class<?> forName = Class.forName(server.getMBeanInfo(objectName).getClassName());
        
        if(!forName.isAnnotationPresent(Rrd.class)) {
            forName = AccumulatorManager.getOverride(objectName);
            logger.log(Level.INFO, "rrdmanager.createoverride", forName.getName());
        }
        return forName;
    }



    /**
     * @param forName
     * @return dss A list of Ds attributes.
     */
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

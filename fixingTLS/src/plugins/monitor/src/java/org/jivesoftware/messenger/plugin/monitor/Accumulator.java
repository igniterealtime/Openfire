/**
 * 
 */
package org.jivesoftware.messenger.plugin.monitor;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;

import org.jrobin.annotations.Ds;
import org.jrobin.core.Datasource;
import org.jrobin.core.RrdDb;
import org.jrobin.core.RrdDbPool;
import org.jrobin.core.RrdException;
import org.jrobin.core.Sample;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public class Accumulator implements Runnable {

    final static private Logger logger = Logger.getLogger("ACCUMULATOR", "resources");
    
    private final String dbPath;
    private final MBeanServer server;
    private final ObjectName objectName;
    private final RrdDbPool pool;
    private final List<Ds> ds;

    public Accumulator(MBeanServer server, ObjectName objectName, String db, List<Ds> anonDs, RrdDbPool pool) {
        this.dbPath = db;
        this.server = server;
        this.objectName = objectName;
        this.pool = pool;
        this.ds = anonDs;
    }
    
    /**
     * @see java.lang.Runnable#run()
     */
    public void run() {
        RrdDb db = null;
        try {
            db = pool.requestRrdDb(dbPath);
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            return;
        }
        try {
            
            Sample s = db.createSample((System.currentTimeMillis() + 500L)/ 1000L);
            for(Ds ds : this.ds) {
                String[] calls = ds.expr().split("\\.");
                if(calls.length == 0) {
                    calls = new String[1];
                    calls[0] = ds.name();
                }
                int i = 0;
                Object result = server.getAttribute(objectName, calls[i++]);
                
                if(result instanceof CompositeData) {
                    for(int j = i; j < calls.length;j++) {
                        CompositeData cd = (CompositeData) result;
                        result = cd.get(calls[j]);
                    }
                }
                
                if(result instanceof Number) {
                    Number n = (Number)result;
                    s.setValue(ds.name(), n.doubleValue());                        
                } else {
                    logger.log(Level.WARNING, "accumulator.resultnotnumber", 
                            new Object[]{result.getClass().getName(), 
                                result.toString(),
                                db.getPath()});
                }
                
            }
            s.update();
            
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (AttributeNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InstanceNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (MBeanException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ReflectionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (RrdException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            try {
                pool.release(db);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

}

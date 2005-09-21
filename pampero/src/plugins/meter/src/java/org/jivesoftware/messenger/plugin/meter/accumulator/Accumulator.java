/**
 * 
 */
package org.jivesoftware.messenger.plugin.meter.accumulator;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.ObjectName;

import org.jivesoftware.messenger.plugin.meter.UnableToAllocateAccumulator;
import org.jrobin.annotations.Arc;
import org.jrobin.annotations.Ds;
import org.jrobin.annotations.Rrd;
import org.jrobin.core.RrdDb;
import org.jrobin.core.RrdDbPool;
import org.jrobin.core.RrdDef;
import org.jrobin.core.RrdException;
import org.jrobin.core.Sample;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public class Accumulator implements AccumulatorMBean, Runnable {

    
    /** The logger. */
    final static private Logger logger = Logger.getLogger("ACCUMULATOR", "resources");
    
    
    /** The dbPath. */
    private final String dbPath;
    
    /** The objectName. */
    private final ObjectName objectName;
    
    /** The pool. */
    private final RrdDbPool pool;

    /**
     * Construct a new <code>Accumulator</code>.
     *
     * @param objectName
     * @param db
     */
    public Accumulator(ObjectName objectName, String db) {
        this.dbPath = db;
        this.objectName = objectName;
        this.pool = RrdDbPool.getInstance();
    }
    
    /** The lastTimestamp. */
    private long lastTimestamp = Long.MIN_VALUE;
    
    /** The count. */
    private long count = 0;
    
    /**
     * @see java.lang.Runnable#run()
     */
    public void run() {
        RrdDb db = null;
        try {
            db = pool.requestRrdDb(dbPath);
        } catch (IOException e1) {
            logger.log(Level.SEVERE, "accumulator.unabletoopenrrd", new Object[]{dbPath,
                    e1});
            return;
        } catch (RrdException e1) {
            logger.log(Level.SEVERE, "accumulator.unabletoprocessrrd", new Object[]{dbPath,
                    e1});
            return;
        }
        try {
            
            long timestamp = 0;
            Sample s = db.createSample();
            timestamp = s.getTime();
            
            
            AccumulatorHelper helper = (AccumulatorHelper) AccumulatorManager.getAccumulator(this.objectName);
            Map<String, Number> results = helper.getResults();
            for(String key : results.keySet()) {
                s.setValue(key, results.get(key).doubleValue());
            }
            
            if( timestamp == lastTimestamp) {
                logger.log(Level.INFO,"accumulator.rush", new Object[]{new Date(timestamp * 1000), 
                        this.objectName, this.dbPath, 
                        Thread.currentThread().getName(), 
                        Thread.currentThread().getId()});
                Thread.sleep(1000);
            }
            s.update();
            lastTimestamp = timestamp;
            count++;
            
        } catch (IOException e) {
            logger.log(Level.WARNING, "accumulator.ioexception", e);
        } catch (RrdException e) {
            logger.log(Level.WARNING, "accumulator.rrdexception", e);
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "accumulator.illegalargumentexceptin", e);
        } catch (SecurityException e) {
            logger.log(Level.WARNING, "accumulator.securityexception", e);
        } catch (InterruptedException e) {
            logger.log(Level.FINE, "accumulator.interrupted",e.getLocalizedMessage());
        } catch (UnableToAllocateAccumulator e) {
            logger.log(Level.WARNING, "accumulator.allocateaccumulatorfailed", e.getLocalizedMessage());
        } catch (AccumulatorDefinitionNotFoundException e) {
            logger.log(Level.WARNING, "accumulator.allocatornotfound", e.getLocalizedMessage());
        } finally {
            try {
                pool.release(db);
            } catch (Exception e) {
                logger.log(Level.WARNING, "accumulator.releasefailed", e); 
            }
        }

    }

    /**
     * @see org.jivesoftware.messenger.plugin.meter.accumulator.AccumulatorMBean#getPath()
     */
    public String getPath() {
        return this.getPath();
    }

    /**
     * @see org.jivesoftware.messenger.plugin.meter.accumulator.AccumulatorMBean#getSourceMBean()
     */
    public ObjectName getSourceMBean() {
        return this.objectName;
    }

    /**
     * @see org.jivesoftware.messenger.plugin.meter.accumulator.AccumulatorMBean#getTotalReads()
     */
    public long getTotalReads() {
        return count;
    }
}

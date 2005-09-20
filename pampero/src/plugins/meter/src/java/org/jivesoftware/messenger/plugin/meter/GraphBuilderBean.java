/**
 * 
 */
package org.jivesoftware.messenger.plugin.meter;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.jrobin.annotations.Rrd;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public class GraphBuilderBean {
    
    
    /**
     * Return all the ObjectNames
     * 
     * @return objectnames
     * @throws Exception 
     */
    public List<String> getChartableMBeans() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Set mbeans = server.queryNames(null, null);
        List<String> results = new ArrayList<String>(mbeans.size());
        for(Object mbean : mbeans) {
            ObjectName objectName = ((ObjectName)mbean);
            MBeanInfo info = server.getMBeanInfo(objectName);
            Rrd rrd = info.getClass().getAnnotation(Rrd.class);
            if(rrd != null)
                results.add(objectName.getCanonicalName());
        }
        return results;
    }
    
    public List<String> getStores() {
        return new ArrayList<String>(RrdManager.listStores().keySet());
    }
    
}

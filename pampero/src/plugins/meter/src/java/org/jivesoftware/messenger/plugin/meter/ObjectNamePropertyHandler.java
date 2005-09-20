/**
 * 
 */
package org.jivesoftware.messenger.plugin.meter;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.naming.OperationNotSupportedException;

import org.apache.commons.jxpath.DynamicPropertyHandler;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public class ObjectNamePropertyHandler implements DynamicPropertyHandler {

    
    /** The server. */
    private static final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    
    /** The logger. */
    final static private Logger logger = Logger.getLogger(
            "objectnamepropertyhandler", "resources");
    /**
     * @see org.apache.commons.jxpath.DynamicPropertyHandler#getPropertyNames(java.lang.Object)
     */
    public String[] getPropertyNames(Object object) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            ObjectName objectName = (ObjectName)object;
            MBeanInfo info = server.getMBeanInfo(objectName);
            MBeanAttributeInfo[] attrs = info.getAttributes();
            
            for(MBeanAttributeInfo attr : attrs) {
                list.add(attr.getName());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "unabletoevaluatepropertyname", e);
        }
        return list.toArray(new String[list.size()]);
    }

    /**
     * @see org.apache.commons.jxpath.DynamicPropertyHandler#getProperty(java.lang.Object, java.lang.String)
     */
    public Object getProperty(Object object, String propertyName) {
        try {
            return server.getAttribute((ObjectName) object, propertyName);
        } catch (AttributeNotFoundException e) {
            logger.log(Level.WARNING, "attributenotfound", e);
        } catch (InstanceNotFoundException e) {
            logger.log(Level.WARNING, "instancenotfound", e);
        } catch (MBeanException e) {
            logger.log(Level.WARNING, "mbeanexception", e);
        } catch (ReflectionException e) {
            logger.log(Level.WARNING, "reflectionexception", e);
        }
        return null;
    }

    /**
     * @see org.apache.commons.jxpath.DynamicPropertyHandler#setProperty(java.lang.Object, java.lang.String, java.lang.Object)
     */
    public void setProperty(@SuppressWarnings("unused") Object object, 
            @SuppressWarnings("unused") String propertyName, 
            @SuppressWarnings("unused") Object value) {
        throw new RuntimeException(new OperationNotSupportedException("unable to perform this opertion."));
    }

}

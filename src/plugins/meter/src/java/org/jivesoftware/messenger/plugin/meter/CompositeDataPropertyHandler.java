/**
 * 
 */
package org.jivesoftware.messenger.plugin.meter;

import java.util.ArrayList;
import java.util.Set;

import javax.management.openmbean.CompositeData;

import org.apache.commons.jxpath.DynamicPropertyHandler;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public class CompositeDataPropertyHandler implements DynamicPropertyHandler {

    /**
     * @see org.apache.commons.jxpath.DynamicPropertyHandler#getPropertyNames(java.lang.Object)
     */
    public String[] getPropertyNames(Object object) {
        ArrayList<String> names = new ArrayList<String>(20);
       if(object instanceof CompositeData) {
           CompositeData cd = (CompositeData)object;
           Set keys = cd.getCompositeType().keySet();
           for(Object key : keys) {
               names.add((String) key);
           }
       }
       return names.toArray(new String[names.size()]);
    }

    /**
     * @see org.apache.commons.jxpath.DynamicPropertyHandler#getProperty(java.lang.Object, java.lang.String)
     */
    public Object getProperty(Object object, String propertyName) {
        if(object instanceof CompositeData) {
            CompositeData cd = (CompositeData)object;
            return cd.get(propertyName);
        }
        return null;
    }

    /**
     * @see org.apache.commons.jxpath.DynamicPropertyHandler#setProperty(java.lang.Object, java.lang.String, java.lang.Object)
     */
    public void setProperty(@SuppressWarnings("unused") Object object, 
            @SuppressWarnings("unused") String propertyName, 
            @SuppressWarnings("unused") Object value) {
        throw new UnsupportedOperationException("read-only");

    }

}

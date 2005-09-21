/**
 * 
 */
package org.jivesoftware.messenger.plugin.meter.accumulator;

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.commons.jxpath.CompiledExpression;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.JXPathException;
import org.jivesoftware.messenger.plugin.meter.UnableToAllocateAccumulator;
import org.jrobin.annotations.Ds;
import org.jrobin.annotations.Rrd;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public class AccumulatorManager {
 
    
    /** The overrides. */
    private static Map<ObjectName, Class> overrides = new HashMap<ObjectName, Class>();
    
    /**
     * @param name
     * @return cls The class that overrides the default MBeanInfo.getClassName
     */
    public static Class getOverride(ObjectName name) {
        return overrides.get(name);
    }
    
    /**
     * @param objectName
     * @param cls
     */
    public static void registerOverride(ObjectName objectName, Class cls) {
        overrides.put(objectName, cls);
    }
    
    
    /**
     * @param objectName
     */
    public static void deregisterOverride(ObjectName objectName) {
        overrides.remove(objectName);
    }
    
    /**
     * @author Noah Campbell
     * @version 1.0
     */
    static final class AccumulatorProxy implements InvocationHandler {
        
        /** The mbean server. */
        private final static MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        /**
         * Construct a new <code>AccumulatorProxy</code>.
         *
         * @param objectName
         * @param override
         * @throws AccumulatorDefinitionNotFoundException 
         */
        @SuppressWarnings("unchecked")
        public AccumulatorProxy(ObjectName objectName, Class override) throws AccumulatorDefinitionNotFoundException {
            this.objectName = objectName;
            Class cls = null;
            if(override == null) {
                try {
                    Class overrideClass = overrides.get(objectName);
                    if(overrideClass != null) {
                        cls = overrideClass;
                    } else {
                        // last ditch effort.
                        cls = Class.forName(server.getMBeanInfo(objectName).getClassName());
                    }
                } catch (Exception e) {
                    throw new AccumulatorDefinitionNotFoundException(e);
                }
            } else {
                cls = override;
            }            
            
            if(cls == null) {
                throw new AccumulatorDefinitionNotFoundException("Unable to locate class");
            }
            
            if(cls.getAnnotation(Rrd.class) == null) {
                throw new IllegalArgumentException("No @Rrd specified");
            }
            
            accumulator = java.lang.reflect.Proxy.newProxyInstance(
                    Thread.currentThread().getContextClassLoader(),
                    new Class[]{cls, AccumulatorHelper.class},
                    this);
            
            this.mbeanClass = cls;
            context = JXPathContext.newContext(this.objectName);
        }
        
        /**
         * Construct a new <code>AccumulatorProxy</code>.
         *
         * @param name
         * @throws Exception
         */
        public AccumulatorProxy(ObjectName name) throws Exception {
            this(name, null);
        }
       
        
        /** The accumulator. */
        private final Object accumulator;
        
        /** The objectName. */
        private final ObjectName objectName;
        
        /** The mbeanClass. */
        private final Class mbeanClass;
        
        /** The context. */
        private JXPathContext context = null;
//        private Map<Method, CompiledExpression> methodCache = new HashMap<Method, CompiledExpression>();
        
        
        /**
         * @return accumulator The accumulator.
         */
        public Object getAccumulator() {
            return this.accumulator;
        }
        
        /**
         * @see java.lang.reflect.InvocationHandler#invoke(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
         */
        public Object invoke(@SuppressWarnings("unused") Object proxy, Method method, @SuppressWarnings("unused") Object[] args)
                throws Throwable {
            
            if(method.getName().equals("getResults")) {
                return getResults();
            }
            
            try {
                Ds ds = method.getAnnotation(Ds.class);
                return extract(ds);
            } catch (Exception e) {
                return 0;
            }
        }
        
        
        
        /** ZERO.  Declared once, used many. */
        private static final Number ZERO = new Double(0.0d);
        
        /**
         * @param ds The \@Ds to extract.
         * @return number The value or 0 if the result is not of type Number.
         */
        private Number extract(Ds ds) {
            
            try {
                CompiledExpression e = JXPathContext.compile(ds.expr());
                Object result = e.getValue(context);
                if(result != null) {
                    logger.log(Level.INFO, "accumulator.exprresults", new Object[]{ds.name(),
                            ds.expr(), result});
                    return (Number) result;
                } else {
                    logger.log(Level.WARNING, "accumulatorproxy.nullresult", 
                            new Object[] { ds.name(), ds.expr() });
                }
            } catch (JXPathException e) {
                logger.log(Level.WARNING, "accumulator.getvaluefailed", 
                      new Object[]{ds.name(),
                      ds.expr(),
                      e.getLocalizedMessage()});
            } catch (Exception e) {
                logger.log(Level.WARNING, "accumulatorproxy.unabletoextract", 
                        new Object[]{ds.name(), ds.expr(), e.getLocalizedMessage()});
            } 
            
            return ZERO;
        }
        
        
        /**
         * @return map A map of results.
         */
        private Map<String, Number> getResults() {
            Map<String, Number> results = new HashMap<String, Number>();
            List<Ds> dss = findDs(this.mbeanClass);
            for(Ds ds : dss) {
                results.put(ds.name(), extract(ds));
            }
            return results;
        }
        
        
        
        /**
         * @param forName
         * @return dss List of Ds.
         */
        private List<Ds> findDs(Class<?> forName) {
            List<Ds> anonDs = new ArrayList<Ds>();
            Method[] methods = forName.getMethods();
            for(Method m : methods) {
                Ds ds = m.getAnnotation(Ds.class);
                if(ds != null) {
                   if(m.getReturnType().isPrimitive() || m.getReturnType().isAssignableFrom(Number.class)) {
                        anonDs.add(ds);
                    } else {
                        logger.log(Level.WARNING, "accumulatorproxy.invalidreturntype", ds.name());
                    }
                }
            }
            return anonDs;
        }
    }
    
    
    /** The logger. */
    final static private Logger logger = Logger.getLogger("accumulatormanager",
            "resources");
    
    
    /** The accumulators. */
    private static Map<ObjectName, AccumulatorProxy> accumulators = new ConcurrentHashMap<ObjectName, AccumulatorProxy>();
    
    
    /**
     * @param objectName
     * @param override
     * @return accumulator
     * @throws UnableToAllocateAccumulator 
     * @throws AccumulatorDefinitionNotFoundException 
     */
    @SuppressWarnings("unused")
    public static Object getAccumulator(ObjectName objectName, Class override) throws UnableToAllocateAccumulator, AccumulatorDefinitionNotFoundException {
        if(!accumulators.containsKey(objectName)) {
            accumulators.put(objectName, new AccumulatorProxy(objectName, override));
        }
        return accumulators.get(objectName).getAccumulator();
    }
    
    
    /**
     * @param objectName
     * @return accumulator Return the accumulator.
     * @throws AccumulatorDefinitionNotFoundException 
     * @throws UnableToAllocateAccumulator 
     */
    public static Object getAccumulator(ObjectName objectName) throws UnableToAllocateAccumulator, AccumulatorDefinitionNotFoundException {
        return getAccumulator(objectName, null);
    }
}

package org.jivesoftware.openfire.plugin;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.jivesoftware.openfire.plugin.service.MonitoringAPI;

import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

/**
 * The Class JerseyWrapper.
 */
public class JerseyWrapper extends ServletContainer {

    private static final long serialVersionUID = 4807992231163442643L;

    /** The Constant RESOURCE_CONFIG_CLASS_KEY. */
    private static final String RESOURCE_CONFIG_CLASS_KEY = "com.sun.jersey.config.property.resourceConfigClass";

    /** The Constant RESOURCE_CONFIG_CLASS. */
    private static final String RESOURCE_CONFIG_CLASS = "com.sun.jersey.api.core.PackagesResourceConfig";

    /** The config. */
    private static Map<String, Object> config;

    /** The prc. */
    private static PackagesResourceConfig prc;

    static {
        config = new HashMap<String, Object>();
        config.put(RESOURCE_CONFIG_CLASS_KEY, RESOURCE_CONFIG_CLASS);
        config.put("com.sun.jersey.api.json.POJOMappingFeature", true);
        prc = new PackagesResourceConfig(JerseyWrapper.class.getPackage().getName());
        prc.setPropertiesAndFeatures(config);

        prc.getClasses().add(MonitoringAPI.class);

        prc.getClasses().add(RESTExceptionMapper.class);
    }

    /**
     * Instantiates a new jersey wrapper.
     */
    public JerseyWrapper() {
        super(prc);
    }

    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
    }

    @Override
    public void destroy() {
        super.destroy();
    }
}

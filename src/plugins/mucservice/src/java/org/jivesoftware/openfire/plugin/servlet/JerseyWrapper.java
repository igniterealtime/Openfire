package org.jivesoftware.openfire.plugin.servlet;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.service.MUCRoomAdminsService;
import org.jivesoftware.openfire.service.MUCRoomMembersService;
import org.jivesoftware.openfire.service.MUCRoomOutcastsService;
import org.jivesoftware.openfire.service.MUCRoomOwnersService;
import org.jivesoftware.openfire.service.MUCRoomService;

import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

/**
 * The Class JerseyWrapper.
 */
public class JerseyWrapper extends ServletContainer {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /** The Constant SERVLET_URL. */
    private static final String SERVLET_URL = "mucservice/*";

    /** The Constant SCAN_PACKAGE_DEFAULT. */
    private static final String SCAN_PACKAGE_DEFAULT = JerseyWrapper.class.getPackage().getName();

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
        prc = new PackagesResourceConfig(SCAN_PACKAGE_DEFAULT);
        prc.setPropertiesAndFeatures(config);
        prc.getProperties().put("com.sun.jersey.spi.container.ContainerRequestFilters",
                "org.jivesoftware.openfire.plugin.servlet.AuthFilter");
        prc.getClasses().add(MUCRoomService.class);
        prc.getClasses().add(MUCRoomOwnersService.class);
        prc.getClasses().add(MUCRoomAdminsService.class);
        prc.getClasses().add(MUCRoomMembersService.class);
        prc.getClasses().add(MUCRoomOutcastsService.class);
        
        prc.getClasses().add(RESTExceptionMapper.class);
    }

    /**
     * Instantiates a new jersey wrapper.
     */
    public JerseyWrapper() {
        super(prc);
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
     */
    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        // Exclude this servlet from requering the user to login
        AuthCheckFilter.addExclude(SERVLET_URL);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.jersey.spi.container.servlet.ServletContainer#destroy()
     */
    @Override
    public void destroy() {
        super.destroy();
        // Release the excluded URL
        AuthCheckFilter.removeExclude(SERVLET_URL);
    }
}

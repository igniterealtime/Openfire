package org.jivesoftware.openfire.plugin.rest.service;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.plugin.rest.exceptions.RESTExceptionMapper;

import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

/**
 * The Class JerseyWrapper.
 */
public class JerseyWrapper extends ServletContainer {

	/** The Constant AUTHFILTER. */
	private static final String AUTHFILTER = "org.jivesoftware.openfire.plugin.rest.AuthFilter";

	/** The Constant CONTAINER_REQUEST_FILTERS. */
	private static final String CONTAINER_REQUEST_FILTERS = "com.sun.jersey.spi.container.ContainerRequestFilters";

	/** The Constant RESOURCE_CONFIG_CLASS_KEY. */
	private static final String RESOURCE_CONFIG_CLASS_KEY = "com.sun.jersey.config.property.resourceConfigClass";

	/** The Constant RESOURCE_CONFIG_CLASS. */
	private static final String RESOURCE_CONFIG_CLASS = "com.sun.jersey.api.core.PackagesResourceConfig";

	/** The Constant SCAN_PACKAGE_DEFAULT. */
	private static final String SCAN_PACKAGE_DEFAULT = JerseyWrapper.class.getPackage().getName();

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 1L;

	/** The Constant SERVLET_URL. */
	private static final String SERVLET_URL = "restapi/*";

	/** The config. */
	private static Map<String, Object> config;

	/** The prc. */
	private static PackagesResourceConfig prc;
	
	/** The Constant JERSEY_LOGGER. */
	private final static Logger JERSEY_LOGGER = Logger.getLogger("com.sun.jersey");

	static {
		JERSEY_LOGGER.setLevel(Level.SEVERE); 
		config = new HashMap<String, Object>();
		config.put(RESOURCE_CONFIG_CLASS_KEY, RESOURCE_CONFIG_CLASS);
		prc = new PackagesResourceConfig(SCAN_PACKAGE_DEFAULT);
		prc.setPropertiesAndFeatures(config);
		prc.getProperties().put(CONTAINER_REQUEST_FILTERS, AUTHFILTER);

		prc.getClasses().add(RestAPIService.class);
		
		prc.getClasses().add(MUCRoomService.class);
		prc.getClasses().add(MUCRoomOwnersService.class);
		prc.getClasses().add(MUCRoomAdminsService.class);
		prc.getClasses().add(MUCRoomMembersService.class);
		prc.getClasses().add(MUCRoomOutcastsService.class);
		
		prc.getClasses().add(UserServiceLegacy.class);
		prc.getClasses().add(UserService.class);
		prc.getClasses().add(UserRosterService.class);
		prc.getClasses().add(UserGroupService.class);
		prc.getClasses().add(UserLockoutService.class);

		prc.getClasses().add(GroupService.class);

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

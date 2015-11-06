package org.jivesoftware.openfire.plugin.rest.service;

import java.util.HashMap;
import java.util.Map;

import java.lang.ClassNotFoundException;
import java.lang.Class;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.plugin.rest.exceptions.RESTExceptionMapper;
import org.jivesoftware.util.JiveGlobals;

import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

import org.apache.log4j.Logger;

/**
 * The Class JerseyWrapper.
 */
public class JerseyWrapper extends ServletContainer {
	
	/** The Constant CUSTOM_AUTH_PROPERTY_NAME */
	private static final String CUSTOM_AUTH_PROPERTY_NAME = "plugin.restapi.customAuthFilter";

	/** The Constant AUTHFILTER. */
	private static final String AUTHFILTER = "org.jivesoftware.openfire.plugin.rest.AuthFilter";
	
	/** The Constant CORSFILTER. */
	private static final String CORSFILTER = "org.jivesoftware.openfire.plugin.rest.CORSFilter";

	/** The Constant CONTAINER_REQUEST_FILTERS. */
	private static final String CONTAINER_REQUEST_FILTERS = "com.sun.jersey.spi.container.ContainerRequestFilters";
	
	/** The Constant CONTAINER_RESPONSE_FILTERS. */
	private static final String CONTAINER_RESPONSE_FILTERS = "com.sun.jersey.spi.container.ContainerResponseFilters";

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

	private static Logger LOGGER = Logger.getLogger(JerseyWrapper.class);
	
	static {
		
		// Check if custom AuthFilter is available
		String customAuthFilterClassName = JiveGlobals.getProperty(CUSTOM_AUTH_PROPERTY_NAME);
		String pickedAuthFilter = AUTHFILTER;
		
		
		try {
			if(customAuthFilterClassName != null) {
				LOGGER.info("Trying to set a custom authentication filter for restAPI plugin with classname: " + customAuthFilterClassName);
				Class.forName(customAuthFilterClassName, false, JerseyWrapper.class.getClassLoader());
				pickedAuthFilter = customAuthFilterClassName;
			}
		} catch (ClassNotFoundException e) {
			LOGGER.info("No custom auth filter found for restAPI plugin! Still using the default one");
        }
		
		config = new HashMap<String, Object>();
		config.put(RESOURCE_CONFIG_CLASS_KEY, RESOURCE_CONFIG_CLASS);
		prc = new PackagesResourceConfig(SCAN_PACKAGE_DEFAULT);
		prc.setPropertiesAndFeatures(config);		
		prc.getProperties().put(CONTAINER_REQUEST_FILTERS, pickedAuthFilter);
		prc.getProperties().put(CONTAINER_RESPONSE_FILTERS, CORSFILTER);

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
		prc.getClasses().add(SessionService.class);
		prc.getClasses().add(MsgArchiveService.class);
		prc.getClasses().add(StatisticsService.class);
		prc.getClasses().add(MessageService.class);

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

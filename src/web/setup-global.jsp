<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="java.lang.reflect.Method,
                 java.beans.PropertyDescriptor,
                 java.sql.Connection,
                 org.jivesoftware.database.DbConnectionManager,
                 java.io.File,
                 java.sql.Statement,
                 java.sql.SQLException,
                 java.util.Map,
                 org.jivesoftware.util.ClassUtils" %>

<%  // Figure out if we've already run setup:

	boolean doSetup = false;
    //
    try {
        // ServiceLookup lookup = ServiceLookupFactory.getServiceLookup();
        Class serviceLookupFactoryClass = loadClass("org.jivesoftware.messenger.container.ServiceLookupFactory");
        Method getLookupMethod = serviceLookupFactoryClass.getMethod("getServiceLookup",null);
        Object serviceLookupObj = getLookupMethod.invoke(serviceLookupFactoryClass,null);

        // Container container = (Container)lookup.lookup(Container.class);
        Method lookupMethod = serviceLookupObj.getClass().getMethod("lookup",new Class[]{java.lang.Class.class});
        Object containerObj = lookupMethod.invoke(serviceLookupObj,new Class[]{org.jivesoftware.messenger.container.Container.class});

        // boolean isSetup = container.isSetupMode()
        Method isSetupModeMethod = containerObj.getClass().getMethod("isSetupMode",null);
        Object isSetupObj = isSetupModeMethod.invoke(containerObj,null);
        boolean setupMode = ((Boolean)isSetupObj).booleanValue();

        if (setupMode) {
            doSetup = true;
        }
    }
    catch (Throwable t) {
        //t.printStackTrace();
        doSetup = true;
    }

    if (!doSetup) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }

    // embedded mode?
    boolean embeddedMode = false;
    try {
        ClassUtils.forName("org.jivesoftware.messenger.container.starter.ServerStarter");
        embeddedMode = true;
    }
    catch (Exception ignored) {}

    // sidebar var for sidebar page - it has to be global.
    boolean showSidebar = true;
%>

<%! // Trys to load a class 3 different ways.
    Class loadClass(String className) throws ClassNotFoundException {
        Class theClass = null;
        try {
            theClass = Class.forName(className);
        }
        catch (ClassNotFoundException e1) {
            try {
                theClass = Thread.currentThread().getContextClassLoader().loadClass(className);
            }
            catch (ClassNotFoundException e2) {
                theClass = getClass().getClassLoader().loadClass(className);
            }
        }
        return theClass;
    }

    final PropertyDescriptor getPropertyDescriptor(PropertyDescriptor[] pd, String name) {
        for (int i=0; i<pd.length; i++) {
            if (name.equals(pd[i].getName())) {
                return pd[i];
            }
        }
        return null;
    }

    boolean testConnection(Map errors) {
        boolean success = true;
        Connection con = null;
        try {
            con = DbConnectionManager.getConnection();
            if (con == null) {
                success = false;
                errors.put("general","A connection to the database could not be "
                    + "made. View the error message by opening the "
                    + "\"[jiveHome]" + File.separator + "logs" + File.separator + "error.log\" log "
                    + "file, then go back to fix the problem.");
            }
            else {
            	// See if the Jive db schema is installed.
            	try {
            		Statement stmt = con.createStatement();
            		// Pick an arbitrary table to see if it's there.
            		stmt.executeQuery("SELECT * FROM jiveID");
            		stmt.close();
            	}
            	catch (SQLException sqle) {
                    success = false;
                    errors.put("general","The Jive Messenger database schema does not "
                        + "appear to be installed. Follow the installation guide to "
                        + "fix this error.");
            	}
            }
        }
        catch (Exception ignored) {}
        finally {
            try {
        	    con.close();
            } catch (Exception ignored) {}
        }
        return success;
    }
%>
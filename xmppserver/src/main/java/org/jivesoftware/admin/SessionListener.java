package org.jivesoftware.admin;

import org.jivesoftware.util.JiveGlobals;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import javax.servlet.annotation.WebListener;

/**
 * A http session listener that implements a configurable session timeout.<p>
 *
 * The session timeout is taken from the property {@code adminConsole.sessionTimeout}, the default is 30min.
 *
 * @author Guido Jaekel, guido.jaekel@gmx.de
 */

@WebListener
public class SessionListener implements HttpSessionListener {

     private static final int sessionTimeout = JiveGlobals.getIntProperty( "adminConsole.sessionTimeout", 30*60 ); // secs


     public void sessionCreated(HttpSessionEvent event){
         event.getSession().setMaxInactiveInterval(sessionTimeout);
     }


     public void sessionDestroyed(HttpSessionEvent event) {
     }

}

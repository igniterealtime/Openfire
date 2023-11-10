package org.jivesoftware.admin;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import jakarta.servlet.annotation.WebListener;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * A http session listener that implements a configurable session timeout.<p>
 *
 * The session timeout is taken from the property {@code adminConsole.sessionTimeout}, the default is 30min.
 *
 * @author Guido Jaekel, guido.jaekel@gmx.de
 */

@WebListener
public class SessionListener implements HttpSessionListener {

     public static final SystemProperty<Duration> SESSION_TIMEOUT = SystemProperty.Builder.ofType(Duration.class)
                                                                        .setKey("adminConsole.sessionTimeout")
                                                                        .setChronoUnit(ChronoUnit.SECONDS)
                                                                        .setDefaultValue( Duration.ofMinutes(30) )
                                                                        .setMinValue(Duration.ZERO)
                                                                        .setMaxValue(Duration.ofSeconds(Integer.MAX_VALUE) )
                                                                        .setDynamic(true)
                                                                        .build();

     public void sessionCreated(HttpSessionEvent event){
         event.getSession().setMaxInactiveInterval((int) SESSION_TIMEOUT.getValue().toSeconds());
     }


     public void sessionDestroyed(HttpSessionEvent event) {
     }

}

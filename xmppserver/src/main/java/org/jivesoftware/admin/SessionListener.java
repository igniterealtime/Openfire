package org.jivesoftware.admin;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import javax.servlet.annotation.WebListener;
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
         // Using Duration#getSeconds() isn't ideal, as that ignores part of the duration (the sign, as well as the
         // nano-seconds). However, as the value can expected to be a positive integer, this data can safely be ignored.
         // TODO: After moving to Java 9 or higher, this should be replaced with Duration#toSeconds()
         event.getSession().setMaxInactiveInterval((int) SESSION_TIMEOUT.getValue().getSeconds());
     }


     public void sessionDestroyed(HttpSessionEvent event) {
     }

}

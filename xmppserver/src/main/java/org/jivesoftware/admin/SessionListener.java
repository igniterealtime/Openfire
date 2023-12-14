/*
 * Copyright (C) 2021-2022 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
         event.getSession().setMaxInactiveInterval((int) SESSION_TIMEOUT.getValue().toSeconds());
     }


     public void sessionDestroyed(HttpSessionEvent event) {
     }

}

/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License version 2 as 
 * published by the Free Software Foundation and distributed hereunder 
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this 
 * code. 
 */

package com.sun.voip.server;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class BuildDate {

    private static final String BUILD_DATE = "/com/sun/voip/server/builddate.txt";

    private BuildDate() {
    }

    public static String getBuildDate() {
    InputStream in = BuildDate.class.getResourceAsStream(BUILD_DATE);

        if (in == null) {
            return "Can't read " + BUILD_DATE;
        }

        in = new BufferedInputStream(in);

    int bytesAvailable = 0;

        try {
            bytesAvailable = in.available();
        } catch (IOException e) {
            return e.getMessage();
        }

    byte[] buf = new byte[bytesAvailable];

    try {
        in.read(buf, 0, bytesAvailable);
    } catch (IOException e) {
            return e.getMessage();
        }

    return new String(buf).trim();
    }

}

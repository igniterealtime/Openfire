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

package com.sun.voip;

import java.lang.reflect.Constructor;

import java.util.prefs.Preferences;

public class PlcFactory {

    private static PlcFactory plcFactory;

    private PlcFactory() {
    }

    public static PlcFactory getInstance() {
	if (plcFactory == null) {
	    plcFactory = new PlcFactory();
	}
	
	return plcFactory;
    }

    public Plc createPlc() {
	return createPlc(null);
    }

    public Plc createPlc(String s) {
	if (s != null) {
	    try {
                Class plcClass = Class.forName(s);
                Class[] params = new Class[] { };

                Constructor constructor = plcClass.getConstructor(params);

                if (constructor != null) {
                    Object[] args = new Object[] { };

                    return (Plc) constructor.newInstance(args);
		}

                Logger.println("constructor not found for: " + s);
            } catch (Exception e) {
                Logger.println("Error loading '" + s + "': " + e.getMessage());
            }
	}

	return new PlcCompress();
    }

}

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

public class PlcCompress implements Plc {
    private String id;

    public void setId(String id) {
	this.id = id;
    }

    public void reset() {
    }

    public void addPacket(JitterObject jo) {
    }   

    public JitterObject repair(JitterObject jo) {
	/*
	 * Nothing to do here.  return null so JitterManager will 
	 * get next packet
	 */
        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println(id + ":  missing packet compressed out "
                 + (jo.sequence & 0xffff));
        }

	return null;
    }

}

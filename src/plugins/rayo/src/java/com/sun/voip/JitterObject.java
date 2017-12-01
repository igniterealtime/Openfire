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

/*
 * The JitterManager maintains a list of JitterObjects.
 * data is kept as Object because it can be either a byte[] when used
 * byte the softphone or int[] when used by the bridge.
 * 
 * Only the JitterManager creates JitterObjects and it has two
 * separate insertPacket() methods, one with a byte[] argument and
 * the other with a int[] argument.
 */
public class JitterObject {

    public int sequence;
    public boolean isMissing;
    public Object data;

    public JitterObject(int sequence, boolean isMissing, Object data) {
    this.sequence = sequence;
    this.isMissing = isMissing;
    this.data = data;
    }

    public String toString() {
        return "sequence " + sequence
            + ", isMissing " + isMissing
            + ", data " + data;
    }

}

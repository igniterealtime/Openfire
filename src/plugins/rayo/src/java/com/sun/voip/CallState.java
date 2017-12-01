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
 * Call state
 */
public class CallState {

    public static final int UNINITIALIZED = 0;
    public static final int INVITED       = 1;
    public static final int ANSWERED      = 2;
    public static final int ESTABLISHED   = 3;
    public static final int ENDING        = 4;
    public static final int ENDED         = 5;
    public static final int INCOMING      = 6;

    private static final int LAST_STATE = 6;
    private int state;

    private String[] stateString = {
    "000 UNINITIALIZED",
    "100 INVITED",
    "110 ANSWERED",
    "200 ESTABLISHED",
    "290 ENDING",
    "299 ENDED"
    };

    public CallState() {
    state = UNINITIALIZED;
    }

    public CallState(int state) {
    this.state = state;
    }

    public int getState() {
        return state;
    }

    public boolean equals(int state) {
    return this.state == state;
    }

    public String toString() {
    if (state < 0 || state > LAST_STATE) {
            return ("Unknown state " + state);
    }

    return stateString[state];
    }

}

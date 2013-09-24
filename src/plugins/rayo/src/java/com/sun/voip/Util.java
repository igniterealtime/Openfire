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

/**
 * This class is used for debugging to dump arrays of bytes.
 */
public class Util {
    private Util() {
    }

    /**
     * Print a byte array in hex.
     *
     * @param data byte array of data to dump
     * @param length integer number of bytes to dump
     * XXX length needs to be a multiple of 16.
     */
    public static synchronized void dump(String msg, byte[] data, int offset,
	    int length) {

	Logger.println(msg);

	String s = "";

	String t = "";

	char[] v = new char[1];

	for (int i = 0; i < length; i++) {
	    if ((i % 16) == 0) {
		if (i > 0) {
		    Logger.println(s + "\t" + t);
		}

		s = Integer.toHexString(i + offset) + ":  ";

		t = "";
	    }

	    s += Integer.toHexString(data[i] & 0xff) + " ";

	    v[0] = (char)(data[i + offset] & 0xff);

	    if (v[0] < 0x20 || v[0] > 0x7e) {
		t += ".";
	    } else {
		t += String.copyValueOf(v);
	    }
	}
	Logger.println(s + "\t" + t);
    }

    public static synchronized void dump(String msg, int[] data, int offset,
	    int length) {
	
	Logger.println(msg);

	String s = "";

	String t = "";

	char[] v = new char[1];

	for (int i = 0; i < length; i++) {
	    if ((i % 8) == 0) {
		if (i > 0) {
		    Logger.println(s + "\t" + t);
		}

		s = Integer.toHexString(i + offset) + ":  ";

		t = "";
	    }

	    s += Integer.toHexString(data[i + offset] & 0xffff) + " ";

	    v[0] = (char)((data[i + offset] >> 8) & 0xff);

	    if (v[0] < 0x20 || v[0] > 0x7e) {
		t += ".";
	    } else {
		t += String.copyValueOf(v);
	    }

	    v[0] = (char) (data[i + offset] & 0xff);

	    if (v[0] < 0x20 || v[0] > 0x7e) {
		t += ".";
	    } else {
		t += String.copyValueOf(v);
	    }
	}
	Logger.println(s + "\t" + t);
    }

}

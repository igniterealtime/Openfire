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

/*
 * Create a Ticker using the specifed Ticker implementation class.
 */
public class TickerFactory {

    private static TickerFactory tickerFactory;

    private TickerFactory() {
    }

    public static TickerFactory getInstance() {
	if (tickerFactory != null) {
	    return tickerFactory;	
	}

	tickerFactory = new TickerFactory();
	return tickerFactory;
    }

    public Ticker createTicker(String tickerClassName, String id) 
	    throws TickerException {

	Ticker ticker = null;

	if (tickerClassName != null && tickerClassName.length() > 0) {
	    try {
	        Class tickerClass = Class.forName(tickerClassName);

	        Class[] params = new Class[] {String.class};

                Constructor constructor = tickerClass.getConstructor(params);

                if (constructor != null) {
                    Object[] args = new Object[] {
		        new String("TickerTest") 
	            };

                    ticker = (Ticker) (constructor.newInstance(args));
                } else {
	            Logger.println(id 
		        + ":  Unable to find constructor for class " 
	                + tickerClassName);
	        }
	    } catch (Exception e) {
	        Logger.println(id + ":  Unable to create ticker for class " 
	        + tickerClassName + " " + e.getMessage());
	    }
	}

	if (ticker == null) {
	    ticker = new TickerSleep(id);

	    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	        Logger.println(id + ":  using default ticker TickerSleep");
	    }
	}

	return ticker;
    }

}

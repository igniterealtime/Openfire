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
 * Create a Ticker using the specifed Ticker implementation class.
 */
public class TickerTest {

    private String id;
    private int timePeriod;
    private int duration;
    private int statFrequency;
    private String tickerClassName;

    private Ticker ticker;

    public TickerTest() {
    }

    private void usage() {
    System.out.println("Usage:  java com.sun.voip.TickerTest "
    + "\t\t-t <tick period in milliseconds> [-d <duration in seconds>]"
    + "\t\t[-s <stat frequency in ticker calls>] [-c TickerClassName]");

    System.exit(1);
    }

    public static void main(String args[]) {
    TickerTest tickerTest = new TickerTest();

    try {
        tickerTest.initialize(args);
    } catch (TickerException e) {
        System.out.println(e.getMessage());
        System.exit(1);
    }

    tickerTest.runTest();
    }

    private void initialize(String[] args) throws TickerException {
    for (int i = 0; i < args.length; i++) {
        if (args[i].equalsIgnoreCase("-t")) {
        i++;

        timePeriod = getIntArg(args, i);
        } else if (args[i].equalsIgnoreCase("-d")) {
        i++;

        duration = getIntArg(args, i);
        } else if (args[i].equalsIgnoreCase("-s")) {
        i++;
        
        statFrequency = getIntArg(args, i);
        } else if (args[i].equalsIgnoreCase("-c")) {
        i++;

        if (i >= args.length) {
                usage();
        }

        tickerClassName = args[i];
        } else {
        usage();
        }
    }

    if (timePeriod == 0) {
        timePeriod = 20;
    }

    if (duration == 0) {
        duration = 10;
    }

    if (statFrequency == 0) {
        statFrequency = 200;
    }

    if (tickerClassName == null) {
        tickerClassName = "com.sun.voip.TickerSleep";
    }

    TickerFactory tickerFactory = TickerFactory.getInstance();

        ticker = tickerFactory.createTicker(tickerClassName, "TickerTest");
    }

    private void runTest() {
    Logger.println("Running ticker test with " + tickerClassName);

    long start = System.currentTimeMillis();

    long elapsed = 0;

    int n = 0;

    ticker.arm(timePeriod, timePeriod);

    while (elapsed < duration * 1000) {
        try {
            ticker.tick();
        } catch (TickerException e) {
        Logger.println("tick() failed! " + e.getMessage());
        System.exit(1);
        }

        elapsed = System.currentTimeMillis() - start;

        n++;

        if ((n % statFrequency) == 0) {
        ticker.printStatistics();
        }
    }

    ticker.disarm();
    }

    private int getIntArg(String[] args, int i) {
    if (i >= args.length) {
        usage();
    }

    int n = 0;

    try {
        n = Integer.parseInt(args[i]);

        if (n <= 0) {	
        Logger.println("Number must be positive:  " + args[i]);
        System.exit(1);
        }

    } catch (NumberFormatException e) {
        Logger.println("Invalid integer " + args[i]
        + " " + e.getMessage());
        System.exit(1);
    }

    return n;
    }

}

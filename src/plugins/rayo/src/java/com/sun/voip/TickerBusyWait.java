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

public class TickerBusyWait implements Ticker {

    private String id;
    private int timePeriod;

    private long startTime;
    private int count;
    private int overSlept;
    private int underSlept;
    private int minDrift;
    private int maxDrift;
    private int totalDrift;

    private long lastTime;

    private int[] waitDistribution = new int[200];
    private int totalWaitTime = 0;

    private boolean armed;

    public TickerBusyWait(String id) {
	this.id = id;
    }

    public void arm(long delay, long timePeriod) {
	this.timePeriod = (int) timePeriod;

	startTime = System.currentTimeMillis();
	lastTime = startTime;

	minDrift = 0;
	maxDrift = 0;
	totalDrift = 0;
	count = 0;

	armed = true;
    }

    public void disarm() {
	armed = false;
    }
	
    public void tick() throws TickerException {
	if (!armed) {
	    throw new TickerException(id + " not armed");
	}

	long start = System.currentTimeMillis();

	int drift = getDrift();

	totalDrift += drift;

	if (drift > maxDrift) {
	    maxDrift = drift;
	}

	if (drift < minDrift) {
	    minDrift = drift;
	}

	count++;

	if (Logger.logLevel == -99) {
	    Logger.println("drift " + drift);
	    Logger.logLevel = 3;
	}

	if (drift > timePeriod) { 
	    overSlept++;
	    return;
	} else if (drift < -timePeriod) {
	    underSlept++;
	}


	do {
	    drift = getDrift();

            while ((int) (System.currentTimeMillis() - lastTime) <
	            timePeriod) {

	        Thread.yield();
	    }
	} while (drift < -timePeriod);

	updateWaitDistribution(start);
    }

    private int getDrift() {
	int actualElapsed = (int) (System.currentTimeMillis() - startTime);

	int expectedElapsed = count * timePeriod;

	return actualElapsed - expectedElapsed;
    }

    public int getMinDrift() {
	return minDrift;
    }

    public int getMaxDrift() {
	return maxDrift;
    }

    private void updateWaitDistribution(long start) {
	int elapsed = (int) (System.currentTimeMillis() - start);	

	if (elapsed < 0) {
	    elapsed = 0;
	} else if (elapsed >= waitDistribution.length) {
	    elapsed = waitDistribution.length - 1;
	}

	waitDistribution[elapsed]++;

	totalWaitTime += elapsed;
    }

    public double getAvg() {
	return ((double)(System.currentTimeMillis() - startTime)) / count;
    }
	
    public void printStatistics() {
	if (count > 0) {
	    Logger.println(id
	        + " average time between ticks " 
	        + ((float)(System.currentTimeMillis() - startTime) / 
		(float)count) + " ms");

	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("Total calls to TickerBusyWait " + count);
	        Logger.println("OverSlept count " + overSlept);
	        Logger.println("UnderSlept count " + underSlept);
    	        Logger.println("Minimum drift " + getMinDrift());
	        Logger.println("Maximum drift " + getMaxDrift());
	        Logger.println("Average drift " + (totalDrift / count));

	        Logger.println("");

	        Logger.println(id + " Wait time distribution");

	        Logger.println(id + " ms\tCount");
           
	        for (int i = 0; i < waitDistribution.length; i++) {
                    if (waitDistribution[i] != 0) {
		        Logger.println(id + " " + i + "\t" 
			    + waitDistribution[i]);
                    }
                }

                Logger.println("");
	    }
	}
    }

    public static void main(String args[]) {
	TickerBusyWait tickerBusyWait = new TickerBusyWait("Test");

	tickerBusyWait.arm(RtpPacket.PACKET_PERIOD, RtpPacket.PACKET_PERIOD);

	while (true) {
	    try {
	        tickerBusyWait.tick();
	    } catch (TickerException e) {
		System.out.println("tick() failed! " + e.getMessage());
		System.exit(1);
	    }

	    Logger.println(" avg " + tickerBusyWait.getAvg());
	}
    }

}

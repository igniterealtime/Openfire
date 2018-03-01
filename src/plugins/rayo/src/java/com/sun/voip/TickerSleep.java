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

public class TickerSleep implements Ticker {

    private String id;
    private int timePeriod;

    private int sleepTime;

    private long startTime;
    private int count;
    private int overSlept;
    private int underSlept;
    private int minDrift;
    private int maxDrift;
    private int totalDrift;

    private long lastTime;

    private int[] sleepDistribution = new int[200];

    private boolean armed;

    public TickerSleep(String id) {
    this.id = id;
    }

    public void arm(long delay, long timePeriod) {
    this.timePeriod = (int) timePeriod;

        String s = System.getProperty("os.name");

        if (s.startsWith("Mac OS X") == true) {
        /*
         * Mac OS has millisecond sleep granuarity
         * and we can take advantage of that.
         */
        sleepTime = (int) timePeriod;
    } else {
        sleepTime = (int) timePeriod / 2;
    }

    if (sleepTime == 0) {
        sleepTime = 1;
    }

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
        throw new TickerException(id + ":  ticker not armed");
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

    if (drift > sleepTime) { 
        overSlept++;
        return;
    } else if (drift < -sleepTime) {
        underSlept++;
    }

    do {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
        }
        drift = getDrift();
    } while (drift < -sleepTime);

    updateSleepDistribution(start);
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

    private void updateSleepDistribution(long start) {
    int elapsed = (int) (System.currentTimeMillis() - start);	

    if (elapsed < 0) {
        elapsed = 0;
    } else if (elapsed >= sleepDistribution.length) {
        elapsed = sleepDistribution.length - 1;
    }

    sleepDistribution[elapsed]++;
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
            Logger.println("Total calls TickerSleep " + count);
            Logger.println("OverSlept count " + overSlept);
            Logger.println("UnderSlept count " + underSlept);
                Logger.println("Minimum drift " + getMinDrift());
            Logger.println("Maximum drift " + getMaxDrift());
            Logger.println("Average drift " + (totalDrift / count));

            Logger.println("");

            Logger.println(id + " Sleep time distribution");

            Logger.println(id + " ms\tCount");
           
            for (int i = 0; i < sleepDistribution.length; i++) {
                    if (sleepDistribution[i] != 0) {
                Logger.println(id + " " + i + "\t\t" 
                + sleepDistribution[i]);
                    }
                }

                Logger.println("");
        }
    }
    }

    public static void main(String args[]) {
    TickerSleep tickerSleep = new TickerSleep("Test");

    tickerSleep.arm(RtpPacket.PACKET_PERIOD, RtpPacket.PACKET_PERIOD);

    while (true) {
        try {
            tickerSleep.tick();
        } catch (TickerException e) {
        Logger.println("tick() failed! " + e.getMessage());
        }

        Logger.println(" avg " + tickerSleep.getAvg());
    }
    }

}

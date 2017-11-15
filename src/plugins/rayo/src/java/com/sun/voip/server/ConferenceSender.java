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

import com.sun.voip.Logger;
import com.sun.voip.RtpPacket;
import com.sun.voip.Ticker;
import com.sun.voip.TickerException;
import com.sun.voip.TickerFactory;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.NoSuchElementException;

/**
 * Send data to conference members
 */
public class ConferenceSender extends Thread {
    /*
     * Tuneable parameters (mostly for debugging)
     */
    private static int senderThreads;

    private ArrayList conferenceList;

    private boolean done = false;

    /* Statistics */

    private int packetsSent = 0;

    private long totalSendTime;

    private static double averageSendTime;

    private static double lastMaxSendTime;

    private static long startTime;
    private static double timeBetweenSends;

    Ticker ticker;

    private static ArrayList<SenderCallbackListener> senderCallbackList =
        new ArrayList<SenderCallbackListener>();

    public ConferenceSender(ArrayList conferenceList) {
    this.conferenceList = conferenceList;

    setName("TheLoneSender");
    initialize();
    }

    public ConferenceSender(ConferenceManager conferenceManager) {
    conferenceList = new ArrayList();
    conferenceList.add(conferenceManager);

    setName("Sender-" + conferenceManager.getId());

    initialize();
    }

    private void initialize() {
    senderThreads = Runtime.getRuntime().availableProcessors();

    setPriority(Thread.MAX_PRIORITY);
    start();
    }

    public static void addSenderCallbackListener(SenderCallbackListener listener) {
        synchronized (senderCallbackList) {
            senderCallbackList.add(listener);
        }
    }

    /**
     * The job of the conference sender is to send a voice data packet
     * to each conference member every 20 ms.
     */
    public void run() {
    String tickerClassName = System.getProperty("com.sun.voip.TICKER");

    try {
            TickerFactory tickerFactory = TickerFactory.getInstance();

            ticker = tickerFactory.createTicker(tickerClassName, getName());
    } catch (TickerException e) {
        Logger.println(e.getMessage());
        end();
        return;
        }

    /*
     * Pump out data every <timeBetweenPackets> ms to each member.
     */
    ticker.arm(RtpPacket.PACKET_PERIOD, RtpPacket.PACKET_PERIOD);

        long sendTime = 0;
        long maxSendTime = 0;

    while (!done) {
        long startTime = System.nanoTime();

            for (SenderCallbackListener listener : senderCallbackList) {
        try {
                    listener.senderCallback();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.println("Sender callback failed!  "
            + e.getMessage());
        }
            }

        sendDataToConferences();

        int elapsed = (int) (System.nanoTime() - startTime);

        if (elapsed > maxSendTime) {
            maxSendTime = elapsed;
        }

        totalSendTime += elapsed;
        sendTime += elapsed;

        try {
                ticker.tick();
            } catch (TickerException e) {
                Logger.println(getName() + " tick() failed! " + e.getMessage());
        end();
        break;
            }

        if (ConferenceManager.getTotalMembers() == 0) {
        resetStatistics();
        sendTime = 0;
        maxSendTime = 0;
        continue;
        }

        packetsSent++;

        if ((packetsSent % 250) == 0) {
        averageSendTime = sendTime / 1000000000. / 250.;

        lastMaxSendTime = maxSendTime / 1000000000.;

            String s = getName()
                + " time to send a packet to " + ConferenceManager.getTotalMembers()
                + " members in last 5 seconds is " + (sendTime / 1000000000.)
            + " seconds, average time " + averageSendTime + " seconds "
            + ", maxSendTime " + lastMaxSendTime
            + ", members speaking " + CallHandler.getTotalSpeaking();

            if (Logger.logLevel >= Logger.LOG_DETAIL) {
                Logger.println(s);
            } else {
            Logger.writeFile(s);
            }

        if (packetsSent > 0) {
            timeBetweenSends = (System.nanoTime() - startTime) /
            1000000000. / 250.;
        }

        startTime = System.nanoTime();
            maxSendTime = 0;
        sendTime = 0;
        }
    }

    ticker.disarm();
    }

    public static double getAverageSendTime() {
        return averageSendTime;
    }

    public static double getMaxSendTime() {
    return lastMaxSendTime;
    }

    public static double getTimeBetweenSends() {
    return timeBetweenSends;
    }

    private void sendDataToConferences() {
    /*
     * Build a memberList containing the members of all conferences.
     */
    ArrayList memberList = new ArrayList();

    for (int i = 0; i < conferenceList.size(); i++) {
        ConferenceManager conferenceManager = (ConferenceManager) conferenceList.get(i);

        //ArrayList ml = (ArrayList) conferenceManager.getMemberList();

        //memberList.addAll(ml);

        /*
         * Take a snapshot of member data
         */
        //for (int j = 0; j < ml.size(); j++) {
            //    ConferenceMember member = (ConferenceMember) ml.get(j);
        //	member.saveCurrentContribution();
        //}

        /*
         * Take a snapshot of all members and all whisper groups
         * in the conference.
         */
        synchronized (conferenceManager) {
                WGManager wgManager = conferenceManager.getWGManager();

        if (wgManager == null) {
            continue;	// not initialized yet
        }

            ArrayList whisperGroups = wgManager.getWhisperGroups();

            synchronized(whisperGroups) {
                    for (int j = 0; j < whisperGroups.size(); j++) {
                        WhisperGroup whisperGroup = (WhisperGroup)
                            whisperGroups.get(j);

                ArrayList ml = whisperGroup.getMembers();

                for (int k = 0; k < ml.size(); k++) {
                ConferenceMember member = (ConferenceMember)
                    ml.get(k);

                if (member.getWhisperGroup() == whisperGroup) {
                /*
                 * Member is whispering in this whisper group
                 */
                try {
                        member.saveCurrentContribution();
                } catch (Exception e) {
                    e.printStackTrace();

                    Logger.println(
                    "conf " + getName() + ":  "
                    + " can't save contribution for "
                    + "member " + member);

                    member.getCallHandler().cancelRequest(
                    "Unexpected Exception");
                    continue;
                }

                    memberList.add(member);
                }
                        }

            /*
             * At this point, the whisper group has the data
             * from each whisperer mixed in a buffer.
             */
            try {
                            whisperGroup.saveCurrentContribution();
            } catch (Exception e) {
                            e.printStackTrace();

                Logger.println("conf " + getName() + ":  "
                                + " can't save contribution for whisper group "
                + whisperGroup);
            }
            }
        }
            }
    }

        /*
         * Send data to each member in every conference.
         */
    if (memberList.size() == 0) {
        return;
    }

    sendDataToMembers(memberList);

    for (int i = 0; i < memberList.size(); i++) {
        ConferenceMember member = (ConferenceMember) memberList.get(i);

        member.invalidateCurrentContribution();
    }
    }

    private ArrayList workerThreads = new ArrayList();

    private ConcurrentLinkedQueue workToDo = new ConcurrentLinkedQueue();

    private void sendDataToMembers(ArrayList memberList) {
    if (Logger.logLevel == -55) {
        for (int i = 0; i < memberList.size(); i++) {
        ConferenceMember m = (ConferenceMember) memberList.get(i);

            Logger.println("conf " + getName() + ": " + m);
        }
        Logger.println("wt size " + workerThreads.size()
        + " sender threads " + senderThreads);
    }

    if (workerThreads.size() > 1 && workerThreads.size() != senderThreads) {
            /*
             * Stop old threads
         * XXX We could just stop the extra threads or add new ones
         * rather than stopping all of them.
             */
        if (Logger.logLevel == -55) {
                Logger.println("Stopping sender worker threads "
                    + workerThreads.size());
        }

            for (int i = 0; i < workerThreads.size(); i++) {
                ((WorkerThread) workerThreads.get(i)).done();
            }

        workerThreads.clear();
    }

    if (senderThreads <= 1) {
        singleThreadSendDataToMembers(memberList);
        return;
    }

    CountDownLatch doneSignal = new CountDownLatch(workerThreads.size());

    if (workerThreads.size() != senderThreads) {
            /*
             * Start new threads
             */
            for (int i = 0; i < senderThreads; i++) {
                workerThreads.add(new WorkerThread(i, doneSignal));
            }

            Logger.println("Started " + senderThreads + " sender threads");
    }

    workToDo.clear();

    for (int i = 0; i < memberList.size(); i++) {
        ConferenceMember member = (ConferenceMember) memberList.get(i);

        if (member.getMemberSender().memberIsReadyForSenderData()) {
        workToDo.add(member);
        }
    }

    /*
     * Start all of the worker threads
     */
    for (int i = 0; i < workerThreads.size(); i++) {
        WorkerThread workerThread = (WorkerThread) workerThreads.get(i);

        workerThread.setLatch(doneSignal);

        synchronized (workerThread) {
        workerThread.notify();
        }
    }

    if (!done) {
        try {
            doneSignal.await();	// wait for all to finish
        } catch (InterruptedException e) {
        }
    }
    }

    private void singleThreadSendDataToMembers(ArrayList memberList) {
        for (int i = 0; i < memberList.size(); i++) {
            ConferenceMember member = (ConferenceMember)
                        memberList.get(i);

        if (!member.getMemberSender().memberIsReadyForSenderData()) {
        continue;
        }

        long start = 0;

            if (Logger.logLevel == -33) {
                start = System.nanoTime();
            }

        try {
                member.sendData();
        } catch (Exception e) {
        e.printStackTrace();

        Logger.println("Can't send data to " + member + " "
            + e.getMessage());

        member.getCallHandler().cancelRequest("Unexpected Exception");
        }

            if (Logger.logLevel == -33) {
                Logger.println("Sender sendDataToOneMember time "
                    +  member + " "
                    + ((System.nanoTime() - start) / 1000000000.) + " seconds");

        Logger.logLevel = 3;
            }
    }
    }

    class WorkerThread extends Thread {
    private boolean done;
    private CountDownLatch doneSignal;

    public WorkerThread(int i, CountDownLatch doneSignal) {
        this.doneSignal = doneSignal;

        setName("Sender-WorkerThread-" + i + "-" + getName());
        setPriority(Thread.MAX_PRIORITY);
        start();
    }

    public void setLatch(CountDownLatch doneSignal) {
        this.doneSignal = doneSignal;
    }

    public void done() {
        done = true;
        interrupt();
    }

        public void run() {
        while (!done) {
        try {
                ConferenceMember member = (ConferenceMember)
            workToDo.remove();

            try {
                    member.sendData();
                } catch (Exception e) {
            e.printStackTrace();

            Logger.println("Can't send data to " + member
                     + " " + e.getMessage());

            member.getCallHandler().cancelRequest(
                "Unexpected Exception");
                }
        } catch (NoSuchElementException e) {
            synchronized (this) {
                doneSignal.countDown();

                if (done) {
                break;  // done
                }

            try {
                    wait();
            } catch (InterruptedException ie) {
                    break;
                }
                }
        }
        }
    }
    }

    public void end() {
    done = true;

        printStatistics();
    this.interrupt();

    synchronized (workerThreads) {
        workToDo.clear();
        workerThreads.notifyAll();
    }

        for (int i = 0; i < workerThreads.size(); i++) {
            ((WorkerThread) workerThreads.get(i)).done();
        }
    }

    public void printStatistics() {
    Logger.println(getName() + " " + packetsSent + " packets sent");

    if (packetsSent > 0) {
        Logger.println(getName()
        + " average time to send a packet to every member "
        + (totalSendTime / 1000000000. / packetsSent) + " seconds ");
    }

    ticker.printStatistics();
    }

    private void resetStatistics() {
    packetsSent = 0;
    totalSendTime = 0;
    }

    /*
     * Tuneable parameters
     */
    public static void setSenderThreads(int senderThreads) {
    if (senderThreads < 1) {
        senderThreads = 1;
    } else if (senderThreads > Runtime.getRuntime().availableProcessors()) {
        senderThreads = Runtime.getRuntime().availableProcessors();
    }

    ConferenceSender.senderThreads = senderThreads;
    }

    public static int getSenderThreads() {
    return senderThreads;
    }

    public String toString() {
    return getName();
    }

}

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

import java.util.LinkedList;
import java.util.NoSuchElementException;

public class JitterManager {
    public static int DEFAULT_MIN_JITTER_BUFFER_SIZE = 3; // packets
    public static int DEFAULT_MAX_JITTER_BUFFER_SIZE = 9;

    private int minJitterBufferSize = DEFAULT_MIN_JITTER_BUFFER_SIZE;
    private int maxJitterBufferSize = DEFAULT_MAX_JITTER_BUFFER_SIZE;

    private int jitter;
    private int maxJitter;

    private int elapsed;

    private String id;

    private Plc plc;
    private PlcFactory plcFactory;
    private String plcClassName = "com.sun.voip.PlcCompress";

    /*
     * Manage jitter and lost or out of order packets.
     */ 
    public JitterManager(String id) {
    this.id = id;

    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println(id + ":  jitterManager "
        + " min size " + minJitterBufferSize 
        + " max size " + maxJitterBufferSize);
    }

    plcFactory = PlcFactory.getInstance();

    plc = plcFactory.createPlc(plcClassName);
    plc.setId(id);
    }

    /*
     * Keep track of max jitter
     * When there's no jitter, elapsed is RtpPacket.PACKET_PERIOD
     */
    private void updateJitter(int elapsed) {
    if (elapsed < 0) {
        if (Logger.logLevel >= Logger.LOG_DETAILINFO) {
            Logger.println(id + ":  bad elapsed! " + elapsed);
        }
        elapsed = 0;
        return;
    }

        if (Logger.logLevel == -19) {
        if (elapsed > this.elapsed + (2 * RtpPacket.PACKET_PERIOD)) {
        Logger.println(id + ":  long elapsed " + elapsed);
        }
    }

    this.elapsed = elapsed;

    if (elapsed >= packetArrivalDistribution.length) {
        elapsed = packetArrivalDistribution.length - 1;
    }

    packetArrivalDistribution[elapsed]++;
    
    int jitter = elapsed - RtpPacket.PACKET_PERIOD;

    if (jitter > maxJitter) {
        maxJitter = jitter;
    }

    if (Logger.logLevel == -20) {
        Logger.println(id + ":  " + "old jitter "
        + this.jitter + " new jitter " + jitter);
    }

    if (jitter > this.jitter) {
        this.jitter = jitter;
    } else {
        /*
         * Reduce jitter value slowly
         */
        int change = this.jitter - jitter;
        this.jitter = this.jitter - (change / 4);

        if (this.jitter < 0) {
        this.jitter = 0;
        }
    }
    }

    /*
     * Determine where to place the next packet based on the
     * amount of jitter.  The packet will only be placed at this index
     * if the jitter buffer is empty.  Otherwise the packet is inserted
     * at its correct place in the buffer based on its sequence number.
     */
    private int getJitterIndex() {
    if (maxJitterBufferSize == 0) {
        return 0;
    }

    int jitter = this.jitter;

    jitter = (jitter + RtpPacket.PACKET_PERIOD - 1) / 
        RtpPacket.PACKET_PERIOD * RtpPacket.PACKET_PERIOD;

    int packetListIndex = jitter / RtpPacket.PACKET_PERIOD;

    if (maxJitterBufferSize > 0 && Logger.logLevel >= Logger.LOG_MOREDETAIL) {
        Logger.println(id + ":  jitter " + jitter + " index " + packetListIndex);
    }

    if (packetListIndex < minJitterBufferSize) {
        return minJitterBufferSize;
    } 

    /*
     * Don't fill the jitter buffer with silence packets.
     */
    if (packetListIndex > maxJitterBufferSize / 2) {
        packetListIndex = maxJitterBufferSize / 2;
    }

    return packetListIndex;
    }

    public void setMinJitterBufferSize(int minJitterBufferSize) {
    if (minJitterBufferSize <= 0) {
        this.minJitterBufferSize = 0;
        return;
    }

    if (minJitterBufferSize > maxJitterBufferSize) {
        this.minJitterBufferSize = maxJitterBufferSize;
        return;
    }

    this.minJitterBufferSize = minJitterBufferSize;
    }

    public int getMinJitterBufferSize() {
    return minJitterBufferSize;
    }

    public void setMaxJitterBufferSize(int maxJitterBufferSize) {
    if (maxJitterBufferSize <= 0) {
        Logger.println(id + " invalid maxJitterBufferSize "
        + maxJitterBufferSize + ".  Using default value "
        + " of " + DEFAULT_MAX_JITTER_BUFFER_SIZE);

        maxJitterBufferSize = DEFAULT_MAX_JITTER_BUFFER_SIZE;
    }

    if (maxJitterBufferSize < minJitterBufferSize) {
        this.maxJitterBufferSize = minJitterBufferSize;
        return;
    }

    this.maxJitterBufferSize = maxJitterBufferSize;
    }

    public int getMaxJitterBufferSize() {
    return maxJitterBufferSize;
    }

    public void setPlcClassName(String plcClassName) {
    if (this.plcClassName.equals(plcClassName)) {
        return;
    }

    this.plcClassName = plcClassName;

    synchronized (this) {
        plc = plcFactory.createPlc(plcClassName);
        plc.setId(id);
    }
    }

    public String getPlcClassName() {
    return plc.getClass().getCanonicalName();
    }

    public void printStatistics() {
    Logger.writeFile(id + ":  " + maxJitter + " maxJitter milliseconds");
    Logger.writeFile(id + ":  " + insertedSilence 
        + " times jitter manager inserted silence");
    Logger.writeFile(id + ":  " + outOfOrderPackets + " missing packets");
    Logger.writeFile(id + ":  " + (outOfOrderPackets - failedToRecover)
        + " recovered missing packets");
    Logger.writeFile(id + ":  " + oldTossed + " old packets tossed");
    Logger.writeFile(id + ":  " + packetList.size() 
        + " packets in jitter buffer");

    Logger.writeFile(id + "");

    Logger.writeFile(id + ":  " + "Packet receive distribution");
    
    Logger.writeFile(id + ":  " + "ms\tPackets");
        
    for (int i = 0; i < packetArrivalDistribution.length; i++) {
        if (packetArrivalDistribution[i] != 0) {
            Logger.writeFile(id + ":  " + i + "\t" 
            + packetArrivalDistribution[i]);
        }
    }
    }

    public int getNumberMissingPackets() {
    return failedToRecover;
    }

    public int getPacketListSize() {
    return packetList.size();
    }

    private LinkedList packetList = new LinkedList();  // list of 20ms rcv bufs

    private short firstSequence;

    private int insertedSilence;
    private int outOfOrderPackets;
    private int failedToRecover;
    private int oldTossed;

    private int[] packetArrivalDistribution = new int[500];

    /*
     * packetList holds JitterObjects
     *
     *   - a JitterObject for a missing packet will have isMissing set
     *
     *   _ a JitterObject for inserted silence will have data set to null
     *
     *   - a JitterObject with data null is a place holder.
     *     Place holders preserve the sequence ordering and are used 
     *     for non-media type packets such as comfort payload and 
     *     telephone events and inserted silence.
     * 
     * insertPacket() inserts packets in the right place in packetList as
     * determined by the sequence number.  If the packetList is empty
     * then the jitter index is used to determine how much latency (silence) 
     * to add.
     *
     * The number of inserted silence packets is returned.
     */
    public int insertPacket(short sequence, int elapsed) {
    updateJitter(elapsed);

    return insertPacket(sequence, (byte[]) null);
    }

    public int insertPacket(short sequence, byte[] data) {
    return insertPacket(sequence, (Object) data);
    }

    public int insertPacket(short sequence, int[] data) {
    return insertPacket(sequence, (Object) data);
    }

    private int insertPacket(short sequence, Object data) {
    JitterObject jitterObject = new JitterObject(
        sequence, false, data);

    /*
     * Shouldn't need to do this.  If elapsed is bigger
     * than the maxJitterBufferSize, then the jitter buffer
     * should be empty.
     * XXX
     */
    if (maxJitterBufferSize > 0 &&
        elapsed > maxJitterBufferSize * RtpPacket.PACKET_PERIOD) {

        if (packetList.size() > 0) {
            if (Logger.logLevel >= Logger.LOG_DETAILINFO || 
            Logger.logLevel == -19) {

            Logger.println(id 
                + ":  clearing jitter buffer, no data in a long time, "
                + "packetList size " + packetList.size());
            }

            packetList.clear();
        }

        plc.reset();
    }

    if (maxJitterBufferSize > 0 && 
        packetList.size() >= maxJitterBufferSize) {

        if (Logger.logLevel >= Logger.LOG_MOREINFO ||
            Logger.logLevel == -19) {

            Logger.println(id + ": JitterBuffer full, clearing "
            + packetList.size() + " packets");
        }

        packetList.clear();
        plc.reset();
    }

    int silenceCount = 0;

    int size = packetList.size();

    if (size == 0) {
        /*
         * Insert JitterObjects for silence.
         * Set firstSequence appropriately.
         */
        silenceCount = insertSilence(jitterObject);
    } else if (size >= minJitterBufferSize) {
        /*
         * If we get a burst of packets, try to remove
         * the silence packets we inserted
         */
        removeSilence();
    }

    /*
     * Get the index in packetList where to place this packet.
         */
    short index = (short) (sequence - firstSequence);

    if (index >= 0) {
        if (index < packetList.size()) {
        handleOldPacket(jitterObject, index);
        } else {
        handleNewPacket(jitterObject, index);
        } 
    } else {
        /*
         * We've already delivered packets after this one so we
         * have no choice but to toss it.
         */
        if (Logger.logLevel >= Logger.LOG_MOREINFO ||
                    Logger.logLevel == -19) {

            Logger.println(id + ":  tossing old packet "
            + (sequence & 0xffff) + " index " 
            + index + " firstSequence " + (firstSequence & 0xffff)
            + " packetList size " + packetList.size() + " elapsed " 
            + elapsed);
        }

        oldTossed++;
    }

    return silenceCount;
    }

    private int insertSilence(JitterObject jo) {
    int jitterIndex = getJitterIndex();

    insertedSilence++;

    firstSequence = (short) (jo.sequence - (short) jitterIndex);

    if (jitterIndex > 0) {
        if (Logger.logLevel >= Logger.LOG_MOREINFO ||
                    Logger.logLevel == -19) {

            Logger.println(id + ":  empty list, inserting " 
            + jitterIndex + " silence packets, sequence "
            + (jo.sequence & 0xffff) + " firstSequence " 
            + (firstSequence & 0xffff) + ", elapsed " + elapsed);
        }

        for (int i = 0; i < jitterIndex; i++) {
            JitterObject silence = new JitterObject(
            firstSequence + i, false, null);

            packetList.add(silence);
        }
    }

    return jitterIndex;
    }

    /*
     * Handle a packet which has already been inserted or fill in a
     * place holder.
     */
    private void handleOldPacket(JitterObject jitterObject, int index) {
    JitterObject jo = (JitterObject) packetList.get(index);

    if (jo.isMissing) {
        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println(id + ":  got missing packet " 
            + (jo.sequence & 0xffff)
            + " firstSequence " + (firstSequence & 0xffff)
            + " index " + index
            + " packetList size " + packetList.size());
        }
        //dumpList();
    }

    if (Logger.logLevel >= Logger.LOG_DETAILINFO) {
        Logger.println(id + ":  inserting " 
        + (jitterObject.sequence & 0xffff)
        + " at " + index + " size " + packetList.size()
        + " jitterObject:  " + jitterObject);
    }

    checkIndex(jitterObject.sequence, index);
    packetList.set(index, jitterObject);
    }

    private void handleNewPacket(JitterObject jitterObject, int index) {
    if (index > packetList.size()) {
        handleOutOfOrderPackets(jitterObject, index);
    }

    if (Logger.logLevel >= Logger.LOG_MOREDETAIL ||
            Logger.logLevel == -20) {

        Logger.println(id + ":  appending " 
        + " " + (jitterObject.sequence & 0xffff)
        + " at " + packetList.size()
        + " jitterObject:  " + jitterObject);
    }

    packetList.add(jitterObject);
    }

    private void handleOutOfOrderPackets(JitterObject jo, int index) {
    /*
     * One or more packets is missing.
     * Insert JitterObjects with data set to null to reserve slots in
     * case the missing packets arrive later.
     */
    short expected = (short) (firstSequence + packetList.size());

    int missingPackets = (int) (jo.sequence - expected);

    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println(id + ":  expected " 
            + (expected & 0xffff) + " got " 
        + (jo.sequence & 0xffff) + ", " 
        + missingPackets + " missing packets"
        + " inserting at index " + index
        + " first sequence " + (firstSequence & 0xffff)
        + " list size " + packetList.size()
        + " elapsed " + elapsed);
    }

    outOfOrderPackets += missingPackets;

    // XXX if there are too many missing packets, reset and start over
    if (missingPackets >= maxJitterBufferSize) {
        if (Logger.logLevel >= Logger.LOG_MOREINFO ||
            Logger.logLevel == -19) {

            Logger.println(id + ":  resetting jitter buffer.  " 
           + " too many missing packets " + missingPackets);
        }
       
        packetList.clear();
        plc.reset();

        insertSilence(jo);
        return;
    }

    Object data = null;

    try {
        JitterObject lastPacket = (JitterObject) packetList.getLast();
        data = lastPacket.data;
    } catch (NoSuchElementException e) {
    }

    for (int i = 0; i < missingPackets; i++) {
        JitterObject missing = new JitterObject(expected + i, true, data);

        packetList.add(missing);
    }
    }

    private void dumpList() {
    for (int i = 0; i < packetList.size(); i++) {
        JitterObject jo = (JitterObject) packetList.get(i);

        Logger.println(id + ":  " + ((firstSequence + i) & 0xffff) 
        + " " + jo);
        }
    }

    private void checkIndex(int sequence, int index) {
    if (index >= packetList.size()) {
        return;
    }

    JitterObject jo = (JitterObject) packetList.get(index);

    if (jo.sequence != sequence) {
        Logger.println(id
        + ":  jitterManager overwriting wrong sequence!  index " + index
        + ", jo.sequence " + jo.sequence + " != " 
        + "sequence " + sequence + ", firstSequence " 
        + firstSequence
        + " jitterObject:  " + jo);
    }

    if (jo.isMissing == false && jo.data != null) {
        Logger.println(id 
        + ":  jitterManager overwriting valid packet!  index " 
        + index + " sequence " + jo.sequence + " firstSequence " 
        + firstSequence + " jitterObject:  " + jo);
    }
    }

    private void removeSilence() {
    if (packetList.size() == 0) {
        return;
    }

    JitterObject jo = (JitterObject) packetList.get(0);

    if (jo.isMissing || jo.data != null) {
        return;
    }

    try {
        getFirstPacket();	// remove silence packet

        if (Logger.logLevel == -19) {
        Logger.println(id + ":  removed silence packet "
            + "size " + packetList.size());
        }
    } catch (NoSuchElementException e) {
    }
    }

    public JitterObject getFirstPacket() throws NoSuchElementException {
    JitterObject jo;

    while (true) {
        jo = (JitterObject) packetList.removeFirst();

        if (Logger.logLevel >= Logger.LOG_DETAILINFO) {
            Logger.println(id + ":  getting " 
            + (firstSequence & 0xffff) + " jitterObject:  " + jo);
            Logger.println("");
        }

        if (jo.sequence != firstSequence) {
            Logger.println(id + ":  jo seq " + jo.sequence + " != first seq " 
            + firstSequence);

            dumpList();
        }

        firstSequence++;

        if (jo.isMissing) {
            /*
             * There are missing packets we didn't get.
             * Try to repair the damage.
             */
            failedToRecover++;

        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println(id + ":  Failed to recover packet "
            + (jo.sequence & 0xffff));
        }

            jo = plc.repair(jo);

        if (jo != null) {
            /*
             * Update data field in missing packets after this one.
             */
            for (int i = 0; i < packetList.size(); i++) {
            JitterObject jitterObject = (JitterObject)
                packetList.get(i);

            if (jitterObject.isMissing == false) {
                break;
            }

            jitterObject.data = jo.data;
            }
            break;
        }
        } else {
            if (jo.data != null) {
                plc.addPacket(jo);
            }
        break;
        }
    }

    return jo;
    }

    public int getJitterBufferSize() {
    synchronized (this) {
        return packetList.size();
    }
    }

    public void flush() {
    try {
        while (getFirstPacket() != null) {
        }
    } catch (NoSuchElementException e) {
    }
    }

}

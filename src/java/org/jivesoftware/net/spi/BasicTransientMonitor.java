/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.net.spi;

import org.jivesoftware.net.Monitor;
import org.jivesoftware.util.CircularLinkedList;

import java.util.Date;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <p>Implements a transient (in-memory) generic monitor.</p>
 *
 * @author Iain Shigeoka
 */
public class BasicTransientMonitor implements Monitor {

    /** <p>Total sample quantity.</p> */
    private long totalSamples;
    /** <p>The total sampling time</p> */
    private long totalTime;
    /** <p>The time of the last sample end time.</p> */
    private long lastSampleTime = -1;
    /** <p>The time of the first sample taken.</p> */
    private long firstSampleTime = -1;
    /** <p>The time the monitor was created.</p> */
    private Date startUpTime;
    /** <p>The size of a sample frame in sample events.</p> */
    private int frameSize;
    /** <p>The default size of a sample frame (20 events). </p> */
    private static final int DEFAULT_FRAME_SIZE = 20;
    /** <p>Frame data stored in a circular list.</p> */
    private CircularLinkedList frameList = new CircularLinkedList();
    /** <p>lock on the frame list</p> */
    private ReadWriteLock frameLock = new ReentrantReadWriteLock();

    public BasicTransientMonitor(){
        startUpTime = new Date();
        frameSize = DEFAULT_FRAME_SIZE;
    }

    public void addSample(long quantity, Date startTime, Date endTime) {
        addSample(quantity,startTime.getTime(),endTime.getTime());
    }

    public void addSample(long quantity) {
        if (lastSampleTime < 0){
            lastSampleTime = startUpTime.getTime();
        }
        addSample(quantity,lastSampleTime,System.currentTimeMillis());
    }

    private void addSample(long quantity, long startTime, long endTime){
        totalSamples += quantity;
        totalTime += endTime - startTime;
        lastSampleTime = endTime;
        if (firstSampleTime == -1){
            firstSampleTime = startTime;
        }
        frameLock.writeLock().lock();
        try {
            Sample sample = new Sample(quantity, endTime - startTime);
            if (frameList.size() < frameSize){
                frameList.add(sample);
            } else {
                // overwrite oldest sample
                // the newest sample is at next() and the oldest at prev()
                // so move the pointer to one back using prev() then set that
                // value to be the youngest sample so the samples are again
                // in their natural order.
                frameList.prev();
                frameList.setNext(sample);
            }
        } finally {
            frameLock.writeLock().unlock();
        }
    }

    public long getTotal() {
        return totalSamples;
    }

    public long getTotalTime() {
        return totalTime;
    }

    public float getRate() {
        return (float)totalSamples / (float)(totalTime * 1000);
    }

    public Date getFirstSampleDate() {
        Date time = startUpTime;
        if (firstSampleTime > 0){
            time = new Date(firstSampleTime);
        }
        return time;
    }

    public Date getLastSampleDate() {
        Date time = null;
        if (lastSampleTime > 0){
            time = new Date(lastSampleTime);
        } else {
            time = startUpTime;
        }
        return time;
    }

    public int getFrameSize() {
        return frameSize;
    }

    public void setFrameSize(int newSize) {
        frameLock.writeLock().lock();
        try {
            while (frameList.size() > newSize){
                frameList.prev();
                frameList.remove();
            }
        } finally {
            frameLock.writeLock().unlock();
        }
        frameSize = newSize;
    }

    public long getFrameTotal() {
        long quantity = 0;
        if (frameList.size() > 0){
            frameLock.writeLock().lock();
            try {
                frameList.mark();
                while (frameList.getPassCount() == 0){
                    Sample sample = (Sample) frameList.next();
                    quantity += sample.getQuantity();
                }
            } finally {
                frameLock.writeLock().lock();
            }
        }
        return quantity;
    }

    public long getFrameTotalTime() {
        long time = 0;
        if (frameList.size() > 0){
            frameLock.writeLock().lock();
            try {
                frameList.mark();
                while (frameList.getPassCount() == 0){
                    Sample sample = (Sample) frameList.next();
                    time += sample.getTime();
                }
            } finally {
                frameLock.writeLock().lock();
            }
        }
        return time;
    }

    public float getFrameRate() {
        float time = 0;
        float quantity = 0;
        if (frameList.size() > 0){
            frameLock.writeLock().lock();
            try {
                frameList.mark();
                while (frameList.getPassCount() == 0){
                    Sample sample = (Sample) frameList.next();
                    time += sample.getTime();
                    quantity += sample.getQuantity();
                }
            } finally {
                frameLock.writeLock().lock();
            }
        }
        return quantity / time * 1000;
    }

    /**
     * <p>Represents a single sample event for use in the frame circular list.</p>
     *
     * @author Iain Shigeoka
     */
    private class Sample{
        private long q;
        private long t;

        /**
         * <p>Create a sample with given quantity and time duration.</p>
         *
         * @param quantity The quantity of the sample
         * @param time The time in milliseconds the sample took
         */
        Sample(long quantity, long time){
            q = quantity;
            t = time;
        }

        /**
         * <p>Returns the quantity of the sample.</p>
         *
         * @return Quantity of the sample
         */
        long getQuantity(){
            return q;
        }

        /**
         * <p>Returns the total time of the sample.</p>
         *
         * @return Time of the sample in milliseconds
         */
        long getTime(){
            return t;
        }
    }
}

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

import java.io.IOException;

public class SpeechDetector {
    private boolean speakingChanged = false;
    private boolean isSpeaking  = false;

    static final int POW_THRESH = 50000; // initial power threshold

    static int cnThresh = 50;  // # of avgs to test speaking (1/10 sec at 8k hz)

    static double powerThresholdLimit = 1.05f;

    static int onThresh    = 1;
    static int offThresh   = 4;

    double powthresh = POW_THRESH;
    int oncount     = 0;
    int offcount    = 0;
    double sum       = 0;
    double cnt       = 0;

    int speechDetectorCalls;
    long speechDetectorTime;

    String id;
    MediaInfo mediaInfo;

    public SpeechDetector(String id, MediaInfo mediaInfo) {
    this.id = id;
        this.mediaInfo = mediaInfo;

    cnThresh = mediaInfo.getSampleRate() / 8000 * 50;

    if (mediaInfo.getChannels() == 2) {
        cnThresh *= 2;
    }
    }

    public static void setCnThresh(int cnThresh) {
    SpeechDetector.cnThresh = cnThresh;

    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("cnThresh set to " + cnThresh);
    }
    }

    public static int getCnThresh() {
    return cnThresh;
    }

    public static void setPowerThresholdLimit(double powerThresholdLimit) {
    SpeechDetector.powerThresholdLimit = powerThresholdLimit;

    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("powerThresholdLimit set to " + powerThresholdLimit);
    }
    }

    public static double getPowerThresholdLimit() {
    return powerThresholdLimit;
    }

    public static void setOnThresh(int onThresh) {
    SpeechDetector.onThresh = onThresh;
    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("onThresh set to " + onThresh);
    }
    }

    public static int getOnThresh() {
    return onThresh;
    }

    public static void setOffThresh(int offThresh) {
        SpeechDetector.offThresh = offThresh;
    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("offThresh set to " + offThresh);
    }
    }   

    public static int getOffThresh() {
        return offThresh;
    }

    public boolean reset() {
    sum = 0;
    cnt = 0;
    powthresh = POW_THRESH;

    boolean oldIsSpeaking = isSpeaking;

    speakingChanged = false;
    isSpeaking = false;

    return oldIsSpeaking;
    }

    /*
     * linearData contains 16-bit linear data in a byte array.
     * Returns true if speaking started or stopped.
     */ 
    public boolean processData(byte[] linearData) {
    speechDetectorCalls++;
        long start = CurrentTime.getTime();

    /*
     * Round down to 16 byte boundary in case length isn't
     * a multiple of 16.
     */
    int length = (linearData.length / 16) * 16;

    long sq = 0;

    for (int i = 0;  i <= length - 16; i += 16) {  
        /* 
         * average next 8 samples (MSB only), square result, 
         * add to running avg
         */
        double avg = (double)
            ((linearData[i + 0]
            + linearData[i + 2]
            + linearData[i + 4]
            + linearData[i + 6]
            + linearData[i + 8]
            + linearData[i + 10]
            + linearData[i + 12]
            + linearData[i + 14]) / 8.);

        /*
         * Divide by the number of channels.  For stereo we're
         * likely to be getting similar sounds in each channel.
         */
        avg /= mediaInfo.getChannels();

        /*
         * By squaring the average, the larger values weigh more 
         * than the smaller ones.
         * Also, squaring makes everything positive
         */ 
        sum += (avg * avg);
        cnt++;
    }

    speechDetectorTime += (CurrentTime.getTime() - start);
    return (speakingChanged());
    }

    /*
     * linearData contains 16-bit linear data in a int array.
     * Returns true if speaking started or stopped.
     */ 
    public boolean processData(int[] linearData) {
    speechDetectorCalls++;
        long start = CurrentTime.getTime();

    /*
     * Round down to 8 sample boundary
     */
    int nSamples = (linearData.length / 8) * 8;

    long sq = 0;

    for (int i = 0;  i <= nSamples - 8; i += 8) {  
        /* 
         * average next 8 samples (MSB only), square result, 
         * add to running avg
         */
        double avg = (double) (
        ((byte)(linearData[i + 0] >> 8) +
             (byte)(linearData[i + 1] >> 8) +
             (byte)(linearData[i + 2] >> 8) +
             (byte)(linearData[i + 3] >> 8) +
             (byte)(linearData[i + 4] >> 8) +
             (byte)(linearData[i + 5] >> 8) +
             (byte)(linearData[i + 6] >> 8) +
             (byte)(linearData[i + 7] >> 8)) / 8.);

        /*
         * Divide by the number of channels.  For stereo we're
         * likely to be getting similar sounds in each channel.
         */
        avg /= mediaInfo.getChannels();

        /*
         * By squaring the average, the larger values weigh more 
         * than the smaller ones.
         * Also, squaring makes everything positive
         */ 
        sum += (avg * avg);
        cnt++;
    }

    speechDetectorTime += (CurrentTime.getTime() - start);
    return (speakingChanged());
    }

    /*
     * Returns true if speaker has started or stopped speaking
     */
    private boolean speakingChanged() {
    boolean speakingChanged = false;

    if (cnt < cnThresh) {
        return false; 	// can't tell for sure yet
    }

    double value = sum / cnt;

        /* value is now the power in this sample set. */
        if (value > powthresh) {
            oncount++;
            offcount = 0;

            if (oncount > onThresh) { 
        if (isSpeaking == false) {
                    isSpeaking = true;
            speakingChanged = true;
        }
            }
            // drag powthresh up
            powthresh = ((powthresh + 2) * 63 + value) / 64;
        } else {
            offcount++;
            oncount= 0;

            if (offcount > offThresh) {
        if (isSpeaking == true) {
                    isSpeaking = false;
            speakingChanged = true;
        }
            } 

            // make sure powthresh is < value * 1.1 + 2
            if (powthresh > value * powerThresholdLimit + 2) {
                powthresh = value * powerThresholdLimit + 2;
        }
        }

    sum = 0;
    cnt = 0;

    return speakingChanged;
    }

    /*
     * return true if we can determine that there is speech
     * in the data.  If there's not enough data or we're
     * sure there's not speech, return true.
     */
    public boolean isSpeaking() {
    return isSpeaking;
    }

    public void printStatistics() {
    String s = "";

    if (id != null) {
        s += "Call " + id + ":  ";
    }

        Logger.writeFile(s + "Speech detector calls:  "
        + speechDetectorCalls);

        if (speechDetectorCalls != 0) {
            Logger.writeFile(s + "SpeechDetector average ms per call:  "
                + ((float)((float)speechDetectorTime / speechDetectorCalls) /
            CurrentTime.getTimeUnitsPerSecond()));
        }
    }

    public static void main(String[] args) {
    if (args.length != 1) {
        Logger.println("Usage:  java SpeechDetector <.au file>");
        System.exit(1);
    }

    TreatmentManager treatmentManager = null;

    try {
        treatmentManager = new TreatmentManager(args[0], 0);
    } catch (IOException e) {
        System.out.println("Can't get treatment " + e.getMessage());
        System.exit(1);
    }

    MediaInfo mediaInfo = new MediaInfo((byte) 0, RtpPacket.PCM_ENCODING,
        treatmentManager.getSampleRate(), treatmentManager.getChannels(),
        false);

    Logger.println("MediaInfo " + mediaInfo);

    SpeechDetector speechDetector = new SpeechDetector("Test", mediaInfo);

    byte[] linearData;

    while ((linearData = treatmentManager.getLinearDataBytes(
        RtpPacket.PACKET_PERIOD)) != null) {

        if (speechDetector.processData(linearData) == true) {
        if (speechDetector.isSpeaking()) {
            Logger.println("Started speaking...");
        } else {
            Logger.println("Stopped speaking...");
        }
        }
    }
    }

}

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
import com.sun.voip.Util;
import com.sun.voip.SpatialAudio;

public class SunSpatialAudio implements SpatialAudio {

    private static final double MAX_DELAY = .63;

    private static double falloff = .94;
    private static double minVolume = .7;
    private static double echoDelay = 0;   // .5 seems to be a reasonable value;
    private static double echoVolume = .35;
    private static double behindVolume = .9;
    private static int maxExp;

    private double msPerSample;

    private static double newEchoDelay = echoDelay;

    private String conferenceId;
    private String callId;
    private int sampleRate;
    private int channels;
    private int samplesPerPacket;

    private int packetLength;

    public SunSpatialAudio() {
    }

    public void initialize(String conferenceId, String callId, int sampleRate, int channels, int samplesPerPacket) {

    this.conferenceId = conferenceId;
    this.callId = callId;
    this.sampleRate = sampleRate;
    this.channels = channels;
    this.samplesPerPacket = samplesPerPacket;

    packetLength = samplesPerPacket * channels;

    msPerSample = 1000. / sampleRate;

    if (Logger.logLevel >= Logger.LOG_INFO) {
        Logger.println(toString() + ":" + conferenceId
            + " " + callId + "::"
            + " sample rate " + sampleRate + ", channels " + channels
            + ", milliseconds per sample " + msPerSample);
    }

    setMaxExp();
    }

    public static void setSpatialBehindVolume(double behindVolume) {
    Logger.println("Spatial behind volume set to "
        + behindVolume);

        SunSpatialAudio.behindVolume = behindVolume;
    }

    public static double getSpatialBehindVolume() {
    return behindVolume;
    }

    public static void setSpatialEchoDelay(double echoDelay) {
    Logger.println("Echo delay set to " + echoDelay);
        SunSpatialAudio.newEchoDelay = echoDelay;
    }

    public static double getSpatialEchoDelay() {
        return newEchoDelay;
    }

    public static void setSpatialEchoVolume(double echoVolume) {
    Logger.println("Echo volume set to " + echoVolume);
        SunSpatialAudio.echoVolume = echoVolume;
    }

    public static double getSpatialEchoVolume() {
    return echoVolume;
    }

    public static void setSpatialFalloff(double falloff) {
    SunSpatialAudio.falloff = falloff;

    setMaxExp();
    }

    public static double getSpatialFalloff() {
    return falloff;
    }

    public static void setSpatialMinVolume(double minVolume) {
    SunSpatialAudio.minVolume = minVolume;

    if (minVolume < 0) {
        SunSpatialAudio.minVolume = 0;
    } else if (minVolume > 1) {
        SunSpatialAudio.minVolume = 1;
    }

    setMaxExp();
    }

    public static double getSpatialMinVolume() {
    return minVolume;
    }

    private static void setMaxExp() {
    /*
     * Calculate the exponent by which falloff must be raised to
     * in order to get a value near minVolume.
     *
     * falloff**maxExp = minVolume which is equivalent to
     *
     * maxExp * log(falloff) = log(minVolume) which means
     *
     * maxExp = log(minVolume) / log(falloff);
     */
    if (minVolume >= falloff) {
        maxExp = 0;
    } else {
        maxExp = (int) ((Math.log(minVolume) / Math.log(falloff)));
    }

    if (Logger.logLevel >= Logger.LOG_INFO) {
        Logger.println("minVolume " + minVolume + " falloff " + falloff
            + " maxExp set to " + maxExp);
    }
    }

    int count = 0;

    /*
     * Generate spatial audio.
     */
    public int[] generateSpatialAudio(String sourceId,
        int[] previousContribution, int[] currentContribution,
        double[] spatialValues) {

    if (channels == 1) {
        return currentContribution;  // no support for 1 channel conference
    }

    if (previousContribution == null && currentContribution == null) {
        return null;	// nothing here to do
    }

    echoDelay = newEchoDelay;	// set echo delay in case it changed

    double frontBack = spatialValues[0];

    if (echoVolume == 0 || echoDelay == 0) {
        frontBack = 0;
    }

    double leftRight = spatialValues[1];
    double volume = spatialValues[3];

    double delay = MAX_DELAY * leftRight;

    /*
     * Calculate the number of ints for the delay.
     * There are always 2 channels so we have to multiply by 2.
     */
    int delayLength = (int) Math.round(delay / msPerSample) * 2;

    if (Logger.logLevel >= Logger.LOG_INFO) {
        if ((count++ % 200) == 0) {
            Logger.println("Delay "
            + (Math.round(delay * 1000) / 1000.)
            + " delay length " + delayLength);
        }
    }

    int[] newContribution;

    double nonDominantChannelVolume =
        getAttenuatedVolume(leftRight, volume);

    if (nonDominantChannelVolume < minVolume) {
        nonDominantChannelVolume = minVolume;
    }

    if (delayLength == 0) {
        /*
         * Sound is in the center.
         */
        if (volume == 1 && frontBack >= 0) {
        /*
         * There are no adjustments to be made.  Just return
         * the current contribution.
         */
        if (Logger.logLevel == -88) {
            Util.dump("Current contribution", currentContribution, 0,
            16);
        }
        return currentContribution;
        }

        newContribution = new int[packetLength];

        if (Logger.logLevel == -88) {
        Logger.println("need to make new contribution");
        }

        if (currentContribution != null) {
                int copyLength = Math.min(packetLength,
                                          currentContribution.length);
                System.arraycopy(currentContribution, 0, newContribution, 0,
            copyLength);
        }
    } else {
        if (Logger.logLevel == -88) {
        Logger.println("do leftRight " + delayLength);
        }

        newContribution = doLeftRight(previousContribution,
        currentContribution, delayLength, nonDominantChannelVolume);
    }

        if (frontBack < 0) {
            if (Logger.logLevel == -88) {
        Logger.println("do frontBack " + echoDelay);
        }

            doFrontBack(previousContribution, newContribution, frontBack,
            delayLength, nonDominantChannelVolume);

        volume *= getAttenuatedVolume(frontBack, behindVolume);
    }

    if (volume == 1) {
        return newContribution;
    }

        if (Logger.logLevel == -78) {
        Logger.println("Adjust volumes to "
        + (Math.round(volume * 1000) / 1000.));
    }

    return adjustVolumes(newContribution, volume);
    }

    private int[] doLeftRight(int[] previousContribution,
        int[] currentContribution, int delayLength,
        double nonDominantChannelVolume) {

    int channelOffset;

    if (delayLength < 0) {
        channelOffset = 1;   // delay right channel
        delayLength = -delayLength;
    } else {
        channelOffset = 0;   // delay left channel
    }

        int[] newContribution = new int[packetLength];

    if (currentContribution == null) {
        /*
         * There is no current contribution but there is a
         * previous contribution.  Just copy the previous
             * contribution to our zero filled new contribution.
         */
            int inIx = previousContribution.length - delayLength
            + channelOffset;

        int outIx = channelOffset;

        for (int i = 0; i < delayLength; i += 2) {
            newContribution[outIx] = previousContribution[inIx];
            inIx += 2;
            outIx += 2;
        }

        return newContribution;
        }

    /*
     * There is a current contribution.  There may or may not
     * be a previous contribution.  We must not modify the
     * current contribution so we make a copy.
     */
    System.arraycopy(currentContribution, 0, newContribution, 0,
        packetLength);

    //Util.dump("new contrib", newContribution, 0, newContribution.length);

    /*
     * Now shift the newContribution up by delayLength, then
     * copy the previousContribution samples to the beginning
     * of our newContribution.
     *
     * First copy to an intermediate buffer so we don't overwrite
     * good data.  Otherwise, we'd have to start the copy at the
     * end of the buffer and move downward.
     */
    int[] c = new int[newContribution.length - delayLength];

    for (int i = channelOffset; i < c.length; i += 2) {
        c[i] = newContribution[i];
    }

    for (int i = channelOffset; i < c.length; i += 2) {
        newContribution[i + delayLength] = (int) (c[i] * nonDominantChannelVolume);
    }

    if (previousContribution != null) {
            int inIx = packetLength - delayLength + channelOffset;

        int outIx = channelOffset;

        for (int i = 0; i < delayLength; i += 2) {
            newContribution[outIx] = (int)
            (previousContribution[inIx] * nonDominantChannelVolume);

        inIx += 2;
        outIx += 2;
        }
    } else {
        //Logger.println("current but no prev");

        for (int i = channelOffset; i < delayLength; i += 2) {
            newContribution[i] = 0;
        }
    }

    return newContribution;
    }

    private int count1 = 0;

    private void doFrontBack(int[] previousContribution, int[] newContribution,
        double frontBack, int delayLength, double nonDominantChannelVolume) {

        //Util.dump("result before, p 1, c 3", newContribution, 0,
    //    newContribution.length);

        //dump(newContribution);

    int echoDelayLength = (int) Math.round(echoDelay / msPerSample);

    echoDelayLength *= Math.abs(frontBack);

    echoDelayLength *= 2;   // 2 samples for stereo

    if (echoDelayLength <= 0) {
        return;
    }

    int channelOffset;

    if (delayLength < 0) {
        channelOffset = 1;   // delay right channel
        delayLength = -delayLength;
    } else {
        channelOffset = 0;   // delay left channel
    }

        if (Logger.logLevel >= Logger.LOG_INFO) {
        if ((count1 % 200) == 0) {
            Logger.println("adding echo"
            + " delayLength " + delayLength + " c off " + channelOffset
                + " edl " + echoDelayLength + " echoDelay " + echoDelay
            + " msps " + (Math.round(msPerSample * 1000) / 1000.));
       }
    }

    /*
     * Copy newContribution
     */
    int[] c = new int[packetLength];

    System.arraycopy(newContribution, 0, c, 0, c.length);

    int inIx = 0;
    int outIx = echoDelayLength;
    int length = c.length - echoDelayLength;

        if (Logger.logLevel >= Logger.LOG_INFO) {
            if ((count1 % 200) == 0) {
            Logger.println("inIx " + inIx + " outIx " + outIx + " length "
                + length);
        }
    }

    for (int i = 0; i < length; i++) {
        newContribution[outIx] = clip((int)
        (c[outIx] + (c[inIx] * echoVolume)));

        inIx++;
        outIx++;
    }

    if (previousContribution == null) {
        count1++;
        return;
    }

    /*
     * Add echo from previousContribution
     */
    if (delayLength == 0) {
        inIx = packetLength - echoDelayLength;
        outIx = 0;
        length = echoDelayLength;

        for (int i = 0; i < length; i++) {
            newContribution[outIx] = clip((int)
            (c[outIx] + (previousContribution[inIx] * echoVolume)));

            inIx++;
                outIx++;
        }
        count1++;
        return;
    }

    inIx = packetLength - delayLength - echoDelayLength
        + channelOffset;

    outIx = channelOffset;
    length = echoDelayLength;

        if ((count1 % 200) == 0) {
        Logger.println("inIx " + inIx + " outIx " + outIx + " length "
            + length);
    }

    for (int i = 0; i < length; i += 2) {
        newContribution[outIx] = clip((int)
        (c[outIx] +
        (previousContribution[inIx] * echoVolume * nonDominantChannelVolume)));

        inIx += 2;
        outIx += 2;
    }

    /*
     * Add echo from the other channel
     */
    if (channelOffset == 0) {
        outIx = 1;
    } else {
        outIx = 0;
    }

    inIx = outIx + packetLength - echoDelayLength;
        length = echoDelayLength - outIx;

        if ((count1 % 200) == 0) {
        Logger.println("inIx " + inIx + " outIx " + outIx + " length "
            + length);
    }

    int i = 0;

    for (i = 0; i < length; i += 2) {
        newContribution[outIx] = clip((int)
        (c[outIx] + (previousContribution[inIx] * echoVolume)));

        inIx += 2;
        outIx += 2;
    }

    count1++;
    }

    private int[] adjustVolumes(int[] contribution, double volume) {
    /*
     * Adjust the volume
     */
    int[] c = new int[contribution.length];

    for (int i = 0; i < contribution.length; i++) {
        c[i] = clip((int) (contribution[i] * volume));
    }

    return c;
    }

    private int clip(int sample) {
        if (sample > 32767) {
        if (Logger.logLevel == -79) {
            Logger.println("clipping " + sample + " to 32767");
        }
            return 32767;
        }

        if (sample < -32768) {
        if (Logger.logLevel == -79) {
            Logger.println("clipping " + sample + " to -32768");
        }
            return -32768;
        }

        return sample;
    }

    private double getAttenuatedVolume(double offset, double volume) {

    if (offset == 0) {
        return 1;
    }

    int exp = (int) (Math.abs(offset) * maxExp);

    return volume * Math.pow(falloff, exp);
    }

    public String toString() {
    return "SunSpatialAudio";
    }

    public static void main(String[] args) {
    new SunSpatialAudio().test();
    }

    private void test() {
    initialize("Test", "Test", 44100, 2, 44100 / 50);

    echoDelay = .1;

        double[] spatialValues = new double[4];

        spatialValues[0] = -1;
        spatialValues[1] = .5;
        spatialValues[2] = 0;
        spatialValues[3] = 1;

    int[] p = new int[64];
        int[] c = new int[64];
        int[] result;

    fill(p, 1);
        fill(c, 3);

        //Util.dump("c before", c, 0, c.length);

        result = generateSpatialAudio("Test", p, c, spatialValues);

        Util.dump("result, p 1, c 3", result, 0, result.length);

        dump(result);

if (false) {

    p = c;
        c = new int[64];

        fill(c);

        Util.dump("c before 2", c, 0, c.length);

        result = generateSpatialAudio("Test", p, c, spatialValues);

        dump(result);

        Util.dump("p set, c set", result, 0, result.length);

        c = new int[64];

        fill(c);

        Util.dump("c before 3", c, 0, c.length);

        result = generateSpatialAudio("Test", p, c, spatialValues);

        Util.dump("c null", result, 0, result.length);

        dump(result);

        c = new int[64];

        fill(c, 4);

        Util.dump("c echo", c, 0, c.length);

        spatialValues[0] = -1;

        spatialValues[1] = 1;
        spatialValues[2] = 0;
        spatialValues[3] = 1;

        result = generateSpatialAudio("Test", p, c, spatialValues);

        Util.dump("after adding echo", result, 0, result.length);

        dump(result);

        System.out.println("====================");

        Util.dump("c echo", c, 0, c.length);

        result = generateSpatialAudio("Test", p, c, spatialValues);

        Util.dump("after adding echo", result, 0, result.length);

        dump(result);
}
    }

    private void fill(int[] buf, int v) {
        for (int i = 0; i < buf.length; i += 2) {
            buf[i] = v;
            buf[i + 1] = v;
        }
    }

    private int v = 0;

    private void fill(int[] buf) {
        for (int i = 0; i < buf.length; i += 2) {
            buf[i] = v++;
            buf[i + 1] = v++;
        }
    }

    private void dump(int[] c) {
        System.out.println("\nleft " + c.length);

        for (int i = 0; i < c.length; i += 2) {
            System.out.print(Integer.toHexString(c[i] & 0xff) + " ");
        }

        System.out.println("\n\nright " + c.length);

        for (int i = 1; i < c.length; i += 2) {
            System.out.print(Integer.toHexString(c[i] & 0xff) + " ");
        }

        System.out.println("\n");
    }

}

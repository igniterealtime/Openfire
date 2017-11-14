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

public class LowPassFilter {
    private String id;
    private int channels;

    private static int nAvg = 3;

    private static double lpfVolumeAdjustment = .05D;

    private int lpfCount;
    private long totalLpfTime;

    private int[] previousSamples;	

    public LowPassFilter(String id, int sampleRate, int channels) {
    this.id = id;
    this.channels = channels;
    }

    public void reset() {
        previousSamples = null;
    }

    public void printStatistics() {
    if (lpfCount == 0) {
        return;
    }

    String s = "";

    if (id != null) {
            s += id + ":  ";
    }

    double avg = (double) totalLpfTime / lpfCount;

    avg = (avg / CurrentTime.getTimeUnitsPerSecond()) * 1000;

    Logger.writeFile(s + avg + "ms avg low pass filter time");
    }

    private static final int MAX_NAVG = 50;

    public static void setNAvg(int nAvg) {
    if (nAvg > MAX_NAVG) {
        nAvg = MAX_NAVG;
    }

    LowPassFilter.nAvg = nAvg;

    Logger.println("nAvg set to " + nAvg);

    double x = Math.exp(-2 * Math.PI * (nAvg / 500D));
    a0 = 1 - x;
    b1 = x;

    Logger.println("SP:  cutoff = " + (nAvg / 500D) + " a0 " + a0
        + " b1 " + b1);
    }

    public static int getNAvg() {
    return nAvg;
    }

    public static void setLpfVolumeAdjustment(double lpfVolumeAdjustment) {
    LowPassFilter.lpfVolumeAdjustment = lpfVolumeAdjustment;
    }

    public static double getLpfVolumeAdjustment() {
    return lpfVolumeAdjustment;
    }

    /*
     * Low pass filter using moving average
     */
    public byte[] lpf(byte[] inSamples) {
    int length = inSamples.length & ~1;	// round down

    int[] ints = new int[length / 2];

    AudioConversion.bytesToInts(inSamples, 0, length, ints);

    ints = lpf(ints);

    byte[] bytes = new byte[ints.length * 2];

    AudioConversion.intsToBytes(ints, bytes, 0);

    return bytes;
    }

    public int[] lpf(int[] inSamples) {
    long start = CurrentTime.getTime();
    
    if (nAvg > inSamples.length / channels) {
        nAvg = inSamples.length / channels;
    }

    if (nAvg < 2) {
        return inSamples;
    }

    if (previousSamples == null || 
        previousSamples.length != (nAvg -1) * channels) {

        previousSamples = new int[(nAvg - 1) * channels];
    }

    int[] outSamples = new int[inSamples.length];

    /*
     * Save next set of previous samples
     */
    int[] p = new int[previousSamples.length];

    System.arraycopy(inSamples, inSamples.length - p.length,
        p, 0, p.length);

    if (debug) {
        Util.dump("previous", previousSamples, 0, previousSamples.length);
        Util.dump("inSamples", inSamples, 0, inSamples.length);
    }

    /*
     * Calculate volume level adjustment.  Averaging reduces the
     * volume level and we try to compensate for that.
     */
    double volumeAdjustment = 1.0D + (lpfVolumeAdjustment * nAvg);

    /*
     * Calculate sum of nAvg samples using the previous samples
     * plus the first sample of inSamples.
     */
    int sum1 = 0;
    int sum2 = 0;

    int ix = 0;

        for (int i = 0; i < nAvg - 1; i++) {
            sum1 += previousSamples[ix];

        ix++;

            if (channels == 2) {
                sum2 += previousSamples[ix];
        ix++;
            }
        }

    if (debug) {
        Logger.println("First sum " + sum1);
    }

    /*
     * Handle first nAvg - 1 samples special because
     * they involve previous samples.
     * After that, just the inSamples are used.
     */
    ix = 0;

    for (int i = 0; i < nAvg - 1; i++) {
            sum1 += inSamples[ix];

        if (debug) {
        Logger.println("Adding ix " + ix + " " + inSamples[ix]
            + " sum1 " + sum1);
        }

            /*
             * Set sample to be the average of the last nAvg samples.
             */
            outSamples[ix] = AudioConversion.clip(
                (int) (volumeAdjustment * sum1 / nAvg));

            sum1 -= previousSamples[ix];

        if (debug) {
        Logger.println("subtracting previous ix " + ix + " " 
            + previousSamples[ix] + " sum1 " + sum1
            + " avg " + (sum1 / nAvg));
        }

        ix++;

            if (channels == 2) {
                sum2 += inSamples[ix];

                outSamples[ix] = AudioConversion.clip(
                    (int) (volumeAdjustment * sum2 / nAvg));

                sum2 -= previousSamples[ix];
                ix++;
            }
    }

    int indexToSubtract = 0;

    while (ix < inSamples.length) {
        sum1 += inSamples[ix];

        if (debug) {
        Logger.println("Adding ix " + ix + " " + inSamples[ix]
            + " sum1 " + sum1);
        }
    
            /*
             * Set sample to be the average of the last nAvg samples.
             */
            outSamples[ix] = AudioConversion.clip(
        (int) (volumeAdjustment * sum1 / nAvg));

        sum1 -= inSamples[indexToSubtract];

        if (debug) {
        Logger.println("subtracting ix " + ix + " " 
            + inSamples[ix] + " sum1 " + sum1
            + " avg " + (sum1 / nAvg));
        }

        ix++;
        indexToSubtract++;

            if (channels == 2) {
        sum2 += inSamples[ix];

                outSamples[ix] = AudioConversion.clip(
            (int) (volumeAdjustment * sum2 / nAvg));

        sum2 -= inSamples[indexToSubtract];

        ix++;
            indexToSubtract++;
        }
        }

    //verify(inSamples, outSamples);

    previousSamples = p;

    totalLpfTime += (CurrentTime.getTime() - start);
    lpfCount++;

    if (debug) {
        Util.dump("outSamples", outSamples, 0, outSamples.length);
    }

    return outSamples;
    }

    /*
     * Low pass filter obtained from Ole Lond, lond@GET2NET.DK in
     * a public email sent to JAVASOUND-INTEREST on 5/16/2006.
     * An implementation of http://www.musicdsp.org/archive.php?classid=3#185
     */
    
    private static double c;

    private static double r; 

    private static double x;

    public static void setX(double x) {
    setParams(x, y);
    }
 
    private static double y;

    public static void setY(double y) {
    setParams(x, y);
    }
 
    public static void setParams(double x, double y) {
    LowPassFilter.x = x;
    LowPassFilter.y = y;

    if (x == 0 && y == 0) {
        Logger.println("Disabling Low Pass RC filter");
        return;
    }

    double xx = Math.exp(-2 * Math.PI * (x / 2));
    a0 = 1 - xx;
    b1 = xx;

    Logger.println("SP:  cutoff = " + (x / 2) + " a0 " + a0
        + " b1 " + b1);

        int cutoff = (int) (x * 127);
        int resonance = (int) (y * 127);

        c = Math.pow(0.5, (128 - cutoff) / 16.0);
        r = Math.pow(0.5, (resonance + 24) / 16.0);

    Logger.println("setParams:  x = " + x + " y = " + y
        + " cutoff = " + cutoff + " resonance = " + resonance
        + " c = " + c + " r = " + r);
    } 

    public byte[] lpfRC(byte[] inSamples) {
    if (x == 0 && y == 0) {
        return inSamples;
    }

        int length = inSamples.length & ~1;     // round down

        int[] ints = new int[length / 2];

        AudioConversion.bytesToInts(inSamples, 0, length, ints);

        ints = lpfRC(ints);

        byte[] bytes = new byte[ints.length * 2];

        AudioConversion.intsToBytes(ints, bytes, 0);

        return bytes;
    }

    public int[] lpfRC(int[] inSamples) {
    if (x == 0 && y == 0) {
        return inSamples;
    }

    int[] outSamples = new int[inSamples.length];

        double v0 = 0;

        double v1 = 0;

    double v2 = 0;

    double v3 = 0;

    int i = 0;

    while (i < inSamples.length) {
            v0 = (double) 
        ((1 - r * c) * v0 - c * v1 + c * ((double)inSamples[i]));

        v1 = (double) ((1 - r * c) * v1 + c * v0);

        outSamples[i] = (int) (v1);

        i++;

        if (channels == 2) {
                v2 = (double) 
            ((1 - r * c) * v2 - c * v3 + c * ((double)inSamples[i]));

            v3 = (double) ((1 - r * c) * v3 + c * v2);

            outSamples[i] = (int) (v3);
            i++;
        }
        }

    return outSamples;
    }

    private static double a0;
    private static double b1;

    public byte[] lpfSP(byte[] inSamples) {
    if (a0 == 0) {
        return inSamples;
    }

        int length = inSamples.length & ~1;     // round down

        int[] ints = new int[length / 2];

        AudioConversion.bytesToInts(inSamples, 0, length, ints);

        ints = lpfSP(ints);

        byte[] bytes = new byte[ints.length * 2];

        AudioConversion.intsToBytes(ints, bytes, 0);

        return bytes;
    }

    int[] lastOutSamples;

    public int[] lpfSP(int[] inSamples) {
    if (a0 == 0) {
        return inSamples;
    }

    if (Logger.logLevel == -123) {
        Logger.println("a0 " + a0 + " b1 " + b1);
        Logger.logLevel = 3;
    }

        int[] outSamples = new int[inSamples.length];

    if (lastOutSamples != null) {
            outSamples[0] = (int) (
                (a0 * inSamples[0]) + (b1 * lastOutSamples[0]));

            if (channels == 2) {
                outSamples[1] = (int) (
                    (a0 * inSamples[1]) + (b1 * lastOutSamples[1]));
            }
    } else {
        lastOutSamples = new int[channels];
        outSamples[0] = inSamples[0];

        if (channels == 2) {
            outSamples[1] = inSamples[1];
        }
    }

    int i = channels;

    while (i < inSamples.length) {
        outSamples[i] = (int) (
        (a0 * inSamples[i]) + (b1 * outSamples[i - channels]));

        i++;

        if (channels == 2) {
            outSamples[i] = (int) 
            ((a0 * inSamples[i]) 
            + (b1 * outSamples[i - channels]));

        i++;
        }
        }

    lastOutSamples[0] = outSamples[inSamples.length - channels];

    if (channels == 2) {
        lastOutSamples[1] = outSamples[inSamples.length - channels + 1];
    }

    return outSamples;
    }

    private void verify(int[] inSamples, int[] outSamples) {
    for (int i = nAvg; i < inSamples.length - nAvg; i++) {
        int sum = 0;

        for (int j = 0; j < nAvg; j++) {
            sum += inSamples[i + j];
        }

        if ((sum / nAvg) != outSamples[i + nAvg - 1]) {
            Util.dump("bad avg at " + i, inSamples, 0, 8);
        Util.dump("out ", outSamples, 0, 8);
        break;
        }
    }
    }

    /*
     * sin(x) / x
     */
    private static final double[] filterKernel = {
    0D,
    0.10929240478705181D,
    0.23387232094715982D,
    0.3678830105717742D,
    0.5045511524271046D,
    0.6366197723675814D,
    0.756826728640657D,
    0.8583936913341398D,
    0.935489283788639D,
    0.983631643083466D,
    1D,
    0.983631643083466D,
    0.935489283788639D,
    0.8583936913341398D,
    0.756826728640657D,
    0.6366197723675814D,
    0.5045511524271046D,
    0.3678830105717742D,
    0.23387232094715982D,
    0.10929240478705181D,
    0D,
    };

    public int[] lpfxxx(int[] inSamples) {
    long start = CurrentTime.getTime();

    if (previousSamples == null ||
                previousSamples.length != (nAvg -1) * channels) {

            previousSamples = new int[(nAvg - 1) * channels];
        }

        /*
         * Save next set of previous samples
         */
        int[] p = new int[previousSamples.length];

        System.arraycopy(inSamples, inSamples.length - p.length,
            p, 0, p.length);

    int[] in = new int[previousSamples.length + inSamples.length];

    for (int i = 0; i < previousSamples.length; i++) {
        in[i] = previousSamples[i];
    }

    for (int i = 0; i < inSamples.length; i++) {
        in[previousSamples.length + i] = inSamples[i];
    }

    previousSamples = p;

    int[] out = new int[in.length + filterKernel.length - 1];

    /*
     * Do the convolution.
     */
    for (int i = 0; i < in.length; i++) {
        for (int j = 0; j < filterKernel.length; j++) {
        out[i + j] += (int) (in[i] * filterKernel[j]);
        }
    }

    int[] outSamples = new int[inSamples.length];

    System.arraycopy(out, ((filterKernel.length - 1) / 2), 
        outSamples, 0, inSamples.length);

        totalLpfTime += (CurrentTime.getTime() - start);
        lpfCount++;

    if (debug) {
            Util.dump("outSamples", outSamples, 0, outSamples.length);
        }

    return outSamples;
    }
    
    private static boolean debug = false;

    public static void main(String[] args) {
    LowPassFilter lpf = new LowPassFilter("test", 8000, 1);

    debug = true;

    byte[] buf = new byte[320];

    for (int i = 0; i < buf.length; i += 2) {
        buf[i] = (byte) (((i / 2) >> 8) & 0xff);
        buf[i + 1] = (byte) ((i / 2) & 0xff);
    }

    LowPassFilter.setNAvg(3);
    LowPassFilter.setLpfVolumeAdjustment(0.0);
    lpf.lpf(buf);
    }
    
}

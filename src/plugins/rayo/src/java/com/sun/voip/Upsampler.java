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

/**
 * Upsampler
 */
public class Upsampler extends Resampler {
    private long totalTime;
    private int resampleCount;

    private int[] lastSample;		// for upsampling

    /*
     * XXX We only support big endian 16 bit samples!
     */
    public Upsampler(String id, int inSampleRate, int inChannels,
        int outSampleRate, int outChannels) throws IOException {

    super(id, inSampleRate, inChannels, outSampleRate, outChannels);

    if (inSampleRate > outSampleRate) {
        throw new IOException("Upsampler inSampleRate "
        + inSampleRate + " > outSampleRate " + outSampleRate);
    }

    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("New Upsampler:  from " 
            + inSampleRate + "/" + inChannels + " to " 
            + outSampleRate + "/" + outChannels);
    }

    reset();
    }

    public void reset() {
    lastSample = new int[outChannels];
    }

    public byte[] resample(byte[] inSamples, int offset, int length) 
        throws IOException {

    length = length & ~1;	// round down

    int[] ints = new int[length / 2];

    AudioConversion.bytesToInts(inSamples, offset, length, ints);

    ints = resample(ints);

    byte[] bytes = new byte[ints.length * 2];

    AudioConversion.intsToBytes(ints, bytes, offset);

    return bytes;
    }

    public int[] resample(int[] inSamples) throws IOException {
    if (inSampleRate == outSampleRate && inChannels == outChannels) {
        return inSamples;
    }

    resampleCount++;

    long start = CurrentTime.getTime();

    /*
     * Convert mono to multi-channel or vice versa as needed.
     *
     * Upsampling is done by interpolating between data points
     * and producing the right number of output samples.
     */
    int[] outSamples = reChannel(inSamples);

    if (inSampleRate == outSampleRate) {
        return outSamples;				// no need to resample
    }

    outSamples = upsample(outSamples);

    outSamples = lowPassFilter.lpf(outSamples);

    totalTime += (CurrentTime.getTime() - start);

    return outSamples;
    }

    private int[] upsample(int[] inSamples) {
        /*
         * Calculate the number of inSamples needed to produce an outSample.
         * Round to the nearest integer.
     * XXX The number of input samples must be divide into the
     * input sample rate or else the outLength will be too small!
         */
        int nSamples = inSamples.length / outChannels;

        double sampleTime = (nSamples * 1000.0D) / inSampleRate;

        int outLength = (int)(Math.round(
            (sampleTime * outSampleRate * outChannels / 1000)));

    //Logger.println("outLength " + outLength);

    if ((outLength & 1) != 0) {
        outLength++;
    }

        int[] outSamples = new int[outLength];

    double frameIncr = (double)inSampleRate / (double)(outSampleRate);

    int outIx = 0;

    int[] last = new int[outChannels];

    last[0] = inSamples[inSamples.length - outChannels];

    if (outChannels == 2) {
        last[1] = inSamples[inSamples.length - outChannels + 1];
    }

    int ix = 0;
    double i = 0;

    /*
     * Linear interpolation between each two samples
     */
    while (true) {
        int intI = (int)i;

        ix = intI * outChannels;

        if (ix >= inSamples.length || outIx + outChannels > outLength ) {
        break;
        }

        int s1;

        if (ix == 0) {
        s1 = lastSample[0];
        } else {
            s1 = inSamples[ix - outChannels];
        } 

        int s2 = inSamples[ix];

        int newSample = (int)(s1 + ((s2 - s1) * (i - intI)));

        outSamples[outIx] = (int) newSample;

        outIx++;

        if (outChannels == 2) {
        if (ix == 0) {
            s1 = lastSample[1];
        } else {
                s1 = inSamples[ix - outChannels + 1];
        }
        
            s2 = inSamples[ix + 1];

        newSample = (int)(s1 + ((s2 - s1) * (i - intI)));

            outSamples[outIx] = (int) newSample;

            outIx++;
        }

        if (outIx >= outLength) {
        break;
        }

        i += frameIncr;
    }

    lastSample = last;
    return outSamples;
    }

    public void printStatistics() {
    if (resampleCount == 0) {
        return;
    }

    double avg = (double)totalTime / resampleCount;

    long timeUnitsPerSecond = CurrentTime.getTimeUnitsPerSecond();

    avg = (avg / timeUnitsPerSecond) * 1000;

    String s = "";

    if (id != null) {
            s += "Call " + id + ":  ";
    }

    Logger.writeFile(s
        + avg + "ms avg upsample time from "
        + inSampleRate + "/" + inChannels + " to " + outSampleRate + "/"
        + outChannels);

    lowPassFilter.printStatistics();
    }

}

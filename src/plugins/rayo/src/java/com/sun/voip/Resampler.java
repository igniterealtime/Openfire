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
 * Sample rate converter, convert from one sample rate to another.
 */
public abstract class Resampler {
    protected int inSampleRate;
    protected int inChannels;

    protected int outSampleRate;
    protected int outChannels;

    protected String id;

    protected LowPassFilter lowPassFilter;

    protected Resampler(String id, int inSampleRate, int inChannels,
	    int outSampleRate, int outChannels) throws IOException {

	this.id = id;

	if (inChannels != 1 && inChannels != 2) {
	    Logger.println("invalid in channels " + inChannels);

	    throw new IOException("invalid in channels " + inChannels);
	}

	if (outChannels != 1 && outChannels != 2) {
	    Logger.println("SampleRateConverter:  invalid out channels " 
		+ outChannels);

	    throw new IOException("SampleRateConverter:  invalid in channels " 
		+ inChannels);
	}

	if (inSampleRate <= 0) {
	    Logger.println("SampleRateConverter:  invalid input sample rate " 
		+ inSampleRate);

	    throw new IOException("SampleRateConverter:  "
		+ " invalid input sample rate " + inSampleRate);
	}

	if (outSampleRate <= 0) {
	    Logger.println("SampleRateConverter:  invalid output sample rate " 
		+ outSampleRate);

	    throw new IOException("SampleRateConverter:  "
		+ " invalid output sample rate " + outSampleRate);
	}

	this.inSampleRate = inSampleRate;
	this.inChannels = inChannels;

	this.outSampleRate = outSampleRate;
	this.outChannels = outChannels;

	if (inSampleRate > outSampleRate) {
	    lowPassFilter = new LowPassFilter(id, inSampleRate, inChannels);
	} else {
	    lowPassFilter = new LowPassFilter(id, outSampleRate, outChannels);
	}

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("New Sample Converter:  from " 
	        + inSampleRate + "/" + inChannels + " to " 
	        + outSampleRate + "/" + outChannels);
	}
    }

    protected byte[] resample(byte[] inSamples, int offset, int length)
            throws IOException {

        length = length & ~1;   // round down

        int[] ints = new int[length / 2];

        AudioConversion.bytesToInts(inSamples, offset, length, ints);

        ints = resample(ints);

        byte[] bytes = new byte[ints.length * 2];

        AudioConversion.intsToBytes(ints, bytes, offset);

        return bytes;
    }

    protected abstract int[] resample(int[] inSamples) throws IOException;

    protected int[] reChannel(int[] inSamples) throws IOException {
	if ((inSamples.length % inChannels) != 0) {
            Logger.println("length " + inSamples.length
                + " is not a multiple of the frame size " + inChannels);

            throw new IOException("length " + inSamples.length
                + " is not a multiple of the frame size " + inChannels);
        }

	/*
	 * XXX Possible optimization here.  Always reduce channels before
	 * resampling and wait to increase channels until after resampling.
	 * The assumption is that resampling takes longer than resampling.
	 */
	int[] outSamples;

	if (inChannels > outChannels) {
	    outSamples = reduceChannels(inSamples);
	} else if (inChannels < outChannels) {
	    outSamples = increaseChannels(inSamples);
	} else {
	    outSamples = inSamples;
	}

	return outSamples;
    }

    private int[] reduceChannels(int[] inSamples) {
	/*
	 * inChannels is 2 and outChannels is 1
	 */	
	int outIx = 0;

	int[] outSamples = new int[inSamples.length / 2];

	for (int inIx = 0; inIx < inSamples.length; inIx += 2) {
	    int s1 = inSamples[inIx];
	    int s2 = inSamples[inIx + 1];

	    //outSamples[outIx] = (int) ((s1 + s2) / 2);
	    outSamples[outIx] = (int) s1;
	    outIx ++;
	}
	
	return outSamples;
    }

    private int[] increaseChannels(int[] inSamples) {
	/*
	 * inChannels is 1 and outChannels is 2
	 */	
	int[] outSamples = new int[inSamples.length * 2];

	int outIx = 0;

	for (int inIx = 0; inIx < inSamples.length; inIx++) {
	    outSamples[outIx] = inSamples[inIx];
	    outIx++;
	    outSamples[outIx] = inSamples[inIx];
	    outIx++;
	}
	
	return outSamples;
    }

    public abstract void reset();
    
    public abstract void printStatistics();

}

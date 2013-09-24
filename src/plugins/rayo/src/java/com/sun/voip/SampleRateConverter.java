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
public class SampleRateConverter {
    private Resampler resampler;

    public SampleRateConverter(String id, int inSampleRate, int inChannels,
	    int outSampleRate, int outChannels) throws IOException {

	if (inSampleRate < outSampleRate) {
	    resampler = new Upsampler(id, inSampleRate, inChannels,
		outSampleRate, outChannels);
	} else {
	    resampler = new Downsampler(id, inSampleRate, inChannels,
		outSampleRate, outChannels);
	}

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("New Sample Rate Converter:  from " 
	        + inSampleRate + "/" + inChannels + " to " 
	        + outSampleRate + "/" + outChannels);
	}
    }

    public void reset() {
	resampler.reset();
    }

    public byte[] resample(byte[] inSamples, int offset, int length) 
	    throws IOException {

	return resampler.resample(inSamples, offset, length);
    }

    public int[] resample(int[] inSamples) throws IOException {
	return resampler.resample(inSamples);
    }

    public void printStatistics() {
	resampler.printStatistics();
    }

}

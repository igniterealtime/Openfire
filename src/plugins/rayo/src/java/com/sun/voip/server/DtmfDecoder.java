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

import com.sun.voip.AudioConversion;
import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.RtpPacket;
import com.sun.voip.SampleRateConverter;

import com.sun.medialib.codec.dtmf.Decoder;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;

import java.util.Calendar;

public class DtmfDecoder {
    MemberReceiver memberReceiver = null;

    private final static char[] char_keys = 
	{ '0','1','2','3','4','5','6','7','8', '9','*','#','A','B','C','D' };

    private static final int MAX_KEYS = 1;

    private Decoder decoder;
    private int[] linearData;

    private SampleRateConverter sampleRateConverter;

    private MediaInfo mediaInfo;

    private long totalDecodeTime;
    private int numberOfTimesCalled;

    public DtmfDecoder(MemberReceiver memberReceiver, MediaInfo mediaInfo) {
	this.memberReceiver = memberReceiver;
	this.mediaInfo = mediaInfo;

	decoder = new Decoder();

	linearData = new int[mediaInfo.getSamplesPerPacket()];

	decoder.setRate(mediaInfo.getSampleRate());

	if (mediaInfo.getChannels() != 1) {
	    /*
	     * Must convert multi-channel to 1
	     */
	    try {
	        sampleRateConverter = 
		    new SampleRateConverter("DtmfDecoder",
		    mediaInfo.getSampleRate(),
		    mediaInfo.getChannels(), 
		    mediaInfo.getSampleRate(), 1);
	    } catch (IOException e) {
		Logger.println(
		    "Call " + memberReceiver + " DtmfDecoder:  " + e.getMessage());
	    }
	}
    }

    public boolean dtmfDetected() {
	return decoder.dtmfDetected();
    }

    public String noDataReceived() {
	/*
	 * Create a packet of silence (linear 0)
	 * The dtmf detector needs to know when a dtmf key
	 * is released.  If a phone stops sending immediately
	 * after the dtmf key is released, we need to append
	 * silence so that the dtmf detector will return
	 * the dtmf key.
	 */
        int[] silence = new int[mediaInfo.getSamplesPerPacket()];

	String dtmfKeys = null;

	dtmfKeys = processData(silence);

	if (Logger.logLevel >= Logger.LOG_DETAIL) {
	    Logger.println(
		"no data received, done processing dtmf with silence");

	    if (dtmfKeys != null) {
                Logger.println("silence.  dtmf " + dtmfKeys);
	    }
        }
	
	return dtmfKeys;
    }

    /*
     * data starts at RtpPacket.HEADER_SIZE
     */ 
    public String processData(int[] linearData) {
	numberOfTimesCalled++;
	long start = System.currentTimeMillis();

	if (sampleRateConverter != null) {
	    try {
		int nSamples = linearData.length;

	        linearData = sampleRateConverter.resample(linearData);

		if (Logger.logLevel >= Logger.LOG_DETAIL) {
		    Logger.println("Resample for Dtmf:  nSamples " + nSamples
		        + " new nSamples " + linearData.length);
		}
            } catch (IOException e) {
                Logger.println( "Call " + memberReceiver 
		    + " DtmfDecoder:  " + e.getMessage());
            }
	}

	int keys[] = new int[MAX_KEYS];		// decoded key

	int nkeys = decoder.decode(keys, 
	    AudioConversion.intsToShorts(linearData), 0);

	String dtmfKeys = null;

	if (nkeys > 0) {
	    char[] charKeys = new char[nkeys];

	    for (int i = 0; i < nkeys; i++) {
		charKeys[i] = char_keys[keys[i]];
	    }
	    dtmfKeys = new String(charKeys);
	}

	totalDecodeTime += (System.currentTimeMillis() - start);

	return dtmfKeys;
    }

    public void printStatistics() {
	Logger.writeFile("Call " + memberReceiver.toString() + ":  "
	    + "Dtmf detector calls:  " + numberOfTimesCalled);

	if (numberOfTimesCalled != 0) {
	    Logger.writeFile(memberReceiver.toString() 
	    + ":  Dtmf decoder average ms per call:  " 
	        + ((float)((float)totalDecodeTime / numberOfTimesCalled)));
	}
    }

}

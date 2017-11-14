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

import org.xiph.speex.*;

public class SpeexDecoder {

    private org.xiph.speex.SpeexDecoder speexDecoder;

    private int sampleRate;
    private int channels;

    private int decodes;
    private long decodeTime;

    private boolean bigEndian = true;

    public SpeexDecoder(int sampleRate, int channels) throws SpeexException {
    this.sampleRate = sampleRate;
    this.channels = channels;

        speexDecoder = new org.xiph.speex.SpeexDecoder();

    int mode = 0;

        String s = " Narrow Band";

        if (sampleRate > 8000) {
            s = " Wide Band";
            mode++;         // wide band
        }

        if (sampleRate > 16000) {
            s = " Ultra-Wide Band";
            mode++;         // ultra wide band
        }

    if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Initializing Speex Decoder using "
                + sampleRate + "/" + channels);
    }

        if (speexDecoder.init(mode, sampleRate, channels, true) == false) {
            throw new SpeexException(
        "Speex decoder initialization failed!");
        }

        try {
            speexDecoder.setBigEndian(true);
        } catch (Exception e) {
            bigEndian = false;
        }
    }

    /*
     * Decode speex data starting at offset for length bytes.
     * Return the length of the decoded data.
     */
    boolean debug = false;

    public int[] decodeToIntArray(byte[] data, int offset, int length) 
            throws SpeexException {

    byte[] byteData = decodeToByteArray(data, offset, length);

    return AudioConversion.bytesToInts(byteData);
    }

    public byte[] decodeToByteArray(byte[] data, int offset, int length) 
            throws SpeexException {

    if (Logger.logLevel >= Logger.LOG_MOREDETAIL) {
        Util.dump("decode input:  offset " + offset 
        + " length " + length, data, 0, offset + length);
    }

        long start = CurrentTime.getTime();

    try {
            speexDecoder.processData(data, offset, length);
    } catch (java.io.StreamCorruptedException e) {
        throw new SpeexException(e.getMessage());
    }

        int decodedLength = speexDecoder.getProcessedDataByteSize();

        if (decodedLength <= 0) {
            Logger.println("Speex decode data length is " + decodedLength);
            throw new SpeexException("Decoded length negative");
        }

    byte[] byteData = new byte[decodedLength];

        speexDecoder.getProcessedData(byteData, 0);

    if (bigEndian == false) {
        /*
         * The latest version of Speex only understands little endian
         */
        for (int i = 0; i < byteData.length; i += 2) {
            byte b = byteData[i];

            byteData[i] = byteData[i + 1];
            byteData[i + 1] = b;
        }
    }

        decodes++;
        decodeTime += (CurrentTime.getTime() - start);

    return byteData;
    }

    public int getDecodes() {
    return decodes;
    }

    public long getDecodeTime() {
    return decodeTime;
    }

    public void resetStatistics() {
    decodes = 0;
    decodeTime = 0;
    }

}


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

public class SpeexEncoder {

    private org.xiph.speex.SpeexEncoder speexEncoder;

    private int sampleRate;
    private int channels;

    private int pcmPacketSize;

    private int encodes;
    private int bytesEncoded = 0;
    private long encodeTime;

    private boolean bigEndian = true;

    public SpeexEncoder(int sampleRate, int channels) 
        throws SpeexException {

    this.sampleRate = sampleRate;
    this.channels = channels;

        if (sampleRate > 32000) {
            throw new SpeexException(
        "Speex cannot be used with sample rate " + sampleRate); 
        }

    speexEncoder = new org.xiph.speex.SpeexEncoder();

    int mode = 0;

        String s = "Narrow Band";

        if (sampleRate > 8000) {
            s = "Wide Band";
            mode++;         // wide band
        }

        if (sampleRate > 16000) {
            s = "Ultra-Wide Band";
            mode++;         // ultra wide band
        }

    if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("Initializing Speex encoder using "
                + sampleRate + "/" + channels + " " + s);
    }

        if (speexEncoder.init(mode, 0, sampleRate, channels) == false) {
            throw new SpeexException(
        "Speex encoder initialization failed!");
    }

    try {
        speexEncoder.setBigEndian(true);
    } catch (Exception e) {
        bigEndian = false;
    }

        speexEncoder.getEncoder().setVbr(true);

        pcmPacketSize = 2 * channels * speexEncoder.getFrameSize();;

    if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("speex frame size "
                + speexEncoder.getFrameSize()
                + " pcmPacketSize " + pcmPacketSize);
    }
    }

    public void setQuality(int quality) {
    speexEncoder.getEncoder().setQuality(quality);
    }

    public void setComplexity(int complexity) {
    // XXX setting complexity to 0 breaks it horribly!
    speexEncoder.getEncoder().setComplexity(complexity);
    }

    public int getPcmPacketSize() {
    return pcmPacketSize;
    }

    /*
     * processes data starting at offset for length bytes.
     * Return the length of the encoded data.
     */
    public int encode(int[] inData, byte[] outData, int outOffset) 
        throws SpeexException {

    return encode(AudioConversion.intsToBytes(inData), outData, 
        outOffset);
    }

    public int encode(byte[] inData, byte[] outData, int outOffset)
        throws SpeexException {

        if (Logger.logLevel == -59) {
        Logger.logLevel = 3;

        Util.dump("encode input:  length " 
        + inData.length, inData, 0, 16);
    }

    long start = CurrentTime.getTime();

    if (bigEndian == false) {
        /*
         * The latest version of JSpeex only understands little endian
         */
        byte[] data = new byte[inData.length];

            for (int i = 0; i < data.length; i += 2) {
            data[i] = inData[i + 1];
            data[i + 1] = inData[i];
        }

        inData = data;
    }

    try {
            speexEncoder.processData(inData, 0, inData.length);
    } catch (Exception e) {
        Logger.println("inData.length " + inData.length
        + " outData.length " + outData.length
        + " outOffset " + outOffset);

        e.printStackTrace();
        throw new SpeexException("SpeexEncode:  " + e.getMessage());
    }

    int encSize = speexEncoder.getProcessedDataByteSize();

        encodes++;
        encodeTime += (CurrentTime.getTime() - start);
        bytesEncoded += inData.length;

    speexEncoder.getProcessedData(outData, outOffset);

        if (Logger.logLevel >= Logger.LOG_MOREDETAIL) {
        Util.dump("encodeData output: " + encSize, outData, 0, encSize);
    }

    return encSize;
    }

    public int getEncodes() {
    return encodes;
    }

    public int getBytesEncoded() {
    return bytesEncoded;
    }

    public long getEncodeTime() {
    return encodeTime;
    }

    public void resetStatistics() {
    encodes = 0;
    bytesEncoded = 0;
    encodeTime = 0;
    }

}

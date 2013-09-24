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

import java.net.DatagramPacket;
import java.net.SocketAddress;

/**
 * Definitions for RTP data packets.  See RFC1889 for more details.
 *
 *
 *     The RTP header has the following format:
 *
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           timestamp                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           synchronization source (SSRC) identifier            |
 * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * |            contributing source (CSRC) identifiers             |
 * |                             ....                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 */
public class RtpPacket {
    /**
     * RTP Header size in bytes
     */
    public static final int HEADER_SIZE = 12;

    /**
     * Offset to start of data
     */
    public static final int DATA = HEADER_SIZE;

    /*
     * Header extension bit
     */
    public static final byte X_BIT = (byte)0x10;

    /**
     * Payload change to indicate comfort noise.  This is the
     * low 7 bits of byte 1 of the RTP packet.
     */
    public static final byte COMFORT_PAYLOAD = (byte)0xd;

    /**
     * Payload change to indicate ulaw data.  This is the
     * low 7 bits of byte 1 of the RTP packet.
     */
    public static final byte PCMU_PAYLOAD = (byte)0;

    /**
     * Change Media payload
     */
    public static final byte CHANGE_MEDIA_PAYLOAD = 127;   // XXX

    /**
     * Flag to indicate a significant event such as an unexpected
     * change in the sequence number after resuming sending data 
     * after comfort noise.
     */
    public static final byte MARK_BIT = (byte)0x80;

    /**
     * Flag to indicate dtmf key has been released.
     */
    public static final byte DTMF_END_BIT = (byte)0x80;

    /**
     * Level of comfort noise to generate
     */
    public static byte defaultNoiseLevel = (byte)62;
    public static byte comfortNoiseLevel = defaultNoiseLevel;

    /**
     * Number of milliseconds between RTP packets.
     */
    public final static int PACKET_PERIOD = 20;

    /**
     * Number of packets per second
     */
    public final static int PACKETS_PER_SECOND = 1000 / PACKET_PERIOD;

    /**
     * Number of bytes per sample for ULAW data
     */
    public final static int PCMU_SAMPLE_SIZE = 1;

    /**
     * Number of bytes per sample for linear data
     */
    public final static int PCM_SAMPLE_SIZE = 2;

    /**
     * Encoding values
     */
    public final static int PCMU_ENCODING = 0;
    public final static int PCM_ENCODING = 1;
    public final static int SPEEX_ENCODING = 2;

    public final static int MAX_SAMPLE_RATE = 48000;
    public final static int MAX_CHANNELS = 2;
     
    public final static int MAC_SAMPLE_RATE = 44100;
    public final static int MAC_CHANNELS = 2;
     
    /*
     * It would have been nicer to extend DatagramPacket rather than to
     * have a DatagramPacket here.   Then the DatagramPacket routines wouldn't
     * have to be duplicated below.  However, DatagramPacket is final
     * and can't be extended.
     */
    protected DatagramPacket packet;
    protected byte[] buffer;
    protected int bufferSize;	// total size of buffer

    protected int dataSize;	// data size for PACKET_PERIOD

    protected short rtpSequenceNumber = 1;
    protected long rtpTimestamp = 0;

    protected int samplesPerPacket;

    protected int size;		// actual size of data in this packet now

    protected int encoding;
    protected int sampleRate;
    protected int channels;

    public RtpPacket(byte[] buffer) {
	this.buffer = buffer;
	bufferSize = buffer.length;
    }

    public RtpPacket(int encoding, int sampleRate, int channels) {
	this(encoding, sampleRate, channels,
	    HEADER_SIZE + getDataSize(encoding, sampleRate, channels));
    }

    public RtpPacket(int encoding, int sampleRate, int channels,
	    int bufferSize) {

	this.encoding = encoding;
	this.sampleRate = sampleRate;
	this.channels = channels;
	this.bufferSize = bufferSize;

	//System.out.println("RtpPacket:  " 
	//    + (encoding == RtpPacket.PCMU_ENCODING ? "PCMU/" : "PCM/")
	//    + sampleRate + "/" + channels + "/" + encoding 
	//    + " bufferSize " + bufferSize);

	buffer = new byte[bufferSize];

        packet = new DatagramPacket(buffer, bufferSize);

	dataSize = getDataSize(encoding, sampleRate, channels);
    }

    public int getDataSize() {
	return getDataSize(encoding, sampleRate, channels);
    }
  
    public static int getMaxDataSize() {
	return getDataSize(PCM_ENCODING, MAX_SAMPLE_RATE, 2);
    }

    public static int getDataSize(int encoding, int sampleRate, int channels) {
	int dataSize;
	String e;

	int samplesPerPacket = (sampleRate * channels) / PACKETS_PER_SECOND;

	if (encoding == PCMU_ENCODING) {
	    dataSize = samplesPerPacket * PCMU_SAMPLE_SIZE;
	    e = "ULAW";
	} else if (encoding == PCM_ENCODING) {
	    dataSize = samplesPerPacket * PCM_SAMPLE_SIZE;
	    e = "PCM";
	} else {
	    dataSize = samplesPerPacket * PCM_SAMPLE_SIZE;
	    e = "SPEEX";
	}

	if (Logger.logLevel >= Logger.LOG_DEBUG) {
	    Logger.writeFile("Packet data size is " + dataSize
	        + " for " + e + "/" + sampleRate + "/" + channels);
	}

	return dataSize;
    }

    /**
     * Get number of samples in each packet
     */
    public int getSamplesPerPacket(int sampleRate, int channels) {
	return (sampleRate * channels) / PACKETS_PER_SECOND;
    }

    /*
     * Set X bit.
     */
    public void setX(boolean x) {
	if (x == true) {
	    buffer[0] |= X_BIT;
	} else {
	    buffer[0] &= ~X_BIT;
	}
    }

    public boolean getX() {
	return (buffer[0] & X_BIT) != 0;
    }

    /**
     * Get the RTP Payload type.
     * @return payload byte paylod
     */
    public byte getRtpPayload() {
	return (byte)(buffer[1] & ~MARK_BIT);
    }

    /**
     * Set the RTP Payload type.
     * @param payload byte payload
     */
    public void setRtpPayload(byte payload) {
	byte mark = (byte)(buffer[1] & MARK_BIT);

	buffer[1] = (byte)(payload | mark);
    }

    /**
     * Set the MARK bit.
     */
    public void setMark() {
	buffer[1] |= MARK_BIT;
    }

    public void clearMark() {
	buffer[1] &= ~MARK_BIT;
    }

    /**
     * Determine if the MARK bit is set.
     * @return isMarkSet boolean true if set
     */
    public boolean isMarkSet() {
	return (buffer[1] & MARK_BIT) != 0;
    }

    /**
     * Determine if the DTMF_END_BIT is set.
     * @return isDtmfSet boolean true if set
     */
    public boolean isDtmfEndSet() {
	return (buffer[DATA + 1] & DTMF_END_BIT) != 0;
    }

    /**
     * Get RTP sequence number
     * @return sequence number 
     */
    public short getRtpSequenceNumber() {
	return (short)
	    (((buffer[2] << 8) & 0xff00) | 
	    (buffer[3]) & 0xff);
    }
	
    public void setRtpSequenceNumber(short rtpSequenceNumber) {
	buffer[2] = (byte) ((rtpSequenceNumber >> 8) & 0xff);
	buffer[3] = (byte) (rtpSequenceNumber & 0xff);
    }

    /**
     * Get RTP timestamp
     * @return RTP timestamp
     */
    public long getRtpTimestamp() {
	long ts = (long)(((((long)(buffer[4] & 0xff)) << 24) & 0xff000000)
            | ((((long)(buffer[5] & 0xff)) << 16) & 0x00ff0000)
            | ((((long)(buffer[6] & 0xff)) << 8)  & 0x0000ff00)
            | ((long)(buffer[7] & 0xff))) & 0xffffffff;

	return ts;
    }

    public void setRtpTimestamp(int rtpTimestamp) {
	buffer[4] = (byte) ((rtpTimestamp >> 24) & 0xff);
	buffer[5] = (byte) ((rtpTimestamp >> 16) & 0xff);
	buffer[6] = (byte) ((rtpTimestamp >> 8) & 0xff);
	buffer[7] = (byte) (rtpTimestamp & 0xff);
    }

    public int getSynchronizationSource() {
        int ss = (int)(((((int)(buffer[8] & 0xff)) << 24) & 0xff000000)
	   | ((((int)(buffer[9] & 0xff)) << 16) & 0x00ff0000)
	   | ((((int)(buffer[10] & 0xff)) << 8)  & 0x0000ff00)
	   | ((int)(buffer[11] & 0xff))) & 0xffffffff;
 
        return ss;
    }

    public void setSynchronizationSource(int synchronizationSource) {
	buffer[8] = (byte) ((synchronizationSource >> 24) & 0xff); 
	buffer[9] = (byte) ((synchronizationSource >> 16) & 0xff); 
	buffer[10] = (byte) ((synchronizationSource >> 8) & 0xff); 
	buffer[11] = (byte) (synchronizationSource & 0xff); 
    }

    /**
     * Get the comfort noise level
     */
    public byte getComfortNoiseLevel() {
	return buffer[DATA];
    }

    /**
     * Get the packet data buffer
     * @return data buffer byte array data buffer
     */
    public byte[] getData() {
	return packet.getData();
    }

    /**
     * Set the packet data buffer
     */
    public void setBuffer(byte[] buffer) {
	this.buffer = buffer;
	packet.setData(buffer);
    }

    /**
     * Get the DatagramPacket.
     * @return datagramPacket DatagramPacket
     */
    public DatagramPacket getDatagramPacket() {
	return packet;
    }

    /**
     * Get data buffer length.
     * @ return length int length
     */
    public int getLength() {
	return packet.getLength();
    }

    public int getLinearLength() {
	if (encoding == PCMU_ENCODING) {
	    return ((packet.getLength() - HEADER_SIZE) * 2) + HEADER_SIZE;
	}

	return packet.getLength();
    }

    /**
     * Get the SocketAddress of the sender of this packet.
     * @return socketAddress SocketAddress
     */
    public SocketAddress getSocketAddress() {
	return packet.getSocketAddress();
    }

    /**
     * Set the length of data to send
     * @param size int size
     */
    public void setLength(int size) {
	this.size = size;
	packet.setLength(size);
    }

    /**
     * Set the SocketAddress of where to send this packet.
     */
    public void setSocketAddress(SocketAddress socketAddress) {
	packet.setSocketAddress(socketAddress);
    }

    /**
     * Translate comfort noise level to a multiplier for the volume level.
     *
     * The volume level is used to adjust the volume of the comfort noise
     * we play to a HeadSet.
     *
     * The comfort noise level is a byte with values from 0 to 127 
     * representing 0.
     *
     * We have arbitrarily assigned the value 64 to be a volumeLevel of 1.0
     * w.r.t. the comfort noise audio file that we use to generate 
     * comfort noise.
     *
     * Values smaller than 64 increase volume; values bigger than 64
     * reduce volume.  A value of 0 increases the volume by 4.0.
     * A value of 127 reduces the volume to 1/4.
     *
     * Feel free to adjust these values!
     */
    public static double getVolumeLevel(byte comfortNoiseLevel) {
	if (comfortNoiseLevel < 0 || 
		comfortNoiseLevel == defaultNoiseLevel) {

	    return 1.0F;
	}
	
	double volumeLevel;

	if (comfortNoiseLevel > defaultNoiseLevel) {
	    volumeLevel = ((double)defaultNoiseLevel - comfortNoiseLevel - 1) / 
	        defaultNoiseLevel / 4;
	} else {
	    volumeLevel = ((double)defaultNoiseLevel - comfortNoiseLevel) / 
	        defaultNoiseLevel * 4;
	}

	return volumeLevel;
    }

    /**
     * Set the default comfort noise value.
     */
    public static void setDefaultComfortNoiseLevel(byte comfortNoiseLevel) {
	RtpPacket.comfortNoiseLevel = comfortNoiseLevel;
    }

    /**
     * Get the default comfort noise volume level.
     */
    public static byte getDefaultComfortNoiseLevel() {
	return comfortNoiseLevel;
    }
    
}

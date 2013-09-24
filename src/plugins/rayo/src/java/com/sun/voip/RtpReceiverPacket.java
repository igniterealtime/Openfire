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

/*
 * Packet for Receiving RTP data
 */
public class RtpReceiverPacket extends RtpPacket {
    String id;

    private long previousRtpTimestamp;

    /*
     * Statistics
     */
    private long packetsDropped = 0;
    private int outOfSequencePackets;
    private int shortPackets = 0;
    private int wrongRtpTimestamp = 0;
    private int sequenceOffset = 0;

    public RtpReceiverPacket(String id, int encoding, int sampleRate, 
	    int channels, int bufferSize) {

	super(encoding, sampleRate, channels, bufferSize);

	this.id = id;		// for Logging
    }

    public RtpReceiverPacket(String id, int encoding, int sampleRate, 
	    int channels) {

	super(encoding, sampleRate, channels);

	this.id = id;		// for Logging
    }

    public void setId(String id) {
	this.id = id;
    }

    /**
     * Update RTP receiver information.  Keep statistics on packet arrival 
     * times and out-of-sequence packets.
     * Returns offset of packet sequence relative to the correct sequence
     * e.g. 0 means packet is in order, -1 means packet seq is one less 
     * than expected, +1 means packet seq is one higher than expected.
     */
    public void updateRtpHeader(int length) {
	long packetRtpTimestamp = getRtpTimestamp();
	short packetRtpSequenceNumber = getRtpSequenceNumber();

	boolean outOfSequence = false;

	sequenceOffset = 0;

	if (isMarkSet()) {
	    /*
	     * The MARK_BIT is supposed to be set in the first packet
	     * and also the first packet with PCMU_PAYLOAD after COMFORT_PAYLOAD
	     * has been received.
	     * 
	     * When the MARK_BIT is set, we accept the sequence number
	     * and timestamp in the packet.
	     *
	     * Calculate what we expect the next packet to have.
	     */
	    setMark();		// make sure mark is set;
	    rtpSequenceNumber = (short) (packetRtpSequenceNumber + 1);

	    rtpTimestamp = packetRtpTimestamp + length;
	} else {
	    /*
	     * Normal packet without the MARK_BIT set.
	     * Verify the sequenceNumber and timestamp
	     *
	     * Set what we expect next packet to have
	     */
            if (packetRtpSequenceNumber == rtpSequenceNumber) {
	        rtpSequenceNumber = ++rtpSequenceNumber;
	    } else {
                outOfSequencePackets++;
		outOfSequence = true;

		sequenceOffset = packetRtpSequenceNumber - rtpSequenceNumber;

                if (Logger.logLevel >= Logger.LOG_INFO) {
                    Logger.writeFile(id + ":  PACKET OUT OF SEQUENCE!  "
			+ "seq expected 0x"
                        + Integer.toHexString(rtpSequenceNumber)
                        + ", got 0x"
                        + Integer.toHexString(packetRtpSequenceNumber)
                        + ", total out of seq " + outOfSequencePackets
			+ ", payload " + getRtpPayload() 
			+ ", length " + getLength());
		}

		/*
		 * Set the sequence number to what we expect next
		 * but never lower the sequence number.
		 * We have to be careful here because after
		 * 0x7fff the next sequence number is negative.
		 */
		if ((packetRtpSequenceNumber > 0 && rtpSequenceNumber > 0) ||
		    (packetRtpSequenceNumber < 0 && rtpSequenceNumber < 0)) {

		    if (packetRtpSequenceNumber > rtpSequenceNumber) {
		        rtpSequenceNumber = (short)
		            (packetRtpSequenceNumber + 1); // reset seq number
		    } 
		} else {
		    rtpSequenceNumber = (short)
		        (packetRtpSequenceNumber + 1); // reset seq number
		}

		/*
		 * XXX We may want to insert packets which are missing.
		 * One approach (Norco does this) is to duplicate 
		 * the last packet received.  Maybe inserting a packet 
		 * of silence would be ok too.
		 * This is handled by the ConferenceMember.
		 */
            }

            if (rtpTimestamp == packetRtpTimestamp) {
	        rtpTimestamp += length; 
	    } else {
		if (outOfSequence == false) {
		    /*
		     * Don't report wrong timestamp if packet is out of sequence.
		     */
                    wrongRtpTimestamp++;

		    if ((wrongRtpTimestamp % 1000) == 0) {
                        if (Logger.logLevel >= Logger.LOG_INFO) {
                            Logger.writeFile(id + " Bad packet received:  len " 
			        + length 
			        + ", ts off by " 
			        + (long)(packetRtpTimestamp - rtpTimestamp)
                                + ", total wrong ts " + wrongRtpTimestamp 
			        + ", seq " + packetRtpSequenceNumber);
			}
                    }
		}

		/*
		 * XXX Should we reset the timestamp to what we just got?
		 * Seems we have to...
		 * It's possible some packets were lost and we're
		 * never going to get the timestamp we'd like.
		 */
		if (packetRtpTimestamp > rtpTimestamp) { 
		    rtpTimestamp = packetRtpTimestamp + length;
		} 
            }
	}

	rtpSequenceNumber &= 0xffff;
	rtpTimestamp &= 0xffffffff;
	previousRtpTimestamp = packetRtpTimestamp;
    }

    public long getRtpTimestampDiff() {
	return rtpTimestamp - previousRtpTimestamp;
    }

    public void incrementShortPackets() {
	shortPackets++;
    }

    public int getShortPackets() {
	return shortPackets;
    }

    public void incrementOutOfSequencePackets() {
	outOfSequencePackets++;
    }

    public int getOutOfSequencePackets() {
	return outOfSequencePackets;
    }

    public void incrementWrongRtpTimestamp() {
	wrongRtpTimestamp++;
    }

    public int getWrongRtpTimestamp() {
	return wrongRtpTimestamp;
    }

    public int getSequenceOffset() {
	return sequenceOffset;
    }

    /*
     * Make sure sequence number wraps properly.
     */ 
    public static void main(String[] args) {
        RtpReceiverPacket packet = new RtpReceiverPacket(
            "Test", PCM_ENCODING, 8000, 1);

        Logger.logLevel = 5;

        short expected = packet.rtpSequenceNumber;

        while (true) {
	    packet.updateRtpHeader(180);

            if (packet.rtpSequenceNumber != expected) {
                Logger.println("expected " + expected
                    + " got " + packet.rtpSequenceNumber);
            }

            expected++;
	    packet.buffer[2] = (byte) ((packet.rtpSequenceNumber >> 8) & 0xff);
	    packet.buffer[3] = (byte) (packet.rtpSequenceNumber & 0xff);
        }
    }

}

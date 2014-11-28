/**
 *    Copyright 2012 Voxbone SA/NV
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.ifsoft.sip;

/**
 * Utility functions for Parsing information from RTP packets
 *
 */
public class RtpUtil
{

	/*

	   The RTP header has the following format:

	    0                   1                   2                   3
	    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
	   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	   |V=2|P|X|  CC   |M|     PT      |       sequence number         |
	   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	   |                           timestamp                           |
	   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	   |           synchronization source (SSRC) identifier            |
	   +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
	   |            contributing source (CSRC) identifiers             |
	   |                             ....                              |
	   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

	 */
	public static void buildRtpHeader(byte [] buffer, int payload, short seq, long timestamp, byte [] ssrc)
	{
		buffer[0] = (byte) 0x80;
		buffer[1] = (byte) payload;
		setSequenceNumber(buffer, seq);
		setTimeStamp(buffer, timestamp);
		setSSRC(buffer, ssrc);
	}

	/*
	    0                   1                   2                   3
	    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
	   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	   |V=2|P|   FMT   |       PT      |          length               |
	   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	   |                  SSRC of packet sender                        |
	   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	   |                  SSRC of media source                         |
	   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	   :            Feedback Control Information (FCI)                 :
	   :                                                               :


	      		Name   | Value | Brief Description
	         ----------+-------+------------------------------------
	            RTPFB  |  205  | Transport layer FB message
	            PSFB   |  206  | Payload-specific FB message

	    0                   1                   2                   3
	    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
	   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	   |                              SSRC                             |
	   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	   | Seq nr.       |    Reserved                                   |
	   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+


	 */
	public static void buildFIR(byte [] buffer, int seq, byte [] senderSsrc, byte [] destSsrc)
	{
		byte [] sr ={(byte)0x80,(byte)0xc9,(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,(byte)0x81,(byte)0xca,(byte)0x00,(byte)0x02,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,(byte)0x01,(byte)0x01,(byte)0x31,(byte)0x00};
        	System.arraycopy(sr, 0, buffer, 0, sr.length);
        	buffer[sr.length+0] = (byte) 0x84;
        	buffer[sr.length+1] = (byte) 206;
        	buffer[sr.length+2] = 0;
        	buffer[sr.length+3] = 4;

        	// packet sender 0001
        	//System.arraycopy(senderSsrc, 0, buffer, sr.length+4, 4);
        	buffer[sr.length +4+3] = (byte)0x01;

	        // media source keeps 0
	        //System.arraycopy(senderSsrc, 0, buffer, sr.length+8, 4);
	        System.arraycopy(destSsrc, 0, buffer, sr.length+12, 4);
	        buffer[sr.length+16] = (byte) seq;
	}

	public static int getFIRSequence(byte [] buffer, int start, int end)
    	{
        	if(start+1 >= end)
        	{
        	    // no FIR
        	    return -1;
        	}

        	if(buffer[start+1] == (byte)206 && (buffer[start]&0x0F) == (byte)0x04)
        	{
        	    // fir found!
        	    return (int)buffer[start+16]&0xFF;
        	}
        	else
        	{
        	    // not fir, check next rtcp
            	int length = getShort(buffer, start+2)*4 + 4;
            	return getFIRSequence(buffer, start+length, end);
        	}
    	}

	public static short getSequenceNumber(byte [] buffer)
	{
		return getShort(buffer, 2);
	}

	public static void setSequenceNumber(byte [] buffer, short seq)
	{
		buffer[3] = (byte) (seq & 0xFF);
		buffer[2] = (byte) ((seq >> 8) & 0xFF);
	}

	public static byte [] getSSRC(byte [] buffer)
	{
		byte ssrc[] = new byte[4];
		System.arraycopy(buffer, 8, ssrc, 0, 4);
		return ssrc;
	}

	public static void setSSRC(byte [] buffer, byte [] ssrc)
	{
		System.arraycopy(ssrc, 0, buffer, 8, 4);
	}

	public static long getTimeStamp(byte [] buffer)
	{
		long timestamp = 0;
		timestamp = 0xFF & buffer[4];

		timestamp = timestamp << 8;
		timestamp |= 0xFF & buffer[5];

		timestamp = timestamp << 8;
		timestamp |= 0xFF & buffer[6];

		timestamp = timestamp << 8;
		timestamp |= 0xFF & buffer[7];

		return timestamp;
	}

	public static void setTimeStamp(byte [] buffer, long timestamp)
	{
		buffer[7] = (byte) (timestamp & 0xFF);
		buffer[6] = (byte) ((timestamp >> 8) & 0xFF);
		buffer[5] = (byte) ((timestamp >> 16) & 0xFF);
		buffer[4] = (byte) ((timestamp >> 24) & 0xFF);
	}

	public static void setMarker(byte [] buffer, boolean set)
	{
		if (set)
		{
			buffer[1] |= (0x1 << 7);
		}
		else
		{
			buffer[1] &= (0x7F);
		}
	}

	public static int getNALType(byte [] input, int start)
	{
		return (int) (0x1F & input[start]);
	}

	public static short getShort(byte [] buffer, int offset)
	{
		int seq = 0;
		seq = 0xFF & buffer[offset];
		seq = seq << 8;
		seq |= 0xFF & buffer[offset + 1];
		return (short) seq;
	}

	public static int filterSVC(byte [] input, byte [] output, int input_size)
	{
		int output_offset = 12;
		int input_offset = 12 + 2 + 2 + 8;

		System.arraycopy(input, 0, output, 0, 12);

		output[0] &= 0xEF;

		int nalType = getNALType(input, input_offset);
		if (nalType == 24)
		{
			output[output_offset] = input[input_offset];
			input_offset += 1;
			output_offset += 1;

			while (input_offset < input_size)
			{
				short length = getShort(input, input_offset);
				nalType = getNALType(input, input_offset + 2);
				if (nalType != 30 && nalType != 14 && nalType != 20)
				{
					System.arraycopy(input, input_offset, output, output_offset, length + 2);
					input_offset += length + 2;
					output_offset += length + 2;
				}
				else
				{
					input_offset += length + 2;
				}
			}
		}
		else if (nalType != 30 && nalType != 14 && nalType != 20)
		{
			System.arraycopy(input, input_offset, output, output_offset, input_size - input_offset);
			output_offset += input_size - input_offset;
		}

		return output_offset;
	}

}

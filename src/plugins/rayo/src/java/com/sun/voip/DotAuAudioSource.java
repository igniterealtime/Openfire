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
import java.io.InputStream;


/** 
 * Read audio samples from a Sun .au file.
 * Up/down sample as necessary.
 */
public class DotAuAudioSource extends FileAudioSource {
    
    private String path;
    private int sampleRate;
    private int channels;
    private int encoding;

    private static final int AUDIO_FILE_HEADER_SIZE = 24;

    private InputStream in;

    /*
     * Read an audio file.  Pad with linear silence.
     */
    public DotAuAudioSource(String path) throws IOException {
	this.path = path;
	initialize();
    }

    private void initialize() throws IOException {
	done();

	in = getInputStream(path);

	/*
	 * Audio file header
  	 *
         * int magic = 0x2e 0x73 0x6e 0x64 which is ".snd"
         * int hdr_size;
         * int data_size;
         * int encoding = 1 for ulaw, 3 for linear;
         * int sample_rate;
         * int channels;
	 */
	int bytesAvailable = 0;

	try {
	    bytesAvailable = in.available();
	} catch (IOException ioe) {
	    throw new IOException("available() failed " + path);
	}

	if (bytesAvailable < AUDIO_FILE_HEADER_SIZE) {
	    throw new IOException ("audiofile " + path + " is too small " + 
		bytesAvailable);
	}

	byte[] audioFileHeader = new byte[AUDIO_FILE_HEADER_SIZE];

	try {
	    in.read(audioFileHeader, 0, AUDIO_FILE_HEADER_SIZE);
	} catch (Exception e) {
	    throw new IOException("error reading " + path + " "
		+ e.getMessage());
	}
	    
	encoding = audioFileHeader[15];

	channels = audioFileHeader[23];

	if (audioFileHeader[0] != 0x2e || audioFileHeader[1] != 0x73 ||
	    audioFileHeader[2] != 0x6e || audioFileHeader[3] != 0x64 ||
	    (encoding != ULAW && encoding != LINEAR) ||
	    channels > 16) {
	
	    throw new IOException("bad audio file header " + path);
	}

	sampleRate = 
	    ((((int)audioFileHeader[16]) << 24) & 0xff000000) +
	    ((((int)audioFileHeader[17]) << 16) & 0x00ff0000) +
	    ((((int)audioFileHeader[18]) << 8) & 0x0000ff00) +
	    (((int)audioFileHeader[19]) & 0xff);

	if (Logger.logLevel >= Logger.LOG_MOREINFO) {
	    Logger.println("AudioFile is " + path + ".  Resource is " + 
                DotAuAudioSource.class.getResource(path) + ".  size " 
                + in.available() + " encoding " + encoding 
		+ " channels " + channels + " sampleRate " 
		+ sampleRate);
	}

	try {
            bytesAvailable = in.available();
            
            int hdr_size =
                ((audioFileHeader[4] << 24) & 0xff0000) |
                ((audioFileHeader[5] << 16) & 0xff0000) |
                ((audioFileHeader[6] <<  8) & 0xff00) | 
                (audioFileHeader[7] & 0xff);
                
            if (hdr_size > AUDIO_FILE_HEADER_SIZE) {
                /*
                 * read remainder of the header and discard
                 */
                int excess_hdr_size =
                    hdr_size - AUDIO_FILE_HEADER_SIZE;
                    
                byte[] data = new byte[excess_hdr_size];
                
                in.read(data, 0, excess_hdr_size);

		if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		    Logger.println("Reading excess header " 
		        + " hdr size " + hdr_size 
			+ " excess " + excess_hdr_size);
		}
            }  
	} catch (Exception e) {
            throw new IOException("Can't read data!  " + path + " "
		+ e.getMessage());
        }
    }

    public int[] getLinearData(int sampleTime) throws IOException {
	byte[] fileData = readAudioFile(sampleTime);

	if (fileData == null) {
	    return null;
	}

	int[] linearData;
	if (encoding == ULAW) {
            // 1 ulaw byte for each int
            linearData = new int[fileData.length];
	    AudioConversion.ulawToLinear(fileData, 0, fileData.length, 
		linearData);
	} else {
            // 2 linear bytes for each int
            linearData = new int[fileData.length / 2];

	    for (int i = 0; i < linearData.length; i++) {
	        linearData[i] = (int) 
		    ((short)(((fileData[2 * i] << 8) & 0xff00) |
	            (fileData[(2 * i) + 1] & 0xff)));
	    }
	}

	return linearData;
    }

    private byte[] readAudioFile(int sampleTime) throws IOException {
	int bytesAvailable;

	if (in == null || (bytesAvailable = in.available()) == 0) {
	    done();
	    return null;
	}

	int sampleSize = 2;

	if (encoding == AudioSource.ULAW) {
	    sampleSize = 1;
	}

	int len = 
	    sampleRate * sampleTime * channels * sampleSize / 1000;

	byte[] data = new byte[len];

	try {
	    int readSize;

	    readSize = Math.min(len, bytesAvailable);
	
	    /*
	     * Read the file
	     */ 
    	    in.read(data, 0, readSize);

	    byte b;

	    if (encoding == ULAW) {
		b = AudioConversion.PCMU_SILENCE;
	    } else {
		b = AudioConversion.PCM_SILENCE;
	    }

	    for (int i = readSize; i < len; i++) {
		data[i] = b;
	    }
	} catch (IOException e) {
	    throw new IOException("Can't read data!  " + path + " "
		+ e.getMessage());
	}

	return data;
    }

    public int getSampleRate() {
	return sampleRate;
    }

    public int getChannels() {
	return channels;
    }

    public void rewind() throws IOException {
	initialize();
    }

    public void done() {
	if (in != null) {
	    try {
	        in.close();
	    } catch (IOException e) {
	    }
	}
    }

}

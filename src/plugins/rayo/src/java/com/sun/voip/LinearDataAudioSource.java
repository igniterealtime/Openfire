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

/*
 * Return samples from a linear data array.
 * FreeTTS generates a linear data array.
 */
public class LinearDataAudioSource implements AudioSource {

    private int[] linearData;
    private int sampleRate;
    private int channels;

    private int linearOffset = 0;

    public LinearDataAudioSource(int[] linearData, 
        int sampleRate, int channels) {

    this.linearData = linearData;
    this.sampleRate = sampleRate;
    this.channels = channels;
    }

    /*
     * Get linear data from the linear data array.
     */
    public int[] getLinearData(int sampleTime) throws IOException {
        if (linearOffset >= linearData.length) {
            return null;
        }

        int byteLen = sampleRate * sampleTime * channels * 2 / 1000;

    int[] data = new int[byteLen / 2];

    int dataOffset = 0;

        for (int i = 0; i < byteLen; i += 2) {
            if (linearOffset >= linearData.length) {
                /*
                 * We've reached the end of the linear data.
                 * Pad the rest of linearData will be zero which
                 * is linear silence.
                 */
                break;
        }

            data[dataOffset++] = linearData[linearOffset++];
    }

        return data;
    }

    public void rewind() throws IOException {
    linearOffset = 0;
    }

    public void done() {
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

}

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
 * Generate sine waves
 */
public class SineWaveAudioSource implements AudioSource {

    private int frequency;
    private int duration;
    private float volume;

    private int sampleRate;
    private int channels;

    private double amplitude = 2048.0;
    private int sample = 0;

    private int timeRemaining;
 
    private double twoPI = Math.PI * 2;

    public SineWaveAudioSource(int frequency, int duration,
        float volume, int sampleRate, int channels) {

    this.frequency = frequency;
    this.duration = duration;
    this.volume = volume;

    this.sampleRate = sampleRate;
    this.channels = channels;

    timeRemaining = duration;
    }

    /*
     * Generate linear data
     */
    public int[] getLinearData(int sampleTime) throws IOException {
    if (timeRemaining <= 0) {
        return null;
    }

    timeRemaining -= sampleTime;

        int length = sampleRate * sampleTime * channels / 1000;

    int[] linearData = new int[length];

    /*
     * twoPI represents one full cycle.  twoPI / sampleRate is the increment
     * for each sample.  
     */
        for (int i = 0; i < length; i += (2 * channels)) {
        int s = (int) (amplitude * volume * 
        Math.sin(sample * twoPI * frequency / sampleRate));

            linearData[i] = s;

        if (channels == 2) {
                linearData[i + 1] = s;
        }

            sample++;
        }

    //Util.dump("sine", linearData, 64);
    return linearData;
    }

    public void rewind() throws IOException {
    sample = 0;
    timeRemaining = duration;
    }

    public void done() {
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public static void main(String[] args) {
    SineWaveAudioSource s = new SineWaveAudioSource(440, 2000, 1.0F, 8000, 1);

    try {
        int[] d = s.getLinearData(20);

        Util.dump("SineWaveData", d, 0, d.length);
    } catch (IOException e) {
        Logger.println(e.getMessage());
    }
    }

}

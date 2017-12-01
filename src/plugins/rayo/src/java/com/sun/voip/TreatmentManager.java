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

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;

/**
 * Read and manager audio treatments
 */
public class TreatmentManager implements MixDataSource {
    private String treatment;
    private int repeatCount;
    private int sampleRate;
    private int channels;

    private boolean isPaused = false;
    private boolean isStopped = false;

    /*
     * This ArrayList contains one byte[] element per audio file.
     */
    private ArrayList<AudioSource> treatments = new ArrayList();

    private ArrayList treatmentDoneListeners = new ArrayList();

    private static String[] soundPath;

    static {
    String s = System.getProperty("com.sun.voip.server.Bridge.soundPath", "/com/sun/voip/server/sounds");

    String[] sp = s.split(":");

    soundPath = new String[sp.length + 1];

    for (int i = 0; i < sp.length; i++) {
        soundPath[i] = sp[i];
    }

    /*
     * On Windows user.dir contains a ":" such as C:\
     */
    soundPath[sp.length] = System.getProperty("user.dir");
    }

    public TreatmentManager(String treatment, int repeatCount)
        throws IOException {

    this(treatment, repeatCount, 8000, 1);
    }

    public TreatmentManager(String treatment, int repeatCount,
        int sampleRate, int channels) throws IOException {

    this.treatment = treatment;
    this.repeatCount = repeatCount;
    this.sampleRate = sampleRate;
    this.channels = channels;

    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("new treatment:  " + treatment
        + " repeat " + repeatCount + " sampleRate " + sampleRate
        + " channels " + channels);
    }

    parseTreatment(treatment);
    }

    public String getId() {
    return treatment;
    }

    public int getSampleRate() {
    AudioSource audioSource = this.audioSource;

    if (audioSource == null) {
        audioSource = getAudioSource();
    }

    if (audioSource == null) {
        return 0;
    }

    return audioSource.getSampleRate();
    }

    public int getChannels() {
    AudioSource audioSource = this.audioSource;

    if (audioSource == null) {
        audioSource = getAudioSource();
    }

    if (audioSource == null) {
        return 0;
    }

    return audioSource.getChannels();
    }

    public void pause(boolean isPaused) {
    this.isPaused = isPaused;

    Logger.println("TreatmentManager paused " + isPaused);
    }

    public boolean isPaused() {
    return isPaused;
    }


    private int[] previousContribution;
    private int[] currentContribution;

    public String getSourceId() {
    return treatment;
    }

    public boolean contributionIsInCommonMix() {
    return true;
    }

    public int[] getPreviousContribution() {
    return previousContribution;
    }

    public int[] getCurrentContribution() {
    return currentContribution;
    }

    public void saveCurrentContribution() {
    previousContribution = currentContribution;
    currentContribution = getLinearData(RtpPacket.PACKET_PERIOD);
    }

    private int treatmentIndex = 0;
    private AudioSource audioSource;
    private SampleRateConverter sampleRateConverter;

    public byte[] getLinearDataBytes(int sampleTime) {
    if (isStopped) {
        return null;
    }

    int[] intData = getLinearData(sampleTime);

    if (intData == null) {
        return null;
    }

    byte[] byteData = new byte[intData.length * 2];

    try {
        AudioConversion.intsToBytes(intData, byteData, 0);
    } catch (Exception e) {
        Logger.println("getLinearDataBytes, intData len "
        + intData.length + " byteData len " + byteData.length);
        e.printStackTrace();
        return null;
    }

    return byteData;
    }

    public int[] getLinearData(int sampleTime) {

    if (isPaused) {
        return null;
    }

    synchronized (treatments) {
        audioSource = getAudioSource();

        if (audioSource == null) {
        Logger.println(
            "Audio source is null, stopping treatment "
            + treatment);
        stopTreatment();
            return null;
        }
    }

    int[] linearData = null;

    try {
        linearData = audioSource.getLinearData(sampleTime);
    } catch (IOException e) {
        Logger.println("Can't read linear data for " + treatment
        + " " + e.getMessage());
    }

    if (linearData != null) {
        if (sampleRateConverter != null) {
        try {
            linearData = sampleRateConverter.resample(linearData);
        } catch (IOException e) {
            Logger.println("Can't resample treatment! "
            + e.getMessage());

            return null;
        }
        }
        return linearData;
    }

    try {
        audioSource.rewind();
    } catch (IOException e) {
        Logger.println("Can't rewind treatment " + treatment
        + " " + e.getMessage());
    }

    treatmentIndex++;

    synchronized (treatments) {
        if (treatmentIndex >= treatments.size()) {
            /*
             * We're done playing all of the data, repeat if necessary
             */
            if (repeatCount != -1) {
            if (repeatCount == 0) {
                if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                    Logger.println("done playing treatment "
                    + treatment);
                }
                stopTreatment();
                return null;	// all done
            }
            repeatCount--;
            }
            treatmentIndex = 0;
        }
    }

    sampleRateConverter = null;
    audioSource = getAudioSource();

    if (audioSource == null) {
        return null;
    }

    try {
        linearData = audioSource.getLinearData(sampleTime);

        if (linearData != null && sampleRateConverter != null) {
        linearData = sampleRateConverter.resample(linearData);
        }

        return linearData;
    } catch (IOException e) {
        Logger.println("Can't read linear data for " + treatment
        + " " + e.getMessage());
    }
    return null;
    }

    private AudioSource getAudioSource() {
    synchronized (treatments) {
        if (treatments.size() == 0) {
        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Treatments list is empty for " + treatment);
        }
            return null;
        }

        AudioSource audioSource = treatments.get(treatmentIndex);

        if (sampleRateConverter != null) {
            return audioSource;
        }

        if (sampleRate != audioSource.getSampleRate() ||
                channels != audioSource.getChannels()) {

            try {
                sampleRateConverter =
                new SampleRateConverter("Treatment",
                audioSource.getSampleRate(),
                    audioSource.getChannels(),
                sampleRate, channels);
            } catch (IOException e) {
            Logger.println("Can't resample treatment! "
                + e.getMessage());
            return null;
            }
        }
        return audioSource;
    }
    }

    /*
     * Register to be notified when the treatment finishes.
     */
    public void addTreatmentDoneListener(TreatmentDoneListener listener) {
    synchronized (treatmentDoneListeners) {
        treatmentDoneListeners.add(listener);
    }
    }

    public void removeTreatmentDoneListener(TreatmentDoneListener listener) {
    synchronized (treatmentDoneListeners) {
        treatmentDoneListeners.remove(listener);
    }
    }

    public void stopTreatment() {
    stopTreatment(true);
    }

    public void stopTreatment(boolean notify) {
    if (isStopped) {
        return;
    }

    isStopped = true;

    for (AudioSource audioSource : treatments) {
        audioSource.done();
    }

    synchronized (treatments) {
        treatments.clear();
    }

    if (notify == false) {
        return;
    }

    repeatCount = 0;

    synchronized (treatmentDoneListeners) {
        while (treatmentDoneListeners.size() > 0) {
            TreatmentDoneListener listener = (TreatmentDoneListener)
            treatmentDoneListeners.remove(0);
            listener.treatmentDoneNotification(this);
        }
    }

    if (audioSource != null) {
        try {
        audioSource.done();
        } catch (Exception e) {
        Logger.println("Exception calling audioSource.done() " + e.getMessage());
        }
    }
    }

    private void addTreatment(String path) throws IOException {
    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("adding " + path);
    }

    synchronized (treatments) {
            if (path.substring(0, 1).equals(File.separator) ||
           path.startsWith("/") ||
           path.startsWith("http://")) {

                AudioSource as = FileAudioSource.getAudioSource(path);

        if (as == null) {
            throw new IOException("Invalid treatment: " + path);
        }

        treatments.add(as);
        return;
        }

        for (int i = 0; i < soundPath.length; i++) {

                String s = soundPath[i] + File.separator + path;

                AudioSource as = FileAudioSource.getAudioSource(s);

                if (as != null) {
                    treatments.add(as);
            return;
        }
        }

        throw new IOException("Invalid treatment: " + path);
    }
    }

    /*
     * The rest of this file contains methods to parse treatments and
     * add them to the treatment manager linear data vector.
     */
    private static String[] dtmf = {
        "dtmf0.au",
        "dtmf1.au",
        "dtmf2.au",
        "dtmf3.au",
        "dtmf4.au",
        "dtmf5.au",
        "dtmf6.au",
        "dtmf7.au",
        "dtmf8.au",
        "dtmf9.au",
        "dtmfpound.au",
        "dtmfstar.au"
    };

    /*
     * The treatment specifier is as follows:
     * "file:[<commas>]<path>[<commas>];file:[<commas>]<path><commas>; ...]
     * "dtmf:[<commas>]<0-9#*>[<commas>][<0-9#*>][<commas>] ...
     * "tts:<text>;<text>; ...
     * "s:<frequency>.<milliseconds>[+<frequency>.<milliseconds>...]
     * each "," is worth 100 ms of silence.
     */
    private void parseTreatment(String treatment) throws IOException {
    String treatments[] = treatment.split(";");

    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        for (int i = 0; i < treatments.length; i++) {
            Logger.println("treatments:  " + treatments[i]);
        }
    }

    for (int i = 0; i < treatments.length; i++) {
        String currentTreatment = treatments[i];

            if (currentTreatment.indexOf("dtmf:") == 0) {
        addDtmfTreatment(currentTreatment.substring(5));
        } else if (currentTreatment.indexOf("d:") == 0) {
        addDtmfTreatment(currentTreatment.substring(2));
            } else if (currentTreatment.indexOf("tts:") == 0) {
        addTtsTreatment(currentTreatment.substring(4));
        } else if (currentTreatment.indexOf("t:") == 0) {
        addTtsTreatment(currentTreatment.substring(2));
        } else if (currentTreatment.indexOf("s:") == 0) {
        addSineWaveTreatment(currentTreatment.substring(2));
            } else {
        if (currentTreatment.indexOf("file:") == 0) {
            currentTreatment = currentTreatment.substring(5);
                }
                addFileTreatment(currentTreatment);
            }
        }
    }

    /*
     * treatment looks like [<commas>]<number>[<commas>]...
     */
    private void addDtmfTreatment(String treatment) throws IOException {
    String s = treatment.replaceAll("[ \t]", "");

    for (int i = 0; i < s.length(); i++) {
        if (s.charAt(i) == ',') {
        addSilence();
        } else {
        addOneDtmfTreatment(s.charAt(i));
        }
    }
    }

    private void addOneDtmfTreatment(char c) throws IOException {
        int n;

        if (c == '#') {
            n = 10;
        } else if (c == '*') {
            n = 11;
        } else {
            try {
                n = Integer.parseInt(String.valueOf(c));
            } catch (NumberFormatException e) {
                Logger.error("invalid dtmf treatment code '" + c + "'");
                return;
            }
        }

        if (n < 0 || n > 11) {
            Logger.error("invalid dtmf treatment code " + c);
        return;
    }

        String s = dtmf[n];
        addTreatment(s);
        return;
    }

    /*
     * treatment is like <text>
     */
    private void addTtsTreatment(String treatment) throws IOException {

        int[] linearData = null;

        try {
            linearData = FreeTTSClient.textToSpeech(treatment);
        } catch (IOException e) {
            Logger.println("Can't convert text to speech '" + treatment + "' "
                + e.getMessage());
            throw new IOException("Can't convert text to speech '" + treatment
        + "'");
        }

        treatment = "FreeTTS";

        /*
         * TTS data is always 16000/1
         */
        synchronized (treatments) {
            treatments.add(new LinearDataAudioSource(linearData, 16000, 1));
        }
    }

    private void addSineWaveTreatment(String treatment) throws IOException {

    String s = treatment.replaceAll("[\t]", "");

    String[] notes;

    notes = treatment.split("[\\+]");

    for (int i = 0; i < notes.length; i++) {
        int frequency;
        int duration = 2000;
        float volume = 1.0F;

        String[] param = notes[i].split("[\\.]", 3);

        if (param.length < 1) {
        throw new IOException("missing frequency " + treatment);
        }

        try {
        frequency = Integer.parseInt(param[0]);
        } catch (NumberFormatException e) {
        throw new IOException("invalid duration " + treatment);
        }

        if (param.length > 1) {
            try {
            duration = Integer.parseInt(param[1]);
            } catch (NumberFormatException e) {
            throw new IOException("invalid duration " + treatment);
            }
        }

        if (param.length > 2) {
        try {
            volume = Float.parseFloat(param[2]);
        } catch (NumberFormatException e) {
            throw new IOException("invalid volume " + treatment);
        }
        }

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("Sine treatment:  frequency "
            + frequency + " duration " + duration + " volume " + volume);
        }

        synchronized (treatments) {
            treatments.add(new SineWaveAudioSource(frequency, duration,
            volume, sampleRate, channels));
        }
    }
    }

    private void addFileTreatment(String treatment) throws IOException {
        /*
         * The treatment looks like this:
         *    [<commas>]<path>[<commas>]
         */
    //String s = treatment.replaceAll("[ \t]", "");
        String s = treatment;
    while (s.length() > 0) {
        if (s.charAt(0) == ',') {
        addSilence();
        s = s.substring(1);	// skip comma
        } else {
        String currentTreatment = s;

        int i = s.indexOf(",");	// find trailing commas

        if (i > 0) {
            currentTreatment = s.substring(0, i);
            addTreatment(currentTreatment);
            s = s.substring(i);
        } else {
            addTreatment(s);
            break;
        }
        }

    }

    }

    private void addSilence() throws IOException {
        addTreatment("silence.100ms.au");
    }

    public String toAbbreviatedString() {
    return treatment;
    }

}

/*
 * FreeTTSClient.java  (2001)
 *
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 *
 * Unpublished - rights reserved under the Copyright Laws of the United States.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this document. In
 * particular, and without limitation, these intellectual property rights may
 * include one or more of the U.S. patents listed at http://www.sun.com/patents
 * and one or more additional patents or pending patent applications in the
 * U.S. and in other countries.
 *
 * SUN PROPRIETARY/CONFIDENTIAL.
 *
 * U.S. Government Rights - Commercial software. Government users are subject
 * to the Sun Microsystems, Inc. standard license agreement and applicable
 * provisions of the FAR and its supplements.
 *
 * Use is subject to license terms.
 *
 * This distribution may include materials developed by third parties. Sun, Sun
 * Microsystems, the Sun logo, Java, Jini, Solaris and Sun Ray are trademarks
 * or registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively
 * licensed through X/Open Company, Ltd.
 */
package com.sun.voip;

import com.sun.voip.Logger;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;

import com.sun.speech.freetts.util.Utilities;

import com.sun.speech.freetts.audio.AudioPlayer;
import javax.sound.sampled.AudioFormat;

import java.io.IOException;

/**
 * Implements a Java Client for the Client/Server demo. For details about
 * the protocol between client and server, consult the file
 * <code>Protocol.txt</code>.
 */
public class FreeTTSClient {

    private static boolean initialized = false;

    private static int sampleRate =	Utilities.getInteger("sampleRate", 16000).intValue();

    private static Voice voice;
    private static String voice16kName = Utilities.getProperty("voice16kName", "kevin16");

    private FreeTTSClient() {
    }

    public static void initialize() throws IOException {
    if (initialized) {
        return;
    }

    initialized = true;

        VoiceManager voiceManager = VoiceManager.getInstance();
    voice = voiceManager.getVoice(voice16kName);
        voice.allocate();
    }

    /**
     * Run the TTS protocol.
     */
    public static int[] textToSpeech(String text) throws IOException {
    long startTime = System.currentTimeMillis();

    try {
        ServerAudioPlayer serverAudioPlayer = new ServerAudioPlayer();

        voice.setAudioPlayer(serverAudioPlayer);
        voice.speak(text);

        int[] linearData = serverAudioPlayer.getLinearData();

        if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println("TTS time for '" + text + "' "
            + (System.currentTimeMillis() - startTime));
        }

        return linearData;
    } catch (Exception ioe) {
        ioe.printStackTrace();
    }
    return null;
    }

}

/**
 * Implements the AudioPlayer for the freetts Client/Server demo.
 * This SocketAudioPlayer basically sends synthesized wave bytes to the
 * client.
 */
class ServerAudioPlayer implements AudioPlayer {

    private AudioFormat audioFormat;
    private boolean debug = false;
    private int bytesToPlay = 0;
    private int bytesPlayed = 0;

    private byte[] byteLinearData;

    public ServerAudioPlayer() {
    }

    /**
     * Sets the audio format to use for the next set of outputs. Since
     * an audio player can be shared by a number of voices, and since
     * voices can have different AudioFormats (sample rates for
     * example), it is necessary to allow clients to dynamically set
     * the audio format for the player.
     *
     * @param format the audio format
     */
    public void setAudioFormat(AudioFormat format) {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("setAudioFormat");
    }
    this.audioFormat = format;
    }

    /**
     * Retrieves the audio format for this player
     *
     * @return the current audio format
     *
     */
    public AudioFormat getAudioFormat() {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("getAudioFormat");
    }
    return this.audioFormat;
    }

    /**
     * Pauses all audio output on this player. Play can be resumed
     * with a call to resume. Not implemented in this Player.
     */
    public void pause() {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("pause");
    }
    }

    /**
     * Resumes audio output on this player. Not implemented in this Player.
     */
    public void resume() {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("resume");
    }
    }

    /**
     * Prepares for another batch of output. Larger groups of output
     * (such as all output associated with a single FreeTTSSpeakable)
     * should be grouped between a reset/drain pair.
     */
    public void reset() {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("reset");
    }
    }

    /**
     * Flushes all the audio data to the Socket.
     *
     * @return <code>true</code> all the time
     */
    public boolean drain() {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("drain");
    }
    return true;
    }

    /**
     *  Starts the output of a set of data. Audio data for a single
     *  utterance should be grouped between begin/end pairs.
     *
     * @param size the size of data in bytes to be output before
     *    <code>end</code> is called.
     */
    public void begin(int size) {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("begin size " + size);
    }
    }

    /**
     * Starts the first sample timer (none in this player)
     */
    public void startFirstSampleTimer() {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("startFirstSampleTimer");
    }
    }

    /**
     *  Signals the end of a set of data. Audio data for a single
     *  utterance should be groupd between <code> begin/end </code> pairs.
     *
     *  @return <code>true</code> if the audio was output properly,
     *          <code> false</code> if the output was cancelled
     *          or interrupted.
     *
     */
    public boolean end() {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("end bytesPlayed " + bytesPlayed);
    }

    if (bytesPlayed < bytesToPlay) {
        int bytesNotPlayed = bytesToPlay - bytesPlayed;
        write(new byte[bytesNotPlayed], 0, bytesNotPlayed);
    }

    bytesToPlay = 0;
    bytesPlayed = 0;
    return true;
    }

    /**
     * Cancels all queued output. All 'write' calls until the next
     * reset will return false. Not implemented in this Player.
     *
     */
    public void cancel() {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("cancel");
    }
    }

    /**
     * Waits for all audio playback to stop, and closes this AudioPlayer.
     * Not implemented in this Player.
     */
    public void close() {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("close");
    }
    }

    /**
     * Returns the current volume. The volume is specified as a number
     * between 0.0 and 1.0, where 1.0 is the maximum volume and 0.0 is
     * the minimum volume. Not implemented in this Player.
     *
     * @return the current volume (between 0 and 1)
     */
    public float getVolume() {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("getVolume");
    }
    return -1;
    }

    /**
     * Sets the current volume. The volume is specified as a number
     * between 0.0 and 1.0, where 1.0 is the maximum volume and 0.0 is
     * the minimum volume. Not implemented in this Player.
     *
     * @param volume the new volume (between 0 and 1)
     */
    public void setVolume(float volume) {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("setVolume");
    }
    }

    /**
     * Gets the amount of audio played since the last resetTime.
     * Not implemented in this Player.
     *
     * @returns the amount of audio in milliseconds
     */
    public long getTime() {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("getTime");
    }
    return -1;
    }

    /**
     * Resets the audio clock. Not implemented in this Player.
     */
    public void resetTime() {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("reset");
    }
    }

    /**
     * Shows metrics for this audio player. Not implemented in this Player.
     */
    public void showMetrics() {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("showMetrics");
    }
    }

    /**
     * Writes the given bytes to the audio stream
     *
     * @param audioData audio data to write to the device
     *
     * @return <code>true</code> of the write completed successfully,
     *          <code> false </code>if the write was cancelled.
     */
    public boolean write(byte[] audioData) {
    return write(audioData, 0, audioData.length);
    }


    /**
     * Writes the given bytes to the byte linear data buffer
     *
     * @param audioData audio data to write to the device
     * @param offset the offset into the buffer
     * @param size the number of bytes to write.
     *
     * @return <code>true</code> of the write completed successfully,
     *          <code> false </code>if the write was cancelled.
     */
    public boolean write(byte[] audioData, int offset, int size) {
    /*
     * get a new buffer which will hold all of the data
     */
        byte[] b = new byte[bytesPlayed + audioData.length];

        for (int i = 0; i < bytesPlayed; i++) {
            b[i] = byteLinearData[i];	// copy old data
    }

    for (int i = 0; i < size; i++) {
        b[i + bytesPlayed] = audioData[i + offset];  // new data
    }

        byteLinearData = b;

    bytesPlayed += size;

    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("bytesPlayed " + bytesPlayed);
    }
    return true;
    }

    /**
     * Return the data in an int array.
     * @return data int[] voice data
     */
    public int[] getLinearData() {
    return AudioConversion.bytesToInts(byteLinearData);
    }

}

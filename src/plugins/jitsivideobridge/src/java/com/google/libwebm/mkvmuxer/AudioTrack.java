// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

public class AudioTrack extends Track {

  public AudioTrack(int seed) {
    nativePointer = newAudioTrack(seed);
  }

  public long bitDepth() {
    return bitDepth(nativePointer);
  }

  public long channels() {
    return channels(nativePointer);
  }

  @Override
  public long payloadSize() {
    return PayloadSize(nativePointer);
  }

  public double sampleRate() {
    return sampleRate(nativePointer);
  }

  public void setBitDepth(long bitDepth) {
    setBitDepth(nativePointer, bitDepth);
  }

  public void setChannels(long channels) {
    setChannels(nativePointer, channels);
  }

  public void setSampleRate(double sampleRate) {
    setSampleRate(nativePointer, sampleRate);
  }

  @Override
  public boolean write(IMkvWriter writer) {
    return Write(nativePointer, writer.getNativePointer());
  }

  protected AudioTrack(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteAudioTrack(nativePointer);
  }

  private static native long bitDepth(long jAudioTrack);
  private static native long channels(long jAudioTrack);
  private static native void deleteAudioTrack(long jAudioTrack);
  private static native long newAudioTrack(int jSeed);
  private static native long PayloadSize(long jAudioTrack);
  private static native double sampleRate(long jAudioTrack);
  private static native void setBitDepth(long jAudioTrack, long bit_depth);
  private static native void setChannels(long jAudioTrack, long channels);
  private static native void setSampleRate(long jAudioTrack, double sample_rate);
  private static native boolean Write(long jAudioTrack, long jWriter);
}

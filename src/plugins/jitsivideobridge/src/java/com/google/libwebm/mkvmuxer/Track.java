// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

import com.google.libwebm.Common;

public class Track extends Common {

  public static Track newTrack(long nativePointer) {
    Track track = null;
    int type = getClassType(nativePointer);
    if (type == 1) {
      track = new AudioTrack(nativePointer);
    } else if (type == 2) {
      track = new Track(nativePointer);
    } else if (type == 3) {
      track = new VideoTrack(nativePointer);
    }
    return track;
  }

  public Track(int seed) {
    nativePointer = newTrack(seed);
  }

  public boolean addContentEncoding() {
    return AddContentEncoding(nativePointer);
  }

  public String codecId() {
    return codecId(nativePointer);
  }

  public byte[] codecPrivate() {
    return codecPrivate(nativePointer);
  }

  public long codecPrivateLength() {
    return codecPrivateLength(nativePointer);
  }

  public int contentEncodingEntriesSize() {
    return contentEncodingEntriesSize(nativePointer);
  }

  public ContentEncoding getContentEncodingByIndex(int index) {
    long pointer = GetContentEncodingByIndex(nativePointer, index);
    return new ContentEncoding(pointer);
  }

  public String language() {
    return language(nativePointer);
  }

  public String name() {
    return name(nativePointer);
  }

  public long number() {
    return number(nativePointer);
  }

  public long payloadSize() {
    return PayloadSize(nativePointer);
  }

  public void setCodecId(String codecId) {
    setCodecId(nativePointer, codecId);
  }

  public boolean setCodecPrivate(byte[] codecPrivate) {
    return SetCodecPrivate(nativePointer, codecPrivate, codecPrivate.length);
  }

  public void setLanguage(String language) {
    setLanguage(nativePointer, language);
  }

  public void setName(String name) {
    setName(nativePointer, name);
  }

  public void setNumber(long number) {
    setNumber(nativePointer, number);
  }

  public void setType(long type) {
    setType(nativePointer, type);
  }

  public long size() {
    return Size(nativePointer);
  }

  public long type() {
    return type(nativePointer);
  }

  public long uid() {
    return uid(nativePointer);
  }

  public boolean write(IMkvWriter writer) {
    return Write(nativePointer, writer.getNativePointer());
  }

  protected Track() {
  }

  protected Track(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteTrack(nativePointer);
  }

  private static native boolean AddContentEncoding(long jTrack);
  private static native int contentEncodingEntriesSize(long jTrack);
  private static native String codecId(long jTrack);
  private static native byte[] codecPrivate(long jTrack);
  private static native long codecPrivateLength(long jTrack);
  private static native void deleteTrack(long jTrack);
  private static native int getClassType(long jTrack);
  private static native long GetContentEncodingByIndex(long jTrack, int index);
  private static native String language(long jTrack);
  private static native String name(long jTrack);
  private static native long newTrack(int jSeed);
  private static native long number(long jTrack);
  private static native long PayloadSize(long jTrack);
  private static native void setCodecId(long jTrack, String jCodecId);
  private static native void setLanguage(long jTrack, String jLanguage);
  private static native void setName(long jTrack, String jName);
  private static native void setNumber(long jTrack, long number);
  private static native void setType(long jTrack, long type);
  private static native boolean SetCodecPrivate(long jTrack, byte[] jCodecPrivate, long length);
  private static native long Size(long jTrack);
  private static native long type(long jTrack);
  private static native long uid(long jTrack);
  private static native boolean Write(long jTrack, long jWriter);
}

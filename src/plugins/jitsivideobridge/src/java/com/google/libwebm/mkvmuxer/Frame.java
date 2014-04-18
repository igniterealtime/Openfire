// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

import com.google.libwebm.Common;

public class Frame extends Common {

  public Frame() {
    nativePointer = newFrame();
  }

  public byte[] frame() {
    return frame(nativePointer);
  }

  public boolean init(byte[] frame) {
    return Init(nativePointer, frame, frame.length);
  }

  public boolean isKey() {
    return isKey(nativePointer);
  }

  public long length() {
    return length(nativePointer);
  }

  public void setIsKey(boolean key) {
    setIsKey(nativePointer, key);
  }

  public void setTimestamp(long timestamp) {
    setTimestamp(nativePointer, timestamp);
  }

  public void setTrackNumber(long trackNumber) {
    setTrackNumber(nativePointer, trackNumber);
  }

  public long timestamp() {
    return timestamp(nativePointer);
  }

  public long trackNumber() {
    return trackNumber(nativePointer);
  }

  protected Frame(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteFrame(nativePointer);
  }

  private static native void deleteFrame(long jFrame);
  private static native byte[] frame(long jFrame);
  private static native boolean Init(long jFrame, byte[] jFrameBuffer, long length);
  private static native boolean isKey(long jFrame);
  private static native long length(long jFrame);
  private static native long newFrame();
  private static native void setIsKey(long jFrame, boolean key);
  private static native void setTimestamp(long jFrame, long timestamp);
  private static native void setTrackNumber(long jFrame, long track_number);
  private static native long timestamp(long jFrame);
  private static native long trackNumber(long jFrame);
}

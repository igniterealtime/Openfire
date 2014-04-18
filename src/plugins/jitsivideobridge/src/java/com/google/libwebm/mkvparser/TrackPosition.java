// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class TrackPosition extends Common {

  public TrackPosition() {
    nativePointer = newTrackPosition();
  }

  public long getBlock() {
    return getBlock(nativePointer);
  }

  public long getPos() {
    return getPos(nativePointer);
  }

  public long getTrack() {
    return getTrack(nativePointer);
  }

  public void parse(IMkvReader mkvReader, long start, long size) {
    Parse(nativePointer, mkvReader.getNativePointer(), start, size);
  }

  public void setBlock(long block) {
    setBlock(nativePointer, block);
  }

  public void setPos(long pos) {
    setPos(nativePointer, pos);
  }

  public void setTrack(long track) {
    setTrack(nativePointer, track);
  }

  protected TrackPosition(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteTrackPosition(nativePointer);
  }

  private static native void deleteTrackPosition(long jTrackPosition);
  private static native long getBlock(long jTrackPosition);
  private static native long getPos(long jTrackPosition);
  private static native long getTrack(long jTrackPosition);
  private static native long newTrackPosition();
  private static native void Parse(long jTrackPosition, long jMkvReader, long start, long size);
  private static native void setBlock(long jTrackPosition, long block);
  private static native void setPos(long jTrackPosition, long pos);
  private static native void setTrack(long jTrackPosition, long track);
}

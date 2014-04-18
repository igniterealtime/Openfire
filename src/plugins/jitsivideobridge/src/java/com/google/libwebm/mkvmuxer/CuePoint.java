// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

import com.google.libwebm.Common;

public class CuePoint extends Common {

  public CuePoint() {
    nativePointer = newCuePoint();
  }

  public long blockNumber() {
    return blockNumber(nativePointer);
  }

  public long clusterPos() {
    return clusterPos(nativePointer);
  }

  public boolean outputBlockNumber() {
    return outputBlockNumber(nativePointer);
  }

  public void setBlockNumber(long blockNumber) {
    setBlockNumber(nativePointer, blockNumber);
  }

  public void setClusterPos(long clusterPos) {
    setClusterPos(nativePointer, clusterPos);
  }

  public void setOutputBlockNumber(boolean outputBlockNumber) {
    setOutputBlockNumber(nativePointer, outputBlockNumber);
  }

  public void setTime(long time) {
    setTime(nativePointer, time);
  }

  public void setTrack(long track) {
    setTrack(nativePointer, track);
  }

  public long size() {
    return Size(nativePointer);
  }

  public long time() {
    return time(nativePointer);
  }

  public long track() {
    return track(nativePointer);
  }

  public boolean write(IMkvWriter writer) {
    return Write(nativePointer, writer.getNativePointer());
  }

  protected CuePoint(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteCuePoint(nativePointer);
  }

  private static native long blockNumber(long jCuePoint);
  private static native long clusterPos(long jCuePoint);
  private static native void deleteCuePoint(long jCuePoint);
  private static native long newCuePoint();
  private static native boolean outputBlockNumber(long jCuePoint);
  private static native void setBlockNumber(long jCuePoint, long block_number);
  private static native void setClusterPos(long jCuePoint, long cluster_pos);
  private static native void setOutputBlockNumber(long jCuePoint, boolean output_block_number);
  private static native void setTime(long jCuePoint, long time);
  private static native void setTrack(long jCuePoint, long track);
  private static native long Size(long jCuePoint);
  private static native long time(long jCuePoint);
  private static native long track(long jCuePoint);
  private static native boolean Write(long jCuePoint, long jWriter);
}

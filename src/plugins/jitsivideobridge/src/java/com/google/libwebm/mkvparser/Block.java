// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class Block extends Common {

  public enum Lacing {
    kLacingNone,
    kLacingXiph,
    kLacingFixed,
    kLacingEbml
  };

  public Block() {
    nativePointer = newBlock();
  }

  public Frame getFrame(int frameIndex) {
    long pointer = GetFrame(nativePointer, frameIndex);
    return new Frame(pointer);
  }

  public int getFrameCount() {
    return GetFrameCount(nativePointer);
  }

  public Lacing getLacing() {
    int ordinal = GetLacing(nativePointer);
    return Lacing.values()[ordinal];
  }

  public long getSize() {
    return getSize(nativePointer);
  }

  public long getStart() {
    return getStart(nativePointer);
  }

  public long getTime(Cluster cluster) {
    return GetTime(nativePointer, cluster.getNativePointer());
  }

  public long getTimeCode(Cluster cluster) {
    return GetTimeCode(nativePointer, cluster.getNativePointer());
  }

  public long getTrackNumber() {
    return GetTrackNumber(nativePointer);
  }

  public boolean isInvisible() {
    return IsInvisible(nativePointer);
  }

  public boolean isKey() {
    return IsKey(nativePointer);
  }

  public long parse(Cluster cluster) {
    return Parse(nativePointer, cluster.getNativePointer());
  }

  public void setKey(boolean key) {
    SetKey(nativePointer, key);
  }

  protected Block(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteBlock(nativePointer);
  }

  private static native void deleteBlock(long jBlock);
  private static native long GetFrame(long jBlock, int frameIndex);
  private static native int GetFrameCount(long jBlock);
  private static native int GetLacing(long jBlock);
  private static native long getSize(long jBlock);
  private static native long getStart(long jBlock);
  private static native long GetTime(long jBlock, long jCluster);
  private static native long GetTimeCode(long jBlock, long jCluster);
  private static native long GetTrackNumber(long jBlock);
  private static native boolean IsInvisible(long jBlock);
  private static native boolean IsKey(long jBlock);
  private static native long newBlock();
  private static native long Parse(long jBlock, long jCluster);
  private static native void SetKey(long jBlock, boolean key);
}

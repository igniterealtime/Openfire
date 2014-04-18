// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

import com.google.libwebm.Common;

public class Cluster extends Common {

  public Cluster(long timecode, long cuesPos) {
    nativePointer = newCluster(timecode, cuesPos);
  }

  public boolean addFrame(byte[] frame, long trackNumber, long timecode, boolean isKey) {
    return AddFrame(nativePointer, frame, frame.length, trackNumber, timecode, isKey);
  }

  public boolean addMetadata(byte[] frame, long trackNumber, long timecode, long duration) {
    return AddMetadata(nativePointer, frame, frame.length, trackNumber, timecode, duration);
  }

  public void addPayloadSize(long size) {
    AddPayloadSize(nativePointer, size);
  }

  public int blocksAdded() {
    return blocksAdded(nativePointer);
  }

  public boolean finalizeCluster() {
    return Finalize(nativePointer);
  }

  public boolean init(IMkvWriter writer) {
    return Init(nativePointer, writer.getNativePointer());
  }

  public long payloadSize() {
    return payloadSize(nativePointer);
  }

  public long positionForCues() {
    return positionForCues(nativePointer);
  }

  public long size() {
    return Size(nativePointer);
  }

  public long timecode() {
    return timecode(nativePointer);
  }

  protected Cluster(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteCluster(nativePointer);
  }

  private static native boolean AddFrame(long jCluster, byte[] jFrame, long length,
      long track_number, long timecode, boolean is_key);
  private static native boolean AddMetadata(long jCluster, byte[] jFrame, long length,
      long track_number, long timecode, long duration);
  private static native void AddPayloadSize(long jCluster, long size);
  private static native int blocksAdded(long jCluster);
  private static native void deleteCluster(long jCluster);
  private static native boolean Finalize(long jCluster);
  private static native boolean Init(long jCluster, long jWriter);
  private static native long newCluster(long timecode, long cues_pos);
  private static native long payloadSize(long jCluster);
  private static native long positionForCues(long jCluster);
  private static native long Size(long jCluster);
  private static native long timecode(long jCluster);
}

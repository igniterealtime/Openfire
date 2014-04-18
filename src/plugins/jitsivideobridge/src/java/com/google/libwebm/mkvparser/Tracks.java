// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class Tracks extends Common {

  public Tracks(Segment segment, long start, long size, long element_start, long element_size) {
    nativePointer = newTracks(segment.getNativePointer(), start, size, element_start, element_size);
  }

  public long getElementSize() {
    return getElementSize(nativePointer);
  }

  public long getElementStart() {
    return getElementStart(nativePointer);
  }

  public Segment getSegment() {
    long pointer = getSegment(nativePointer);
    return new Segment(pointer);
  }

  public long getSize() {
    return getSize(nativePointer);
  }

  public long getStart() {
    return getStart(nativePointer);
  }

  public Track getTrackByIndex(long idx) {
    long pointer = GetTrackByIndex(nativePointer, idx);
    return Track.newTrack(pointer);
  }

  public Track getTrackByNumber(long tn) {
    long pointer = GetTrackByNumber(nativePointer, tn);
    return Track.newTrack(pointer);
  }

  public long getTracksCount() {
    return GetTracksCount(nativePointer);
  }

  public long Parse() {
    return Parse(nativePointer);
  }

  protected Tracks(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteTracks(nativePointer);
  }

  private static native void deleteTracks(long jTracks);
  private static native long getElementSize(long jTracks);
  private static native long getElementStart(long jTracks);
  private static native long getSegment(long jTracks);
  private static native long getSize(long jTracks);
  private static native long getStart(long jTracks);
  private static native long GetTrackByIndex(long jTracks, long idx);
  private static native long GetTrackByNumber(long jTracks, long tn);
  private static native long GetTracksCount(long jTracks);
  private static native long newTracks(long jSegment, long start, long size, long element_start,
      long element_size);
  private static native long Parse(long jTracks);
}

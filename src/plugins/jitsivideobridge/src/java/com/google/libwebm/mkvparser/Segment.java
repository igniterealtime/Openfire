// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class Segment extends Common {

  public static long createInstance(IMkvReader mkvReader, long position, Segment[] segment) {
    long[] jSegment = {0};
    long result = CreateInstance(mkvReader.getNativePointer(), position, jSegment);
    segment[0] = new Segment(jSegment[0]);
    return result;
  }

  public boolean doneParsing() {
    return DoneParsing(nativePointer);
  }

  public Cluster findCluster(long timeNanoseconds) {
    long pointer = FindCluster(nativePointer, timeNanoseconds);
    return new Cluster(pointer);
  }

  public Cluster findOrPreloadCluster(long position) {
    long pointer = FindOrPreloadCluster(nativePointer, position);
    return new Cluster(pointer);
  }

  public Chapters getChapters() {
    long pointer = GetChapters(nativePointer);
    return new Chapters(pointer);
  }

  public long getCount() {
    return GetCount(nativePointer);
  }

  public Cues getCues() {
    long pointer = GetCues(nativePointer);
    return new Cues(pointer);
  }

  public long getDuration() {
    return GetDuration(nativePointer);
  }

  public long getElementStart() {
    return getElementStart(nativePointer);
  }

  public Cluster getEos() {
    long pointer = getEos(nativePointer);
    return new Cluster(pointer);
  }

  public Cluster getFirst() {
    long pointer = GetFirst(nativePointer);
    return new Cluster(pointer);
  }

  public SegmentInfo getInfo() {
    long pointer = GetInfo(nativePointer);
    return new SegmentInfo(pointer);
  }

  public Cluster getLast() {
    long pointer = GetLast(nativePointer);
    return new Cluster(pointer);
  }

  public Cluster getNext(Cluster current) {
    long pointer = GetNext(nativePointer, current.getNativePointer());
    return new Cluster(pointer);
  }

  public IMkvReader getReader() {
    long pointer = getReader(nativePointer);
    return IMkvReader.newMkvReader(pointer);
  }

  public SeekHead getSeekHead() {
    long pointer = GetSeekHead(nativePointer);
    return new SeekHead(pointer);
  }

  public long getSize() {
    return getSize(nativePointer);
  }

  public long getStart() {
    return getStart(nativePointer);
  }

  public Tracks getTracks() {
    long pointer = GetTracks(nativePointer);
    return new Tracks(pointer);
  }

  public long load() {
    return Load(nativePointer);
  }

  public long loadCluster() {
    return LoadClusterWithoutPosition(nativePointer);
  }

  public long loadCluster(long[] position, long[] size) {
    return LoadClusterAndPosition(nativePointer, position, size);
  }

  public long parseCues(long cuesOffset, long[] position, long[] length) {
    return ParseCues(nativePointer, cuesOffset, position, length);
  }

  public long parseHeaders() {
    return ParseHeaders(nativePointer);
  }

  public long parseNext(Cluster current, Cluster[] next, long[] position, long[] size) {
    long[] jNext = {0};
    long result = ParseNext(nativePointer, current.getNativePointer(), jNext, position, size);
    next[0] = new Cluster(jNext[0]);
    return result;
  }

  public void setEos(Cluster eos) {
    setEos(nativePointer, eos.getNativePointer());
  }

  protected Segment(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteSegment(nativePointer);
  }

  private static native long CreateInstance(long jMkvReader, long position, long[] jSegment);
  private static native void deleteSegment(long jSegment);
  private static native boolean DoneParsing(long jSegment);
  private static native long FindCluster(long jSegment, long time_nanoseconds);
  private static native long FindOrPreloadCluster(long jSegment, long position);
  private static native long GetChapters(long jSegment);
  private static native long GetCount(long jSegment);
  private static native long GetCues(long jSegment);
  private static native long GetDuration(long jSegment);
  private static native long getElementStart(long jSegment);
  private static native long getEos(long jSegment);
  private static native long GetFirst(long jSegment);
  private static native long GetInfo(long jSegment);
  private static native long GetLast(long jSegment);
  private static native long GetNext(long jSegment, long jCurrent);
  private static native long getReader(long jSegment);
  private static native long GetSeekHead(long jSegment);
  private static native long getSize(long jSegment);
  private static native long getStart(long jSegment);
  private static native long GetTracks(long jSegment);
  private static native long Load(long jSegment);
  private static native long LoadClusterAndPosition(long jSegment, long[] jPosition, long[] jSize);
  private static native long LoadClusterWithoutPosition(long jSegment);
  private static native long ParseCues(long jSegment, long cues_off, long[] jPosition,
      long[] jLength);
  private static native long ParseHeaders(long jSegment);
  private static native long ParseNext(long jSegment, long jCurrent, long[] jNext, long[] jPosition,
      long[] jSize);
  private static native void setEos(long jSegment, long jEos);
}

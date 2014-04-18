// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class Cluster extends Common {

  public static Cluster create(Segment segment, long index, long offset) {
    long pointer = Create(segment.getNativePointer(), index, offset);
    return new Cluster(pointer);
  }

  public static long hasBlockEntries(Segment segment, long offset, long[] position, long[] size) {
    return HasBlockEntries(segment.getNativePointer(), offset, position, size);
  }

  public Cluster() {
    nativePointer = newCluster();
  }

  public boolean eos() {
    return EOS(nativePointer);
  }

  public long getElementSize() {
    return GetElementSize(nativePointer);
  }

  public long getElementStart() {
    return getElementStart(nativePointer);
  }

  public long getEntryCount() {
    return GetEntryCount(nativePointer);
  }

  public long getEntry(long index, BlockEntry[] blockEntry) {
    long[] jBlockEntry = {0};
    long result = GetEntryIndex(nativePointer, index, jBlockEntry);
    blockEntry[0] = BlockEntry.newBlockEntry(jBlockEntry[0]);
    return result;
  }

  public BlockEntry getEntry(CuePoint cuePoint, TrackPosition trackPosition) {
    long pointer = GetEntryCuePoint(nativePointer, cuePoint.getNativePointer(),
        trackPosition.getNativePointer());
    return BlockEntry.newBlockEntry(pointer);
  }

  public BlockEntry getEntry(Track track, long ns) {
    long pointer = GetEntryTrack(nativePointer, track.getNativePointer(), ns);
    return BlockEntry.newBlockEntry(pointer);
  }

  public long getFirst(BlockEntry[] blockEntry) {
    long[] jBlockEntry = {0};
    long result = GetFirst(nativePointer, jBlockEntry);
    blockEntry[0] = BlockEntry.newBlockEntry(jBlockEntry[0]);
    return result;
  }

  public long getFirstTime() {
    return GetFirstTime(nativePointer);
  }

  public long getLast(BlockEntry[] blockEntry) {
    long[] jBlockEntry = {0};
    long result = GetLast(nativePointer, jBlockEntry);
    blockEntry[0] = BlockEntry.newBlockEntry(jBlockEntry[0]);
    return result;
  }

  public long getLastTime() {
    return GetLastTime(nativePointer);
  }

  public long getNext(BlockEntry current, BlockEntry[] next) {
    long[] jNext = {0};
    long result = GetNext(nativePointer, current.getNativePointer(), jNext);
    next[0] = BlockEntry.newBlockEntry(jNext[0]);
    return result;
  }

  public long getPosition() {
    return GetPosition(nativePointer);
  }

  public long getSegment() {
    return getSegment(nativePointer);
  }

  public long getTime() {
    return GetTime(nativePointer);
  }

  public long getTimeCode() {
    return GetTimeCode(nativePointer);
  }

  public long load(long[] position, long[] size) {
    return Load(nativePointer, position, size);
  }

  public long parse(long[] position, long[] size) {
    return Parse(nativePointer, position, size);
  }

  protected Cluster(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteCluster(nativePointer);
  }

  private static native long Create(long jSegment, long index, long offset);
  private static native void deleteCluster(long jCluster);
  private static native boolean EOS(long jCluster);
  private static native long GetElementSize(long jCluster);
  private static native long getElementStart(long jCluster);
  private static native long GetEntryCount(long jCluster);
  private static native long GetEntryCuePoint(long jCluster, long jCuePoint, long jTrackPosition);
  private static native long GetEntryIndex(long jCluster, long index, long[] jBlockEntry);
  private static native long GetEntryTrack(long jCluster, long jTrack, long ns);
  private static native long GetFirst(long jCluster, long[] jBlockEntry);
  private static native long GetFirstTime(long jCluster);
  private static native long GetIndex(long jCluster);
  private static native long GetLast(long jCluster, long[] jBlockEntry);
  private static native long GetLastTime(long jCluster);
  private static native long GetNext(long jCluster, long jCurrent, long[] jNext);
  private static native long GetPosition(long jCluster);
  private static native long getSegment(long jCluster);
  private static native long GetTime(long jCluster);
  private static native long GetTimeCode(long jCluster);
  private static native long HasBlockEntries(long jSegment, long offset, long[] jPosition,
      long[] jSize);
  private static native long Load(long jCluster, long[] jPosition, long[] jSize);
  private static native long newCluster();
  private static native long Parse(long jCluster, long[] jPosition, long[] jSize);
}

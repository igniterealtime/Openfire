// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class Track extends Common {

  public enum Type {
    None,
    kVideo,
    kAudio
  };

  public static long create(Segment segment, Info info, long element_start, long element_size,
      Track[] track) {
    long[] jTrack = {0};
    long result = Create(segment.getNativePointer(), info.getNativePointer(), element_start,
        element_size, jTrack);
    track[0] = Track.newTrack(jTrack[0]);
    return result;
  }

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

  public String getCodecId() {
    return GetCodecId(nativePointer);
  }

  public String getCodecNameAsUtf8() {
    return GetCodecNameAsUTF8(nativePointer);
  }

  public byte[] getCodecPrivate(long[] size) {
    return GetCodecPrivate(nativePointer, size);
  }

  public ContentEncoding getContentEncodingByIndex(long idx) {
    long pointer = GetContentEncodingByIndex(nativePointer, idx);
    return new ContentEncoding(pointer);
  }

  public long getContentEncodingCount() {
    return GetContentEncodingCount(nativePointer);
  }

  public long getElementSize() {
    return getElementSize(nativePointer);
  }

  public long getElementStart() {
    return getElementStart(nativePointer);
  }

  public BlockEntry getEos() {
    long pointer = GetEOS(nativePointer);
    return BlockEntry.newBlockEntry(pointer);
  }

  public long getFirst(BlockEntry[] blockEntry) {
    long[] jBlockEntry = {0};
    long result = GetFirst(nativePointer, jBlockEntry);
    blockEntry[0] = BlockEntry.newBlockEntry(jBlockEntry[0]);
    return result;
  }

  public boolean getLacing() {
    return GetLacing(nativePointer);
  }

  public String getNameAsUtf8() {
    return GetNameAsUTF8(nativePointer);
  }

  public long getNext(BlockEntry current, BlockEntry[] next) {
    long[] jNext = {0};
    long result = GetNext(nativePointer, current.getNativePointer(), jNext);
    next[0] = BlockEntry.newBlockEntry(jNext[0]);
    return result;
  }

  public long getNumber() {
    return GetNumber(nativePointer);
  }

  public Segment getSegment() {
    long pointer = getSegment(nativePointer);
    return new Segment(pointer);
  }

  public Type getType() {
    int ordinal = (int) GetType(nativePointer);
    return Type.values()[ordinal];
  }

  public long getUid() {
    return GetUid(nativePointer);
  }

  public long parseContentEncodingsEntry(long start, long size) {
    return ParseContentEncodingsEntry(nativePointer, start, size);
  }

  public long seek(long time_ns, BlockEntry[] result) {
    long[] jResult = {0};
    long output = Seek(nativePointer, time_ns, jResult);
    result[0] = BlockEntry.newBlockEntry(jResult[0]);
    return output;
  }

  public boolean vetEntry(BlockEntry blockEntry) {
    return VetEntry(nativePointer, blockEntry.getNativePointer());
  }

  protected Track(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteTrack(nativePointer);
  }

  private static native long Create(long jSegment, long jInfo, long element_start,
      long element_size, long[] jTrack);
  private static native void deleteTrack(long jTrack);
  private static native int getClassType(long jTrack);
  private static native String GetCodecId(long jTrack);
  private static native String GetCodecNameAsUTF8(long jTrack);
  private static native byte[] GetCodecPrivate(long jTrack, long[] jSize);
  private static native long GetContentEncodingByIndex(long jTrack, long idx);
  private static native long GetContentEncodingCount(long jTrack);
  private static native long getElementSize(long jTrack);
  private static native long getElementStart(long jTrack);
  private static native long GetEOS(long jTrack);
  private static native long GetFirst(long jTrack, long[] jBlockEntry);
  private static native boolean GetLacing(long jTrack);
  private static native String GetNameAsUTF8(long jTrack);
  private static native long GetNext(long jTrack, long jCurrent, long[] jNext);
  private static native long GetNumber(long jTrack);
  private static native long getSegment(long jTrack);
  private static native long GetType(long jTrack);
  private static native long GetUid(long jTrack);
  private static native long ParseContentEncodingsEntry(long jTrack, long start, long size);
  private static native long Seek(long jTrack, long time_ns, long[] jResult);
  private static native boolean VetEntry(long jTrack, long jBlockEntry);
}

// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class Chapters extends Common {

  public Chapters(Segment segment, long payloadStart, long payloadSize, long elementStart,
      long elementSize) {
    nativePointer = newChapters(segment.getNativePointer(), payloadStart, payloadSize, elementStart,
        elementSize);
  }

  public Edition getEdition(int index) {
    long pointer = GetEdition(nativePointer, index);
    return new Edition(pointer);
  }

  public int getEditionCount() {
    return GetEditionCount(nativePointer);
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

  public long parse() {
    return Parse(nativePointer);
  }

  protected Chapters(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteChapters(nativePointer);
  }

  private static native void deleteChapters(long jChapters);
  private static native long GetEdition(long jChapters, int index);
  private static native int GetEditionCount(long jChapters);
  private static native long getElementSize(long jChapters);
  private static native long getElementStart(long jChapters);
  private static native long getSegment(long jChapters);
  private static native long getSize(long jChapters);
  private static native long getStart(long jChapters);
  private static native long newChapters(long jSegment, long payload_start, long payload_size,
      long element_start, long element_size);
  private static native long Parse(long jChapters);
}

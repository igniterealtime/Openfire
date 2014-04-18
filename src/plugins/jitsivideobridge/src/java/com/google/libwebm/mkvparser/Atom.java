// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;
import com.google.libwebm.mkvmuxer.Chapters;

public class Atom extends Common {

  public Display getDisplay(int index) {
    long pointer = GetDisplay(nativePointer, index);
    return new Display(pointer);
  }

  public int getDisplayCount() {
    return GetDisplayCount(nativePointer);
  }

  public long getStartTime(Chapters chapters) {
    return GetStartTime(nativePointer, chapters.getNativePointer());
  }

  public long getStartTimecode() {
    return GetStartTimecode(nativePointer);
  }

  public long getStopTime(Chapters chapters) {
    return GetStopTime(nativePointer, chapters.getNativePointer());
  }

  public long getStopTimecode() {
    return GetStopTimecode(nativePointer);
  }

  public String getStringUid() {
    return GetStringUID(nativePointer);
  }

  public long getUid() {
    return GetUID(nativePointer);
  }

  protected Atom(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
  }

  private static native long GetDisplay(long jAtom, int index);
  private static native int GetDisplayCount(long jAtom);
  private static native long GetStartTime(long jAtom, long jChapters);
  private static native long GetStartTimecode(long jAtom);
  private static native long GetStopTime(long jAtom, long jChapters);
  private static native long GetStopTimecode(long jAtom);
  private static native String GetStringUID(long jAtom);
  private static native long GetUID(long jAtom);
}

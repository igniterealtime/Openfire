// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

import com.google.libwebm.Common;

public class Chapter extends Common {

  public boolean addString(String title, String language, String country) {
    return addString(nativePointer, title, language, country);
  }

  public boolean setId(String id) {
    return setId(nativePointer, id);
  }

  public void setTime(Segment segment, long startTimeNs, long endTimeNs) {
    setTime(nativePointer, segment.getNativePointer(), startTimeNs, endTimeNs);
  }

  protected Chapter(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
  }

  private static native boolean addString(long jChapter, String jTitle, String jLanguage,
      String jCountry);
  private static native boolean setId(long jChapter, String jId);
  private static native void setTime(long jChapter, long jSegment, long start_time_ns,
      long end_time_ns);
}

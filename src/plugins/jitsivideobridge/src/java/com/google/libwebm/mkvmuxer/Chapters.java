// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

import com.google.libwebm.Common;

public class Chapters extends Common {

  public Chapters() {
    nativePointer = newChapters();
  }

  public Chapter addChapter(int seed) {
    long pointer = AddChapter(nativePointer, seed);
    return new Chapter(pointer);
  }

  public int count() {
    return Count(nativePointer);
  }

  public boolean write(IMkvWriter writer) {
    return Write(nativePointer, writer.getNativePointer());
  }

  protected Chapters(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteChapters(nativePointer);
  }

  private static native long AddChapter(long jChapters, int jSeed);
  private static native int Count(long jChapters);
  private static native void deleteChapters(long jChapters);
  private static native long newChapters();
  private static native boolean Write(long jChapters, long jWriter);
}

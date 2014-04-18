// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

import com.google.libwebm.Common;

public class SeekHead extends Common {

  public SeekHead() {
    nativePointer = newSeekHead();
  }

  public boolean addSeekEntry(int id, long pos) {
    return AddSeekEntry(nativePointer, id, pos);
  }

  public boolean finalizeSeekHead(IMkvWriter writer) {
    return Finalize(nativePointer, writer.getNativePointer());
  }

  public boolean write(IMkvWriter writer) {
    return Write(nativePointer, writer.getNativePointer());
  }

  protected SeekHead(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteSeekHead(nativePointer);
  }

  private static native boolean AddSeekEntry(long jSeekHead, int id, long pos);
  private static native void deleteSeekHead(long jSeekHead);
  private static native boolean Finalize(long jSeekHead, long jWriter);
  private static native long newSeekHead();
  private static native boolean Write(long jSeekHead, long jWriter);
}

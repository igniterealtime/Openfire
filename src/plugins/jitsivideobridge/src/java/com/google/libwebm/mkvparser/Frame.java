// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class Frame extends Common {

  public Frame() {
    nativePointer = newFrame();
  }

  public long getLen() {
    return getLen(nativePointer);
  }

  public long getPos() {
    return getPos(nativePointer);
  }

  public long read(IMkvReader mkvReader, byte[][] buffer) {
    return Read(nativePointer, mkvReader.getNativePointer(), buffer);
  }

  public void setLen(long len) {
    setLen(nativePointer, len);
  }

  public void setPos(long pos) {
    setPos(nativePointer, pos);
  }

  protected Frame(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteFrame(nativePointer);
  }

  private static native void deleteFrame(long jFrame);
  private static native long getLen(long jFrame);
  private static native long getPos(long jFrame);
  private static native long newFrame();
  private static native long Read(long jFrame, long jMkvReader, byte[][] jBuffer);
  private static native void setLen(long jFrame, long len);
  private static native void setPos(long jFrame, long pos);
}

// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

public class MkvWriter extends IMkvWriter {

  public MkvWriter() {
    nativePointer = newMkvWriter();
  }

  public void close() {
    Close(nativePointer);
  }

  @Override
  public void elementStartNotify(long elementId, long position) {
    ElementStartNotify(nativePointer, elementId, position);
  }

  public boolean open(String fileName) {
    return Open(nativePointer, fileName);
  }

  @Override
  public long position() {
    return GetPosition(nativePointer);
  }

  @Override
  public int position(long position) {
    return SetPosition(nativePointer, position);
  }

  @Override
  public boolean seekable() {
    return Seekable(nativePointer);
  }

  @Override
  public int write(byte[] buffer) {
    return Write(nativePointer, buffer, buffer.length);
  }

  protected MkvWriter(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteMkvWriter(nativePointer);
  }

  private static native void Close(long jMkvWriter);
  private static native void deleteMkvWriter(long jMkvWriter);
  private static native void ElementStartNotify(long jMkvWriter, long element_id, long position);
  private static native long GetPosition(long jMkvWriter);
  private static native long newMkvWriter();
  private static native boolean Open(long jMkvWriter, String jFilename);
  private static native boolean Seekable(long jMkvWriter);
  private static native int SetPosition(long jMkvWriter, long position);
  private static native int Write(long jMkvWriter, byte[] jBuffer, int length);
}

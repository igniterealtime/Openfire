// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

public class MkvReader extends IMkvReader {

  public MkvReader() {
    nativePointer = newMkvReader();
  }

  public void close() {
    Close(nativePointer);
  }

  @Override
  public int length(long[] total, long[] available) {
    return Length(nativePointer, total, available);
  }

  public int open(String fileName) {
    return Open(nativePointer, fileName);
  }

  @Override
  public int read(long position, long length, byte[][] buffer) {
    return Read(nativePointer, position, length, buffer);
  }

  protected MkvReader(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteMkvReader(nativePointer);
  }

  private static native void Close(long jMkvReader);
  private static native void deleteMkvReader(long jMkvReader);
  private static native int Length(long jMkvReader, long[] jTotal, long[] jAvailable);
  private static native long newMkvReader();
  private static native int Open(long jMkvReader, String jFileName);
  private static native int Read(long jMkvReader, long position, long length, byte[][] jBuffer);
}

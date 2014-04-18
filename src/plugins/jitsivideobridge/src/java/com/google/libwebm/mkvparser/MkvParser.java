// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public abstract class MkvParser extends Common {

  public static final int E_BUFFER_NOT_FULL = -3;
  public static final int E_FILE_FORMAT_INVALID = -2;

  public static long getUintLength(IMkvReader mkvReader, long position, long[] length) {
    return GetUIntLength(mkvReader.getNativePointer(), position, length);
  }

  public static void getVersion(int[] major, int[] minor, int[] build, int[] revision) {
    GetVersion(major, minor, build, revision);
  }

  public static boolean match(IMkvReader mkvReader, long[] position, long id, long[] value) {
    return MatchValue(mkvReader.getNativePointer(), position, id, value);
  }

  public static boolean match(IMkvReader mkvReader, long[] position, long id, byte[][] buffer) {
    long[] bufferLength = {0};
    return MatchBuffer(mkvReader.getNativePointer(), position, id, buffer, bufferLength);
  }

  public static long parseElementHeader(IMkvReader mkvReader, long[] position, long stop, long[] id,
      long[] size) {
    return ParseElementHeader(mkvReader.getNativePointer(), position, stop, id, size);
  }

  public static long readUint(IMkvReader mkvReader, long position, long[] length) {
    return ReadUInt(mkvReader.getNativePointer(), position, length);
  }

  public static long unserializeFloat(IMkvReader mkvReader, long position, long size,
      double[] result) {
    return UnserializeFloat(mkvReader.getNativePointer(), position, size, result);
  }

  public static long unserializeInt(IMkvReader mkvReader, long position, long length,
      long[] result) {
    return UnserializeInt(mkvReader.getNativePointer(), position, length, result);
  }

  public static long unserializeString(IMkvReader mkvReader, long position, long size,
      String[] str) {
    return UnserializeString(mkvReader.getNativePointer(), position, size, str);
  }

  public static long unserializeUint(IMkvReader mkvReader, long position, long size) {
    return UnserializeUInt(mkvReader.getNativePointer(), position, size);
  }

  private static native long GetUIntLength(long jMkvReader, long position, long[] jLength);
  private static native void GetVersion(int[] jMajor, int[] jMinor, int[] jBuild, int[] jRevision);
  private static native boolean MatchBuffer(long jMkvReader, long[] jPosition, long id,
      byte[][] jBuffer, long[] jBufferLength);
  private static native boolean MatchValue(long jMkvReader, long[] jPosition, long id,
      long[] jValue);
  private static native long ParseElementHeader(long jMkvReader, long[] jPosition, long stop,
      long[] jId, long[] jSize);
  private static native long ReadUInt(long jMkvReader, long position, long[] jLength);
  private static native long UnserializeFloat(long jMkvReader, long position, long size,
      double[] jResult);
  private static native long UnserializeInt(long jMkvReader, long position, long length,
      long[] jResult);
  private static native long UnserializeString(long jMkvReader, long position, long size,
      String[] jStr);
  private static native long UnserializeUInt(long jMkvReader, long position, long size);
}

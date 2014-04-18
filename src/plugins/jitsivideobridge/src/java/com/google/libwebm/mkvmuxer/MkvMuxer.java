// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

import com.google.libwebm.Common;

public abstract class MkvMuxer extends Common {

  public final long kEbmlUnknownValue = 0x01ffffffffffffffL;

  public static long ebmlElementSize(long type, long value) {
    return EbmlElementSizeLong(type, value);
  }

  public static long ebmlElementSize(long type, float value) {
    return EbmlElementSizeFloat(type, value);
  }

  public static long ebmlElementSize(long type, byte[] value) {
    return EbmlElementSizeBuffer(type, value, value.length);
  }

  public static long ebmlElementSize(long type, String value) {
    return EbmlElementSizeString(type, value);
  }

  public static long ebmlMasterElementSize(long type, long value) {
    return EbmlMasterElementSize(type, value);
  }

  public static void getVersion(int[] major, int[] minor, int[] build, int[] revision) {
    GetVersion(major, minor, build, revision);
  }

  public static long makeUid(int seed) {
    return MakeUID(seed);
  }

  public static int serializeInt(IMkvWriter writer, long value, int size) {
    return SerializeInt(writer.getNativePointer(), value, size);
  }

  public static boolean writeEbmlElement(IMkvWriter writer, long type, long value) {
    return WriteEbmlElementLong(writer.getNativePointer(), type, value);
  }

  public static boolean writeEbmlElement(IMkvWriter writer, long type, float value) {
    return WriteEbmlElementFloat(writer.getNativePointer(), type, value);
  }

  public static boolean writeEbmlElement(IMkvWriter writer, long type, byte[] value) {
    return WriteEbmlElementBuffer(writer.getNativePointer(), type, value, value.length);
  }

  public static boolean writeEbmlElement(IMkvWriter writer, long type, String value) {
    return WriteEbmlElementString(writer.getNativePointer(), type, value);
  }

  public static boolean writeEbmlHeader(IMkvWriter writer) {
    return WriteEbmlHeader(writer.getNativePointer());
  }

  public static boolean writeEbmlMasterElement(IMkvWriter writer, long value, long size) {
    return WriteEbmlMasterElement(writer.getNativePointer(), value, size);
  }

  public static int writeId(IMkvWriter writer, long type) {
    return WriteID(writer.getNativePointer(), type);
  }

  public static long writeMetadataBlock(IMkvWriter writer, byte[] data, long trackNumber,
      long timeCode, long durationTimeCode) {
    return WriteMetadataBlock(writer.getNativePointer(), data, data.length, trackNumber, timeCode,
        durationTimeCode);
  }

  public static long writeSimpleBlock(IMkvWriter writer, byte[] data, long trackNumber,
      long timeCode, long isKey) {
    return WriteSimpleBlock(writer.getNativePointer(), data, data.length, trackNumber, timeCode,
        isKey);
  }

  public static int writeUint(IMkvWriter writer, long value) {
    return WriteUInt(writer.getNativePointer(), value);
  }

  public static int writeUintSize(IMkvWriter writer, long value, int size) {
    return WriteUIntSize(writer.getNativePointer(), value, size);
  }

  public static long writeVoidElement(IMkvWriter writer, long size) {
    return WriteVoidElement(writer.getNativePointer(), size);
  }

  private static native long EbmlElementSizeBuffer(long type, byte[] jValue, long size);
  private static native long EbmlElementSizeFloat(long type, float value);
  private static native long EbmlElementSizeLong(long type, long jValue);
  private static native long EbmlElementSizeString(long type, String jValue);
  private static native long EbmlMasterElementSize(long type, long value);
  private static native void GetVersion(int[] jMajor, int[] jMinor, int[] jBuild, int[] jRevision);
  private static native long MakeUID(int jSeed);
  private static native int SerializeInt(long jWriter, long value, int size);
  private static native boolean WriteEbmlElementBuffer(long jWriter, long type, byte[] jValue,
      long size);
  private static native boolean WriteEbmlElementFloat(long jWriter, long type, float value);
  private static native boolean WriteEbmlElementLong(long jWriter, long type, long jValue);
  private static native boolean WriteEbmlElementString(long jWriter, long type, String jValue);
  private static native boolean WriteEbmlHeader(long jWriter);
  private static native boolean WriteEbmlMasterElement(long jWriter, long value, long size);
  private static native int WriteID(long jWriter, long type);
  private static native long WriteMetadataBlock(long jWriter, byte[] jData, long length,
      long track_number, long timecode, long duration_timecode);
  private static native long WriteSimpleBlock(long jWriter, byte[] jData, long length,
      long track_number, long timecode, long is_key);
  private static native int WriteUInt(long jWriter, long value);
  private static native int WriteUIntSize(long jWriter, long value, int size);
  private static native long WriteVoidElement(long jWriter, long size);
}

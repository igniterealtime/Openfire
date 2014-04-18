// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class ContentEncoding extends Common {

  public enum CipherMode {
    None,
    kCTR
  };

  public ContentEncoding() {
    nativePointer = newContentEncoding();
  }

  public long encodingOrder() {
    return encodingOrder(nativePointer);
  }

  public long encodingScope() {
    return encodingScope(nativePointer);
  }

  public long encodingType() {
    return encodingType(nativePointer);
  }

  public ContentCompression getCompressionByIndex(long index) {
    long pointer = GetCompressionByIndex(nativePointer, index);
    return new ContentCompression(pointer);
  }

  public long getCompressionCount() {
    return GetCompressionCount(nativePointer);
  }

  public ContentEncryption getEncryptionByIndex(long index) {
    long pointer = GetEncryptionByIndex(nativePointer, index);
    return new ContentEncryption(pointer);
  }

  public long getEncryptionCount() {
    return GetEncryptionCount(nativePointer);
  }

  public long parseContentEncAesSettingsEntry(long start, long size, IMkvReader mkvReader,
      ContentEncAesSettings contentEncAesSettings) {
    return ParseContentEncAESSettingsEntry(nativePointer, start, size, mkvReader.getNativePointer(),
        contentEncAesSettings.getNativePointer());
  }

  public long parseContentEncodingEntry(long start, long size, IMkvReader mkvReader) {
    return ParseContentEncodingEntry(nativePointer, start, size, mkvReader.getNativePointer());
  }

  public long parseEncryptionEntry(long start, long size, IMkvReader mkvReader,
      ContentEncryption contentEncryption) {
    return ParseEncryptionEntry(nativePointer, start, size, mkvReader.getNativePointer(),
        contentEncryption.getNativePointer());
  }

  protected ContentEncoding(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteContentEncoding(nativePointer);
  }

  private static native void deleteContentEncoding(long jContentEncoding);
  private static native long encodingOrder(long jContentEncoding);
  private static native long encodingScope(long jContentEncoding);
  private static native long encodingType(long jContentEncoding);
  private static native long GetCompressionByIndex(long jContentEncoding, long idx);
  private static native long GetCompressionCount(long jContentEncoding);
  private static native long GetEncryptionByIndex(long jContentEncoding, long idx);
  private static native long GetEncryptionCount(long jContentEncoding);
  private static native long newContentEncoding();
  private static native long ParseContentEncAESSettingsEntry(long jContentEncoding, long start,
      long size, long jMkvReader, long jContentEncAesSettings);
  private static native long ParseContentEncodingEntry(long jContentEncoding, long start, long size,
      long jMkvReader);
  private static native long ParseEncryptionEntry(long jContentEncoding, long start, long size,
      long jMkvReader, long jContentEncryption);
}

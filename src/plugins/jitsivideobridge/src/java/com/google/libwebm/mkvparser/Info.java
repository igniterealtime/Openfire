// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class Info extends Common {

  public Info() {
    nativePointer = newInfo();
  }

  public void clear() {
    Clear(nativePointer);
  }

  public int copy(Info destination) {
    return Copy(nativePointer, destination.getNativePointer());
  }

  public String getCodecId() {
    return getCodecId(nativePointer);
  }

  public String getCodecNameAsUtf8() {
    return getCodecNameAsUTF8(nativePointer);
  }

  public byte[] getCodecPrivate() {
    return getCodecPrivate(nativePointer);
  }

  public boolean getLacing() {
    return getLacing(nativePointer);
  }

  public String getNameAsUtf8() {
    return getNameAsUTF8(nativePointer);
  }

  public long getNumber() {
    return getNumber(nativePointer);
  }

  public Settings getSettings() {
    long pointer = getSettings(nativePointer);
    return new Settings(pointer);
  }

  public long getType() {
    return getType(nativePointer);
  }

  public void setCodecId(String codecId) {
    setCodecId(nativePointer, codecId);
  }

  public void setCodecNameAsUtf8(String codecNameAsUtf8) {
    setCodecNameAsUTF8(nativePointer, codecNameAsUtf8);
  }

  public void setCodecPrivate(String codecPrivate) {
    setCodecPrivate(nativePointer, codecPrivate);
  }

  public void setLacing(boolean lacing) {
    setLacing(nativePointer, lacing);
  }

  public void setNameAsUtf8(String nameAsUtf8) {
    setNameAsUTF8(nativePointer, nameAsUtf8);
  }

  public void setNumber(long number) {
    setNumber(nativePointer, number);
  }

  public void setSettings(Settings settings) {
    setSettings(nativePointer, settings.getNativePointer());
  }

  public void setType(long type) {
    setType(nativePointer, type);
  }

  public void setUid(long uid) {
    setUid(nativePointer, uid);
  }

  protected Info(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteInfo(nativePointer);
  }

  private static native void Clear(long jInfo);
  private static native int Copy(long jInfo, long jDestination);
  private static native void deleteInfo(long jInfo);
  private static native String getCodecId(long jInfo);
  private static native String getCodecNameAsUTF8(long jInfo);
  private static native byte[] getCodecPrivate(long jInfo);
  private static native boolean getLacing(long jInfo);
  private static native String getNameAsUTF8(long jInfo);
  private static native long getNumber(long jInfo);
  private static native long getSettings(long jInfo);
  private static native long getType(long jInfo);
  private static native long newInfo();
  private static native void setCodecId(long jInfo, String jCodecId);
  private static native void setCodecNameAsUTF8(long jInfo, String jCodecNameAsUtf8);
  private static native void setCodecPrivate(long jInfo, String jCodecPrivate);
  private static native void setLacing(long jInfo, boolean lacing);
  private static native void setNameAsUTF8(long jInfo, String jNameAsUtf8);
  private static native void setNumber(long jInfo, long number);
  private static native void setSettings(long jInfo, long jSettings);
  private static native void setType(long jInfo, long type);
  private static native void setUid(long jInfo, long uid);
}

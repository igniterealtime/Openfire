// Copyright 2012 Google Inc. All Rights Reserved.
// Author: frkoenig@google.com (Fritz Koenig)
package com.google.libvpx;

/**
 * JNI interface to C struct used for configuring
 * the libvpx encoder.
 */
public class LibVpxEncConfig extends LibVpxCom {
  private long encCfgObj;

  private native long vpxCodecEncAllocCfg();
  private native void vpxCodecEncFreeCfg(long cfg);

  private native int vpxCodecEncConfigDefault(long cfg, int argUsage);

  private native void vpxCodecEncSetUsage(long cfg, int value);
  private native void vpxCodecEncSetThreads(long cfg, int value);
  private native void vpxCodecEncSetProfile(long cfg, int value);
  private native void vpxCodecEncSetWidth(long cfg, int value);
  private native void vpxCodecEncSetHeight(long cfg, int value);
  private native void vpxCodecEncSetTimebase(long cfg, int num, int den);
  private native void vpxCodecEncSetErrorResilient(long cfg, int value);
  private native void vpxCodecEncSetPass(long cfg, int value);
  private native void vpxCodecEncSetLagInFrames(long cfg, int value);
  private native void vpxCodecEncSetRCDropframeThresh(long cfg, int value);
  private native void vpxCodecEncSetRCResizeAllowed(long cfg, int value);
  private native void vpxCodecEncSetRCResizeUpThresh(long cfg, int value);
  private native void vpxCodecEncSetRCResizeDownThresh(long cfg, int value);
  private native void vpxCodecEncSetRCEndUsage(long cfg, int value);
  private native void vpxCodecEncSetRCTargetBitrate(long cfg, int value);
  private native void vpxCodecEncSetRCMinQuantizer(long cfg, int value);
  private native void vpxCodecEncSetRCMaxQuantizer(long cfg, int value);
  private native void vpxCodecEncSetRCUndershootPct(long cfg, int value);
  private native void vpxCodecEncSetRCOvershootPct(long cfg, int value);
  private native void vpxCodecEncSetRCBufSz(long cfg, int value);
  private native void vpxCodecEncSetRCBufInitialSz(long cfg, int value);
  private native void vpxCodecEncSetRCBufOptimalSz(long cfg, int value);
  private native void vpxCodecEncSetRC2PassVBRBiasPct(long cfg, int value);
  private native void vpxCodecEncSetRC2PassVBRMinsectionPct(long cfg, int value);
  private native void vpxCodecEncSetRC2PassVBRMaxsectioniasPct(long cfg, int value);
  private native void vpxCodecEncSetKFMode(long cfg, int value);
  private native void vpxCodecEncSetKFMinDist(long cfg, int value);
  private native void vpxCodecEncSetKFMaxDist(long cfg, int value);

  private native int vpxCodecEncGetUsage(long cfg);
  private native int vpxCodecEncGetThreads(long cfg);
  private native int vpxCodecEncGetProfile(long cfg);
  private native int vpxCodecEncGetWidth(long cfg);
  private native int vpxCodecEncGetHeight(long cfg);
  private native Rational vpxCodecEncGetTimebase(long cfg);
  private native int vpxCodecEncGetErrorResilient(long cfg);
  private native int vpxCodecEncGetPass(long cfg);
  private native int vpxCodecEncGetLagInFrames(long cfg);
  private native int vpxCodecEncGetRCDropframeThresh(long cfg);
  private native int vpxCodecEncGetRCResizeAllowed(long cfg);
  private native int vpxCodecEncGetRCResizeUpThresh(long cfg);
  private native int vpxCodecEncGetRCResizeDownThresh(long cfg);
  private native int vpxCodecEncGetRCEndUsage(long cfg);
  private native int vpxCodecEncGetRCTargetBitrate(long cfg);
  private native int vpxCodecEncGetRCMinQuantizer(long cfg);
  private native int vpxCodecEncGetRCMaxQuantizer(long cfg);
  private native int vpxCodecEncGetRCUndershootPct(long cfg);
  private native int vpxCodecEncGetRCOvershootPct(long cfg);
  private native int vpxCodecEncGetRCBufSz(long cfg);
  private native int vpxCodecEncGetRCBufInitialSz(long cfg);
  private native int vpxCodecEncGetRCBufOptimalSz(long cfg);
  private native int vpxCodecEncGetRC2PassVBRBiasPct(long cfg);
  private native int vpxCodecEncGetRC2PassVBRMinsectionPct(long cfg);
  private native int vpxCodecEncGetRC2PassVBRMaxsectioniasPct(long cfg);
  private native int vpxCodecEncGetKFMode(long cfg);
  private native int vpxCodecEncGetKFMinDist(long cfg);
  private native int vpxCodecEncGetKFMaxDist(long cfg);

  private native int vpxCodecEncGetFourcc();

  public LibVpxEncConfig(int width, int height) throws LibVpxException {
    encCfgObj = vpxCodecEncAllocCfg();
    if (encCfgObj == 0) {
      throw new LibVpxException("Can not allocate JNI encoder configure object");
    }

    int res = vpxCodecEncConfigDefault(encCfgObj, 0);
    if (res != 0) {
      vpxCodecEncFreeCfg(encCfgObj);
      throw new LibVpxException(errToString(res));
    }

    setWidth(width);
    setHeight(height);

    /* Change the default timebase to a high enough value so that the encoder
     * will always create strictly increasing timestamps.
     */
    setTimebase(1, 1000);
  }

  public void close() {
    vpxCodecEncFreeCfg(encCfgObj);
  }

  public long handle() {
    return encCfgObj;
  }

  public void setThreads(int value) {
    vpxCodecEncSetThreads(encCfgObj, value);
  }

  public void setProfile(int value) {
    vpxCodecEncSetProfile(encCfgObj, value);
  }

  public void setWidth(int value) {
    vpxCodecEncSetWidth(encCfgObj, value);
  }

  public void setHeight(int value) {
    vpxCodecEncSetHeight(encCfgObj, value);
  }

  public void setTimebase(int num, int den) {
    vpxCodecEncSetTimebase(encCfgObj, num, den);
  }

  public void setErrorResilient(int value) {
    vpxCodecEncSetErrorResilient(encCfgObj, value);
  }

  public void setPass(int value) {
    vpxCodecEncSetPass(encCfgObj, value);
  }

  public void setLagInFrames(int value) {
    vpxCodecEncSetLagInFrames(encCfgObj, value);
  }

  public void setRCDropframeThresh(int value) {
    vpxCodecEncSetRCDropframeThresh(encCfgObj, value);
  }

  public void setRCResizeAllowed(int value) {
    vpxCodecEncSetRCResizeAllowed(encCfgObj, value);
  }

  public void setRCResizeUpThresh(int value) {
    vpxCodecEncSetRCResizeUpThresh(encCfgObj, value);
  }

  public void setRCResizeDownThresh(int value) {
    vpxCodecEncSetRCResizeDownThresh(encCfgObj, value);
  }

  public void setRCEndUsage(int value) {
    vpxCodecEncSetRCEndUsage(encCfgObj, value);
  }

  public void setRCTargetBitrate(int value) {
    vpxCodecEncSetRCTargetBitrate(encCfgObj, value);
  }

  public void setRCMinQuantizer(int value) {
    vpxCodecEncSetRCMinQuantizer(encCfgObj, value);
  }

  public void setRCMaxQuantizer(int value) {
    vpxCodecEncSetRCMaxQuantizer(encCfgObj, value);
  }

  public void setRCUndershootPct(int value) {
    vpxCodecEncSetRCUndershootPct(encCfgObj, value);
  }

  public void setRCOvershootPct(int value) {
    vpxCodecEncSetRCOvershootPct(encCfgObj, value);
  }

  public void setRCBufSz(int value) {
    vpxCodecEncSetRCBufSz(encCfgObj, value);
  }

  public void setRCBufInitialSz(int value) {
    vpxCodecEncSetRCBufInitialSz(encCfgObj, value);
  }

  public void setRCBufOptimalSz(int value) {
    vpxCodecEncSetRCBufOptimalSz(encCfgObj, value);
  }

  public void setKFMinDist(int value) {
    vpxCodecEncSetKFMinDist(encCfgObj, value);
  }

  public void setKFMaxDist(int value) {
    vpxCodecEncSetKFMaxDist(encCfgObj, value);
  }

  public int getThreads() {
    return vpxCodecEncGetThreads(encCfgObj);
  }

  public int getProfile() {
    return vpxCodecEncGetProfile(encCfgObj);
  }

  public int getWidth() {
    return vpxCodecEncGetWidth(encCfgObj);
  }

  public int getHeight() {
    return vpxCodecEncGetHeight(encCfgObj);
  }

  public Rational getTimebase() {
    return vpxCodecEncGetTimebase(encCfgObj);
  }

  public int getErrorResilient() {
    return vpxCodecEncGetErrorResilient(encCfgObj);
  }

  public int getPass() {
    return vpxCodecEncGetPass(encCfgObj);
  }

  public int getLagInFrames() {
    return vpxCodecEncGetLagInFrames(encCfgObj);
  }

  public int getRCDropframeThresh() {
    return vpxCodecEncGetRCDropframeThresh(encCfgObj);
  }

  public int getRCResizeAllowed() {
    return vpxCodecEncGetRCResizeAllowed(encCfgObj);
  }

  public int getRCResizeUpThresh() {
    return vpxCodecEncGetRCResizeUpThresh(encCfgObj);
  }

  public int getRCResizeDownThresh() {
    return vpxCodecEncGetRCResizeDownThresh(encCfgObj);
  }

  public int getRCEndUsage() {
    return vpxCodecEncGetRCEndUsage(encCfgObj);
  }

  public int getRCTargetBitrate() {
    return vpxCodecEncGetRCTargetBitrate(encCfgObj);
  }

  public int getRCMinQuantizer() {
    return vpxCodecEncGetRCMinQuantizer(encCfgObj);
  }

  public int getRCMaxQuantizer() {
    return vpxCodecEncGetRCMaxQuantizer(encCfgObj);
  }

  public int getRCUndershootPct() {
    return vpxCodecEncGetRCUndershootPct(encCfgObj);
  }

  public int getRCOvershootPct() {
    return vpxCodecEncGetRCOvershootPct(encCfgObj);
  }

  public int getRCBufSz() {
    return vpxCodecEncGetRCBufSz(encCfgObj);
  }

  public int getRCBufInitialSz() {
    return vpxCodecEncGetRCBufInitialSz(encCfgObj);
  }

  public int getRCBufOptimalSz() {
    return vpxCodecEncGetRCBufOptimalSz(encCfgObj);
  }

  public int getFourcc() {
    return vpxCodecEncGetFourcc();
  }
}

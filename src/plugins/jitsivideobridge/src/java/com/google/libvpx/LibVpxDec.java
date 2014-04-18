// Copyright 2012 Google Inc. All Rights Reserved.
// Author: frkoenig@google.com (Fritz Koenig)
package com.google.libvpx;

/**
 * libvpx JNI wrapper for decoding functions.
 */
public class LibVpxDec extends LibVpxCom {
  private long decCfgObj;

  private native long vpxCodecDecAllocCfg();
  private native void vpxCodecDecFreeCfg(long cfg);

  private native void vpxCodecDecSetThreads(long cfg, int value);
  private native void vpxCodecDecSetWidth(long cfg, int value);
  private native void vpxCodecDecSetHeight(long cfg, int value);
  private native int vpxCodecDecGetThreads(long cfg);
  private native int vpxCodecDecGetWidth(long cfg);
  private native int vpxCodecDecGetHeight(long cfg);

  private native boolean vpxCodecDecInit(long decoder, long cfg,
                                         boolean postproc, boolean ecEnabled);
  private native int vpxCodecDecDecode(long decoder, byte[] buf, int bufSize);
  private native byte[] vpxCodecDecGetFrame(long decoder);

  public LibVpxDec(int width, int height,
                   int threads,
                   boolean postProcEnabled,
                   boolean errorConcealmentEnabled) throws LibVpxException {
    decCfgObj = vpxCodecDecAllocCfg();
    vpxCodecIface = vpxCodecAllocCodec();

    if (width > 0) {
      vpxCodecDecSetWidth(decCfgObj, width);
    }

    if (height > 0) {
      vpxCodecDecSetHeight(decCfgObj, height);
    }

    if (threads > 0) {
      vpxCodecDecSetThreads(decCfgObj, threads);
    }

    if (!vpxCodecDecInit(vpxCodecIface, decCfgObj,
                         postProcEnabled, errorConcealmentEnabled)) {
        throw new LibVpxException(vpxCodecError(vpxCodecIface));
    }
  }

  public LibVpxDec(boolean postProcEnabled,
                   boolean errorConcealmentEnabled) throws LibVpxException {
    this(0, 0, 0, postProcEnabled, errorConcealmentEnabled);
  }

  public LibVpxDec() throws LibVpxException {
    this(0, 0, 0, false, false);
  }

  public byte[] decodeFrameToBuffer(byte[] rawFrame) throws LibVpxException {
    if (vpxCodecDecDecode(vpxCodecIface, rawFrame, rawFrame.length) != 0) {
      throw new LibVpxException(vpxCodecErrorDetail(vpxCodecIface));
    }

    return vpxCodecDecGetFrame(vpxCodecIface);
  }

  public void close() {
    vpxCodecDestroy(vpxCodecIface);
    vpxCodecDecFreeCfg(decCfgObj);
    vpxCodecFreeCodec(vpxCodecIface);
  }
}

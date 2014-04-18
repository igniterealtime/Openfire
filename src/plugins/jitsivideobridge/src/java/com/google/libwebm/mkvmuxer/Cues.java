// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvmuxer;

import com.google.libwebm.Common;

public class Cues extends Common {

  public Cues() {
    nativePointer = newCues();
  }

  public boolean addCue(CuePoint cue) {
    return AddCue(nativePointer, cue.getNativePointer());
  }

  public int cueEntriesSize() {
    return cueEntriesSize(nativePointer);
  }

  public CuePoint getCueByIndex(int index) {
    long pointer = GetCueByIndex(nativePointer, index);
    return new CuePoint(pointer);
  }

  public boolean outputBlockNumber() {
    return outputBlockNumber(nativePointer);
  }

  public void setOutputBlockNumber(boolean outputBlockNumber) {
    setOutputBlockNumber(nativePointer, outputBlockNumber);
  }

  public boolean write(IMkvWriter writer) {
    return Write(nativePointer, writer.getNativePointer());
  }

  protected Cues(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteCues(nativePointer);
  }

  private static native boolean AddCue(long jCues, long jCue);
  private static native int cueEntriesSize(long jCues);
  private static native long GetCueByIndex(long jCues, int index);
  private static native void deleteCues(long jCuePoint);
  private static native long newCues();
  private static native boolean outputBlockNumber(long jCues);
  private static native void setOutputBlockNumber(long jCues, boolean output_block_number);
  private static native boolean Write(long jCues, long jWriter);
}

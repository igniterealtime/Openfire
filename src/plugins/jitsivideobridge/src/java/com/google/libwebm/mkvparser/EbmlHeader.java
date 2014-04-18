// Author: mszal@google.com (Michael Szal)

package com.google.libwebm.mkvparser;

import com.google.libwebm.Common;

public class EbmlHeader extends Common {

  public EbmlHeader() {
    nativePointer = newEBMLHeader();
  }

  public String getDocType() {
    return getDocType(nativePointer);
  }

  public long getDocTypeReadVersion() {
    return getDocTypeReadVersion(nativePointer);
  }

  public long getDocTypeVersion() {
    return getDocTypeVersion(nativePointer);
  }

  public long getMaxIdLength() {
    return getMaxIdLength(nativePointer);
  }

  public long getMaxSizeLength() {
    return getMaxSizeLength(nativePointer);
  }

  public long getReadVersion() {
    return getReadVersion(nativePointer);
  }

  public long getVersion() {
    return getVersion(nativePointer);
  }

  public void init() {
    Init(nativePointer);
  }

  public long parse(IMkvReader mkvReader, long[] position) {
    return Parse(nativePointer, mkvReader.getNativePointer(), position);
  }

  public void setDocType(String docType) {
    setDocType(nativePointer, docType);
  }

  public void setDocTypeReadVersion(long docTypeReadVersion) {
    setDocTypeReadVersion(nativePointer, docTypeReadVersion);
  }

  public void setDocTypeVersion(long docTypeVersion) {
    setDocTypeVersion(nativePointer, docTypeVersion);
  }

  public void setMaxIdLength(long maxIdLength) {
    setMaxIdLength(nativePointer, maxIdLength);
  }

  public void setMaxSizeLength(long maxSizeLength) {
    setMaxSizeLength(nativePointer, maxSizeLength);
  }

  public void setReadVersion(long readVersion) {
    setReadVersion(nativePointer, readVersion);
  }

  public void setVersion(long version) {
    setVersion(nativePointer, version);
  }

  protected EbmlHeader(long nativePointer) {
    super(nativePointer);
  }

  @Override
  protected void deleteObject() {
    deleteEBMLHeader(nativePointer);
  }

  private static native void deleteEBMLHeader(long jEbmlHeader);
  private static native String getDocType(long jEbmlHeader);
  private static native long getDocTypeReadVersion(long jEbmlHeader);
  private static native long getDocTypeVersion(long jEbmlHeader);
  private static native long getMaxIdLength(long jEbmlHeader);
  private static native long getMaxSizeLength(long jEbmlHeader);
  private static native long getReadVersion(long jEbmlHeader);
  private static native long getVersion(long jEbmlHeader);
  private static native void Init(long jEbmlHeader);
  private static native long newEBMLHeader();
  private static native long Parse(long jEbmlHeader, long jMkvReader, long[] jPosition);
  private static native void setDocType(long jEbmlHeader, String jDocType);
  private static native void setDocTypeReadVersion(long jEbmlHeader, long docTypeReadVersion);
  private static native void setDocTypeVersion(long jEbmlHeader, long docTypeVersion);
  private static native void setMaxIdLength(long jEbmlHeader, long maxIdLength);
  private static native void setMaxSizeLength(long jEbmlHeader, long maxSizeLength);
  private static native void setReadVersion(long jEbmlHeader, long readVersion);
  private static native void setVersion(long jEbmlHeader, long version);
}

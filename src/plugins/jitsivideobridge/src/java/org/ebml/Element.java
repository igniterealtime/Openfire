/**
 * JEBML - Java library to read/write EBML/Matroska elements.
 * Copyright (C) 2004 Jory Stone <jebml@jory.info>
 * Based on Javatroska (C) 2002 John Cannon <spyder@matroska.org>
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.ebml;

/*
 * Element.java
 *
 * Created on November 19, 2002, 9:11 PM
 */

import org.ebml.io.*;
import org.ebml.util.*;

/**
     * Defines the basic EBML element.  Subclasses may provide child element access.
 * @author  John Cannon
 */
public class Element {

  protected Element parent;
  protected ElementType typeInfo;
  protected byte[] type = {
      0x00};
  private static int MIN_SIZE_LENGTH = 0;
  protected long size = 0;
  protected byte[] data;
  protected boolean dataRead = false;
  private int headerSize;

  /** Creates a new instance of Element */
  public Element(byte[] type) {
    this.type = type;
  }

  /** Read the element data 
   */
  public void readData(DataSource source) {
    //Setup a buffer for it's data
    this.data = new byte[(int)size];
    //Read the data
    source.read(this.data, 0, this.data.length);
    dataRead = true;
  }

  /** Skip the element data 
   */
  public void skipData(DataSource source) {
    if (!dataRead) {
      // Skip the data
      source.skip(size);
      dataRead = true;
    }
  }

  public long writeElement(DataWriter writer) 
  {
    return writeHeaderData(writer) + writeData(writer);
  }

  /** Write the element header data.
   *  Override this in sub-classes for more specialized writing.
   */
  public long writeHeaderData(DataWriter writer) 
  {
    long len = 0;

    byte [] type = getType();
    len += type.length;
    writer.write(type);
    
    byte [] size = Element.makeEbmlCodedSize(getSize());
    len += size.length;
    writer.write(size);

    return len;
  }

  /** Write the element data.
   *  Override this in sub-classes for more specialized writing.
   */
  public long writeData(DataWriter writer) 
  {
    return writer.write(this.data, 0, this.data.length);
  }

  /** Getter for property data.
   * @return Value of property data.
   *
   */
  public byte[] getData() {
    return this.data;
  }

  /** Setter for property data.
   * @param data New value of property data.
   *
   */
  public void setData(byte[] data) {
    this.data = data;
    this.size = data.length;
  }

  /** Clears the data of this element, useful if you just want 
   * this element to be a placeholder
   */
  public void clearData() 
  {
    this.data = null;
  }

  /** Getter for property size.
   * @return Value of property size.
   *
   */
  public long getSize() {
    return size;
  }

  /** Setter for property size.
   * @param size New value of property size.
   *
   */
  public void setSize(long size) {
    this.size = size;
  }

  /** Get the total size of this element
   */
  public long getTotalSize() 
  {
    long totalSize = 0;
    totalSize += getType().length;
    //totalSize += Element.codedSizeLength(getSize());
    totalSize += this.headerSize;
    totalSize += getSize();    
    return totalSize;
  }

  /** Getter for property type.
   * @return Value of property type.
   *
   */
  public byte[] getType() {
    return type;
  }

  /** Setter for property type.
   * @param type New value of property type.
   *
   */
  public void setType(byte[] type) {
    this.type = type;
  }

  public void setElementType(ElementType typeInfo) {
    this.typeInfo = typeInfo;
  }

  public ElementType getElementType() {
    return typeInfo;
  }

  /** Getter for property parent.
   * @return Value of property parent.
   *
   */
  public Element getParent() {
    return this.parent;
  }

  /** Setter for property parent.
   * @param parent New value of property parent.
   *
   */
  public void setParent(Element parent) {
    this.parent = parent;
  }

  public byte[] toByteArray() {
    byte[] head = makeEbmlCode(type, size);
    byte[] ret = new byte[head.length + data.length];
    org.ebml.util.ArrayCopy.arraycopy(head, 0, ret, 0, head.length);
    org.ebml.util.ArrayCopy.arraycopy(data, 0, ret, head.length, data.length);
    return ret;
  }

  public boolean equals(byte [] typeId) {
    return ElementType.compareIDs(this.type, typeId);
  }

  public boolean equals(ElementType elemType) {
    return this.equals(elemType.id);
  }

  public static void setMinSizeLength(int minSize) {
    MIN_SIZE_LENGTH = minSize;
  }

  public static int getMinSizeLength() {
    return MIN_SIZE_LENGTH;
  }

  public static byte[] makeEbmlCode(byte[] typeID, long size) {
    int codedLen = codedSizeLength(size);
    byte[] ret = new byte[typeID.length + codedLen];
    ArrayCopy.arraycopy(typeID, 0, ret, 0, typeID.length);
    byte[] codedSize = makeEbmlCodedSize(size);
    ArrayCopy.arraycopy(codedSize, 0, ret, typeID.length, codedSize.length);
    return ret;
  }

  public static byte[] makeEbmlCodedSize(long size) {
    int len = codedSizeLength(size);
    byte[] ret = new byte[len];
    //byte[] packedSize = packIntUnsigned(size);
    long mask = 0x00000000000000FFL;
    for (int i = 0; i < len; i++) {
      ret[len - 1 - i] = (byte)((size & mask) >>> (i * 8));
      mask <<= 8;
    }
    //The first size bits should be clear, otherwise we have an error in the size determination.
    ret[0] |= 0x80 >> (len - 1);
    return ret;
  }

  public static int getMinByteSize(long value) {
    if (value <= 0x7F && value >= 0x80) {
      return 1;
    }
    else if (value <= 0x7FFF && value >= 0x8000) {
      return 2;
    }
    else if (value <= 0x7FFFFF && value >= 0x800000) {
      return 3;
    }
    else if (value <= 0x7FFFFFFF && value >= 0x80000000) {
      return 4;
    }
    else if (value <= 0x7FFFFFFFFFL && value >= 0x8000000000L) {
      return 5;
    }
    else if (value <= 0x7FFFFFFFFFFFL && value >= 0x800000000000L) {
      return 6;
    }
    else if (value <= 0x7FFFFFFFFFFFFFL && value >= 0x80000000000000L) {
      return 7;
    }
    else {
      return 8;
    }
  }

  public static int getMinByteSizeUnsigned(long value) {
    int size = 8;
    long mask = 0xFF00000000000000L;
    for (int i = 0; i < 8; i++) {
      if ((value & mask) == 0) {
        mask = mask >>> 8;
        size--;
      }
      else {
        return size;
      }
    }
    return 8;
  }

  public static int codedSizeLength(long value) {
    int codedSize = 0;
    if (value < 127) {
      codedSize = 1;
    }
    else if (value < 16383) {
      codedSize = 2;
    }
    else if (value < 2097151) {
      codedSize = 3;
    }
    else if (value < 268435455) {
      codedSize = 4;
    }
    if ((MIN_SIZE_LENGTH > 0) && (codedSize <= MIN_SIZE_LENGTH)) {
      codedSize = MIN_SIZE_LENGTH;
    }
    else {
      //codedSize = 8;
    }
    return codedSize;
  }

  public static byte[] packIntUnsigned(long value) {
    int size = getMinByteSizeUnsigned(value);
    return packInt(value, size);
  }

  public static byte[] packInt(long value) {
    int size = getMinByteSize(value);
    return packInt(value, size);
  }

  public static byte[] packInt(long value, int size) 
  {    
    byte[] ret = new byte[size];
    long mask = 0x00000000000000FFL;
    int b = size - 1;
    for (int i = 0; i < size; i++) 
    {
      ret[b] = (byte)(((value >>> (8 * i)) & mask));
      b--;
    }
    return ret;
  }

public void setHeaderSize(int headerSize) {
	this.headerSize = headerSize;
	
}
}

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

import org.ebml.io.*;
import org.ebml.util.*;

/**
 * EBMLReader.java
 *
 * Created on November 18, 2002, 4:03 PM
 *
 * @version 1.0
 */

/**
 * <h1>JEBML Intro</h1>
 * <hr>
 * <p>The following are the basic steps of how reading in JEBML works.</p>
 * <ul>
 * <li>1. The EbmlReader class reads the element header (id+size) and 
 *        looks it up in the supplied DocType class.
 * <li>2. The correct element type (Binary, UInteger, String, etc) is 
 *        created using the DocType data, BinaryElement is default 
 *        element type for unknown elements.
 * <li>3. The MatroskaDocType has the ids of all the elements staticly 
 *        declared.
 * <br>So to easily find out what an element is, you can use some code 
 *     like the following code
 * <p>
 * <code>
 * Element level1; <br>
 * // ... fill level1 <br>
 * if (level1.equals(MatroskaDocType.SegmentInfo_Id)) { <br>
 *   // Your Code Here <br>
 * } <br>
 * </code>
 * </p>
 * <li>4. To get the actual data for an Element you call the readData 
 *        method, if you just want to skip it use skipData().
 * <li>5. MasterElements are special, they have the readNextChild() 
 *        method which returns the next child element, returning null 
 *        when all the children have been read (it keeps track of the 
 *        current inputstream position).
 * <li>The usage method for JEBML is very close to libebml/libmatroska.
 * </ul>
 * <hr>
 *
 * Reads EBML elements from a <code>DataSource</code> and looks them up 
 * in the provided <code>DocType</code>.
 *
 * @author (c) 2002 John Cannon
 * @author (c) 2004 Jory Stone
 */
public class EBMLReader {

  protected DataSource source;
  protected DocType doc;
  protected ElementType elementTypes;
  protected ElementType lastElementType;

  /** Creates a new <code>EBMLReader</code> reading from the <code>DataSource
   * source</code>. The <code>DocType doc</code> is used to validate the
   * document.
   *
   * @param source DataSource to read from
   * @param doc DocType to use to validate the docment
   */
  public EBMLReader(DataSource source, DocType doc) {
    this.source = source;
    this.doc = doc;
    this.elementTypes = doc.getElements();
  }

  public Element readNextElement() {
    //Read the type.
    byte[] elementType = readEBMLCodeAsBytes(source);

    if (elementType == null)
      // Failed to read type id
      return null;

    //Read the size.
    byte[] data = getEBMLCodeAsBytes(source);
    long elementSize = parseEBMLCode(data);
    if (elementSize == 0)
      // Zero sized element is valid
      ;//return null;

    Element elem = null;
    //lastElementType = elementTypes.findElement(elementType);
    //if (lastElementType == null) {
    //  lastElementType = new UnknownElementType(elementType);
    //}
    //elem = doc.createElement(lastElementType);
    elem = doc.createElement(elementType);

    if (elem == null) {
      return null;
    }

    //Set it's size
    elem.setSize(elementSize);
    elem.setHeaderSize(data.length);

    //Setup a buffer for it's data
    //byte[] elementData = new byte[(int)elementSize];
    //Read the data
    //source.read(elementData, 0, elementData.length);
    //Set the data property on the element
    //elem.setData(elementData);

    //System.out.println("EBMLReader.readNextElement() returning element " + elem.getElementType().name + " with size " + Long.toString(elem.getTotalSize()-elementSize)+" "+Long.toString(elementSize));

    //Return the element
    return elem;
  }

  static public byte[] getEBMLCodeAsBytes(DataSource source) {
	    //Begin loop with byte set to newly read byte.
	    byte firstByte = source.readByte();
	    int numBytes = 0;

	    //Begin by counting the bits unset before the first '1'.
	    long mask = 0x0080;
	    for (int i = 0; i < 8; i++) {
	      //Start at left, shift to right.
	      if ((firstByte & mask) == mask) { //One found
	        //Set number of bytes in size = i+1 ( we must count the 1 too)
	        numBytes = i + 1;
	        //exit loop by pushing i out of the limit
	        i = 8;
	      }
	      mask >>>= 1;
	    }
	    if (numBytes == 0)
	      // Invalid size
	      return null;

	    //Setup space to store the bits
	    byte[] data = new byte[numBytes];

	    //Clear the 1 at the front of this byte, all the way to the beginning of the size
	    data[0] = (byte)(firstByte & ((0xFF >>> (numBytes))));

	    if (numBytes > 1) {
	      //Read the rest of the size.
	      source.read(data, 1, numBytes - 1);
	    }

	    return data;
	  }
  
  
  /**
   * Reads an (Unsigned) EBML code from the DataSource and encodes it into a long.  This size should be
   * cast into an int for actual use as Java only allows upto 32-bit file I/O operations.
   *
   * @return ebml size
   */
  static public long readEBMLCode(DataSource source) {
    //Begin loop with byte set to newly read byte.
    byte firstByte = source.readByte();
    int numBytes = 0;

    //Begin by counting the bits unset before the first '1'.
    long mask = 0x0080;
    for (int i = 0; i < 8; i++) {
      //Start at left, shift to right.
      if ((firstByte & mask) == mask) { //One found
        //Set number of bytes in size = i+1 ( we must count the 1 too)
        numBytes = i + 1;
        //exit loop by pushing i out of the limit
        i = 8;
      }
      mask >>>= 1;
    }
    if (numBytes == 0)
      // Invalid size
      return 0;

    //Setup space to store the bits
    byte[] data = new byte[numBytes];

    //Clear the 1 at the front of this byte, all the way to the beginning of the size
    data[0] = (byte)(firstByte & ((0xFF >>> (numBytes))));

    if (numBytes > 1) {
      //Read the rest of the size.
      source.read(data, 1, numBytes - 1);
    }

    //Put this into a long
    long size = 0;
    long n = 0;
    for (int i = 0; i < numBytes; i++) {
      n = ((long)data[numBytes - 1 - i] << 56) >>> 56;
      size = size | (n << (8 * i));
    }
    return size;
  }
  
  public static long parseEBMLCode(byte[] data) {
	if(data==null)
		return 0;
    //Put this into a long
    long size = 0;
    long n = 0;
    for (int i = 0; i < data.length; i++) {
      n = ((long)data[data.length - 1 - i] << 56) >>> 56;
      size = size | (n << (8 * i));
    }
    return size;
  }

  /**
   * Reads an (Unsigned) EBML code from the DataSource and encodes it into a long.  This size should be
   * cast into an int for actual use as Java only allows upto 32-bit file I/O operations.
   *
   * @return ebml size
   */
  static public long readEBMLCode(byte [] source) 
  {
    return readEBMLCode(source, 0);
  }

  /**
   * Reads an (Unsigned) EBML code from the DataSource and encodes it into a long.  This size should be
   * cast into an int for actual use as Java only allows upto 32-bit file I/O operations.
   *
   * @return ebml size
   */
  static public long readEBMLCode(byte [] source, int offset) 
  {
    //Begin loop with byte set to newly read byte.
    byte firstByte = source[offset];
    int numBytes = 0;

    //Begin by counting the bits unset before the first '1'.
    long mask = 0x0080;
    for (int i = 0; i < 8; i++) 
    {
      //Start at left, shift to right.
      if ((firstByte & mask) == mask) 
      { //One found
        //Set number of bytes in size = i+1 ( we must count the 1 too)
        numBytes = i + 1;
        //exit loop by pushing i out of the limit
        i = 8;
      }
      mask >>>= 1;
    }
    if (numBytes == 0)
      // Invalid size
      return 0;

    //Setup space to store the bits
    byte[] data = new byte[numBytes];

    //Clear the 1 at the front of this byte, all the way to the beginning of the size
    data[0] = (byte)(firstByte & ((0xFF >>> (numBytes))));

    if (numBytes > 1) 
    {
      //Read the rest of the size.
      ArrayCopy.arraycopy(data, 1, source, offset+1, numBytes - 1);
    }

    //Put this into a long
    long size = 0;
    long n = 0;
    for (int i = 0; i < numBytes; i++) 
    {
      n = ((long)data[numBytes - 1 - i] << 56) >>> 56;
      size = size | (n << (8 * i));
    }
    return size;
  }

  /**
   * Reads an Signed EBML code from the DataSource and encodes it into a long.  This size should be
   * cast into an int for actual use as Java only allows upto 32-bit file I/O operations.
   *
   * @return ebml size
   */
  static public long readSignedEBMLCode(byte [] source) 
  {
    return readSignedEBMLCode(source, 0);
  }

  /**
   * Reads an Signed EBML code from the DataSource and encodes it into a long.  This size should be
   * cast into an int for actual use as Java only allows upto 32-bit file I/O operations.
   *
   * @return ebml size
   */
  static public long readSignedEBMLCode(byte [] source, int offset) 
  {
    //Begin loop with byte set to newly read byte.
    byte firstByte = source[offset];
    int numBytes = 0;

    //Begin by counting the bits unset before the first '1'.
    long mask = 0x0080;
    for (int i = 0; i < 8; i++) 
    {
      //Start at left, shift to right.
      if ((firstByte & mask) == mask) 
      { //One found
        //Set number of bytes in size = i+1 ( we must count the 1 too)
        numBytes = i + 1;
        //exit loop by pushing i out of the limit
        i = 8;
      }
      mask >>>= 1;
    }
    if (numBytes == 0)
      // Invalid size
      return 0;

    //Setup space to store the bits
    byte[] data = new byte[numBytes];

    //Clear the 1 at the front of this byte, all the way to the beginning of the size
    data[0] = (byte)(firstByte & ((0xFF >>> (numBytes))));

    if (numBytes > 1) 
    {
      //Read the rest of the size.
      ArrayCopy.arraycopy(data, 1, source, offset+1, numBytes - 1);
    }

    //Put this into a long
    long size = 0;
    long n = 0;
    for (int i = 0; i < numBytes; i++) 
    {
      n = ((long)data[numBytes - 1 - i] << 56) >>> 56;
      size = size | (n << (8 * i));
    }

    // Sign it ;)
    if (numBytes == 1) 
    {
      size -= 63;

    } 
    else if (numBytes == 2) 
    {
      size -= 8191;

    } 
    else if (numBytes == 3) 
    {
      size -= 1048575;

    } 
    else if (numBytes == 4) 
    {
      size -= 134217727;
    }

    return size;
  }

  /**
   * Reads an Signed EBML code from the DataSource and encodes it into a long.  This size should be
   * cast into an int for actual use as Java only allows upto 32-bit file I/O operations.
   *
   * @return ebml size
   */
  static public long readSignedEBMLCode(DataSource source) {

    //Begin loop with byte set to newly read byte.
    byte firstByte = source.readByte();
    int numBytes = 0;

    //Begin by counting the bits unset before the first '1'.
    long mask = 0x0080;
    for (int i = 0; i < 8; i++) {
      //Start at left, shift to right.
      if ((firstByte & mask) == mask) { //One found
        //Set number of bytes in size = i+1 ( we must count the 1 too)
        numBytes = i + 1;
        //exit loop by pushing i out of the limit
        i = 8;
      }
      mask >>>= 1;
    }
    if (numBytes == 0)
      // Invalid size
      return 0;

    //Setup space to store the bits
    byte[] data = new byte[numBytes];

    //Clear the 1 at the front of this byte, all the way to the beginning of the size
    data[0] = (byte)(firstByte & ((0xFF >>> (numBytes))));

    if (numBytes > 1) {
      //Read the rest of the size.
      source.read(data, 1, numBytes - 1);
    }

    //Put this into a long
    long size = 0;
    long n = 0;
    for (int i = 0; i < numBytes; i++) {
      n = ((long)data[numBytes - 1 - i] << 56) >>> 56;
      size = size | (n << (8 * i));
    }

    // Sign it ;)
    if (numBytes == 1) {
      size -= 63;

    } else if (numBytes == 2) {
      size -= 8191;

    } else if (numBytes == 3) {
      size -= 1048575;

    } else if (numBytes == 4) {
      size -= 134217727;
    }

    return size;
  }

  /**
   *Reads an EBML code from the DataSource.
   *
   * @return byte array filled with the ebml size, (size bits included)
   */
  static public byte[] readEBMLCodeAsBytes(DataSource source) {
    //Begin loop with byte set to newly read byte.
    byte firstByte = source.readByte();
    int numBytes = 0;

    //Begin by counting the bits unset before the first '1'.
    long mask = 0x0080;
    for (int i = 0; i < 8; i++) {
      //Start at left, shift to right.
      if ((firstByte & mask) == mask) { //One found
        //Set number of bytes in size = i+1 ( we must count the 1 too)
        numBytes = i + 1;
        //exit loop by pushing i out of the limit
        i = 8;
      }
      mask >>>= 1;
    }
    if (numBytes == 0)
      // Invalid element
      return null;
    //Setup space to store the bits
    byte[] data = new byte[numBytes];

    //Clear the 1 at the front of this byte, all the way to the beginning of the size
    data[0] = (byte)((firstByte));// & ((0xFF >>> (numBytes))));

    if (numBytes > 1) {
      //Read the rest of the size.
      source.read(data, 1, numBytes - 1);
    }
    return data;
  }

}

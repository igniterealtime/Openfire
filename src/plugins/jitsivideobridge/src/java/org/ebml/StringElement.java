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

/**
 * Baisc class for handling an EBML string data type.  This class encapsulates
 * both UTF and ASCII string types and can use any string type supported by
 * the Java platform.
 *
 * @author  John Cannon
 */
public class StringElement
    extends org.ebml.BinaryElement {

  private String charset = "UTF-8";

  /** Creates a new instance of StringElement */
  public StringElement(byte[] typeID) {
    super(typeID);
  }

  public StringElement(byte[] typeID, String encoding) {
    super(typeID);
    charset = encoding;
  }

  private boolean checkForCharsetHack() 
  {
    // Check if we are trying to read UTF-8, if so lets try UTF8.
    // Microsofts Java supports "UTF8" but not "UTF-8"
    if (charset.compareTo("UTF-8") == 0) 
    {
      charset = "UTF8";
      // Let's try again
      return true;
    } 
    else if (charset.compareTo("US-ASCII") == 0) 
    {
      // This is the same story as UTF-8, 
      // If Microsoft is going to hijack Java they should at least support the orignal :>
      charset = "ASCII";
      // Let's try again
      return true;            
    }
    return false;
  }

  public String getValue() {
    try {
      if (data == null)
        throw new java.lang.IllegalStateException("Call readData() before trying to extract the string value.");

      return new String(data, charset);
    }
    catch (java.io.UnsupportedEncodingException ex) {
      if (checkForCharsetHack()) 
      {
        return getValue();
      }
      ex.printStackTrace();
      return "";
    }
  }

  public void setValue(String value) {
    try {
      setData(value.getBytes(charset));
    }
    catch (java.io.UnsupportedEncodingException ex) {
      if (checkForCharsetHack()) 
      {
        setValue(value);
        return;
      }
      ex.printStackTrace();
    }
  }

  public String getEncoding() {
    return charset;
  }
}

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

import java.util.*;


public class ElementType {
  public static short UNKNOWN_ELEMENT = 0;
  public static short MASTER_ELEMENT = 1;
  public static short BINARY_ELEMENT = 2;
  public static short SINTEGER_ELEMENT = 3;
  public static short UINTEGER_ELEMENT = 4;
  public static short FLOAT_ELEMENT = 5;
  public static short STRING_ELEMENT = 6;
  public static short ASCII_STRING_ELEMENT = 7;
  public static short DATE_ELEMENT = 8;
  public static short LAST_ELEMENT_TYPE = 100;

  public String name;
  public short level;
  public byte [] id;
  public short type;
  //public HashMap child;
  public ArrayList<ElementType> children;

  public ElementType() {

  }
  public ElementType(String name, short level, byte [] id, short type, ArrayList<ElementType> children) {
    this.name = name;
    this.level = level;
    this.id = id;
    this.type = type;
    this.children = children;
  }

  public ElementType findElement(byte [] id) {
    if (this.isElement(id))
      return this;

    if (children != null) {
      for (int i = 0; i < children.size(); i++) {
        ElementType entry = (ElementType)children.get(i);
        if (entry.isElement(id))
          return entry;
        entry = entry.findElement(id);
        if (entry != null)
          return entry;
      }
    }
    return null;
  }

  public boolean isElement(byte [] id) {
    return ElementType.compareIDs(this.id, id);
  }

  public static boolean compareIDs(byte[] id1, byte[] id2) {
    if ((id1 == null)
        || (id2 == null)
        || (id1.length != id2.length))
      return false;

    for (int i = 0; i < id1.length; i++) {
      if (id1[i] != id2[i])
        return false;
    }
    return true;
  }

  public Element createElement() {
    Element elem;

    if (this.type == ElementType.MASTER_ELEMENT) {
      elem = new MasterElement(this.id);

    } else if (this.type == ElementType.BINARY_ELEMENT) {
      elem = new BinaryElement(this.id);

    } else if (this.type == ElementType.STRING_ELEMENT) {
      elem = new StringElement(this.id);

    } else if (this.type == ElementType.ASCII_STRING_ELEMENT) {
      elem = new StringElement(this.id, "US-ASCII");

    } else if (this.type == ElementType.SINTEGER_ELEMENT) {
      elem = new SignedIntegerElement(this.id);

    } else if (this.type == ElementType.UINTEGER_ELEMENT) {
      elem = new UnsignedIntegerElement(this.id);

    } else if (this.type == ElementType.FLOAT_ELEMENT) {
      elem = new FloatElement(this.id);

    } else if (this.type == ElementType.DATE_ELEMENT) {
      elem = new DateElement(this.id);

    } else if (this.type == ElementType.UNKNOWN_ELEMENT) {
      elem = new BinaryElement(this.id);

    } else {
      return null;
    }
    elem.setElementType(this);
    return elem;
  }
}
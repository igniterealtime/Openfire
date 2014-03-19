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
import java.util.*;

public class MasterElement extends Element {
  protected long usedSize;
  protected ArrayList<Element> children = new ArrayList<Element>();

  public MasterElement(byte[] type) {
    super(type);
    usedSize = 0;
  }

  public Element readNextChild(EBMLReader reader) {
    if (usedSize >= this.getSize())
      return null;

    Element elem = reader.readNextElement();
    if (elem == null)
      return null;

    elem.setParent(this);

    usedSize += elem.getTotalSize();

    return elem;
  }

  /* Skip the element data */
  public void skipData(DataSource source) {
    // Skip the child elements
    source.skip(size-usedSize);
  }

  public long writeData(DataWriter writer) 
  {
    long len = 0;
    for (int i = 0; i < children.size(); i++) 
    {
      Element elem = (Element)children.get(i);
      len += elem.writeElement(writer);
    }
    return len;
  }

  public void addChildElement(Element elem) 
  {
    children.add(elem);
    size += elem.getTotalSize();
  }
}
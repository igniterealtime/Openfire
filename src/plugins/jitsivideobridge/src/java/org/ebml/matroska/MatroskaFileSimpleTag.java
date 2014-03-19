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
package org.ebml.matroska;

import java.util.ArrayList;

public class MatroskaFileSimpleTag 
{
  public String Name;
  public String Value;
  public ArrayList<MatroskaFileSimpleTag> Children = new ArrayList<MatroskaFileSimpleTag>();

  public String toString(int depth) 
  {
    String s = new String();
    String depthIndent = new String();
    for (int d = 0; d < depth; d++)
      depthIndent += "\t";

    s += depthIndent + "SimpleTag\n";
    s += depthIndent + "\tName: " + Name + "\n";
    s += depthIndent + "\tValue: " + Value + "\n";

    depth++;
    for (int t = 0; t < Children.size(); t++) 
    {
      s += ((MatroskaFileSimpleTag)Children.get(t)).toString(depth);
    }

    return s;
  }
}
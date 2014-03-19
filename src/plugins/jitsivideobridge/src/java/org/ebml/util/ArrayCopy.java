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
package org.ebml.util;

/**
 * Static methods for copying arrays
 */
public class ArrayCopy
{
  /**
   * Private construct as this class only has static methods
   */
  private ArrayCopy()
  {

  }

  public static void arraycopy(byte [] src, int src_offset, byte [] dest, int dest_offset, int count) 
  {
    for (int i = 0; i < count; i++) 
    {
      dest[dest_offset + i] = src[src_offset + i];
    }
  }

  public static void arraycopy(char [] src, int src_offset, char [] dest, int dest_offset, int count) 
  {
    for (int i = 0; i < count; i++) 
    {
      dest[dest_offset + i] = src[src_offset + i];
    }
  }

  public static void arraycopy(short [] src, int src_offset, short [] dest, int dest_offset, int count) 
  {
    for (int i = 0; i < count; i++) 
    {
      dest[dest_offset + i] = src[src_offset + i];
    }
  }

  public static void arraycopy(int [] src, int src_offset, int [] dest, int dest_offset, int count) 
  {
    for (int i = 0; i < count; i++) 
    {
      dest[dest_offset + i] = src[src_offset + i];
    }
  }

  public static void arraycopy(long [] src, int src_offset, long [] dest, int dest_offset, int count) 
  {
    for (int i = 0; i < count; i++) 
    {
      dest[dest_offset + i] = src[src_offset + i];
    }
  }

  public static void arraycopy(Object [] src, int src_offset, Object [] dest, int dest_offset, int count) 
  {
    for (int i = 0; i < count; i++) 
    {
      dest[dest_offset + i] = src[src_offset + i];
    }
  }
}

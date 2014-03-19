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
package org.ebml.io;

/**
 * Defines the interface used for custom <code>DataWriter</code>'s.  A
 * <code>DataWriter</code> provides methods of writing bytes individually or
 * in arrays.  These basic functions must be defined in any <code>DataWriter</code>
 * objects to be used with the <code>EBMLWriter</code>.
 *
 * @author Jory Stone
 */
public interface DataWriter extends DataSeekable 
{
  public int write(byte b);

  public int write(byte[] buff);

  public int write(byte[] buff, int offset, int length);
}

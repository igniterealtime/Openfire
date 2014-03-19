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

/**
 * Summary description for EBMLWriter.
 */
public class EBMLWriter
{
  protected DataWriter writer;

  /** Creates a new <code>EBMLReader</code> reading from the <code>DataSource
   * source</code>. The <code>DocType doc</code> is used to validate the
   * document.
   *
   * @param source DataSource to read from
   * @param doc DocType to use to validate the docment
   */
  public EBMLWriter(DataWriter writer) 
  {
    this.writer = writer;
  }

  public long writeElement(Element elem) 
  {
    return elem.writeHeaderData(writer) + elem.writeData(writer);
  }
}

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

public class DateElement extends SignedIntegerElement {
  //const uint64 EbmlDate::UnixEpochDelay = 978307200; // 2001/01/01 00:00:00 UTC
  public static long UnixEpochDelay = 978307200; // 2001/01/01 00:00:00 UTC
  private static int MIN_SIZE_LENGTH = 8;

  public DateElement(byte[] type) {
    super(type);
  }

  /**
   * Set the Date of this element
   * @param value Date to set
   */
  public void setDate(Date value) {
    long val = (value.getTime() - UnixEpochDelay) * 1000000000;
    setData(packInt(val, MIN_SIZE_LENGTH));
  }

  /**
   * Get the Date value of this element
   * @return Date of this element
   */
  public Date getDate() {
    /*
    Date begin = new Date(0);
    Date start = new Date(1970, 1, 1, 0, 0, 0);
    Date end = new Date(2001, 1, 1, 0, 0, 0);
    long diff0 = begin.getTime();
    long diff1 = start.getTime();
    long diff2 = end.getTime();
    long diff3 = Date.UTC(2001, 1, 1, 0, 0, 0) - Date.UTC(1970, 1, 1, 0, 0, 0);
    */
    long val = getValue();;      
    val = val / 1000000000 + UnixEpochDelay;
    return new Date(val);
  }

  /**
   * It's not recommended to use this method.
   * Use the setDate(Date) method instead.
   */
  public void setValue(long value) 
  {
    setData(packInt(value, MIN_SIZE_LENGTH));
  }
}
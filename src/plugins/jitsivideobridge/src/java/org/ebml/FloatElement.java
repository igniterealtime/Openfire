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

import java.io.*;

public class FloatElement extends BinaryElement {
  public FloatElement(byte[] type) {
    super(type);
  }

  /**
   * Set the float value of this element
   * @param value Float value to set
   * @throws ArithmeticException if the float value is larger than Double.MAX_VALUE
   */
  public void setValue(double value) {
    try {
      if (value < Float.MAX_VALUE) {
        ByteArrayOutputStream bIO = new ByteArrayOutputStream(4);
        DataOutputStream dIO = new DataOutputStream(bIO);
        dIO.writeFloat((float)value);

        setData(bIO.toByteArray());

      } else if (value < Double.MAX_VALUE) {
        ByteArrayOutputStream bIO = new ByteArrayOutputStream(8);
        DataOutputStream dIO = new DataOutputStream(bIO);
        dIO.writeDouble(value);

        setData(bIO.toByteArray());

      } else {
        throw new ArithmeticException(
            "80-bit floats are not supported, BTW How did you create such a large float in Java?");
      }
    } catch (IOException ex) {
      return;
    }
  }

  /**
   * Get the float value of this element
   * @return Float value of this element
   * @throws ArithmeticException for 80-bit or 10-byte floats. AFAIK Java doesn't support them
   */
  public double getValue() {
    try {
      if (size == 4) {
        float value = 0;
        ByteArrayInputStream bIS = new ByteArrayInputStream(data);
        DataInputStream dIS = new DataInputStream(bIS);
        value = dIS.readFloat();
        return value;

      } else if (size == 8) {
        double value = 0;
        ByteArrayInputStream bIS = new ByteArrayInputStream(data);
        DataInputStream dIS = new DataInputStream(bIS);
        value = dIS.readDouble();
        return value;

      } else {
        throw new ArithmeticException(
            "80-bit floats are not supported");
      }
    } catch (IOException ex) {
      return 0;
    }
  }
}
/*
 * Copyright (C) 2013 Burton Alexander
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 * 
 */
package asia.stampy.common.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.codec.binary.Base64;

import asia.stampy.common.StampyLibrary;

/**
 * Convenience class to encapsulate the serialize/deserialize functionality.
 */
@StampyLibrary(libraryName="stampy-core")
public class SerializationUtils {

  private static Lock SERIALIZE_LOCK = new ReentrantLock(true);
  private static Lock DESERIALIZE_LOCK = new ReentrantLock(true);

  /**
   * Serialize base64.
   * 
   * @param o
   *          the o
   * @return the string
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  public static String serializeBase64(Object o) throws IOException {
    SERIALIZE_LOCK.lock();
    try {
      if (o instanceof byte[]) return Base64.encodeBase64String((byte[]) o);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);

      oos.writeObject(o);

      return Base64.encodeBase64String(baos.toByteArray());
    } finally {
      SERIALIZE_LOCK.unlock();
    }
  }

  /**
   * Deserialize base64.
   * 
   * @param s
   *          the s
   * @return the object
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   * @throws ClassNotFoundException
   *           the class not found exception
   */
  public static Object deserializeBase64(String s) throws IOException, ClassNotFoundException {
    DESERIALIZE_LOCK.lock();
    try {
      byte[] bytes = Base64.decodeBase64(s);

      ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));

      return ois.readObject();
    } finally {
      DESERIALIZE_LOCK.unlock();
    }
  }

}

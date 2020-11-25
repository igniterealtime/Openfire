/*
 * Copyright (C) 2020 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.util.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default serialization strategy.
 *
 * @author Tom Evans
 * @author Gaston Dombiak
 */
// Note that this implementation is heavily based on the implementation provided by the 'clustering' plugin in
// org.jivesoftware.openfire.plugin.util.cache.ClusterExternalizableUtil - if changes are made to either implementation,
// it's likely that the other implementation needs a similar change. See https://igniterealtime.atlassian.net/browse/OF-1984
public class DefaultExternalizableUtil implements ExternalizableUtilStrategy {

    /**
     * Writes a Map of String key and value pairs. This method handles the
     * case when the Map is <tt>null</tt>.
     *
     * @param out       the output stream.
     * @param stringMap the Map of String key/value pairs.
     * @throws java.io.IOException if an error occurs.
     */
    public void writeStringMap(DataOutput out, Map<String, String> stringMap) throws IOException {
        writeObject(out, stringMap);
    }

    /**
     * Reads a Map of String key and value pairs. This method will return
     * <tt>null</tt> if the Map written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return a Map of String key/value pairs.
     * @throws IOException if an error occurs.
     */
    public Map<String, String> readStringMap(DataInput in) throws IOException {
        return (Map<String, String>) readObject(in);
    }

    /**
     * Writes a Map of String key and Set of Strings value pairs. This method DOES NOT handle the
     * case when the Map is <tt>null</tt>.
     *
     * @param out       the output stream.
     * @param map       the Map of String key and Set of Strings value pairs.
     * @throws java.io.IOException if an error occurs.
     */
    public void writeStringsMap(DataOutput out, Map<String, Set<String>> map) throws IOException {
        writeObject(out, map);
    }

    /**
     * Reads a Map of String key and Set of Strings value pairs.
     *
     * @param in the input stream.
     * @param map a Map of String key and Set of Strings value pairs.
     * @return number of elements added to the collection.
     * @throws IOException if an error occurs.
     */
    public int readStringsMap(DataInput in, Map<String, Set<String>> map) throws IOException {
        Map<String, Set<String>> result = (Map<String, Set<String>>) readObject(in);
        if (result == null) return 0;
        map.putAll(result);
        return result.size();
    }

    /**
     * Writes a Map of Long key and Integer value pairs. This method handles
     * the case when the Map is <tt>null</tt>.
     *
     * @param out the output stream.
     * @param map the Map of Long key/Integer value pairs.
     * @throws IOException if an error occurs.
     */
    public void writeLongIntMap(DataOutput out, Map<Long, Integer> map) throws IOException {
        writeObject(out, map);
    }

    /**
     * Reads a Map of Long key and Integer value pairs. This method will return
     * <tt>null</tt> if the Map written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return a Map of Long key/Integer value pairs.
     * @throws IOException if an error occurs.
     */
    public Map<Long, Integer> readLongIntMap(DataInput in) throws IOException {
        return (Map<Long, Integer>) readObject(in);
    }

    /**
     * Writes a List of Strings. This method handles the case when the List is
     * <tt>null</tt>.
     *
     * @param out        the output stream.
     * @param stringList the List of Strings.
     * @throws IOException if an error occurs.
     */
    public void writeStringList(DataOutput out, List<String> stringList) throws IOException {
        writeObject(out, stringList);
    }

    /**
     * Reads a List of Strings. This method will return <tt>null</tt> if the List
     * written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return a List of Strings.
     * @throws IOException if an error occurs.
     */
    public List<String> readStringList(DataInput in) throws IOException {
        return (List<String>) readObject(in);
    }

    /**
     * Writes an array of long values. This method handles the case when the
     * array is <tt>null</tt>.
     *
     * @param out   the output stream.
     * @param array the array of long values.
     * @throws IOException if an error occurs.
     */
    public void writeLongArray(DataOutput out, long [] array) throws IOException {
        writeObject(out, array);
    }

    /**
     * Reads an array of long values. This method will return <tt>null</tt> if
     * the array written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @return an array of long values.
     * @throws IOException if an error occurs.
     */
    public long [] readLongArray(DataInput in) throws IOException {
        return (long []) readObject(in);
    }

    public void writeLong(DataOutput out, long value) throws IOException {
        writeObject(out, value);
    }

    public long readLong(DataInput in) throws IOException {
        return (Long) readObject(in);
    }

    public void writeByteArray(DataOutput out, byte[] value) throws IOException {
        writeObject(out, value);
    }

    public byte[] readByteArray(DataInput in) throws IOException {
        return (byte []) readObject(in);
    }

    public void writeInt(DataOutput out, int value) throws IOException {
        writeObject(out, value);
    }

    public int readInt(DataInput in) throws IOException {
        return (Integer) readObject(in);
    }

    public void writeBoolean(DataOutput out, boolean value) throws IOException {
        writeObject(out, value);
    }

    public boolean readBoolean(DataInput in) throws IOException {
        return (Boolean) readObject(in);
    }

    public void writeSerializable(DataOutput out, Serializable value) throws IOException {
        writeObject(out, value);
    }

    public Serializable readSerializable(DataInput in) throws IOException {
        return (Serializable) readObject(in);
    }

    public void writeSafeUTF(DataOutput out, String value) throws IOException {
        writeObject(out, value);
    }

    public String readSafeUTF(DataInput in) throws IOException {
        return (String) readObject(in);
    }

    /**
     * Writes a collection of Externalizable objects. The collection passed as a parameter
     * must be a collection and not a <tt>null</null> value.
     *
     * @param out   the output stream.
     * @param value the collection of Externalizable objects. This value must not be null.
     * @throws IOException if an error occurs.
     */
    public void writeExternalizableCollection(DataOutput out, Collection<? extends Externalizable> value) throws IOException {
        writeObject(out, value);
    }

    /**
     * Writes a collection of Serializable objects. The collection passed as a parameter
     * must be a collection and not a <tt>null</null> value.
     *
     * @param out   the output stream.
     * @param value the collection of Serializable objects. This value must not be null.
     * @throws IOException if an error occurs.
     */
    public void writeSerializableCollection(DataOutput out, Collection<? extends Serializable> value) throws IOException {
        writeObject(out, value);
    }

    /**
     * Reads a collection of Externalizable objects and adds them to the collection passed as a parameter. The
     * collection passed as a parameter must be a collection and not a <tt>null</null> value.
     *
     * @param in the input stream.
     * @param value the collection of Externalizable objects. This value must not be null.
     * @param loader class loader to use to build elements inside of the serialized collection.
     * @throws IOException if an error occurs.
     * @return the number of elements added to the collection.
     */
    public int readExternalizableCollection(DataInput in, Collection<? extends Externalizable> value, ClassLoader loader) throws IOException {
        Collection<Externalizable> result = (Collection<Externalizable>) readObject(in);
        if (result == null) return 0;
        ((Collection<Externalizable>)value).addAll(result);
        return result.size();
    }

    /**
     * Reads a collection of Serializable objects and adds them to the collection passed as a parameter. The
     * collection passed as a parameter must be a collection and not a <tt>null</null> value.
     *
     * @param in the input stream.
     * @param value the collection of Serializable objects. This value must not be null.
     * @param loader class loader to use to build elements inside of the serialized collection.
     * @throws IOException if an error occurs.
     * @return the number of elements added to the collection.
     */
    public int readSerializableCollection(DataInput in, Collection<? extends Serializable> value, ClassLoader loader) throws IOException {
        Collection<Serializable> result = (Collection<Serializable>) readObject(in);
        if (result == null) return 0;
        ((Collection<Serializable>)value).addAll(result);
        return result.size();
    }

    /**
     * Writes a Map of String key and value pairs. This method handles the
     * case when the Map is <tt>null</tt>.
     *
     * @param out       the output stream.
     * @param map       the Map of String key and Externalizable value pairs.
     * @throws java.io.IOException if an error occurs.
     */
    public void writeExternalizableMap(DataOutput out, Map<String, ? extends Externalizable> map) throws IOException {
        writeObject(out, map);
    }

    /**
     * Writes a Map of Serializable key and value pairs. This method handles the
     * case when the Map is <tt>null</tt>.
     *
     * @param out       the output stream.
     * @param map       the Map of Serializable key and value pairs.
     * @throws java.io.IOException if an error occurs.
     */
    public void writeSerializableMap(DataOutput out, Map<? extends Serializable, ? extends Serializable> map) throws IOException {
        writeObject(out, map);
    }

    /**
     * Reads a Map of String key and value pairs. This method will return
     * <tt>null</tt> if the Map written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @param map a Map of String key and Externalizable value pairs.
     * @param loader class loader to use to build elements inside of the serialized collection.
     * @throws IOException if an error occurs.
     * @return the number of elements added to the collection.
     */
    public int readExternalizableMap(DataInput in, Map<String, ? extends Externalizable> map, ClassLoader loader) throws IOException {
        Map<String, Externalizable> result = (Map<String, Externalizable>) readObject(in);
        if (result == null) return 0;
        ((Map<String, Externalizable>)map).putAll(result);
        return result.size();
    }

    /**
     * Reads a Map of Serializable key and value pairs. This method will return
     * <tt>null</tt> if the Map written to the stream was <tt>null</tt>.
     *
     * @param in the input stream.
     * @param map a Map of Serializable key and value pairs.
     * @param loader class loader to use to build elements inside of the serialized collection.
     * @throws IOException if an error occurs.
     * @return the number of elements added to the collection.
     */
    public int readSerializableMap(DataInput in, Map<? extends Serializable, ? extends Serializable> map, ClassLoader loader) throws IOException {
        Map<String, Serializable> result = (Map<String, Serializable>) readObject(in);
        if (result == null) return 0;
        ((Map<String, Serializable>)map).putAll(result);
        return result.size();
    }

    public void writeStrings(DataOutput out, Collection<String> collection) throws IOException {
        writeObject(out, collection);
    }

    public int readStrings(DataInput in, Collection<String> collection) throws IOException {
        Collection<String> result = (Collection<String>) readObject(in);
        if (result == null) return 0;
        collection.addAll(result);
        return result.size();
    }

    // serialization helpers

    public static void writeObject(DataOutput out, Object obj) throws IOException {
        if (obj == null) {
            out.writeByte(0);
        } else if (obj instanceof Long) {
            out.writeByte(1);
            out.writeLong((Long) obj);
        } else if (obj instanceof Integer) {
            out.writeByte(2);
            out.writeInt((Integer) obj);
        } else if (obj instanceof String) {
            out.writeByte(3);
            out.writeUTF((String) obj);
        } else if (obj instanceof Double) {
            out.writeByte(4);
            out.writeDouble((Double) obj);
        } else if (obj instanceof Float) {
            out.writeByte(5);
            out.writeFloat((Float) obj);
        } else if (obj instanceof Boolean) {
            out.writeByte(6);
            out.writeBoolean((Boolean) obj);
        } else if (obj instanceof Date) {
            out.writeByte(8);
            out.writeLong(((Date) obj).getTime());
        } else if(obj instanceof byte[]){
            out.writeByte(9);
            // write length
            out.writeInt(((byte[]) obj).length);
            // write byte array
            out.write((byte[]) obj);
        } else {
            out.writeByte(10);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(obj);
            oos.close();
            byte[] buf = bos.toByteArray();
            out.writeInt(buf.length);
            out.write(buf);
        }
    }

    public static Object readObject(DataInput in) throws IOException {
        byte type = in.readByte();
        if (type == 0) {
            return null;
        } else if (type == 1) {
            return in.readLong();
        } else if (type == 2) {
            return in.readInt();
        } else if (type == 3) {
            return in.readUTF();
        } else if (type == 4) {
            return in.readDouble();
        } else if (type == 5) {
            return in.readFloat();
        } else if (type == 6) {
            return in.readBoolean();
        } else if (type == 8) {
            return new Date(in.readLong());
        } else if (type == 9) {
            byte[] buf = new byte[in.readInt()];
            in.readFully(buf);
            return buf;
        } else if (type == 10) {
            int len = in.readInt();
            byte[] buf = new byte[len];
            in.readFully(buf);
            ObjectInputStream oin = newObjectInputStream(new ByteArrayInputStream(buf));
            try {
                return oin.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            } finally {
                oin.close();
            }
        } else {
            throw new IOException("Unknown object type=" + type);
        }
    }

    public static ObjectInputStream newObjectInputStream(final InputStream in) throws IOException {
        return new ObjectInputStream(in) {
            @Override
            protected Class<?> resolveClass(final ObjectStreamClass desc) throws ClassNotFoundException {
                return loadClass(desc.getName());
            }
        };
    }

    public static Class<?> loadClass(final String className) throws ClassNotFoundException {
        return loadClass(null, className);
    }

    public static Class<?> loadClass(final ClassLoader classLoader, final String className) throws ClassNotFoundException {
        if (className == null) {
            throw new IllegalArgumentException("ClassName cannot be null!");
        }
        if (className.length() <= MAX_PRIM_CLASSNAME_LENGTH && Character.isLowerCase(className.charAt(0))) {
            for (int i = 0; i < PRIMITIVE_CLASSES_ARRAY.length; i++) {
                if (className.equals(PRIMITIVE_CLASSES_ARRAY[i].getName())) {
                    return PRIMITIVE_CLASSES_ARRAY[i];
                }
            }
        }
        ClassLoader theClassLoader = classLoader;

        if (theClassLoader == null) {
            theClassLoader = Thread.currentThread().getContextClassLoader();
        }
        if (theClassLoader != null) {
            if (className.startsWith("[")) {
                return Class.forName(className, true, theClassLoader);
            } else {
                return theClassLoader.loadClass(className);
            }
        }
        return Class.forName(className);
    }

    private static final Class[] PRIMITIVE_CLASSES_ARRAY = {int.class, long.class, boolean.class, byte.class,
        float.class, double.class, byte.class, char.class, short.class, void.class};
    private static final int MAX_PRIM_CLASSNAME_LENGTH = 7; // boolean.class.getName().length();


}

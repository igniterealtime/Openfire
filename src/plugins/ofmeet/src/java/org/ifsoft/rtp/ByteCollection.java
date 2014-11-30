package org.ifsoft.rtp;

import java.util.ArrayList;
import java.util.Iterator;


public class ByteCollection
{

    private ArrayList arrayList;

    public void add(byte b)
    {
        arrayList.add(Byte.valueOf(b));
    }

    public void add(int b)
    {
        arrayList.add(Byte.valueOf((byte)b));
    }

    public void addRange(byte buffer[])
    {
        byte arr$[] = buffer;
        int len$ = arr$.length;
        for(int i$ = 0; i$ < len$; i$++)
        {
            byte b = arr$[i$];
            add(b);
        }

    }

    public void addRange(ByteCollection collection)
    {
        byte b;
        for(Iterator i$ = collection.getList().iterator(); i$.hasNext(); add(b))
            b = ((Byte)i$.next()).byteValue();

    }

    public ByteCollection()
    {
        arrayList = new ArrayList();
    }

    public ByteCollection(byte buffer[])
    {
        arrayList = new ArrayList();
        addRange(buffer);
    }

    public byte get(Integer index)
    {
        return ((Byte)arrayList.get(index.intValue())).byteValue();
    }

    public Integer getCount()
    {
        return Integer.valueOf(arrayList.size());
    }

    ArrayList getList()
    {
        return arrayList;
    }

    public byte[] getRange(Integer index, Integer count)
    {
        byte range[] = new byte[count.intValue()];
        for(int i = 0; i < count.intValue(); i++)
            range[i] = ((Byte)arrayList.get(index.intValue() + i)).byteValue();

        return range;
    }

    public void insertRange(Integer index, byte buffer[])
    {
        for(int i = 0; i < buffer.length; i++)
            arrayList.add(index.intValue() + i, Byte.valueOf(buffer[i]));

    }

    public void insertRange(Integer index, ByteCollection collection)
    {
        for(int i = 0; i < collection.getCount().intValue(); i++)
            arrayList.add(index.intValue() + i, Byte.valueOf(collection.get(Integer.valueOf(i))));

    }

    public void removeRange(Integer index, Integer count)
    {
        removeRange(arrayList, index.intValue(), count.intValue());
    }

    public byte[] toArray()
    {
        byte array[] = new byte[arrayList.size()];
        for(int i = 0; i < array.length; i++)
            array[i] = ((Byte)arrayList.get(i)).byteValue();

        return array;
    }

    public void removeRange(ArrayList array, int index, int count)
    {
        for(int i = 0; i < count; i++)
            array.remove(index);

    }
}

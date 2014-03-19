package org.ifsoft;

import java.util.ArrayList;
import java.util.Iterator;


public class ByteCollection
{
    private ArrayList _list;

    public void add(byte b)
    {
        _list.add(Byte.valueOf(b));
    }

    public void add(int b)
    {
        _list.add(Byte.valueOf((byte)b));
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
        _list = new ArrayList();
    }

    public ByteCollection(byte buffer[])
    {
        _list = new ArrayList();
        addRange(buffer);
    }

    public byte get(Integer index)
    {
        return ((Byte)_list.get(index.intValue())).byteValue();
    }

    public Integer getCount()
    {
        return Integer.valueOf(_list.size());
    }

    ArrayList getList()
    {
        return _list;
    }

    public byte[] getRange(Integer index, Integer count)
    {
        byte range[] = new byte[count.intValue()];
        for(int i = 0; i < count.intValue(); i++)
            range[i] = ((Byte)_list.get(index.intValue() + i)).byteValue();

        return range;
    }

    public void insertRange(Integer index, byte buffer[])
    {
        for(int i = 0; i < buffer.length; i++)
            _list.add(index.intValue() + i, Byte.valueOf(buffer[i]));

    }

    public void insertRange(Integer index, ByteCollection collection)
    {
        for(int i = 0; i < collection.getCount().intValue(); i++)
            _list.add(index.intValue() + i, Byte.valueOf(collection.get(Integer.valueOf(i))));

    }

    public void removeRange(Integer index, Integer count)
    {
        ArrayListExtensions.removeRange(_list, index.intValue(), count.intValue());
    }

    public byte[] toArray()
    {
        byte array[] = new byte[_list.size()];
        for(int i = 0; i < array.length; i++)
            array[i] = ((Byte)_list.get(i)).byteValue();

        return array;
    }
}

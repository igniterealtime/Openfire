package org.ifsoft;

import java.util.*;

public class ArrayListExtensions
{

    public ArrayListExtensions()
    {
    }

    public static Integer getCount(ArrayList array)
    {
        return Integer.valueOf(array.size());
    }

    public static ArrayList getItem(ArrayList array)
    {
        return array;
    }

    public static void copyTo(ArrayList array, Object target[], int index)
    {
        for(int i = 0; i < array.size(); i++)
            target[index + i] = array.get(i);

    }

    public static void insert(ArrayList array, int index, Object value)
    {
        array.add(index, value);
    }

    public static void removeAt(ArrayList array, int index)
    {
        array.remove(index);
    }

    public static ArrayList createArray(Object elements[])
    {
        ArrayList array = new ArrayList(elements.length);
        Object arr$[] = elements;
        int len$ = arr$.length;
        for(int i$ = 0; i$ < len$; i$++)
        {
            Object element = arr$[i$];
            array.add(element);
        }

        return array;
    }

    public static ArrayList createArray(Iterable elements)
    {
        ArrayList array = new ArrayList();
        Object element;
        for(Iterator i$ = elements.iterator(); i$.hasNext(); array.add(element))
            element = i$.next();

        return array;
    }

    public static void addRange(ArrayList array, Iterable elements)
    {
        Object element;
        for(Iterator i$ = elements.iterator(); i$.hasNext(); array.add(element))
            element = i$.next();

    }

    public static void addRange(ArrayList array, Object elements[])
    {
        Object arr$[] = elements;
        int len$ = arr$.length;
        for(int i$ = 0; i$ < len$; i$++)
        {
            Object element = arr$[i$];
            array.add(element);
        }

    }

    public static ArrayList getRange(ArrayList array, int index, int count)
    {
        ArrayList sublist = new ArrayList(count);
        for(int i = 0; i < count; i++)
            sublist.add(array.get(index + i));

        return sublist;
    }

    public static void insertRange(ArrayList array, int index, Iterable elements)
    {
        int i = 0;
        for(Iterator i$ = elements.iterator(); i$.hasNext();)
        {
            Object element = i$.next();
            array.add(index + i, element);
            i++;
        }

    }

    public static void insertRange(ArrayList array, int index, Object elements[])
    {
        int i = 0;
        Object arr$[] = elements;
        int len$ = arr$.length;
        for(int i$ = 0; i$ < len$; i$++)
        {
            Object element = arr$[i$];
            array.add(index + i, element);
            i++;
        }

    }

    public static void removeRange(ArrayList array, int index, int count)
    {
        for(int i = 0; i < count; i++)
            array.remove(index);

    }
}

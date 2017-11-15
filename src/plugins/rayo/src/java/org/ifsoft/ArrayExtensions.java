package org.ifsoft;

public class ArrayExtensions
{
    public ArrayExtensions()
    {
    }

    public static Integer getLength(Object array[])
    {
        return Integer.valueOf(array.length);
    }

    public static void reverse(Object array[])
    {
        for(int i = 0; i < array.length / 2; i++)
        {
            Object temp = array[i];
            array[i] = array[array.length - i - 1];
            array[array.length - i - 1] = temp;
        }

    }

    public static void copy(Object source[], int sourceIndex, Object destination[], int destinationIndex, int length)
    {
        for(int i = 0; i < length; i++)
            destination[destinationIndex + i] = source[sourceIndex + i];

    }
}

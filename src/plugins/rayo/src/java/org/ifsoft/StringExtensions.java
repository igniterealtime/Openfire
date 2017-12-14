package org.ifsoft;

import java.util.ArrayList;

public class StringExtensions
{
    public static String empty = "";

    public static String concat(Object strs[])
    {
        StringBuilder sb = new StringBuilder();
        Object arr$[] = strs;
        int len$ = arr$.length;
        for(int i$ = 0; i$ < len$; i$++)
        {
            Object s = arr$[i$];
            sb.append(s.toString());
        }

        return sb.toString();
    }

    public static String concat(String s1)
    {
        return s1;
    }

    public static String concat(String s1, String s2)
    {
        return (new StringBuilder()).append(s1).append(s2).toString();
    }

    public static String concat(String s1, String s2, String s3)
    {
        return (new StringBuilder()).append(s1).append(s2).append(s3).toString();
    }

    public static String concat(String s1, String s2, String s3, String s4)
    {
        return (new StringBuilder()).append(s1).append(s2).append(s3).append(s4).toString();
    }

    public static String join(String separator, String array[])
    {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < array.length; i++)
        {
            if(i > 0)
                sb.append(separator);
            sb.append(array[i]);
        }

        return sb.toString();
    }

    public static String[] split(String s, Character chars[])
    {
        ArrayList splits = new ArrayList();
        if(chars.length > 0)
        {
            splits.add(s);
            Character arr$[] = chars;
            int len$ = arr$.length;
            for(int i$ = 0; i$ < len$; i$++)
            {
                Character c = arr$[i$];
                for(int i = 0; i < splits.size(); i++)
                {
                    String subsplit[] = ((String)splits.get(i)).split(c.toString());
                    splits.remove(i);
                    int j;
                    for(j = 0; j < subsplit.length; j++)
                        splits.add(i + j, subsplit[j]);

                    i += j - 1;
                }

            }

        } else
        {
            splits.add(s);
        }
        return (String[])splits.toArray(new String[0]);
    }

    public static Boolean isNullOrEmpty(String s)
    {
        return Boolean.valueOf(s == null || s.equals(""));
    }

    public static Integer indexOf(String str, String s)
    {
        return Integer.valueOf(str.indexOf(s));
    }

    public static Integer indexOf(String str, String s, StringComparison stringComparison)
    {
        if(stringComparison == StringComparison.CurrentCultureIgnoreCase || stringComparison == StringComparison.InvariantCultureIgnoreCase || stringComparison == StringComparison.OrdinalIgnoreCase)
            return Integer.valueOf(str.toLowerCase().indexOf(s.toLowerCase()));
        else
            return Integer.valueOf(str.indexOf(s));
    }

    public static Boolean startsWith(String str, String s, StringComparison stringComparison)
    {
        if(stringComparison == StringComparison.CurrentCultureIgnoreCase || stringComparison == StringComparison.InvariantCultureIgnoreCase || stringComparison == StringComparison.OrdinalIgnoreCase)
            return Boolean.valueOf(str.toLowerCase().startsWith(s.toLowerCase()));
        else
            return Boolean.valueOf(str.startsWith(s));
    }

    public static Boolean endsWith(String str, String s, StringComparison stringComparison)
    {
        if(stringComparison == StringComparison.CurrentCultureIgnoreCase || stringComparison == StringComparison.InvariantCultureIgnoreCase || stringComparison == StringComparison.OrdinalIgnoreCase)
            return Boolean.valueOf(str.toLowerCase().endsWith(s.toLowerCase()));
        else
            return Boolean.valueOf(str.endsWith(s));
    }

    public static String format(String format, Object args[])
    {
        switch(args.length)
        {
        case 0: // '\0'
            return format;

        case 1: // '\001'
            return format(format, (String)args[0]);

        case 2: // '\002'
            return format(format, (String)args[0], (String)args[1]);

        case 3: // '\003'
            return format(format, (String)args[0], (String)args[1], (String)args[2]);

        case 4: // '\004'
        default:
            return format(format, (String)args[0], (String)args[1], (String)args[2], (String)args[3]);
        }
    }

    public static String format(String format, Object arg0)
    {
        return String.format(reformatNetFormat(format, 1), new Object[] {
            arg0
        });
    }

    public static String format(String format, Object arg0, Object arg1)
    {
        return String.format(reformatNetFormat(format, 2), new Object[] {
            arg0, arg1
        });
    }

    public static String format(String format, Object arg0, Object arg1, Object arg2)
    {
        return String.format(reformatNetFormat(format, 3), new Object[] {
            arg0, arg1, arg2
        });
    }

    public static String format(String format, Object arg0, Object arg1, Object arg2, Object arg3)
    {
        return String.format(reformatNetFormat(format, 4), new Object[] {
            arg0, arg1, arg2, arg3
        });
    }

    public static String reformatNetFormat(String format, int count)
    {
        String reformat = format;
        for(int i = 0; i < count; i++)
            reformat = reformat.replace(String.format("{%d}", new Object[] {
                Integer.valueOf(i)
            }), String.format("%%%d$s", new Object[] {
                Integer.valueOf(i + 1)
            }));

        return reformat;
    }

    public static String toLower(String s)
    {
        return s.toLowerCase();
    }

    public static Integer getLength(String str)
    {
        return Integer.valueOf(str.length());
    }

    public static ArrayList getChars(String str)
    {
        ArrayList chars = new ArrayList(str.length());
        for(int i = 0; i < str.length(); i++)
            chars.add(i, Character.valueOf(str.charAt(i)));

        return chars;
    }

    public static String substring(String str, int startIndex, int length)
    {
        return str.substring(startIndex, startIndex + length);
    }

    public static String trimEnd(String str, Character chars[])
    {
        StringBuilder s = new StringBuilder();
        Character arr$[] = chars;
        int len$ = arr$.length;
        for(int i$ = 0; i$ < len$; i$++)
        {
            Character c = arr$[i$];
            s.append(c);
        }

        return str.replaceAll((new StringBuilder()).append("[").append(s.toString()).append("]+$").toString(), "");
    }
}

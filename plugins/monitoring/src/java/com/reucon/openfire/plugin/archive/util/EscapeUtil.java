package com.reucon.openfire.plugin.archive.util;

public class EscapeUtil
{
    public static String escapeHtml(String source)
    {

        int terminatorIndex;
        if (source == null)
        {
            return null;
        }
        StringBuffer result = new StringBuffer(source.length() * 2);
        for (int i = 0; i < source.length(); i++)
        {
            int ch = source.charAt(i);
            // avoid escaping already escaped characters
            if (ch == 38)
            {
                terminatorIndex = source.indexOf(";", i);
                if (terminatorIndex > 0)
                {
                    if (source.substring(i + 1, terminatorIndex).matches("#[0-9]+|lt|gt|amp|quote"))
                    {
                        result.append(source.substring(i, terminatorIndex + 1));
                        // Skip remaining chars up to (and including) ";"
                        i = terminatorIndex;
                        continue;
                    }
                }
            }
            if (ch == 10)
            {
                result.append("<br/>");
            }
            else if (ch != 32 && (ch > 122 || ch < 48 || ch == 60 || ch == 62))
            {
                result.append("&#");
                result.append(ch);
                result.append(";");
            }
            else
            {
                result.append((char) ch);
            }
        }
        return new String(result);
    }

}

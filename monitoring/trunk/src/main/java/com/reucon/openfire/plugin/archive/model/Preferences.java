package com.reucon.openfire.plugin.archive.model;

import java.util.Map;

/**
 * A user's archiving preferences according to XEP-0136.
 */
public class Preferences
{
    public enum MethodUsage
    {
        forbid,
        concide,
        prefer
    }

    private String username;
    private Map<String, MethodUsage> methods;

    

}

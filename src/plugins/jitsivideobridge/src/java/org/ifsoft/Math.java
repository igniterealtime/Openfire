package org.ifsoft;


public class Math
{
    public Math()
    {
    }

    public static Integer max(Integer val1, Integer val2)
    {
        return Integer.valueOf(Math.max(val1.intValue(), val2.intValue()));
    }

    public static Long max(Long val1, Long val2)
    {
        return Long.valueOf(Math.max(val1.longValue(), val2.longValue()));
    }

    public static Integer min(Integer val1, Integer val2)
    {
        return Integer.valueOf(Math.min(val1.intValue(), val2.intValue()));
    }

    public static Long min(Long val1, Long val2)
    {
        return Long.valueOf(Math.min(val1.longValue(), val2.longValue()));
    }

    public static Double pow(Double val, Double exp)
    {
        return Double.valueOf(Math.pow(val.doubleValue(), exp.doubleValue()));
    }

    public static Double ceiling(Double val)
    {
        return Double.valueOf(java.lang.Math.ceil(val.doubleValue()));
    }
}

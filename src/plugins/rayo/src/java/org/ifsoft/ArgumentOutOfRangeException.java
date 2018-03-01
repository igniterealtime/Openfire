package org.ifsoft;

public class ArgumentOutOfRangeException extends ArgumentException
{

    private static final long serialVersionUID = 1L;
    private Object actualValue;

    public ArgumentOutOfRangeException()
    {
        super("Nonnegative number required.");
    }

    public ArgumentOutOfRangeException(String paramName)
    {
        super("Nonnegative number required.", paramName);
    }

    public ArgumentOutOfRangeException(String message, Exception innerException)
    {
        super(message, innerException);
    }

    public ArgumentOutOfRangeException(String paramName, String message)
    {
        super(message, paramName);
    }

    public ArgumentOutOfRangeException(String paramName, Object actualValue, String message)
    {
        super(message, paramName);
        this.actualValue = actualValue;
    }

    public Object getActualValue()
    {
        return actualValue;
    }
}

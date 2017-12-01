package org.ifsoft;

public class ArgumentNullException extends ArgumentException
{
    private static final long serialVersionUID = 1L;

    public ArgumentNullException()
    {
        super("Argument cannot be null.");
    }

    public ArgumentNullException(String paramName)
    {
        super("Argument cannot be null.", paramName);
    }

    public ArgumentNullException(String message, Exception innerException)
    {
        super(message, innerException);
    }

    public ArgumentNullException(String paramName, String message)
    {
        super(message, paramName);
    }
}

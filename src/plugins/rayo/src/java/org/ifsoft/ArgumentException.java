package org.ifsoft;

public class ArgumentException extends Exception
{

    private static final long serialVersionUID = 1L;
    private Exception innerException;
    private String paramName;

    public ArgumentException()
    {
        super("An invalid argument was specified.");
    }

    public ArgumentException(String message)
    {
        super(message);
    }

    public ArgumentException(String message, Exception innerException)
    {
        super(message);
        this.innerException = innerException;
    }

    public ArgumentException(String message, String paramName)
    {
        super(message);
        this.paramName = paramName;
    }

    public ArgumentException(String message, String paramName, Exception innerException)
    {
        super(message);
        this.paramName = paramName;
        this.innerException = innerException;
    }

    public Exception getInnerException()
    {
        return innerException;
    }

    public String getParamName()
    {
        return paramName;
    }

}

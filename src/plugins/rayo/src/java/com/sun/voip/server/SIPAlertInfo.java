package com.sun.voip.server;

import gov.nist.core.NameValueList;
import javax.sip.header.AlertInfoHeader;
import gov.nist.javax.sip.header.*;
import java.lang.StringBuilder;

public final class SIPAlertInfo extends ParametersHeader
{
    private static final long serialVersionUID = 0x39ba1254fc6b29efL;
    protected String namePair;

    public SIPAlertInfo()
    {
        super("Alert-Info");
    }

    protected String encodeBody()
    {
        StringBuffer stringbuffer = new StringBuffer();
        stringbuffer.append(namePair);

        if(!parameters.isEmpty())
            stringbuffer.append(";").append(parameters.encode());

        return stringbuffer.toString();
    }

    protected StringBuilder encodeBody(StringBuilder builder)
    {
        builder.append(namePair);

        if(!parameters.isEmpty())
            builder.append(";").append(parameters.encode());

        return builder;
    }

    public void setNamePair(String namePair)
    {
        this.namePair = namePair;
    }

    public String getNamePair()
    {
        return namePair;
    }

    public Object clone()
    {
        SIPAlertInfo alertinfo = (SIPAlertInfo)super.clone();

        if(namePair != null)
            alertinfo.namePair = this.namePair;

        return alertinfo;
    }
}

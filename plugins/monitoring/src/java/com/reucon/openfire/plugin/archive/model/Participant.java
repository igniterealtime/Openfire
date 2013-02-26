package com.reucon.openfire.plugin.archive.model;

import org.jivesoftware.database.JiveID;

import java.util.Date;

/**
 *
 */
@JiveID(603)
public class Participant
{
    private long id;
    private final Date start;
    private Date end;
    private final String jid;

    public Participant(Date start, String jid)
    {
        this.start = start;
        this.jid = jid;
    }

    public long getId()
    {
        return id;
    }

    public void setId(long id)
    {
        this.id = id;
    }

    public Date getStart()
    {
        return start;
    }

    public Date getEnd()
    {
        return end;
    }

    public void setEnd(Date end)
    {
        this.end = end;
    }

    public String getJid()
    {
        return jid;
    }
}

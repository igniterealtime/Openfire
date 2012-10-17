package com.reucon.openfire.plugin.archive.xep0136;

import com.reucon.openfire.plugin.archive.util.XmppDateUtil;
import org.dom4j.Element;

import java.util.Date;

/**
 * A request to remove one or more collections.
 * <p>
 * To request the removal of a single collection the client sends an empty &lt;remove/&gt; element.<br>
 * The 'with' (full JID) and 'start' attributes MUST be included to uniquely identify the collection.
 * <p>
 * The client may remove several collections at once.<br/>
 * The 'start' and 'end' elements MAY be specified to indicate a date range.<br/>
 * The 'with' attribute MAY be a full JID, bare JID or domain.
 * <p>
 * If the value of the optional 'open' attribute is set to 'true' then only collections that are currently
 * being recorded automatically by the server (see Automated Archiving) are removed.
 */
public class RemoveRequest
{
    private final String with;
    private final Date start;
    private final Date end;
    private final boolean open;

    public RemoveRequest(Element listElement)
    {
        this.with = listElement.attributeValue("with");
        this.start = XmppDateUtil.parseDate(listElement.attributeValue("start"));
        this.end = XmppDateUtil.parseDate(listElement.attributeValue("end"));
        this.open = "true".equals(listElement.attributeValue("open"));
    }

    /**
     * The 'with' attribute MAY be a full JID, bare JID or domain.<br>
     * If the 'with' attribute is omitted then collections with any JID are removed.
     *
     * @return the value of the with attribute, may be <code>null</code>.
     */
    public String getWith()
    {
        return with;
    }

    /**
     * If the start date is before all the collections in the archive then all collections prior
     * to the end date are removed.
     *
     * @return the value of the start attribute, may be <code>null</code>.
     */
    public Date getStart()
    {
        return start;
    }

    /**
     * If the end date is in the future then then all collections after the start date are removed.
     *
     * @return the value of the end attribute, may be <code>null</code>.
     */
    public Date getEnd()
    {
        return end;
    }

    /**
     * If the value of the optional 'open' attribute is set to 'true' then only collections that
     * are currently being recorded automatically by the server (see Automated Archiving) are removed.
     *
     * @return the value of the open attribute or <code>false</code> if not set.
     */
    public boolean getOpen()
    {
        return open;
    }
}
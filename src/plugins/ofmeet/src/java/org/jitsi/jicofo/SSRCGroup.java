/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.impl.protocol.jabber.jinglesdp.*;

import org.jitsi.util.*;

import java.util.*;

/**
 * Wrapper for <tt>SourceGroupPacketExtension</tt>.
 *
 * @author Pawel Domas
 */
public class SSRCGroup
{
    /**
     * Underlying source group packet extension.
     */
    private final SourceGroupPacketExtension group;

    /**
     * Extracts SSRC groups from Jingle content packet extension.
     * @param content the <tt>ContentPacketExtension</tt> that contains(or not)
     *                the description of SSRC groups.
     * @return the list of <tt>SSRCGroup</tt>s described by given
     *         <tt>ContentPacketExtension</tt>.
     */
    public static List<SSRCGroup> getSSRCGroupsForContent(
            ContentPacketExtension content)
    {
        List<SSRCGroup> groups = new ArrayList<SSRCGroup>();

        RtpDescriptionPacketExtension rtpDescPe
            = JingleUtils.getRtpDescription(content);

        if (rtpDescPe == null)
        {
            return groups;
        }

        List<SourceGroupPacketExtension> groupExtensions
            = rtpDescPe.getChildExtensionsOfType(
                    SourceGroupPacketExtension.class);

        for (SourceGroupPacketExtension groupPe : groupExtensions)
        {
            groups.add(new SSRCGroup(groupPe));
        }

        return groups;
    }

    /**
     * Creates new instance of <tt>SSRCGroup</tt>.
     * @param group the packet extension that described SSRC group to be wrapped
     *              by new object.
     */
    public SSRCGroup(SourceGroupPacketExtension group)
    {
        this.group = group;
    }

    /**
     * Returns deep copy of underlying <tt>SourceGroupPacketExtension</tt>.
     */
    public SourceGroupPacketExtension getExtensionCopy()
    {
        return group.copy();
    }

    /**
     * Returns full copy of this <tt>SSRCGroup</tt>.
     */
    public SSRCGroup copy()
    {
        return new SSRCGroup(getExtensionCopy());
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof SSRCGroup))
        {
            return false;
        }

        SSRCGroup other = (SSRCGroup) obj;
        String semantics = other.group.getSemantics();
        if (StringUtils.isNullOrEmpty(semantics)
            && !StringUtils.isNullOrEmpty(group.getSemantics()))
        {
            return false;
        }
        if (!group.getSemantics().equals(semantics))
        {
            return false;
        }
        for (SourcePacketExtension ssrcToFind : group.getSources())
        {
            boolean found = false;
            for (SourcePacketExtension ssrc : other.group.getSources())
            {
                if (ssrc.getSSRC() == ssrcToFind.getSSRC())
                {
                    found = true;
                    break;
                }
            }
            if (!found)
            {
                return false;
            }
        }
        return true;
    }
}

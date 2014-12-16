/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;

import java.util.*;

/**
 * Class maps lists of SSRC groups to media types and encapsulates various
 * utility operations.
 *
 * @author Pawel Domas
 */
public class MediaSSRCGroupMap
{
    /**
     * Map backend.
     */
    private final Map<String, List<SSRCGroup>> groupMap;

    /**
     * Creates new instance of <tt>MediaSSRCGroupMap</tt>.
     */
    public MediaSSRCGroupMap()
    {
        groupMap = new HashMap<String, List<SSRCGroup>>();
    }

    /**
     * Creates new instance of <tt>MediaSSRCGroupMap</tt>.
     * @param map the map with predefined values that will be used by new
     *            instance.
     */
    private MediaSSRCGroupMap(Map<String, List<SSRCGroup>> map)
    {
        this.groupMap = map;
    }

    /**
     * Returns the list of {@link SSRCGroup} for given media type.
     * @param media the name of media type for which list of SSRC groups will be
     *              returned.
     */
    public List<SSRCGroup> getSSRCGroupsForMedia(String media)
    {
        List<SSRCGroup> mediaGroups = groupMap.get(media);
        if (mediaGroups == null)
        {
            mediaGroups = new ArrayList<SSRCGroup>();
            groupMap.put(media, mediaGroups);
        }
        return mediaGroups;
    }

    /**
     * Extracts SSRC groups from Jingle content list.
     * @param contents the list of <tt>ContentPacketExtension</tt> which will be
     *                 examined for media SSRC groups.
     * @return <tt>MediaSSRCGroupMap</tt> that reflects SSRC groups of media
     *         described by given content list.
     */
    public static MediaSSRCGroupMap getSSRCGroupsForContents(
            List<ContentPacketExtension> contents)
    {
        MediaSSRCGroupMap mediaSSRCGroupMap = new MediaSSRCGroupMap();

        for (ContentPacketExtension content : contents)
        {
            List<SSRCGroup> mediaGroups
                = mediaSSRCGroupMap.getSSRCGroupsForMedia(content.getName());

            // FIXME: does not check for duplicates
            mediaGroups.addAll(SSRCGroup.getSSRCGroupsForContent(content));
        }

        return mediaSSRCGroupMap;
    }

    /**
     * Returns all media types stored in this map(some of them might be empty).
     */
    public List<String> getMediaTypes()
    {
        return new ArrayList<String>(groupMap.keySet());
    }

    /**
     * Adds mapping of SSRC group to media type.
     * @param media the media type name.
     * @param ssrcGroup <tt>SSRCGroup</tt> that will be mapped to given media
     *                  type.
     */
    public void addSSRCGroup(String media, SSRCGroup ssrcGroup)
    {
        getSSRCGroupsForMedia(media).add(ssrcGroup);
    }

    /**
     * Adds mapping of SSRC groups to media type.
     * @param media the media type name.
     * @param ssrcGroups <tt>SSRCGroup</tt>s that will be mapped to given media
     *                  type.
     */
    public void addSSRCGroups(String media, List<SSRCGroup> ssrcGroups)
    {
        getSSRCGroupsForMedia(media).addAll(ssrcGroups);
    }

    /**
     * Adds SSRC groups contained in given <tt>MediaSSRCGroupMap</tt> to this
     * map instance.
     * @param ssrcGroups the <tt>MediaSSRCGroupMap</tt> that will be added to
     *                   this map instance.
     */
    public void add(MediaSSRCGroupMap ssrcGroups)
    {
        for (String media : ssrcGroups.getMediaTypes())
        {
            addSSRCGroups(media, ssrcGroups.getSSRCGroupsForMedia(media));
        }
    }

    /**
     * Returns <tt>true</tt> if this map contains any SSRC groups.
     */
    public boolean isEmpty()
    {
        for (String media : groupMap.keySet())
        {
            if (!getSSRCGroupsForMedia(media).isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Removes SSRC groups contained ing given <tt>MediaSSRCGroupMap</tt> from
     * this map if they exist.
     * @param mapToRemove the <tt>MediaSSRCGroupMap</tt> that contains SSRC
     *                    groups mappings to be removed from this instance.
     */
    public void remove(MediaSSRCGroupMap mapToRemove)
    {
        for (String media : mapToRemove.groupMap.keySet())
        {
            List<SSRCGroup> groupList
                = getSSRCGroupsForMedia(media);

            List<SSRCGroup> toBeRemoved
                = new ArrayList<SSRCGroup>();

            for (SSRCGroup ssrcGroupToCheck
                : mapToRemove.groupMap.get(media))
            {
                for (SSRCGroup ssrcGroup : groupList)
                {
                    if (ssrcGroupToCheck.equals(ssrcGroup))
                    {
                        toBeRemoved.add(ssrcGroup);
                    }
                }
            }

            groupList.removeAll(toBeRemoved);
        }
    }

    /**
     * Returns deep copy of this map instance.
     */
    public MediaSSRCGroupMap copy()
    {
        Map<String, List<SSRCGroup>> mapCopy
            = new HashMap<String, List<SSRCGroup>>();

        for (String media : groupMap.keySet())
        {
            List<SSRCGroup> listToCopy
                = new ArrayList<SSRCGroup>(groupMap.get(media));
            List<SSRCGroup> listCopy
                = new ArrayList<SSRCGroup>(listToCopy.size());

            for (SSRCGroup group : listToCopy)
            {
                listCopy.add(group.copy());
            }

            mapCopy.put(media, listCopy);
        }

        return new MediaSSRCGroupMap(mapCopy);
    }
}

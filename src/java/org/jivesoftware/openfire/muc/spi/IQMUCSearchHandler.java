/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.muc.spi;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.PacketError.Condition;
import org.xmpp.resultsetmanagement.ResultSet;
import org.xmpp.resultsetmanagement.ResultSetImpl;

import java.util.*;

/**
 * This class adds jabber:iq:search combined with 'result set management'
 * functionality to the MUC service of Openfire.
 * 
 * @author Guus der Kinderen - Nimbuzz B.V. <guus@nimbuzz.com>
 * @author Giancarlo Frison - Nimbuzz B.V. <giancarlo@nimbuzz.com>
 */
public class IQMUCSearchHandler
{
	/**
	 * The MUC-server to extend with jabber:iq:search functionality.
	 */
	private final MultiUserChatService mucService;

	/**
	 * Creates a new instance of the search provider.
	 * 
	 * @param mucService
	 *            The server for which to return search results.
	 */
	public IQMUCSearchHandler(MultiUserChatService mucService)
	{
		this.mucService = mucService;
	}

	/**
	 * Utility method that returns a 'jabber:iq:search' child element filled
	 * with a blank dataform.
	 * 
	 * @return Element, named 'query', escaped by the 'jabber:iq:search'
	 *         namespace, filled with a blank dataform.
	 */
	private static Element getDataElement()
	{
		final DataForm searchForm = new DataForm(DataForm.Type.form);
		searchForm.setTitle("Chat Rooms Search");
		searchForm.addInstruction("Instructions");

		final FormField typeFF = searchForm.addField();
		typeFF.setVariable("FORM_TYPE");
		typeFF.setType(FormField.Type.hidden);
		typeFF.addValue("jabber:iq:search");

		final FormField nameFF = searchForm.addField();
		nameFF.setVariable("name");
		nameFF.setType(FormField.Type.text_single);
		nameFF.setLabel("Name");
		nameFF.setRequired(false);

		final FormField matchFF = searchForm.addField();
		matchFF.setVariable("name_is_exact_match");
		matchFF.setType(FormField.Type.boolean_type);
		matchFF.setLabel("Name must match exactly");
		matchFF.setRequired(false);

		final FormField subjectFF = searchForm.addField();
		subjectFF.setVariable("subject");
		subjectFF.setType(FormField.Type.text_single);
		subjectFF.setLabel("Subject");
		subjectFF.setRequired(false);

		final FormField userAmountFF = searchForm.addField();
		userAmountFF.setVariable("num_users");
		userAmountFF.setType(FormField.Type.text_single);
		userAmountFF.setLabel("Number of users");
		userAmountFF.setRequired(false);

		final FormField maxUsersFF = searchForm.addField();
		maxUsersFF.setVariable("num_max_users");
		maxUsersFF.setType(FormField.Type.text_single);
		maxUsersFF.setLabel("Max number allowed of users");
		maxUsersFF.setRequired(false);

		final FormField includePasswordProtectedFF = searchForm.addField();
		includePasswordProtectedFF.setVariable("include_password_protected");
		includePasswordProtectedFF.setType(FormField.Type.boolean_type);
		includePasswordProtectedFF.setLabel("Include password protected rooms");
		includePasswordProtectedFF.setRequired(false);

		final Element probeResult = DocumentHelper.createElement(QName.get(
			"query", "jabber:iq:search"));
		probeResult.add(searchForm.getElement());
		return probeResult;
	}

	/**
	 * Constructs an answer on a IQ stanza that contains a search request. The
	 * answer will be an IQ stanza of type 'result' or 'error'.
	 * 
	 * @param iq
	 *            The IQ stanza that is the search request.
	 * @return An answer to the provided request.
	 */
	public IQ handleIQ(IQ iq)
	{
		final IQ reply = IQ.createResultIQ(iq);
		final Element formElement = iq.getChildElement().element(
			QName.get("x", "jabber:x:data"));
		if (formElement == null)
		{
			reply.setChildElement(getDataElement());
			return reply;
		}

		// parse params from request.
		final DataForm df = new DataForm(formElement);
		boolean name_is_exact_match = false;
		String subject = null;
		int numusers = -1;
		int numaxusers = -1;
		boolean includePasswordProtectedRooms = true;

		final Set<String> names = new HashSet<String>();
		for (final FormField field : df.getFields()) 
		{
			if (field.getVariable().equals("name"))
			{
				names.add(field.getFirstValue());
			}
		}

		final FormField matchFF = df.getField("name_is_exact_match");
		if (matchFF != null)
		{
			final String b = matchFF.getFirstValue();
			if (b != null)
			{
				name_is_exact_match = b.equals("1")
						|| b.equalsIgnoreCase("true")
						|| b.equalsIgnoreCase("yes");
			}
		}

		final FormField subjectFF = df.getField("subject");
		if (subjectFF != null)
		{
			subject = subjectFF.getFirstValue();
		}

		try
		{
			final FormField userAmountFF = df.getField("num_users");
			if (userAmountFF != null)
			{
                String value = userAmountFF.getFirstValue();
                if (value != null && !"".equals(value)) {
                    numusers = Integer.parseInt(value);
                }
			}

			final FormField maxUsersFF = df.getField("num_max_users");
			if (maxUsersFF != null)
			{
                String value = maxUsersFF.getFirstValue();
                if (value != null && !"".equals(value)) {
                    numaxusers = Integer.parseInt(value);
                }
            }
		}
		catch (NumberFormatException e)
		{
			reply.setError(PacketError.Condition.bad_request);
			return reply;
		}

		final FormField includePasswordProtectedRoomsFF = df.getField("include_password_protected");
		if (includePasswordProtectedRoomsFF != null)
		{
			final String b = includePasswordProtectedRoomsFF.getFirstValue();
			if (b != null)
			{
				if (b.equals("0") || b.equalsIgnoreCase("false")
						|| b.equalsIgnoreCase("no"))
				{
					includePasswordProtectedRooms = false;
				}
			}
		}

		// search for chatrooms matching the request params.
		final List<MUCRoom> mucs = new ArrayList<MUCRoom>();
		for (MUCRoom room : mucService.getChatRooms())
		{
			boolean find = false;

			if (names.size() > 0)
			{
				for (final String name : names)
				{
					if (name_is_exact_match)
					{
						if (name.equalsIgnoreCase(room.getNaturalLanguageName()))
						{
							find = true;
							break;
						}
					}
					else
					{
						if (room.getNaturalLanguageName().toLowerCase().indexOf(
							name.toLowerCase()) != -1)
						{
							find = true;
							break;
						}
					}
				}
			}

			if (subject != null
					&& room.getSubject().toLowerCase().indexOf(
						subject.toLowerCase()) != -1)
			{
				find = true;
			}

			if (numusers > -1 && room.getParticipants().size() < numusers)
			{
				find = false;
			}

			if (numaxusers > -1 && room.getMaxUsers() < numaxusers)
			{
				find = false;
			}

			if (!includePasswordProtectedRooms && room.isPasswordProtected())
			{
				find = false;
			}

			if (find && canBeIncludedInResult(room))
			{
				mucs.add(room);
			}
		}

		final ResultSet<MUCRoom> searchResults = new ResultSetImpl<MUCRoom>(
			sortByUserAmount(mucs));

		// See if the requesting entity would like to apply 'result set
		// management'
		final Element set = iq.getChildElement().element(
			QName.get("set", ResultSet.NAMESPACE_RESULT_SET_MANAGEMENT));
		final List<MUCRoom> mucrsm;

		// apply RSM only if the element exists, and the (total) results
		// set is not empty.
		final boolean applyRSM = set != null && !mucs.isEmpty();

		if (applyRSM)
		{
			if (!ResultSet.isValidRSMRequest(set))
			{
				reply.setError(Condition.bad_request);
				return reply;
			}

			try
			{
				mucrsm = searchResults.applyRSMDirectives(set);
			}
			catch (NullPointerException e)
			{
				final IQ itemNotFound = IQ.createResultIQ(iq);
				itemNotFound.setError(Condition.item_not_found);
				return itemNotFound;
			}
		}
		else
		{
			// if no rsm, all found rooms are part of the result.
			mucrsm = new ArrayList<MUCRoom>(searchResults);
		}

		final Element res = DocumentHelper.createElement(QName.get("query",
			"jabber:iq:search"));

		final DataForm resultform = new DataForm(DataForm.Type.result);
		boolean atLeastoneResult = false;
		for (MUCRoom room : mucrsm)
		{
			final Map<String, Object> fields = new HashMap<String, Object>();
			fields.put("name", room.getNaturalLanguageName());
			fields.put("subject", room.getSubject());
			fields.put("num_users", room.getOccupantsCount());
			fields.put("num_max_users", room.getMaxUsers());
			fields.put("is_password_protected", room.isPasswordProtected());
			fields.put("is_member_only", room.isMembersOnly());
			fields.put("jid", room.getRole().getRoleAddress().toString());
            resultform.addItemFields(fields);
			atLeastoneResult = true;
		}
		if (atLeastoneResult)
		{
			resultform.addReportedField("name", "Name", FormField.Type.text_single);
			resultform.addReportedField("subject", "Subject", FormField.Type.text_single);
			resultform.addReportedField("num_users", "Number of users", FormField.Type.text_single);
			resultform.addReportedField("num_max_users", "Max number allowed of users", FormField.Type.text_single);
			resultform.addReportedField("is_password_protected", "Is a password protected room.", FormField.Type.boolean_type);
			resultform.addReportedField("is_member_only", "Is a member only room.", FormField.Type.boolean_type);
			resultform.addReportedField("jid", "JID", FormField.Type.text_single);
		}

		if (applyRSM)
		{
			res.add(searchResults.generateSetElementFromResults(mucrsm));
		}

		reply.setChildElement(res);

		return reply;
	}

	/**
	 * Sorts the provided list in such a way that the MUC with the most users
	 * will be the first one in the list.
	 * 
	 * @param mucs
	 *            The unordered list that will be sorted.
     * @return The sorted list of MUC rooms.
	 */
	private static List<MUCRoom> sortByUserAmount(List<MUCRoom> mucs)
	{
		Collections.sort(mucs, new Comparator<MUCRoom>()
		{
			public int compare(MUCRoom o1, MUCRoom o2)
			{
				return o2.getOccupantsCount() - o1.getOccupantsCount();
			}
		});

		return mucs;
	}

	/**
	 * Checks if the room may be included in search results. This is almost
	 * identical to {@link MultiUserChatServiceImpl#canDiscoverRoom(MUCRoom room)},
	 * but that method is private and cannot be re-used here.
	 * 
	 * @param room
	 *            The room to check
	 * @return ''true'' if the room may be included in search results, ''false''
	 *         otherwise.
	 */
	private static boolean canBeIncludedInResult(MUCRoom room)
	{
		// Check if locked rooms may be discovered
		final boolean discoverLocked = MUCPersistenceManager.getBooleanProperty(room.getMUCService().getServiceName(), "discover.locked", true);

		if (!discoverLocked && room.isLocked())
		{
			return false;
		}
		return room.isPublicRoom();
	}

	/**
	 * Returns the first value from the FormField, or 'null' if no value has
	 * been set.
	 * 
	 * @param formField
	 *            The field from which to return the first value.
	 * @return String based value, or 'null' if the FormField has no values.
	 * @deprecated replaced by {@link FormField#getFirstValue()}
	 */
	@Deprecated
	public static String getFirstValue(FormField formField)
	{
		if (formField == null)
		{
			throw new IllegalArgumentException(
				"The argument 'formField' cannot be null.");
		}

		List<String> it = formField.getValues();

		if (it.isEmpty())
		{
			return null;
		}

		return it.get(0);
	}
}

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
import org.jivesoftware.openfire.forms.DataForm;
import org.jivesoftware.openfire.forms.FormField;
import org.jivesoftware.openfire.forms.spi.XDataFormImpl;
import org.jivesoftware.openfire.forms.spi.XFormFieldImpl;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
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
		final XDataFormImpl searchForm = new XDataFormImpl(DataForm.TYPE_FORM);
		searchForm.setTitle("Chat Rooms Search");
		searchForm.addInstruction("Instructions");

		final FormField typeFF = new XFormFieldImpl("FORM_TYPE");
		typeFF.setType(FormField.TYPE_HIDDEN);
		typeFF.addValue("jabber:iq:search");
		searchForm.addField(typeFF);

		final FormField nameFF = new XFormFieldImpl("name");
		nameFF.setType(FormField.TYPE_TEXT_SINGLE);
		nameFF.setLabel("Name");
		nameFF.setRequired(false);
		searchForm.addField(nameFF);

		final FormField matchFF = new XFormFieldImpl("name_is_exact_match");
		matchFF.setType(FormField.TYPE_BOOLEAN);
		matchFF.setLabel("Name must match exactly");
		matchFF.setRequired(false);
		searchForm.addField(matchFF);

		final FormField subjectFF = new XFormFieldImpl("subject");
		subjectFF.setType(FormField.TYPE_TEXT_SINGLE);
		subjectFF.setLabel("Subject");
		subjectFF.setRequired(false);
		searchForm.addField(subjectFF);

		final FormField userAmountFF = new XFormFieldImpl("num_users");
		userAmountFF.setType(FormField.TYPE_TEXT_SINGLE);
		userAmountFF.setLabel("Number of users");
		userAmountFF.setRequired(false);
		searchForm.addField(userAmountFF);

		final FormField maxUsersFF = new XFormFieldImpl("num_max_users");
		maxUsersFF.setType(FormField.TYPE_TEXT_SINGLE);
		maxUsersFF.setLabel("Max number allowed of users");
		maxUsersFF.setRequired(false);
		searchForm.addField(maxUsersFF);

		final FormField includePasswordProtectedFF = new XFormFieldImpl(
			"include_password_protected");
		includePasswordProtectedFF.setType(FormField.TYPE_BOOLEAN);
		includePasswordProtectedFF.setLabel("Include password protected rooms");
		includePasswordProtectedFF.setRequired(false);
		searchForm.addField(includePasswordProtectedFF);

		final Element probeResult = DocumentHelper.createElement(QName.get(
			"query", "jabber:iq:search"));
		probeResult.add(searchForm.asXMLElement());
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
		final XDataFormImpl df = new XDataFormImpl();
		df.parse(formElement);
		boolean name_is_exact_match = false;
		String subject = null;
		int numusers = -1;
		int numaxusers = -1;
		boolean includePasswordProtectedRooms = true;

		final Set<String> names = new HashSet<String>();
        @SuppressWarnings("unchecked")
        final Iterator<FormField> formFields = df.getFields();
		while (formFields.hasNext())
		{

			final FormField field = formFields.next();
			if (field.getVariable().equals("name"))
			{
				names.add(getFirstValue(field));
			}
		}

		final FormField matchFF = df.getField("name_is_exact_match");
		if (matchFF != null)
		{
			final String b = getFirstValue(matchFF);
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
			subject = getFirstValue(subjectFF);
		}

		try
		{
			final FormField userAmountFF = df.getField("num_users");
			if (userAmountFF != null)
			{
                String value = getFirstValue(userAmountFF);
                if (value != null && !"".equals(value)) {
                    numusers = Integer.parseInt(value);
                }
			}

			final FormField maxUsersFF = df.getField("num_max_users");
			if (maxUsersFF != null)
			{
                String value = getFirstValue(maxUsersFF);
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
			final String b = getFirstValue(includePasswordProtectedRoomsFF);
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

		final XDataFormImpl resultform = new XDataFormImpl(DataForm.TYPE_RESULT);
		boolean atLeastoneResult = false;
		for (MUCRoom room : mucrsm)
		{
			ArrayList<XFormFieldImpl> fields = new ArrayList<XFormFieldImpl>();
			XFormFieldImpl innerfield = new XFormFieldImpl("name");
			innerfield.setType(FormField.TYPE_TEXT_SINGLE);
			innerfield.addValue(room.getNaturalLanguageName());
			fields.add(innerfield);
			innerfield = new XFormFieldImpl("subject");
			innerfield.setType(FormField.TYPE_TEXT_SINGLE);
			innerfield.addValue(room.getSubject());
			fields.add(innerfield);
			innerfield = new XFormFieldImpl("num_users");
			innerfield.setType(FormField.TYPE_TEXT_SINGLE);
			innerfield.addValue(String.valueOf(room.getOccupantsCount()));
			fields.add(innerfield);
			innerfield = new XFormFieldImpl("num_max_users");
			innerfield.setType(FormField.TYPE_TEXT_SINGLE);
			innerfield.addValue(String.valueOf(room.getMaxUsers()));
			fields.add(innerfield);
			innerfield = new XFormFieldImpl("is_password_protected");
			innerfield.setType(FormField.TYPE_BOOLEAN);
			innerfield.addValue(Boolean.toString(room.isPasswordProtected()));
			fields.add(innerfield);
			innerfield = new XFormFieldImpl("is_member_only");
			innerfield.setType(FormField.TYPE_BOOLEAN);
			innerfield.addValue(Boolean.toString(room.isMembersOnly()));
			fields.add(innerfield);
            innerfield = new XFormFieldImpl("jid");
            innerfield.setType(FormField.TYPE_TEXT_SINGLE);
            innerfield.addValue(room.getRole().getRoleAddress().toString());
            fields.add(innerfield);
            resultform.addItemFields(fields);
			atLeastoneResult = true;
		}
		if (atLeastoneResult)
		{
			final FormField rffName = new XFormFieldImpl("name");
			rffName.setLabel("Name");
			resultform.addReportedField(rffName);

			final FormField rffSubject = new XFormFieldImpl("subject");
			rffSubject.setLabel("Subject");
			resultform.addReportedField(rffSubject);

			final FormField rffNumUsers = new XFormFieldImpl("num_users");
			rffNumUsers.setLabel("Number of users");
			resultform.addReportedField(rffNumUsers);

			final FormField rffNumMaxUsers = new XFormFieldImpl("num_max_users");
			rffNumMaxUsers.setLabel("Max number allowed of users");
			resultform.addReportedField(rffNumMaxUsers);

			final FormField rffPasswordProtected = new XFormFieldImpl(
				"is_password_protected");
			rffPasswordProtected.setLabel("Is a password protected room.");
			resultform.addReportedField(rffPasswordProtected);

            final FormField rffJID = new XFormFieldImpl("jid");
            rffJID.setLabel("JID");
            resultform.addReportedField(rffJID);

            FormField innerfield = new XFormFieldImpl("is_member_only");
			innerfield.setType(FormField.TYPE_TEXT_SINGLE);
			innerfield.setLabel("Is a member only room.");
			resultform.addReportedField(innerfield);
			res.add(resultform.asXMLElement());
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
	 */
	public static String getFirstValue(FormField formField)
	{
		if (formField == null)
		{
			throw new IllegalArgumentException(
				"The argument 'formField' cannot be null.");
		}

		Iterator<String> it = formField.getValues();

		if (!it.hasNext())
		{
			return null;
		}

		return it.next();
	}
}

/**
 * $RCSfile: ,v $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2004-2010 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 package org.jivesoftware.spark.plugin.jitsivideobridge;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;

import org.jivesoftware.spark.SparkManager;
import org.jivesoftware.spark.component.RolloverButton;
import org.jivesoftware.spark.ui.ChatRoom;
import org.jivesoftware.spark.util.*;
import org.jivesoftware.spark.util.log.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;

public class ChatRoomDecorator
{
	public RolloverButton jitsivideobridgeButton;
	public ChatRoom room;


	private final String url;

	public ChatRoomDecorator(final ChatRoom room, final String url)
	{
		this.room = room;
		this.url = url;

		ClassLoader cl = getClass().getClassLoader();
		ImageIcon jitsivideobridgeIcon = new ImageIcon(cl.getResource("images/jitsi_logo16x16.png"));
		jitsivideobridgeButton = new RolloverButton(jitsivideobridgeIcon);
		jitsivideobridgeButton.setToolTipText(GraphicUtils.createToolTip("Jitsi Videobridge"));
		final String roomId = getNode(room.getRoomname());
		final String sessionID = roomId + "-" + SparkManager.getConnection().getConnectionID();
		final String nickname = getNode(org.jivesoftware.smack.util.StringUtils.parseBareAddress(SparkManager.getSessionManager().getJID()));

		jitsivideobridgeButton.addActionListener( new ActionListener()
		{
				public void actionPerformed(ActionEvent event)
				{
					String newUrl;

					if ("groupchat".equals(room.getChatType().toString()))
					{
						newUrl = url + "r=" + roomId;
						sendInvite(room.getRoomname(), newUrl, Message.Type.groupchat);

					} else {

						newUrl = url + "r=" + sessionID;
						sendInvite(room.getRoomname(), newUrl, Message.Type.chat);
					}

					BareBonesBrowserLaunch.openURL(newUrl);
				}
		});
		room.getEditorBar().add(jitsivideobridgeButton);

	}

	public void finished()
	{
		Log.warning("ChatRoomDecorator: finished " + room.getRoomname());
	}

	private String getNode(String jid)
	{
		String node = jid;
		int pos = node.indexOf("@");

		if (pos > -1)
			node = jid.substring(0, pos);

		return node;
	}

	private void sendInvite(String jid, String url, Message.Type type)
	{
		Message message2 = new Message();
		message2.setTo(jid);
		message2.setType(type);
		message2.setBody(url);
		SparkManager.getConnection().sendPacket(message2);
	}
}

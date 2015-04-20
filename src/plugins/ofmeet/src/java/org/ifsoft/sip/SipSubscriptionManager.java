/**
 *    Copyright 2012 Voxbone SA/NV
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.ifsoft.sip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.sip.address.SipURI;

import org.slf4j.*;
import org.slf4j.Logger;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;


/**
 * Table to track all active SIP Events dialogs
 *
 */

public class SipSubscriptionManager
{
    private static final Logger Log = LoggerFactory.getLogger(SipSubscriptionManager.class);

	// Outbound subscriptions
	static Hashtable<String, List<SipSubscription>> subscriptions = new Hashtable<String, List<SipSubscription>>();

	// Inbound subscriptions
	static Hashtable<String, List<SipSubscription>> watchers = new Hashtable<String, List<SipSubscription>>();

	private static String spoolPath = null;


	public static void configure(Properties properties) {
		spoolPath = properties.getProperty("com.voxbone.kelpie.spool_directory", "/var/spool/kelpie");
	}

	public static void addSubscriber(String user, SipSubscription subscription)
	{
		synchronized (subscriptions)
		{
			if (subscriptions.get(user) == null)
			{
				subscriptions.put(user, new LinkedList<SipSubscription>());
			}

			subscriptions.get(user).add(subscription);
			subscription.schedule();

			try
			{
				saveSubscription(subscription);
			}
			catch (IOException e)
			{
				Log.error("Error persisting new subscription", e);
			}
			catch (SAXException e)
			{
				Log.error("Error persisting new subscription", e);
			}
		}
	}

	public static void addWatcher(String user, SipSubscription subscription)
	{
		synchronized (watchers)
		{
			if (watchers.get(user) == null)
			{
				watchers.put(user, new LinkedList<SipSubscription>());
			}

			watchers.get(user).add(subscription);

			try
			{
				saveWatcher(subscription);
			}
			catch (IOException e)
			{
				Log.error("Error persisting new watcher", e);
			}
			catch (SAXException e)
			{
				Log.error("Error persisting new watcher", e);
			}
		}
	}

	public static SipSubscription getWatcherByCallID(String user, String callId)
	{
		synchronized (watchers)
		{
			List<SipSubscription> subs = watchers.get(user);

			if (subs == null)
			{
				return null;
			}

			for (SipSubscription sub : subs)
			{
				if (callId.equals(sub.callId))
				{
					return sub;
				}
			}
		}
		return null;
	}

	public static SipSubscription getSubscriptionByCallID(String user, String callId)
	{
		synchronized (subscriptions)
		{
			List<SipSubscription> subs = subscriptions.get(user);

			if (subs == null)
			{
				return null;
			}

			for (SipSubscription sub : subs)
			{
				if (callId.equals(sub.callId))
				{
					return sub;
				}
			}
		}
		return null;
	}

	public static SipSubscription getSubscription(String user, String dest)
	{
		synchronized (subscriptions)
		{
			List<SipSubscription> subs = subscriptions.get(user);

			if (subs!=null)
			{
				for (SipSubscription sub : subs)
				{
					String subDest = ((SipURI) sub.remoteParty.getURI()).getUser();

					if (subDest.equals(dest))
					{
						return sub;
					}
				}
			}
		}
		return null;
	}

	public static void removeWatcher(String user, SipSubscription subscription)
	{
		synchronized (watchers)
		{
			List<SipSubscription> subs = watchers.get(user);
			subs.remove(subscription);

			deleteWatcher(subscription);
		}
	}

	public static SipSubscription getWatcher(String user, String dest)
	{
		synchronized (watchers)
		{
			List<SipSubscription> subs = watchers.get(user);

			if (subs!=null)
			{
				for (SipSubscription sub : subs)
				{
					String subDest = ((SipURI) sub.remoteParty.getURI()).getUser();

					if (subDest.equals(dest))
					{
						return sub;
					}
				}
			}
		}
		return null;
	}

	public static SipSubscription removeSubscription(String user, String dest)
	{
		synchronized (subscriptions)
		{
			List<SipSubscription> subs = subscriptions.get(user);

			if (subs!=null)
			{
				for (SipSubscription sub : subs)
				{
					String subDest = ((SipURI) sub.remoteParty.getURI()).getUser();

					if (subDest.equals(dest))
					{
						sub.cancel();
						subs.remove(sub);
						deleteSubscription(sub);
						return sub;
					}
				}
			}
		}
		return null;
	}

	public static SipSubscription removeSubscriptionByCallID(String user, String callID)
	{
		synchronized (subscriptions)
		{
			List<SipSubscription> subs = subscriptions.get(user);

			if (subs!=null)
			{
				for (SipSubscription sub : subs)
				{
					if (sub.callId.equals(callID))
					{
						sub.cancel();
						subs.remove(sub);
						deleteSubscription(sub);
						return sub;
					}
				}
			}
		}
		return null;
	}

	public static void saveSubscription(SipSubscription sub) throws IOException, SAXException
	{
		String filename = spoolPath + "/subscriptions/" + ((SipURI) sub.localParty.getURI()).getUser() + "_" + sub.callId;
		FileOutputStream fs = new FileOutputStream(new File(filename));

		OutputFormat of = new OutputFormat("XML", "ISO-8859-1", true);
		of.setIndent(1);
		of.setIndenting(true);
		XMLSerializer serializer = new XMLSerializer(fs, of);
		ContentHandler hd = serializer.asContentHandler();
		hd.startDocument();
		hd.startElement("", "", "SUBSCRIPTION", null);
		sub.buildSubscriptionXML(hd);
		hd.endElement("", "", "SUBSCRIPTION");
		hd.endDocument();
		fs.close();
	}

	public static void saveWatcher(SipSubscription sub) throws IOException, SAXException
	{
		String filename = spoolPath + "/watchers/" + ((SipURI) sub.localParty.getURI()).getUser() + "_" + sub.callId;
		FileOutputStream fs = new FileOutputStream(new File(filename));

		OutputFormat of = new OutputFormat("XML", "ISO-8859-1", true);
		of.setIndent(1);
		of.setIndenting(true);
		XMLSerializer serializer = new XMLSerializer(fs, of);
		ContentHandler hd = serializer.asContentHandler();
		hd.startDocument();
		hd.startElement("", "", "SUBSCRIPTION", null);
		sub.buildSubscriptionXML(hd);
		hd.endElement("", "", "SUBSCRIPTION");
		hd.endDocument();
		fs.close();
	}

	public static void deleteSubscription(SipSubscription sub)
	{
		try {
		String filename = spoolPath + "/subscriptions/" + ((SipURI) sub.localParty.getURI()).getUser() + "_" + sub.callId;

		File file = new File(filename);
		file.delete();
		} catch(Exception ioe) {
                System.err.println( ioe.getMessage() + "Error deleting!");
                }

	}

	public static void deleteWatcher(SipSubscription sub)
	{
		try {
		String filename = spoolPath + "/watchers/" + ((SipURI) sub.localParty.getURI()).getUser() + "_" + sub.callId;

		File file = new File(filename);
		file.delete();
		} catch(Exception ioe) {
                System.err.println( ioe.getMessage() + "Error deleting!");
                }

	}

	public static void loadData()
	{

		long now = System.currentTimeMillis();
		String dirName = spoolPath + "/subscriptions/";
		File dirFile = new File(dirName);
		String [] children = dirFile.list();

		for (String child : children)
		{
			SipSubscription sub = new SipSubscription(dirName + child);

			if (sub != null)
			{
				if (now < sub.expires)
				{
					String user = ((SipURI) sub.localParty.getURI()).getUser();

					Log.info("adding subscriber for " + user);
					synchronized (subscriptions)
					{
						if (subscriptions.get(user) == null)
						{
							subscriptions.put(user, new LinkedList<SipSubscription>());
						}

						subscriptions.get(user).add(sub);
						long nextCall = sub.expires - now - (1800 * 1000);
						if (nextCall < 0)
						{
							nextCall = 0;
						}
						sub.schedule(nextCall);
					}
				}
				else
				{
					Log.error("Skipping already expired subscription");
					try
					{
						deleteSubscription(sub);
					}
					catch(Exception e)
					{
						Log.error("Error Deleting subscription");
					}
				}
			}
		}

		dirName = spoolPath + "/watchers/";
		dirFile = new File(dirName);
		children = dirFile.list();

		for (String child : children)
		{
			SipSubscription sub = new SipSubscription(dirName + child);

			if (now < sub.expires)
			{
				String user = ((SipURI) sub.localParty.getURI()).getUser();

				Log.info("adding watcher for " + user);
				synchronized (watchers)
				{
					if (watchers.get(user) == null)
					{
						watchers.put(user, new LinkedList<SipSubscription>());
					}

					watchers.get(user).add(sub);
				}
			}
			else
			{
				Log.error("Skipping already expired watcher");
				deleteWatcher(sub);
			}
		}
	}

}

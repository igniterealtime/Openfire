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


 /**
  * <b>Bare Bones Browser Launch for Java</b><br>
  * Utility class to open a web page from a Swing application
  * in the user's default browser.<br>
  * Supports: Mac OS X, GNU/Linux, Unix, Windows XP/Vista/7<br>
  * Example Usage:<code><br> &nbsp; &nbsp;
  *    String url = "http://www.google.com/";<br> &nbsp; &nbsp;
  *    BareBonesBrowserLaunch.openURL(int width, int height, String url, String title);<br></code>
  * Latest Version: <a href="http://www.centerkey.com/java/browser/">www.centerkey.com/java/browser</a><br>
  * Author: Dem Pilafian<br>
  * Public Domain Software -- Free to Use as You Like
  * @version 3.1, June 6, 2010
 */


package org.jivesoftware.spark.plugin.jitsivideobridge;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;


import org.jivesoftware.spark.SparkManager;
import org.jivesoftware.spark.component.browser.*;
import org.jivesoftware.spark.util.log.*;

public class BareBonesBrowserLaunch {

   static final String[] browsers = { "google-chrome", "firefox", "opera","epiphany", "konqueror", "conkeror", "midori", "kazehakase", "mozilla" };
   static final String errMsg = "Error attempting to launch web browser";

   /**
    * Opens the specified web page in the user's default browser
    * @param url A web address (URL) of a web page (ex: "http://www.google.com/")
    */

   public static void openURL(String url)
   {
	 Log.warning("BareBonesBrowserLaunch.openURL: " + url);

	 String osName = System.getProperty("os.name").toLowerCase();

	 try {

		if (osName.startsWith("mac os"))
		{
		   Class.forName("com.apple.eio.FileManager").getDeclaredMethod("openURL", new Class[] {String.class}).invoke(null, new Object[] {url});

		}  else if (osName.startsWith("windows")) {
		   Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);


		} else { //assume Unix or Linux
		   String browser = null;

		   for (String b : browsers)
		   {
			  if (browser == null && Runtime.getRuntime().exec(new String[] {"which", b}).getInputStream().read() != -1)
			  {
				 Runtime.getRuntime().exec(new String[] {browser = b, url});
			  }
		   }

		   if (browser == null)
		   {
			  throw new Exception(Arrays.toString(browsers));
		   }
		}

	 } catch (Exception e) {
		Log.warning("BareBonesBrowserLaunch.openURL: error " + e);
		JOptionPane.showMessageDialog(null, errMsg + "\n" + e.toString());
	 }
   }
}

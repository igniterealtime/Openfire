package com.ifsoft.jmxweb.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.*;
import org.jivesoftware.util.*;
import org.jivesoftware.openfire.XMPPServer;

public class HttpClient {
    private static Logger Log = LoggerFactory.getLogger("JmxWebPlugin:HttpClient");
    StringBuilder resultString = new StringBuilder("");
    String line="";
    public String getMemoryData(){
        try {
            String port = JiveGlobals.getProperty("httpbind.port.plain", "7070");
            String host = XMPPServer.getInstance().getServerInfo().getHostname();
            String username = JiveGlobals.getProperty("jmxweb.admin.username", "admin");
            String password = JiveGlobals.getProperty("jmxweb.admin.password", "admin");

            URL url = new URL("http://" + username + ":" + password + "@" + host + ":" + port + "/jolokia/read/java.lang:type=Memory/HeapMemoryUsage");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("HTTP Call Failed : HTTP error code : " + conn.getResponseCode());
            }
            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
            while ((line = br.readLine()) != null) {
                resultString.append(line);
            }
            Log.info("Memory data: "+ resultString.toString());
            conn.disconnect();

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultString.toString();
    }

}

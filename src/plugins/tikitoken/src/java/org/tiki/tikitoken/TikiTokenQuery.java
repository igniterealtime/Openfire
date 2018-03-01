package org.tiki.tikitoken;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Scanner;

import org.jivesoftware.util.JiveGlobals;

import com.owlike.genson.Genson;


public class TikiTokenQuery {
    private final String DEFAULT_BASE_URL = "http://tikiconverse.docker/";
    private String username;
    private String token;
    
    public TikiTokenQuery(String username, String token) {
        this.username = username;
        this.token = token;
    }
    
    public URL getUrl() throws MalformedURLException, URISyntaxException {
        String baseAddress = JiveGlobals.getProperty("org.tiki.tikitoken.baseUrl", this.DEFAULT_BASE_URL);
        String script = String.format("tiki-ajax_services.php?controller=xmpp&action=check_token&user=%s&token=%s", this.username, this.token);
        
        URL baseUrl = new URL(baseAddress);
        URL fullUrl = new URL(baseUrl, script);
        
        return fullUrl;
        
    }
    
    public boolean isValid() {
        String content = this.fetch();
        Genson genson = new Genson();
        Map<String, Boolean> root = genson.deserialize(content, Map.class);
        return root.get("valid");
    }
    
    public String fetch() {
        try {
            URL url = this.getUrl();
            InputStream stream = url.openStream();
            Scanner s = new java.util.Scanner( stream ).useDelimiter( "\\A" );
            String result = s.hasNext() ? s.next() : "";
            s.close();
            return result;
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }
}

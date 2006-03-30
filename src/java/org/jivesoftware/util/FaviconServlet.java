/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Servlet that gets favicons of webservers and includes them in HTTP responses. This
 * servlet can be used when getting a favicon can take some time so pages can use this
 * servlet as the image source to let the page load quickly and get the favicon images
 * as they are available.<p>
 *
 * This servlet expects the web application to have the <tt>images/server_16x16.gif</tt>
 * file that is used when no favicon is found.
 *
 * @author Gaston Dombiak
 */
public class FaviconServlet extends HttpServlet {
    /**
     * The content-type of the images to return.
     */
    private static final String CONTENT_TYPE = "image/jpeg";
    private byte[] defaultBytes;


    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            URL resource = config.getServletContext().getResource("/images/server_16x16.gif");
            defaultBytes = getImage(resource.toString());
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieve the image based on it's name.
     *
     * @param request  the httpservletrequest.
     * @param response the httpservletresponse.
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String host = request.getParameter("host");

        byte[] bytes = getImage(host, defaultBytes);
        if (bytes != null) {
            writeBytesToStream(bytes, response);
        }
    }

    /**
     * Writes out a <code>byte</code> to the ServletOuputStream.
     *
     * @param bytes the bytes to write to the <code>ServletOutputStream</code>.
     */
    private void writeBytesToStream(byte[] bytes, HttpServletResponse response) {
        response.setContentType(CONTENT_TYPE);

        // Send image
        try {
            ServletOutputStream sos = response.getOutputStream();
            sos.write(bytes);
            sos.flush();
            sos.close();
        }
        catch (IOException e) {
            // Do nothing
        }
    }

    /**
     * Returns the favicon image bytes of the specified host.
     *
     * @param host the name of the host to get its favicon.
     * @return the image bytes found, otherwise null.
     */
    private byte[] getImage(String host, byte[] defaultImage) {
        byte[] bytes = getImage("http://" + host + "/favicon.ico");
        if (bytes == null) {
            // Return byte of default icon
            bytes = defaultImage;
        }
        return bytes;
    }

    private byte[] getImage(String url) {
        try {
            URLConnection urlConnection = new URL(url).openConnection();
            urlConnection.setReadTimeout(1000);

            urlConnection.connect();
            DataInputStream di = new DataInputStream(urlConnection.getInputStream());

            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteStream);

            int len;
            byte[] b = new byte[1024];
            while ((len = di.read(b)) != -1) {
                out.write(b, 0, len);
            }
            di.close();
            out.flush();

            return byteStream.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

}

package org.jivesoftware.openfire.fastpath;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jivesoftware.openfire.fastpath.settings.chat.ChatSetting;
import org.jivesoftware.openfire.fastpath.settings.chat.ChatSettings;
import org.jivesoftware.openfire.fastpath.settings.chat.ChatSettingsManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.xmpp.packet.JID;

/**
 * A servlet that displays images.
 */
public class ImageServlet extends HttpServlet {

    /**
     * The content-type of the images to return.
     */
    private static final String CONTENT_TYPE = "image/jpeg";

    private ChatSettingsManager chatSettingsManager;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        // Initialize chat settings manager.
        chatSettingsManager = ChatSettingsManager.getInstance();
    }

    /**
     * Retrieve the image based on it's name.
     *
     * @param request the httpservletrequest.
     * @param response the httpservletresponse.
     * @throws ServletException
     * @throws IOException
     */
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        String imageName = request.getParameter("imageName");
        String workgroupName = (String)request.getSession().getAttribute("workgroup");
        if (workgroupName == null) {
            workgroupName = request.getParameter("workgroup");
        }

        byte[] bytes = getImage(imageName, workgroupName);
        if (bytes != null) {
            writeBytesToStream(bytes, response);
        }
    }


    /**
     * Writes out a <code>byte</code> to the ServletOuputStream.
     *
     * @param bytes the bytes to write to the <code>ServletOutputStream</code>.
     * @param response the HttpServeltResponse.
     */
    public void writeBytesToStream(byte[] bytes, HttpServletResponse response) {
        if (bytes == null) {
            return;
        }

        response.setContentType(CONTENT_TYPE);

        // Send back image
        try {
            ServletOutputStream sos = response.getOutputStream();
            sos.write(bytes);
            sos.flush();
            sos.close();
        }
        catch (IOException e) {
            // Ignore.
        }
    }

    /**
     * Returns the image bytes of the encoded image.
     *
     * @param imageName the name of the image.
     * @param workgroupName the name of the workgroup.
     * @return the image bytes found, otherwise null.
     */
    public byte[] getImage(String imageName, String workgroupName) {
        WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
        JID workgroupJID = new JID(workgroupName);

        Workgroup workgroup;
        try {
            workgroup = workgroupManager.getWorkgroup(workgroupJID);
        }
        catch (UserNotFoundException e) {
            Log.error(e);
            return null;
        }

        ChatSettings chatSettings = chatSettingsManager.getChatSettings(workgroup);
        ChatSetting setting = chatSettings.getChatSetting(imageName);
        String encodedValue = setting.getValue();
        if (encodedValue == null) {
            return null;
        }

        return StringUtils.decodeBase64(encodedValue);
    }
}
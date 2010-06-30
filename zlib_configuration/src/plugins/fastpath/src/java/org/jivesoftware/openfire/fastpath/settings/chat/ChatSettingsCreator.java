/**
 * $RCSfile$
 * $Revision: 26622 $
 * $Date: 2006-01-31 20:52:54 -0800 (Tue, 31 Jan 2006) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.fastpath.settings.chat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.xmpp.workgroup.UnauthorizedException;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import com.thoughtworks.xstream.XStream;

/**
 * Creates the default settings for the Web Chat UI. This includes the Web Chat UI
 * images and text settings.
 */
public class ChatSettingsCreator {

	private static final Logger Log = LoggerFactory.getLogger(ChatSettingsCreator.class);

    private static final ChatSettingsCreator instance = new ChatSettingsCreator();

    /**
     * Holds the default images to use.
     */
    private final Map<KeyEnum, String> imageMap = new HashMap<KeyEnum, String>();

    /**
     * Holds the default messages to use in the web client.
     */
    private final Map<KeyEnum, String> textMap = new HashMap<KeyEnum, String>();

    /**
     * Holds the default messages to use by the chatbot.
     */
    private final Map<KeyEnum, String> botMap = new HashMap<KeyEnum, String>();

    /**
     * Holds the label to use in the Admin Console while editing the property.
     */
    private final Map<KeyEnum, String> labelMap = new HashMap<KeyEnum, String>();

    /**
     * Holds the description to use in the Admin Console while editing the property.
     */
    private final Map<String, String> descriptions = new HashMap<String, String>();

    private final ChatSettingsManager chatSettingsManager = ChatSettingsManager.getInstance();

    /**
     * Returns the unique instance of this class.
     *
     * @return instance of class
     */
    public static ChatSettingsCreator getInstance() {
        return instance;
    }

    /**
     * Setups up the individual mappings to use for default settings.
     */
    private ChatSettingsCreator() {
        // Populate Image Map
        imageMap.put(KeyEnum.online_image, "online.gif");
        labelMap.put(KeyEnum.online_image, "Online");

        imageMap.put(KeyEnum.offline_image, "offline.gif");
        labelMap.put(KeyEnum.offline_image, "Offline");

        imageMap.put(KeyEnum.title_logo_image, "logo.gif");
        labelMap.put(KeyEnum.title_logo_image, "Title Logo");

        imageMap.put(KeyEnum.powered_by_image, "poweredBy.gif");
        labelMap.put(KeyEnum.powered_by_image, "Powered By");

        imageMap.put(KeyEnum.background_image, "white_background.gif");
        labelMap.put(KeyEnum.background_image, "Background");

        imageMap.put(KeyEnum.end_button_image, "end_button.gif");
        labelMap.put(KeyEnum.end_button_image, "End Button");

        imageMap.put(KeyEnum.secure_image, "secure_button.gif");
        labelMap.put(KeyEnum.secure_image, "Secure Button");

        imageMap.put(KeyEnum.agent_typing_image, "typing_button.gif");
        labelMap.put(KeyEnum.agent_typing_image, "Agent Typing Button");

        imageMap.put(KeyEnum.send_message_image, "send_button.gif");
        labelMap.put(KeyEnum.send_message_image, "Send Message Button");

        imageMap.put(KeyEnum.send_mail_image, "send_transcript_button.gif");
        labelMap.put(KeyEnum.send_mail_image, "Send Mail Button");

        // Populate Text Map
        populateTextMap();

        // Populate Bot Map
        botMap.put(KeyEnum.welcome_message, "Welcome to the workgroup '${workgroup}'.");
        labelMap.put(KeyEnum.welcome_message, "Greetings");

        botMap.put(KeyEnum.join_question, "Would you like to join the chat, yes or no?");
        labelMap.put(KeyEnum.join_question, "Join question");

        botMap.put(KeyEnum.bye_message, "Thanks for coming. We hope to see you soon again.");
        labelMap.put(KeyEnum.bye_message, "User is leaving");

        botMap.put(KeyEnum.routing_message, "You have entered a waiting queue. An agent will be with you soon");
        labelMap.put(KeyEnum.routing_message, "User has entered a queue");

        botMap.put(KeyEnum.position_message, "Your current position in the queue is ${position}");
        labelMap.put(KeyEnum.position_message, "Inform user position in the queue");

        botMap.put(KeyEnum.departure_confirmed_message, "You have left the waiting queue");
        labelMap.put(KeyEnum.departure_confirmed_message, "User cancelled request to join or request timedout");

        botMap.put(KeyEnum.cannot_join_message, "The workgroup is closed or you are not allowed to enter");
        labelMap.put(KeyEnum.cannot_join_message, "Request to join workgroup was denied");

        botMap.put(KeyEnum.fillout_form_message, "Please, fill out this form to contact an agent");
        labelMap.put(KeyEnum.fillout_form_message, "Inform user that a form must be completed");

        botMap.put(KeyEnum.not_acceptable_message, "Invalid or unknown command. Use !help for more information");
        labelMap.put(KeyEnum.not_acceptable_message, "User sent an unknown or invalid command");

        botMap.put(KeyEnum.not_in_queue_message, "Error, you are not in the waiting queue");
        labelMap.put(KeyEnum.not_in_queue_message, "User asked for his position in the queue but he is not in the queue");

        botMap.put(KeyEnum.workgroup_closed_message, "This workgroup is currently closed");
        labelMap.put(KeyEnum.workgroup_closed_message, "Workgroup is closed");

        botMap.put(KeyEnum.send_email_question, "Do you want to receive the chat transcript by email, yes or no?");
        labelMap.put(KeyEnum.send_email_question, "Email question");

        botMap.put(KeyEnum.send_get_email_question, "Please enter your email address to receive the chat transcript");
        labelMap.put(KeyEnum.send_get_email_question, "Enter Email address");

        botMap.put(KeyEnum.invitation_sent_message, "An invitation to start a chat with an agent has been sent");
        labelMap.put(KeyEnum.invitation_sent_message, "User is being routed to an agent");

        botMap.put(KeyEnum.send_invitation_question, "Do you want to receive another room invitation, yes or no?");
        labelMap.put(KeyEnum.send_invitation_question, "Send invitation again question");

        botMap.put(KeyEnum.invitation_resent_message, "The room invitation was sent again");
        labelMap.put(KeyEnum.invitation_resent_message, "Confirmation that the invitation was sent again");

        botMap.put(KeyEnum.email_sent_message, "Transcript sent to the following email address ${email}");
        labelMap.put(KeyEnum.email_sent_message, "Email was sent to the user");

        botMap.put(KeyEnum.back_command, "!back");
        labelMap.put(KeyEnum.back_command, "Text representing the 'back' command");

        botMap.put(KeyEnum.bye_command, "!bye");
        labelMap.put(KeyEnum.bye_command, "Text representing the 'bye' command");

        botMap.put(KeyEnum.help_command, "!help");
        labelMap.put(KeyEnum.help_command, "Text representing the 'help' command");

        botMap.put(KeyEnum.position_command, "!position");
        labelMap.put(KeyEnum.position_command, "Text representing the 'position' command");

        botMap.put(KeyEnum.repeat_command, "!repeat");
        labelMap.put(KeyEnum.repeat_command, "Text representing the 'repeat' command");

        botMap.put(KeyEnum.back_help_message, "!back - Use this command to return to the previous step.");
        labelMap.put(KeyEnum.back_help_message, "Description of the 'back' command");

        botMap.put(KeyEnum.bye_help_message, "!bye - Use this command to finish the chat.");
        labelMap.put(KeyEnum.bye_help_message, "Description of the 'bye' command");

        botMap.put(KeyEnum.help_help_message, "!help - Use this command to display the list of commands.");
        labelMap.put(KeyEnum.help_help_message, "Description of the 'help' command");

        botMap.put(KeyEnum.position_help_message, "!position - Use this command to learn your position in the queue.");
        labelMap.put(KeyEnum.position_help_message, "Description of the 'position' command");

        botMap.put(KeyEnum.repeat_help_message, "!repeat - Use this command to repeat the last question.");
        labelMap.put(KeyEnum.repeat_help_message, "Description of the 'repeat' command");

        // Define image descriptions

        descriptions.put("Online", "The button to show when agents of this workgroup are online.");
        descriptions.put("Offline", "The button to show when no agents in this workgroup are available to chat or offline.");
        descriptions.put("Title Logo", "Image shown at the upper left hand corner of the client.");
        descriptions.put("Powered By", "Powered by images is shown on the right-bottom corner of the client.");
        descriptions.put("Background", "Image used in the background of the client.");
        descriptions.put("Send Message Button", "The button used in the client to send messages.");
        descriptions.put("Send Mail Button", "The send button to use when sending email messages.");
        descriptions.put("End Button", "The button used to end a chat session.");
        descriptions.put("Secure Button", "Used to show a secure connection has been established.");
        descriptions.put("Agent Typing Button", "Used to show that the agent in the conversation is typing a message.");
    }

    /**
     * Adds the encoded imageMap to the database.
     *
     * @param workgroupJID - the jid of the workgroup to setup.
     */
    private void createImageSettings(JID workgroupJID) {
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        Plugin fastpathPlugin = pluginManager.getPlugin("fastpath");
        File fastpathPluginDirectory = pluginManager.getPluginDirectory(fastpathPlugin);

        File imagesDir = new File(fastpathPluginDirectory, "web/images");

        for (KeyEnum key : imageMap.keySet()) {
            String value = imageMap.get(key);

            File image = new File(imagesDir, value);
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(image);
            }
            catch (FileNotFoundException e) {
                Log.error(e.getMessage(), e);
            }

            if (stream != null) {
                byte[] bytes;
                try {
                    bytes = new byte[(int)image.length()];
                    int read = stream.read(bytes);
                    if (read != bytes.length) {
                        throw new IOException("Failed to read all image bytes."); 
                    }
                    stream.close();
                    final String encodedFile = StringUtils.encodeBase64(bytes);

                    createImageChatSetting(workgroupJID, key,
                            ChatSettings.SettingType.image_settings, encodedFile);
                }
                catch (IOException e) {
                    Log.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Adds the default text settings to the database.
     *
     * @param workgroupJID - the JID of the workgroup to setup.
     */
    private void createTextSettings(JID workgroupJID) {
        for (KeyEnum key : textMap.keySet()) {
            String value = textMap.get(key);
            createChatSetting(workgroupJID, key, ChatSettings.SettingType.text_settings, value);
        }
    }

    /**
     * Adds the default bot settings to the database.
     *
     * @param workgroupJID - the JID of the workgroup to setup.
     */
    private void createBotSettings(JID workgroupJID) {
        try {
            // Enable the workgroup chatbot by default
            Workgroup workgroup = WorkgroupManager.getInstance().getWorkgroup(workgroupJID);
            workgroup.chatbotEnabled(true);
            for (KeyEnum key : botMap.keySet()) {
                String value = botMap.get(key);
                createChatSetting(workgroupJID, key, ChatSettings.SettingType.bot_settings, value);
            }
        }
        catch (UserNotFoundException e) {
            Log.error(e.getMessage(), e);
        }
        catch (UnauthorizedException e) {
            Log.error(e.getMessage(), e);
        }
    }

    private void createChatSetting(JID workgroupJID, KeyEnum key, ChatSettings.SettingType type,
                                   final String value) {
        ChatSetting setting = new ChatSetting(key);
        setting.setWorkgroupNode(workgroupJID.getNode());
        setting.setType(type);
        setting.setValue(value);
        setting.setDefaultValue(value);

        String label = labelMap.get(key);

        if (!ModelUtil.hasLength(label)) {
            label = "";
        }

        String description = descriptions.get(label);
        if (!ModelUtil.hasLength(description)) {
            description = descriptions.get(key.toString());
        }

        if (!ModelUtil.hasLength(description)) {
            description = "";
        }


        setting.setDescription(description);
        setting.setLabel(label);

        chatSettingsManager.addChatSetting(setting);
    }

    private void createImageChatSetting(JID workgroupJID, KeyEnum key,
            ChatSettings.SettingType type, final String value)
    {
        ChatSetting setting = new ChatSetting(key);
        setting.setWorkgroupNode(workgroupJID.getNode());
        setting.setType(type);
        setting.setValue(value);
        setting.setDefaultValue("");

        String label = labelMap.get(key);

        if (!ModelUtil.hasLength(label)) {
            label = "";
        }

        String description = descriptions.get(label);
        if (!ModelUtil.hasLength(description)) {
            description = descriptions.get(key.toString());
        }

        if (!ModelUtil.hasLength(description)) {
            description = "";
        }

        setting.setDescription(description);
        setting.setLabel(label);

        chatSettingsManager.addChatSetting(setting);
    }

    /**
     * Add default settings to a workgroup.
     *
     * @param workgroupJID the full-jid of the workgroup.
     */
    public void createDefaultSettings(JID workgroupJID) {
        final XStream xstream = new XStream();
        xstream.alias("ChatSettings", ChatSettings.class);
        xstream.alias("Key", KeyEnum.class);
        xstream.alias("Setting", ChatSetting.class);

        createImageSettings(workgroupJID);
        createTextSettings(workgroupJID);
        createBotSettings(workgroupJID);
    }


    public void createDefaultSetting(JID workgroupJID, KeyEnum key) {
        if (imageMap.containsKey(key)) {
            String value = imageMap.get(key);
            InputStream stream = getClass().getResourceAsStream("fastpath/imageMap/" + value);
            if (stream == null) {
                stream = getClass().getResourceAsStream("/fastpath/imageMap/" + value);
            }

            if (stream == null) {
                stream = getClass().getResourceAsStream("/fastpath/images/" + value);
            }
            if (stream != null) {
                byte[] bytes;
                try {
                    bytes = new byte[stream.available()];
                    int read = stream.read(bytes);
                    if (read != bytes.length) {
                        throw new IOException("Failed to read all bytes.");
                    }
                    stream.close();
                    final String encodedFile = StringUtils.encodeBase64(bytes);

                    createChatSetting(workgroupJID, key, ChatSettings.SettingType.image_settings,
                            encodedFile);
                }
                catch (IOException e) {
                    Log.error(e.getMessage(), e);
                }
            }
        }
        else if (textMap.containsKey(key)) {
            String value = textMap.get(key);
            createChatSetting(workgroupJID, key, ChatSettings.SettingType.text_settings, value);
        }
        else if (botMap.containsKey(key)) {
            String value = botMap.get(key);
            createChatSetting(workgroupJID, key, ChatSettings.SettingType.bot_settings, value);
        }
    }

    private void populateTextMap() {
        textMap.put(KeyEnum.user_input_page_title, "<b>Start a Live Chat</b><p>Enter your name and all required information.</p>");
        labelMap.put(KeyEnum.user_input_page_title, "User Input Page Greeting");
        descriptions.put(KeyEnum.user_input_page_title.toString(), "The user information text displayed at the top of the page.");


        textMap.put(KeyEnum.start_chat_button, "Start Chat");
        labelMap.put(KeyEnum.start_chat_button, "Start Chat Button");
        descriptions.put(KeyEnum.start_chat_button.toString(), "Text to display on the button to put user into a queue.");


        textMap.put(KeyEnum.queue_title_text, "<b>Routing Your Request...</b><p>Your chat request is being routed. You can cancel your request by clicking \"Close Window\" below.</p>");
        labelMap.put(KeyEnum.queue_title_text, "Title Text In Queue Page");
        descriptions.put(KeyEnum.queue_title_text.toString(), "Text displayed to user while they are waiting in the queue.");

        textMap.put(KeyEnum.queue_description_text, "<div  style=\"border:1px #ccc solid; background-color:#ffe;padding:3px;\"><table border=\"0\"><tr><td><img src=\"images/busy.gif\"></td><td>" +
                "You are currently number ${position} in the queue. It is estimated that your wait time will be ${waitTime}.</td></tr></table></div>");
        labelMap.put(KeyEnum.queue_description_text, "Queue Position Text");
        descriptions.put(KeyEnum.queue_description_text.toString(), "Notifies user of their position in the queue and their estimated wait time.");

        textMap.put(KeyEnum.queue_footer_text, "Please stand by, or you can <a href=\"javascript:showLeaveAMessage()\">leave a message</a>.");
        labelMap.put(KeyEnum.queue_footer_text, "Queue Footer Text");
        descriptions.put(KeyEnum.queue_footer_text.toString(), "Footer text seen in queue.");

        textMap.put(KeyEnum.no_agent_text, "We are unable to route your request at this time. To leave a message or request a call back <a href=\"javascript:showLeaveAMessage()\">click here</a>.");
        labelMap.put(KeyEnum.no_agent_text, "No Agent Available");
        descriptions.put(KeyEnum.no_agent_text.toString(), "Message seen when no agent was able to take the request.");


        textMap.put(KeyEnum.accepted_chat_text, "You are now chatting with ${agent}");
        labelMap.put(KeyEnum.accepted_chat_text, "Chat Room Greeting");
        descriptions.put(KeyEnum.accepted_chat_text.toString(), "The message to display to the user when they first join the room.");


        textMap.put(KeyEnum.transferred_chat_text, "Your conversation is being transferred to another agent.");
        labelMap.put(KeyEnum.transferred_chat_text, "Agent Transfer Message");
        descriptions.put(KeyEnum.transferred_chat_text.toString(), "Message displayed to the user when an agent transfers their chat to another agent.");

        textMap.put(KeyEnum.agent_invite_text, "Inviting another agent to the conversation. Please wait.");
        labelMap.put(KeyEnum.agent_invite_text, "Agent Invitation Message");
        descriptions.put(KeyEnum.agent_invite_text.toString(), "Message displayed to the user when the agent invites another agent into the conversation");

        /*
        textMap.put(KeyEnum.chat_disconnected_text, "Your chat session has ended");
        labelMap.put(KeyEnum.chat_disconnected_text, "Chat session has ended");
        */

        textMap.put(KeyEnum.agent_ends_chat_text, "Your chat session has been ended by ${agent}");
        labelMap.put(KeyEnum.agent_ends_chat_text, "Agent ends the conversation");
        descriptions.put(KeyEnum.agent_ends_chat_text.toString(), "Message displayed to user when their session has been closed by the agent.");

        textMap.put(KeyEnum.transcript_text, "Thank you for chatting with us. Enter your email address below to have a copy of the transcript mailed to you.");
        labelMap.put(KeyEnum.transcript_text, "Message displayed on transcript email page");
        descriptions.put(KeyEnum.transcript_text.toString(), "Message displayed to the user on the email transcript page.");

        textMap.put(KeyEnum.transcript_sent_text, "The transcript has been sent. Thank you for chatting with us.");
        labelMap.put(KeyEnum.transcript_sent_text, "Transcript Sent Message");
        descriptions.put(KeyEnum.transcript_sent_text.toString(), "Message displayed to user when their transcript has been sent successfully");

        textMap.put(KeyEnum.transcript_not_sent_text, "The transcript could not be sent. We are sorry for the inconvience");
        labelMap.put(KeyEnum.transcript_not_sent_text, "Transcript Send Error");
        descriptions.put(KeyEnum.transcript_not_sent_text.toString(), "Message displayed when a transcript could not be sent.");


        textMap.put(KeyEnum.no_help, "<b>Live Chat Not Available</b><p>Sorry we were not available to handle your request. Please try again later.</p>");
        labelMap.put(KeyEnum.no_help, "No Help Available");
        descriptions.put(KeyEnum.no_help.toString(), "Message displayed when no agent is online and offline settings is not configured.");
    }
}
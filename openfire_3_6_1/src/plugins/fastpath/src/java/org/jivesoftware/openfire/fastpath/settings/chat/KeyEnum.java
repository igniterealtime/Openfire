/**
 * $RCSfile$
 * $Revision: 19437 $
 * $Date: 2005-08-02 17:17:34 -0700 (Tue, 02 Aug 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.fastpath.settings.chat;

/**
 * Defines all Chat Setting mapped keys.
 */
public enum KeyEnum {

    // User Input Page
    user_input_page_title("user_input_page_title"),
    start_chat_button("start_chat_button"),

    // Queue Page
    queue_title_text("queue_title_text"),
    queue_description_text("queue_description_text"),
    queue_footer_text("queue_footer_text"),

    // No agent available page
    no_agent_text("no_agent_text"),

    // Chat Room
    accepted_chat_text("acceptedChat_text"),
    chat_disconnected_text("chatDisconnected_text"),
    agent_ends_chat_text("chatSessionEnded_text"),
    agent_invite_text("inviteChat_text"),
    transferred_chat_text("transferChat_text"),

    // Email Transcripts
    transcript_text("transcript_window_text"),
    transcript_sent_text("transcript_send_text"),
    transcript_not_sent_text("transcript_not_sent_text"),

    // Offline
    no_help("no_help_text"),

    // Images
    agent_typing_image("agenttyping"),
    end_button_image("end"),
    title_logo_image("logo"),
    background_image("main"),
    offline_image("offline"),
    online_image("online"),
    powered_by_image("poweredby"),
    secure_image("secure"),
    send_mail_image("sendemail"),
    send_message_image("sendmessage"),

    // Keys used by the chatbot
    welcome_message("welcome_message"),
    join_question("join_question"),
    bye_message("bye_message"),
    routing_message("routing_message"),
    position_message("position_message"),
    departure_confirmed_message("departure_confirmed_message"),
    cannot_join_message("cannot_join_message"),
    fillout_form_message("fillout_form_message"),
    not_acceptable_message("not_acceptable_message"),
    not_in_queue_message("not_in_queue_message"),
    workgroup_closed_message("workgroup_closed_message"),
    send_email_question("send_email_question"),
    invitation_sent_message("invitation_sent_message"),
    send_invitation_question("send_invitation_question"),
    send_get_email_question("send_get_email_question"),
    invitation_resent_message("invitation_resent_message"),
    email_sent_message("email_sent_message"),
    back_command("back_command"),
    bye_command("bye_command"),
    help_command("help_command"),
    position_command("position_command"),
    repeat_command("repeat_command"),
    back_help_message("back_help_message"),
    bye_help_message("bye_help_message"),
    help_help_message("help_help_message"),
    position_help_message("position_help_message"),
    repeat_help_message("repeat_help_message"),

    // Keys used by search
    jive_knowledge_base("jive_knowledge_base"),
    jive_forums("jive_forums"),

    // Keys used by offline
    offline_web_page("offline_web_page"),
    offline_email_address("offline_email_address"),
    offline_subject("offline_subject"),
    offline_text("offline_text");

    private String key;

    KeyEnum(String k) {
        key = k;
    }

    public String toString() {
        return (key);
    }

}
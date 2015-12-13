<?php

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * 
 * @author Jignesh Nakrani <jignesh.nakrani@rtcamp.com>
 */
class RTMediaNotification {

    public $component_id;
    public $component_slug;
    public $component_callback;
    public $component_action;

    function __construct($args) {

        foreach ($args as $key => $val) {
            $this->{$key} = $val;
        }

        add_action('bp_setup_globals', array($this, 'notifier_setup_globals'));
    }

    /**
     * register a new component for notifications
     * @global type $bp
     */
    function notifier_setup_globals() {
        global $bp;
        $component = $this->component_id;
        $bp->{$component} = new stdClass();
        $bp->{$component}->id = $component;
        $bp->{$component}->slug = $this->component_slug;
        $bp->{$component}->notification_callback = $this->component_callback;
        $bp->active_components[$bp->{$component}->id] = $bp->{$component}->id;
    }

    /**
     * 
     * @param int $post_id          
     * @param int $post_author_id
     * @param int $user_id
     * 
     * @return int|bool  notification id on success or false
     */
    function add_notification( $post_id, $post_author_id, $user_id ) {
        $args_add_noification = array(
            'item_id' => $post_id,
            'user_id' => $post_author_id,
            'component_name' => $this->component_id,
            'component_action' => $this->component_action . $post_id,
            'secondary_item_id' => $user_id,
            'date_notified' => bp_core_current_time(),
        );
        global $rtmedia;
	if ( isset( $rtmedia->options[ "buddypress_enableNotification" ] ) && $rtmedia->options[ "buddypress_enableNotification" ] != "0" ) {
            return bp_notifications_add_notification( $args_add_noification );
        }
        return false;
    }

    /**
     * mark related notification as read once media is visit by user
     * 
     * @param   int     $media_id    ID of media to mark notification as read 
     */
    function mark_notification_unread($media_id) {
        $post_id = rtmedia_media_id($media_id);
        $user_id = get_current_user_id();
        bp_notifications_mark_notifications_by_type($user_id, $this->component_id, $this->component_action . $post_id, $is_new = false);
    }

    /**
     * deletes existing media notification of a perticular user
     * @param   int     $post_author_id       Author of post
     * @param   int     $post_id              ID of a post to delete related notification
     */
    function delete_notification_by_item_id($post_author_id, $post_id) {
        bp_notifications_delete_notifications_by_item_id($post_author_id, $post_id, $this->component_id, $this->component_action . $post_id);
    }

}

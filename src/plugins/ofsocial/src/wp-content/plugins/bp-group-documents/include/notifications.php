<?php
// Exit if accessed directly
if ( ! defined( 'ABSPATH' ) )
    exit ;

/**
 * bp_group_documents_screen_notification_settings()
 *
 * Adds notification settings for the component, so that a user can turn off email
 * notifications set on specific component actions.  These will be added to the
 * bottom of the existing "Group" settings
 * @version 2, 21/6/2013, stergatu, fix a bug which prevented the notifications setting to be saved
 */
function bp_group_documents_screen_notification_settings() {

    if ( ! $notification_group_documents_upload_member = bp_get_user_meta( bp_displayed_user_id() , 'notification_group_documents_upload_member' , true ) ) {
        $notification_group_documents_upload_member = 'yes' ;
    }
    if ( ! $notification_group_documents_upload_mod = bp_get_user_meta( bp_displayed_user_id() , 'notification_group_documents_upload_mod' , true ) ) {
        $notification_group_documents_upload_mod = 'yes' ;
    }
    ?>
    <tr id="groups-notification-settings-user-upload-file">
        <td></td>
        <td><?php _e( 'A member uploads a document to a group you belong to' , 'bp-group-documents' ) ; ?></td>
        <td class="yes"><input type="radio" name="notifications[notification_group_documents_upload_member]" value="yes" <?php checked( $notification_group_documents_upload_member , 'yes' , true ) ?>/></td>
        <td class="no"><input type="radio" name="notifications[notification_group_documents_upload_member]" value="no" <?php checked( $notification_group_documents_upload_member , 'no' , true ) ?>/></td>
    </tr>
    <tr>
        <td></td>
        <td><?php _e( 'A member uploads a document to a group for which you are an moderator/admin' , 'bp-group-documents' ) ?></td>
        <td class="yes"><input type="radio" name="notifications[notification_group_documents_upload_mod]" value="yes" <?php checked( $notification_group_documents_upload_mod , 'yes' , true ) ?>/></td>
        <td class="no"><input type="radio" name="notifications[notification_group_documents_upload_mod]" value="no" <?php checked( $notification_group_documents_upload_mod , 'no' , true ) ?>/></td>
    </tr>

    <?php do_action( 'bp_group_documents_notification_settings' ) ; ?>
    <?php
}

add_action( 'groups_screen_notification_settings' , 'bp_group_documents_screen_notification_settings' ) ;

/**
 * bp_group_documents_email_notificiation()
 *
 * This function will send email notifications to users on successful document upload.
 * For each group member, it will check to see the users notification settings first,
 * if the user has the notifications turned on, they will be sent a formatted email notification.
 * @version 2, include @jreeve fix http://wordpress.org/support/topic/document-upload-notification?replies=6#post-5464069
 */
function bp_group_documents_email_notification( $document ) {
    $bp = buddypress();
    ;

    $user_name = bp_core_get_userlink( $bp->loggedin_user->id , true ) ;
    $user_profile_link = bp_core_get_userlink( $bp->loggedin_user->id , false , true ) ;
    $group_name = $bp->groups->current_group->name ;
    $group_link = bp_get_group_permalink( $bp->groups->current_group ) ;
    $document_name = $document->name ;
    $document_link = $document->get_url() ;


    $subject = '[' . get_blog_option( 1 , 'blogname' ) . '] ' . sprintf( __( 'A document was uploaded to %s' , 'bp-group-documents' ) , $bp->groups->current_group->name ) ;

    //these will be all the emails getting the update
    //'user_id' => 'user_email
    $emails = array () ;

    //group users were handled differently in 1.1
    if ( '1.1' == substr( BP_VERSION , 0 , 3 ) ) {
        foreach ( $bp->groups->current_group->user_dataset as $user ) {
            if ( $user->is_admin || $user->is_mod ) {
                if ( 'no' == get_user_meta( $user->user_id , 'notification_group_documents_upload_mod' ) )
                    continue ;
            } else {
                if ( 'no' == get_user_meta( $user->user_id , 'notification_group_documents_upload_member' ) )
                    continue ;
            }
            $ud = get_userdata( $user->user_id ) ;
            $emails[ $user->user_id ] = $ud->user_email ;
        }
    } else { //1.2 and later
        //first get the admin & moderator emails
        if ( count( $bp->groups->current_group->admins ) ) {
            foreach ( $bp->groups->current_group->admins as $user ) {
                $mod_notif_prefs = get_user_meta( $user->user_id , 'notification_group_documents_upload_mod' ) ;
                if ( in_array( 'no' , $mod_notif_prefs ) ) {
                    continue ;
                }
                $emails[ $user->user_id ] = $user->user_email ;
            }
        }
        if ( count( $bp->groups->current_group->mods ) ) {
            foreach ( $bp->groups->current_group->mods as $user ) {
                $mod_notif_prefs = get_user_meta( $user->user_id , 'notification_group_documents_upload_mod' ) ;
                if ( in_array( 'no' , $mod_notif_prefs ) ) {
                    continue ;
                }
                if ( ! in_array( $user->user_email , $emails ) ) {
                    $emails[ $user->user_id ] = $user->user_email ;
                }
            }
        }

        //now get all member emails, checking to make sure not to send any emails twice
        $user_ids = BP_Groups_Member::get_group_member_ids( $bp->groups->current_group->id ) ;
        foreach ( ( array ) $user_ids as $user_id ) {
            $member_notif_prefs = get_user_meta( $user_id , 'notification_group_documents_upload_member' ) ;
            if ( in_array( 'no' , $member_notif_prefs ) ) {
                continue ;
            }
            $ud = bp_core_get_core_userdata( $user_id ) ;
            if ( ! in_array( $ud->user_email , $emails ) ) {
                $emails[ $user_id ] = $ud->user_email ;
            }
        }
    }

    foreach ( $emails as $current_id => $current_email ) {
        $message = sprintf( __(
                        '%s uploaded a new file: %s to the group: %s.

To see %s\'s profile: %s

To see the group %s\'s homepage: %s

To download the new document directly: %s

------------------------
' , 'bp-group-documents' ) , $user_name , $document_name , $group_name , $user_name , $user_profile_link , $group_name , $group_link , $document_link ) ;


        $settings_link = bp_core_get_user_domain( $current_id ) . $bp->settings->slug . '/notifications/' ;
        $message .= sprintf( __( 'To disable these notifications please log in and go to: %s' , 'bp-group-documents' ) , $settings_link ) ;

        // Set up and send the message
        $to = $current_email ;
        wp_mail( $to , $subject , $message ) ;
        unset( $to , $message ) ;
    } //end foreach
}

add_action( 'bp_group_documents_add_success' , 'bp_group_documents_email_notification' , 10 ) ;


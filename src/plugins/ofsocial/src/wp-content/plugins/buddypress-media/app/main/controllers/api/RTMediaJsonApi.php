<?php
/**
 * File Description
 * @author Umesh Kumar <umeshsingla05@gmail.com>
 */
class RTMediaJsonApi{

    var $ec_method_missing = 600001,
        $msg_method_missing = 'no method specified',
        $ec_token_missing = 600002, 
        $msg_token_missing = 'token empty', 
        $ec_token_invalid = 600003, 
        $msg_token_invalid = 'token invalid', 
        $ec_server_error = 600004, 
        $msg_server_error = 'server error', 
        $ec_media_activity_id_missing = 600005, 
        $msg_media_activity_id_missing = 'media/activity id missing', 
        $ec_invalid_media_id = 600006, 
        $msg_invalid_media_id = 'invalid media id',
        $ec_invalid_request_type = 600007,
        $msg_invalid_request_type = 'invalid request type',
        $ec_bp_missing = 600008,
        $msg_bp_missing = 'buddypress not found',
        $ec_api_disabled = 600009,
        $msg_api_disabled = 'API disabled by site administrator',
        $rtmediajsonapifunction,
        $user_id = '';

    function __construct(){
        if (!class_exists('RTMediaApiLogin') || !class_exists('RTMediaJsonApiFunctions')) {
            return;
        }

        add_action('wp_ajax_nopriv_rtmedia_api', array($this, 'rtmedia_api_process_request') );
        add_action('wp_ajax_rtmedia_api', array($this, 'rtmedia_api_process_request') );
    }

    function rtmedia_api_process_request(){
        $rtmedia_enable_json_api = FALSE;
        if(function_exists('rtmedia_get_site_option')){
            $rtmedia_options = rtmedia_get_site_option('rtmedia-options');
            if(!empty($rtmedia_options)){
                if($rtmedia_options['rtmedia_enable_api']){
                    $rtmedia_enable_json_api = TRUE;
                }
            }
        }
        if(!$rtmedia_enable_json_api){
            echo $this->rtmedia_api_response_object( 'FALSE', $this->ec_api_disabled, $this->msg_api_disabled );
            die;
        }
        if ( empty ( $_POST['method'] )  ){
            echo $this->rtmedia_api_response_object( 'FALSE', $this->ec_method_missing, $this->msg_method_missing );
            die;
        }
        if (!class_exists('BuddyPress')) {
            echo $this->rtmedia_api_response_object( 'FALSE', $this->ec_bp_missing, $this->msg_bp_missing );
            die;
        }
        $this->rtmediajsonapifunction = new RTMediaJsonApiFunctions();

        if(!empty($_POST['token'])){
            $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
            $this->user_id = $this->rtmediajsonapifunction->rtmedia_api_get_user_id_from_token($_POST['token']);
            //add filter
            add_filter('rtmedia_current_user', array($this->rtmediajsonapifunction, 'rtmedia_api_set_user_id'));
        }
        //Process Request
        $method = !empty( $_POST['method'] ) ? $_POST['method']: '';

        switch ( $method ){

            case 'wp_login':
                $this->rtmedia_api_process_wp_login_request();
                break;
            case 'wp_logout':
               $this->rtmedia_api_process_wp_logout_request();
                break;
            case 'wp_register':
                $this->rtmedia_api_process_wp_register_request();
                break;
            case 'wp_forgot_password':
               $this->rtmedia_api_process_wp_forgot_password_request();
               break;
           case 'bp_get_profile':
               $this->rtmedia_api_process_bp_get_profile_request();
               break;
           case 'bp_get_activities':
               $this->rtmedia_api_process_bp_get_activities_request();
               break;
           case 'add_rtmedia_comment':
               $this->rtmedia_api_process_add_rtmedia_comment_request();
               break;
           case 'like_media':
               $this->rtmedia_api_process_like_media_request();
               break;
           case 'get_rtmedia_comments':
               $this->rtmedia_api_process_get_rtmedia_comments_request();
               break;
           case 'get_likes_rtmedia':
               $this->rtmedia_api_process_get_likes_rtmedia_request();
               break;
           case 'remove_comment':
               $this->rtmedia_api_process_remove_comment_request();
               break;
           case 'update_profile':
               $this->rtmedia_api_process_update_profile_request();
               break;
           case 'rtmedia_upload_media':
               $this->rtmedia_api_process_rtmedia_upload_media_request();
               break;
           case 'rtmedia_gallery':
               $this->rtmedia_api_process_rtmedia_gallery_request();
               break;
           case 'rtmedia_get_media_details':
               $this->rtmedia_api_process_rtmedia_get_media_details_request();
               break;
           default:
               echo $this->rtmedia_api_response_object( 'FALSE', $this->ec_invalid_request_type, $this->msg_invalid_request_type );
               exit;
        }

        die(1);
    }
    /**
     * Returns a json object
     * @param type $status
     * @param type $status_code
     * @param type $message
     * @param type $data
     * @return boolean
     */
    function rtmedia_api_response_object( $status, $status_code, $message, $data = false ){
        if ( $status === '' || empty( $status_code ) || empty( $message ) ) {
            return false;
            exit;
        }

        if (ob_get_contents()) {
            ob_end_clean();
        }
        global $wpdb;
        $rtmapilogin = new RTMediaApiLogin();
        $login_details = array( 'last_access'   => $wpdb->get_var("SELECT current_timestamp();") );
        if( !empty( $_POST['token'] ) ){
            $where = array('user_id'  => $this->user_id, 'token' => $_POST['token'] );
        }
        if( !empty( $where ) ){
            $rtmapilogin->update( $login_details, $where);
        }
        $response_object = array();
        $response_object['status']  = $status;
        $response_object['status_code']  = $status_code;
        $response_object['message']  = $message;
        $response_object['data']  = $data;

        $response_object = json_encode( $response_object );
        return $response_object;
    }
    /**
     * Takes username and password, if succesful returns a access token
     */
    function rtmedia_api_process_wp_login_request(){
        //Login Errors and Messages
        $ec_user_pass_missing = 200001;
        $msg_user_pass_missing = __('username/password empty', 'buddypress-media' );

        $ec_incorrect_username = 200002;
        $msg_incorrect_username = __('incorrect username', 'buddypress-media' );

        $ec_incorrect_pass = 200003;
        $msg_incorrect_pass = __('incorrect password', 'buddypress-media' );

        $ec_login_success = 200004;
        $msg_login_success = __('login success', 'buddypress-media' );

        if ( empty( $_POST['username'] ) || empty( $_POST['password'] ) ){
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_user_pass_missing, $msg_user_pass_missing );
            exit;
        } else{
            $user_login = wp_authenticate( trim( $_POST['username'] ), trim( $_POST['password'] ) );
            if (is_wp_error( $user_login ) ){

                $incorrect_password = !empty( $user_login->errors['incorrect_password'] ) ? TRUE : FALSE;
                $incorrect_username = !empty( $user_login->errors['invalid_username'] ) ? TRUE : FALSE;
                if ( $incorrect_password ){
                    echo $this->rtmedia_api_response_object( 'FALSE', $ec_incorrect_pass, $msg_incorrect_pass );
                    exit;
                }elseif ( $incorrect_username ) {
                    echo $this->rtmedia_api_response_object( 'FALSE', $ec_incorrect_username, $msg_incorrect_username );
                    exit;
                }

            }else{
                $access_token = $this->rtmediajsonapifunction->rtmedia_api_get_user_token( $user_login->ID, $user_login->data->user_login );
                $data = array(
                    'access_token' => $access_token,
                );
                echo $this->rtmedia_api_response_object( 'TRUE', $ec_login_success, $msg_login_success, $data );

                $rtmapilogin = new RTMediaApiLogin();

                //update all tokens for user to exired on each login
                $rtmapilogin->update( array('status'  => 'FALSE' ), array('user_id' => $user_login->ID ) );
                $login_details = array( 'user_id'   => $user_login->ID,
                    'ip'    => $_SERVER['REMOTE_ADDR'],
                    'token' =>  $access_token,
                    'token_time'    => date("Y-m-d H:i:s")
                );
                $rtmapilogin->insert( $login_details );
            }
        }
    }
    /**
     * register a user through api request
     * requires signup_* => display_name, username, password, confirm password, location,
     */
    function rtmedia_api_process_wp_register_request(){
        //Registration errors and messages
        $ec_register_fields_missing = 300001;
        $msg_register_fields_missing = __('fields empty', 'buddypress-media' );

        $ec_invalid_email = 300002;
        $msg_invalid_email = __('invalid email', 'buddypress-media' );

        $ec_pass_do_not_match = 300003;
        $msg_pass_do_not_match = __('password do not match', 'buddypress-media' );

        $ec_username_exists = 300004;
        $msg_username_exists = __('username already registered', 'buddypress-media' );

        $ec_email_exists = 300005;
        $msg_email_existsh = __('email already exists', 'buddypress-media' );

        $ec_user_insert_success = 300007;
        $msg_user_insert_success = __('new user created', 'buddypress-media' );

        $registration_fields = array('username', 'email', 'password', 'password_confirm');
        //fields empty field_1, field_4
        if ( empty( $_POST['field_1'] ) ) {
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_register_fields_missing, $msg_register_fields_missing );
            exit;
        }
        foreach ( $registration_fields as $field_name ){
            if ( empty( $_POST['signup_'.$field_name] ) ) {
                echo $this->rtmedia_api_response_object( 'FALSE', $ec_register_fields_missing, $msg_register_fields_missing );
                exit;
            }
        }
        //incorrect email
        if ( !is_email( $_POST['signup_email'] ) ){
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_invalid_email, $msg_invalid_email );
            exit;
        }
        //Passwords do not match
        elseif ( $_POST['signup_password'] !== $_POST['signup_password_confirm'] ){
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_pass_do_not_match, $msg_pass_do_not_match );
            exit;
        }
        //Username already registered
        elseif ( username_exists( $_POST['signup_username'] ) ){
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_username_exists, $msg_username_exists );
            exit;
        }
        //email already registered
        elseif (email_exists( $_POST['signup_email'] ) ){
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_email_exists, $msg_email_existsh );
            exit;
        }else{
            $userdata = array(
                'user_login'    => $_POST['signup_username'],
                'user_pass'     => $_POST['signup_password'],
                'display_name'  => $_POST['field_1']
            );

            $user_id = wp_insert_user( $userdata );
            if ( !is_wp_error( $user_id )){
                echo xprofile_get_field_id_from_name('field_1');
                xprofile_set_field_data( 1, $user_id, $_POST['field_1'] );
                update_user_meta( $user_id, 'register_source', 'site_api' );
                echo $this->rtmedia_api_response_object('TRUE', $ec_user_insert_success, $msg_user_insert_success );
                exit;
            }else{
                echo $this->rtmedia_api_response_object( 'FALSE', $this->ec_server_error, $this->msg_server_error );
                exit;
            }
        }
    }
    /**
     * Sends a reset link to user email
     * @global type $wpdb
     */
    function rtmedia_api_process_wp_forgot_password_request(){
        global $wpdb;
         //Registration errors and messages
        $ec_email_missing = 500001;
        $msg_email_missing = __('email empty', 'buddypress-media' );

        $ec_username_email_not_registered = 500002;
        $msg_username_email_not_registered = __('username/email not registered', 'buddypress-media' );

        $ec_email_sent = 500003;
        $msg_email_sent = __('reset link sent', 'buddypress-media' );

        if ( empty( $_POST['user_login'] ) ) { echo $this->rtmedia_api_response_object('FALSE', $ec_email_missing, $msg_email_missing ); exit; }

        if ( username_exists( $_POST['user_login'] ) ){
            $user_exists = true;
            $user = get_user_by('login', $_POST['user_login'] );
        }
        // Then, by e-mail address
        elseif( email_exists( $_POST['user_login'] ) ){
                $user_exists = true;
                $user = get_user_by('email', $_POST['user_login'] );
        }else{
            echo $this->rtmedia_api_response_object('FALSE', $ec_username_email_not_registered, $msg_username_email_not_registered ); 
            exit;
        }
        $user_login = $user->data->user_login;
        $user_email = $user->data->user_email;

        // Generate something random for a key...
        $key = wp_generate_password(20, false);
        do_action('retrieve_password_key', $user_login, $key);
        // Now insert the new md5 key into the db
        // Now insert the key, hashed, into the DB.
	if ( empty( $wp_hasher ) ) {
		require_once ABSPATH . 'wp-includes/class-phpass.php';
		$wp_hasher = new PasswordHash( 8, true );
	}
	$hashed = $wp_hasher->HashPassword( $key );
	$wpdb->update( $wpdb->users, array( 'user_activation_key' => $hashed ), array( 'user_login' => $user_login ) );

        //create email message
        $message = __('Someone has asked to reset the password for the following site and username.', 'buddypress-media') . "\r\n\r\n";
        $message .= get_option('siteurl') . "\r\n\r\n";
        $message .= sprintf(__('Username: %s', 'buddypress-media'), $user_login) . "\r\n\r\n";
        $message .= __('To reset your password visit the following address, otherwise just ignore this email and nothing will happen.', 'buddypress-media') . "\r\n\r\n";
        $message .='<' . network_site_url("wp-login.php?action=rp&key=$key&login=" . rawurlencode($user_login), 'login') . ">\r\n";
        //send email meassage
        if (FALSE == wp_mail($user_email, sprintf(__('[%s] Password Reset','buddypress-media'), get_option('blogname')), $message))
            echo $this->rtmedia_api_response_object ('FALSE', $this->ec_server_error, $this->msg_server_error);
        else{
            echo $this->rtmedia_api_response_object ('TRUE', $ec_email_sent, $msg_email_sent);
        }
        exit;
    }
    /**
     * Sends a reset link to user email
     * @global type $wpdb
     */
    function rtmedia_api_process_bp_get_activities_request(){
        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
        //Feed Errors
        $ec_latest_feed = 700001;
        $msg_latest_feed = __('bp activities', 'buddypress-media' );

        $ec_my_looks = 700002;
        $msg_my_looks = __('user activities', 'buddypress-media' );

        //Fetch user id from token
        $activity_user_id = '';
        extract($_REQUEST);
        $per_page = !empty($_REQUEST['per_page']) ? $_REQUEST['per_page'] : 10;
        $activity_feed = $this->rtmediajsonapifunction->rtmedia_api_get_feed($activity_user_id, '', $per_page);
        if( empty($activity_feed) ){
            $activity_feed = 'no updates';
        }
        if ( !empty( $activity_user_id ) ){
            echo $this->rtmedia_api_response_object('TRUE', $ec_my_looks, $msg_my_looks, $activity_feed ); 
        }else{
            echo $this->rtmedia_api_response_object('TRUE', $ec_latest_feed, $msg_latest_feed, $activity_feed ); 
        }
        exit;
    }
    /**
     * Post comment on activity_id or media_id
     * @global type $this->msg_server_error
     * @global int $this->ec_server_error
     * @global int $this->ec_invalid_media_id
     * @global type $this->msg_invalid_media_id
     */
    function rtmedia_api_process_add_rtmedia_comment_request(){

        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
        $this->rtmediajsonapifunction->rtmedia_api_media_activity_id_missing();
        //Post comment errors
        $ec_comment_content_missing = 800001;
        $msg_comment_content_missing = __('comment content missing', 'buddypress-media' );

        $ec_comment_posted = 800002;
        $msg_comment_posted = __('comment posted', 'buddypress-media' );

         //Fetch user id from token
        $user_data = get_userdata( $this->user_id );
        if ( empty( $_POST['content'] ) ) {
            echo $this->rtmedia_api_response_object('FALSE', $ec_comment_content_missing, $msg_comment_content_missing ); 
            exit;
        }
        extract($_POST);

        if ( empty( $activity_id ) && !empty( $media_id ) ){
            $activity_id =  $this->rtmediajsonapifunction->rtmedia_api_activityid_from_mediaid( $media_id );
        }
        if(empty($activity_id)){
            echo $this->rtmedia_api_response_object('FALSE', $this->ec_invalid_media_id, $this->msg_invalid_media_id ); 
            exit;
        }
        $args = array(
            'content'     => $content,
            'activity_id' => $activity_id,
            'user_id'   => $this->user_id,
            'parent_id'   => false
        );
        if ( function_exists ( 'bp_activity_new_comment' ) ) {
            $comment_id = bp_activity_new_comment ( $args );
        }
        if ( $comment_id ) {
            echo $this->rtmedia_api_response_object('TRUE', $ec_comment_posted, $msg_comment_posted ); 
            exit;
        }else{
            echo $this->rtmedia_api_response_object('FALSE', $this->msg_server_error, $this->ec_server_error ); 
            exit;
        }
    }
    /**
     * Like/Unlike by media_id or activity_id
     * @global int $this->ec_server_error
     * @global type $this->msg_server_error
     * @global int $this->ec_invalid_media_id
     * @global type $this->msg_invalid_media_id
     */
    function rtmedia_api_process_like_media_request(){
        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
        $this->rtmediajsonapifunction->rtmedia_api_media_activity_id_missing();

        //Like errors
        $ec_already_liked = 900001;
        $msg_already_liked = __('unliked media', 'buddypress-media' );

        $ec_liked_media = 900002;
        $msg_liked_media = __('liked media', 'buddypress-media' );

        extract($_POST);

        if(class_exists('RTMediaInteractionModel') ):
        $rtmediainteraction = new RTMediaInteractionModel();

        if(class_exists('RTMediaLike') )
        $rtmedialike = new RTMediaLike();

        $action = 'like';
        // Like or Unlike
        if( !rtmedia_media_id( $media_id) ){
            echo $this->rtmedia_api_response_object( 'FALSE', $this->ec_invalid_media_id, $this->msg_invalid_media_id );
            exit;
        }

        $like_count_old = get_rtmedia_like( rtmedia_media_id( $media_id) );   
        $check_action = $rtmediainteraction->check( $this->user_id, $media_id, $action);
        if($check_action) {
            $results = $rtmediainteraction->get_row( $this->user_id, $media_id, $action);
            $row = $results[0];
            $curr_value = $row->value;
            if($curr_value == "1") {
                $value = "0";
                $increase =false;
            } else {
                $value = "1";
                $increase = true;
            }
            $update_data = array('value' => $value);
            $where_columns = array(
                'user_id' =>  $this->user_id,
                'media_id' => $media_id,
                'action' => $action,
            );
            $update = $rtmediainteraction->update($update_data, $where_columns);
        } else {
            $value = "1";
            $columns = array(
                'user_id' =>  $this->user_id,
                'media_id' => $media_id,
                'action' => $action,
                'value' => $value
            );
            $insert_id = $rtmediainteraction->insert($columns);
            $increase = true;
        }
       if ( $increase ){
           $like_count_old++;
       }elseif(!$increase){
           $like_count_old--;
       }
       if($like_count_old < 0 ){
           $like_count_old = 0;
       }
       $data = array('like_count' =>  $like_count_old );
       if ( !empty( $insert_id ) ){
            $rtmedialike->model->update( array( 'likes' => $like_count_old ), array( 'id' => $media_id ) );
            echo $this->rtmedia_api_response_object( 'TRUE', $ec_liked_media, $msg_liked_media, $data );
            exit;
       }elseif ( !empty( $update ) ){
           $rtmedialike->model->update( array( 'likes' => $like_count_old ), array( 'id' => $media_id ) );
           if ( $value == 1 ){
               echo $this->rtmedia_api_response_object( 'TRUE', $ec_liked_media, $msg_liked_media, $data );
               exit;
           }elseif( $value == 0 ){
                echo $this->rtmedia_api_response_object( 'TRUE', $ec_already_liked, $msg_already_liked, $data );
                exit;
           }
       }else{
            echo $this->rtmedia_api_response_object( 'FALSE', $this->ec_server_error, $this->msg_server_error );
            exit;
       }
       endif;
    }
    /**
     * Fetch Comments by media id
     * @global type $wpdb
     */
    function rtmedia_api_process_get_rtmedia_comments_request(){
        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
        //Errors Fetching comment
        $ec_no_comments = 800003;
        $msg_no_comments = __('no comments', 'buddypress-media' );

        $ec_media_comments = 800004;
        $msg_media_comments = __('media comments', 'buddypress-media' );

        $ec_my_comments = 800005;
        $msg_my_comments = __('my comments', 'buddypress-media' );

        extract($_REQUEST);
        global $wpdb;
        if ( empty( $media_id ) ){

            $user_data = $this->rtmediajsonapifunction->rtmedia_api_user_data_from_id($this->user_id);
            $comments = $wpdb->get_results ( "SELECT * FROM $wpdb->comments WHERE user_id = '" . $this->user_id . "'", ARRAY_A );
            $my_comments = array();
            if ( !empty( $comments ) ){
                foreach ( $comments as $comment ){
                    $my_comments['comments'][] =   array(
                        'comment_ID'    =>  $comment['comment_ID'],
                        'comment_content'   => $comment['comment_content'],
                        'media_id'  => $comment['comment_post_ID']
                    );
                }
                $my_comments['user']    =   array(
                    'user_id'   => $this->user_id,
                    'name'  => $user_data['name'],
                    'avatar'    => $user_data['avatar']
                );

                echo $this->rtmedia_api_response_object( 'TRUE', $ec_media_comments, $msg_media_comments, $my_comments );
                exit;
            }
        }else{
            $media_comments = $this->rtmediajsonapifunction->rtmedia_api_get_media_comments($media_id);
            if( $media_comments ){
                echo $this->rtmedia_api_response_object( 'TRUE', $ec_media_comments, $msg_media_comments, $media_comments );
                exit;
            }else{
                echo $this->rtmedia_api_response_object( 'FALSE', $ec_no_comments, $msg_no_comments );
                exit;
            }
        }
        //If no comments
        echo $this->rtmedia_api_response_object( 'FALSE', $ec_no_comments, $msg_no_comments );
        exit;
    }
    /**
     * Fetch Likes by media id
     * @global type $wpdb
     */
    function rtmedia_api_process_get_likes_rtmedia_request(){
        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
        $this->rtmediajsonapifunction->rtmedia_api_media_activity_id_missing();
        global $wpdb;
        //Errors Fetching Likes
        $ec_no_likes = 900003;
        $msg_no_likes = __('no likes', 'buddypress-media' );

        $ec_media_likes = 900004;
        $msg_media_likes = __('media likes', 'buddypress-media' );
        $media_like_users = array();
        $media_likes = array();
        $media_likes['user'] = array();
        extract($_POST);
        $media_like_users = $this->rtmediajsonapifunction->rtmedia_api_media_liked_by_user($media_id);
        if ( !empty( $media_like_users ) ){
            foreach ( $media_like_users as $like_details){
                if ( !array_key_exists(  $like_details->user_id, $media_likes['user'] ) ){

                    $user_data = $this->rtmediajsonapifunction->rtmedia_api_user_data_from_id(  $like_details->user_id );
                    $mysql_time = $wpdb->get_var('select CURRENT_TIMESTAMP()');
                    $like_time = human_time_diff( strtotime($like_details->action_date), strtotime( $mysql_time ) );
                    $media_likes['likes'][] = array(
                        'activity_time' =>  $like_time,
                        'user_id'   =>  $like_details->user_id
                    );
                    $media_likes['user'][$like_details->user_id]    =   array(
                        'name'  => $user_data['name'],
                        'avatar'    => $user_data['avatar']
                    );
                }
            }
        }
       if( !empty ( $media_likes ) ){
          echo $this->rtmedia_api_response_object( 'TRUE', $ec_media_likes, $msg_media_likes, $media_likes );
          exit;
        }else{
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_no_likes, $msg_no_likes );
            exit;
        }
    }
    /**
     * Delete comment by activity id or media id
     */
    function rtmedia_api_process_remove_comment_request(){
        global $wpdb;
        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
        $this->rtmediajsonapifunction->rtmedia_api_media_activity_id_missing();
        //Errors Deleting comment

        $ec_comment_not_found = 800007;
        $msg_comment_not_found = __('invalid comment/media id', 'buddypress-media' );

        $ec_no_comment_id = 800008;
        $msg_no_comment_id = __('no comment id', 'buddypress-media' );

        $ec_comment_deleted = 800009;
        $msg_comment_deleted = __('comment deleted', 'buddypress-media' );
        extract($_POST);

        if ( empty( $comment_id ) ){
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_no_comment_id, $msg_no_comment_id );
            exit;
        }
        $id = rtmedia_media_id($media_id);

        $sql = "SELECT * FROM {$wpdb->comments} WHERE comment_ID = " . $comment_id . " AND comment_post_ID = " . $id. " AND user_id = " . $this->user_id;

        $comments = $wpdb->get_results ( $sql, ARRAY_A );
        //Delete Comment
        if ( !empty( $comments ) ) {
            $comment = new RTMediaComment();

            $activity_id = get_comment_meta($comment_id, 'activity_id',true);

            if(!empty($activity_id)){
                $activity_deleted = bp_activity_delete_comment ($activity_id, $comment_id);

                $delete = bp_activity_delete( array( 'id' => $activity_id, 'type' => 'activity_comment' ) );

            }
            $comment_deleted = $comment->rtmedia_comment_model->delete($comment_id);;

            if ( $comment_deleted ){
                echo $this->rtmedia_api_response_object( 'TRUE', $ec_comment_deleted, $msg_comment_deleted );
                exit;
            }else{
                echo $this->rtmedia_api_response_object( 'FALSE', $this->ec_server_error, $this->msg_server_error );
                exit;
            }

        }else{
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_comment_not_found, $msg_comment_not_found );
            exit;
        }
    }
    function rtmedia_api_process_bp_get_profile_request(){
        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
        //Errors
        $ec_no_fields = 400001;
        $msg_no_fields = __('no profile found', 'buddypress-media' );

        $ec_profile_fields = 400002;
        $msg_profile_fields = __('profile fields', 'buddypress-media' );

        $profile_fields = array();
        $user_id = $loggedin_user_id = '';
        extract($_REQUEST);
        if(empty($user_id)){
            $user_id    = $this->user_id;
        }else{
            $loggedin_user_id = $this->user_id;
        }
        $user = get_userdata($user_id);
        if(empty($user)){
            echo $this->rtmedia_api_response_object( 'TRUE', $ec_no_fields, $msg_no_fields);
            exit;
        }
        $user_data = $this->rtmediajsonapifunction->rtmedia_api_user_data_from_id($user_id, 250, 250, 'full' );
        $profile_fields['id']   = $user_id;
        $profile_fields['avatar']['src']   =   $user_data['avatar'];
        $profile_fields['avatar']['width']   =   250;
        $profile_fields['avatar']['height']   =   250;

        if ( bp_has_profile(array('user_id' => $user_id) ) ) :
            while ( bp_profile_groups() ) : bp_the_profile_group();

                if ( bp_profile_group_has_fields() ) :

                    while ( bp_profile_fields() ) : bp_the_profile_field();

                        if ( bp_field_has_data() ) :

                            $profile_fields['fields'][bp_get_the_profile_field_name()] = array(
                              'value'   =>  strip_tags( bp_get_the_profile_field_value() ),
                              'privacy' => bp_get_the_profile_field_visibility_level()
                            );
                        endif;

                    endwhile;
                endif;
            endwhile;
        else: 
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_no_fields, $msg_no_fields);
            exit;
        endif;
        //If followers plugin exists
        if( function_exists('rtmedia_api_followers') ){
            $followers = rtmedia_api_followers($user_id);
            $following = rtmedia_api_following($user_id);

            foreach ( $followers as $follower ){
                $follower_data = $this->rtmediajsonapifunction->rtmedia_api_user_data_from_id( $follower, 66, 66 );
                $profile_fields['follower'][] = array(
                  'id'    => $follower,
                  'name'    => $follower_data['name'],
                  'avatar'    => $follower_data['avatar'],
                );
            }

            foreach ( $following as $follow ){
                $follow_data = $this->rtmediajsonapifunction->rtmedia_api_user_data_from_id( $follow, 66, 66 );
                $profile_fields['following'][] = array(
                  'id'    => $follow,
                  'name'    => $follow_data['name'],
                  'avatar'    => $follow_data['avatar'],
                );
            }
        }
        if(!empty($_REQUEST['user_id']) && $loggedin_user_id != $user_id ){
            $args   = array(
                'leader_id' =>  $user_id,
                'follower_id'   => $loggedin_user_id
            );
            if(function_exists('bp_follow_is_following')){
                $profile_fields['loggedin_user']['following'] = 'FALSE';
                if (bp_follow_is_following( $args )){
                    $profile_fields['loggedin_user']['following'] = 'TRUE';
                }

                $args   = array(
                    'leader_id' =>  $loggedin_user_id,
                    'follower_id'   => $user_id
                );
                $profile_fields['loggedin_user']['followed'] = 'FALSE';
                if (bp_follow_is_following( $args )){
                    $profile_fields['loggedin_user']['followed'] = 'TRUE';
                }
            }
        }
        echo $this->rtmedia_api_response_object( 'TRUE', $ec_profile_fields, $msg_profile_fields, $profile_fields);
        exit;
    }

    function rtmedia_api_process_follow_request(){
        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
        $ec_empty_follow_id = 400003;
        $msg_empty_follow_id = __('follow user id missing', 'buddypress-media' );

        $ec_started_following = 400004;
        $msg_started_following = __('started following', 'buddypress-media' );

        $ec_already_following = 400005;
        $msg_already_following = __('already following', 'buddypress-media' );

        extract($_POST);

        if ( empty( $follow_id ) ){
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_empty_follow_id, $msg_empty_follow_id );
            exit;
        }
        $args   = array(
            'leader_id' =>  $follow_id,
            'follower_id'   => $this->user_id
        );
        $already_following = bp_follow_is_following($args);
        if( !$already_following ){
            $follow_user = bp_follow_start_following($args);
            if ( $follow_user ){
                echo $this->rtmedia_api_response_object( 'TRUE', $ec_started_following, $msg_started_following );
                exit;
            }
            else{
                echo $this->rtmedia_api_response_object( 'TRUE', $this->ec_server_error, $this->msg_server_error );
                exit;
            }
        }else{
            echo $this->rtmedia_api_response_object( 'TRUE', $ec_already_following, $msg_already_following );
            exit;
        }
    }
    function rtmedia_api_process_unfollow_request(){
        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();

        $ec_empty_unfollow_id = 400006;
        $msg_empty_unfollow_id = __('unfollow id missing', 'buddypress-media' );

        $ec_stopped_following = 400007;
        $msg_stopped_following = __('stopped following', 'buddypress-media' );

        $ec_not_following = 400008;
        $msg_not_following = __('not following', 'buddypress-media' );

        extract($_POST);

        if ( empty( $unfollow_id ) ){
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_empty_unfollow_id, $msg_empty_unfollow_id );
            exit;
        }

        $args   = array(
            'leader_id' =>  $unfollow_id,
            'follower_id'   => $this->user_id
        );
        $following = bp_follow_is_following($args);
        if( $following ){
            $unfollow_user = bp_follow_stop_following($args);
            if ( $unfollow_user ){
                echo $this->rtmedia_api_response_object( 'TRUE', $ec_stopped_following, $msg_stopped_following );
                exit;
            }
            else{
                echo $this->rtmedia_api_response_object( 'TRUE', $this->ec_server_error, $this->msg_server_error );
                exit;
            }
        }else{
            echo $this->rtmedia_api_response_object( 'TRUE', $ec_not_following, $msg_not_following );
            exit;
        }
    }
    function rtmedia_api_process_update_profile_request(){
        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
        $ec_empty_name_location = 120001;
        $msg_empty_name_location = __('name/location empty', 'buddypress-media' );

        $ec_profile_updated = 120002;
        $msg_profile_updated = __('profile updated', 'buddypress-media' );
        extract($_POST);

        for ( $i=1; $i<=12; $i++ ){
            $field_str = 'field_';
            $field_str .= $i;
            $field_str_privacy = $field_str.'_privacy';
            !empty( $$field_str ) ? $$field_str : '';
            !empty( $$field_str_privacy ) ? $$field_str_privacy : 'public';
            if ( $i ==1 || $i == 4 ){
                $field_str_privacy = 'public';
                if ( empty( $field_str )){
                    echo $this->rtmedia_api_response_object( 'TRUE', $ec_empty_name_location, $msg_empty_name_location );
                    exit;
                }
            }
            xprofile_set_field_data( $i, $this->user_id, $$field_str );
            xprofile_set_field_visibility_level($i, $this->user_id, $$field_str_privacy);
        }
        echo $this->rtmedia_api_response_object( 'TRUE', $ec_profile_updated, $msg_profile_updated );
        exit;
    }
    function rtmedia_api_process_update_avatar_request(){

        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
        $ec_no_file = 130001;
        $msg_no_file = __('no file', 'buddypress-media' );

        $ec_invalid_image = 130002;
        $msg_invalid_image = __('upload failed, check size and file type', 'buddypress-media' );

        $ec_avatar_updated = 130003;
        $msg_avatar_updated = __('avatar updated', 'buddypress-media' );
        extract($_POST);
        if( empty( $_FILES['file'] )){
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_no_file, $msg_no_file );
            exit;
        }
        $uploaded = bp_core_avatar_handle_upload( $_FILES, 'xprofile_avatar_upload_dir' );
        if ( !$uploaded ){
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_invalid_image, $msg_invalid_image );
            exit;
        }else{
            echo $this->rtmedia_api_response_object( 'TRUE', $ec_avatar_updated, $msg_avatar_updated );
            exit;
        }
    }

    function rtmedia_api_process_rtmedia_upload_media_request(){

        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
        //Error Codes for new look
        $ec_no_file = 140001;
        $msg_no_file = __('no file', 'buddypress-media' );

        $ec_invalid_file_string = 140005;
        $msg_invalid_file_string = __('invalid file string', 'buddypress-media' );

        $ec_image_type_missing = 140006;
        $msg_image_type_missing = __('image type missing', 'buddypress-media' );

        $ec_no_file_title = 140002;
        $msg_no_file_title = __('no title', 'buddypress-media' );

        $ec_invalid_image = 140003;
        $msg_invalid_image = __('upload failed, check size and file type', 'buddypress-media' );

        $ec_look_updated = 140004;
        $msg_look_updated = __('media updated', 'buddypress-media' );

        $description = '';
        extract($_POST);
        $updated = FALSE;
        $uploaded_look = FALSE;
        if( empty($_POST['rtmedia_file'] ) && empty($_FILES['rtmedia_file'] ) ){
            echo $this->rtmedia_api_response_object( 'FALSE', $ec_no_file, $msg_no_file );
            exit;
        }
        if( !empty($_POST['rtmedia_file'] ) ) {
            if( empty($_POST['image_type'] ) ){
                echo $this->rtmedia_api_response_object( 'FALSE', $ec_image_type_missing, $msg_image_type_missing );
                exit;
            }
            if( empty($title ) ){
                echo $this->rtmedia_api_response_object( 'FALSE', $ec_no_file_title, $msg_no_file_title );
                exit;
            }
        }
        if( !empty($_FILES['rtmedia_file']) ){
            $_POST['rtmedia_upload_nonce'] = $_REQUEST['rtmedia_upload_nonce'] = wp_create_nonce ( 'rtmedia_upload_nonce' );
            $_POST['rtmedia_simple_file_upload'] = $_REQUEST['rtmedia_simple_file_upload']   =   1;
            $_POST['context']  = $_REQUEST['context'] = !empty($_REQUEST['context']) ? $_REQUEST['context'] : 'profile';
            $_POST['context_id']   = $_REQUEST['context_id'] = !empty($_REQUEST['context_id']) ? $_REQUEST['context_id'] : $this->user_id;
            $_POST['mode']  =   $_REQUEST['mode'] = 'file_upload';
            $_POST['media_author'] = $_REQUEST['media_author'] = $this->user_id;
            $upload = new RTMediaUploadEndpoint();
            $uploaded_look = $upload->template_redirect ();
        }else{
            //Process rtmedia_file
            $img = $rtmedia_file;
			$image_type = $_POST['image_type'];
			$str_replace = 'data:image/'.$image_type.';base64,';
            $img = str_replace($str_replace, '', $img);
        //    $img = str_replace(' ', '+', $img);
            $rtmedia_file = base64_decode($img);
            if( !$rtmedia_file ){
                    echo $this->rtmedia_api_response_object( 'FALSE', $ec_invalid_file_string, $msg_invalid_file_string );
                    exit;
            }
            define('UPLOAD_DIR_LOOK', sys_get_temp_dir().'/' );
            $tmp_name = UPLOAD_DIR_LOOK . $title;
            $file = $tmp_name . '.'.$image_type;
            $success = file_put_contents($file, $rtmedia_file);
            add_filter('upload_dir', array($this, 'api_new_media_upload_dir'));
        //    echo $file;
            $new_look = wp_upload_bits($title.'.'.$image_type, '', $rtmedia_file);
            $new_look['type'] = 'image/'.$image_type;
            remove_filter('upload_dir', array($this, 'api_new_media_upload_dir'));
            foreach ( $new_look as $key => $value ){
                $new_look[0][$key] = $value;
                unset($new_look[$key]);
            }
            //Jugaad
            if(!empty($tags)){
                $tags = explode(',', $tags);
            }
            $uploaded['rtmedia_upload_nonce'] =  wp_create_nonce ( 'rtmedia_upload_nonce' );
            $uploaded['rtmedia_simple_file_upload'] =   1;
	        $uploaded['context'] = !empty($_POST['context']) ? $_POST['context'] : 'profile';
	        $uploaded['context_id'] = !empty($_POST['context_id']) ? $_POST['context_id'] : $this->user_id;
            $uploaded['mode'] = 'file_upload';
            $uploaded['media_author'] = $this->user_id;
            $uploaded['album_id'] = !empty($_POST['album_id']) ? $_POST['album_id'] : RTMediaAlbum::get_default();
            $uploaded['privacy'] = !empty($_POST['privacy']) ? $_POST['privacy'] : get_rtmedia_default_privacy();
            $uploaded['title']  = $title;
            $uploaded['description']    = $description;
            $uploaded['taxonomy'] = array();
            $uploaded['custom_fields'] = array();
            $rtmedia = new RTMediaMedia();
            $rtupload = $rtmedia->add( $uploaded, $new_look );
            $id = rtmedia_media_id($rtupload[0]);
            if(!empty($_POST['tags'])){
                wp_set_post_terms( $id , $_POST["tags"] , 'media-category',true);
            }
            $media = $rtmedia->model->get ( array( 'id' => $rtupload[ 0 ] ) );
            $rtMediaNav = new RTMediaNav();
            $perma_link = "";

            if(isset($media) && sizeof($media) > 0) {
                $perma_link = get_rtmedia_permalink($media[0]->id);
                if($media[0]->media_type == "photo") {
                    $thumb_image = rtmedia_image("rt_media_thumbnail", $rtupload[ 0 ], false);
                } elseif( $media[0]->media_type == "music" ) {
                    $thumb_image = $media[0]->cover_art;
                } else {
                    $thumb_image = "";
                }

                if ( $media[ 0 ]->context == "group" ) {
                    $rtMediaNav->refresh_counts ( $media[ 0 ]->context_id, array( "context" => $media[ 0 ]->context, 'context_id' => $media[ 0 ]->context_id ) );
                } else {
                    $rtMediaNav->refresh_counts ( $media[ 0 ]->media_author, array( "context" => "profile", 'media_author' => $media[ 0 ]->media_author ) );
                }
                $activity_id = $rtmedia->insert_activity ( $media[ 0 ]->media_id, $media[ 0 ] );
                $rtmedia->model->update ( array( 'activity_id' => $activity_id ), array( 'id' => $rtupload[ 0 ] ) );
                    //
                $same_medias = $rtmedia->model->get ( array( 'activity_id' => $activity_id ) );

                $update_activity_media = Array( );
                foreach ( $same_medias as $a_media ) {
                    $update_activity_media[ ] = $a_media->id;
                }
                $privacy = 0;
                $objActivity = new RTMediaActivity ( $update_activity_media, $privacy, false );

                global $wpdb, $bp;
                $updated = $wpdb->update ( $bp->activity->table_name, array( "type" => "rtmedia_update", "content" => $objActivity->create_activity_html () ), array( "id" => $activity_id ) );

				// if there is only single media the $updated value will be false even if the value we are passing to check is correct.
				// So we need to hardcode the $updated to true if there is only single media for same activity
				if( sizeof( $same_medias ) == 1 && $activity_id ){
					$updated = true;
				}
            }
        }

        if ( $updated || $uploaded_look) {
            echo $this->rtmedia_api_response_object( 'TRUE', $ec_look_updated, $msg_look_updated );
            exit;
        }else{
            echo $this->rtmedia_api_response_object( 'TRUE', $ec_invalid_image, $msg_invalid_image );
            exit;
        }
    }
    function rtmedia_api_process_rtmedia_gallery_request(){
        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
        //Errors
        $ec_media = 160002;
        $msg_media = __('media list', 'buddypress-media' );

        $ec_no_media = 160003;
        $msg_no_media = __('no media found for requested media type', 'buddypress-media' );

        $ec_invalid_media_type = 160004;
        $msg_invalid_media_type = __('media_type not allowed', 'buddypress-media' );

        global $rtmedia;
        $rtmediamodel = new RTMediaModel();
        //Media type to fetch
        $media_type = $allowed_types = array_keys($rtmedia->allowed_types);
        $media_type[] = 'album';
        $allowed_types[] = 'album';
        if( !empty($_REQUEST['media_type']) ) {
            if(!is_array( $_REQUEST['media_type'] ) ){
                $media_type = explode(',', $_REQUEST['media_type']);
            }else{
                $media_type = $_REQUEST['media_type'];
            }
            //Check array for currently allowed media types
            $media_type = array_intersect($media_type, $allowed_types);
        }
        //Args for fetching media
        $args = array(
            'media_type'    =>  $media_type,
        );
        
        //global
        if(isset($_REQUEST['global'])){
            if( $_REQUEST['global'] == 'false' ){
                $args['context'] = array(
                    'compare'   => 'IS NOT',
                    'value' => 'NULL'
                );
            }
        }
        //context
        if(isset($_REQUEST['context'])){
            $args['context'] = $_REQUEST['context'];
        }
        //context Id
        if(isset($_POST['context_id'])){
            $args['context_id'] = $_REQUEST['context_id'];
        }

        //album id
        if(isset($_POST['album_id'])){
            $args['album_id'] = $_REQUEST['album_id'];
        }
        //Media Author
        $media_author = '';
        if(!is_super_admin()){
            $media_author = $this->user_id;
            $args['media_author'] = $media_author;
        }
        if( !empty($_REQUEST['media_author'])){
            if( is_super_admin( $this->user_id ) ){
                $media_author = (int)$_REQUEST['media_author'];
                $args['media_author'] = $media_author;
            }
        }
        $offset = !empty($_REQUEST['page']) ? (int)$_REQUEST['page'] : 0;
        $per_page = isset($_REQUEST['per_page']) ? (int)$_REQUEST['per_page'] : 10;
        $order_by = !empty($_REQUEST['order_by']) ? $_REQUEST['order_by'] : 'media_id desc';

        $media_list = $rtmediamodel->get($args, $offset, $per_page, $order_by );
        $media_result = array();
        foreach($media_list as $media ){
            $data = array(
                'id'    => $media->id,
                'media_title'   => $media->media_title,
                'album_id'  => $media->album_id,
                'media_type'    => $media->media_type,
                'media_author'  => $media->media_author,
                'url'   => get_rtmedia_permalink($media->id),
                'cover'     => rtmedia_image('rt_media_thumbnail', $media->media_id, FALSE)
            );
            //for album list all medias
            if($media->media_type == 'album'){
                $data['media'] = $this->rtmediajsonapifunction->rtmedia_api_album_media($media->id);
            }
            $media_result[] = $data;
        }
        if(!empty($media_result)){
            echo $this->rtmedia_api_response_object("TRUE", $ec_media, $msg_media, $media_result);
        }else{
            echo $this->rtmedia_api_response_object("FALSE", $ec_no_media, $msg_no_media);
        }
    }
    function rtmedia_api_process_rtmedia_get_media_details_request(){

        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
        $this->rtmediajsonapifunction->rtmedia_api_media_activity_id_missing();
        //Errors
        $ec_single_media = 150002;
        $msg_single_media = __('single media', 'buddypress-media' );

        extract($_REQUEST);
        $id = rtmedia_media_id( $media_id );
        if (empty( $id ) ){
            echo $this->rtmedia_api_response_object( 'TRUE', $this->ec_invalid_media_id, $this->msg_invalid_media_id );
            exit;
        }
        if(class_exists('RTMediaModel')){
            $rtmediamodel = new RTMediaModel();
            $args = array(
                'media_id'  =>  $id,
                'id'    => $media_id
            );
            $media = $rtmediamodel->get($args);
        }
       $activity_id = !empty( $media) ? $media[0]->activity_id : '';
       if( empty( $activity_id ) ){
           echo $this->rtmedia_api_response_object( 'FALSE', $this->ec_invalid_media_id, $this->msg_invalid_media_id );
           exit;
       }
       $media_single = $this->rtmediajsonapifunction->rtmedia_api_get_feed(FALSE, $activity_id );

       if( $media_single ){
           echo $this->rtmedia_api_response_object( 'TRUE', $ec_single_media, $msg_single_media, $media_single );
           exit;
       }
    }
    function rtmedia_api_process_logout_request(){
        $this->rtmediajsonapifunction->rtmedia_api_verfiy_token();
        extract($_POST);
        //Errors
        $ec_logged_out = 200005;
        $msg_logged_out = "logged out";
        $rtmapilogin = new RTMediaApiLogin();
        $updated = $rtmapilogin->update( array('status'  => 'FALSE' ), array('user_id' => $this->user_id ) );
        if( $updated ){
            echo $this->rtmedia_api_response_object( "TRUE", $ec_logged_out, $msg_logged_out);
            exit;
        }else{
            echo $this->rtmedia_api_response_object( 'FALSE', $this->ec_server_error, $this->msg_server_error );
            exit;
        }

    }
    function api_new_media_upload_dir($args){
       if( !empty($args) || !is_array($args) || empty($_POST['token']) ){
           foreach( $args as $key => $arg ){
               $replacestring = 'uploads/rtMedia/users/'.$this->rtmediajsonapifunction->rtmedia_api_get_user_id_from_token($_POST['token']);
               $arg = str_replace('uploads', $replacestring, $arg);
               $args[$key] = $arg;
           }
           $args['error'] = FALSE;
           return $args;
       }
    }
}

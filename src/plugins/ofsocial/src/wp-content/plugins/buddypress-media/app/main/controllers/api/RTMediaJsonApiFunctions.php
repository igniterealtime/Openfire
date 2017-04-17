<?php

/**
 * @author Umesh Kumar<umeshsingla05@gmail.com>
 */
class RTMediaJsonApiFunctions{
    
    public function __construct() {
    }
    /**
     * Generates a user token for user login
     * @param type $user_id
     * @param type $user_login
     * @return type
     */

    function rtmedia_api_get_user_token( $user_id, $user_login ){
        if( empty( $user_id ) || empty( $user_login ) ) return false;
        $string = '08~'.$user_id.'~'.$user_login.'~kumar';
        return sha1($string.  current_time('timestamp').rand(1,9));
    }
    //User data from user id

    function rtmedia_api_user_data_from_id( $user_id, $width = 80, $height = 80, $type = 'thumb' ){
        if ( empty($user_id) )        return false;
        $user_data = array();
        $user_data['id'] = $user_id;
        $user_data['name'] = xprofile_get_field_data( 'Name', $user_id );

        $avatar_args = array( 'item_id' => $user_id, 'width' =>$width, 'height' => $height, 'html' => false, 'alt' => '', 'type'    =>  $type );
        $user_data['avatar'] = bp_core_fetch_avatar( $avatar_args );
        return $user_data;
    }
    //Media details from id
    function rtmedia_api_media_data_from_object( $media ){
        if ( empty($media) )        return false;
        $media_data = array();
        $media_data['id']   = $media["id"];
        $media_data['src'] = rtmedia_image('rt_media_activity_image', $media["id"], false);
        $media_data["title"] = $media["media_title"];
        $media_data['comment_count']    = bp_activity_get_comment_count();
        return $media_data;
    }
    //Validate token
    function rtmedia_api_validate_token( $token ){
        if ( empty($token ) ) return false;
        if ( class_exists( "RTMediaApiLogin" )) {
            $rtmediaapilogin = new RTMediaApiLogin();
            $columns = array(
                'token' => $token
            );
            $token_data =  $rtmediaapilogin->get($columns);
            if ( empty( $token_data ) || $token_data[0]->status === 'FALSE' ) {
                return FALSE;
            }
            return $token_data;
        }else
            return false;
    }
    //user id from token
    function rtmedia_api_get_user_id_from_token( $token ){
        if ( empty($token ) ) return false;
        $token_data = $this->rtmedia_api_validate_token( $token );
        return $token_data[0]->user_id;
    }

    // Token processing for all data fetch/post requests

    function rtmedia_api_verfiy_token(){
        $rtmjsonapi = new RTMediaJsonApi();
         if ( empty($_POST['token'] ) ){
            echo $rtmjsonapi->rtmedia_api_response_object('FALSE', $rtmjsonapi->ec_token_missing, $rtmjsonapi->msg_token_missing ); 
            exit;
        }
        //Validate token

        $token_valid = $this->rtmedia_api_validate_token( $_POST['token'] );

        if ( !$token_valid ){
            echo $rtmjsonapi->rtmedia_api_response_object('FALSE', $rtmjsonapi->ec_token_invalid, $rtmjsonapi->msg_token_invalid ); 
            exit;
        }
    }
    function rtmedia_api_media_activity_id_missing(){
        $rtmjsonapi = new RTMediaJsonApi();
        if ( empty( $_POST['activity_id'] ) && empty( $_POST['media_id'] ) ) {
            echo $rtmjsonapi->rtmedia_api_response_object('FALSE', $rtmjsonapi->ec_media_activity_id_missing,$rtmjsonapi->msg_media_activity_id_missing ); 
            exit;
        }
    }

    function rtmedia_api_activityid_from_mediaid( $media_id ){
        $rtmjsonapi = new RTMediaJsonApi();
        if ( empty( $media_id )) return false;
        $mediaModel = new RTMediaModel();
        $result = $mediaModel->get ( array( 'id' => $media_id ) );

        if ( empty( $result ) ){
            echo $rtmjsonapi->rtmedia_api_response_object('FALSE', $rtmjsonapi->ec_invalid_media_id, $rtmjsonapi->msg_invalid_media_id ); 
            exit;
        }
        return $result[ 0 ]->activity_id;
    }

    function rtmedia_api_followers( $user_id){
        if(empty($user_id)) return false;
        $followers = bp_follow_get_followers( array( 'user_id'  => $user_id ) );
        return $followers;
    }
    function rtmedia_api_following( $user_id){
        if(empty($user_id)) return false;
        $followers = bp_follow_get_following( array( 'user_id'  => $user_id ) );
        return $followers;
    }
    /**
     * Accepts a rtmedia media object and returns a array of media details
     */
    function rtmedia_api_media_details( $media_list ){
        global $rtmediajsonapi;
        if(empty($media_list)) return false;
        $result = array();
        if(is_array($media_list)){
            foreach($media_list as $media ){
                //Media likes
                $rtmediainteraction = new RTMediaInteractionModel();
                $action = 'like';
                $results = $rtmediainteraction->get_row( $rtmediajsonapi->user_id, $media['id'], $action);
                $row = !empty($results )? $results[0] : '';
                $current_user = ( !empty( $row ) && $row->value == 1 ) ? 'TRUE' : 'FALSE';
                

                $result[] = array(
                    'id'    => $media['id'],
                    'title'    => $media['media_title'],
                    'src'    => rtmedia_image('rt_media_activity_image', $media["id"], false),
                    'likes'    => $media['likes'],
                    'current_user'    => $current_user,
                    
                ); 
            }
        }
        return $result;
    }
    /**
     * Fetches Activity for rtmedia updates, if user id for activity is provided fetches the user specific rtmedia updates
     * @global type $activities_template
     * @param type $activity_user_id
     * @param type $activity_id
     * @return array(), Activity data
     */
    function rtmedia_api_get_feed($activity_user_id = FALSE, $activity_id = FALSE, $per_page = 10 ){
        global $activities_template, $rtmediajsonapi;
        $activity_feed = array();
        extract($_REQUEST);
        $i = 0;
        $args = array (
            'user_id'   => $activity_user_id,
            'action'=>'', /* or rtmedia_update for fetching only rtmedia updates */
            'page' => !empty( $_REQUEST['page'] ) ? $_REQUEST['page'] : 1, 
            'per_page' => $per_page, 
            'in'   => $activity_id 
        );
        if ( bp_has_activities($args) ) :
            $activity_feed['total_activity_count'] = $activities_template->total_activity_count;
            $activity_feed['total'] = ceil( (int) $activities_template->total_activity_count / (int) $activities_template->pag_num );
            $activity_feed['current'] = $activities_template->pag_page;
             while ( bp_activities() ) : bp_the_activity();
                //Activity basic details
                $activity_feed[$i]['id']    = $activities_template->activity->id;
                $activity_feed[$i]['activity_type']    = $activities_template->activity->type;
                $activity_feed[$i]['activity_time'] = bp_get_activity_date_recorded();
                $activity_feed[$i]['activity_time_human'] = strip_tags(bp_insert_activity_meta( '' ));
                $activity_feed[$i]['activity_content'] = $activities_template->activity->content;
                
                //activity User
                if ( !$activity_user_id ) {
                    //Activity User data
                    $activity_feed[$i]['user'] = $this->rtmedia_api_user_data_from_id( bp_get_activity_user_id() );
                }

                //Media Details
                if (class_exists("RTMediaModel")) {
                    $model = new RTMediaModel();
                    $media = $model->get_by_activity_id($activities_template->activity->id);

                    if (isset($media['result']) && count($media['result']) > 0){
                        //Create media array
                        $media = $this->rtmedia_api_media_details($media['result']);
                    }
                    else
                        $media = false;
                }
                if ( $activity_id ){;
                    //Activity Comment Count
                    $id = $media[0]['id'];
                    $activity_feed[$i]['comments'] = $this->rtmedia_api_get_media_comments($id) ;
                }
                //Activity Image
                $activity_feed[$i]['media'] = $media;
                $i++;
            endwhile;
        endif;
        return $activity_feed;
    }
    function rtmedia_api_get_media_comments( $media_id ){
        global $wpdb;
        $rtmjsonapi =new RTMediaJsonApi();
        $id = rtmedia_media_id($media_id);
        if( empty( $id ) ){
            echo $rtmjsonapi->rtmedia_api_response_object('FALSE', $rtmjsonapi->ec_invalid_media_id, $rtmjsonapi->msg_invalid_media_id ); 
            exit;
        }
        $comments = $wpdb->get_results ( "SELECT * FROM $wpdb->comments WHERE comment_post_ID = '" . $id . "'", ARRAY_A );

        $media_comments = array();
        if ( !empty( $comments ) ){
            foreach ( $comments as $comment ){
                $media_comments['comments'][] =   array(
                    'comment_ID'    =>  $comment['comment_ID'],
                    'comment_content'   => $comment['comment_content'],
                    'user_id'   => $comment['user_id']
                );
                if ( !array_key_exists(  $comment['user_id'], $media_comments['user'] ) ){

                    $user_data = $this->rtmedia_api_user_data_from_id(  $comment['user_id'] );

                    $media_comments['user'][$comment['user_id']]    =   array(
                        'name'  => $user_data['name'],
                        'avatar'    => $user_data['avatar']
                    );
                }
            }
        }
        return $media_comments;
    }
    function rtmedia_api_media_liked_by_user( $media_id ){
        $rtmediainteractionmodel = new RTMediaInteractionModel();
        $media_like_cols = array(
                'media_id' => $media_id,
                'action' => 'like',
                'value' => 1
        );
        $likers = $rtmediainteractionmodel->get( $media_like_cols, FALSE, FALSE, 'action_date');
        return $likers;
    }
    function rtmedia_api_album_media($album_id){
        if(empty($album_id)) return false;
        $rtmediamodel = new RTMediaModel();
        $args = array(
            'album_id'  => $album_id
        );
        $media_list = $rtmediamodel->get($args);
        $media_data = array();
        if(!empty($media_list) && is_array($media_list)){
            foreach($media_list as $media ){
                $media_data[] = array(
                    'id'    =>  $media->id,
                    'media_title'   => $media->media_title,
                    'media_url' => get_rtmedia_permalink($media->media_id),
                    'media_author'  => $media->media_author,
                    'cover' => rtmedia_image('rt_media_thumbnail', $media->media_id, FALSE)
                );
            }
        }
        return $media_data;
    }
    function  rtmedia_api_set_user_id(){
        global $rtmediajsonapi;
        return $rtmediajsonapi->user_id;
    }
}
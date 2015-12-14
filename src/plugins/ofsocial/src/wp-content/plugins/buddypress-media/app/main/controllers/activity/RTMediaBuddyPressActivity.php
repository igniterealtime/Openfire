<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of RTMediaBuddyPressActivity
 *
 * @author faishal
 */
class RTMediaBuddyPressActivity {

	function __construct(){
		global $rtmedia;
		if ( 0 != $rtmedia->options['buddypress_enableOnActivity'] ){
			add_action( 'bp_after_activity_post_form', array( &$this, 'bp_after_activity_post_form' ) );
			add_action( 'bp_activity_posted_update', array( &$this, 'bp_activity_posted_update' ), 99, 3 );

			// manage user's last activity update.
			add_action( 'bp_activity_posted_update', array( &$this, 'manage_user_last_activity_update' ), 999, 3 );
			add_action( 'bp_groups_posted_update', array( &$this, 'bp_groups_posted_update' ), 99, 4 );
		}
		add_action( 'bp_init', array( $this, 'non_threaded_comments' ) );
		add_action( 'bp_activity_comment_posted', array( $this, 'comment_sync' ), 10, 2 );
		add_action( 'bp_activity_delete_comment', array( $this, 'delete_comment_sync' ), 10, 2 );
		add_filter( 'bp_activity_allowed_tags', array( &$this, 'override_allowed_tags' ) );
		add_filter( 'bp_get_activity_parent_content', array( &$this, 'bp_get_activity_parent_content' ) );
		add_action( 'bp_activity_deleted_activities', array( &$this, 'bp_activity_deleted_activities' ) );
        
        // Filter bp_activity_prefetch_object_data for translatable activity actions
        add_filter( 'bp_activity_prefetch_object_data', array( $this, 'bp_prefetch_activity_object_data' ), 10, 1 );
	}

	function bp_activity_deleted_activities( $activity_ids_deleted ){
		//$activity_ids_deleted
		$rt_model  = new RTMediaModel();
		$all_media = $rt_model->get( array( 'activity_id' => $activity_ids_deleted ) );
		if ( $all_media ){
			$media = new RTMediaMedia();
			remove_action( 'bp_activity_deleted_activities', array( &$this, 'bp_activity_deleted_activities' ) );
			foreach ( $all_media as $single_media ) {
				$media->delete( $single_media->id, false, false );
			}
		}
	}

	function bp_get_activity_parent_content( $content ){
		global $activities_template;

		// Get the ID of the parent activity content
		if ( ! $parent_id = $activities_template->activity->item_id ){
			return false;
		}

		// Bail if no parent content
		if ( empty( $activities_template->activity_parents[ $parent_id ] ) ){
			return false;
		}

		// Bail if no action
		if ( empty( $activities_template->activity_parents[ $parent_id ]->action ) ){
			return false;
		}

		// Content always includes action
		$content = $activities_template->activity_parents[ $parent_id ]->action;

		// Maybe append activity content, if it exists
		if ( ! empty( $activities_template->activity_parents[ $parent_id ]->content ) ){
			$content .= ' ' . $activities_template->activity_parents[ $parent_id ]->content;
		}

		// Remove the time since content for backwards compatibility
		$content = str_replace( '<span class="time-since">%s</span>', '', $content );

		return $content;
	}

	function delete_comment_sync( $activity_id, $comment_id ){
		global $wpdb;
		$comment_id = $wpdb->get_var( $wpdb->prepare( "select comment_id from {$wpdb->commentmeta} where meta_key = 'activity_id' and meta_value=%s", $comment_id ) );
		if ( $comment_id ){
			wp_delete_comment( $comment_id, true );
		}
	}

	function comment_sync( $comment_id, $param ){

		$user_id        = '';
		$comment_author = '';
		extract( $param );
		if ( ! empty( $user_id ) ){
			$user_data      = get_userdata( $user_id );
			$comment_author = $user_data->data->user_login;
		}
		$mediamodel = new RTMediaModel();
		$media      = $mediamodel->get( array( 'activity_id' => $param['activity_id'] ) );
		// if there is only single media in activity
		if ( 1 == sizeof( $media ) && isset( $media[0]->media_id ) ){
			$media_id = $media[0]->media_id;
			$comment  = new RTMediaComment();
			$id       = $comment->add( array( 'comment_content' => $param['content'], 'comment_post_ID' => $media_id, 'user_id' => $user_id, 'comment_author' => $comment_author ) );
			update_comment_meta( $id, 'activity_id', $comment_id );
		}
	}

	function non_threaded_comments(){
		if ( isset( $_POST['action'] ) && 'new_activity_comment' == $_POST['action'] ){
			$activity_id = $_POST['form_id'];
			$act         = new BP_Activity_Activity( $activity_id );

			if ( 'rtmedia_update' == $act->type ){
				$_POST['comment_id'] = $_POST['form_id'];
			}
		}
	}

	function bp_groups_posted_update( $content, $user_id, $group_id, $activity_id ){
		$this->bp_activity_posted_update( $content, $user_id, $activity_id );
	}

	function bp_activity_posted_update( $content, $user_id, $activity_id ){
		global $wpdb, $bp;
		$updated_content = '';

		// hook for rtmedia buddypress before activity posted
		do_action( 'rtmedia_bp_before_activity_posted', $content, $user_id, $activity_id );

		if ( isset( $_POST['rtMedia_attached_files'] ) && is_array( $_POST['rtMedia_attached_files'] ) ){
			$updated_content = $wpdb->get_var( "select content from  {$bp->activity->table_name} where  id= $activity_id" );

			$objActivity  = new RTMediaActivity( $_POST['rtMedia_attached_files'], 0, $updated_content );
			$html_content = $objActivity->create_activity_html();
			bp_activity_update_meta( $activity_id, 'bp_old_activity_content', $html_content );
			bp_activity_update_meta( $activity_id, 'bp_activity_text', $updated_content );
			$wpdb->update( $bp->activity->table_name, array( 'type' => 'rtmedia_update', 'content' => $html_content ), array( 'id' => $activity_id ) );
			$mediaObj = new RTMediaModel();
			$sql      = "update $mediaObj->table_name set activity_id = '" . $activity_id . "' where blog_id = '" . get_current_blog_id() . "' and id in (" . implode( ',', $_POST['rtMedia_attached_files'] ) . ')';
			$wpdb->query( $sql );
		}
		// hook for rtmedia buddypress after activity posted
		do_action( 'rtmedia_bp_activity_posted', $updated_content, $user_id, $activity_id );

		if ( isset( $_POST['rtmedia-privacy'] ) ){
			$privacy = - 1;
			if ( is_rtmedia_privacy_enable() ){
				if ( is_rtmedia_privacy_user_overide() ){
					$privacy = $_POST['rtmedia-privacy'];
				} else {
					$privacy = get_rtmedia_default_privacy();
				}
			}
			bp_activity_update_meta( $activity_id, 'rtmedia_privacy', $privacy );
			// insert/update activity details in rtmedia activity table
			$rtmedia_activity_model = new RTMediaActivityModel();
			if ( ! $rtmedia_activity_model->check( $activity_id ) ){
				$rtmedia_activity_model->insert( array( 'activity_id' => $activity_id, 'user_id' => $user_id, 'privacy' => $privacy ) );
			} else {
				$rtmedia_activity_model->update( array( 'activity_id' => $activity_id, 'user_id' => $user_id, 'privacy' => $privacy ), array( 'activity_id' => $activity_id ) );
			}
		}
	}

	/**
	 * Update `bp_latest_update` user meta with lasted public update.
	 *
	 * @param $content
	 * @param $user_id
	 * @param $activity_id
	 */
	function manage_user_last_activity_update( $content, $user_id, $activity_id ){
		global $wpdb,$bp;

		// do not proceed if not allowed
		if( ! apply_filters( 'rtm_manage_user_last_activity_update', true, $activity_id ) ){
			return;
		}
		$rtm_activity_model = new RTMediaActivityModel();

		$rtm_activity_obj = $rtm_activity_model->get( array( 'activity_id' => $activity_id ) );

		if( !empty( $rtm_activity_obj ) ){
			if( isset( $rtm_activity_obj[0]->privacy ) && $rtm_activity_obj[0]->privacy > 0 ){

				$get_columns = array(
					'activity_id' => array(
						'compare' => '<',
						'value' => $activity_id
					),
					'user_id' => $user_id,
					'privacy' => array(
						'compare' => '<=',
						'value' => 0
					),
				);

				// get user's latest public activity update
				$new_last_activity_obj = $rtm_activity_model->get( $get_columns, 0, 1 );

				if( !empty( $new_last_activity_obj ) ){
					// latest public activity id
					$public_activity_id = $new_last_activity_obj[0]->activity_id;

					// latest public activity content
					$activity_content = bp_activity_get_meta( $public_activity_id, 'bp_activity_text' );
					if( empty( $activity_content ) ){
						$activity_content = $wpdb->get_var( "SELECT content FROM {$bp->activity->table_name} WHERE id = $public_activity_id" );
					}
					$activity_content = apply_filters( 'bp_activity_latest_update_content', $activity_content, $activity_content );

					// update user's latest update
					bp_update_user_meta( $user_id, 'bp_latest_update', array(
						'id'      => $public_activity_id,
						'content' => $activity_content
					) );
				}
			}
		}
	}

	function bp_after_activity_post_form(){
		$url = trailingslashit ( $_SERVER[ "REQUEST_URI" ] );
		$slug_split = explode( '/', $url );
		// check position of media slug for end of the URL
		if ( $slug_split[ sizeof( $slug_split ) - 1 ] == RTMEDIA_MEDIA_SLUG ) {
			// replace media slug with the blank space
			$slug_split[ sizeof( $slug_split ) - 1 ] = '';
			$url_upload = implode( '/', $slug_split );
			$url = trailingslashit ( $url_upload ) . "upload/";
		} else {
			$url = trailingslashit ( $url ) . "upload/";
		}
		if ( rtmedia_is_uploader_view_allowed( true, 'activity' ) ){
			$params = array(
				'url'             => $url,
				'runtimes' => 'html5,flash,html4', 'browse_button' => 'rtmedia-add-media-button-post-update', // browse button assigned to "Attach Files" Button.
				'container'       => 'rtmedia-whts-new-upload-container', 'drop_element' => 'whats-new-textarea', // drag-drop area assigned to activity update textarea
				'filters'         => apply_filters( 'rtmedia_plupload_files_filter', array( array( 'title' => __( 'Media Files', 'buddypress-media' ), 'extensions' => get_rtmedia_allowed_upload_type() ) ) ),
				'max_file_size' => ( wp_max_upload_size() ) / ( 1024 * 1024 ) . 'M',
				'multipart' => true, 'urlstream_upload' => true,
				'flash_swf_url' => includes_url( 'js/plupload/plupload.flash.swf' ),
				'silverlight_xap_url' => includes_url( 'js/plupload/plupload.silverlight.xap' ),
				'file_data_name' => 'rtmedia_file', // key passed to $_FILE.
				'multi_selection' => true,
				'multipart_params' => apply_filters( 'rtmedia-multi-params', array( 'redirect' => 'no', 'rtmedia_update' => 'true', 'action' => 'wp_handle_upload', '_wp_http_referer' => $_SERVER['REQUEST_URI'], 'mode' => 'file_upload', 'rtmedia_upload_nonce' => RTMediaUploadView::upload_nonce_generator( false, true ) ) ),
				'max_file_size_msg' => apply_filters( 'rtmedia_plupload_file_size_msg', min( array( ini_get( 'upload_max_filesize' ), ini_get( 'post_max_size' ) ) ) )
			);
			if ( wp_is_mobile() ){
				$params['multi_selection'] = false;
			}
			$params = apply_filters( 'rtmedia_modify_upload_params', $params );
			wp_enqueue_script( 'rtmedia-backbone', false, '', false, true );
			$is_album        = is_rtmedia_album() ? true : false;
			$is_edit_allowed = is_rtmedia_edit_allowed() ? true : false;
			wp_localize_script( 'rtmedia-backbone', 'is_album', $is_album );
			wp_localize_script( 'rtmedia-backbone', 'is_edit_allowed', $is_edit_allowed );
			wp_localize_script( 'rtmedia-backbone', 'rtMedia_update_plupload_config', $params );

			$uploadView = new RTMediaUploadView( array( 'activity' => true ) );
			$uploadView->render( 'uploader' );
		} else {
			echo "<div class='rtmedia-upload-not-allowed'>" . apply_filters( 'rtmedia_upload_not_allowed_message', __( 'You are not allowed to upload/attach media.', 'buddypress-media' ), 'activity' ) . '</div>';
		}
	}

	function override_allowed_tags( $activity_allowedtags ){

		$activity_allowedtags['video']               = array();
		$activity_allowedtags['video']['id']       = array();
		$activity_allowedtags['video']['class']    = array();
		$activity_allowedtags['video']['src']      = array();
		$activity_allowedtags['video']['controls'] = array();
		$activity_allowedtags['video']['preload']  = array();
		$activity_allowedtags['video']['alt']      = array();
		$activity_allowedtags['video']['title']    = array();
		$activity_allowedtags['video']['width']    = array();
		$activity_allowedtags['video']['height']   = array();
		$activity_allowedtags['video']['poster']   = array();
		$activity_allowedtags['audio']               = array();
		$activity_allowedtags['audio']['id']       = array();
		$activity_allowedtags['audio']['class']    = array();
		$activity_allowedtags['audio']['src']      = array();
		$activity_allowedtags['audio']['controls'] = array();
		$activity_allowedtags['audio']['preload']  = array();
		$activity_allowedtags['audio']['alt']      = array();
		$activity_allowedtags['audio']['title']    = array();
		$activity_allowedtags['audio']['width']    = array();
		$activity_allowedtags['audio']['poster']   = array();
		
        if( !isset( $activity_allowedtags['div'] ) ){
            $activity_allowedtags['div']           = array();
        }
        
		$activity_allowedtags['div']['id']         = array();
		$activity_allowedtags['div']['class']      = array();
		
        if( !isset( $activity_allowedtags['a'] ) ){
            $activity_allowedtags['a']             = array();
        }        
        
		$activity_allowedtags['a']['title']        = array();
		$activity_allowedtags['a']['href']         = array();
		
        if( !isset( $activity_allowedtags['ul'] ) ){
            $activity_allowedtags['ul']            = array();
        }
        
		$activity_allowedtags['ul']['class']       = array();
        
        if( !isset( $activity_allowedtags['li'] ) ){
            $activity_allowedtags['li']            = array();
        }
		$activity_allowedtags['li']['class']       = array();

		/* Legacy Code */
		//$activity_allowedtags[ 'script' ] = array( );
		//$activity_allowedtags[ 'script' ][ 'type' ] = array( );

		return $activity_allowedtags;
	}
    
    /*
     * To add dynamic activity actions for translation of activity items
     */
    function bp_prefetch_activity_object_data( $activities ) {
        // If activities array is empty then return
        if( empty( $activities ) ) {
            return;
        }
        
        // To store activity_id
        $activity_ids = array();
        $activity_index_array = array();
        
        foreach ( $activities as $i => $activity ) {
            // Checking if activity_type is of rtmedia and component must be profile
            if( $activity->type == 'rtmedia_update' && $activity->component == 'profile' ) {
                // Storing activity_id
                $activity_ids[] = $activity->id;
                // Storing index of activity from activities array
                $activity_index_array[] = $i;
            }
        }
        
        // Checking if media is linked with any of activity
        if( !empty( $activity_ids ) ) {
            $rtmedia_model = new RTMediaModel();
            
            // Where condition array to get media using activity_id from rtm_media table
            $rtmedia_media_where_array = array();
            $rtmedia_media_where_array[ 'activity_id' ] = array(
                'compare' => 'IN',
                'value' => $activity_ids
            );            
            $rtmedia_media_query = $rtmedia_model->get( $rtmedia_media_where_array );
            
            // Array to store media_type in simplified manner with activity_id as key
            $rtmedia_media_type_array = array();            
            for( $i = 0; $i < sizeof( $rtmedia_media_query ); $i++ ) {
                // Storing media_type of uploaded media to check whether all media are of same type or different and key is activity_id
                // Making activity_id array because there might be more then 1 media linked with activity
				if( ! isset( $rtmedia_media_type_array[ $rtmedia_media_query[ $i ]->activity_id ] ) || ! is_array( $rtmedia_media_type_array[ $rtmedia_media_query[ $i ]->activity_id ] ) ) {
                    $rtmedia_media_type_array[ $rtmedia_media_query[ $i ]->activity_id ] = array();
                }
                
                array_push( $rtmedia_media_type_array[ $rtmedia_media_query[ $i ]->activity_id ], $rtmedia_media_query[ $i ]->media_type );
            }
            
            // Updating action
            for( $a = 0; $a < sizeof( $activity_ids ); $a++ ) {
                // Getting index of activity which is being updated
                $index = $activity_index_array[ $a ];
                //error_log( var_export( $activities[ $index ], true ) );
                // This pattern is for getting name. User might have change display name as first name or something instead on nicename.
                $pattern = "/<a ?.*>(.*)<\/a>/";
                preg_match( $pattern, $activities[ $index ]->action, $matches );

                // Generating user_link with name
                $user_link = '<a href="' . $activities[ $index ]->primary_link . '">' . $matches[ 1 ] . '</a>';
                // Counting media linked with activity
                $count = sizeof( $rtmedia_media_type_array[ $activities[ $index ]->id ] );
                // Getting constant with single label or plural label
                $media_const      = 'RTMEDIA_' . strtoupper( $rtmedia_media_type_array[ $activities[ $index ]->id ][ 0 ] );
                if ( $count > 1 ){
                    $media_const .= '_PLURAL';
                }
                $media_const .= '_LABEL';
				if( defined( $media_const ) ){
					$media_str = constant( $media_const );
				} else {
					$media_str = RTMEDIA_MEDIA_SLUG;
				}

                $action = '';
                $user = get_userdata( $activities[ $index ]->user_id );
                // Updating activity based on count
                if( $count == 1 ) {
                    $action = sprintf( __( '%s added a %s', 'buddypress-media' ), $user_link, $media_str );
                } else {
                    // Checking all the media linked with activity are of same type
                    if( isset( $rtmedia_media_type_array[ $activities[ $index ]->id ] ) 
                        && !empty( $rtmedia_media_type_array[ $activities[ $index ]->id ] ) 
                        && count( array_unique( $rtmedia_media_type_array[ $activities[ $index ]->id ] ) ) == 1 ) {
                        $action = sprintf( __( '%s added %d %s', 'buddypress-media' ), $user_link, $count, $media_str );
                    } else {
                        $action = sprintf( __( '%s added %d %s', 'buddypress-media' ), $user_link, $count, RTMEDIA_MEDIA_SLUG );
                    }
                }
                
                $action = apply_filters( 'rtmedia_bp_activity_action_text', $action, $user_link, $count, $user, $rtmedia_media_type_array[ $activities[ $index ]->id ][ 0 ], $activities[ $index ]->id );
                $activities[ $index ]->action = $action;
            }
        }
        
        return $activities;
    }

}

<?php

/**
 * Description of RTMediaUploadEndpoint
 *
 * @author Joshua Abenazer <joshua.abenazer@rtcamp.com>
 */
class RTMediaUploadEndpoint {

	public $upload;

	/**
	 *
	 */
	public function __construct() {
		add_action( 'rtmedia_upload_redirect', array( $this, 'template_redirect' ) );
	}

	/**
	 *
	 */
	function template_redirect( $create_activity = true ) {
		ob_start();
		if ( ! count( $_POST ) ){
			include get_404_template();
		} else {
			$nonce = $_REQUEST[ 'rtmedia_upload_nonce' ];
			if ( isset( $_REQUEST[ 'mode' ] ) ){
				$mode = $_REQUEST[ 'mode' ];
			}
			$rtupload     = false;
			$activity_id  = - 1;
			$redirect_url = "";
			if ( wp_verify_nonce( $nonce, 'rtmedia_upload_nonce' ) ){
				$model        = new RTMediaUploadModel();
                do_action( 'rtmedia_upload_set_post_object' );
				$this->upload = $model->set_post_object();
				if ( isset ( $_POST[ 'activity_id' ] ) && $_POST[ 'activity_id' ] != - 1 ){
					$this->upload[ 'activity_id' ] = $_POST[ 'activity_id' ];
					$activity_id                   = $_POST[ 'activity_id' ];

				}

				//                ////if media upload is being made for a group, identify the group privacy and set media privacy accordingly
				if ( isset( $this->upload[ 'context' ] ) && isset( $this->upload[ 'context_id' ] ) && $this->upload[ 'context' ] == 'group' && function_exists( 'groups_get_group' ) ){

					$group = groups_get_group( array( 'group_id' => $this->upload[ 'context_id' ] ) );
					if ( isset( $group->status ) && $group->status != 'public' ){
						// if group is not public, then set media privacy as 20, so only the group members can see the images
						$this->upload[ 'privacy' ] = '20';
					} else {
						// if group is public, then set media privacy as 0
						$this->upload[ 'privacy' ] = '0';
					}

				}
				$this->upload = apply_filters( 'rtmedia_media_param_before_upload', $this->upload );
				$rtupload   = new RTMediaUpload ( $this->upload );
				$mediaObj   = new RTMediaMedia();
				$media      = $mediaObj->model->get( array( 'id' => $rtupload->media_ids[ 0 ] ) );
				$rtMediaNav = new RTMediaNav();
				$perma_link = "";
				if ( isset( $media ) && sizeof( $media ) > 0 ){
					$perma_link = get_rtmedia_permalink( $media[ 0 ]->id );
					if ( $media[ 0 ]->media_type == "photo" ){
						$thumb_image = rtmedia_image( "rt_media_thumbnail", $rtupload->media_ids[ 0 ], false );
					} elseif ( $media[ 0 ]->media_type == "music" ) {
						$thumb_image = $media[ 0 ]->cover_art;
					} else {
						$thumb_image = "";
					}

					if ( $media[ 0 ]->context == "group" ){
						$rtMediaNav->refresh_counts( $media[ 0 ]->context_id, array( "context" => $media[ 0 ]->context, 'context_id' => $media[ 0 ]->context_id ) );
					} else {
						$rtMediaNav->refresh_counts( $media[ 0 ]->media_author, array( "context" => "profile", 'media_author' => $media[ 0 ]->media_author ) );
					}
					if ( $create_activity !== false && class_exists( 'BuddyPress' ) ){
						$allow_single_activity = apply_filters( 'rtmedia_media_single_activity', false );

						// Following will not apply to activity uploads. For first time activity won't be generated.
						// Create activity first and pass activity id in response.

						// todo fixme rtmedia_media_single_activity filter. It will create 2 activity with same media if uploaded from activity page.

						if ( ( $activity_id == - 1 && ( ! ( isset ( $_POST[ "rtmedia_update" ] ) && $_POST[ "rtmedia_update" ] == "true" ) ) ) || $allow_single_activity ){
							$activity_id = $mediaObj->insert_activity( $media[ 0 ]->media_id, $media[ 0 ] );
						} else {
							$mediaObj->model->update( array( 'activity_id' => $activity_id ), array( 'id' => $rtupload->media_ids[ 0 ] ) );
							//
							$same_medias = $mediaObj->model->get( array( 'activity_id' => $activity_id ) );

							$update_activity_media = Array();
							foreach ( $same_medias as $a_media ) {
								$update_activity_media[ ] = $a_media->id;
							}
							$privacy = 0;
							if ( isset ( $_POST[ "privacy" ] ) ){
								$privacy = $_POST[ "privacy" ];
							}
							$objActivity = new RTMediaActivity ( $update_activity_media, $privacy, false );
							global $wpdb, $bp;
							$user     = get_userdata( $same_medias[ 0 ]->media_author );
							$username = '<a href="' . get_rtmedia_user_link( $same_medias[ 0 ]->media_author ) . '">' . $user->user_nicename . '</a>';
							$action   = sprintf( __( '%s added %d %s', 'buddypress-media' ), $username, sizeof( $same_medias ), RTMEDIA_MEDIA_SLUG );
							$action   = apply_filters( 'rtmedia_buddypress_action_text_fitler_multiple_media', $action, $username, sizeof( $same_medias ), $user->user_nicename );
							$wpdb->update( $bp->activity->table_name, array( "type" => "rtmedia_update", "content" => $objActivity->create_activity_html(), 'action' => $action ), array( "id" => $activity_id ) );
						}

						// update group last active
						if ( $media[ 0 ]->context == "group" ){
							RTMediaGroup::update_last_active( $media[ 0 ]->context_id );
						}
					}
				}

				if ( isset( $this->upload[ 'rtmedia_simple_file_upload' ] ) && $this->upload[ 'rtmedia_simple_file_upload' ] == true ){
					if ( isset( $media ) && sizeof( $media ) > 0 ){
						if ( isset ( $_POST[ "redirect" ] ) ){
							if ( intval( $_POST[ "redirect" ] ) > 1 ){
								//bulkurl
								if ( $media[ 0 ]->context == "group" ){
									$redirect_url = trailingslashit( get_rtmedia_group_link( $media[ 0 ]->context_id ) ) . RTMEDIA_MEDIA_SLUG;
								} else {
									$redirect_url = trailingslashit( get_rtmedia_user_link( $media[ 0 ]->media_author ) ) . RTMEDIA_MEDIA_SLUG;
								}
							} else {
								$redirect_url = get_rtmedia_permalink( $media[ 0 ]->id );
							}
							$redirect_url = apply_filters( "rtmedia_simple_file_upload_redirect_url_filter", $redirect_url );
							wp_safe_redirect( esc_url_raw( $redirect_url ) );
							die();
						}

						return $media;
					}

					return false;
				}
			}

			$redirect_url = "";
			if ( isset ( $_POST[ "redirect" ] ) && is_numeric( $_POST[ "redirect" ] ) ){
				if ( intval( $_POST[ "redirect" ] ) > 1 ){
					//bulkurl
					if ( $media[ 0 ]->context == "group" ){
						$redirect_url = trailingslashit( get_rtmedia_group_link( $media[ 0 ]->context_id ) ) . RTMEDIA_MEDIA_SLUG;
					} else {
						$redirect_url = trailingslashit( get_rtmedia_user_link( $media[ 0 ]->media_author ) ) . RTMEDIA_MEDIA_SLUG;
					}
				} else {
					$redirect_url = get_rtmedia_permalink( $media[ 0 ]->id );
				}
			}


			// Ha ha ha
			ob_end_clean();
			//check for simpe
			/**
			 * if(redirect)
			 *
			 */
			if ( isset ( $_POST[ "rtmedia_update" ] ) && $_POST[ "rtmedia_update" ] == "true" ){
				if ( preg_match( '/(?i)msie [1-9]/', $_SERVER[ 'HTTP_USER_AGENT' ] ) ){ // if IE(<=9) set content type = text/plain
					header( 'Content-type: text/plain' );
				} else {
					header( 'Content-type: application/json' );
				}
				echo json_encode( $rtupload->media_ids );
			} else {
				// Media Upload Case - on album/post/profile/group
				if( isset( $media[0] ) ) {
					$data = array( 'media_id' => $media[ 0 ]->id, 'activity_id' => $activity_id, 'redirect_url' => $redirect_url, 'permalink' => $perma_link, 'cover_art' => $thumb_image );
				} else {
					$data = array();
				}
				if ( preg_match( '/(?i)msie [1-9]/', $_SERVER[ 'HTTP_USER_AGENT' ] ) ){ // if IE(<=9) set content type = text/plain
					header( 'Content-type: text/plain' );
				} else {
					header( 'Content-type: application/json' );
				}
				echo json_encode( apply_filters( 'rtmedia_upload_endpoint_response', $data ) );
			}


			die ();
		}
	}

}

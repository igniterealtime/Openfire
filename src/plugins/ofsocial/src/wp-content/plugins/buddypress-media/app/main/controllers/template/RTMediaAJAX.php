<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of RTMediaAJAX
 *
 * @author udit
 */
class RTMediaAJAX {

	public function __construct() {
		add_action( 'wp_ajax_rtmedia_backbone_template', array( $this, 'backbone_template' ) );
		add_action( 'wp_ajax_rtmedia_create_album', array( $this, 'create_album' ) );
	}

	function backbone_template() {
		include RTMEDIA_PATH.'templates/media/media-gallery-item.php';
	}

	function create_album() {
		$nonce = $_POST[ 'create_album_nonce' ];

		$return['error'] = false;
		if( wp_verify_nonce( $nonce, 'rtmedia_create_album_nonce' ) && isset( $_POST[ 'name' ] ) && $_POST[ 'name' ] && is_rtmedia_album_enable() ) {
			if( isset( $_POST[ 'context' ] ) && $_POST[ 'context' ] == "group" ) {
				$group_id = !empty( $_POST[ 'context_id' ] ) ? $_POST[ 'context_id' ] : '';

				if( can_user_create_album_in_group( $group_id ) == false ) {
					$return['error'] = __( 'You can not create album in this group.', 'buddypress-media' );
				}
			}

			$create_album = apply_filters( "rtm_is_album_create_enable", true );
			if( !$create_album ) {
				$return['error'] = __( 'You can not create album.', 'buddypress-media' );
			}

			$create_album = apply_filters( "rtm_display_create_album_button", true, $_POST[ 'context_id' ] );
			if( !$create_album ) {
				$return['error'] = __( 'You can not create more albums, you exceed your album limit.', 'buddypress-media' );
			}

			if( $return['error'] !== false ){
				echo json_encode( $return );
				wp_die();
			}

			$album = new RTMediaAlbum();

			// setup context values
			$context = $_POST['context'];
			if( $context == 'profile' ){
				$context_id = get_current_user_id();
			} else {
				$context_id = ( isset( $_POST['context_id'] ) ? $_POST['context_id'] : 0 );
			}

			// setup new album data
			$album_data = apply_filters( 'rtmedia_create_album_data', array(
				'title' => $_POST['name'],
				'author' => get_current_user_id(),
				'new' => true,
				'post_id'=> false,
				'context' => $context,
				'context_id' => $context_id,
			) );

			$rtmedia_id = $album->add( $album_data['title'], $album_data['author'], $album_data['new'], $album_data['post_id'], $album_data['context'], $album_data['context_id'] );

			$rtMediaNav = new RTMediaNav();

			if( $_POST[ 'context' ] == "group" ) {
				$rtMediaNav->refresh_counts( $_POST[ 'context_id' ], array( "context" => $_POST[ 'context' ], 'context_id' => $_POST[ 'context_id' ] ) );
			} else {
				$rtMediaNav->refresh_counts( get_current_user_id(), array( "context" => "profile", 'media_author' => get_current_user_id() ) );
			}

			if( $rtmedia_id ){
				$return['album'] = apply_filters( 'rtmedia_create_album_response', $rtmedia_id );
				echo json_encode( $return );
			} else {
				echo false;
			}
		} else {
			$return['error'] = __( 'Data mismatch, Please insert data properly.', 'buddypress-media' );
			echo json_encode( $return );
		}

		wp_die();
	}
}

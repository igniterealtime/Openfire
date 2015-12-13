<?php

/**
 * Description of RTMediaGroupFeatured
 *
 * @author ritz <ritesh.patel@rtcamp.com>
 */
class RTMediaGroupFeatured extends RTMediaUserInteraction {

	public $group_id;
	public $featured;
	public $settings;

	function __construct( $group_id = false, $flag = true ){
		$args = array(
			'action' => 'group-featured',
			'label' => __( 'Set as Featured', 'buddypress-media' ),
			'plural' => '',
			'undo_label' => __( 'Unset Featured', 'buddypress-media' ),
			'privacy' => 20,
			'countable' => false,
			'single' => true,
			'repeatable' => false,
			'undoable' => true,
			'icon_class' => 'dashicons dashicons-star-filled rtmicon',
		);

		$this->group_id = $group_id;
		parent::__construct( $args );
		$this->settings();
		remove_filter( 'rtmedia_action_buttons_before_delete', array( $this, 'button_filter' ) );
		if ( $flag ){
			add_filter( 'rtmedia_addons_action_buttons', array( $this, 'button_filter' ) );
			add_filter( 'rtmedia_author_media_options', array( $this, 'button_filter' ), 12, 1 );
		}
		//$this->get();
	}

	function before_render(){

		if( ! class_exists( 'BuddyPress' ) || ! bp_is_active( 'groups' ) ){
			return false;
		}

		$this->get();

		// if group id is not set, don't render "Set featured"
		if( empty( $this->group_id ) ){
			return false;
		}

		$user_id = get_current_user_id();

		// if current is not group moderator or group admin, don't render "Set featured"
		if ( ! groups_is_user_mod( $user_id, $this->group_id ) && ! groups_is_user_admin( $user_id, $this->group_id ) && ! is_rt_admin() ){
			return false;
		}

		// if current media is not any group media, don't render "Set featured"
		if ( ( ! ( isset( $this->settings[ $this->media->media_type ] ) && $this->settings[ $this->media->media_type ] ) ) || ( isset( $this->media->context ) && ( 'group' != $this->media->context ) ) ){
			return false;
		}

		if ( isset( $this->action_query ) && isset( $this->action_query->id ) && $this->action_query->id == $this->featured ){
			$this->label = $this->undo_label;
		}
	}

	function set( $media_id = false ){
		if ( false === $media_id ){
			return;
		}
		if ( false === $this->group_id ){
			return;
		}
		groups_update_groupmeta( $this->group_id, 'rtmedia_group_featured_media', $media_id );
	}

	function get(){
		if ( false === $this->group_id ){
			global $groups_template;
			if( !empty( $groups_template->group ) ){
				$group_id = bp_get_group_id();
				if ( !empty( $group_id ) ){
					$this->group_id = $group_id;
				}
			} else if ( isset( $this->media ) && isset( $this->media->context_id ) ){
				$this->group_id = $this->media->context_id;
			} else {
				return false;
			}
		}
		$this->featured = groups_get_groupmeta( $this->group_id, 'rtmedia_group_featured_media', true );
		return $this->featured;
	}

	function settings(){
		global $rtmedia;
		$this->settings['photo']  = isset( $rtmedia->options['allowedTypes_photo_featured'] ) ? $rtmedia->options['allowedTypes_photo_featured'] : 0;
		$this->settings['video']  = isset( $rtmedia->options['allowedTypes_video_featured'] ) ? $rtmedia->options['allowedTypes_video_featured'] : 0;
		$this->settings['music']  = isset( $rtmedia->options['allowedTypes_music_featured'] ) ? $rtmedia->options['allowedTypes_music_featured'] : 0;
		$this->settings['width']  = isset( $rtmedia->options['defaultSizes_featured_default_width'] ) ? $rtmedia->options['defaultSizes_featured_default_width'] : 400;
		$this->settings['height'] = isset( $rtmedia->options['defaultSizes_featured_default_height'] ) ? $rtmedia->options['defaultSizes_featured_default_height'] : 300;
		$this->settings['crop']   = isset( $rtmedia->options['defaultSizes_featured_default_crop'] ) ? $rtmedia->options['defaultSizes_featured_default_crop'] : 1;
	}

	function valid_type( $type ){
		if ( isset( $this->settings[ $type ] ) && $this->settings[ $type ] > 0 ){
			return true;
		}
		return false;
	}

	function get_last_media(){

	}

	function generate_featured_size( $media_id ){
		$metadata = wp_get_attachment_metadata( $media_id );
		$resized  = image_make_intermediate_size( get_attached_file( $media_id ), $this->settings['width'], $this->settings['height'], $this->settings['crop'] );
		if ( $resized ){
			$metadata['sizes']['rt_media_featured_image'] = $resized;
			wp_update_attachment_metadata( $media_id, $metadata );
		}
	}

	function media_exists( $id ){
		global $wpdb;
		$post_exists = $wpdb->get_row( "SELECT * FROM $wpdb->posts WHERE id = '" . $id . "'", 'ARRAY_A' );
		if ( $post_exists ){
			return true;
		} else {
			return false;
		}
	}

	function content(){
		$this->get();
		$actions = $this->model->get( array( 'id' => $this->featured ) );
		if ( ! $actions ){
			return false;
		}

		$featured = $actions[0];
		$type     = $featured->media_type;

		$content_xtra = '';
		switch ( $type ) {
			case 'video' :
				$this->generate_featured_size( $this->featured );
				if ( $featured->media_id ){
					$image_array  = image_downsize( $featured->media_id, 'rt_media_thumbnail' );
					$content_xtra = 'poster="' . $image_array[0] . '" ';
				}
				$content = '<video class="bp-media-featured-media wp-video-shortcode"' . $content_xtra . 'src="' . wp_get_attachment_url( $featured->media_id ) . '" width="' . $this->settings['width'] . '" height="' . $this->settings['height'] . '" type="video/mp4" id="bp_media_video_' . $this->featured . '" controls="controls" preload="true"></video>';
				break;
			case 'music' :
				$content = '<audio class="bp-media-featured-media wp-audio-shortcode"' . $content_xtra . 'src="' . wp_get_attachment_url( $featured->media_id ) . '" width="' . $this->settings['width'] . '" type="audio/mp3" id="bp_media_audio_' . $this->featured . '" controls="controls" preload="none"></video>';
				break;
			case 'photo' :
				$this->generate_featured_size( $featured->media_id );
				$image_array = image_downsize( $featured->media_id, 'rt_media_featured_image' );
				$content     = '<img src="' . $image_array[0] . '" alt="' . $featured->media_title . '" />';
				break;
			default :
				return false;
		}

		return apply_filters( 'rtmedia_featured_media_content', $content );
	}

	function process(){
		if ( ! isset( $this->action_query->id ) ){
			return;
		}
		$return      = array();
		$this->model = new RTMediaModel();
		$actions     = $this->model->get( array( 'id' => $this->action_query->id ) );
		$this->get();
		if ( 1 == intval( $this->settings[ $actions[0]->media_type ] ) ){
			if ( $this->action_query->id == $this->featured ){
				$this->set( 0 );
				$return['next'] = $this->label;
			} else {
				$this->set( $this->action_query->id );
				$return['next'] = $this->undo_label;
			}
			$return['status'] = true;
			global $rtmedia_points_media_id;
			$rtmedia_points_media_id = $this->action_query->id;
			do_action( 'rtmedia_after_set_featured', $this );
		} else {
			$return['status'] = false;
			$return['error']  = __( 'Media type is not allowed', 'buddypress-media' );
		}
		if ( isset( $_REQUEST['json'] ) && 'true' == $_REQUEST['json'] ){
			echo json_encode( $return );
			die();
		} else {
			wp_safe_redirect( esc_url_raw( $_SERVER['HTTP_REFERER'] ) );
		}
	}

}

function rtmedia_group_featured( $group_id = false ){
	echo rtmedia_get_group_featured( $group_id );
}

function rtmedia_get_group_featured( $group_id = false ){
	$featured = new RTMediaGroupFeatured( $group_id, false );
	return $featured->content();
}
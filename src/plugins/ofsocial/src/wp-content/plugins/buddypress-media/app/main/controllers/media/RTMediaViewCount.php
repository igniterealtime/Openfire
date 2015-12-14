<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of RTMediaViewCount
 *
 * @author ritz
 */
class RTMediaViewCount extends RTMediaUserInteraction {
	function __construct() {
		$args = array(
			'action' => 'view', 'label' => 'view', 'privacy' => 0
		);
		parent::__construct( $args );
		remove_filter( 'rtmedia_action_buttons_before_delete', array( $this, 'button_filter' ) );
		add_filter( 'rtmedia_action_buttons_after_delete', array( $this, 'button_filter' ), 99 );
	}

	function render() {
		/**
		 * We were using session to store view count for a media by a particular user.
		 * Session will no more use in rtmedia.
		 * 
		 * All Media View reports will be genrated using rtmedia_interaction table only
		 */
		
		$link = trailingslashit( get_rtmedia_permalink( $this->media->id ) ) . $this->action . '/';
		//echo '<div style="clear:both"></div><form action="'. $link .'" id="rtmedia-media-view-form"></form>';
		echo '<form action="' . $link . '" id="rtmedia-media-view-form"></form>';
		do_action( "rtmedia_view_media_counts", $this );
	}

	function process() {
		$user_id = $this->interactor;
		if ( ! $user_id ){
			$user_id = - 1;
		}
		$media_id           = $this->action_query->id;
		$action             = $this->action_query->action;
		$rtmediainteraction = new RTMediaInteractionModel();
		$check_action       = $rtmediainteraction->check( $user_id, $media_id, $action );
		if ( $check_action ){
			$results       = $rtmediainteraction->get_row( $user_id, $media_id, $action );
			$row           = $results[ 0 ];
			$curr_value    = $row->value;
			$update_data   = array( 'value' => ++$curr_value );
			$where_columns = array(
				'user_id' => $user_id, 'media_id' => $media_id, 'action' => $action
			);
			$update        = $rtmediainteraction->update( $update_data, $where_columns );
		} else {
			$columns   = array(
				'user_id' => $user_id, 'media_id' => $media_id, 'action' => $action, 'value' => "1"
			);
			$insert_id = $rtmediainteraction->insert( $columns );
		}
		global $rtmedia_points_media_id;
		$rtmedia_points_media_id = $this->action_query->id;
		do_action( "rtmedia_after_view_media", $this );
		die();
	}
}
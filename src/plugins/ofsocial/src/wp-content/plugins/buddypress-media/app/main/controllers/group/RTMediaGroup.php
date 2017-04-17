<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 *
 */

class RTMediaGroup {
	public $create_slug = "media-setting";

	function __construct() {
		global $rtmedia;
		$options = $rtmedia->options;
		if ( isset ( $options ['buddypress_enableOnGroup'] ) && $options ['buddypress_enableOnGroup'] == "1" ) {
			// return;
			$extension = true;
			if ( isset( $options['general_enableAlbums'] ) && $options['general_enableAlbums'] == 0 ) {
				$extension = false;
			}
			$extension = apply_filters( 'rtmedia_group_media_extension', $extension );
			if ( ! $extension ) {
				return;
			}

		} else {
			return;
		}

		if ( function_exists( "bp_is_active" ) && bp_is_active( "groups" ) ) {
			bp_register_group_extension( "RTMediaGroupExtension" );
		}

	}

	/**
	 * Update group last activity.
	 *
	 * @param $group_id
	 */
	static function update_last_active( $group_id ){
		groups_update_last_activity( $group_id );
	}

}

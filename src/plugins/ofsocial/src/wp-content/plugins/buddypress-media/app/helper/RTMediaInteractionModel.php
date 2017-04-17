<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of RTMediaInteractionModel
 *
 * @author ritz
 */
class RTMediaInteractionModel extends RTDBModel {

	/**
	 * Constructor
	 *
	 * @access public
	 * @return void
	 */
	public function __construct(){
		parent::__construct( 'rtm_media_interaction', false, 10, true );
	}

	/**
	 * Check user id and media id.
	 *
	 * @access public
	 *
	 * @param  int  $user_id
	 * @param  int  $media_id
	 * @param  type $action
	 *
	 * @return bool
	 */
	public function check( $user_id = '', $media_id = '', $action = '' ){
		if ( $user_id == '' || $media_id == '' || $action == '' ){
			return false;
		}

		$columns = array(
			'user_id' => $user_id,
			'media_id' => $media_id,
			'action' => $action,
		);

		$results = $this->get( $columns );

		if ( $results ){
			return true;
		} else {
			return false;
		}
	}

	/**
	 * get a array of media details.
	 *
	 * @access public
	 *
	 * @param  int  $user_id
	 * @param  int  $media_id
	 * @param  type $action
	 *
	 * @return type $results
	 */
	function get_row( $user_id = '', $media_id = '', $action = '' ){
		if ( $user_id == '' && $media_id == '' && $action == '' ){
			return false;
		}

		$columns = array();
		if ( '' != $user_id ) {
			$columns['user_id'] = $user_id;
		}
		if ( '' != $media_id ) {
			$columns['media_id'] = $media_id;
		}
		if ( '' != $action ) {
			$columns['action'] = $action;
		}
		
		$results = $this->get( $columns );

		return $results;
	}
}

<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of RTMediaContext
 *
 * Default Context - The page on from which the request is generating will be taken
 * as the default context; if any context/context_id is not passed while uploading any media
 * or displaying the gallery.
 *
 * @author saurabh
 */
class RTMediaContext {

	/**
	 *
	 * @var type
	 *
	 * $type - Context Type. It can be any type among these. (post, page, custom_post, home_page, archive etc.)
	 * $id - context id of the context
	 */
	public $type, $id;

	/**
	 *
	 * @return \RTMediaContext
	 */
	function __construct(){
		$this->set_context();

		return $this;
	}

	/**
	 *
	 */
	function set_context(){
		if ( class_exists( 'BuddyPress' ) ){
			$this->set_bp_context();
		} else {
			$this->set_wp_context();
		}
	}

	/**
	 *
	 * @global type $post
	 */
	function set_wp_context(){
		global $post;
		global $bp;
		if ( is_author() ){
			$this->type = 'profile';
			$this->id   = get_query_var( 'author' );
		} elseif ( isset( $post->post_type ) ) {
			$this->type = $post->post_type;
			$this->id   = $post->ID;
		} else {
			$this->type = 'profile';
			$this->id   = get_current_user_id();
		}
		$this->type = apply_filters( 'rtmedia_wp_context_type', $this->type );
		$this->id   = apply_filters( 'rtmedia_wp_context_id', $this->id );
	}

	/**
	 *
	 */
	function set_bp_context(){
		if ( bp_is_blog_page() && ! is_home() ){
			$this->set_wp_context();
		} else {
			$this->set_bp_component_context();
		}
	}

	/**
	 *
	 */
	function set_bp_component_context(){
		if ( bp_displayed_user_id() && ! bp_is_group() ){
			$this->type = 'profile';
		} else {
			if ( ! bp_displayed_user_id() && bp_is_group() ){
				$this->type = 'group';
			} else {
				$this->type = 'profile';
			}
		}
		$this->id = $this->get_current_bp_component_id();
		if ( $this->id == null ){
			global $bp;
			$this->id = $bp->loggedin_user->id;
		}
	}

	/**
	 *
	 * @return type
	 */
	function get_current_bp_component_id(){
		switch ( bp_current_component() ) {
			case 'groups':
				if ( function_exists( 'bp_get_current_group_id' ) ){
					return bp_get_current_group_id();
				}

				return null;
				break;
			default:
				return bp_displayed_user_id();
				break;
		}
	}

}

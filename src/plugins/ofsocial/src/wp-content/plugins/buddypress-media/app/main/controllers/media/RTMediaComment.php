<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of RTMediaComment
 *
 * @author udit
 */
class RTMediaComment {

	var $rtmedia_comment_model;

	public function __construct() {
		$this->rtmedia_comment_model = new RTMediaCommentModel();
	}

	static function comment_nonce_generator($echo = true) {
		if($echo) {
			wp_nonce_field('rtmedia_comment_nonce','rtmedia_comment_nonce');
		} else {
			$token = array(
				'action' => 'rtmedia_comment_nonce',
				'nonce' => wp_create_nonce('rtmedia_comment_nonce')
			);

			return json_encode($token);
		}
	}

	/**
	 * returns user_id of the current logged in user in wordpress
	 *
	 * @global type $current_user
	 * @return type
	 */
	function get_current_id() {

		global $current_user;
		get_currentuserinfo();
		return $current_user->ID;
	}

	/**
	 * returns user_id of the current logged in user in wordpress
	 *
	 * @global type $current_user
	 * @return type
	 */
	function get_current_author() {

		global $current_user;
		get_currentuserinfo();
		return $current_user->user_login;
	}

	function add($attr) {
                global $allowedtags;
	       do_action('rtmedia_before_add_comment', $attr);
               $defaults = array(
                        'user_id'           => $this->get_current_id(),
                        'comment_author'    => $this->get_current_author(),
                        'comment_date'      =>  current_time('mysql')
                );
		$attr[ 'comment_content' ] = wp_kses($attr[ 'comment_content' ], $allowedtags);
                $params = wp_parse_args( $attr, $defaults );
		$id = $this->rtmedia_comment_model->insert($params);
		global $rtmedia_points_media_id;
		$rtmedia_points_media_id = rtmedia_id($params['comment_post_ID']);
		$params['comment_id'] = $id;
		do_action('rtmedia_after_add_comment', $params);

		return $id;
	}

	function remove($id) {

		do_action('rtmedia_before_remove_comment', $id);
                
                $comment = "";
                if(!empty($id)) {
                    $comment = get_comment( $id );
                }
                
                if(isset($comment->comment_post_ID) && isset( $comment->user_id )){
                    $model = new RTMediaModel();
                    //get the current media from the comment_post_ID
                    $media  = $model->get(array('media_id' => $comment->comment_post_ID));
                    $media_author = $media[0]->media_author;
                    // if user is comment creator, or media uploader or admin, allow to delete
                    if( isset( $media[0]->media_author ) && ( is_rt_admin() || $comment->user_id == get_current_user_id() || $media[0]->media_author = get_current_user_id()) ) {
                    
                        $comment_deleted = $this->rtmedia_comment_model->delete($id);

                        do_action('rtmedia_after_remove_comment', $id);

                        return $comment_deleted;
                    }
                }
                return false;
	}
}

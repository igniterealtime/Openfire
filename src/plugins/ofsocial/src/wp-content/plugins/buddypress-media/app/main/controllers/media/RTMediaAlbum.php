<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of RTMediaAlbum
 *
 * @author Udit Desai <udit.desai@rtcamp.com>
 */
class RTMediaAlbum {

	/**
	 *
	 * @var type
	 *
	 * Media object associated with the album. It works as an interface
	 * for the actions specific the media from this album
	 */
	var $media;

	/**
	 *
	 */
	public function __construct() {
		add_action( 'init', array( &$this, 'register_post_types' ), 12 );
		add_action( 'init', array( &$this, 'rtmedia_album_custom_post_status', 13 ) );
		$this->media = new RTMediaMedia();
	}

	function rtmedia_album_custom_post_status() {
		$args = array(
			'label' => _x( 'hidden', 'Status General Name', 'buddypress-media' ), 'label_count' => _n_noop( 'Hidden (%s)', 'Hidden (%s)', 'buddypress-media' ), 'public' => false, 'show_in_admin_all_list' => false, 'show_in_admin_status_list' => false, 'exclude_from_search' => true,
		);
		register_post_status( 'hidden', $args );
	}


	/**
	 * Register Custom Post Types required by rtMedia
	 */
	function register_post_types() {

		/* Set up Album labels */
		$album_labels = array(
			'name' => __( 'Albums', 'buddypress-media' ),
			'singular_name' => __( 'Album', 'buddypress-media' ),
			'add_new' => __( 'Create', 'buddypress-media' ),
			'add_new_item' => __( 'Create Album', 'buddypress-media' ),
			'edit_item' => __( 'Edit Album', 'buddypress-media' ),
			'new_item' => __( 'New Album', 'buddypress-media' ),
			'all_items' => __( 'All Albums', 'buddypress-media' ),
			'view_item' => __( 'View Album', 'buddypress-media' ),
			'search_items' => __( 'Search Albums', 'buddypress-media' ),
			'not_found' => __( 'No album found', 'buddypress-media' ),
			'not_found_in_trash' => __( 'No album found in Trash', 'buddypress-media' ),
			'parent_item_colon' => __( 'Parent', 'buddypress-media' ),
			'menu_name' => __( 'Albums', 'buddypress-media' )
		);

		$album_slug = apply_filters( 'rtmedia_album_rewrite_slug', 'rtmedia-album' );

		$rewrite = array(
			'slug' => $album_slug,
			'with_front' => false,
			'pages' => true,
			'feeds' => false,
		);

		/* Set up Album post type arguments */
		$album_args = array(
			'labels' => $album_labels,
			'public' => false,
			'publicly_queryable' => false,
			'show_ui' => false,
			'show_in_menu' => false,
			'query_var' => "rtmedia_album",
			'capability_type' => 'post',
			'has_archive' => false,
			'hierarchical' => true,
			'menu_position' => null,
			'rewrite' => $rewrite,
			'supports' => array( 'title', 'author', 'thumbnail', 'excerpt', 'comments' )
		);
		$album_args = apply_filters( 'rtmedia_albums_args', $album_args );

		/* register Album post type */
		register_post_type( 'rtmedia_album', $album_args );
	}

	/**
	 * Method verifies the nonce passed while performing any CRUD operations
	 * on the album.
	 *
	 * @param type $mode
	 *
	 * @return boolean
	 */
	function verify_nonce( $mode ) {

		$nonce = $_REQUEST[ "rtmedia_{$mode}_album_nonce" ];
		$mode  = $_REQUEST[ 'mode' ];
		if ( wp_verify_nonce( $nonce, 'rtmedia_' . $mode ) ){
			return true;
		} else {
			return false;
		}
	}

	/**
	 * returns user_id of the current logged in user in wordpress
	 *
	 * @global type $current_user
	 * @return type
	 */
	function get_current_author() {

		return apply_filters( 'rtmedia_current_user', get_current_user_id() );
	}

	/**
	 * Adds a new album
	 *
	 * @global type $rtmedia_interaction
	 *
	 * @param type  $title
	 * @param type  $author_id
	 * @param type  $new
	 * @param type  $post_id
	 *
	 * @return type
	 */
	function add( $title = '', $author_id = false, $new = true, $post_id = false, $context = false, $context_id = false ) {

		global $rtmedia_interaction;
		/* action to perform any task before adding the album */
		do_action( 'rtmedia_before_add_album' );

		$author_id = $author_id ? $author_id : $this->get_current_author();

		/* Album Details which will be passed to Database query to add the album */
		$post_vars = array(
			'post_title' => ( empty ( $title ) ) ? __( 'Untitled Album', 'buddypress-media' ) : $title, 'post_type' => 'rtmedia_album', 'post_author' => $author_id, 'post_status' => 'hidden'
		);

		/* Check whether to create a new album in wp_post table
		 * This is the case when a user creates a album of his own. We need to
		 * create a separte post in wp_post which will work as parent for
		 * all the media uploaded to that album
		 *
		 *  */
		if ( $new ){
			$album_id = wp_insert_post( $post_vars );
		} /**
		 * if user uploads any media directly to a post or a page or any custom
		 * post then the context in which the user is uploading a media becomes
		 * an album in itself. We do not need to create a separate album in this
		 * case.
		 */ else {
			$album_id = $post_id;
		}

		$current_album = get_post( $album_id, ARRAY_A );
		if ( $context === false ){
			$context = ( isset ( $rtmedia_interaction->context->type ) ) ? $rtmedia_interaction->context->type : null;
		}
		if ( $context_id === false ){
			$context_id = ( isset ( $rtmedia_interaction->context->id ) ) ? $rtmedia_interaction->context->id : null;
		}
		// add in the media since album is also a media
		//defaults

		$attributes  = array(
			'blog_id' => get_current_blog_id(), 'media_id' => $album_id, 'album_id' => null, 'media_title' => $current_album[ 'post_title' ], 'media_author' => $current_album[ 'post_author' ], 'media_type' => 'album', 'context' => $context, 'context_id' => $context_id, 'activity_id' => null, 'privacy' => null
		);
		$attributes  = apply_filters( "rtmedia_before_save_album_attributes", $attributes, $_POST );
		$rtmedia_id  = $this->media->insert_album( $attributes );
		$rtMediaNav  = new RTMediaNav();
		$media_count = $rtMediaNav->refresh_counts( $context_id, array( "context" => $context, 'media_author' => $context_id ) );
		/* action to perform any task after adding the album */
		global $rtmedia_points_media_id;
		$rtmedia_points_media_id = $rtmedia_id;
		do_action( 'rtmedia_after_add_album', $this );

		return $rtmedia_id;
	}

	/**
	 * Wrapper method to add a global album
	 *
	 * @param type $title
	 *
	 * @return boolean
	 */
	function add_global( $title = '' ) {

		//		$super_user_ids = get_super_admins();
		$author_id = $this->get_current_author();
		/**
		 * only admin privilaged user can add a global album
		 */
		if ( current_user_can( 'activate_plugins' ) ){

			$album_id = $this->add( $title, $author_id, true, false );

			$this->save_globals( $album_id );

			return $album_id;
		} else {
			return false;
		}
	}

	/**
	 * Get the list of all global albums
	 *
	 * @return type
	 */
	static function get_globals() {
		return rtmedia_get_site_option( 'rtmedia-global-albums' );
	}

	/**
	 * There is a default global album which works as a Wall Post Album for the
	 * user.
	 *
	 * @return type
	 */
	static function get_default() {
		$albums = self::get_globals();
		if ( isset ( $albums[ 0 ] ) ){
			return $albums[ 0 ];
		} else {
			return false;
		}
	}

	/**
	 * Save global albums for newly added album
	 *
	 * @param type $album_ids
	 *
	 * @return boolean
	 */
	function save_globals( $album_ids = false ) {

		if ( ! $album_ids ){
			return false;
		}

		$albums = self::get_globals();

		if ( ! $albums ){
			$albums = array();
		}

		if ( ! is_array( $album_ids ) ){
			$album_ids = array( $album_ids );
		}

		$albums = array_merge( $albums, $album_ids );
		rtmedia_update_site_option( 'rtmedia-global-albums', $albums );
	}

	/**
	 * Wrapper method to update details for any global album
	 *
	 * @param type $id
	 * @param type $title
	 *
	 * @return boolean
	 */
	function update_global( $id, $title = '' ) {

		/**
		 * Only admin can update global albums
		 */
		$super_user_ids = get_super_admins();
		if ( in_array( $this->get_current_author(), $super_user_ids ) ){

			return $this->update( $id, $title );
		} else {
			return false;
		}
	}

	/**
	 * Update any album. Generic method for all the user.
	 *
	 * @param type $id
	 * @param type $title
	 *
	 * @return boolean
	 */
	function update( $id, $title = '' ) {

		/* Action to perform before updating the album */
		do_action( 'rtmedia_before_update_album', $this );
		if ( empty ( $title ) && empty ( $id ) ){
			return false;
		} else {

			$args   = array(
				'ID' => $id, 'post_title' => $title
			);
			$status = wp_insert_post( $args );
			if ( is_wp_error( $status ) || $status == 0 ){
				return false;
			} else {
				/* Action to perform after updating the album */
				do_action( 'rtmedia_after_update_album', $this );

				return true;
			}
		}
	}

	/**
	 * Wrapper method to delete a global album
	 *
	 * @param type $id
	 *
	 * @return boolean
	 */
	function delete_global( $id ) {

		/**
		 * Only admin can delete a global album
		 */
		$super_user_ids = get_super_admins();
		if ( in_array( $this->get_current_author(), $super_user_ids ) ){

			$default_album = self::get_default();

			/**
			 * Default album is NEVER deleted.
			 */
			if ( $id == $default_album ){
				return false;
			}

			/**
			 * If a global album is deleted then all the media of that album
			 * is merged to the default global album and then the album is deleted.
			 */
			//merge with the default album
			$this->merge( $default_album, $id );

			return $this->delete( $id );
		} else {
			return false;
		}
	}

	/**
	 * Generic method to delete any album
	 *
	 * @param type $id
	 *
	 * @return type
	 */
	function delete( $id ) {

		/* action to perform any task befor deleting an album */
		do_action( 'rtmedia_before_delete_album', $this );

		/**
		 * First fetch all the media from that album
		 */
		add_filter( 'rt_db_model_per_page', array( $this, 'set_queries_per_page' ), 10, 2 );
		$page = 1;
		$flag = true;

		/**
		 * Delete each media from the album first
		 */
		while ( $media = $this->media->model->get_by_album_id( $id, $page ) ) {

			$media_id = $media[ 'result' ][ 0 ][ 'media_id' ];

			$flag = wp_delete_attachment( $media_id );

			if ( ! $flag ){
				break;
			}

			$page ++;
		}

		/**
		 * If all the media are deleted from the album then delete the album at last.
		 */
		if ( $flag ){
			$this->media->delete( $id );
		}

		/* action to perform any task after deleting an album */
		do_action( 'rtmedia_after_delete_album', $this );

		return $flag;
	}

	/**
	 * Helper function to set number of queries in pagination
	 *
	 * @param int  $per_page
	 * @param type $table_name
	 *
	 * @return int
	 */
	function set_queries_per_page( $per_page, $table_name ) {

		$per_page = 1;

		return $per_page;
	}

	/**
	 * Generic function to merge two albums
	 *
	 * @param type $primary_album_id
	 * @param type $secondary_album_id
	 *
	 * @return type
	 */
	function merge( $primary_album_id, $secondary_album_id ) {

		add_filter( 'rt_db_model_per_page', array( $this, 'set_queries_per_page' ), 10, 2 );
		$page = 1;

		/**
		 * Transfer all the media from secondary album to primary album
		 */
		while ( $media = $this->media->model->get_by_album_id( $secondary_album_id, $page ) ) {

			$media_id = $media[ 'result' ][ 0 ][ 'media_id' ];
			$this->media->move( $media_id, $primary_album_id );

			$page ++;
		}

		$author        = $this->get_current_author();
		$admins        = get_super_admins();
		$global_albums = self::get_globals();

		if ( in_array( $secondary_album_id, $global_albums ) ){
			if ( in_array( $author, $admins ) ){
				$this->delete_global( $secondary_album_id );
			} else {
				return false;
			}
		} else {
			$this->delete( $secondary_album_id );
		}

		return $primary_album_id;
	}

	//		Legacy code
	//    /**
	//     * Convert a post which is not indexed in rtMedia to an album.
	//     *
	//     * All the attachments from that post will become media of the new album.
	//     *
	//     * @global type $wpdb
	//     * @param type $post_id
	//     * @return boolean
	//     */
	//    function convert_post ( $post_id ) {
	//
	//        global $wpdb;
	//        /**
	//         * Fetch all the attachments from the given post
	//         */
	//        $attachment_ids = $wpdb->get_results ( "SELECT ID
	//								FROM $wpdb->posts
	//								WHERE post_parent = $post_id" );
	//
	//        /**
	//         * Create a album. Not a new album. Just give index to this post in rtMedia
	//         */
	//        $album_id = $this->add ( $post[ 'post_title' ], $post[ 'post_author' ], false, $post_id );
	//
	//        $album_data = $this->model->get_by_media_id ( $album_id );
	//
	//        /* Album details */
	//        $album_meta = array(
	//            'album_id' => $album_id,
	//            'context' => $album_data[ 'results' ][ 0 ][ 'context' ],
	//            'context_id' => $album_data[ 'results' ][ 0 ][ 'context_id' ],
	//            'activity_id' => $album_data[ 'results' ][ 0 ][ 'activity_id' ],
	//            'privacy' => $album_data[ 'results' ][ 0 ][ 'privacy' ]
	//        );
	//
	//        /**
	//         * Index attachments in rtMedia
	//         */
	//        $this->media->insertmedia ( $attachment_ids, $album_meta );
	//
	//        return true;
	//    }
	//
	//    /**
	//     * Check if a post is being indexed as an rtMedia album
	//     * @param integer $post_id the post id to check
	//     * @return boolean if a post is an rtmedia album
	//     */
	//    function is_post_album ( $post_id ) {
	//        $album = $this->model->get ( array( 'album_id' => $post_id ) );
	//        if ( ! empty ( $album ) && count ( $album ) > 0 ) {
	//            return true;
	//        }
	//        return false;
	//    }
	//
	//    /**
	//     * Convert an existing post, with attachments indexed by rtMedia to rtMedia album
	//     * @param integer $post_id The post id to convert
	//     */
	//    function post_to_album ( $post_id ) {
	//        $album_id = $this->add ( $post[ 'post_title' ], $post[ 'post_author' ], false, $post_id );
	//        $this->model->update (
	//                array( 'album_id' => $album_id ), array( 'context' => $post[ 'post_type' ], 'context_id' => $post_id )
	//        );
	//    }

}

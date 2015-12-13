<?php

/**
 * @package rtMedia
 * @subpackage URL Manipulation
 */

/**
 * Handles routing in WordPress for /media/ and /upload/ endpoints
 *
 * @author Saurabh Shukla <saurabh.shukla@rtcamp.com>
 */
class RTMediaRouter {

    /**
     *
     * @var string The slug for the route
     */
    public $slug;

    /**
     *
     * @var array The query variables passed to our route
     */
    public $query_vars;

    /**
     * Initialise the route
     * @param string $slug The slug for which the route needs to be registered, for eg, /media/
     */
    function __construct ( $slug = RTMEDIA_MEDIA_SLUG ) {

        //set up the slug for the route
        $this->slug ( $slug );


        $this->template_redirect ();

        //
        add_filter ( 'template_include', array( $this, 'template_include' ), 0, 1 );
        add_action ( 'wp_ajax_rtmedia_include_gallery_item', array( 'RTMediaTemplate', 'include_gallery_item' ) );
    }

    /**
     * Check if there is a constant defined for this route and use that instead
     * So, this can be overridden by defining RTMEDIA_MEDIA_SLUG in wp-config.php
     *
     * @param string $slug The slug string passed for the route, in the constructor
     */
    function slug ( $slug ) {

        // create the slug constant name
        $slug_constant = 'RTMEDIA_' . strtoupper ( $slug ) . '_SLUG';

        // check if the constant is defined
        if ( defined ( $slug_constant ) ) {

            // assign the value of the constant instead
            $slug = constant ( $slug_constant );
        }

        // set the slug property
        $this->slug = $slug;
    }

    /**
     * Checks if the route has been requested
     *
     * @global object $wp_query
     * @return boolean
     */
    function is_template () {
		global $wp_query;
		global $rtmedia, $rtmedia_query;
		//		if( isset( $rtmedia_query ) && isset( $rtmedia_query->query ) && isset($rtmedia_query->query['context']) ) {
		//	    	if( ( ! isset( $rtmedia->options['buddypress_enableOnGroup'] ) ) || ( $rtmedia_query->query['context'] == "group" && isset( $rtmedia->options['buddypress_enableOnGroup'] ) && $rtmedia->options['buddypress_enableOnGroup'] == '0' ) ) {
		//				$wp_query->is_404 = true;
		//				return false;
		//	    	}
		//	    	if( ( ! isset( $rtmedia->options['buddypress_enableOnProfile'] ) ) || ( $rtmedia_query->query['context'] == "profile" && isset( $rtmedia->options['buddypress_enableOnProfile'] ) && $rtmedia->options['buddypress_enableOnProfile'] == '0' ) ) {
		//				$wp_query->is_404 = true;
		//				return false;
		//	    	}
		//		}
        $return = isset ( $wp_query->query_vars[ $this->slug ] );
		$return = apply_filters('rtmedia_return_is_template',$return,$this->slug);
        if ( $return ) {
            if ( isset ( $wp_query->query_vars[ 'action' ] ) && $wp_query->query_vars[ 'action' ] == 'bp_avatar_upload' )
                $return = false;
            if ( isset ( $wp_query->query_vars[ 'pagename' ] ) && $wp_query->query_vars[ 'pagename' ] == 'group-avatar' )
                $return = false;
	    $wp_query->is_feed = false;
        }

        if ( $return ) {
            $wp_query->is_404 = false;
        }

		if( isset( $rtmedia_query ) && isset( $rtmedia_query->query ) && ! isset($rtmedia_query->query['context']) ) {
			$wp_query->is_404 = true;
			$return = false;
		}

		if( isset( $rtmedia_query->shortcode_global ) && $rtmedia_query->shortcode_global ) {
			$wp_query->is_404 = false;
			$return = true;
		}
        return $return;
    }

    /**
     * Hook into the template redirect action to populate the global objects
     *
     */
    function template_redirect () {

        // if it is not our route, return early
        if ( ! $this->is_template () )
            return;

        status_header ( 200 );
        //set up the query variables
        $this->set_query_vars ();


        // otherwise provide a hook for only this route,
        // pass the slug to the function hooking here
        do_action ( "rtmedia_" . $this->slug . "_redirect" );
    }

    /**
     * Hook into the template_include filter to load custom template files
     *
     * @param string $template Template file path of the default template
     * @return string File path of the template file to be loaded
     */
    function template_include ( $template ) {

        // if it is not our route, return the default template early
        if ( ! $this->is_template () )
            return $template;

        // otherwise, apply a filter to the template,
        // pass the template  and slug to the function hooking here
        // so it can load a custom template

        $template_load = new RTMediaTemplate();
        global $new_rt_template;
        $new_rt_template = $template_load->set_template ( $template );

        $new_rt_template = apply_filters ( "rtmedia_" . $this->slug . "_include", $new_rt_template );
        global $rt_ajax_request;
        $rt_ajax_request = false;

        // check if it is an ajax request
        if (
                        ! empty( $_SERVER[ 'HTTP_X_REQUESTED_WITH' ] ) &&
                        strtolower( $_SERVER[ 'HTTP_X_REQUESTED_WITH' ] ) == 'xmlhttprequest'
         ){
                $rt_ajax_request = true;
        }
         if($rt_ajax_request)
             return $new_rt_template;
        if(  function_exists ('bp_set_theme_compat_active'))
        	bp_set_theme_compat_active( apply_filters( 'rtmedia_main_template_set_theme_compat', true ) );
        add_filter( 'the_content', array(&$this,'rt_replace_the_content') );
        $this ->rt_theme_compat_reset_post();

        return apply_filters( 'rtmedia_main_template_include', $template, $new_rt_template );

    }

    /**
 * This fun little function fills up some WordPress globals with dummy data to
 * stop your average page template from complaining about it missing.
 *
 * @since BuddyPress (1.7)
 * @global WP_Query $wp_query
 * @global object $post
 * @param array $args
 */
    function rt_replace_the_content( $content = '' ) {
	// Do we have new content to replace the old content?
	global $new_rt_template, $rt_template_content;
	//var_dump($new_rt_template);
	if( !isset( $rt_template_content ) ) {
	    ob_start();
	    load_template($new_rt_template);
	    $rt_template_content = ob_get_contents ();
	    ob_end_clean();
	}
	return $rt_template_content;
        $new_content = apply_filters( 'bp_replace_the_content', $rt_template_content );

	// Juggle the content around and try to prevent unsightly comments
        if ( !empty( $new_content ) && ( $new_content !== $rt_template_content ) ) {

		// Set the content to be the new content
		$rt_template_content = $new_content;

		// Clean up after ourselves
		unset( $new_content );

		// Reset the $post global
		wp_reset_postdata();
	}

	// Return possibly hi-jacked content
	return $content;
}
function rt_theme_compat_reset_post( $args = array() ) {
	global $wp_query, $post;

	// Switch defaults if post is set
        global $rtmedia_query;
	 if ( isset( $wp_query->post ) ) {
             if(isset($rtmedia_query->query) && isset( $rtmedia_query->query["media_type"] ) && $rtmedia_query->query["media_type"] == "album" && isset( $rtmedia_query->media_query["album_id"])){
                 foreach($rtmedia_query->album as $al){
                     if($al->id == $rtmedia_query->media_query["album_id"]){
                         $wp_query->post = get_post($al->media_id);
                         break;
                     }
                 }
             }else if( isset($rtmedia_query->media) && count($rtmedia_query->media) ==  1 && $rtmedia_query->media ){
		 $wp_query->post = get_post($rtmedia_query->media[0]->media_id);
             }
		$dummy = wp_parse_args( $args, array(
			'ID'                    => $wp_query->post->ID,
			'post_status'           => $wp_query->post->post_status,
			'post_author'           => $wp_query->post->post_author,
			'post_parent'           => $wp_query->post->post_parent,
			'post_type'             => 'rtmedia', //$wp_query->post->post_type,
			'post_date'             => $wp_query->post->post_date,
			'post_date_gmt'         => $wp_query->post->post_date_gmt,
			'post_modified'         => $wp_query->post->post_modified,
			'post_modified_gmt'     => $wp_query->post->post_modified_gmt,
			'post_content'          => $wp_query->post->post_content,
			'post_title'            => $wp_query->post->post_title,
			'post_excerpt'          => $wp_query->post->post_excerpt,
			'post_content_filtered' => $wp_query->post->post_content_filtered,
			'post_mime_type'        => $wp_query->post->post_mime_type,
			'post_password'         => $wp_query->post->post_password,
			'post_name'             => $wp_query->post->post_name,
			'guid'                  => $wp_query->post->guid,
			'menu_order'            => $wp_query->post->menu_order,
			'pinged'                => $wp_query->post->pinged,
			'to_ping'               => $wp_query->post->to_ping,
			'ping_status'           => $wp_query->post->ping_status,
			'comment_status'        => $wp_query->post->comment_status,
			'comment_count'         => $wp_query->post->comment_count,
			'filter'                => $wp_query->post->filter,

			'is_404'                => false,
			'is_page'               => false,
			'is_single'             => false,
			'is_archive'            => false,
			'is_tax'                => false,
		) );
	} else {
		$dummy = wp_parse_args( $args, array(
			'ID'                    => 0,
			'post_status'           => 'public',
			'post_author'           => 0,
			'post_parent'           => 0,
			'post_type'             => 'bp_member',
			'post_date'             => 0,
			'post_date_gmt'         => 0,
			'post_modified'         => 0,
			'post_modified_gmt'     => 0,
			'post_content'          => '',
			'post_title'            => '',
			'post_excerpt'          => '',
			'post_content_filtered' => '',
			'post_mime_type'        => '',
			'post_password'         => '',
			'post_name'             => '',
			'guid'                  => '',
			'menu_order'            => 0,
			'pinged'                => '',
			'to_ping'               => '',
			'ping_status'           => '',
			'comment_status'        => 'closed',
			'comment_count'         => 0,
			'filter'                => 'raw',
			'is_404'                => false,
			'is_page'               => false,
			'is_single'             => false,
			'is_archive'            => false,
			'is_tax'                => false,
		) );
	}
        if(  function_exists ( "bp_is_group")){
            if(bp_is_group ( )){
                $dummy['post_type'] = "bp_group";
                if("bp-default" != get_option( 'stylesheet' ))
                    $dummy['post_title'] = '<a href="' . bp_get_group_permalink( groups_get_current_group() ) . '">' . bp_get_current_group_name() . '</a>';
            }else{
                $dummy['post_type'] = "bp_member";
                if("bp-default" != get_option( 'stylesheet' ))
                    $dummy['post_title'] =  '<a href="' . bp_get_displayed_user_link() . '">' . bp_get_displayed_user_fullname() . '</a>';
            }
        }else{
            global $rtmedia_query;
            $dummy['comment_status'] = 'closed';
            if(isset($rtmedia_query->media_query)){
                if(isset($rtmedia_query->media_query["media_author"])){
                    $dummy["post_author"] =$rtmedia_query->media_query["media_author"];
                }
                if(isset($rtmedia_query->media_query["id"])){
                    //var_dump($rtmedia_query);
                    //echo $rtmedia_query->media_query["id"];
                }
            }
        }


	// Bail if dummy post is empty
	if ( empty( $dummy ) ) {
		return;
	}

	// Set the $post global
	$post = new WP_Post( (object) $dummy );

	// Copy the new post global into the main $wp_query
	$wp_query->post       = $post;
	$wp_query->posts      = array( $post );

	// Prevent comments form from appearing
	$wp_query->post_count = 1;
	$wp_query->is_404     = $dummy['is_404'];
	$wp_query->is_page    = $dummy['is_page'];
	$wp_query->is_single  = $dummy['is_single'];
	$wp_query->is_archive = $dummy['is_archive'];
	$wp_query->is_tax     = $dummy['is_tax'];

	// Clean up the dummy post
	unset( $dummy );

	/**
	 * Force the header back to 200 status if not a deliberate 404
	 *
	 * @see http://bbpress.trac.wordpress.org/ticket/1973
	 */
	if ( ! $wp_query->is_404() ) {
		status_header( 200 );
	}


}


    /**
     * Break the request URL into an array of variables after the route slug
     *
     * @global object $wp_query
     */
    function set_query_vars () {

        global $wp_query;
	$query_vars_array = "";
	if( isset( $wp_query->query_vars[ $this->slug ] ) ) {
	    $query_vars_array = explode ( '/', $wp_query->query_vars[ $this->slug ] );
	}
        $this->query_vars = apply_filters ( 'rtmedia_query_vars', $query_vars_array );
    }

}
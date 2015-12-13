<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of RTMediaGalleryShortcode
 *
 * rtMedia Gallery Shortcode to embedd a gallery of media anywhere
 *
 * @author Udit Desai <udit.desai@rtcamp.com>
 */
class RTMediaGalleryShortcode {

    static $add_script;

    /**
     *
     */
    public function __construct () {

        add_shortcode ( 'rtmedia_gallery', array( 'RTMediaGalleryShortcode', 'render' ) );
        add_action( 'wp_ajax_rtmedia_get_template',array(&$this,'ajax_rtmedia_get_template' ));
        add_action( 'wp_ajax_nopriv_rtmedia_get_template', array(&$this,'ajax_rtmedia_get_template' ));
        //add_action('init', array($this, 'register_scripts'));
        //add_action('wp_footer', array($this, 'print_script'));
    }

    function ajax_rtmedia_get_template(){
        if(isset($_REQUEST["template"])){
            $template_url = RTMediaTemplate::locate_template( $_REQUEST["template"], "media/", false );
            require_once $template_url ;
        }
        die();
    }
    static function register_scripts () {
		if( ! wp_script_is ( 'plupload-all' ) ) {
			wp_enqueue_script ( 'plupload-all' );
		}
        wp_enqueue_script ( 'rtmedia-backbone', RTMEDIA_URL . 'app/assets/js/rtMedia.backbone.js', array( 'plupload-all', 'backbone' ), RTMEDIA_VERSION, true );

    	if(is_rtmedia_album_gallery()) {
    	    $template_url = esc_url( add_query_arg( array( "action" => 'rtmedia_get_template', "template" => "album-gallery-item" ),admin_url("admin-ajax.php") ), null, '' );
        }else{
                $template_url = esc_url( add_query_arg( array( "action" => 'rtmedia_get_template', "template" => apply_filters('rtmedia_backbone_template_filter',"media-gallery-item") ),admin_url("admin-ajax.php") ), null, '' );
        }
        wp_localize_script ( 'rtmedia-backbone', 'template_url', $template_url );
    	$url = trailingslashit ( $_SERVER[ "REQUEST_URI" ] );
		$rtmedia_slug = "/".RTMEDIA_MEDIA_SLUG;
        // check position of media slug from end of the URL
		if ( strrpos ( $url, $rtmedia_slug ) !== false ) {
			// split the url upto the last occurance of media slug
			$url_upload = substr( $url, 0, strrpos ( $url, $rtmedia_slug ) );
			$url = trailingslashit ( $url_upload ) . "upload/";
        } else {
            $url = trailingslashit ( $url ) . "upload/";
        }
		
        $params = array(
            'url' => $url,
            'runtimes' => 'html5,flash,html4',
            'browse_button' => 'rtMedia-upload-button',
            'container' => 'rtmedia-upload-container',
            'drop_element' => 'drag-drop-area',
            'filters' => apply_filters ( 'rtmedia_plupload_files_filter', array( array( 'title' => "Media Files", 'extensions' => get_rtmedia_allowed_upload_type () ) ) ),
            'max_file_size' => ( wp_max_upload_size() ) / ( 1024 * 1024 ) . 'M',
            'multipart' => true,
            'urlstream_upload' => true,
            'flash_swf_url' => includes_url ( 'js/plupload/plupload.flash.swf' ),
            'silverlight_xap_url' => includes_url ( 'js/plupload/plupload.silverlight.xap' ),
            'file_data_name' => 'rtmedia_file', // key passed to $_FILE.
            'multi_selection' => true,
            'multipart_params' => apply_filters ( 'rtmedia-multi-params', array( 'redirect' => 'no', 'action' => 'wp_handle_upload', '_wp_http_referer' => $_SERVER[ 'REQUEST_URI' ], 'mode' => 'file_upload', 'rtmedia_upload_nonce' => RTMediaUploadView::upload_nonce_generator ( false, true ) ) ),
	    'max_file_size_msg' => apply_filters("rtmedia_plupload_file_size_msg",min ( array( ini_get ( 'upload_max_filesize' ), ini_get ( 'post_max_size' ) ) ))
        );
        if ( wp_is_mobile () )
            $params[ 'multi_selection' ] = false;

	    $params = apply_filters("rtmedia_modify_upload_params",$params);
       
        global $rtmedia;
        $rtmedia_extns = array();
        
        foreach( $rtmedia->allowed_types as $allowed_types_key => $allowed_types_value ) {
            $rtmedia_extns[ $allowed_types_key ] = $allowed_types_value[ 'extn' ];
        }
        
        wp_localize_script ( 'rtmedia-backbone', 'rtmedia_exteansions', $rtmedia_extns );
        wp_localize_script ( 'rtmedia-backbone', 'rtMedia_plupload_config', $params );
        wp_localize_script ( 'rtmedia-backbone', 'rMedia_loading_file', admin_url ( "/images/loading.gif" ) );
    }

    /**
     * Helper function to check whether the shortcode should be rendered or not
     *
     * @return type
     */
    static function display_allowed () {
        $flag = true;

        //$flag = !(is_home() || is_post_type_archive() || is_author());
        $flag = apply_filters ( 'before_rtmedia_gallery_display', $flag );
        return $flag;
    }

    /**
     * Render a shortcode according to the attributes passed with it
     *
     * @param boolean $attr
     */
    static function render ( $attr ) {
        if ( self::display_allowed () ) {
            self::$add_script = true;
                    
            ob_start ();
            $authorized_member = true; //by default, viewer is authorized
            
            if ( ( ! isset ( $attr )) || empty ( $attr ) )
                $attr = true;

            $attr = array( 'name' => 'gallery', 'attr' => $attr );
            global $post;
            if ( isset ( $attr ) && isset ( $attr[ "attr" ] ) ) {
                if ( ! is_array ( $attr[ "attr" ] ) ) {
                    $attr[ "attr" ] = Array( );
                }
                if ( ! isset ( $attr[ "attr" ][ "context_id" ] )
	                && isset ( $attr[ "attr" ][ "context" ] )
	                && $attr[ "attr" ][ "context" ] == 'profile' ) {
	                $attr[ "attr" ][ "context_id" ] = get_current_user_id();
                } elseif( ! isset ( $attr[ "attr" ][ "context_id" ] ) && isset ( $post->ID ) ){
	                $attr[ "attr" ][ "context_id" ] = $post->ID;
                }
                
                //check if context is group, then the gallery should only be visible to users according to the group privacy
                if(isset($attr['attr']['context']) && $attr['attr']['context'] == 'group'){
                    
                    if(function_exists('groups_get_group')){ //if buddypress group is enabled
                        $group = groups_get_group( array( 'group_id' => $attr[ "attr" ][ "context_id" ] ) );
                        if( isset($group->status) && $group->status != 'public'){
                            if(is_user_logged_in()){
                                $is_member = groups_is_user_member( get_current_user_id() , $attr[ "attr" ][ "context_id" ] ) ;
                                if(!$is_member){
                                   $authorized_member = false;
                                   //if user doesnot have access to the specified group
                                }   
                            }else {
                                $authorized_member = false;
                                //if user is  groupnot logged in and visits group media gallery 
                            }
                            
                        }
                    }

                }
                
                if ( ! isset ( $attr[ "attr" ][ "context" ] ) && isset ( $post->post_type ) ) {
                    $attr[ "attr" ][ "context" ] = $post->post_type;
                }
            }

            if( $authorized_member ){ // if current user has access to view the gallery (when context is 'group')
                global $rtmedia_query;
                if(!$rtmedia_query) {
                    $rtmedia_query = new RTMediaQuery($attr[ "attr" ]);
                }
                $rtmedia_query->is_gallery_shortcode = true;// to check if gallery shortcode is executed to display the gallery.
                
                $template = new RTMediaTemplate();
                $gallery_template = false;
                if( isset( $attr[ "attr" ][ "global"]) &&  $attr[ "attr" ][ "global"] == true){
                    add_filter('rtmedia-model-where-query', array( 'RTMediaGalleryShortcode' , 'rtmedia_query_where_filter'), 10 ,3 );
                }
                $template->set_template ( $gallery_template, $attr );
                if( isset( $attr[ "attr" ][ "global"]) &&  $attr[ "attr" ][ "global"] == true){
                    remove_filter('rtmedia-model-where-query', array( 'RTMediaGalleryShortcode' , 'rtmedia_query_where_filter'), 10 ,3 );
                }
                
            } else { //if user cannot view the media gallery (when context is 'group'), show message
                echo __ ( 'You do not have sufficient privileges to view this gallery', 'buddypress-media' );
                return false;
            }

            return ob_get_clean ();
        }
    }
    // for gallery shortcode having attribute global as true, include all media except ones having context as "group"
    static function rtmedia_query_where_filter($where, $table_name, $join) {
        $where .= ' AND (' . $table_name . '.privacy = "0" OR ' . $table_name . '.privacy is NULL ) ';
        return $where;
    }
    
    static function print_script () {
        if ( ! self::$add_script )
            return;
        if ( ! wp_script_is ( 'rtmedia-backbone' ) ) {
            wp_print_scripts ( 'rtmedia-backbone' );
        }
    }

}

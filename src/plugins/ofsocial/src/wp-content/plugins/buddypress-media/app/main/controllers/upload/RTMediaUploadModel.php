<?php

/**
 * Description of RTMediaUploadModel
 *
 * @author Joshua Abenazer <joshua.abenazer@rtcamp.com>
 */
class RTMediaUploadModel {

    public $upload = array(
        'mode' => 'file_upload',
        'context' => false,
        'context_id' => false,
        'privacy' => 0,
        'custom_fields' => array( ),
        'taxonomy' => array( ),
        'album_id' => false,
        'files' => false,
        'title' => false,
        'description' => false,
        'media_author' => false
    );

    /**
     *
     * @return array
     */
    function set_post_object ( $upload_params = array() ) {
	    $upload_array = empty( $upload_params ) ? $_POST : $upload_params;
        $this->upload = wp_parse_args ( $upload_array, $this->upload );
        $this->sanitize_object ();
        return $this->upload;
    }

    /**
     *
     * @return boolean
     */
    function has_context () {
        if ( isset ( $this->upload[ 'context_id' ] ) && ! empty ( $this->upload[ 'context_id' ] ) )
            return true;
        return false;
    }

    /**
     *
     * @global type $rtmedia_interaction
     */
    function sanitize_object () {

        if ( ! $this->has_context () ) {
            // Set context_id to Logged in user id if context is profile and context_id is not provided
            if( $this->upload[ 'context' ] == 'profile' ) {
                $this->upload[ 'context_id' ] = get_current_user_id();
            } else {
                global $rtmedia_interaction;

                $this->upload[ 'context' ] = $rtmedia_interaction->context->type;
                $this->upload[ 'context_id' ] = $rtmedia_interaction->context->id;
            }
        }
        
        if ( ! is_array ( $this->upload[ 'taxonomy' ] ) )
            $this->upload[ 'taxonomy' ] = array( $this->upload[ 'taxonomy' ] );

        if ( ! is_array ( $this->upload[ 'custom_fields' ] ) )
            $this->upload[ 'custom_fields' ] = array( $this->upload[ 'custom_fields' ] );

        if ( ! $this->has_album_id () || ! $this->has_album_permissions () )
            $this->set_album_id ();

        if ( ! $this->has_author () )
            $this->set_author ();
        if ( is_rtmedia_privacy_enable () ) {
            if ( is_rtmedia_privacy_user_overide () ) {
                if ( ! isset ( $_POST[ "privacy" ] ) ) {
                    $this->upload[ 'privacy' ] = get_rtmedia_default_privacy ();
                } else {
                    $this->upload[ 'privacy' ] = $_POST[ "privacy" ];
                }
            } else {
                $this->upload[ 'privacy' ] = get_rtmedia_default_privacy ();
            }
        } else {
            $this->upload[ 'privacy' ] = 0;
        }
    }

    /**
     *
     * @return type
     */
    function has_author () {
        return $this->upload[ 'media_author' ];
    }

    function set_author () {
        $this->upload[ 'media_author' ] = get_current_user_id ();
    }

    /**
     *
     * @return boolean
     */
    function has_album_id () {
        if ( ! $this->upload[ 'album_id' ] || $this->upload[ 'album_id' ] == "undefined" )
            return false;
        return true;
    }

    /**
     *
     * @return boolean
     */
    function has_album_permissions () {
        //yet to be coded for the privacy options of the album
        return true;
    }

    /**
     *
     * @param type $id
     * @return boolean
     */
    function album_id_exists ( $id ) {
        return true;
    }

    /**
     *
     */
    function set_album_id () {
        if ( class_exists ( 'BuddyPress' ) ) {
            $this->set_bp_album_id ();
        } else {
            $this->set_wp_album_id ();
        }
    }

    /**
     *
     */
    function set_bp_album_id () {
        if ( bp_is_blog_page () ) {
            $this->set_wp_album_id ();
        } else {
            $this->set_bp_component_album_id ();
        }
    }

    /**
     *
     * @throws RTMediaUploadException
     */
    function set_wp_album_id () {
        if ( isset ( $this->upload[ 'context' ] ) ) {
            $this->upload[ 'album_id' ] = $this->upload[ 'context_id' ];               
            // If context is profile then set album_id to default global album
            if( $this->upload[ 'context' ] == 'profile' ){
                $this->upload[ 'album_id' ] = RTMediaAlbum::get_default();
            }
        } else {
            throw new RTMediaUploadException ( 9 ); // Invalid Context
        }
    }

    /**
     *
     */
    function set_bp_component_album_id () {
        switch ( bp_current_component () ) {
            case 'groups': $this->upload[ 'album_id' ] = RTMediaAlbum::get_default ();
                break;
            default:
                $this->upload[ 'album_id' ] = RTMediaAlbum::get_default ();
                break;
        }
    }

}

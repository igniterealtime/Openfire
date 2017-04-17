<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of RTMediaMedia
 *
 * @author Udit Desai <udit.desai@rtcamp.com>
 */
class RTMediaMedia {

    static $default_object = array(
        'id', 'blog_id', 'media_id', 'media_author', 'media_title', 'album_id', 'media_type', 'context', 'context_id', 'source', 'source_id', 'activity_id', 'cover_art', 'privacy', 'views', 'downloads', 'ratings_total', 'ratings_count', 'ratings_average', 'likes', 'dislikes'
    );

    /**
     * DB Model object to interact on Database operations
     *
     * @var object the database model
     */
    var $model;

    /**
     * Initialises the model object of the mediua object
     */
    public function __construct() {

        $this->model = new RTMediaModel();
    }

    /**
     * Generate nonce
     *
     * @param boolean $echo whether nonce should be echoed
     *
     * @return string json encoded nonce
     */
    static function media_nonce_generator( $id, $echo = true ) {
        if( $echo ) {
            wp_nonce_field( 'rtmedia_' . $id, 'rtmedia_media_nonce' );
        } else {
            $token = array(
                'action' => 'rtmedia_media_nonce', 'nonce' => wp_create_nonce( 'rtmedia_' . $id )
            );

            return json_encode( $token );
        }
    }

    /**
     * Method verifies the nonce passed while performing any CRUD operations
     * on the media.
     *
     * @param string $mode The upload mode
     *
     * @return boolean whether the nonce is valid
     */
    function verify_nonce( $mode ) {

        $nonce = $_REQUEST[ "rtmedia_{$mode}_media_nonce" ];
        $mode = $_REQUEST[ 'mode' ];

        if( wp_verify_nonce( $nonce, 'rtmedia_' . $mode ) ) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a hook to delete_attachment tag called
     * when a media is deleted externally out of rtMedia context
     */
    public function delete_hook() {
        add_action( 'delete_attachment', array( $this, 'delete_wordpress_attachment' ) );
        add_action( 'delete_user', array( $this, 'reassign_wordpress_user' ), 10, 2 );
    }

    /**
     * Adds taxonomy
     *
     * @param array $attachments ids of the attachments created after upload
     * @param array $taxonomies  array of terms indexed by a taxonomy
     */
    function add_taxonomy( $attachments, $taxonomies ) {

        foreach( $attachments as $id ) {

            foreach( $taxonomies as $taxonomy => $terms ) {
                if( !taxonomy_exists( $taxonomy ) ) {
                    continue;
                }

                wp_set_object_terms( $id, $terms, $taxonomy );
            }
        }
    }

    /**
     *
     * @param array $attachments   attachment ids
     * @param array $custom_fields array of key value pairs of meta
     *
     * @return boolean success of meta
     */
    function add_meta( $attachments, $custom_fields ) {

        foreach( $attachments as $id ) {
            $row = array( 'media_id' => $id );

            foreach( $custom_fields as $key => $value ) {

                if( !is_null( $value ) ) {
                    $row[ 'meta_key' ] = $key;
                    $row[ 'meta_value' ] = $value;
                    $status = add_rtmedia_meta( $id, $key, $value );

                    if( is_wp_error( $status ) || $status == 0 ) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Helper method to check for multisite - will add a few additional checks
     * for handling taxonomies
     *
     * @return boolean
     */
    function is_multisite() {
        return is_multisite();
    }

    /**
     * Generic method to add a media
     *
     * @param type $uploaded
     * @param type $file_object
     *
     * @return type
     */
    function add( $uploaded, $file_object ) {

        /* action to perform any task before adding a media */
        do_action( 'rtmedia_before_add_media', $file_object, $uploaded );

        /* Generate media details required to feed in database */
        $attachments = $this->generate_post_array( $uploaded, $file_object );

        /* Insert the media as an attachment in Wordpress context */
        $attachment_ids = $this->insert_attachment( $attachments, $file_object );

        /* check for multisite and if valid then add taxonomies */
        if( !$this->is_multisite() ) {
            $this->add_taxonomy( $attachment_ids, $uploaded[ 'taxonomy' ] );
        }

        /* fetch custom fields and add them to meta table */
        $this->add_meta( $attachment_ids, $uploaded[ 'custom_fields' ] );


        /* add media in rtMedia context */
        $media_ids = $this->insertmedia( $attachment_ids, $uploaded, $file_object /* passing file object to check the extension */ );

        $rtmedia_type = rtmedia_type( $media_ids );
        /* action to perform any task after adding a media */
        global $rtmedia_points_media_id;
        if( $media_ids && is_array( $media_ids ) && isset( $media_ids[ 0 ] ) ) {
            $rtmedia_points_media_id = $media_ids[ 0 ];
        }

        do_action( 'rtmedia_after_add_' . $rtmedia_type );

        do_action( 'rtmedia_after_add_media', $media_ids, $file_object, $uploaded );

        return $media_ids;
    }

    /**
     * Generic method to update a media. media details can be changed from this method
     *
     * @param type $media_id
     * @param type $meta
     *
     * @return boolean
     */
    function update( $id, $data, $media_id ) {

        /* action to perform any task before updating a media */
        do_action( 'rtmedia_before_update_media', $id );

        $defaults = array();
        $data = wp_parse_args( $data, $defaults );
        $where = array( 'id' => $id );

        if( array_key_exists( 'media_title', $data ) || array_key_exists( 'description', $data ) ) {
            $post_data[ 'ID' ] = $media_id;
            if( isset( $data[ 'media_title' ] ) ) {
                $data[ 'media_title' ] = wp_kses( $data[ 'media_title' ], wp_kses_allowed_html() );
                $post_data[ 'post_title' ] = $data[ 'media_title' ];
                $post_data[ 'post_name' ] = $data[ 'media_title' ];
            }
            if( isset( $data[ 'description' ] ) ) {
                $post_data[ 'post_content' ] = $data[ 'description' ];
                $post_data[ 'description' ] = sanitize_text_field( $data[ 'description' ] );
                unset( $data[ 'description' ] );
            }
            wp_update_post( $post_data );
        }

        $status = $this->model->update( $data, $where );

        // insert/update activity details in rtmedia activity table
        $media_model = new RTMediaModel();
        $media = $media_model->get( array( 'id' => $id ) );

        if( !empty( $media ) ) {
            $rtmedia_activity_model = new RTMediaActivityModel();
            $similar_media = $media_model->get( array( 'activity_id' => $media[ 0 ]->activity_id ) );
            $max_privacy = 0;

            foreach( $similar_media as $s_media ) {
                if( $s_media->privacy > $max_privacy ) {
                    $max_privacy = $s_media->privacy;
                }
            }

            if( !$rtmedia_activity_model->check( $media[ 0 ]->activity_id ) ) {
                $rtmedia_activity_model->insert( array(
                    'activity_id' => $media[ 0 ]->activity_id, 'user_id' => $media[ 0 ]->media_author, 'privacy' => $max_privacy
                ) );
            } else {
                $rtmedia_activity_model->update( array(
                    'activity_id' => $media[ 0 ]->activity_id, 'user_id' => $media[ 0 ]->media_author, 'privacy' => $max_privacy
                    ), array( 'activity_id' => $media[ 0 ]->activity_id ) );
            }
        }

        /* action to perform any task after updating a media */
        do_action( 'rtmedia_after_update_media', $id );

        if( $status === false ) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Generic method to delete a media from wordpress media library ( other than by rtMedia )
     *
     * @param type $media_id
     *
     * @return boolean
     */
    function delete_wordpress_attachment( $id ) {
        $media = $this->model->get( array( 'media_id' => $id ), false, false );

        if( $media ) {
            $this->delete( $media[ 0 ]->id, true );
        }
    }

    /**
     * Method to reassign media to another user while deleting user
     *
     * @param type $user_id, $reassign
     *
     */
    public function reassign_wordpress_user( $user_id, $reassign ) {
        if( $reassign != null || $reassign != '' ) {
            // Updating media author
            $rtmedia_model = new RTMediaModel();
            $rtmedia_model->update( array( 'media_author' => $reassign ), array( 'media_author' => $user_id ) );

            // Updating user id from interaction
            $rtmediainteraction = new RTMediaInteractionModel();
            $rtmediainteraction->update( array( 'user_id' => $reassign ), array( 'user_id' => $user_id ) );
        }
    }

    /**
     * Generic method to delete a media
     *
     * @param type $media_id
     *
     * @return boolean
     */
    function delete( $id, $core = false, $delete_activity = true ) {
        do_action( 'rtmedia_before_delete_media', $id );

        $media = $this->model->get( array( 'id' => $id ), false, false );

        $status = 0;

        if( $media ) {
            /* delete meta */
            //delete_rtmedia_meta($id);
            if( $delete_activity ) {
                if( $media[ 0 ]->activity_id && function_exists( 'bp_activity_delete_by_activity_id' ) ) {
                    $related_media = $this->model->get( array( 'activity_id' => $media[ 0 ]->activity_id ), false, false );
                    if( count( $related_media ) > 1 ) {
                        $activity_media = array();
                        foreach( $related_media as $temp_media ) {
                            if( $temp_media->id == $id ) {
                                continue;
                            }
                            $activity_media[] = $temp_media->id;
                        }
                        $objActivity = new RTMediaActivity( $activity_media );
                        global $wpdb, $bp;
                        $wpdb->update( $bp->activity->table_name, array( "type" => "rtmedia_update", "content" => $objActivity->create_activity_html() ), array( "id" => $media[ 0 ]->activity_id ) );
                    } else {
                        if( isset( $media[ 0 ] ) && isset( $media[ 0 ]->activity_id ) ) {
                            bp_activity_delete_by_activity_id( $media[ 0 ]->activity_id );
                        }
                    }
                }
            }
            if( !$core ) {
                wp_delete_attachment( $media[ 0 ]->media_id, true );
            }
            $status = $this->model->delete( array( 'id' => $id ) );

            //delete media meta (view) from wp_rt_rtm_media_meta
            $delete_rtmedia_views = delete_rtmedia_meta( $id, 'view' );

            if( $status != 0 && ( $media[ 0 ]->media_type == "album" || $media[ 0 ]->media_type == "playlist" ) ) {
                $status = wp_delete_post( $media[ 0 ]->media_id );
            }
            $rtMediaNav = new RTMediaNav();
            if( $media[ 0 ]->context == "group" ) {
                $rtMediaNav->refresh_counts( $media[ 0 ]->context_id, array(
                    "context" => $media[ 0 ]->context, 'context_id' => $media[ 0 ]->context_id
                ) );
            } else {
                $rtMediaNav->refresh_counts( $media[ 0 ]->media_author, array(
                    "context" => "profile", 'media_author' => $media[ 0 ]->media_author
                ) );
            }
        }

        if( !$status ) {
            return false;
        } else {
            global $rtmedia_points_media_id;
            $rtmedia_points_media_id = $id;
            do_action( 'rtmedia_after_delete_media', $id );

            return true;
        }
    }

    /**
     * Move a media from one album to another
     *
     * @global type $wpdb
     *
     * @param type  $media_id
     * @param type  $album_id
     *
     * @return boolean
     */
    function move( $media_id, $album_id ) {

        global $wpdb;
        /* update the post_parent value in wp_post table */
        $status = $wpdb->update( $wpdb->posts, array( 'post_parent' => $album_id ), array( 'ID' => $media_id ) );

        if( is_wp_error( $status ) || $status == 0 ) {
            return false;
        } else {
            $id = rtmedia_id( $media_id );
            /* update album_id, context, context_id and privacy in rtMedia context */
            $album_data = $this->model->get( array( 'media_id' => $media_id ) );
            $data = array(
                'album_id' => $album_id,
                'context' => $album_data->context,
                'context_id' => $album_data->context_id,
                'privacy' => $album_data->privacy
            );

            return $this->update( $id, $data, $media_id );
        }
    }

    /**
     *  Imports attachment as media
     */
    function import_attachment() {

    }

    /**
     * Check if BuddyPress and the activity component are enabled
     *
     * @return boolean
     */
    function activity_enabled() {

        if( !function_exists( 'bp_is_active' ) || !bp_is_active( 'activity' ) ) {
            return false;
        }

        global $rtmedia;

        return $rtmedia->options[ 'buddypress_enableOnActivity' ];
    }

    /**
     *
     * @param type $uploaded
     * @param type $file_object
     *
     * @return type
     */
    function generate_post_array( $uploaded, $file_object ) {
        if( $uploaded[ 'album_id' ] ) {
            $model = new RTMediaModel();
            $parent_details = $model->get( array( 'id' => $uploaded[ 'album_id' ] ) );
            if( is_array( $parent_details ) && sizeof( $parent_details ) > 0 ) {
                $album_id = $parent_details[ 0 ]->media_id;
            } else {
                $album_id = 0;
            }
        } else {
            $album_id = 0;
        }

        if( !in_array( $uploaded[ "context" ], array( "profile", "group" ) ) ) {
            $album_id = $uploaded[ "context_id" ];
        }

        $attachments = array();

        foreach( $file_object as $index => $file ) {
            $uploaded[ 'title' ] = wp_kses( $uploaded[ 'title' ], wp_kses_allowed_html() );
            $uploaded[ 'description' ] = sanitize_text_field( $uploaded[ 'description' ] );
            $attachments[ $index ] = array(
                'post_mime_type' => $file[ 'type' ],
                'guid' => $file[ 'url' ],
                'post_title' => $uploaded[ 'title' ] ? $uploaded[ 'title' ] : preg_replace( "/\\.[^.\\s]{3,4}$/", "", $file[ 'name' ] ),
                'post_content' => $uploaded[ 'description' ] ? $uploaded[ 'description' ] : '',
                'post_parent' => $album_id,
                'post_author' => $uploaded[ 'media_author' ]
            );

            if( !empty( $uploaded[ 'date' ] ) ) {
                $attachments[ $index ][ 'post_date' ] = $uploaded[ 'date' ];
            }
        }

        return $attachments;
    }

    /**
     *
     * @param type $attachments
     * @param type $file_object
     *
     * @return array $updated_attachment_ids
     * @throws Exception
     */
    function insert_attachment( $attachments, $file_object ) {
        $updated_attachment_ids = array();
        foreach( $attachments as $key => $attachment ) {
            $attachment_id = wp_insert_attachment( $attachment, $file_object[ $key ][ 'file' ], $attachment[ 'post_parent' ] );
            if( !is_wp_error( $attachment_id ) ) {
                add_filter( 'intermediate_image_sizes', array( $this, 'image_sizes' ), 99 );
                /**
                 * FIX WORDPRESS 3.6 METADATA
                 */
                require_once( ABSPATH . 'wp-admin/includes/media.php' );
                /**
                 *
                 */
                wp_update_attachment_metadata( $attachment_id, wp_generate_attachment_metadata( $attachment_id, $file_object[ $key ][ 'file' ] ) );
            } else {
                unlink( $file_object[ $key ][ 'file' ] );
                throw new Exception( __( 'Error creating attachment for the media file, please try again', 'buddypress-media' ) );
            }
            $updated_attachment_ids[] = $attachment_id;
        }

        return $updated_attachment_ids;
    }

    /**
     *
     * @param type $sizes
     *
     * @return type
     */
    function image_sizes( $sizes ) {
        return array( 'rt_media_thumbnail', 'rt_media_activity_image', 'rt_media_single_image', 'rt_media_featured_image' );
    }

    /**
     *
     * @param type $attributes
     */
    function insert_album( $attributes ) {

        return $this->model->insert( $attributes );
    }

    function set_media_type( $mime_type, $file_object ) {
        switch( $mime_type ) {
            case 'image':
                return 'photo';
                break;
            case 'audio':
                return 'music';
                break;
            case 'video':
                return 'video';
                break;
            default:
                return apply_filters( 'rtmedia_set_media_type_filter', $mime_type, $file_object );
                break;
        }
    }

    /**
     *
     * @param array $attachment_ids
     * @param array $uploaded
     */
    function insertmedia( $attachment_ids, $uploaded, $file_object /* added for file extension */ ) {

        $defaults = array(
            'activity_id' => $this->activity_enabled(),
            'privacy' => 0
        );

        $uploaded = wp_parse_args( $uploaded, $defaults );

        $blog_id = get_current_blog_id();
        $media_id = Array();
        foreach( $attachment_ids as $id ) {
            $attachment = get_post( $id, ARRAY_A );
            $mime_type = explode( '/', $attachment[ 'post_mime_type' ] );

            $media = array(
                'blog_id' => $blog_id,
                'media_id' => $id,
                'album_id' => $uploaded[ 'album_id' ],
                'media_author' => $attachment[ 'post_author' ],
                'media_title' => $attachment[ 'post_title' ],
                'media_type' => $this->set_media_type( $mime_type[ 0 ], $file_object /* added for file extension */ ),
                'context' => $uploaded[ 'context' ],
                'context_id' => $uploaded[ 'context_id' ],
                'privacy' => $uploaded[ 'privacy' ]
            );
            if( isset( $file_object ) && isset( $file_object[ 0 ] ) && isset( $file_object[ 0 ][ 'file' ] ) ) {
                $media[ 'file_size' ] = ( isset( $file_object[ 0 ][ 'file_size' ] ) ) ? $file_object[ 0 ][ 'file_size' ] : filesize( $file_object[ 0 ][ 'file' ] );
            }
            $media[ 'upload_date' ] = $attachment[ 'post_date' ];
            $media_id[] = $this->model->insert( $media );
        }

        return $media_id;
    }

    function insert_activity( $id, $media ) {
        if( !$this->activity_enabled() ) {
            return false;
        }
        $activity = new RTMediaActivity( $media->id, $media->privacy );
        $activity_content = $activity->create_activity_html();
        $user = get_userdata( $media->media_author );
        $username = '<a href="' . get_rtmedia_user_link( $media->media_author ) . '">' . $user->user_nicename . '</a>';
        $count = count( $id );
        $media_const = 'RTMEDIA_' . strtoupper( $media->media_type );
        if( $count > 1 ) {
            $media_const .= '_PLURAL';
        }
        $media_const .= '_LABEL';

        $media_str = constant( $media_const );

        $action = sprintf( ( $count == 1 ) ? __( '%1$s added a %2$s', 'buddypress-media' ) : __( '%1$s added %4$d %3$s', 'buddypress-media' ), $username, $media->media_type, $media_str, $count );
        $action = apply_filters( 'rtmedia_buddypress_action_text_fitler', $action, $username, $count, $user->user_nicename, $media->media_type );
        $activity_args = array(
            'user_id' => $user->ID,
            'action' => $action,
            'content' => $activity_content,
            'type' => 'rtmedia_update',
            'primary_link' => '',
            'item_id' => $id
        );

        if( $media->context == "group" && function_exists( "bp_get_group_status" ) && bp_get_group_status( groups_get_group( array( "group_id" => $media->context_id ) ) ) != "public" ) {
            $activity_args[ "hide_sitewide" ] = true;
        }

        if( $media->context == 'group' || 'profile' ) {
            $activity_args[ 'component' ] = $media->context;
            if( $media->context == 'group' ) {
                $activity_args[ 'component' ] = "groups";
                $activity_args[ 'item_id' ] = $media->context_id;
            }
        }

        $activity_id = bp_activity_add( $activity_args );
        bp_activity_update_meta( $activity_id, 'rtmedia_privacy', ( $media->privacy == 0 ) ? - 1 : $media->privacy  );

        $this->model->update( array( 'activity_id' => $activity_id ), array( 'id' => $media->id ) );

        // insert/update activity details in rtmedia activity table
        $rtmedia_activity_model = new RTMediaActivityModel();
        if( !$rtmedia_activity_model->check( $activity_id ) ) {
            $rtmedia_activity_model->insert( array( 'activity_id' => $activity_id, 'user_id' => $media->media_author, 'privacy' => $media->privacy ) );
        } else {
            $rtmedia_activity_model->update( array( 'activity_id' => $activity_id, 'user_id' => $media->media_author, 'privacy' => $media->privacy ), array( 'activity_id' => $activity_id ) );
        }

        return $activity_id;
    }

}

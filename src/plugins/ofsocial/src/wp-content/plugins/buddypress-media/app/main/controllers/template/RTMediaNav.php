<?php

class RTMediaNav {

    function __construct ( $action = true ) {
        if ( $action === false ) {
            return;
        }
        add_action ( 'admin_bar_menu', array( $this, 'admin_nav' ), 99 );

        if ( class_exists ( 'BuddyPress' ) ) {
            add_action ( 'bp_init', array( $this, 'custom_media_nav_tab' ), 10, 1 );
        }
        
        add_filter( 'bp_settings_admin_nav', array( $this, 'setup_settings_privacy_nav' ), 3 );
    }

    function media_screen () {
        return;
    }
    
    public function setup_settings_privacy_nav( $wp_admin_nav ) {
        // Setup the logged in user variables

        if( is_rtmedia_privacy_user_overide() ) {
            $settings_link = trailingslashit( bp_loggedin_user_domain() . bp_get_settings_slug() );

            // Add the "Profile" subnav item
            $wp_admin_nav[] = array(
                'parent' => 'my-account-' . buddypress()->settings->id,
                'id' => 'my-account-' . buddypress()->settings->id . '-privacy',
                'title' => _x( 'Privacy', 'My Account Privacy sub nav', 'buddypress-media' ),
                'href' => trailingslashit( $settings_link . 'privacy' )
            );
        }
        
        return $wp_admin_nav;
    }

    /**
     * Load Custom tabs on BuddyPress
     *
     * @global object $bp global BuddyPress object
     */
    function custom_media_nav_tab () {
        global $bp;
        if ( ! function_exists ( "bp_core_new_nav_item" ) )
            return;
        if ( bp_is_blog_page () || ( ! bp_is_group () && ! ( isset ( $bp->displayed_user ) && isset ( $bp->displayed_user->id )) ) || apply_filters( 'rtmedia_render_bp_nav', false ) )
            return;
        global $rtmedia;
        if ( function_exists ( "bp_is_group" ) && ! bp_is_group () ) {
            if ( isset ( $bp->displayed_user ) && isset ( $bp->displayed_user->id ) ) {
                $profile_counts = $this->actual_counts ( $bp->displayed_user->id );
            }
	    $tab_position = apply_filters('rtmedia_media_tab_position',99);
            if ( $rtmedia->options[ "buddypress_enableOnProfile" ] != 0 ) {
                bp_core_new_nav_item ( array(
                    'name' => RTMEDIA_MEDIA_LABEL . '<span>' . $profile_counts[ 'total' ][ 'all' ] . '</span>',
                    'slug' => apply_filters('rtmedia_media_tab_slug', RTMEDIA_MEDIA_SLUG ),
                    'screen_function' => array( $this, 'media_screen' ),
                    'default_subnav_slug' => 'all',
		    'position' => $tab_position
                ) );
            }
        }
        if ( bp_is_group () && $rtmedia->options[ "buddypress_enableOnGroup" ] != 0 ) {
            global $bp;
            $media_enabled = true;
            //filter for rtMedia PRO for PER GROUP MEDIA enable/disable functionality
            $media_enabled = apply_filters('rtmedia_media_enabled_for_current_group', $media_enabled);

			// check if current user can view this group
			$current_group      = groups_get_current_group();
			$is_visible_to_current_user = $current_group->is_visible;

			if( $media_enabled && $is_visible_to_current_user ){
                $group_counts = $this->actual_counts ( $bp->groups->current_group->id, "group" );
				$slug = apply_filters('rtmedia_group_media_tab_slug', RTMEDIA_MEDIA_SLUG );
                $bp->bp_options_nav[ bp_get_current_group_slug () ][ 'media' ] = array(
                    'name' => RTMEDIA_MEDIA_LABEL . '<span>' . $group_counts[ 'total' ][ 'all' ] . '</span>',
                    'link' => trailingslashit ( bp_get_root_domain () . '/' . bp_get_groups_root_slug () . '/' . bp_get_current_group_slug () . '/' ) . $slug,
                    'slug' => $slug,
                    'user_has_access' => true,
                    'css_id' => 'rtmedia-media-nav',
                    'position' => 99,
                    'screen_function' => array( $this, 'media_screen' ),
                    'default_subnav_slug' => 'all'
                );
            }
        }
    }

    function admin_nav () {
        global $wp_admin_bar;
	global $rtmedia;
	if( ( ! isset( $rtmedia->options['buddypress_enableOnProfile'] ) ) || ( isset( $rtmedia->options['buddypress_enableOnProfile'] ) && $rtmedia->options['buddypress_enableOnProfile'] == '0' ) ) {
	    return;
	}
        if ( function_exists ( "bp_use_wp_admin_bar" ) && ! bp_use_wp_admin_bar () )
            return;

        // Bail if this is an ajax request
        if ( defined ( 'DOING_AJAX' ) )
            return;
        // Only add menu for logged in user
        if ( is_user_logged_in () ) {
            // Add secondary parent item for all BuddyPress components
            $wp_admin_bar->add_menu ( array(
                'parent' => 'my-account',
                'id' => 'my-account-' . RTMEDIA_MEDIA_SLUG,
                'title' => RTMEDIA_MEDIA_LABEL,
                'href' => trailingslashit ( get_rtmedia_user_link ( get_current_user_id () ) ) . RTMEDIA_MEDIA_SLUG . '/'
            ) );

            if ( is_rtmedia_album_enable () ) {
                $wp_admin_bar->add_menu ( array(
                    'parent' => 'my-account-' . RTMEDIA_MEDIA_SLUG,
                    'id' => 'my-account-media-' . RTMEDIA_ALBUM_SLUG,
                    'title' => RTMEDIA_ALBUM_PLURAL_LABEL,
                    'href' => trailingslashit ( get_rtmedia_user_link ( get_current_user_id () ) ) . RTMEDIA_MEDIA_SLUG . '/album/'
                ) );
            }

            global $rtmedia;

            foreach ( $rtmedia->allowed_types as $type ) {
		if( isset( $rtmedia->options[ 'allowedTypes_' . $type[ 'name' ] . '_enabled' ] ) ) {
		    if ( ! $rtmedia->options[ 'allowedTypes_' . $type[ 'name' ] . '_enabled' ] )
			continue;
		    $name = strtoupper ( $type[ 'name' ] );
		    $wp_admin_bar->add_menu ( array(
			'parent' => 'my-account-' . constant ( 'RTMEDIA_MEDIA_SLUG' ),
			'id' => 'my-account-media-' . constant ( 'RTMEDIA_' . $name . '_SLUG' ),
			'title' => $type[ 'plural_label' ],
			'href' => trailingslashit ( get_rtmedia_user_link ( get_current_user_id () ) ) . RTMEDIA_MEDIA_SLUG . '/' . constant ( 'RTMEDIA_' . $name . '_SLUG' ) . '/'
		    ) );
		}
            }
            do_action( 'rtmedia_add_admin_bar_media_sub_menu', 'my-account-' . RTMEDIA_MEDIA_SLUG  );
        }
    }

    public function sub_nav () {
		global $rtmedia, $rtmedia_query;
		if ( function_exists ( 'bp_is_group' ) && bp_is_group () ) {
			if( isset( $rtmedia->options['buddypress_enableOnGroup'] ) && $rtmedia->options['buddypress_enableOnGroup'] == '0' ) {
				return;
			}
			global $bp;
			$counts = $this->actual_counts ( $bp->groups->current_group->id, "group" );
		} else {
			if( class_exists( 'BuddyPress' ) && isset( $rtmedia->options['buddypress_enableOnProfile'] ) && $rtmedia->options['buddypress_enableOnProfile'] == '0' ) {
				return;
			}
			$counts = $this->actual_counts ();
		}

        $default = false;
	if ( function_exists ( 'bp_is_group' ) && bp_is_group () ) {
	    $link = get_rtmedia_group_link ( bp_get_group_id () );
	    $model = new RTMediaModel();
	    $other_count = $model->get_other_album_count ( bp_get_group_id (), "group" );
	} else {
	    if ( function_exists ( 'bp_displayed_user_id' ) && bp_displayed_user_id () ) {
		$link = get_rtmedia_user_link ( bp_displayed_user_id () );
	    } elseif ( get_query_var ( 'author' ) ) {
		$link = get_rtmedia_user_link ( get_query_var ( 'author' ) );
	    }
	    $model = new RTMediaModel();
	    $other_count = $model->get_other_album_count ( bp_displayed_user_id (), "profile" );
	}
	$all = '';
	if ( ! isset ( $rtmedia_query->action_query->media_type )) {
	    $all = 'class="current selected"';
	}
	echo apply_filters ( 'rtmedia_sub_nav_all', '<li id="rtmedia-nav-item-all-li" ' . $all . '><a id="rtmedia-nav-item-all" href="' . trailingslashit ( $link ) . RTMEDIA_MEDIA_SLUG . '/">' . __ ( "All", 'buddypress-media' ) . '<span>' . ((isset ( $counts[ 'total' ][ 'all' ] )) ? $counts[ 'total' ][ 'all' ] : 0 ) . '</span>' . '</a></li>' );

        if ( ! isset ( $rtmedia_query->action_query->action ) || empty ( $rtmedia_query->action_query->action ) ) {
            $default = true;
        }
        //print_r($rtmedia_query->action_query);

        $global_album = '';
        $albums = '';
        if ( isset ( $rtmedia_query->action_query->media_type ) && $rtmedia_query->action_query->media_type == 'album' ) {
	    $albums = 'class="current selected"';
	}

        //$other_count = 0;
        if ( is_rtmedia_album_enable () ) {

            if ( ! isset ( $counts[ 'total' ][ "album" ] ) ) {
                $counts[ 'total' ][ "album" ] = 0;
            }

            $counts[ 'total' ][ "album" ] = $counts[ 'total' ][ "album" ] + $other_count;
	    $album_label = __( defined('RTMEDIA_ALBUM_PLURAL_LABEL') ? constant ( 'RTMEDIA_ALBUM_PLURAL_LABEL' ) : 'Albums', 'buddypress-media' );
            echo apply_filters ( 'rtmedia_sub_nav_albums', '<li id="rtmedia-nav-item-albums-li" ' . $albums . '><a id="rtmedia-nav-item-albums" href="' . trailingslashit ( $link ) . RTMEDIA_MEDIA_SLUG . '/album/">' . $album_label . '<span>' . ((isset ( $counts[ 'total' ][ "album" ] )) ? $counts[ 'total' ][ "album" ] : 0 ) . '</span>' . '</a></li>' );
        }

        foreach ( $rtmedia->allowed_types as $type ) {
            //print_r($type);
	    if( ! isset( $rtmedia->options[ 'allowedTypes_' . $type[ 'name' ] . '_enabled' ] ) )
		continue;
            if ( ! $rtmedia->options[ 'allowedTypes_' . $type[ 'name' ] . '_enabled' ] )
                continue;

            $selected = '';

            if ( isset ( $rtmedia_query->action_query->media_type ) && $type[ 'name' ] == $rtmedia_query->action_query->media_type ) {
                $selected = ' class="current selected"';
            } else {
                $selected = '';
            }

            $context = isset ( $rtmedia_query->query[ 'context' ] ) ? $rtmedia_query->query[ 'context' ] : 'default';
            $context_id = isset ( $rtmedia_query->query[ 'context_id' ] ) ? $rtmedia_query->query[ 'context_id' ] : 0;
            $name = strtoupper ( $type[ 'name' ] );
            $is_group = false;
            $profile = self::profile_id ();
            if ( ! $profile ) {
                $profile = self::group_id ();
                $is_group = true;
            }


            if ( ! $is_group ) {
                $profile_link = trailingslashit (
                        get_rtmedia_user_link (
                                $profile
                        )
                );
            } else {
                $profile_link = trailingslashit (
                        get_rtmedia_group_link (
                                $profile
                        )
                );
            }

	    $type_label = __( defined('RTMEDIA_' . $name . '_PLURAL_LABEL') ? constant ( 'RTMEDIA_' . $name . '_PLURAL_LABEL' ) : $type[ 'plural_label' ], 'buddypress-media' );
            echo apply_filters ( 'rtmedia_sub_nav_' . $type[ 'name' ], '<li id="rtmedia-nav-item-' . $type[ 'name' ]
                    . '-' . $context . '-' . $context_id . '-li" ' . $selected
                    . '><a id="rtmedia-nav-item-' . $type[ 'name' ] . '" href="'
                    . $profile_link . RTMEDIA_MEDIA_SLUG . '/'
                    . constant ( 'RTMEDIA_' . $name . '_SLUG' ) . '/' . '">'
                    . $type_label . '<span>' . ((isset ( $counts[ 'total' ][ $type[ 'name' ] ] )) ? $counts[ 'total' ][ $type[ 'name' ] ] : 0) . '</span>' . '</a></li>', $type[ 'name' ]
            );
        }

        do_action("add_extra_sub_nav");
    }

    function refresh_counts ( $user_id, $where ) {
        $model = new RTMediaModel();
        $counts = $model->get_counts ( $user_id, $where );
        $media_count = array( );
        foreach ( $counts as $count ) {
            if ( ! isset ( $count->privacy ) ) {
                $count->privacy = 0;
            }
            if ( isset ( $media_count[ strval ( $count->privacy ) ] ) ) {
                foreach ( $media_count[ strval ( $count->privacy ) ] as $key => $val ) {
                    $media_count[ strval ( $count->privacy ) ]->{$key} = intval ( $count->{$key} ) + intval ( $val );
                }
            }
            else
                $media_count[ strval ( $count->privacy ) ] = $count;
            unset ( $media_count[ strval ( $count->privacy ) ]->privacy );
        }

        if ( isset ( $where[ "context" ] ) ) {
            if ( $where[ "context" ] == "profile" ) {
                update_user_meta ( $user_id, 'rtmedia_counts_' . get_current_blog_id(), $media_count );
            } else if ( $where[ "context" ] == "group" && function_exists ( "groups_update_groupmeta" ) ) {
                groups_update_groupmeta ( $user_id, 'rtmedia_counts_' . get_current_blog_id(), $media_count );
            }
        }
        return $media_count;
    }

    function get_counts ( $profile_id = false, $context = "profile" ) {
        if ( $profile_id === false && $context = "profile" )
            $profile_id = $this->profile_id ();
        else if ( $profile_id === false && $context = "profile" )
            $profile_id = $this->group_id ();
        if ( ! $profile_id )
            return false;
        if ( $context == "profile" ) {
            $counts = get_user_meta ( $profile_id, 'rtmedia_counts_' . get_current_blog_id(), true );
            if ( $counts == false || empty ( $counts ) ) {
                $counts = $this->refresh_counts ( $profile_id, array( "context" => $context, 'media_author' => $profile_id ) );
            }
        } else if ( function_exists ( "groups_get_groupmeta" ) && $context = "group" ) {
            $counts = groups_get_groupmeta ( $profile_id, 'rtmedia_counts_' . get_current_blog_id() );
            if ( $counts === false || empty ( $counts ) ) {
                $counts = $this->refresh_counts ( $profile_id, array( "context" => $context, 'context_id' => $profile_id ) );
            }
        }
        return $counts;
    }

    function profile_id () {
        global $rtmedia_query;
        if ( isset ( $rtmedia_query->query[ 'context' ] ) && ($rtmedia_query->query[ 'context' ] == 'profile') ) {
            return $rtmedia_query->query[ 'context_id' ];
        }
        return false;
    }

    function group_id () {
        global $rtmedia_query;
        if ( isset ( $rtmedia_query->query[ 'context' ] ) && ($rtmedia_query->query[ 'context' ] == 'group') ) {
            return $rtmedia_query->query[ 'context_id' ];
        }
    }

    function actual_counts ( $profile_id = false, $context = "profile" ) {
        if ( $profile_id === false ) {
            if ( ! $this->profile_id () )
                return;
        }

        $media_count = $this->get_counts ( $profile_id, $context );
        $privacy = $this->set_privacy ( $profile_id );
        return $this->process_count ( $media_count, $privacy );
    }

    function process_count ( $media_count, $privacy ) {
        $total = array( 'all' => 0 );
        $media_count = !empty( $media_count ) ? $media_count : array();
		$exclude_type_count = apply_filters( 'rtmedia_media_count_exclude_type', array( 'album' ) );
        foreach ( $media_count as $private => $ind_count ) {
            if ( $private <= $privacy ) {
                foreach ( $ind_count as $type => $ind_ind_count ) {
                    if ( in_array( $type, $exclude_type_count ) ) {
						// do nothing
                    } else {
						$total[ 'all' ]+= ( int ) $ind_ind_count;
					}
                    if ( ! isset ( $total[ $type ] ) )
                        $total[ $type ] = 0;
                    $total[ $type ]+=( int ) $ind_ind_count;
                }
            } else {
                unset ( $media_count[ $private ] );
            }
        }

        $media_count[ 'total' ] = $total;
        return $media_count;
    }

    function visitor_id () {
        if ( is_user_logged_in () ) {
            $user = get_current_user_id ();
        } else {
            $user = 0;
        }
        return $user;
    }

    function set_privacy ( $profile ) {
        if( is_rt_admin () )
            return 60;

        $user = $this->visitor_id ();
        $privacy = 0;
        if ( $user ) {
            $privacy = 20;
        }
        if ( $profile === false )
            $profile = $this->profile_id ();
        if ( class_exists ( 'BuddyPress' ) && bp_is_active ( 'friends' ) ) {

            if ( friends_check_friendship_status ( $user, $profile ) ) {
                $privacy = 40;
            }
        }
        if ( $user === $profile ) {
            $privacy = 60;
        }

        return $privacy;
    }

}
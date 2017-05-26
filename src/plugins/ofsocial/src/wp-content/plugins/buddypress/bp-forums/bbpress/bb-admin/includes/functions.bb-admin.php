<?php

function bb_get_admin_header()
{
	do_action( 'bb_admin-header.php' );
	include( 'admin-header.php' );
	do_action( 'bb_get_admin_header' );
}

function bb_get_admin_footer()
{
	do_action( 'bb_admin-footer.php' );
	include( 'admin-footer.php' );
}

function bb_admin_notice( $message, $class = false )
{
	if ( is_string( $message ) ) {
		$message = '<p>' . $message . '</p>';
		$class = $class ? $class : 'updated';
	} elseif ( is_wp_error( $message ) ) {
		$errors = $message->get_error_messages();
		switch ( count( $errors ) ) {
			case 0:
				return false;
				break;
			case 1:
				$message = '<p>' . $errors[0] . '</p>';
				break;
			default:
				$message = '<ul>' . "\n\t" . '<li>' . join( '</li>' . "\n\t" . '<li>', $errors ) . '</li>' . "\n" . '</ul>';
				break;
		}
		$class = $class ? $class : 'error';
	} else {
		return false;
	}

	$message = '<div id="message" class="' . esc_attr( $class ) . '">' . $message . '</div>';
	$message = str_replace( "'", "\'", $message );
	$lambda = create_function( '', "echo '$message';" );
	add_action( 'bb_admin_notices', $lambda );
	return $lambda;
}

/* Menu */

function bb_admin_menu_generator()
{
	global $bb_menu, $bb_submenu;
	$bb_menu = array();
	$bb_submenu = array();

	// Dashboard menu items < 50
	$bb_menu[0]  = array( __( 'Dashboard' ), 'moderate', 'index.php', '', 'bb-menu-dashboard' );
		$bb_submenu['index.php'][5]   = array( __( 'Dashboard' ), 'moderate', 'index.php' );

	// 50 < Plugin added menu items < 100

	$bb_menu[100] = array( '', 'read', 'separator' );

	// 100 < Plugin added menu items < 150

	// 150 < First menu items < 200
	$bb_menu[150] = array( __( 'Forums' ), 'manage_forums', 'forums.php', '', 'bb-menu-forums' );
		$bb_submenu['forums.php'][5]   = array( __( 'Forums' ), 'manage_forums', 'forums.php' );
	$bb_menu[155] = array( __( 'Topics' ), 'moderate', 'topics.php', '', 'bb-menu-topics' );
		$bb_submenu['topics.php'][5]   = array( __( 'Topics' ), 'moderate', 'topics.php' );
	$bb_menu[160] = array( __( 'Posts' ), 'moderate', 'posts.php', '', 'bb-menu-posts' );
		$bb_submenu['posts.php'][5]   = array( __( 'Posts' ), 'moderate', 'posts.php' );

	// 200 < Plugin added menu items < 250

	$bb_menu[250] = array( '', 'read', 'separator' );

	// 250 < Plugin added menu items < 300

	// 300 < Second menu items < 350
	$bb_menu[300] = array( __( 'Appearance' ), 'manage_themes', 'themes.php', '', 'bb-menu-appearance' );
		$bb_submenu['themes.php'][5]   = array(__('Themes'), 'manage_themes', 'themes.php');
	$bb_menu[305] = array( __( 'Plugins' ), 'use_keys', 'plugins.php', '', 'bb-menu-plugins' );
		$bb_submenu['plugins.php'][5]  = array( __( 'Installed' ), 'manage_plugins', 'plugins.php' );
	$bb_menu[310] = array( __( 'Users' ), 'moderate', 'users.php', '', 'bb-menu-users' );
		$bb_submenu['users.php'][5]  = array( __( 'Users' ), 'moderate', 'users.php' );
		$bb_submenu['users.php'][10]  = array( __( 'Add New' ), 'manage_options', 'user-add-new.php' );
	$bb_menu[315] = array( __( 'Tools' ), 'recount', 'tools-recount.php', '', 'bb-menu-tools' );
		$bb_submenu['tools-recount.php'][5] = array( __( 'Re-count' ), 'recount', 'tools-recount.php' );
	$bb_menu[320] = array( __( 'Settings' ), 'manage_options', 'options-general.php', '', 'bb-menu-settings' );
		$bb_submenu['options-general.php'][5]  = array( __( 'General' ), 'manage_options', 'options-general.php' );
		//$bb_submenu['options-general.php'][10] = array( __( 'Date and Time' ), 'manage_options', 'options-time.php' );
		$bb_submenu['options-general.php'][15] = array( __( 'Writing' ), 'manage_options', 'options-writing.php' );
		$bb_submenu['options-general.php'][20] = array( __( 'Reading' ), 'manage_options', 'options-reading.php' );
		$bb_submenu['options-general.php'][25] = array( __( 'Discussion' ), 'manage_options', 'options-discussion.php' );
		$bb_submenu['options-general.php'][30] = array( __( 'Permalinks' ), 'manage_options', 'options-permalinks.php' );
		$bb_submenu['options-general.php'][35] = array( __( 'WordPress Integration' ), 'manage_options', 'options-wordpress.php' );

	// 350 < Plugin added menu items

	do_action( 'bb_admin_menu_generator' );
	ksort( $bb_menu );

	$last_key = false;
	foreach ( $bb_menu as $key => $m ) {
		if ( $last_key === false || $bb_menu[$last_key][2] === 'separator' ) {
			$bb_menu[$key][3] .= ' bb-menu-first';
		}
		if ( $bb_menu[$key][2] === 'separator' ) {
			$bb_menu[$last_key][3] .= ' bb-menu-last';
		}
		$last_key = $key;
		if ( isset( $bb_submenu[$m[2]] ) ) {
			ksort( $bb_submenu[$m[2]] );
		}
	}
	$bb_menu[$last_key][3] .= ' bb-menu-last';
}

function bb_admin_add_menu( $display_name, $capability, $file_name, $menu_position = false, $class = '', $id = '' )
{
	global $bb_menu;
	global $bb_registered_plugin_callbacks;
	if ( empty( $bb_registered_plugin_callbacks ) ) {
		$bb_registered_plugin_callbacks = array();
	}

	if ( $display_name && $capability && $file_name ) {
		// Get an array of the keys
		$menu_keys = array_keys( $bb_menu );

		if ( $menu_position ) {
			if ( is_numeric( $menu_position ) ) {
				if ( !isset( $bb_menu[$menu_position] ) ) {
					$plugin_menu_next = $menu_position;
				} else {
					return bb_admin_add_menu( $display_name, $capability, $file_name, ( $menu_position + 1 ), $class, $id );
				}
			} else {
				// Set the bounds for different menu groups (main or side)
				switch ( $menu_position ) {
					case 'dash':
						$lower = 50;
						$upper = 100;
						break;
					case 'main':
						$lower = 200;
						$upper = 250;
						break;
					default:
						$lower = 350;
						$upper = 500;
						break;
				}

				// Get an array of all plugin added keys
				$plugin_menu_keys = array_filter( $menu_keys, create_function( '$v', 'if ($v >= ' . $lower . ' && $v < ' . $upper . ') { return $v; }' ) );

				// If there is an array of keys
				if ( is_array( $plugin_menu_keys ) && count( $plugin_menu_keys ) ) {
					// Get the highest key value and add one
					$plugin_menu_next = max( $plugin_menu_keys ) + 1;
				} else {
					// It's the first one
					$plugin_menu_next = $lower;
				}
			}
		} else {
			$plugin_menu_next = max( array_keys( $bb_menu ) ) + 1;
			$bb_menu[$plugin_menu_next] = array( '', 'read', 'separator' );
			$plugin_menu_next++;
		}

		if ( strpos( $file_name, '.php' ) === false ) {
			$bb_registered_plugin_callbacks[] = $file_name;
		}

		// Add the menu item at the given key
		$bb_menu[$plugin_menu_next] = array( $display_name, $capability, $file_name, $class, $id );

		ksort( $bb_menu );

		return $plugin_menu_next;
	}

	return false;
}

function bb_admin_add_submenu( $display_name, $capability, $file_name, $parent = 'plugins.php' )
{
	global $bb_submenu;
	global $bb_registered_plugin_callbacks;
	if ( empty( $bb_registered_plugin_callbacks ) ) {
		$bb_registered_plugin_callbacks = array();
	}

	if ( $display_name && $capability && $file_name ) {
		if ( strpos( $file_name, '.php' ) === false ) {
			$bb_registered_plugin_callbacks[] = $file_name;
		}
		$bb_submenu[$parent][] = array( $display_name, $capability, $file_name );
		ksort( $bb_submenu );
	}
}

function bb_get_current_admin_menu()
{
	global $bb_menu, $bb_submenu, $bb_admin_page, $bb_current_menu, $bb_current_submenu;
	foreach ( $bb_submenu as $m => $b ) {
		foreach ( $b as $s ) {
			if ( $s[2] == $bb_admin_page ) {
				$bb_current_submenu = $s;
				$bb_current_menu = $m;
				break;
			}
		}
	}
	if ( !isset($bb_current_menu) ) {
		$bb_current_menu = $bb_menu[0];
		$bb_current_submenu = $bb_submenu['index.php'][5];
	} else {
		foreach ( $bb_menu as $m ) {
			if ( $m[2] == $bb_current_menu ) {
				$bb_current_menu = $m;
				break;
			}
		}
	}
	if ( $bb_current_submenu && !bb_current_user_can( $bb_current_submenu[1] ) || !bb_current_user_can( $bb_current_menu[1] ) ) {
		wp_redirect( bb_get_uri(null, null, BB_URI_CONTEXT_HEADER) );
		exit;
	}
}

function bb_admin_title()
{
	global $bb_current_menu, $bb_current_submenu;

	$title = $bb_current_menu[0] . ' &lsaquo; ' . bb_get_option( 'name' ) . ' &#8212; ' . __( 'bbPress' );

	if ( $bb_current_submenu && $bb_current_submenu[0] !== $bb_current_menu[0] ) {
		$title = $bb_current_submenu[0] . ' &lsaquo; ' . $title;
	}

	echo esc_html( $title );
}

function bb_admin_menu()
{
	global $bb_menu, $bb_submenu, $bb_current_menu, $bb_current_submenu;

	if ( !is_array( $bb_menu ) || !count( $bb_menu ) ) {
		return '';
	}

	$r = "\t\t\t" . '<ul id="bbAdminMenu">' . "\n";

	foreach ( $bb_menu as $key => $m ) {
		if ( !bb_current_user_can( $m[1] ) ) {
			continue;
		}
		$class = 'bb-menu';
		if ( isset( $m[3] ) ) {
			$class .= ' ' . $m[3];
		}
		$id = '';
		if ( isset( $m[4] ) ) {
			$id .= ' id="' . $m[4] . '"';
		}
		$m[0] = esc_html( $m[0] );
		if ( $m[2] === 'separator' ) {
			if ( 'f' == bb_get_user_setting( 'fm' ) ) {
				$href = '?foldmenu=0';
			} else {
				$href = '?foldmenu=1';
			}
			$m[0] = '<br />';
			$class .= ' bb-menu-separator';
		} elseif ( strpos( $m[2], 'http://' ) === 0 || strpos( $m[2], 'https://' ) === 0 ) {
			$href = esc_url( $m[2] );
			$class .= ' bb-menu-external';
		} else {
			$href = esc_url( bb_get_option( 'path' ) . 'bb-admin/' . bb_get_admin_tab_link( $m[2] ) );
		}
		if ( $m[2] == $bb_current_menu[2] ) {
			$class .= ' bb-menu-current';
		}

		$sr = '';
		if ( $m[2] !== 'separator' && isset( $bb_submenu[$m[2]] ) && is_array( $bb_submenu[$m[2]] ) && count( $bb_submenu[$m[2]] ) ) {
			$sr .= "\t\t\t\t\t" . '<div class="bb-menu-sub-wrap"><span>' . $m[0] . '</span>' . "\n";
			$sr .= "\t\t\t\t\t\t" . '<ul>' . "\n";
			$sc = 0;
			foreach ( $bb_submenu[$m[2]] as $skey => $sm ) {
				if ( !bb_current_user_can( $sm[1] ) ) {
					continue;
				}
				if ( $sc === 0 && $sm[2] === $m[2] ) {
					$no_submenu = true;
				}
				if ( $sc > 0 ) {
					$no_submenu = false;
				}
				$sc++;
				$sclass = 'bb-menu-sub';
				if ( isset( $sm[3] ) ) {
					$sclass .= ' ' . $sm[3];
				}
				if ( strpos( $sm[2], 'http://' ) === 0 || strpos( $sm[2], 'https://' ) === 0 ) {
					$shref = $sm[2];
					$sclass .= ' bb-menu-external';
				} else {
					$shref = bb_get_option( 'path' ) . 'bb-admin/' . bb_get_admin_tab_link( $sm[2] );
				}
				if ( $sm[2] == $bb_current_submenu[2] ) {
					$sclass .= ' bb-menu-sub-current';
				}
				$sr .= "\t\t\t\t\t\t\t" . '<li class="' . esc_attr( trim( $sclass ) ) . '"><a href="' . esc_url( $shref ) . '">' . esc_html( $sm[0] ) . '</a></li>' . "\n";
			}
			$sr .= "\t\t\t\t\t\t" . '</ul>' . "\n";
			$sr .= "\t\t\t\t\t" . '</div>' . "\n";
		}

		if ( $sr && !$no_submenu ) {
			$class .= ' bb-menu-has-submenu';
			if ( $m[2] == $bb_current_menu[2] ) {
				$class .= ' bb-menu-open';
			}
		}

		$r .= "\t\t\t\t" . '<li' . $id . ' class="' . esc_attr( trim( $class ) ) . '"><a href="' . $href . '">';

		if ( $m[2] !== 'separator' ) {
			$r .= '<div class="bb-menu-icon"></div>';
		}

		$r .= '<span>' . $m[0] . '</span></a>' . "\n";

		if ( $sr && !$no_submenu ) {
			$r .= '<div class="bb-menu-toggle"></div>';
			$r .= $sr;
		}

		$r .= "\t\t\t\t" . '</li>' . "\n";
	}

	$r .= "\t\t\t" . '</ul>' . "\n";

	echo $r;
}

function bb_get_admin_tab_link( $tab )
{
	if ( is_array( $tab ) ) {
		$tab = $tab[2];
	}
	if ( strpos( $tab, '.php' ) !== false ) {
		return $tab;
	} else {
		return 'admin-base.php?plugin=' . $tab;
	}
}

/* Stats */

function bb_get_recently_moderated_objects( $num = 5 ) {
	$post_query  = new BB_Query( 'post', array( 'per_page' => $num, 'post_status' => '-normal', 'topic_status' => 0 ) ); // post_time != moderation_time;
	$topic_query = new BB_Query( 'topic', array( 'per_page' => $num, 'topic_status' => '-normal' ) ); // topic_time == topic_start_time != moderation_time;

	$objects = array();
	if ( $post_query->results )
		foreach ( array_keys($post_query->results) as $key )
			$objects[bb_gmtstrtotime($post_query->results[$key]->post_time)] = array('type' => 'post', 'data' => $post_query->results[$key]);
	if ( $topic_query->results )
		foreach ( array_keys($topic_query->results) as $key )
			$objects[bb_gmtstrtotime($topic_query->results[$key]->topic_time)] = array('type' => 'topic', 'data' => $topic_query->results[$key]);
	krsort($objects);
	return array_slice($objects, 0, $num);
}

/* Users */

function bb_manage_user_fields( $edit_user = '' ) {
	global $wp_roles, $wp_users_object, $bbdb;

	// Cap checks
	$user_roles    = $wp_roles->role_names;
	$can_keep_gate = bb_current_user_can( 'keep_gate' );

	if ( 'post' == strtolower( $_SERVER['REQUEST_METHOD'] ) ) {

		bb_check_admin_referer( 'user-manage' );

		// Instantiate required vars
		$_POST                   = stripslashes_deep( $_POST );
		$create_user_errors      = new WP_Error;

		// User login
		$trimmed_user_login      = str_replace( ' ', '', $_POST['user_login'] );
		$user_login              = sanitize_user( $_POST['user_login'], true );
		$user_meta['first_name'] = $_POST['first_name'];
		$user_meta['last_name']  = $_POST['last_name'];
		$user_display_name       = $_POST['display_name'];
		$user_email              = $_POST['user_email'];
		$user_url                = $_POST['user_url'];
		$user_meta['from']       = $_POST['from'];
		$user_meta['occ']        = $_POST['occ'];
		$user_meta['interest']   = $_POST['interest'];
		$user_role               = $_POST['userrole'];
		$user_meta['throttle']   = $_POST['throttle'];
		$user_pass1              = $_POST['pass1'];
		$user_pass2              = $_POST['pass2'];
		$user_status             = 0;
		$user_pass               = false;
		$user_url                = $user_url ? bb_fix_link( $user_url ) : '';

		// Check user_login
		if ( !isset( $_GET['action'] ) && empty( $user_login ) ) {
			$create_user_errors->add( 'user_login', __( 'Username is a required field.' ) );
		} else {
			if ( $user_login !== $trimmed_user_login ) {
				$create_user_errors->add( 'user_login', sprintf( __( '%s is an invalid username. How\'s this one?' ), esc_html( $_POST['user_login'] ) ) );
				$user_login = $trimmed_user_login;
			}
		}

		// Check email
		if ( isset( $user_email ) && empty( $user_email ) )
			$create_user_errors->add( 'user_email', __( 'Email address is a required field.' ) );

		// Password Sanity Check
		if ( ( !empty( $user_pass1 ) || !empty( $user_pass2 ) ) && $user_pass1 !== $user_pass2 )
			$create_user_errors->add( 'pass', __( 'You must enter the same password twice.' ) );
		elseif ( !isset( $_GET['action'] ) && ( empty( $user_pass1 ) && empty( $user_pass2 ) ) )
			$create_user_errors->add( 'pass', __( 'You must enter a password.' ) );
		elseif ( isset( $_GET['action'] ) && ( empty( $user_pass1 ) && empty( $user_pass2 ) ) )
			$user_pass = '';
		else
			$user_pass = $user_pass1;

		// No errors
		if ( !$create_user_errors->get_error_messages() ) {

			// Create or udpate
			switch ( $_POST['action'] ) {
				case 'create' :
					$goback = bb_get_uri( 'bb-admin/users.php', array( 'created' => 'true' ), BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN );
					$user   = $wp_users_object->new_user( compact( 'user_login', 'user_email', 'user_url', 'user_nicename', 'user_status', 'user_pass' ) );

					// Error handler
					if ( is_wp_error( $user ) ) {
						bb_admin_notice( $user );
						unset( $goback );

					// Update additional user data
					} else {
						// Update caps
						bb_update_usermeta( $user['ID'], $bbdb->prefix . 'capabilities', array( $user_role => true ) );

						// Update all user meta
						foreach ( $user_meta as $key => $value )
							bb_update_usermeta( $user['ID'], $key, $value );

						// Don't send email if empty
						if ( !empty( $user_pass ) )
							bb_send_pass( $user['ID'], $user_pass );

						do_action( 'bb_new_user',    $user['ID'], $user_pass );
					}

					break;

				case 'update' :
					$goback = bb_get_uri( 'bb-admin/users.php', array( 'updated' => 'true' ), BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN );
					$user   = $wp_users_object->get_user( $_GET['user_id'], array( 'output' => ARRAY_A ) );
					bb_update_user( $user['ID'], $user_email, $user_url, $user_display_name );

					// Don't change PW if empty
					if ( !empty( $user_pass ) )
						bb_update_user_password( $user['ID'], $user_pass );

					// Error handler
					if ( is_wp_error( $user ) ) {
						bb_admin_notice( $user );
						unset( $goback );

					// Update additional user data
					} else {
						// Update caps
						bb_update_usermeta( $user['ID'], $bbdb->prefix . 'capabilities', array( $user_role => true ) );

						// Update all user meta
						foreach ( $user_meta as $key => $value )
							bb_update_usermeta( $user['ID'], $key, $value );

						// Don't send email if empty
						if ( !empty( $user_pass ) )
							bb_send_pass( $user['ID'], $user_pass );

						do_action( 'bb_update_user', $user['ID'], $user_pass );
					}

					break;
			}

			// Redirect
			if ( isset( $goback ) && !empty( $goback ) )
				bb_safe_redirect( $goback );

		// Error handler
		} else {
			bb_admin_notice( $create_user_errors );
		}
	} elseif ( isset( $_GET['action'] ) && $_GET['action'] == 'edit' ) {
		if ( isset( $_GET['user_id'] ) && is_numeric( $_GET['user_id'] ) ) {
			$disabled = true;

			// Get the user
			if ( empty( $edit_user ) )
				$edit_user = bb_get_user( bb_get_user_id( $_GET['user_id'] ) );

			// Instantiate required vars
			$user_login              = $edit_user->user_login;
			$user_meta['first_name'] = $edit_user->first_name;
			$user_meta['last_name']  = $edit_user->last_name;
			$user_display_name       = $edit_user->display_name;
			$user_email              = $edit_user->user_email;
			$user_url                = $edit_user->user_url;
			$user_meta['from']       = $edit_user->from;
			$user_meta['occ']        = $edit_user->occ;
			$user_meta['interest']   = $edit_user->interest;
			$user_role               = array_search( 'true', $edit_user->capabilities );
			$user_meta['throttle']   = $edit_user->throttle;

			// Keymasters can't demote themselves
			if ( ( $edit_user->ID == bb_get_current_user_info( 'id' ) && $can_keep_gate ) || ( isset( $edit_user->capabilities ) && is_array( $edit_user->capabilities ) && array_key_exists( 'keymaster', $edit_user->capabilities ) && !$can_keep_gate ) )
				$user_roles = array( 'keymaster' => $user_roles['keymaster'] );

			// only keymasters can promote others to keymaster status
			elseif ( !$can_keep_gate )
				unset( $user_roles['keymaster'] );
		}
	}

	// Load password strength checker
	wp_enqueue_script( 'password-strength-meter' );
	wp_enqueue_script( 'profile-edit' );

	// Generate a few PW hints
	$some_pass_hints = '';
	for ( $l = 3; $l != 0; $l-- )
		$some_pass_hints .= '<p>' . bb_generate_password() . '</p>';

	// Create  the user fields
	$user_fields = array(
		'user_login' => array(
			'title'    => __( 'Username' ),
			'note'     => __( 'Required! Unique identifier for new user.' ),
			'value'    => $user_login,
			'disabled' => $disabled
		),
		'first_name' => array(
			'title'    => __( 'First Name' ),
			'value'    => $user_meta['first_name']
		),
		'last_name' => array(
			'title'    => __( 'Last Name' ),
			'value'    => $user_meta['last_name']
		),
		'display_name' => array(
			'title'    => __( 'Display Name' ),
			'value'    => $user_display_name
		),
		'user_email' => array(
			'title'    => __( 'Email' ),
			'note'     => __( 'Required! Will be used for notifications and profile settings changes.' ),
			'value'    => $user_email
		),
		'user_url' => array(
			'title'    => __( 'Website' ),
			'class'    => array( 'long', 'code' ),
			'note'     => __( 'The full URL of user\'s homepage or blog.' ),
			'value'    => $user_url
		),
		'from' => array(
			'title'    => __( 'Location' ),
			'class'    => array( 'long' ),
			'value'    => $user_meta['from']
		),
		'occ' => array(
			'title'    => __( 'Occupation' ),
			'class'    => array( 'long' ),
			'value'    => $user_meta['occ']
		),
		'interest' => array(
			'title'    => __( 'Interests' ),
			'class'    => array( 'long' ),
			'value'    => $user_meta['interest']
		),
		'userrole' => array(
			'title'    => __( 'User Role' ),
			'type'     => 'select',
			'options'  => $user_roles,
			'note'     => __( 'Allow user the above privileges.' ),
			'value'    => $user_role,
		),
		'pass1' => array(
			'title'    => __( 'New Password' ),
			'type'     => 'password',
			'class'    => array( 'short', 'text', 'code' ),
			'note'     => __( 'Hints: ' ) . $some_pass_hints,
			'value'    => $user_pass1,
		),
		'pass2' => array(
			'title'    => __( 'Repeat New Password' ),
			'type'     => 'password',
			'class'    => array( 'short', 'text', 'code' ),
			'note'     => __( 'If you ignore hints, remember: the password should be at least seven characters long. To make it stronger, use upper and lower case letters, numbers and symbols like ! " ? $ % ^ &amp; ).' ),
			'value'    => $user_pass2,
		),
		'email_pass' => array(
			'title'    => '',
			'type'     => 'checkbox',
			'options'  => array(
				'1' => array(
					'label'      => __( 'Email the new password.' ),
					'attributes' => array( 'checked' => true )
				)
			),
		),
		'pass-strength-fake-input' => array(
			'title' => __( 'Password Strength' ),
			'type'  => 'hidden',
		),
	);

	return apply_filters( 'bb_manage_user_fields', $user_fields );
}

// Not bbdb::prepared
function bb_get_ids_by_role( $role = 'moderator', $sort = 0, $page = 1, $limit = 50 ) {
	global $bbdb, $bb_last_countable_query;
	$sort = $sort ? 'DESC' : 'ASC';
	$key = $bbdb->escape( $bbdb->prefix . 'capabilities' );

	if ( !$page = abs( (int) $page ) )
		$page = 1;
	$limit = abs( (int) $limit );

	$limit = ($limit * ($page - 1)) . ", $limit";

	$role = $bbdb->escape_deep($role);

	if ( is_array($role) )
		$and_where = "( meta_value LIKE '%" . join("%' OR meta_value LIKE '%", $role) . "%' )";
	else
		$and_where = "meta_value LIKE '%$role%'";
	$bb_last_countable_query = "SELECT user_id FROM $bbdb->usermeta WHERE meta_key = '$key' AND $and_where ORDER BY user_id $sort LIMIT $limit";

	$ids = false;

	$_tuple = compact( 'ids', 'role', 'sort', 'page', 'key', 'limit', 'bb_last_countable_query' );
	$_tuple = apply_filters( 'bb_get_ids_by_role', $_tuple );
	extract( $_tuple, EXTR_OVERWRITE );

	if ( !$ids ) {
		$ids = (array) $bbdb->get_col( $bb_last_countable_query );
	}

	if ( $ids ) {
		bb_cache_users( $ids );
	}

	return $ids;
}

function bb_user_row( $user, $role = '', $email = false ) {
	$actions = "<a href='" . esc_attr( get_user_profile_link( $user->ID ) ) . "'>" . __('View') . "</a>";
	$title = '';
	if ( bb_current_user_can( 'edit_user', $user_id ) ) {
		$actions .= " | <a href='" . esc_attr( bb_get_user_admin_link( $user->ID ) ) . "'>" . __('Edit') . "</a>";
		$title = " title='" . esc_attr( sprintf( __( 'User ID: %d' ), $user->ID ) ) . "'";
	}
	$r  = "\t<tr id='user-$user->ID'" . get_alt_class("user-$role") . ">\n";
	$r .= "\t\t<td class=\"user\">" . bb_get_avatar( $user->ID, 32 ) . "<span class=\"row-title\"><a href='" . get_user_profile_link( $user->ID ) . "'" . $title . ">" . get_user_name( $user->ID ) . "</a></span><div><span class=\"row-actions\">$actions</span>&nbsp;</div></td>\n";
	$r .= "\t\t<td><a href='" . get_user_profile_link( $user->ID ) . "'>" . get_user_display_name( $user->ID ) . "</a></td>\n";
	if ( $email ) {
		$email = bb_get_user_email( $user->ID );
		$r .= "\t\t<td><a href='mailto:$email'>$email</a></td>\n";
	}
	
	$registered_time = bb_gmtstrtotime( $user->user_registered );
	if ( $registered_time < ( time() - 86400 ) ) {
		$time = date( 'Y/m/d\<\b\r \/\>H:i:s', bb_offset_time( $registered_time ) );
	} else {
		$time = sprintf( __( '%s ago' ), bb_since( $registered_time ) );
	}
	
	$r .= "\t\t<td>" . $time . "</td>\n";
	
	if (
		!isset($user->capabilities) ||
		!is_array($user->capabilities) ||
		empty($user->capabilities)
	) {
		$role = array( __('Inactive (no role)') );
	} else {
		global $wp_roles;
		$_roles = $wp_roles->get_names();
		$role = array();
		foreach ( $user->capabilities as $cap => $cap_set ) {
			if (!$cap_set) {
				continue;
			}
			$role[] = $_roles[$cap];
		}
		if ( !count( $role ) ) {
			$role[] = __('None');
		}
	}
	
	$r .= "\t\t<td>" . join(', ', $role) . "</td>\n\t</tr>";
	return $r;
}

// BB_User_Search class
// by Mark Jaquith

class BB_User_Search {
	var $results;
	var $search_term;
	var $page;
	var $raw_page;
	var $users_per_page = 50;
	var $first_user;
	var $last_user;
	var $query_limit;
	var $total_users_for_query = 0;
	var $search_errors;
	var $paging_text;
	var $paging_text_bottom;

	function BB_User_Search ($search_term = false, $page = 1, $roles = false ) { // constructor
		$this->search_term = $search_term ? stripslashes($search_term) : false;
		$this->raw_page = ( '' == $page ) ? false : (int) $page;
		$page = (int) $page;
		$this->page = $page < 2 ? 1 : $page;
		$roles = (array) $roles;
		$_roles = array();
		foreach ( $roles as $role ) {
			if ( false !== $role ) {
				$_roles[] = stripslashes( $role );
			}
		}
		$this->roles = empty( $_roles ) ? false : $_roles;

		$this->prepare_query();
		$this->query();
		$this->prepare_vars_for_template_usage();
		$this->do_paging();
	}

	function prepare_query() {
		$this->first_user = ($this->page - 1) * $this->users_per_page;
	}

	function query() {
		$users = bb_user_search( array(
				'query' => $this->search_term,
				'user_email' => true,
				'users_per_page' => $this->users_per_page,
				'page' => $this->page,
				'roles' => $this->roles
		) );

		if ( is_wp_error($users) )
			$this->search_errors = $users;
		else if ( $users )
			$this->results = $users;
		//	foreach ( (array) $users as $user )
		//		$this->results[] = $user->ID;

		if ( $this->results )
			$this->total_users_for_query = bb_count_last_query();
		elseif ( !is_wp_error($this->search_errors) )
			$this->search_errors = new WP_Error( 'no_matching_users_found', __( '<strong>No matching users were found!</strong>' ) );

		if ( is_wp_error( $this->search_errors ) )
			bb_admin_notice( $this->search_errors );
	}

	function prepare_vars_for_template_usage() {
		$this->search_term = stripslashes($this->search_term); // done with DB, from now on we want slashes gone
	}

	function do_paging() {
		global $bb_current_submenu;
		$displaying_num = sprintf(
			__( '%1$s to %2$s of %3$s' ),
			bb_number_format_i18n( ( $this->page - 1 ) * $this->users_per_page + 1 ),
			$this->page * $this->users_per_page < $this->total_users_for_query ? bb_number_format_i18n( $this->page * $this->users_per_page ) : '<span class="total-type-count">' . bb_number_format_i18n( $this->total_users_for_query ) . '</span>',
			'<span class="total-type-count">' . bb_number_format_i18n( $this->total_users_for_query ) . '</span>'
		);
		$page_number_links = $this->total_users_for_query > $this->users_per_page ? get_page_number_links( $this->page, $this->total_users_for_query, $this->users_per_page, false ) : '';
		$this->paging_text = "<div class='tablenav-pages'><span class='displaying-num'>$displaying_num</span><span class=\"displaying-pages\">$page_number_links</span><div class=\"clear\"></div></div>\n";
		$this->paging_text_bottom = "<div class='tablenav-pages'><span class=\"displaying-pages\">$page_number_links</span><div class=\"clear\"></div></div>\n";
	}

	function get_results() {
		return (array) $this->results;
	}

	function page_links() {
		echo $this->paging_text;
	}

	function results_are_paged() {
		if ( isset($this->paging_text) && $this->paging_text )
			return true;
		return false;
	}

	function is_search() {
		if ( $this->search_term )
			return true;
		return false;
	}

	function display( $show_search = true, $show_email = false ) {
		global $wp_roles;

		$r = '';

		if ( isset($this->title) )
			$title = $this->title;
		elseif ( $this->is_search() )
			$title = sprintf(__('Users Matching "%s" by Role'), esc_html( $this->search_term ));

		$h2_search = $this->search_term;
		$h2_role   = $this->roles[0];

		$roles = $wp_roles->get_names();
		if ( in_array( $h2_role, array_keys( $roles ) ) ) {
			$h2_role = $roles[$h2_role];
		}

		$h2_search = $h2_search ? ' ' . sprintf( __('containing &#8220;%s&#8221;'), esc_html( $h2_search ) ) : '';
		$h2_role  = $h2_role  ? ' ' . sprintf( __('with role &#8220;%s&#8221;'), esc_html( $h2_role ) ) : '';

		$h2_span = '<span class="subtitle">';
		$h2_span .= apply_filters( 'bb_user_search_description', sprintf( __( '%1$s%2$s' ), $h2_search, $h2_role ), $h2_search, $h2_role, $this );
		$h2_span .= '</span>';

		echo "<h2 class=\"first\">" . apply_filters( 'bb_user_search_title', __('Users') ) . $h2_span . "</h2>\n";
		do_action( 'bb_admin_notices' );

		if ( $show_search ) {
			$roles = apply_filters( 'bb_user_search_form_roles', $wp_roles->get_names() );
			
			$r .= "<form action='' method='get' id='search' class='search-form'>\n";
			$r .= "<fieldset>\n";
			$r .= "<div>\n";
			$r .= "\t\t<label for='usersearch'>" . __('Search term') . "</label>";
			$r .= "\t\t<div><input type='text' name='usersearch' id='usersearch' class='text-input' value='" . esc_html( $this->search_term, 1) . "' /></div>\n";
			$r .= "</div>\n";
			$r .= "<div>\n";
			$r .= "\t\t<label for='userrole'>" . __('Role') . "</label>";
			$r .= "\t\t<div><select name='userrole[]' id='userrole'>\n";
			$r .= "\t\t\t<option value=''>" . _x( 'All', 'user roles' ) . "</option>\n";
			
			foreach ( $roles as $role => $display ) {
				$selected = '';
				if ( is_array( $this->roles ) && in_array( $role, $this->roles ) ) {
					$selected = ' selected="selected"';
				}
				$value = esc_attr($role);
				$display = esc_html(translate($display));
				$r .= "\t\t\t<option value='$value'$selected>$display</option>\n";
			}
			
			$r .= "\t\t</select></div>\n";
			$r .= "</div>\n";
			
			$r = apply_filters( 'bb_user_search_form_inputs', $r, $this );
			
			$r .= "<div class=\"submit\">\n";
			$r .= "\t\t<label class='hidden' for='submit'>" . __('Search') . "</label>";
			$r .= "\t\t<div><input type='submit' id='submit' class='button submit-input' value='" . __('Filter') . "' /></div>\n";
			$r .= "</div>\n";
			$r .= "</fieldset>\n";
			$r .= "</form>\n\n";
		}

		if ( $this->get_results() ) {
			if ( $this->results_are_paged() )
				$r .= "<div class='tablenav'>\n" . $this->paging_text . "</div><div class=\"clear\"></div>\n\n";

			//foreach($roleclasses as $role => $roleclass) {
				//ksort($roleclass);
				//if ( !empty($role) )
				//	$r .= "\t\t<h3>{$wp_roles->role_names[$role]}</h3>\n";
				//else
				//	$r .= "\t\t<h3><em>" . __('Users with no role in these forums') . "</h3>\n";
				$r .= "<table class='widefat'>\n";
				$r .= "<thead>\n";
				$r .= "\t<tr>\n";
				if ( $show_email ) {
					$r .= "\t\t<th style='width:30%;'>" . __('Username') . "</th>\n";
					$r .= "\t\t<th style='width:20%;'>" . __('Name') . "</th>\n";
					$r .= "\t\t<th style='width:20%;'>" . __('E-mail') . "</th>\n";
				} else {
					$r .= "\t\t<th style='width:40%;'>" . __('Username') . "</th>\n";
					$r .= "\t\t<th style='width:30%;'>" . __('Name') . "</th>\n";
				}
				$r .= "\t\t<th style='width:15%;'>" . __('Registered') . "</th>\n";
				$r .= "\t\t<th style='width:15%;'>" . __('Role') . "</th>\n";
				$r .= "\t</tr>\n";
				$r .= "</thead>\n\n";
				$r .= "<tfoot>\n";
				$r .= "\t<tr>\n";
				if ( $show_email ) {
					$r .= "\t\t<th style='width:30%;'>" . __('Username') . "</th>\n";
					$r .= "\t\t<th style='width:20%;'>" . __('Name') . "</th>\n";
					$r .= "\t\t<th style='width:20%;'>" . __('E-mail') . "</th>\n";
				} else {
					$r .= "\t\t<th style='width:40%;'>" . __('Username') . "</th>\n";
					$r .= "\t\t<th style='width:30%;'>" . __('Name') . "</th>\n";
				}
				$r .= "\t\t<th style='width:15%;'>" . __('Registered') . "</th>\n";
				$r .= "\t\t<th style='width:15%;'>" . __('Role') . "</th>\n";
				$r .= "\t</tr>\n";
				$r .= "</tfoot>\n\n";

				$r .= "<tbody id='role-$role'>\n";
				foreach ( (array) $this->get_results() as $user_object )
					$r .= bb_user_row($user_object, $role, $show_email);
				$r .= "</tbody>\n";
				$r .= "</table>\n\n";
			//}

			if ( $this->results_are_paged() )
				$r .= "<div class='tablenav bottom'>\n" . $this->paging_text_bottom . "</div><div class=\"clear\"></div>\n\n";
		}
		echo $r;
	}

}

class BB_Users_By_Role extends BB_User_Search {
	var $role = '';
	var $title = '';

	function BB_Users_By_Role($role = '', $page = '') { // constructor
		$this->role = $role ? $role : 'member';
		$this->raw_page = ( '' == $page ) ? false : (int) $page;
		$this->page = (int) ( '' == $page ) ? 1 : $page;

		$this->prepare_query();
		$this->query();
		$this->do_paging();
	}

	function query() {
		if ( $_results = bb_get_ids_by_role( $this->role, 0, $this->page, $this->users_per_page ) ) {
			$this->results = bb_get_user($_results);
			$this->total_users_for_query = bb_count_last_query();
		} else
			$this->search_errors = new WP_Error( 'no_matching_users_found', __( '<strong>No matching users were found!</strong>' ) );

		if ( is_wp_error( $this->search_errors ) )
			bb_admin_notice( $this->search_errors );
	}
}

/* Forums */

// Expects forum_name, forum_desc to be pre-escaped
function bb_new_forum( $args ) {
	global $bbdb;
	if ( !bb_current_user_can( 'manage_forums' ) )
		return false;

	$defaults = array( 'forum_name' => '', 'forum_desc' => '', 'forum_parent' => 0, 'forum_order' => false, 'forum_is_category' => 0 );
	$args = wp_parse_args( $args, $defaults );
	if ( 1 < func_num_args() ) : // For back compat
		$args['forum_name']  = func_get_arg(0);
		$args['forum_desc']  = func_get_arg(1);
		$args['forum_order'] = 2 < func_num_args() ? func_get_arg(2) : 0;
	endif;

	extract($args, EXTR_SKIP);

	if ( !is_numeric($forum_order) )
		$forum_order = (int) $bbdb->get_var("SELECT MAX(forum_order) FROM $bbdb->forums") + 1;

	$forum_order = (int) $forum_order;
	$forum_parent = (int) $forum_parent;
	$forum_is_category = (int) $forum_is_category;

	$forum_name = apply_filters( 'bb_pre_forum_name', stripslashes( wp_specialchars_decode( $forum_name, ENT_QUOTES ) ) );
	$forum_desc = apply_filters( 'bb_pre_forum_desc', stripslashes($forum_desc) );

	if ( strlen($forum_name) < 1 )
		return false;

	$forum_sql = "SELECT forum_slug FROM $bbdb->forums WHERE forum_slug = %s";

	$forum_slug = $_forum_slug = bb_slug_sanitize($forum_name);
	if ( strlen($_forum_slug) < 1 )
		return false;

	while ( is_numeric($forum_slug) || $existing_slug = $bbdb->get_var( $bbdb->prepare( $forum_sql, $forum_slug ) ) )
		$forum_slug = bb_slug_increment($_forum_slug, $existing_slug);

	$bbdb->insert( $bbdb->forums, compact( 'forum_name', 'forum_slug', 'forum_desc', 'forum_parent', 'forum_order' ) );
	$forum_id = $bbdb->insert_id;
	if ($forum_id && $forum_is_category)
		bb_update_forummeta($forum_id, 'forum_is_category', $forum_is_category);
	wp_cache_flush( 'bb_forums' );

	return $forum_id;
}

// Expects forum_name, forum_desc to be pre-escaped
function bb_update_forum( $args ) {
	global $bbdb;
	if ( !bb_current_user_can( 'manage_forums' ) )
		return false;

	$defaults = array( 'forum_id' => 0, 'forum_name' => '', 'forum_slug' => '', 'forum_desc' => '', 'forum_parent' => 0, 'forum_order' => 0, 'forum_is_category' => 0 );
	$fields = array( 'forum_name', 'forum_desc', 'forum_parent', 'forum_order' );
	$args = wp_parse_args( $args, $defaults );
	if ( 1 < func_num_args() ) : // For back compat
		$args['forum_id']    = func_get_arg(0);
		$args['forum_name']  = func_get_arg(1);
		$args['forum_desc']  = 2 < func_num_args() ? func_get_arg(2) : '';
		$args['forum_order'] = 3 < func_num_args() && is_numeric(func_get_arg(3)) ? func_get_arg(3) : 0;
	endif;

	extract($args, EXTR_SKIP);

	if ( !$forum_id = (int) $forum_id )
		return false;
	if ( !$forum = bb_get_forum( $forum_id ) )
		return false;
	$forum_order = (int) $forum_order;
	$forum_parent = (int) $forum_parent;
	$forum_is_category = (int) $forum_is_category;

	$forum_name = apply_filters( 'bb_pre_forum_name', stripslashes( wp_specialchars_decode( $forum_name, ENT_QUOTES ) ), $forum_id );
	$forum_desc = apply_filters( 'bb_pre_forum_desc', stripslashes($forum_desc), $forum_id );

	if ( strlen($forum_name) < 1 )
		return false;

	// Slug is not changing, don't update it
	if ( !$forum_slug || $forum_slug == $forum->forum_slug ) {
		// [sic]
	} else {
		$forum_slug = $_forum_slug = bb_slug_sanitize($forum_slug);
		if ( strlen($_forum_slug) < 1 )
			return false;

		$forum_sql = "SELECT forum_slug FROM $bbdb->forums WHERE forum_slug = %s";

		while ( is_numeric($forum_slug) || $existing_slug = $bbdb->get_var( $bbdb->prepare( $forum_sql, $forum_slug ) ) )
			$forum_slug = bb_slug_increment($_forum_slug, $existing_slug);

		$fields[] = 'forum_slug';
	}

	wp_cache_delete( $forum_id, 'bb_forum' );
	wp_cache_flush( 'bb_forums' );

	$update_result = $bbdb->update( $bbdb->forums, compact( $fields ), compact( 'forum_id' ) );

	if ($forum_is_category)
		bb_update_forummeta($forum_id, 'forum_is_category', $forum_is_category);
	else
		bb_delete_forummeta($forum_id, 'forum_is_category');

	return $update_result;
}

// When you delete a forum, you delete *everything*
// NOT bbdb::prepared
function bb_delete_forum( $forum_id ) {
	global $bbdb;
	if ( !bb_current_user_can( 'delete_forum', $forum_id ) )
		return false;
	if ( !$forum_id = (int) $forum_id )
		return false;

	if ( !$forum = bb_get_forum( $forum_id ) )
		return false;

	if ( $topic_ids = $bbdb->get_col( $bbdb->prepare( "SELECT topic_id FROM $bbdb->topics WHERE forum_id = %d", $forum_id ) ) ) {
		foreach ($topic_ids as $topic_id) {
			bb_remove_topic_tags( $topic_id );
		}
		$_topic_ids = join(',', array_map('intval', $topic_ids));
		$bbdb->query("DELETE FROM $bbdb->posts WHERE topic_id IN ($_topic_ids) AND topic_id != 0");
		$bbdb->query("DELETE FROM $bbdb->meta WHERE object_type = 'bb_topic' AND object_id IN ($_topic_ids)");
		$bbdb->query( $bbdb->prepare( "DELETE FROM $bbdb->topics WHERE forum_id = %d", $forum_id ) );
	}
	
	$bbdb->update( $bbdb->forums, array( 'forum_parent' => $forum->forum_parent ), array( 'forum_parent' => $forum_id ) );

	$return = $bbdb->query( $bbdb->prepare( "DELETE FROM $bbdb->forums WHERE forum_id = %d", $forum_id ) );

	wp_cache_flush( 'bb_post' );

	if ( $topic_ids )
		foreach ( $topic_ids as $topic_id ) {
			// should maybe just flush these groups instead
			wp_cache_delete( $topic_id, 'bb_topic' );
			wp_cache_delete( $topic_id, 'bb_thread' );
		}

	wp_cache_delete( $forum_id, 'bb_forum' );
	wp_cache_flush( 'bb_forums' );

	return $return;
}

function bb_forum_row( $forum_id = 0, $echo = true, $close = false ) {
	global $forum, $forums_count;
	if ( $forum_id )
		$_forum = bb_get_forum( $forum_id );
	else
		$_forum =& $forum;

	if ( !$_forum )
		return;

	$description = get_forum_description( $_forum->forum_id );

	$r  = '';
	if ( $close )
		$r .= "\t<li id='forum-$_forum->forum_id'" . get_alt_class( 'forum', 'forum clear list-block' ) . ">\n";
	$r .= "\t\t<div class='list-block posrel'>\n";
	$r .= "\t\t\t<div class=\"row-title\">" . get_forum_name( $_forum->forum_id ) . "</div>\n";
	if ( $description )
		$r .= "\t\t\t<p class=\"row-description\">" . get_forum_description( $_forum->forum_id ) . "</p>\n";
	$r .= "\t\t\t<div class=\"row-actions\"><span>\n";
		$r .= "\t\t\t\t<a class='edit' href='" . get_forum_link() . "'>" . __('View') . "</a>\n";
	if ( bb_current_user_can( 'manage_forums' ) )
		$r .= "\t\t\t\t| <a class='edit' href='" . esc_attr( bb_get_uri('bb-admin/forums.php', array('action' => 'edit', 'id' => $_forum->forum_id), BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN) ) . "'>" . __('Edit') . "</a>\n";
	if ( bb_current_user_can( 'delete_forum', $_forum->forum_id ) && 1 < $forums_count )
		$r .= "\t\t\t\t| <a class='delete' href='" . esc_attr( bb_get_uri('bb-admin/forums.php', array('action' => 'delete', 'id' => $_forum->forum_id), BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN) ) . "'>" . __('Delete') . "</a>\n";
	$r .= "\t\t\t</span>&nbsp;</div>\n";
	$r .= "\t\t</div>\n";
	if ( $close )
		$r .= "\t</li>\n";

	if ( $echo )
		echo $r;
	return $r;
}

function bb_forum_form( $forum_id = 0 ) {
	$forum_id = (int) $forum_id;
	if ( $forum_id && !$forum = bb_get_forum( $forum_id ) ) {
		return;
	}

	$forum_name = '';
	$forum_slug = '';
	$forum_description = '';
	$forum_position = '';

	if ( $forum_id ) {
		$forum_name = get_forum_name( $forum_id );
	    	$forum_slug = apply_filters('editable_slug', $forum->forum_slug); 
		$forum_description = get_forum_description( $forum_id );
		$forum_position = get_forum_position( $forum_id );
		$legend = __( 'Edit Forum' );
		$submit = __( 'Save Changes' );
		$action = 'update';
	} else {
		$legend = __( 'Add Forum' );
		$submit = __( 'Add Forum' );
		$action = 'add';
	}

	$forum_options = array(
		'forum_name' => array(
			'title' => __( 'Name' ),
			'value' => $forum_name
		),
		'forum_slug' => array(
			'title' => __( 'Slug' ),
			'value' => $forum_slug
		),
		'forum_desc' => array(
			'title' => __( 'Description' ),
			'value' => $forum_description,
			'class' => 'long'
		),
		'forum_parent' => array(
			'title' => __( 'Parent' ),
			'type' => 'select',
			'options' => bb_get_forum_dropdown( array(
				'cut_branch' => $forum_id,
				'id' => 'forum_parent',
				'none' => true,
				'selected' => $forum_id ? get_forum_parent( $forum_id ) : 0,
				'disable_categories' => 0,
				'options_only' => true
			) )
		),
		'forum_order' => array(
			'title' => __( 'Position' ),
			'value' => $forum_position,
			'class' => 'short'
		),
		'forum_is_category' => array(
			'title' => __( 'Category' ),
			'type' => 'checkbox',
			'options' => array(
				1 => array(
					'label' => __( 'Make this forum a category' ),
					'value' => bb_get_forum_is_category( $forum_id ),
				)
			),
			'note' => __( 'Categories are forums where new topics cannot be created. Categories usually contain a group of sub-forums.' )
		)
	);
	
	if ( !$forum_id ) {
		unset( $forum_options['forum_slug'] );
		unset( $forum_options['forum_order'] );
	}
	
?>
<form class="settings" method="post" id="<?php echo $action; ?>-forum" action="<?php bb_uri('bb-admin/bb-forum.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN); ?>" class="add:forum-list: forum-form">
	<fieldset>
		<legend><?php echo $legend; ?></legend>
<?php
foreach ( $forum_options as $option => $args ) {
	bb_option_form_element( $option, $args );
}
?>
		<fieldset class="submit">
<?php if ( $forum_id ) : ?>
			<input type="hidden" name="forum_id" value="<?php echo $forum_id; ?>" />
<?php endif; ?>
			<?php bb_nonce_field( 'order-forums', 'order-nonce' ); ?>
			<?php bb_nonce_field( $action . '-forum' ); ?>
			<input type="hidden" name="action" value="<?php echo $action; ?>" />
			<input class="submit" type="submit" name="submit" value="<?php echo $submit; ?>" />
		</fieldset>
	</fieldset>
</form>
<?php
}

class BB_Walker_ForumAdminlistitems extends BB_Walker {
	var $tree_type = 'forum';
	var $db_fields = array ('parent' => 'forum_parent', 'id' => 'forum_id'); //TODO: decouple this
	
	function start_lvl($output, $depth) {
		$indent = str_repeat("\t", $depth) . '    ';
		$output .= $indent . "<ul id='forum-root-$this->forum_id' class='list-block holder'>\n";
		return $output;
	}
	
	function end_lvl($output, $depth) {
		$indent = str_repeat("\t", $depth) . '    ';
		$output .= $indent . "</ul>\n";
		return $output;
	}
	
	function start_el($output, $forum, $depth) {
		$this->forum_id = $forum->forum_id;
		$indent = str_repeat("\t", $depth + 1);
		$output .= $indent . "<li id='forum-$this->forum_id'" . get_alt_class( 'forum', 'forum clear list-block' ) . ">\n";

		return $output;
	}
	
	function end_el($output, $forum, $depth) {
		$indent = str_repeat("\t", $depth + 1);
		$output .= $indent . "</li>\n";
		return $output;
	}
}



/* Topics */

function bb_move_forum_topics( $from_forum_id, $to_forum_id ) {
	global $bbdb;
	
	$from_forum_id = (int) $from_forum_id ;
	$to_forum_id = (int) $to_forum_id;
	
	add_filter('get_forum_where', 'bb_no_where'); // Just in case
	
	$from_forum = bb_get_forum( $from_forum_id );
	if ( !$to_forum = bb_get_forum( $to_forum_id ) )
		return false;

	$posts = $to_forum->posts + ( $from_forum ? $from_forum->posts : 0 );
	$topics = $to_forum->topics + ( $from_forum ? $from_forum->topics : 0 );
	
	$bbdb->update( $bbdb->forums, compact( 'topics', 'posts' ), array( 'forum_id' => $to_forum_id ) );
	$bbdb->update( $bbdb->forums, array( 'topics' => 0, 'posts' => 0 ), array( 'forum_id' => $from_forum_id ) );
	$bbdb->update( $bbdb->posts, array( 'forum_id' => $to_forum_id ), array( 'forum_id' => $from_forum_id ) );
	$topic_ids = $bbdb->get_col( $bbdb->prepare( "SELECT topic_id FROM $bbdb->topics WHERE forum_id = %d", $from_forum_id ) );
	$return = $bbdb->update( $bbdb->topics, array( 'forum_id' => $to_forum_id ), array( 'forum_id' => $from_forum_id ) );

	wp_cache_flush( 'bb_post' );

	if ( $topic_ids )
		foreach ( $topic_ids as $topic_id ) {
			// should maybe just flush these groups
			wp_cache_delete( $topic_id, 'bb_topic' );
			wp_cache_delete( $topic_id, 'bb_thread' );
		}

	wp_cache_delete( $from_forum_id, 'bb_forum' );
	wp_cache_delete( $to_forum_id, 'bb_forum' );
	wp_cache_flush( 'bb_forums' );
	
	return $return;
}

/* Posts */

function bb_admin_list_posts() {
	global $bb_posts, $bb_post;
	
	if ( !$bb_posts ) {
?>
<p class="no-results"><?php _e('No posts found.'); ?></p>
<?php
	} else {
?>
<table id="posts-list" class="widefat" cellspacing="0" cellpadding="0">
<thead>
	<tr>
		<th scope="col" class="check-column"><input type="checkbox" /></th>
		<th scope="col"><?php _e( 'Post' ); ?></th>
		<th scope="col"><?php _e( 'Author' ); ?></th>
		<th scope="col"><?php _e( 'Topic' ); ?></th>
		<th scope="col"><?php _e( 'Date' ); ?></th>
	</tr>
</thead>
<tfoot>
	<tr>
		<th scope="col" class="check-column"><input type="checkbox" /></th>
		<th scope="col"><?php _e( 'Post' ); ?></th>
		<th scope="col"><?php _e( 'Author' ); ?></th>
		<th scope="col"><?php _e( 'Topic' ); ?></th>
		<th scope="col"><?php _e( 'Date' ); ?></th>
	</tr>
</tfoot>
<tbody>
<?php
		foreach ( $bb_posts as $bb_post ) {
?>
	<tr id="post-<?php post_id(); ?>"<?php alt_class('post', post_del_class()); ?>>
		<td class="check-column"><input type="checkbox" name="post[]" value="<?php post_id(); ?>" /></td>
		<td class="post">
			<?php post_text(); ?>
			<div>
				<span class="row-actions">
					<a href="<?php echo esc_url( get_post_link() ); ?>"><?php _e( 'View' ); ?></a>
<?php
	bb_post_admin( array(
		'before_each' => ' | ',
		'each' => array(
			'undelete' => array(
				'before' => ' '
			)
		),
		'last_each' => array(
			'before' => ' | '
		)
	) );
?>
				</span>&nbsp;
			</div>
		</td>

		<td class="author">
			<?php if ( get_post_author_id() ) : ?>

				<a href="<?php user_profile_link( get_post_author_id() ); ?>">
					<?php post_author_avatar( '16' ); ?>
					<?php post_author(); ?>
				</a>

			<?php else : ?>

				<span>
					<?php post_author_avatar( '16' ); ?>
					<?php post_author(); ?>
				</span>

			<?php endif; ?>
		</td>

		<td class="topic">
			<a href="<?php topic_link( $bb_post->topic_id ); ?>"><?php topic_title( $bb_post->topic_id ); ?></a>
		</td>
		
		<td class="date">
<?php
	if ( bb_get_post_time( 'U' ) < ( time() - 86400 ) ) {
		bb_post_time( 'Y/m/d\<\b\r \/\>H:i:s' );
	} else {
		printf( __( '%s ago' ), bb_get_post_time( 'since' ) );
	}
?>
		</td>
	</tr>
<?php 
		}
?>
</tbody>
</table>
<?php
	}
}

/* Recounts */

function bb_recount_list()
{
	global $recount_list;
	$recount_list = array(
		5  => array( 'topic-posts', __( 'Count posts of every topic' ) ),
		6  => array( 'topic-voices', __( 'Count voices of every topic' ) ),
		10 => array( 'topic-deleted-posts', __( 'Count deleted posts on every topic' ) ),
		15 => array( 'forums', __( 'Count topics and posts in every forum' ) ),
		20 => array( 'topics-replied', __( 'Count topics to which each user has replied' ) ),
		25 => array( 'topic-tag-count', __( 'Count tags for every topic' ) ),
		30 => array( 'tags-tag-count', __( 'Count topics for every tag' ) ),
		35 => array( 'tags-delete-empty', __( 'Delete tags with no topics' ) ),
		40 => array( 'clean-favorites', __( 'Remove deleted topics from users\' favorites' ) )
	);
	do_action( 'bb_recount_list' );
	ksort( $recount_list );
	return $recount_list;
}

/* Themes */

function bb_get_current_theme_data( $property = 'all' ) {
	if (!$property) {
		$property = 'all';
	}
	$directory = bb_get_active_theme_directory();
	$stylesheet = $directory . 'style.css';
	if (file_exists($stylesheet)) {
		$data = bb_get_theme_data($stylesheet);
	}
	if ($property == 'all') {
		return $data;
	} elseif (isset($data[$property])) {
		return $data[$property];
	} else {
		return false;
	}
}

// Output sanitized for display
function bb_get_theme_data( $theme_file )
{
	if ( strpos($theme_file, '#') !== false ) {
		$theme_file = bb_get_theme_directory( $theme_file ) . 'style.css';
	}
	$theme_code = implode( '', file( $theme_file ) );
	$theme_code = str_replace ( '\r', '\n', $theme_code );
	// Grab just the first commented area from the file
	preg_match( '|/\*(.*)\*/|msU', $theme_code, $theme_block );
	$theme_data = trim( $theme_block[1] );
	preg_match( '|Theme Name:(.*)|i', $theme_data, $theme_name );
	preg_match( '|Theme URI:(.*)|i', $theme_data, $theme_uri );
	preg_match( '|Description:(.*)|i', $theme_data, $description );
	preg_match( '|Author:(.*)|i', $theme_data, $author_name );
	preg_match( '|Author URI:(.*)|i', $theme_data, $author_uri );
	preg_match( '|Ported By:(.*)|i', $theme_data, $porter_name );
	preg_match( '|Porter URI:(.*)|i', $theme_data, $porter_uri );
//	preg_match( '|Template:(.*)|i', $theme_data, $template );
	if ( preg_match( '|Version:(.*)|i', $theme_data, $version ) )
		$version = esc_html( trim( $version[1] ) );
	else
		$version ='';
	if ( preg_match('|Status:(.*)|i', $theme_data, $status) )
		$status = esc_html( trim($status[1]) );
	else
		$status = 'publish';

	$description = trim($description[1]);
	$description = bb_encode_bad( $description );
	$description = bb_code_trick( $description );
	$description = force_balance_tags( $description );
	$description = bb_filter_kses( $description );
	$description = bb_autop( $description );

	$name = $theme_name[1];
	$name = esc_html( trim($name) );
	$theme = $name;

	if ( $author_name || $author_uri ) {
		if ( empty($author_uri[1]) ) {
			$author = bb_filter_kses( trim($author_name[1]) );
		} else {
			$author = '<a href="' . esc_url( trim($author_uri[1]) ) . '" title="' . esc_attr__( 'Visit author homepage' ) . '">' . bb_filter_kses( trim($author_name[1]) ) . '</a>';
		}
	} else {
		$author = '';
	}

	if ( $porter_name || $porter_uri ) {
		if ( empty($porter_uri[1]) ) {
			$porter = bb_filter_kses( trim($porter_name[1]) );
		} else {
			$porter = '<a href="' . esc_url( trim($porter_uri[1]) ) . '" title="' . esc_attr__( 'Visit porter homepage' ) . '">' . bb_filter_kses( trim($porter_name[1]) ) . '</a>';
		}
	} else {
		$porter = '';
	}

	global $bb;

	// Normalise the path to the theme
	$theme_file = str_replace( '\\', '/', $theme_file );

	foreach ( $bb->theme_locations as $_name => $_data ) {
		$_directory = str_replace( '\\', '/', $_data['dir'] );
		if ( 0 === strpos( $theme_file, $_directory ) ) {
			$location = $_name;
			break;
		}
	}

	return array(
		'Location' => $location,
		'Name' => $name,
		'Title' => $theme,
		'Description' => $description,
		'Author' => $author,
		'Porter' => $porter,
		'Version' => $version,
//		'Template' => $template[1],
		'Status' => $status,
		'URI' => esc_url( $theme_uri[1] )
	);
}

if ( !function_exists( 'checked' ) ) :
function checked( $checked, $current) {
	if ( $checked == $current)
		echo ' checked="checked"';
}
endif;

if ( !function_exists( 'selected' ) ) :
function selected( $selected, $current) {
	if ( $selected === $current)
		echo ' selected="selected"';
}
endif;

/* Options */

function bb_option_form_element( $name = 'name', $args = null ) {
	global $bb_hardcoded;

	$defaults = array(
		'title' => 'title',
		'type' => 'text',
		'value' => false,
		'options' => false,
		'message' => false,
		'class' => false,
		'default' => false,
		'before' => '',
		'after' => '',
		'note' => false,
		'attributes' => false,
		'disabled' => false,
	);

	$args = wp_parse_args( $args, $defaults );

	$id = str_replace( array( '_', '[', ']' ), array( '-', '-', '' ), $name );
	if ( false !== strpos( $name, '[' ) ) {
		list( $option_name, $option_key ) = preg_split( '/[\[\]]/', $name, -1, PREG_SPLIT_NO_EMPTY );
		$option = bb_get_option( $option_name );
		$value = false === $args['value'] ? esc_attr( $option[$option_key] ) : esc_attr( $args['value'] );
		$hardcoded = isset( $bb_hardcoded[$option_name][$option_key] );
	} else {
		$value = false === $args['value'] ? bb_get_form_option( $name ) : esc_attr( $args['value'] );
		$hardcoded = isset( $bb_hardcoded[$name] );
	}

	$class = $args['class'] ? (array) $args['class'] : array();
	array_unshift( $class, $args['type'] );

	if ( $hardcoded || $args['disabled'] )
		$disabled = ' disabled="disabled"';
	else
		$disabled = false;

	if ( $args['attributes'] ) {
		$attributes = array();
		foreach ( $args['attributes'] as $k => $v )
			$attributes[] = "$k='$v'";
		$attributes = ' ' . join( ' ', $attributes );
	} else {
		$attributes = '';
	}

?>

		<div id="option-<?php echo $id; ?>"<?php if ( !empty( $disabled ) ) echo ' class="disabled"'; ?>>
<?php
			switch ( $args['type'] ) {
				case 'radio' :
				case 'checkbox' :
				case 'message' :
?>
			<div class="label">
				<?php echo $args['title']; ?>
			</div>

<?php
					break;
				case 'select' :
				default :
?>
			<label for="<?php echo $id; ?>">
				<?php echo $args['title']; ?>
			</label>

<?php
					break;
			}
?>
			<div class="inputs">

<?php
			if ( $args['before'] ) {
				echo '<span class="before">' . $args['before'] . '</span>';
			}
			switch ( $args['type'] ) {
				case 'select' :
					echo "<select$disabled class='" . join( ' ', $class ) . "' name='$name' id='$id'$attributes>\n";
					if ( is_array( $args['options'] ) ) {
						foreach ( $args['options'] as $option => $label )
							echo "\t<option value='$option'" . ( $value == $option ? " selected='selected'" : '' ) . ">$label</option>\n";
					} elseif ( is_string( $args['options'] ) ) {
						echo $args['options'] . "\n";
					}
					echo "</select>\n";
					break;
				case 'radio' :
				case 'checkbox' :
					if ( is_array( $args['options'] ) ) {
						$_id = 0;
						if ( 'radio' === $args['type'] && !in_array( $value, array_keys( $args['options'] ) ) && empty( $value ) ) {
							$use_first_value = true;
						}
						$type = $args['type'];
						foreach ( $args['options'] as $option => $label ) {
							if ( $use_first_value ) {
								$use_first_value = false;
								$value = $option;
							}
							if ( is_array( $label ) ) {
								if ( isset( $label['attributes'] ) ) {
									$attributes = array();
									foreach ( $label['attributes'] as $k => $v )
										$attributes[] = "$k='$v'";
									$attributes = ' ' . join( ' ', $attributes );
								} else {
									$attributes = '';
								}
								if ( isset( $label['name'] ) ) {
									$name = $label['name'];
									$id = str_replace( array( '_', '[', ']' ), array( '-', '-', '' ), $name );
									$hardcoded = isset( $bb_hardcoded[$name] );
								}
								if ( isset( $label['value'] ) ) {
									$_value = $label['value'];
								} else {
									$_value = $args['value'];
								}
								$value = false === $_value ? bb_get_form_option( $name ) : esc_attr( $_value );
								$label = $label['label'];
							}
							echo "<label class=\"{$type}s\"><input$disabled type='$type' class='" . join( ' ', $class ) . "' name='$name' id='$id-$_id' value='$option'" . ( $value == $option ? " checked='checked'" : '' ) . "{$attributes} /> $label</label>\n";
							$_id++;
						}
					} elseif ( is_string( $args['options'] ) ) {
						echo $args['options'] . "\n";
					}
					break;
				case 'message' :
					if ( $args['message'] ) {
						echo $args['message'];
					}
					break;
				default :
					echo "<input$disabled type='$args[type]' class='" . join( ' ', $class ) . "' name='$name' id='$id' value='$value'$attributes />\n";
					break;
			}
			if ( $args['after'] ) {
				echo '<span class="after">' . $args['after'] . '</span>';
			}

			if ( $args['note'] ) {
				foreach ( (array) $args['note'] as $note ) {
?>

				<p><?php echo $note; ?></p>

<?php
				}
			}
?>

			</div>
		</div>

<?php
}

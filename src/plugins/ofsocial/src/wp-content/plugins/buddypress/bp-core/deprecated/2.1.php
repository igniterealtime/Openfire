<?php
/**
 * Deprecated functions
 *
 * @package BuddyPress
 * @subpackage Core
 * @deprecated 2.1.0
 */

// Exit if accessed directly
defined( 'ABSPATH' ) || exit;

/**
 * Register (not enqueue) scripts that used to be used by BuddyPress.
 *
 * @since 2.1.0
 */
function bp_core_register_deprecated_scripts() {
	$min = defined( 'SCRIPT_DEBUG' ) && SCRIPT_DEBUG ? '' : '.min';
	$url = buddypress()->plugin_url . 'bp-core/deprecated/js/';

	$scripts = apply_filters( 'bp_core_register_deprecated_scripts', array(

		// Messages
		'bp-jquery-autocomplete'    => array(
			'file'          => "{$url}autocomplete/jquery.autocomplete{$min}.js",
			'dependencies' => array( 'jquery' ),
		),

		'bp-jquery-autocomplete-fb' => array(
			'file'         => "{$url}autocomplete/jquery.autocompletefb{$min}.js",
			'dependencies' => array( 'jquery' ),
		),

		'bp-jquery-bgiframe' => array(
			'file'         => "{$url}autocomplete/jquery.bgiframe{$min}.js",
			'dependencies' => array( 'jquery' ),
		),

		'bp-jquery-dimensions' => array(
			'file'         => "{$url}autocomplete/jquery.dimensions{$min}.js",
			'dependencies' => array( 'jquery' ),
		),
	) );

	foreach ( $scripts as $id => $script ) {
		wp_register_script( $id, $script['file'], $script['dependencies'], bp_get_version(), true );
	}
}
add_action( 'bp_enqueue_scripts', 'bp_core_register_deprecated_scripts', 1 );

/**
 * Register (not enqueue) styles that used to be used by BuddyPress.
 *
 * @since 2.1.0
 */
function bp_core_register_deprecated_styles() {
	$min = defined( 'SCRIPT_DEBUG' ) && SCRIPT_DEBUG ? '' : '.min';
	$url = buddypress()->plugin_url . 'bp-core/deprecated/css/';

	$styles = apply_filters( 'bp_core_register_deprecated_styles', array(
		// Messages
		'bp-messages-autocomplete' => array(
			'file'         => "{$url}autocomplete/jquery.autocompletefb{$min}.css",
			'dependencies' => array(),
		)
	) );

	foreach ( $styles as $id => $style ) {
		wp_register_style( $id, $style['file'], $style['dependencies'], bp_get_version() );

		wp_style_add_data( $id, 'rtl', true );
		if ( $min ) {
			wp_style_add_data( $id, 'suffix', $min );
		}
	}
}
add_action( 'bp_enqueue_scripts', 'bp_core_register_deprecated_styles', 1 );

/** BuddyBar *****************************************************************/

/**
 * Add a Sites menu to the BuddyBar.
 *
 * @since 1.0.0
 * @deprecated 2.1.0
 *
 * @return bool|null Returns false on failure. Otherwise echoes the menu item.
 */
function bp_adminbar_blogs_menu() {

	if ( ! is_user_logged_in() || ! bp_is_active( 'blogs' ) ) {
		return false;
	}

	if ( ! is_multisite() ) {
		return false;
	}

	$blogs = wp_cache_get( 'bp_blogs_of_user_' . bp_loggedin_user_id() . '_inc_hidden', 'bp' );
	if ( empty( $blogs ) ) {
		$blogs = bp_blogs_get_blogs_for_user( bp_loggedin_user_id(), true );
		wp_cache_set( 'bp_blogs_of_user_' . bp_loggedin_user_id() . '_inc_hidden', $blogs, 'bp' );
	}

	$counter = 0;
	if ( is_array( $blogs['blogs'] ) && (int) $blogs['count'] ) {

		echo '<li id="bp-adminbar-blogs-menu"><a href="' . trailingslashit( bp_loggedin_user_domain() . bp_get_blogs_slug() ) . '">';

		_e( 'My Sites', 'buddypress' );

		echo '</a>';
		echo '<ul>';

		foreach ( (array) $blogs['blogs'] as $blog ) {
			$alt      = ( 0 == $counter % 2 ) ? ' class="alt"' : '';
			$site_url = esc_attr( $blog->siteurl );

			echo '<li' . $alt . '>';
			echo '<a href="' . $site_url . '">' . esc_html( $blog->name ) . '</a>';
			echo '<ul>';
			echo '<li class="alt"><a href="' . $site_url . 'wp-admin/">' . __( 'Dashboard', 'buddypress' ) . '</a></li>';
			echo '<li><a href="' . $site_url . 'wp-admin/post-new.php">' . __( 'New Post', 'buddypress' ) . '</a></li>';
			echo '<li class="alt"><a href="' . $site_url . 'wp-admin/edit.php">' . __( 'Manage Posts', 'buddypress' ) . '</a></li>';
			echo '<li><a href="' . $site_url . 'wp-admin/edit-comments.php">' . __( 'Manage Comments', 'buddypress' ) . '</a></li>';
			echo '</ul>';

			do_action( 'bp_adminbar_blog_items', $blog );

			echo '</li>';
			$counter++;
		}

		$alt = ( 0 == $counter % 2 ) ? ' class="alt"' : '';

		if ( bp_blog_signup_enabled() ) {
			echo '<li' . $alt . '>';
			echo '<a href="' . trailingslashit( bp_get_blogs_directory_permalink() . 'create' ) . '">' . __( 'Create a Site!', 'buddypress' ) . '</a>';
			echo '</li>';
		}

		echo '</ul>';
		echo '</li>';
	}
}

/**
 * If user has upgraded to 1.6 and chose to retain their BuddyBar, offer then a switch to change over
 * to the WP Toolbar.
 *
 * @since 1.6.0
 * @deprecated 2.1.0
 */
function bp_admin_setting_callback_force_buddybar() {
?>

	<input id="_bp_force_buddybar" name="_bp_force_buddybar" type="checkbox" value="1" <?php checked( ! bp_force_buddybar( true ) ); ?> />
	<label for="_bp_force_buddybar"><?php _e( 'Switch to WordPress Toolbar', 'buddypress' ); ?></label>

<?php
}


/**
 * Sanitization for _bp_force_buddybar
 *
 * If upgraded to 1.6 and you chose to keep the BuddyBar, a checkbox asks if you want to switch to
 * the WP Toolbar. The option we store is 1 if the BuddyBar is forced on, so we use this function
 * to flip the boolean before saving the intval.
 *
 * @since 1.6.0
 * @deprecated 2.1.0
 * @access Private
 */
function bp_admin_sanitize_callback_force_buddybar( $value = false ) {
	return $value ? 0 : 1;
}

/**
 * Wrapper function for rendering the BuddyBar.
 *
 * @return bool|null Returns false if the BuddyBar is disabled.
 * @deprecated 2.1.0
 */
function bp_core_admin_bar() {
	$bp = buddypress();

	if ( defined( 'BP_DISABLE_ADMIN_BAR' ) && BP_DISABLE_ADMIN_BAR ) {
		return false;
	}

	if ( (int) bp_get_option( 'hide-loggedout-adminbar' ) && !is_user_logged_in() ) {
		return false;
	}

	$bp->doing_admin_bar = true;

	echo '<div id="wp-admin-bar"><div class="padder">';

	// **** Do bp-adminbar-logo Actions ********
	do_action( 'bp_adminbar_logo' );

	echo '<ul class="main-nav">';

	// **** Do bp-adminbar-menus Actions ********
	do_action( 'bp_adminbar_menus' );

	echo '</ul>';
	echo "</div></div><!-- #wp-admin-bar -->\n\n";

	$bp->doing_admin_bar = false;
}

/**
 * Output the BuddyBar logo.
 *
 * @deprecated 2.1.0
 */
function bp_adminbar_logo() {
	echo '<a href="' . bp_get_root_domain() . '" id="admin-bar-logo">' . get_blog_option( bp_get_root_blog_id(), 'blogname' ) . '</a>';
}

/**
 * Output the "Log In" and "Sign Up" names to the BuddyBar.
 *
 * Visible only to visitors who are not logged in.
 *
 * @deprecated 2.1.0
 *
 * @return bool|null Returns false if the current user is logged in.
 */
function bp_adminbar_login_menu() {

	if ( is_user_logged_in() ) {
		return false;
	}

	echo '<li class="bp-login no-arrow"><a href="' . wp_login_url() . '">' . __( 'Log In', 'buddypress' ) . '</a></li>';

	// Show "Sign Up" link if user registrations are allowed
	if ( bp_get_signup_allowed() ) {
		echo '<li class="bp-signup no-arrow"><a href="' . bp_get_signup_page() . '">' . __( 'Sign Up', 'buddypress' ) . '</a></li>';
	}
}

/**
 * Output the My Account BuddyBar menu.
 *
 * @deprecated 2.1.0
 *
 * @return bool|null Returns false on failure.
 */
function bp_adminbar_account_menu() {
	$bp = buddypress();

	if ( empty( $bp->bp_nav ) || ! is_user_logged_in() ) {
		return false;
	}

	echo '<li id="bp-adminbar-account-menu"><a href="' . bp_loggedin_user_domain() . '">';
	echo __( 'My Account', 'buddypress' ) . '</a>';
	echo '<ul>';

	// Loop through each navigation item
	$counter = 0;
	foreach( (array) $bp->bp_nav as $nav_item ) {
		$alt = ( 0 == $counter % 2 ) ? ' class="alt"' : '';

		if ( -1 == $nav_item['position'] ) {
			continue;
		}

		echo '<li' . $alt . '>';
		echo '<a id="bp-admin-' . $nav_item['css_id'] . '" href="' . $nav_item['link'] . '">' . $nav_item['name'] . '</a>';

		if ( isset( $bp->bp_options_nav[$nav_item['slug']] ) && is_array( $bp->bp_options_nav[$nav_item['slug']] ) ) {
			echo '<ul>';
			$sub_counter = 0;

			foreach( (array) $bp->bp_options_nav[$nav_item['slug']] as $subnav_item ) {
				$link = $subnav_item['link'];
				$name = $subnav_item['name'];

				if ( bp_displayed_user_domain() ) {
					$link = str_replace( bp_displayed_user_domain(), bp_loggedin_user_domain(), $subnav_item['link'] );
				}

				if ( isset( $bp->displayed_user->userdata->user_login ) ) {
					$name = str_replace( $bp->displayed_user->userdata->user_login, $bp->loggedin_user->userdata->user_login, $subnav_item['name'] );
				}

				$alt = ( 0 == $sub_counter % 2 ) ? ' class="alt"' : '';
				echo '<li' . $alt . '><a id="bp-admin-' . $subnav_item['css_id'] . '" href="' . $link . '">' . $name . '</a></li>';
				$sub_counter++;
			}
			echo '</ul>';
		}

		echo '</li>';

		$counter++;
	}

	$alt = ( 0 == $counter % 2 ) ? ' class="alt"' : '';

	echo '<li' . $alt . '><a id="bp-admin-logout" class="logout" href="' . wp_logout_url( home_url() ) . '">' . __( 'Log Out', 'buddypress' ) . '</a></li>';
	echo '</ul>';
	echo '</li>';
}

function bp_adminbar_thisblog_menu() {
	if ( current_user_can( 'edit_posts' ) ) {
		echo '<li id="bp-adminbar-thisblog-menu"><a href="' . admin_url() . '">';
		_e( 'Dashboard', 'buddypress' );
		echo '</a>';
		echo '<ul>';

		echo '<li class="alt"><a href="' . admin_url() . 'post-new.php">' . __( 'New Post', 'buddypress' ) . '</a></li>';
		echo '<li><a href="' . admin_url() . 'edit.php">' . __( 'Manage Posts', 'buddypress' ) . '</a></li>';
		echo '<li class="alt"><a href="' . admin_url() . 'edit-comments.php">' . __( 'Manage Comments', 'buddypress' ) . '</a></li>';

		do_action( 'bp_adminbar_thisblog_items' );

		echo '</ul>';
		echo '</li>';
	}
}

/**
 * Output the Random BuddyBar menu.
 *
 * Not visible for logged-in users.
 *
 * @deprecated 2.1.0
 */
function bp_adminbar_random_menu() {
?>

	<li class="align-right" id="bp-adminbar-visitrandom-menu">
		<a href="#"><?php _e( 'Visit', 'buddypress' ) ?></a>
		<ul class="random-list">
			<li><a href="<?php bp_members_directory_permalink(); ?>?random-member" rel="nofollow"><?php _e( 'Random Member', 'buddypress' ) ?></a></li>

			<?php if ( bp_is_active( 'groups' ) ) : ?>

				<li class="alt"><a href="<?php bp_groups_directory_permalink(); ?>?random-group"  rel="nofollow"><?php _e( 'Random Group', 'buddypress' ) ?></a></li>

			<?php endif; ?>

			<?php if ( is_multisite() && bp_is_active( 'blogs' ) ) : ?>

				<li><a href="<?php bp_blogs_directory_permalink(); ?>?random-blog"  rel="nofollow"><?php _e( 'Random Site', 'buddypress' ) ?></a></li>

			<?php endif; ?>

			<?php do_action( 'bp_adminbar_random_menu' ) ?>

		</ul>
	</li>

	<?php
}

/**
 * Enqueue the BuddyBar CSS.
 *
 * @deprecated 2.1.0
 */
function bp_core_load_buddybar_css() {

	if ( bp_use_wp_admin_bar() || ( (int) bp_get_option( 'hide-loggedout-adminbar' ) && !is_user_logged_in() ) || ( defined( 'BP_DISABLE_ADMIN_BAR' ) && BP_DISABLE_ADMIN_BAR ) ) {
		return;
	}

	$min = defined( 'SCRIPT_DEBUG' ) && SCRIPT_DEBUG ? '' : '.min';

	if ( file_exists( get_stylesheet_directory() . '/_inc/css/adminbar.css' ) ) { // Backwards compatibility
		$stylesheet = get_stylesheet_directory_uri() . '/_inc/css/adminbar.css';
	} else {
		$stylesheet = buddypress()->plugin_url . "bp-core/css/buddybar{$min}.css";
	}

	wp_enqueue_style( 'bp-admin-bar', apply_filters( 'bp_core_buddybar_rtl_css', $stylesheet ), array(), bp_get_version() );

	wp_style_add_data( 'bp-admin-bar', 'rtl', true );
	if ( $min ) {
		wp_style_add_data( 'bp-admin-bar', 'suffix', $min );
	}
}
add_action( 'bp_init', 'bp_core_load_buddybar_css' );

/**
 * Add menu items to the BuddyBar.
 *
 * @since 1.0.0
 *
 * @deprecated 2.1.0
 */
function bp_groups_adminbar_admin_menu() {
	$bp = buddypress();

	if ( empty( $bp->groups->current_group ) ) {
		return false;
	}

	// Only group admins and site admins can see this menu
	if ( !current_user_can( 'edit_users' ) && !bp_current_user_can( 'bp_moderate' ) && !bp_is_item_admin() ) {
		return false;
	} ?>

	<li id="bp-adminbar-adminoptions-menu">
		<a href="<?php bp_groups_action_link( 'admin' ); ?>"><?php _e( 'Admin Options', 'buddypress' ); ?></a>

		<ul>
			<li><a href="<?php bp_groups_action_link( 'admin/edit-details' ); ?>"><?php _e( 'Edit Details', 'buddypress' ); ?></a></li>

			<li><a href="<?php bp_groups_action_link( 'admin/group-settings' );  ?>"><?php _e( 'Group Settings', 'buddypress' ); ?></a></li>

			<?php if ( !(int)bp_get_option( 'bp-disable-avatar-uploads' ) && $bp->avatar->show_avatars ) : ?>

				<li><a href="<?php bp_groups_action_link( 'admin/group-avatar' ); ?>"><?php _e( 'Group Profile Photo', 'buddypress' ); ?></a></li>

			<?php endif; ?>

			<?php if ( bp_is_active( 'friends' ) ) : ?>

				<li><a href="<?php bp_groups_action_link( 'send-invites' ); ?>"><?php _e( 'Manage Invitations', 'buddypress' ); ?></a></li>

			<?php endif; ?>

			<li><a href="<?php bp_groups_action_link( 'admin/manage-members' ); ?>"><?php _e( 'Manage Members', 'buddypress' ); ?></a></li>

			<?php if ( $bp->groups->current_group->status == 'private' ) : ?>

				<li><a href="<?php bp_groups_action_link( 'admin/membership-requests' ); ?>"><?php _e( 'Membership Requests', 'buddypress' ); ?></a></li>

			<?php endif; ?>

			<li><a class="confirm" href="<?php echo wp_nonce_url( bp_get_group_permalink( $bp->groups->current_group ) . 'admin/delete-group/', 'groups_delete_group' ); ?>&amp;delete-group-button=1&amp;delete-group-understand=1"><?php _e( "Delete Group", 'buddypress' ) ?></a></li>

			<?php do_action( 'bp_groups_adminbar_admin_menu' ) ?>

		</ul>
	</li>

	<?php
}
add_action( 'bp_adminbar_menus', 'bp_groups_adminbar_admin_menu', 20 );

/**
 * Add the Notifications menu to the BuddyBar.
 *
 * @deprecated 2.1.0
 */
function bp_adminbar_notifications_menu() {

	// Bail if notifications is not active
	if ( ! bp_is_active( 'notifications' ) ) {
		return false;
	}

	bp_notifications_buddybar_menu();
}
add_action( 'bp_adminbar_menus', 'bp_adminbar_notifications_menu', 8 );

/**
 * Add the Blog Authors menu to the BuddyBar (visible when not logged in).
 *
 * @deprecated 2.1.0
 */
function bp_adminbar_authors_menu() {
	global $wpdb;

	// Only for multisite
	if ( ! is_multisite() ) {
		return false;
	}

	// Hide on root blog
	if ( bp_is_root_blog( $wpdb->blogid ) || ! bp_is_active( 'blogs' ) ) {
		return false;
	}

	$blog_prefix = $wpdb->get_blog_prefix( $wpdb->blogid );
	$authors     = $wpdb->get_results( "SELECT user_id, user_login, user_nicename, display_name, user_email, meta_value as caps FROM $wpdb->users u, $wpdb->usermeta um WHERE u.ID = um.user_id AND meta_key = '{$blog_prefix}capabilities' ORDER BY um.user_id" );

	if ( !empty( $authors ) ) {
		// This is a blog, render a menu with links to all authors
		echo '<li id="bp-adminbar-authors-menu"><a href="/">';
		_e('Blog Authors', 'buddypress');
		echo '</a>';

		echo '<ul class="author-list">';
		foreach( (array) $authors as $author ) {
			$caps = maybe_unserialize( $author->caps );
			if ( isset( $caps['subscriber'] ) || isset( $caps['contributor'] ) ) {
				continue;
			}

			echo '<li>';
			echo '<a href="' . bp_core_get_user_domain( $author->user_id, $author->user_nicename, $author->user_login ) . '">';
			echo bp_core_fetch_avatar( array(
				'item_id' => $author->user_id,
				'email'   => $author->user_email,
				'width'   => 15,
				'height'  => 15,
				'alt'     => sprintf( __( 'Profile picture of %s', 'buddypress' ), $author->display_name )
			) );
 			echo ' ' . $author->display_name . '</a>';
			echo '<div class="admin-bar-clear"></div>';
			echo '</li>';
		}
		echo '</ul>';
		echo '</li>';
	}
}
add_action( 'bp_adminbar_menus', 'bp_adminbar_authors_menu', 12 );

/**
 * Add a member admin menu to the BuddyBar.
 *
 * Adds an Toolbar menu to any profile page providing site moderator actions
 * that allow capable users to clean up a users account.
 *
 * @deprecated 2.1.0
 */
function bp_members_adminbar_admin_menu() {

	// Only show if viewing a user
	if ( ! bp_displayed_user_id() ) {
		return false;
	}

	// Don't show this menu to non site admins or if you're viewing your own profile
	if ( !current_user_can( 'edit_users' ) || bp_is_my_profile() ) {
		return false;
	} ?>

	<li id="bp-adminbar-adminoptions-menu">

		<a href=""><?php _e( 'Admin Options', 'buddypress' ) ?></a>

		<ul>
			<?php if ( bp_is_active( 'xprofile' ) ) : ?>

				<li><a href="<?php bp_members_component_link( 'profile', 'edit' ); ?>"><?php printf( __( "Edit %s's Profile", 'buddypress' ), esc_attr( bp_get_displayed_user_fullname() ) ) ?></a></li>

			<?php endif ?>

			<li><a href="<?php bp_members_component_link( 'profile', 'change-avatar' ); ?>"><?php printf( __( "Edit %s's Profile Photo", 'buddypress' ), esc_attr( bp_get_displayed_user_fullname() ) ) ?></a></li>

			<li><a href="<?php bp_members_component_link( 'settings', 'capabilities' ); ?>"><?php _e( 'User Capabilities', 'buddypress' ); ?></a></li>

			<li><a href="<?php bp_members_component_link( 'settings', 'delete-account' ); ?>"><?php printf( __( "Delete %s's Account", 'buddypress' ), esc_attr( bp_get_displayed_user_fullname() ) ); ?></a></li>

			<?php do_action( 'bp_members_adminbar_admin_menu' ) ?>

		</ul>
	</li>

	<?php
}
add_action( 'bp_adminbar_menus', 'bp_members_adminbar_admin_menu', 20 );

/**
 * Create the Notifications menu for the BuddyBar.
 *
 * @since 1.9.0
 * @deprecated 2.1.0
 */
function bp_notifications_buddybar_menu() {

	if ( ! is_user_logged_in() ) {
		return false;
	}

	echo '<li id="bp-adminbar-notifications-menu"><a href="' . esc_url( bp_loggedin_user_domain() ) . '">';
	_e( 'Notifications', 'buddypress' );

	$notification_count = bp_notifications_get_unread_notification_count( bp_loggedin_user_id() );
	$notifications      = bp_notifications_get_notifications_for_user( bp_loggedin_user_id() );

	if ( ! empty( $notification_count ) ) : ?>
		<span><?php echo bp_core_number_format( $notification_count ); ?></span>
	<?php
	endif;

	echo '</a>';
	echo '<ul>';

	if ( ! empty( $notifications ) ) {
		$counter = 0;
		for ( $i = 0, $count = count( $notifications ); $i < $count; ++$i ) {
			$alt = ( 0 == $counter % 2 ) ? ' class="alt"' : ''; ?>

			<li<?php echo $alt ?>><?php echo $notifications[$i] ?></li>

			<?php $counter++;
		}
	} else { ?>

		<li><a href="<?php echo esc_url( bp_loggedin_user_domain() ); ?>"><?php _e( 'No new notifications.', 'buddypress' ); ?></a></li>

	<?php
	}

	echo '</ul>';
	echo '</li>';
}
add_action( 'bp_adminbar_menus', 'bp_adminbar_notifications_menu', 8 );

/**
 * Output the base URL for subdomain installations of WordPress Multisite.
 *
 * @since 1.6.0
 *
 * @deprecated 2.1.0
 */
function bp_blogs_subdomain_base() {
	_deprecated_function( __FUNCTION__, '2.1', 'bp_signup_subdomain_base()' );
	echo bp_signup_get_subdomain_base();
}

/**
 * Return the base URL for subdomain installations of WordPress Multisite.
 *
 * @since 1.6.0
 *
 * @return string The base URL - eg, 'example.com' for site_url() example.com or www.example.com.
 *
 * @deprecated 2.1.0
 */
function bp_blogs_get_subdomain_base() {
	_deprecated_function( __FUNCTION__, '2.1', 'bp_signup_get_subdomain_base()' );
	return bp_signup_get_subdomain_base();
}

/**
 * Allegedly output an avatar upload form, but it hasn't done that since 2009.
 *
 * @since 1.0.0
 * @deprecated 2.1.0
 */
function bp_avatar_upload_form() {
	_deprecated_function(__FUNCTION__, '2.1', 'No longer used' );
}


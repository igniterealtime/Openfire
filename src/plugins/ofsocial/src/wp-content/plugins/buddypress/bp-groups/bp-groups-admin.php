<?php
/**
 * BuddyPress Groups component admin screen.
 *
 * Props to WordPress core for the Comments admin screen, and its contextual
 * help text, on which this implementation is heavily based.
 *
 * @since 1.7.0
 *
 * @package BuddyPress
 * @subpackage Groups
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

// Include WP's list table class.
if ( !class_exists( 'WP_List_Table' ) ) require( ABSPATH . 'wp-admin/includes/class-wp-list-table.php' );

// per_page screen option. Has to be hooked in extremely early.
if ( is_admin() && ! empty( $_REQUEST['page'] ) && 'bp-groups' == $_REQUEST['page'] )
	add_filter( 'set-screen-option', 'bp_groups_admin_screen_options', 10, 3 );

/**
 * Register the Groups component admin screen.
 *
 * @since 1.7.0
 */
function bp_groups_add_admin_menu() {

	// Add our screen
	$hook = add_menu_page(
		_x( 'Groups', 'Admin Groups page title', 'buddypress' ),
		_x( 'Groups', 'Admin Groups menu', 'buddypress' ),
		'bp_moderate',
		'bp-groups',
		'bp_groups_admin',
		'div'
	);

	// Hook into early actions to load custom CSS and our init handler.
	add_action( "load-$hook", 'bp_groups_admin_load' );
}
add_action( bp_core_admin_hook(), 'bp_groups_add_admin_menu' );

/**
 * Add groups component to custom menus array.
 *
 * This ensures that the Groups menu item appears in the proper order on the
 * main Dashboard menu.
 *
 * @since 1.7.0
 *
 * @param array $custom_menus Array of BP top-level menu items.
 *
 * @return array Menu item array, with Groups added.
 */
function bp_groups_admin_menu_order( $custom_menus = array() ) {
	array_push( $custom_menus, 'bp-groups' );
	return $custom_menus;
}
add_filter( 'bp_admin_menu_order', 'bp_groups_admin_menu_order' );

/**
 * Set up the Groups admin page.
 *
 * Loaded before the page is rendered, this function does all initial setup,
 * including: processing form requests, registering contextual help, and
 * setting up screen options.
 *
 * @since 1.7.0
 *
 * @global BP_Groups_List_Table $bp_groups_list_table Groups screen list table.
 */
function bp_groups_admin_load() {
	global $bp_groups_list_table;

	// Build redirection URL
	$redirect_to = remove_query_arg( array( 'action', 'action2', 'gid', 'deleted', 'error', 'updated', 'success_new', 'error_new', 'success_modified', 'error_modified' ), $_SERVER['REQUEST_URI'] );

	// Decide whether to load the dev version of the CSS and JavaScript
	$min = defined( 'SCRIPT_DEBUG' ) && SCRIPT_DEBUG ? '' : 'min.';

	$doaction = bp_admin_list_table_current_bulk_action();

	/**
	 * Fires at top of groups admin page.
	 *
	 * @since 1.7.0
	 *
	 * @param string $doaction Current $_GET action being performed in admin screen.
	 */
	do_action( 'bp_groups_admin_load', $doaction );

	// Edit screen
	if ( 'do_delete' == $doaction && ! empty( $_GET['gid'] ) ) {

		check_admin_referer( 'bp-groups-delete' );

		$group_ids = wp_parse_id_list( $_GET['gid'] );

		$count = 0;
		foreach ( $group_ids as $group_id ) {
			if ( groups_delete_group( $group_id ) ) {
				$count++;
			}
		}

		$redirect_to = add_query_arg( 'deleted', $count, $redirect_to );

		bp_core_redirect( $redirect_to );

	} elseif ( 'edit' == $doaction && ! empty( $_GET['gid'] ) ) {
		// columns screen option
		add_screen_option( 'layout_columns', array( 'default' => 2, 'max' => 2, ) );

		get_current_screen()->add_help_tab( array(
			'id'      => 'bp-group-edit-overview',
			'title'   => __( 'Overview', 'buddypress' ),
			'content' =>
				'<p>' . __( 'This page is a convenient way to edit the details associated with one of your groups.', 'buddypress' ) . '</p>' .
				'<p>' . __( 'The Name and Description box is fixed in place, but you can reposition all the other boxes using drag and drop, and can minimize or expand them by clicking the title bar of each box. Use the Screen Options tab to hide or unhide, or to choose a 1- or 2-column layout for this screen.', 'buddypress' ) . '</p>'
		) );

		// Help panel - sidebar links
		get_current_screen()->set_help_sidebar(
			'<p><strong>' . __( 'For more information:', 'buddypress' ) . '</strong></p>' .
			'<p><a href="https://buddypress.org/support">' . __( 'Support Forums', 'buddypress' ) . '</a></p>'
		);

		// Register metaboxes for the edit screen.
		add_meta_box( 'submitdiv', _x( 'Save', 'group admin edit screen', 'buddypress' ), 'bp_groups_admin_edit_metabox_status', get_current_screen()->id, 'side', 'high' );
		add_meta_box( 'bp_group_settings', _x( 'Settings', 'group admin edit screen', 'buddypress' ), 'bp_groups_admin_edit_metabox_settings', get_current_screen()->id, 'side', 'core' );
		add_meta_box( 'bp_group_add_members', _x( 'Add New Members', 'group admin edit screen', 'buddypress' ), 'bp_groups_admin_edit_metabox_add_new_members', get_current_screen()->id, 'normal', 'core' );
		add_meta_box( 'bp_group_members', _x( 'Manage Members', 'group admin edit screen', 'buddypress' ), 'bp_groups_admin_edit_metabox_members', get_current_screen()->id, 'normal', 'core' );

		/**
		 * Fires after the registration of all of the default group meta boxes.
		 *
		 * @since 1.7.0
		 */
		do_action( 'bp_groups_admin_meta_boxes' );

		// Enqueue JavaScript files
		wp_enqueue_script( 'postbox' );
		wp_enqueue_script( 'dashboard' );

	// Index screen
	} else {
		// Create the Groups screen list table
		$bp_groups_list_table = new BP_Groups_List_Table();

		// per_page screen option
		add_screen_option( 'per_page', array( 'label' => _x( 'Groups', 'Groups per page (screen options)', 'buddypress' )) );

		// Help panel - overview text
		get_current_screen()->add_help_tab( array(
			'id'      => 'bp-groups-overview',
			'title'   => __( 'Overview', 'buddypress' ),
			'content' =>
				'<p>' . __( 'You can manage groups much like you can manage comments and other content. This screen is customizable in the same ways as other management screens, and you can act on groups by using the on-hover action links or the Bulk Actions.', 'buddypress' ) . '</p>',
		) );

		get_current_screen()->add_help_tab( array(
			'id'      => 'bp-groups-overview-actions',
			'title'   => __( 'Group Actions', 'buddypress' ),
			'content' =>
				'<p>' . __( 'Clicking "Visit" will take you to the group&#8217;s public page. Use this link to see what the group looks like on the front end of your site.', 'buddypress' ) . '</p>' .
				'<p>' . __( 'Clicking "Edit" will take you to a Dashboard panel where you can manage various details about the group, such as its name and description, its members, and other settings.', 'buddypress' ) . '</p>' .
				'<p>' . __( 'If you click "Delete" under a specific group, or select a number of groups and then choose Delete from the Bulk Actions menu, you will be led to a page where you&#8217;ll be asked to confirm the permanent deletion of the group(s).', 'buddypress' ) . '</p>',
		) );

		// Help panel - sidebar links
		get_current_screen()->set_help_sidebar(
			'<p><strong>' . __( 'For more information:', 'buddypress' ) . '</strong></p>' .
			'<p>' . __( '<a href="https://buddypress.org/support/">Support Forums</a>', 'buddypress' ) . '</p>'
		);
	}

	$bp = buddypress();

	// Enqueue CSS and JavaScript
	wp_enqueue_script( 'bp_groups_admin_js', $bp->plugin_url . "bp-groups/admin/js/admin.{$min}js", array( 'jquery', 'wp-ajax-response', 'jquery-ui-autocomplete' ), bp_get_version(), true );
	wp_localize_script( 'bp_groups_admin_js', 'BP_Group_Admin', array(
		'add_member_placeholder' => __( 'Start typing a username to add a new member.', 'buddypress' ),
		'warn_on_leave'          => __( 'If you leave this page, you will lose any unsaved changes you have made to the group.', 'buddypress' ),
	) );
	wp_enqueue_style( 'bp_groups_admin_css', $bp->plugin_url . "bp-groups/admin/css/admin.{$min}css", array(), bp_get_version() );

	wp_style_add_data( 'bp_groups_admin_css', 'rtl', true );
	if ( $min ) {
		wp_style_add_data( 'bp_groups_admin_css', 'suffix', $min );
	}


	if ( $doaction && 'save' == $doaction ) {
		// Get group ID
		$group_id = isset( $_REQUEST['gid'] ) ? (int) $_REQUEST['gid'] : '';

		$redirect_to = add_query_arg( array(
			'gid'    => (int) $group_id,
			'action' => 'edit'
		), $redirect_to );

		// Check this is a valid form submission
		check_admin_referer( 'edit-group_' . $group_id );

		// Get the group from the database
		$group = groups_get_group( 'group_id=' . $group_id );

		// If the group doesn't exist, just redirect back to the index
		if ( empty( $group->slug ) ) {
			wp_redirect( $redirect_to );
			exit;
		}

		// Check the form for the updated properties

		// Store errors
		$error = 0;
		$success_new = $error_new = $success_modified = $error_modified = array();

		// Group name and description are handled with
		// groups_edit_base_group_details()
		if ( !groups_edit_base_group_details( $group_id, $_POST['bp-groups-name'], $_POST['bp-groups-description'], 0 ) ) {
			$error = $group_id;

			// using negative integers for different error messages... eek!
			if ( empty( $_POST['bp-groups-name'] ) && empty( $_POST['bp-groups-description'] ) ) {
				$error = -3;
			} elseif ( empty( $_POST['bp-groups-name'] ) ) {
				$error = -1;
			} elseif ( empty( $_POST['bp-groups-description'] ) ) {
				$error = -2;
			}
		}

		// Enable discussion forum
		$enable_forum   = ( isset( $_POST['group-show-forum'] ) ) ? 1 : 0;

		/**
		 * Filters the allowed status values for the group.
		 *
		 * @since 1.0.2
		 *
		 * @param array $value Array of allowed group statuses.
		 */
		$allowed_status = apply_filters( 'groups_allowed_status', array( 'public', 'private', 'hidden' ) );
		$status         = ( in_array( $_POST['group-status'], (array) $allowed_status ) ) ? $_POST['group-status'] : 'public';

		/**
		 * Filters the allowed invite status values for the group.
		 *
		 * @since 1.5.0
		 *
		 * @param array $value Array of allowed invite statuses.
		 */
		$allowed_invite_status = apply_filters( 'groups_allowed_invite_status', array( 'members', 'mods', 'admins' ) );
		$invite_status	       = in_array( $_POST['group-invite-status'], (array) $allowed_invite_status ) ? $_POST['group-invite-status'] : 'members';

		if ( !groups_edit_group_settings( $group_id, $enable_forum, $status, $invite_status ) ) {
			$error = $group_id;
		}

		// Process new members
		$user_names = array();

		if ( ! empty( $_POST['bp-groups-new-members'] ) ) {
			$user_names = array_merge( $user_names, explode( ',', $_POST['bp-groups-new-members'] ) );
		}

		if ( ! empty( $user_names ) ) {

			foreach( array_values( $user_names ) as $user_name ) {
				$un = trim( $user_name );

				// Make sure the user exists before attempting
				// to add to the group
				$user = get_user_by( 'slug', $un );

				if ( empty( $user ) ) {
					$error_new[] = $un;
				} else {
					if ( ! groups_join_group( $group_id, $user->ID ) ) {
						$error_new[]   = $un;
					} else {
						$success_new[] = $un;
					}
				}
			}
		}

		// Process member role changes
		if ( ! empty( $_POST['bp-groups-role'] ) && ! empty( $_POST['bp-groups-existing-role'] ) ) {

			// Before processing anything, make sure you're not
			// attempting to remove the all user admins
			$admin_count = 0;
			foreach ( (array) $_POST['bp-groups-role'] as $new_role ) {
				if ( 'admin' == $new_role ) {
					$admin_count++;
					break;
				}
			}

			if ( ! $admin_count ) {

				$redirect_to = add_query_arg( 'no_admins', 1, $redirect_to );
				$error = $group_id;

			} else {

				// Process only those users who have had their roles changed
				foreach ( (array) $_POST['bp-groups-role'] as $user_id => $new_role ) {
					$user_id = (int) $user_id;

					$existing_role = isset( $_POST['bp-groups-existing-role'][$user_id] ) ? $_POST['bp-groups-existing-role'][$user_id] : '';

					if ( $existing_role != $new_role ) {
						$result = false;

						switch ( $new_role ) {
							case 'mod' :
								// Admin to mod is a demotion. Demote to
								// member, then fall through
								if ( 'admin' == $existing_role ) {
									groups_demote_member( $user_id, $group_id );
								}

							case 'admin' :
								// If the user was banned, we must
								// unban first
								if ( 'banned' == $existing_role ) {
									groups_unban_member( $user_id, $group_id );
								}

								// At this point, each existing_role
								// is a member, so promote
								$result = groups_promote_member( $user_id, $group_id, $new_role );

								break;

							case 'member' :

								if ( 'admin' == $existing_role || 'mod' == $existing_role ) {
									$result = groups_demote_member( $user_id, $group_id );
								} elseif ( 'banned' == $existing_role ) {
									$result = groups_unban_member( $user_id, $group_id );
								}

								break;

							case 'banned' :

								$result = groups_ban_member( $user_id, $group_id );

								break;

							case 'remove' :

								$result = groups_remove_member( $user_id, $group_id );

								break;
						}

						// Store the success or failure
						if ( $result ) {
							$success_modified[] = $user_id;
						} else {
							$error_modified[]   = $user_id;
						}
					}
				}
			}
		}

		/**
		 * Fires before redirect so plugins can do something first on save action.
		 *
		 * @since 1.6.0
		 *
		 * @param int $group_id ID of the group being edited.
		 */
		do_action( 'bp_group_admin_edit_after', $group_id );

		// Create the redirect URL

		if ( $error ) {
			// This means there was an error updating group details
			$redirect_to = add_query_arg( 'error', (int) $error, $redirect_to );
		} else {
			// Group details were update successfully
			$redirect_to = add_query_arg( 'updated', 1, $redirect_to );
		}

		if ( !empty( $success_new ) ) {
			$success_new = implode( ',', array_filter( $success_new, 'urlencode' ) );
			$redirect_to = add_query_arg( 'success_new', $success_new, $redirect_to );
		}

		if ( !empty( $error_new ) ) {
			$error_new = implode( ',', array_filter( $error_new, 'urlencode' ) );
			$redirect_to = add_query_arg( 'error_new', $error_new, $redirect_to );
		}

		if ( !empty( $success_modified ) ) {
			$success_modified = implode( ',', array_filter( $success_modified, 'urlencode' ) );
			$redirect_to = add_query_arg( 'success_modified', $success_modified, $redirect_to );
		}

		if ( !empty( $error_modified ) ) {
			$error_modified = implode( ',', array_filter( $error_modified, 'urlencode' ) );
			$redirect_to = add_query_arg( 'error_modified', $error_modified, $redirect_to );
		}

		/**
		 * Filters the URL to redirect to after successfully editing a group.
		 *
		 * @since 1.7.0
		 *
		 * @param string $redirect_to URL to redirect user to.
		 */
		wp_redirect( apply_filters( 'bp_group_admin_edit_redirect', $redirect_to ) );
		exit;


	// If a referrer and a nonce is supplied, but no action, redirect back.
	} elseif ( ! empty( $_GET['_wp_http_referer'] ) ) {
		wp_redirect( remove_query_arg( array( '_wp_http_referer', '_wpnonce' ), stripslashes( $_SERVER['REQUEST_URI'] ) ) );
		exit;
	}
}

/**
 * Handle save/update of screen options for the Groups component admin screen.
 *
 * @since 1.7.0
 *
 * @param string $value     Will always be false unless another plugin filters it first.
 * @param string $option    Screen option name.
 * @param string $new_value Screen option form value.
 *
 * @return string Option value. False to abandon update.
 */
function bp_groups_admin_screen_options( $value, $option, $new_value ) {
	if ( 'toplevel_page_bp_groups_per_page' != $option && 'toplevel_page_bp_groups_network_per_page' != $option )
		return $value;

	// Per page
	$new_value = (int) $new_value;
	if ( $new_value < 1 || $new_value > 999 )
		return $value;

	return $new_value;
}

/**
 * Select the appropriate Groups admin screen, and output it.
 *
 * @since 1.7.0
 */
function bp_groups_admin() {
	// Decide whether to load the index or edit screen
	$doaction = bp_admin_list_table_current_bulk_action();

	// Display the single group edit screen
	if ( 'edit' == $doaction && ! empty( $_GET['gid'] ) ) {
		bp_groups_admin_edit();

	// Display the group deletion confirmation screen
	} elseif ( 'delete' == $doaction && ! empty( $_GET['gid'] ) ) {
		bp_groups_admin_delete();

	// Otherwise, display the groups index screen
	} else {
		bp_groups_admin_index();
	}
}

/**
 * Display the single groups edit screen.
 *
 * @since 1.7.0
 */
function bp_groups_admin_edit() {

	if ( ! current_user_can( 'bp_moderate' ) )
		die( '-1' );

	$messages = array();

	// If the user has just made a change to a group, build status messages
	if ( !empty( $_REQUEST['no_admins'] ) || ! empty( $_REQUEST['error'] ) || ! empty( $_REQUEST['updated'] ) || ! empty( $_REQUEST['error_new'] ) || ! empty( $_REQUEST['success_new'] ) || ! empty( $_REQUEST['error_modified'] ) || ! empty( $_REQUEST['success_modified'] ) ) {
		$no_admins        = ! empty( $_REQUEST['no_admins']        ) ? 1                                             : 0;
		$errors           = ! empty( $_REQUEST['error']            ) ? $_REQUEST['error']                            : '';
		$updated          = ! empty( $_REQUEST['updated']          ) ? $_REQUEST['updated']                          : '';
		$error_new        = ! empty( $_REQUEST['error_new']        ) ? explode( ',', $_REQUEST['error_new'] )        : array();
		$success_new      = ! empty( $_REQUEST['success_new']      ) ? explode( ',', $_REQUEST['success_new'] )      : array();
		$error_modified   = ! empty( $_REQUEST['error_modified']   ) ? explode( ',', $_REQUEST['error_modified'] )   : array();
		$success_modified = ! empty( $_REQUEST['success_modified'] ) ? explode( ',', $_REQUEST['success_modified'] ) : array();

		if ( ! empty( $no_admins ) ) {
			$messages[] = __( 'You cannot remove all administrators from a group.', 'buddypress' );
		}

		if ( ! empty( $errors ) ) {
			switch ( $errors ) {
				case -1 :
					$messages[] = __( 'Group name cannot be empty.', 'buddypress' );
					break;

				case -2 :
					$messages[] = __( 'Group description cannot be empty.', 'buddypress' );
					break;

				case -3 :
					$messages[] = __( 'Group name and description cannot be empty.', 'buddypress' );
					break;

				default :
					$messages[] = __( 'An error occurred when trying to update your group details.', 'buddypress' );
					break;
			}

		} elseif ( ! empty( $updated ) ) {
			$messages[] = __( 'The group has been updated successfully.', 'buddypress' );
		}

		if ( ! empty( $error_new ) ) {
			$messages[] = sprintf( __( 'The following users could not be added to the group: %s', 'buddypress' ), '<em>' . esc_html( implode( ', ', $error_new ) ) . '</em>' );
		}

		if ( ! empty( $success_new ) ) {
			$messages[] = sprintf( __( 'The following users were successfully added to the group: %s', 'buddypress' ), '<em>' . esc_html( implode( ', ', $success_new ) ) . '</em>' );
		}

		if ( ! empty( $error_modified ) ) {
			$error_modified = bp_groups_admin_get_usernames_from_ids( $error_modified );
			$messages[] = sprintf( __( 'An error occurred when trying to modify the following members: %s', 'buddypress' ), '<em>' . esc_html( implode( ', ', $error_modified ) ) . '</em>' );
		}

		if ( ! empty( $success_modified ) ) {
			$success_modified = bp_groups_admin_get_usernames_from_ids( $success_modified );
			$messages[] = sprintf( __( 'The following members were successfully modified: %s', 'buddypress' ), '<em>' . esc_html( implode( ', ', $success_modified ) ) . '</em>' );
		}
	}

	$is_error = ! empty( $no_admins ) || ! empty( $errors ) || ! empty( $error_new ) || ! empty( $error_modified );

	// Get the group from the database
	$group      = groups_get_group( 'group_id=' . (int) $_GET['gid'] );

	/** This filter is documented in bp-groups/bp-groups-template.php */
	$group_name = isset( $group->name ) ? apply_filters( 'bp_get_group_name', $group->name ) : '';

	// Construct URL for form
	$form_url = remove_query_arg( array( 'action', 'deleted', 'no_admins', 'error', 'error_new', 'success_new', 'error_modified', 'success_modified' ), $_SERVER['REQUEST_URI'] );
	$form_url = add_query_arg( 'action', 'save', $form_url );

	/**
	 * Fires before the display of the edit form.
	 *
	 * Useful for plugins to modify the group before display.
	 *
	 * @since 1.7.0
	 *
	 * @param BP_Groups_Group $this Instance of the current group being edited. Passed by reference.
	 */
	do_action_ref_array( 'bp_groups_admin_edit', array( &$group ) ); ?>

	<div class="wrap">
		<h2><?php _e( 'Edit Group', 'buddypress' ); ?>

			<?php if ( is_user_logged_in() && bp_user_can_create_groups() ) : ?>
				<a class="add-new-h2" href="<?php echo trailingslashit( bp_get_groups_directory_permalink() . 'create' ); ?>"><?php _e( 'Add New', 'buddypress' ); ?></a>
			<?php endif; ?>

		</h2>

		<?php // If the user has just made a change to an group, display the status messages ?>
		<?php if ( !empty( $messages ) ) : ?>
			<div id="moderated" class="<?php echo ( $is_error ) ? 'error' : 'updated'; ?>"><p><?php echo implode( "</p><p>", $messages ); ?></p></div>
		<?php endif; ?>

		<?php if ( ! empty( $group ) ) : ?>

			<form action="<?php echo esc_url( $form_url ); ?>" id="bp-groups-edit-form" method="post">
				<div id="poststuff">

					<div id="post-body" class="metabox-holder columns-<?php echo 1 == get_current_screen()->get_columns() ? '1' : '2'; ?>">
						<div id="post-body-content">
							<div id="postdiv">
								<div id="bp_groups_name" class="postbox">
									<h3><?php _e( 'Name and Description', 'buddypress' ); ?></h3>
									<div class="inside">
										<label for="bp-groups-name" class="screen-reader-text"><?php _e( 'Group Name', 'buddypress' ); ?></label>
										<input type="text" name="bp-groups-name" id="bp-groups-name" value="<?php echo esc_attr( stripslashes( $group_name ) ) ?>" />
										<div id="bp-groups-permalink-box">
											<strong><?php esc_html_e( 'Permalink:', 'buddypress' ) ?></strong> <span id="sample-permalink"><?php bp_group_permalink( $group ) ?></span> <a href="<?php echo bp_group_permalink( $group ) ?>" class="button button-small" id="bp-groups-visit-group"><?php esc_html_e( 'Visit Group', 'buddypress' ) ?></a>
										</div>

										<label for="bp-groups-description" class="screen-reader-text"><?php _e( 'Group Description', 'buddypress' ); ?></label>
										<?php wp_editor( stripslashes( $group->description ), 'bp-groups-description', array( 'media_buttons' => false, 'teeny' => true, 'textarea_rows' => 5, 'quicktags' => array( 'buttons' => 'strong,em,link,block,del,ins,img,code,spell,close' ) ) ); ?>
									</div>
								</div>
							</div>
						</div><!-- #post-body-content -->

						<div id="postbox-container-1" class="postbox-container">
							<?php do_meta_boxes( get_current_screen()->id, 'side', $group ); ?>
						</div>

						<div id="postbox-container-2" class="postbox-container">
							<?php do_meta_boxes( get_current_screen()->id, 'normal', $group ); ?>
							<?php do_meta_boxes( get_current_screen()->id, 'advanced', $group ); ?>
						</div>
					</div><!-- #post-body -->

				</div><!-- #poststuff -->
				<?php wp_nonce_field( 'closedpostboxes', 'closedpostboxesnonce', false ); ?>
				<?php wp_nonce_field( 'meta-box-order', 'meta-box-order-nonce', false ); ?>
				<?php wp_nonce_field( 'edit-group_' . $group->id ); ?>
			</form>

		<?php else : ?>
			<p><?php printf( __( 'No group found with this ID. <a href="%s">Go back and try again</a>.', 'buddypress' ), esc_url( bp_get_admin_url( 'admin.php?page=bp-groups' ) ) ); ?></p>
		<?php endif; ?>

	</div><!-- .wrap -->

<?php
}

/**
 * Display the Group delete confirmation screen.
 *
 * We include a separate confirmation because group deletion is truly
 * irreversible.
 *
 * @since 1.7.0
 */
function bp_groups_admin_delete() {

	if ( ! bp_current_user_can( 'bp_moderate' ) ) {
		die( '-1' );
	}

	$group_ids = isset( $_REQUEST['gid'] ) ? $_REQUEST['gid'] : 0;
	if ( ! is_array( $group_ids ) ) {
		$group_ids = explode( ',', $group_ids );
	}
	$group_ids = wp_parse_id_list( $group_ids );
	$groups    = groups_get_groups( array(
		'include'     => $group_ids,
		'show_hidden' => true,
		'per_page'    => null, // Return all results
	) );

	// Create a new list of group ids, based on those that actually exist
	$gids = array();
	foreach ( $groups['groups'] as $group ) {
		$gids[] = $group->id;
	}

	$base_url  = remove_query_arg( array( 'action', 'action2', 'paged', 's', '_wpnonce', 'gid' ), $_SERVER['REQUEST_URI'] ); ?>

	<div class="wrap">
		<h2><?php _e( 'Delete Groups', 'buddypress' ) ?></h2>
		<p><?php _e( 'You are about to delete the following groups:', 'buddypress' ) ?></p>

		<ul class="bp-group-delete-list">
		<?php foreach ( $groups['groups'] as $group ) : ?>
			<li><?php echo esc_html( $group->name ) ?></li>
		<?php endforeach; ?>
		</ul>

		<p><strong><?php _e( 'This action cannot be undone.', 'buddypress' ) ?></strong></p>

		<a class="button-primary" href="<?php echo esc_url( wp_nonce_url( add_query_arg( array( 'action' => 'do_delete', 'gid' => implode( ',', $gids ) ), $base_url ), 'bp-groups-delete' ) ); ?>"><?php _e( 'Delete Permanently', 'buddypress' ) ?></a>
		<a class="button" href="<?php echo esc_attr( $base_url ); ?>"><?php _e( 'Cancel', 'buddypress' ) ?></a>
	</div>

	<?php
}

/**
 * Display the Groups admin index screen.
 *
 * This screen contains a list of all BuddyPress groups.
 *
 * @since 1.7.0
 *
 * @global BP_Groups_List_Table $bp_groups_list_table Group screen list table.
 * @global string $plugin_page Currently viewed plugin page.
 */
function bp_groups_admin_index() {
	global $bp_groups_list_table, $plugin_page;

	$messages = array();

	// If the user has just made a change to a group, build status messages
	if ( ! empty( $_REQUEST['deleted'] ) ) {
		$deleted  = ! empty( $_REQUEST['deleted'] ) ? (int) $_REQUEST['deleted'] : 0;

		if ( $deleted > 0 ) {
			$messages[] = sprintf( _n( '%s group has been permanently deleted.', '%s groups have been permanently deleted.', $deleted, 'buddypress' ), number_format_i18n( $deleted ) );
		}
	}

	// Prepare the group items for display
	$bp_groups_list_table->prepare_items();

	/**
	 * Fires before the display of messages for the edit form.
	 *
	 * Useful for plugins to modify the messages before display.
	 *
	 * @since 1.7.0
	 *
	 * @param array $messages Array of messages to be displayed.
	 */
	do_action( 'bp_groups_admin_index', $messages ); ?>

	<div class="wrap">
		<h2>
			<?php _e( 'Groups', 'buddypress' ); ?>

			<?php if ( is_user_logged_in() && bp_user_can_create_groups() ) : ?>
				<a class="add-new-h2" href="<?php echo trailingslashit( bp_get_groups_directory_permalink() . 'create' ); ?>"><?php _e( 'Add New', 'buddypress' ); ?></a>
			<?php endif; ?>

			<?php if ( !empty( $_REQUEST['s'] ) ) : ?>
				<span class="subtitle"><?php printf( __( 'Search results for &#8220;%s&#8221;', 'buddypress' ), wp_html_excerpt( esc_html( stripslashes( $_REQUEST['s'] ) ), 50 ) ); ?></span>
			<?php endif; ?>
		</h2>

		<?php // If the user has just made a change to an group, display the status messages ?>
		<?php if ( !empty( $messages ) ) : ?>
			<div id="moderated" class="<?php echo ( ! empty( $_REQUEST['error'] ) ) ? 'error' : 'updated'; ?>"><p><?php echo implode( "<br/>\n", $messages ); ?></p></div>
		<?php endif; ?>

		<?php // Display each group on its own row ?>
		<?php $bp_groups_list_table->views(); ?>

		<form id="bp-groups-form" action="" method="get">
			<?php $bp_groups_list_table->search_box( __( 'Search all Groups', 'buddypress' ), 'bp-groups' ); ?>
			<input type="hidden" name="page" value="<?php echo esc_attr( $plugin_page ); ?>" />
			<?php $bp_groups_list_table->display(); ?>
		</form>

	</div>

<?php
}

/**
 * Markup for the single group's Settings metabox.
 *
 * @since 1.7.0
 *
 * @param object $item Information about the current group.
 */
function bp_groups_admin_edit_metabox_settings( $item ) {

	$invite_status = groups_get_groupmeta( $item->id, 'invite_status' ); ?>

	<?php if ( bp_is_active( 'forums' ) ) : ?>
		<div class="bp-groups-settings-section" id="bp-groups-settings-section-forum">
			<label for="group-show-forum"><input type="checkbox" name="group-show-forum" id="group-show-forum" <?php checked( $item->enable_forum ) ?> /> <?php _e( 'Enable discussion forum', 'buddypress' ) ?><br />
		</div>
	<?php endif; ?>

	<div class="bp-groups-settings-section" id="bp-groups-settings-section-status">
		<fieldset>
			<legend><?php _e( 'Privacy', 'buddypress' ); ?></legend>

			<ul>
				<li><input type="radio" name="group-status" id="bp-group-status-public" value="public" <?php checked( $item->status, 'public' ) ?> /><label for="bp-group-status-public"><?php _e( 'Public', 'buddypress' ) ?></label></li>
				<li><input type="radio" name="group-status" id="bp-group-status-private" value="private" <?php checked( $item->status, 'private' ) ?> /><label for="bp-group-status-private"><?php _e( 'Private', 'buddypress' ) ?></label></li>
				<li><input type="radio" name="group-status" id="bp-group-status-hidden" value="hidden" <?php checked( $item->status, 'hidden' ) ?> /><label for="bp-group-status-hidden"><?php _e( 'Hidden', 'buddypress' ) ?></label></li>
			</ul>
		</fieldset>
	</div>

	<div class="bp-groups-settings-section" id="bp-groups-settings-section-invite-status">
		<fieldset>
			<legend><?php _e( 'Who can invite others to this group?', 'buddypress' ); ?></legend>

			<ul>
				<li><input type="radio" name="group-invite-status" id="bp-group-invite-status-members" value="members" <?php checked( $invite_status, 'members' ) ?> /><label for="bp-group-invite-status-members"><?php _e( 'All group members', 'buddypress' ) ?></label></li>
				<li><input type="radio" name="group-invite-status" id="bp-group-invite-status-mods" value="mods" <?php checked( $invite_status, 'mods' ) ?> /><label for="bp-group-invite-status-mods"><?php _e( 'Group admins and mods only', 'buddypress' ) ?></label></li>
				<li><input type="radio" name="group-invite-status" id="bp-group-invite-status-admins" value="admins" <?php checked( $invite_status, 'admins' ) ?> /><label for="bp-group-invite-status-admins"><?php _e( 'Group admins only', 'buddypress' ) ?></label></li>
			</ul>
		</fieldset>
	</div>

<?php
}

/**
 * Output the markup for a single group's Add New Members metabox.
 *
 * @since 1.7.0
 */
function bp_groups_admin_edit_metabox_add_new_members( $item ) {
	?>

	<label for="bp-groups-new-members" class="screen-reader-text"><?php _e( 'Add new members', 'buddypress' ); ?></label>
	<input name="bp-groups-new-members" id="bp-groups-new-members" class="bp-suggest-user" placeholder="<?php esc_attr_e( 'Enter a comma-separated list of user logins.', 'buddypress' ) ?>" />
	<ul id="bp-groups-new-members-list"></ul>
	<?php
}

/**
 * Renders the Members metabox on single group pages.
 *
 * @since 1.7.0
 *
 * @param BP_Groups_Group $item The BP_Groups_Group object for the current group.
 */
function bp_groups_admin_edit_metabox_members( $item ) {

	// Pull up a list of group members, so we can separate out the types
	// We'll also keep track of group members here to place them into a
	// JavaScript variable, which will help with group member autocomplete
	$members = array(
		'admin'  => array(),
		'mod'    => array(),
		'member' => array(),
		'banned' => array(),
	);

	$pagination = array(
		'admin'  => array(),
		'mod'    => array(),
		'member' => array(),
		'banned' => array(),
	);

	foreach ( $members as $type => &$member_type_users ) {
		$page_qs_key       = $type . '_page';
		$current_type_page = isset( $_GET[ $page_qs_key ] ) ? absint( $_GET[ $page_qs_key ] ) : 1;
		$member_type_query = new BP_Group_Member_Query( array(
			'group_id'   => $item->id,
			'group_role' => array( $type ),
			'type'       => 'alphabetical',
			'per_page'   => 10,
			'page'       => $current_type_page,
		) );

		$member_type_users   = $member_type_query->results;
		$pagination[ $type ] = bp_groups_admin_create_pagination_links( $member_type_query, $type );
	}

	// Echo out the JavaScript variable
	echo '<script type="text/javascript">var group_id = "' . esc_js( $item->id ) . '";</script>';

	// Loop through each member type
	foreach ( $members as $member_type => $type_users ) : ?>

		<div class="bp-groups-member-type" id="bp-groups-member-type-<?php echo esc_attr( $member_type ) ?>">

			<h4><?php switch ( $member_type ) :
					case 'admin'  : esc_html_e( 'Administrators', 'buddypress' ); break;
					case 'mod'    : esc_html_e( 'Moderators',     'buddypress' ); break;
					case 'member' : esc_html_e( 'Members',        'buddypress' ); break;
					case 'banned' : esc_html_e( 'Banned Members', 'buddypress' ); break;
			endswitch; ?></h4>

			<div class="bp-group-admin-pagination table-top">
				<?php echo $pagination[ $member_type ] ?>
			</div>

		<?php if ( !empty( $type_users ) ) : ?>

			<table class="widefat bp-group-members">
				<thead>
					<tr>
						<th scope="col" class="uid-column"><?php _ex( 'ID', 'Group member user_id in group admin', 'buddypress' ); ?></th>
						<th scope="col" class="uname-column"><?php _ex( 'Name', 'Group member name in group admin', 'buddypress' ); ?></th>
						<th scope="col" class="urole-column"><?php _ex( 'Group Role', 'Group member role in group admin', 'buddypress' ); ?></th>
					</tr>
				</thead>

				<tbody>

				<?php foreach ( $type_users as $type_user ) : ?>
					<tr>
						<th scope="row" class="uid-column"><?php echo esc_html( $type_user->ID ); ?></th>

						<td class="uname-column">
							<a style="float: left;" href="<?php echo bp_core_get_user_domain( $type_user->ID ); ?>"><?php echo bp_core_fetch_avatar( array(
								'item_id' => $type_user->ID,
								'width'   => '32',
								'height'  => '32'
							) ); ?></a>

							<span style="margin: 8px; float: left;"><?php echo bp_core_get_userlink( $type_user->ID ); ?></span>
						</td>

						<td class="urole-column">
							<label for="bp-groups-role-<?php echo esc_attr( $type_user->ID ); ?>" class="screen-reader-text"><?php _e( 'Select group role for member', 'buddypress' ); ?></label>
							<select class="bp-groups-role" id="bp-groups-role-<?php echo esc_attr( $type_user->ID ); ?>" name="bp-groups-role[<?php echo esc_attr( $type_user->ID ); ?>]">
								<optgroup label="<?php esc_attr_e( 'Roles', 'buddypress' ); ?>">
									<option class="admin"  value="admin"  <?php selected( 'admin',  $member_type ); ?>><?php esc_html_e( 'Administrator', 'buddypress' ); ?></option>
									<option class="mod"    value="mod"    <?php selected( 'mod',    $member_type ); ?>><?php esc_html_e( 'Moderator',     'buddypress' ); ?></option>
									<option class="member" value="member" <?php selected( 'member', $member_type ); ?>><?php esc_html_e( 'Member',        'buddypress' ); ?></option>
									<?php if ( 'banned' === $member_type ) : ?>
									<option class="banned" value="banned" <?php selected( 'banned', $member_type ); ?>><?php esc_html_e( 'Banned',        'buddypress' ); ?></option>
									<?php endif; ?>
								</optgroup>
								<optgroup label="<?php esc_attr_e( 'Actions', 'buddypress' ); ?>">
									<option class="remove" value="remove"><?php esc_html_e( 'Remove', 'buddypress' ); ?></option>
									<?php if ( 'banned' !== $member_type ) : ?>
										<option class="banned" value="banned"><?php esc_html_e( 'Ban', 'buddypress' ); ?></option>
									<?php endif; ?>
								</optgroup>
							</select>

							<?php
							/**
							 * Store the current role for this user,
							 * so we can easily detect changes.
							 *
							 * @todo remove this, and do database detection on save
							 */
							?>
							<input type="hidden" name="bp-groups-existing-role[<?php echo esc_attr( $type_user->ID ); ?>]" value="<?php echo esc_attr( $member_type ); ?>" />
						</td>
					</tr>

					<?php if ( has_filter( 'bp_groups_admin_manage_member_row' ) ) : ?>
						<tr>
							<td colspan="3">
								<?php

								/**
								 * Fires after the listing of a single row for members in a group on the group edit screen.
								 *
								 * @since 1.8.0
								 *
								 * @param int             $ID   ID of the user being rendered.
								 * @param BP_Groups_Group $item Object for the current group.
								 */
								do_action( 'bp_groups_admin_manage_member_row', $type_user->ID, $item ); ?>
							</td>
						</tr>
					<?php endif; ?>

				<?php endforeach; ?>

				</tbody>
			</table>

			<div class="bp-group-admin-pagination table-bottom">
				<?php echo $pagination[ $member_type ]; ?>
			</div>

		<?php else : ?>

			<p class="bp-groups-no-members description"><?php esc_html_e( 'No members of this type', 'buddypress' ); ?></p>

		<?php endif; ?>

		</div><!-- .bp-groups-member-type -->

	<?php endforeach;
}

/**
 * Renders the Status metabox for the Groups admin edit screen.
 *
 * @since 1.7.0
 *
 * @param object $item Information about the currently displayed group.
 */
function bp_groups_admin_edit_metabox_status( $item ) {
	$base_url = add_query_arg( array(
		'page' => 'bp-groups',
		'gid'  => $item->id
	), bp_get_admin_url( 'admin.php' ) ); ?>

	<div id="submitcomment" class="submitbox">
		<div id="major-publishing-actions">
			<div id="delete-action">
				<a class="submitdelete deletion" href="<?php echo esc_url( wp_nonce_url( add_query_arg( 'action', 'delete', $base_url ), 'bp-groups-delete' ) ); ?>"><?php _e( 'Delete Group', 'buddypress' ) ?></a>
			</div>

			<div id="publishing-action">
				<?php submit_button( __( 'Save Changes', 'buddypress' ), 'primary', 'save', false ); ?>
			</div>
			<div class="clear"></div>
		</div><!-- #major-publishing-actions -->
	</div><!-- #submitcomment -->

<?php
}

/**
 * Create pagination links out of a BP_Group_Member_Query.
 *
 * This function is intended to create pagination links for use under the
 * Manage Members section of the Groups Admin Dashboard pages. It is a stopgap
 * measure until a more general pagination solution is in place for BuddyPress.
 * Plugin authors should not use this function, as it is likely to be
 * deprecated soon.
 *
 * @since 1.8.0
 *
 * @param BP_Group_Member_Query $query       A BP_Group_Member_Query object.
 * @param string                $member_type member|mod|admin|banned.
 *
 * @return string Pagination links HTML.
 */
function bp_groups_admin_create_pagination_links( BP_Group_Member_Query $query, $member_type ) {
	$pagination = '';

	if ( ! in_array( $member_type, array( 'admin', 'mod', 'member', 'banned' ) ) ) {
		return $pagination;
	}

	// The key used to paginate this member type in the $_GET global
	$qs_key = $member_type . '_page';
	$url_base = remove_query_arg( array( $qs_key, 'updated', 'success_modified' ), $_SERVER['REQUEST_URI'] );

	$page     = isset( $_GET[ $qs_key ] ) ? absint( $_GET[ $qs_key ] ) : 1;
	$per_page = 10; // @todo Make this customizable?

	// Don't show anything if there's no pagination
	if ( 1 === $page && $query->total_users <= $per_page ) {
		return $pagination;
	}

	$current_page_start = ( ( $page - 1 ) * $per_page ) + 1;
	$current_page_end   = $page * $per_page > intval( $query->total_users ) ? $query->total_users : $page * $per_page;

	$pag_links = paginate_links( array(
		'base'      => add_query_arg( $qs_key, '%#%', $url_base ),
		'format'    => '',
		'prev_text' => __( '&laquo;', 'buddypress' ),
		'next_text' => __( '&raquo;', 'buddypress' ),
		'total'     => ceil( $query->total_users / $per_page ),
		'current'   => $page,
	) );

	if ( 1 == $query->total_users ) {
		$viewing_text = __( 'Viewing 1 member', 'buddypress' );
	} else {
		$viewing_text = sprintf(
			_n( 'Viewing %1$s - %2$s of %3$s member', 'Viewing %1$s - %2$s of %3$s members', $query->total_users, 'buddypress' ),
			bp_core_number_format( $current_page_start ),
			bp_core_number_format( $current_page_end ),
			bp_core_number_format( $query->total_users )
		);
	}

	$pagination .= '<span class="bp-group-admin-pagination-viewing">' . $viewing_text . '</span>';
	$pagination .= '<span class="bp-group-admin-pagination-links">' . $pag_links . '</span>';

	return $pagination;
}

/**
 * Get a set of usernames corresponding to a set of user IDs.
 *
 * @since 1.7.0
 *
 * @param array $user_ids Array of user IDs.
 *
 * @return array Array of user_logins corresponding to $user_ids.
 */
function bp_groups_admin_get_usernames_from_ids( $user_ids = array() ) {

	$usernames = array();
	$users     = new WP_User_Query( array( 'blog_id' => 0, 'include' => $user_ids ) );

	foreach ( (array) $users->results as $user ) {
		$usernames[] = $user->user_login;
	}

	return $usernames;
}

/**
 * AJAX handler for group member autocomplete requests.
 *
 * @since 1.7.0
 */
function bp_groups_admin_autocomplete_handler() {

	// Bail if user user shouldn't be here, or is a large network
	if ( ! current_user_can( 'bp_moderate' ) || ( is_multisite() && wp_is_large_network( 'users' ) ) ) {
		wp_die( -1 );
	}

	$term     = isset( $_GET['term'] )     ? sanitize_text_field( $_GET['term'] ) : '';
	$group_id = isset( $_GET['group_id'] ) ? absint( $_GET['group_id'] )          : 0;

	if ( ! $term || ! $group_id ) {
		wp_die( -1 );
	}

	$suggestions = bp_core_get_suggestions( array(
		'group_id' => -$group_id,  // A negative value will exclude this group's members from the suggestions.
		'limit'    => 10,
		'term'     => $term,
		'type'     => 'members',
	) );

	$matches = array();

	if ( $suggestions && ! is_wp_error( $suggestions ) ) {
		foreach ( $suggestions as $user ) {

			$matches[] = array(
				// translators: 1: user_login, 2: user_email
				'label' => sprintf( __( '%1$s (%2$s)', 'buddypress' ), $user->name, $user->ID ),
				'value' => $user->ID,
			);
		}
	}

	wp_die( json_encode( $matches ) );
}
add_action( 'wp_ajax_bp_group_admin_member_autocomplete', 'bp_groups_admin_autocomplete_handler' );

/**
 * List table class for the Groups component admin page.
 *
 * @since 1.7.0
 */
class BP_Groups_List_Table extends WP_List_Table {

	/**
	 * The type of view currently being displayed.
	 *
	 * e.g. "All", "Pending", "Approved", "Spam"...
	 *
	 * @since 1.7.0
	 *
	 * @var string
	 */
	public $view = 'all';

	/**
	 * Group counts for each group type.
	 *
	 * @since 1.7.0
	 *
	 * @var int
	 */
	public $group_counts = 0;

	/**
	 * Multidimensional array of group visibility types and their groups.
	 *
	 * @link https://buddypress.trac.wordpress.org/ticket/6277
	 * @var array
	 */
	public $group_type_ids = array();

	/**
	 * Constructor
	 *
	 * @since 1.7.0
	 */
	public function __construct() {

		// Define singular and plural labels, as well as whether we support AJAX.
		parent::__construct( array(
			'ajax'     => false,
			'plural'   => 'groups',
			'singular' => 'group',
		) );
	}

	/**
	 * Set up items for display in the list table.
	 *
	 * Handles filtering of data, sorting, pagination, and any other data
	 * manipulation required prior to rendering.
	 *
	 * @since 1.7.0
	 */
	public function prepare_items() {
		global $groups_template;

		$screen = get_current_screen();

		// Option defaults
		$include_id   = false;
		$search_terms = false;

		// Set current page
		$page = $this->get_pagenum();

		// Set per page from the screen options
		$per_page = $this->get_items_per_page( str_replace( '-', '_', "{$screen->id}_per_page" ) );

		// Sort order.
		$order = 'DESC';
		if ( !empty( $_REQUEST['order'] ) ) {
			$order = ( 'desc' == strtolower( $_REQUEST['order'] ) ) ? 'DESC' : 'ASC';
		}

		// Order by - default to newest
		$orderby = 'last_activity';
		if ( ! empty( $_REQUEST['orderby'] ) ) {
			switch ( $_REQUEST['orderby'] ) {
				case 'name' :
					$orderby = 'name';
					break;
				case 'id' :
					$orderby = 'date_created';
					break;
				case 'members' :
					$orderby = 'total_member_count';
					break;
				case 'last_active' :
					$orderby = 'last_activity';
					break;
			}
		}

		// Are we doing a search?
		if ( !empty( $_REQUEST['s'] ) )
			$search_terms = $_REQUEST['s'];

		// Check if user has clicked on a specific group (if so, fetch only that group).
		if ( !empty( $_REQUEST['gid'] ) )
			$include_id = (int) $_REQUEST['gid'];

		// Set the current view
		if ( isset( $_GET['group_status'] ) && in_array( $_GET['group_status'], array( 'public', 'private', 'hidden' ) ) ) {
			$this->view = $_GET['group_status'];
		}

		// We'll use the ids of group types for the 'include' param
		$this->group_type_ids = BP_Groups_Group::get_group_type_ids();

		// Pass a dummy array if there are no groups of this type
		$include = false;
		if ( 'all' != $this->view && isset( $this->group_type_ids[ $this->view ] ) ) {
			$include = ! empty( $this->group_type_ids[ $this->view ] ) ? $this->group_type_ids[ $this->view ] : array( 0 );
		}

		// Get group type counts for display in the filter tabs
		$this->group_counts = array();
		foreach ( $this->group_type_ids as $group_type => $group_ids ) {
			$this->group_counts[ $group_type ] = count( $group_ids );
		}

		// If we're viewing a specific group, flatten all activities into a single array.
		if ( $include_id ) {
			$groups = array( (array) groups_get_group( 'group_id=' . $include_id ) );
		} else {
			$groups_args = array(
				'include'  => $include,
				'per_page' => $per_page,
				'page'     => $page,
				'orderby'  => $orderby,
				'order'    => $order
			);

			$groups = array();
			if ( bp_has_groups( $groups_args ) ) {
				while ( bp_groups() ) {
					bp_the_group();
					$groups[] = (array) $groups_template->group;
				}
			}
		}

		// Set raw data to display
		$this->items = $groups;

		// Store information needed for handling table pagination
		$this->set_pagination_args( array(
			'per_page'    => $per_page,
			'total_items' => $groups_template->total_group_count,
			'total_pages' => ceil( $groups_template->total_group_count / $per_page )
		) );
	}

	/**
	 * Get an array of all the columns on the page.
	 *
	 * @since 1.7.0
	 *
	 * @return array Array of column headers.
	 */
	public function get_column_info() {
		$this->_column_headers = array(
			$this->get_columns(),
			array(),
			$this->get_sortable_columns(),
			$this->get_default_primary_column_name(),
		);

		return $this->_column_headers;
	}

	/**
	 * Get name of default primary column
	 *
	 * @since 2.3.3
	 *
	 * @return string
	 */
	protected function get_default_primary_column_name() {
		// comment column is mapped to Group's name
		return 'comment';
	}

	/**
	 * Display a message on screen when no items are found ("No groups found").
	 *
	 * @since 1.7.0
	 */
	public function no_items() {
		_e( 'No groups found.', 'buddypress' );
	}

	/**
	 * Output the Groups data table.
	 *
	 * @since 1.7.0
	*/
	public function display() {
		$this->display_tablenav( 'top' ); ?>

		<table class="wp-list-table <?php echo implode( ' ', $this->get_table_classes() ); ?>" cellspacing="0">
			<thead>
				<tr>
					<?php $this->print_column_headers(); ?>
				</tr>
			</thead>

			<tfoot>
				<tr>
					<?php $this->print_column_headers( false ); ?>
				</tr>
			</tfoot>

			<tbody id="the-comment-list">
				<?php $this->display_rows_or_placeholder(); ?>
			</tbody>
		</table>
		<?php

		$this->display_tablenav( 'bottom' );
	}

	/**
	 * Generate content for a single row of the table.
	 *
	 * @since 1.7.0
	 *
	 * @param object|array $item The current group item in the loop.
	 */
	public function single_row( $item = array() ) {
		static $even = false;

		$row_classes = array();

		if ( $even ) {
			$row_classes = array( 'even' );
		} else {
			$row_classes = array( 'alternate', 'odd' );
		}

		/**
		 * Filters the classes applied to a single row in the groups list table.
		 *
		 * @since 1.9.0
		 *
		 * @param array  $row_classes Array of classes to apply to the row.
		 * @param string $value       ID of the current group being displayed.
		 */
		$row_classes = apply_filters( 'bp_groups_admin_row_class', $row_classes, $item['id'] );
		$row_class = ' class="' . implode( ' ', $row_classes ) . '"';

		echo '<tr' . $row_class . ' id="group-' . esc_attr( $item['id'] ) . '" data-parent_id="' . esc_attr( $item['id'] ) . '" data-root_id="' . esc_attr( $item['id'] ) . '">';
		echo $this->single_row_columns( $item );
		echo '</tr>';

		$even = ! $even;
	}

	/**
	 * Get the list of views available on this table (e.g. "all", "public").
	 *
	 * @since 1.7.0
	 */
	public function get_views() {
		$url_base = bp_get_admin_url( 'admin.php?page=bp-groups' ); ?>

		<ul class="subsubsub">
			<li class="all"><a href="<?php echo esc_url( $url_base ); ?>" class="<?php if ( 'all' == $this->view ) echo 'current'; ?>"><?php _e( 'All', 'buddypress' ); ?></a> |</li>
			<li class="public"><a href="<?php echo esc_url( add_query_arg( 'group_status', 'public', $url_base ) ); ?>" class="<?php if ( 'public' == $this->view ) echo 'current'; ?>"><?php printf( _n( 'Public <span class="count">(%s)</span>', 'Public <span class="count">(%s)</span>', $this->group_counts['public'], 'buddypress' ), number_format_i18n( $this->group_counts['public'] ) ); ?></a> |</li>
			<li class="private"><a href="<?php echo esc_url( add_query_arg( 'group_status', 'private', $url_base ) ); ?>" class="<?php if ( 'private' == $this->view ) echo 'current'; ?>"><?php printf( _n( 'Private <span class="count">(%s)</span>', 'Private <span class="count">(%s)</span>', $this->group_counts['private'], 'buddypress' ), number_format_i18n( $this->group_counts['private'] ) ); ?></a> |</li>
			<li class="hidden"><a href="<?php echo esc_url( add_query_arg( 'group_status', 'hidden', $url_base ) ); ?>" class="<?php if ( 'hidden' == $this->view ) echo 'current'; ?>"><?php printf( _n( 'Hidden <span class="count">(%s)</span>', 'Hidden <span class="count">(%s)</span>', $this->group_counts['hidden'], 'buddypress' ), number_format_i18n( $this->group_counts['hidden'] ) ); ?></a></li>

			<?php

			/**
			 * Fires inside listing of views so plugins can add their own.
			 *
			 * @since 1.7.0
			 *
			 * @param string $url_base Current URL base for view.
			 * @param string $view     Current view being displayed.
			 */
			do_action( 'bp_groups_list_table_get_views', $url_base, $this->view ); ?>
		</ul>
	<?php
	}

	/**
	 * Get bulk actions for single group row.
	 *
	 * @since 1.7.0
	 *
	 * @return array Key/value pairs for the bulk actions dropdown.
	 */
	public function get_bulk_actions() {

		/**
		 * Filters the list of bulk actions to display on a single group row.
		 *
		 * @since 1.7.0
		 *
		 * @param array $value Array of bulk actions to display.
		 */
		return apply_filters( 'bp_groups_list_table_get_bulk_actions', array(
			'delete' => __( 'Delete', 'buddypress' )
		) );
	}

	/**
	 * Get the table column titles.
	 *
	 * @since 1.7.0
	 *
	 * @see WP_List_Table::single_row_columns()
	 *
	 * @return array Array of column titles.
	 */
	public function get_columns() {

		/**
		 * Filters the titles for the columns for the groups list table.
		 *
		 * @since 2.0.0
		 *
		 * @param array $value Array of slugs and titles for the columns.
		 */
		return apply_filters( 'bp_groups_list_table_get_columns', array(
			'cb'          => '<input name type="checkbox" />',
			'comment'     => _x( 'Name', 'Groups admin Group Name column header',               'buddypress' ),
			'description' => _x( 'Description', 'Groups admin Group Description column header', 'buddypress' ),
			'status'      => _x( 'Status', 'Groups admin Privacy Status column header',         'buddypress' ),
			'members'     => _x( '# Members', 'Groups admin Members column header',             'buddypress' ),
			'last_active' => _x( 'Last Active', 'Groups admin Last Active column header',       'buddypress' )
		) );
	}

	/**
	 * Get the column names for sortable columns.
	 *
	 * Note: It's not documented in WP, but the second item in the
	 * nested arrays below is $desc_first. Normally, we would set
	 * last_active to be desc_first (since you're generally interested in
	 * the *most* recently active group, not the *least*). But because
	 * the default sort for the Groups admin screen is DESC by last_active,
	 * we want the first click on the Last Active column header to switch
	 * the sort order - ie, to make it ASC. Thus last_active is set to
	 * $desc_first = false.
	 *
	 * @since 1.7.0
	 *
	 * @return array Array of sortable column names.
	 */
	public function get_sortable_columns() {
		return array(
			'gid'         => array( 'gid', false ),
			'comment'     => array( 'name', false ),
			'members'     => array( 'members', false ),
			'last_active' => array( 'last_active', false ),
		);
	}

	/**
	 * Override WP_List_Table::row_actions().
	 *
	 * Basically a duplicate of the row_actions() method, but removes the
	 * unnecessary <button> addition.
	 *
	 * @since 2.3.3
	 * @since 2.3.4 Visibility set to public for compatibility with WP < 4.0.0.
	 *
	 * @param array $actions The list of actions
	 * @param bool $always_visible Whether the actions should be always visible
	 * @return string
	 */
	public function row_actions( $actions, $always_visible = false ) {
		$action_count = count( $actions );
		$i = 0;

		if ( !$action_count )
			return '';

		$out = '<div class="' . ( $always_visible ? 'row-actions visible' : 'row-actions' ) . '">';
		foreach ( $actions as $action => $link ) {
			++$i;
			( $i == $action_count ) ? $sep = '' : $sep = ' | ';
			$out .= "<span class='$action'>$link$sep</span>";
		}
		$out .= '</div>';

		return $out;
	}

	/**
	 * Markup for the Checkbox column.
	 *
	 * @since 1.7.0
	 *
	 * @see WP_List_Table::single_row_columns()
	 *
	 * @param array $item A singular item (one full row).
	 */
	public function column_cb( $item = array() ) {
		printf( '<label class="screen-reader-text" for="gid-%1$d">' . __( 'Select group %1$d', 'buddypress' ) . '</label><input type="checkbox" name="gid[]" value="%1$d" id="gid-%1$d" />', $item['id'] );
	}

	/**
	 * Markup for the Group ID column.
	 *
	 * @since 1.7.0
	 *
	 * @see WP_List_Table::single_row_columns()
	 *
	 * @param array $item A singular item (one full row).
	 */
	public function column_gid( $item = array() ) {
		echo '<strong>' . absint( $item['id'] ) . '</strong>';
	}

	/**
	 * Name column, and "quick admin" rollover actions.
	 *
	 * Called "comment" in the CSS so we can re-use some WP core CSS.
	 *
	 * @since 1.7.0
	 *
	 * @see WP_List_Table::single_row_columns()
	 *
	 * @param array $item A singular item (one full row).
	 */
	public function column_comment( $item = array() ) {

		// Preorder items: Edit | Delete | View
		$actions = array(
			'edit'   => '',
			'delete' => '',
			'view'   => '',
		);

		// We need the group object for some BP functions
		$item_obj = (object) $item;

		// Build actions URLs
		$base_url   = bp_get_admin_url( 'admin.php?page=bp-groups&amp;gid=' . $item['id'] );
		$delete_url = wp_nonce_url( $base_url . "&amp;action=delete", 'bp-groups-delete' );
		$edit_url   = $base_url . '&amp;action=edit';
		$view_url   = bp_get_group_permalink( $item_obj );

		/**
		 * Filters the group name for a group's column content.
		 *
		 * @since 1.7.0
		 *
		 * @param string $value Name of the group being rendered.
		 * @param array  $item  Array for the current group item.
		 */
		$group_name = apply_filters_ref_array( 'bp_get_group_name', array( $item['name'] ), $item );

		// Rollover actions

		// Edit
		$actions['edit']   = sprintf( '<a href="%s">%s</a>', esc_url( $edit_url   ), __( 'Edit',   'buddypress' ) );

		// Delete
		$actions['delete'] = sprintf( '<a href="%s">%s</a>', esc_url( $delete_url ), __( 'Delete', 'buddypress' ) );

		// Visit
		$actions['view']   = sprintf( '<a href="%s">%s</a>', esc_url( $view_url   ), __( 'View',   'buddypress' ) );

		/**
		 * Filters the actions that will be shown for the column content.
		 *
		 * @since 1.7.0
		 *
		 * @param array $value Array of actions to be displayed for the column content.
		 * @param array $item  The current group item in the loop.
		 */
		$actions = apply_filters( 'bp_groups_admin_comment_row_actions', array_filter( $actions ), $item );

		// Get group name and avatar
		$avatar = '';

		if ( buddypress()->avatar->show_avatars ) {
			$avatar  = bp_core_fetch_avatar( array(
				'item_id'    => $item['id'],
				'object'     => 'group',
				'type'       => 'thumb',
				'avatar_dir' => 'group-avatars',
				'alt'        => sprintf( __( 'Group logo of %s', 'buddypress' ), $group_name ),
				'width'      => '32',
				'height'     => '32',
				'title'      => $group_name
			) );
		}

		$content = sprintf( '<strong><a href="%s">%s</a></strong>', esc_url( $edit_url ), $group_name );

		echo $avatar . ' ' . $content . ' ' . $this->row_actions( $actions );
	}

	/**
	 * Markup for the Description column.
	 *
	 * @since 1.7.0
	 *
	 * @param array $item Information about the current row.
	 */
	public function column_description( $item = array() ) {

		/**
		 * Filters the markup for the Description column.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value Markup for the Description column.
		 * @param array  $item  The current group item in the loop.
		 */
		echo apply_filters_ref_array( 'bp_get_group_description', array( $item['description'], $item ) );
	}

	/**
	 * Markup for the Status column.
	 *
	 * @since 1.7.0
	 *
	 * @param array $item Information about the current row.
	 */
	public function column_status( $item = array() ) {
		$status      = $item['status'];
		$status_desc = '';

		// @todo This should be abstracted out somewhere for the whole
		// Groups component
		switch ( $status ) {
			case 'public' :
				$status_desc = __( 'Public', 'buddypress' );
				break;
			case 'private' :
				$status_desc = __( 'Private', 'buddypress' );
				break;
			case 'hidden' :
				$status_desc = __( 'Hidden', 'buddypress' );
				break;
		}

		/**
		 * Filters the markup for the Status column.
		 *
		 * @since 1.7.0
		 *
		 * @param string $status_desc Markup for the Status column.
		 * @parma array  $item        The current group item in the loop.
		 */
		echo apply_filters_ref_array( 'bp_groups_admin_get_group_status', array( $status_desc, $item ) );
	}

	/**
	 * Markup for the Number of Members column.
	 *
	 * @since 1.7.0
	 *
	 * @param array $item Information about the current row.
	 */
	public function column_members( $item = array() ) {
		$count = groups_get_groupmeta( $item['id'], 'total_member_count' );

		/**
		 * Filters the markup for the number of Members column.
		 *
		 * @since 1.7.0
		 *
		 * @param int   $count Markup for the number of Members column.
		 * @parma array $item  The current group item in the loop.
		 */
		echo apply_filters_ref_array( 'bp_groups_admin_get_group_member_count', array( (int) $count, $item ) );
	}

	/**
	 * Markup for the Last Active column.
	 *
	 * @since 1.7.0
	 *
	 * @param array $item Information about the current row.
	 */
	public function column_last_active( $item = array() ) {
		$last_active = groups_get_groupmeta( $item['id'], 'last_activity' );

		/**
		 * Filters the markup for the Last Active column.
		 *
		 * @since 1.7.0
		 *
		 * @param string $last_active Markup for the Last Active column.
		 * @parma array  $item        The current group item in the loop.
		 */
		echo apply_filters_ref_array( 'bp_groups_admin_get_group_last_active', array( $last_active, $item ) );
	}

	/**
	 * Allow plugins to add their custom column.
	 *
	 * @since 2.0.0
	 *
	 * @param array  $item        Information about the current row.
	 * @param string $column_name The column name.
	 *
	 * @return string
	 */
	public function column_default( $item = array(), $column_name = '' ) {

		/**
		 * Filters a string to allow plugins to add custom column content.
		 *
		 * @since 2.0.0
		 *
		 * @param string $value       Empty string.
		 * @param string $column_name Name of the column being rendered.
		 * @param array  $item        The current group item in the loop.
		 */
		return apply_filters( 'bp_groups_admin_get_group_custom_column', '', $column_name, $item );
	}
}

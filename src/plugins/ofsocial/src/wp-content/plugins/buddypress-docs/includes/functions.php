<?php

/**
 * Miscellaneous utility functions
 *
 * @package BuddyPress_Docs
 * @since 1.2
 */


/**
 * Return the bp_doc post type name
 *
 * @package BuddyPress_Docs
 * @since 1.2
 *
 * @return str The name of the bp_doc post type
 */
function bp_docs_get_post_type_name() {
	global $bp;
	return $bp->bp_docs->post_type_name;
}

/**
 * Return the associated_item taxonomy name
 *
 * @package BuddyPress_Docs
 * @since 1.2
 */
function bp_docs_get_associated_item_tax_name() {
	global $bp;
	return $bp->bp_docs->associated_item_tax_name;
}

/**
 * Return the access taxonomy name
 *
 * @package BuddyPress_Docs
 * @since 1.2
 */
function bp_docs_get_access_tax_name() {
	global $bp;
	return $bp->bp_docs->access_tax_name;
}

/**
 * Utility function to get and cache the current doc
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @return obj Current doc
 */
function bp_docs_get_current_doc() {
	$current_doc = null;

	// Check the queried object first - this works on custom post type
	// pages
	$maybe_doc = get_queried_object();
	if ( is_a( $maybe_doc, 'WP_Post' ) && bp_docs_get_post_type_name() === $maybe_doc->post_type ) {
		$current_doc = $maybe_doc;

	// Check if we're in the loop
	} else if ( $maybe_doc_id = get_the_ID() ) {
		$maybe_doc = get_post( $maybe_doc_id );
		if ( bp_docs_get_post_type_name() === $maybe_doc->post_type ) {
			$current_doc = $maybe_doc;
		}
	}

	return apply_filters( 'bp_docs_get_current_doc', $current_doc );
}


/**
 * Get an item_id based on a taxonomy term slug
 *
 * BuddyPress Docs are associated with groups and users through a taxonomy called
 * bp_docs_associated_item. Terms belonging to this taxonomy have slugs that look like this
 * (since 1.2):
 *   4-user
 *   103-group
 * (where 4-user corresponds to the user with the ID 4, and 103 is the group with group_id 103).
 * If you have a term slug, you can use this function to parse the item id out of it. Note that
 * it will return 0 if you pass a slug that belongs to a different item type.
 *
 * @since 1.2
 * @param str $term_slug The 'slug' property of a WP term object
 * @param str $item_type 'user', 'group', or your custom item type
 * @return mixed Returns false if you don't pass in the proper parameters.
 *		 Returns 0 if you pass a slug that does not correspond to your item_type
 *	         Returns an int (the unique item_id) if successful
 */
function bp_docs_get_associated_item_id_from_term_slug( $term_slug = '', $item_type = '' ) {
	if ( !$term_slug || !$item_type ) {
		return false;
	}

	$item_id = 0;

	// The item_type should be hidden in the slug
	$slug_array = explode( '-', $term_slug );

	if ( isset( $slug_array[1] ) && $item_type == $slug_array[1] ) {
		$item_id = $slug_array[0];
	}

	return apply_filters( 'bp_docs_get_associated_item_id_from_term_slug', $item_id, $term_slug, $item_type );
}

/**
 * Get the term_id for the associated_item term corresponding to a item_id
 *
 * Will create it if it's not found
 *
 * @since 1.2
 *
 * @param int $item_id Such as the group_id or user_id
 * @param str $item_type Such as 'user' or 'group' (slug of the parent term)
 * @param str $item_name Optional. This is the value that will be used to describe the term in the
 *    Dashboard.
 * @return int $item_term_id
 */
function bp_docs_get_item_term_id( $item_id, $item_type, $item_name = '' ) {
	global $bp;

	if ( empty( $item_id ) ) {
		return;
	}

	// Sanitization
	// @todo Maybe this should be more generous
	$item_type = 'group' == $item_type ? 'group' : 'user';

	$item_term_slug = 'bp_docs_associated_' . $item_type . '_' . $item_id;

	$item_term = get_term_by( 'slug', $item_term_slug, bp_docs_get_associated_item_tax_name() );

	// If the item term doesn't exist, then create it
	if ( empty( $item_term ) ) {
		// Set up the arguments for creating the term. Filter this to set your own
		switch ( $item_type ) {
			case 'group' :
				$item = groups_get_group( 'group_id=' . $item_id );
				$item_name = $item->name;
				break;

			case 'user' :
			default :
				$item_name = bp_core_get_user_displayname( $item_id );
				break;
		}

		$item_term_args = apply_filters( 'bp_docs_item_term_values', array(
			'description' => sprintf( _x( 'Docs associated with the %1$s %2$s', 'Description for the associated-item taxonomy term. Of the form "Docs associated with the [item-type] [item-name]" - item-type is group, user, etc', 'bp-docs' ), $item_type, $item_name ),
			'slug'        => $item_term_slug,
		) );

		// Create the item term
		$item_term = wp_insert_term( $item_name, bp_docs_get_associated_item_tax_name(), $item_term_args );
		$term_id = isset( $item_term['term_id'] ) ? $item_term['term_id'] : false;
	} else {
		$term_id = $item_term->term_id;
	}

	return apply_filters( 'bp_docs_get_item_term_id', $term_id, $item_id, $item_type, $item_name );
}

/**
 * Get the absolute path of a given template.
 *
 * Looks first for a template in [theme-dir]/docs/, and falls back on the provided templates.
 *
 * Ideally, I would not need this function. But WP's locate_template() plays funny with directory
 * paths, and bp_core_load_template() does not have an option that will let you locate but not load
 * the found template.
 *
 * @package BuddyPress Docs
 * @since 1.0.5
 *
 * @param str $template This string should be of the format 'edit-docs.php'. Ie, you need '.php',
 *                      but you don't need the leading '/docs/'
 * @return str $template_path The absolute path of the located template file.
 */
function bp_docs_locate_template( $template = '', $load = false, $require_once = true ) {
	if ( empty( $template ) )
		return false;

	// Try to load custom templates first
	$stylesheet_path = STYLESHEETPATH . '/docs/';
	$template_path   = TEMPLATEPATH . '/docs/';

	if ( file_exists( $stylesheet_path . $template ) )
		$template_path = $stylesheet_path . $template;
	elseif ( file_exists( $template_path . $template ) )
		$template_path = $template_path . $template;
	else
		$template_path = BP_DOCS_INCLUDES_PATH . 'templates/docs/' . $template;

	$template_path = apply_filters( 'bp_docs_locate_template', $template_path, $template );

	if ( $template_path ) {
		if ( $load ) {
			load_template( $template_path, $require_once );
		} else {
			return $template_path;
		}
	} else if ( function_exists( 'is_buddypress' ) ) {

		if ( bp_docs_is_docs_component() ) {
			status_header( 200 );
			$wp_query->is_page     = true;
			$wp_query->is_singular = true;
			$wp_query->is_404      = false;
		}

		do_action( 'bp_setup_theme_compat' );
	}
}

/**
 * Determine whether the current user can do something the current doc
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 * @deprecated 1.8
 *
 * @param str $action The cap being tested
 * @return bool $user_can
 */
function bp_docs_current_user_can( $action = 'edit', $doc_id = false ) {
	_deprecated_function( __FUNCTION__, '1.8', 'Use current_user_can() with "bp_docs_" prefixed capabilities instead.' );

	$user_can = bp_docs_user_can( $action, bp_loggedin_user_id(), $doc_id );

	return apply_filters( 'bp_docs_current_user_can', $user_can, $action );
}

/**
 * Determine whether a given user can do something with a given doc
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @param str $action Optional. The action being queried. Eg 'edit', 'read_comments', 'manage'
 * @param int $user_id Optional. Unique user id for the user being tested. Defaults to logged-in ID
 * @param int $doc_id Optional. Unique doc id. Defaults to doc currently being viewed
 */
function bp_docs_user_can( $action = 'edit', $user_id = false, $doc_id = false ) {
	global $bp, $post;

	if ( false === $user_id ) {
		$user_id = bp_loggedin_user_id();
	}

	// Grant all permissions on documents being created, as long as the
	// user is logged in
	if ( $user_id && ( false === $doc_id ) && bp_docs_is_doc_create() ) {
		return true;
	}

	if ( ! $doc_id ) {
		if ( ! empty( $post->ID ) && bp_docs_get_post_type_name() === $post->post_type ) {
			$doc_id = $post->ID;
			$doc = $post;
		} else {
			$doc = bp_docs_get_current_doc();
			if ( isset( $doc->ID ) ) {
				$doc_id = $doc->ID;
			}
		}
	} else {
		$doc = get_post( $doc_id );
	}

	$user_can = false;

	if ( 'create' === $action ) {

		// In the case of Doc creation, this value gets passed through
		// to other components
		$user_can = 0 != $user_id;

	} else if ( ! empty( $doc ) ) {
		$doc_settings = bp_docs_get_doc_settings( $doc_id );
		$the_setting  = isset( $doc_settings[ $action ] ) ? $doc_settings[ $action ] : '';

		if ( empty( $the_setting ) ) {
			$the_setting = 'anyone';
		}

		switch ( $the_setting ) {
			case 'anyone' :
				$user_can = true;
				break;

			case 'loggedin' :
				$user_can = 0 != $user_id;
				break;

			case 'creator' :
				$user_can = $doc->post_author == $user_id;
				break;
			// Do nothing with other settings - they are passed through
		}
	}

	if ( $user_id ) {
		if ( is_super_admin( $user_id ) ) {
			// Super admin always gets to edit. What a big shot
			$user_can = true;
		} else {
			// Filter this so that groups-integration and other plugins can give their
			// own rules. Done inside the conditional so that plugins don't have to
			// worry about the is_super_admin() check
			$user_can = apply_filters( 'bp_docs_user_can', $user_can, $action, $user_id, $doc_id );
		}
	}

	return $user_can;
}

/**
 * Can the current user create a Doc in this context?
 *
 * Is sensitive to group contexts (and the "associated with" permissions
 * levels)
 *
 * @since 1.5
 * @return bool
 */
function bp_docs_current_user_can_create_in_context() {
	if ( function_exists( 'bp_is_group' ) && bp_is_group() ) {
		$can_create = current_user_can( 'bp_docs_associate_with_group', bp_get_current_group_id() );
	} else {
		$can_create = current_user_can( 'bp_docs_create' );
	}

	return apply_filters( 'bp_docs_current_user_can_create_in_context', $can_create );
}

/**
 * Update the Doc count for a given item
 *
 * @since 1.2
 */
function bp_docs_update_doc_count( $item_id = 0, $item_type = '' ) {
	global $bp;

	$doc_count = 0;
	$docs_args = array( 'doc_slug' => '' );

	switch ( $item_type ) {
		case 'group' :
			$docs_args['author_id'] = '';
			$docs_args['group_id']  = $item_id;
			break;

		case 'user' :
			$docs_args['author_id'] = $item_id;
			$docs_args['group_id']  = '';
			break;

		default :
			$docs_args['author_id'] = '';
			$docs_args['group_id']  = '';
			break;
	}

	$query = new BP_Docs_Query( $docs_args );
	$query->get_wp_query();
	if ( $query->query->have_posts() ) {
		$doc_count = $query->query->found_posts;
	}

	// BP has a stupid bug that makes it delete groupmeta when it equals 0. We'll save
	// a string instead of zero to work around this
	if ( !$doc_count )
		$doc_count = '0';

	// Save the count
	switch ( $item_type ) {
		case 'group' :
			groups_update_groupmeta( $item_id, 'bp-docs-count', $doc_count );
			break;

		case 'user' :
			update_user_meta( $item_id, 'bp_docs_count', $doc_count );
			break;

		default :
			bp_update_option( 'bp_docs_count', $doc_count );
			break;
	}

	return $doc_count;
}

/**
 * Is this the BP Docs component?
 */
function bp_docs_is_docs_component() {
	$retval = false;

	$p = get_queried_object();

	if ( is_post_type_archive( bp_docs_get_post_type_name() ) ) {
		$retval = true;
	} else if ( isset( $p->post_type ) && bp_docs_get_post_type_name() == $p->post_type ) {
		$retval = true;
	} else if ( bp_is_current_component( bp_docs_get_docs_slug() ) ) {
		// This covers cases where we're looking at the Docs component of a user
		$retval = true;
	} else if ( bp_is_current_action( bp_docs_get_docs_slug() ) ) {
		// This covers cases where we're looking at the Docs library of a group.
		$retval = true;
	}

	return $retval;
}

/**
 * Get the Doc settings array
 *
 * This will prepopulate many of the required settings, for cases where the settings have not
 * yet been saved for this Doc.
 *
 * @param int $doc_id
 * @param string $type 'default' parses with default options to ensure that all
 *        keys have values. 'raw' returns results as stored in the database.
 * @return array
 */
function bp_docs_get_doc_settings( $doc_id = 0, $type = 'default', $group_id = 0 ) {
	$doc_settings = array();

	$q = get_queried_object();
	if ( !$doc_id && isset( $q->ID ) ) {
		$doc_id = $q->ID;
	}

	$saved_settings = get_post_meta( $doc_id, 'bp_docs_settings', true );
	if ( !is_array( $saved_settings ) ) {
		$saved_settings = array();
	}

	$default_settings = bp_docs_get_default_access_options( $doc_id, $group_id );

	if ( 'raw' !== $type ) {
		// Empty string settings can slip through sometimes
		$saved_settings = array_filter( $saved_settings );

		$doc_settings = wp_parse_args( $saved_settings, $default_settings );
	} else {
		$doc_settings = $saved_settings;
	}

	return apply_filters( 'bp_docs_get_doc_settings', $doc_settings, $doc_id, $default_settings, $saved_settings, $group_id );
}

function bp_docs_define_tiny_mce() {
	BP_Docs_Query::define_wp_tiny_mce();
}

/**
 * Send a Doc to the trash
 *
 * @since 1.3
 * @param int $doc_id
 * @return bool
 */
function bp_docs_trash_doc( $doc_id = 0 ) {
	do_action( 'bp_docs_before_doc_delete', $doc_id );

	$delete_args = array(
		'ID' => $doc_id,
		'post_status' => 'trash'
	);

	$deleted = wp_update_post( $delete_args );

	if ( $deleted ) {
		do_action( 'bp_docs_doc_deleted', $delete_args );
		return true;
	}

	return false;
}

/**
 * Remove a Doc from the Trash.
 *
 * @since 1.5.5
 * @param int $doc_id ID of the Doc to be untrashed.
 * @return bool True on success, otherwise false.
 */
function bp_docs_untrash_doc( $doc_id = 0 ) {
	do_action( 'bp_docs_before_doc_untrash', $doc_id );

	$untrashed = wp_update_post( array(
		'ID' => $doc_id,
		'post_status' => 'publish',
	) );

	if ( $untrashed ) {
		do_action( 'bp_docs_doc_untrashed', $doc_id );
		return true;
	}

	return false;
}

/**
 * Outputs a list of access options
 *
 * Access options are things like 'anyone', 'loggedin', 'group-members', etc
 */
function bp_docs_get_access_options( $settings_field, $doc_id = 0, $group_id = 0 ) {
	$options = array();

	// The base options for every setting
	$options = array(
		20 => array(
			'name'  => 'loggedin',
			'label' => __( 'Logged-in Users', 'bp-docs' ),
			'default' => 1 // default to 'loggedin' for most options. See below for override
		),
		90 => array(
			'name'  => 'creator',
			'label' => __( 'The Doc author only', 'bp-docs' )
		),
	);

	// Default to manage => creator.
	if ( 'manage' == $settings_field ) {
		// Unset the default of loggedin.
		$options[20]['default'] = 0;

		$options[90]['default'] = 1;
	}

	// Allow anonymous reading
	if ( in_array( $settings_field, array( 'read', 'read_comments', 'post_comments', 'view_history' ) ) ) {
		$options[10] = array(
			'name'  => 'anyone',
			'label' => __( 'Anyone', 'bp-docs' ),
			'default' => 1
		);

		$options[20]['default'] = 0; // Default to 'anyone' instead
	}

	// Other integration pieces can mod the options with this filter
	$options = apply_filters( 'bp_docs_get_access_options', $options, $settings_field, $doc_id, $group_id );

	// Options are sorted by the numeric key
	ksort( $options );

	return $options;
}

/**
 * Builds the default access options for a doc.
 *
 * @since 1.8.8
 * @param int $doc_id ID of the doc.
 * @param int $group_id ID of the group that this doc is associated with.
 *
 * @return array Associative array of settings_field => default_option.
 */
function bp_docs_get_default_access_options( $doc_id = 0, $group_id = 0 ) {
	// We may be able to get the associated group from the doc_id.
	if ( empty( $group_id ) && ! empty( $doc_id ) ) {
		$group_id = bp_docs_get_associated_group_id( $doc_id );
	}

	$defaults = array();
	$settings_fields = array( 'read', 'edit', 'read_comments', 'post_comments', 'view_history', 'manage' );

	foreach ( $settings_fields as $settings_field ) {
		$access_options = bp_docs_get_access_options( $settings_field, $doc_id, $group_id );

		foreach ( $access_options as $key => $access_option ) {
			if ( ! empty( $access_option['default'] ) ) {
				$defaults[$settings_field] = $access_option['name'];
				break;
			}
		}
	}

	return apply_filters( 'bp_docs_get_default_access_options', $defaults, $doc_id, $group_id );
}

/**
 * Saves the settings associated with a given Doc
 *
 * @since 1.6.1
 * @param int $doc_id The numeric ID of the doc
 * @return null
 */
function bp_docs_save_doc_access_settings( $doc_id ) {
	// Two cases:
	// 1. User is saving a doc for which he can update the access settings
	if ( isset( $_POST['settings'] ) ) {
		$settings = ! empty( $_POST['settings'] ) ? $_POST['settings'] : array();
		$verified_settings = bp_docs_verify_settings( $settings, $doc_id, bp_loggedin_user_id() );

		$new_settings = array();
		foreach ( $verified_settings as $verified_setting_name => $verified_setting ) {
			$new_settings[ $verified_setting_name ] = $verified_setting['verified_value'];
			if ( $verified_setting['verified_value'] != $verified_setting['original_value'] ) {
				$result['message'] = __( 'Your Doc was successfully saved, but some of your access settings have been changed to match the Doc\'s permissions.', 'bp-docs' );
			}
		}
		update_post_meta( $doc_id, 'bp_docs_settings', $new_settings );

		// The 'read' setting must also be saved to a taxonomy, for
		// easier directory queries
		$read_setting = isset( $new_settings['read'] ) ? $new_settings['read'] : 'anyone';
		bp_docs_update_doc_access( $doc_id, $read_setting );

	// 2. User is saving a doc for which he can't manage the access settings
	// isset( $_POST['settings'] ) is false; the access settings section
	// isn't included on the edit form
	} else {
		// Do nothing.
		// Leave the access settings intact.
	}
}

/**
 * Reset group-related doc access settings to "creator"
 *
 * @since 1.9.0
 * @param int $doc_id The numeric ID of the doc
 * @return void
 */
function bp_docs_remove_group_related_doc_access_settings( $doc_id ) {
	if ( empty( $doc_id ) ) {
		return;
	}

	// When a doc's privacy relies on group association, and that doc loses that group association, we need to make sure that it doesn't become public.
	$settings = bp_docs_get_doc_settings( $doc_id );
	$group_settings = array( 'admins-mods','group-members' );
	$settings_modified = false;

	foreach ( $settings as $capability => $audience ) {
		if ( in_array( $audience, $group_settings ) ) {
			$new_settings[ $capability ] = 'creator';
			$settings_modified = true;
		} else {
			$new_settings[ $capability ] = $audience;
		}
	}

	if ( $settings_modified ) {
		update_post_meta( $doc_id, 'bp_docs_settings', $new_settings );
	}

	// The 'read' setting must also be saved to a taxonomy, for
	// easier directory queries. Update if modified.
	if ( $settings['read'] != $new_settings['read'] ) {
		bp_docs_update_doc_access( $doc_id, $new_settings['read'] );
	}
}

/**
 * Verifies the settings associated with a given Doc
 *
 * @since 1.2
 * @param array $settings Settings passed from the Edit form
 * @param int $doc_id The numeric ID of the doc
 * @param int $user_id The id of the user
 * @return array $is_allowed Keyed by settings names, with boolean values
 */
function bp_docs_verify_settings( $settings, $doc_id, $user_id = 0 ) {
	$verified_settings = array();

	foreach ( $settings as $setting_name => $setting_value ) {
		$allowed_values = bp_docs_get_access_options( $setting_name, $doc_id );

		$verified_settings[ $setting_name ] = array(
			'original_value'  => $setting_value,
			'verified_value'  => '',
			'setting_default' => '',
		);

		// Loop through to collect whitelisted values as well as the
		// default setting, which will be used if the user-provided
		// value doesn't match the whitelist
		foreach ( $allowed_values as $allowed_value ) {
			if ( empty( $verified_settings[ $setting_name ]['verified_value'] ) && $setting_value == $allowed_value['name'] ) {
				$verified_settings[ $setting_name ]['verified_value'] = $setting_value;
			}

			if ( empty( $verified_settings[ $setting_name ]['setting_default'] ) && 1 == $allowed_value['default'] ) {
				$verified_settings[ $setting_name ]['setting_default'] = 1;
			}
		}

		// If no whitelisted value has been found, attempt to fall
		// back on a default value for that option
		if ( empty( $verified_settings[ $setting_name ] ) ) {
			$verified_settings[ $setting_name ]['verified_value'] = $verified_settings[ $setting_name ]['setting_default'];
		}
	}

	return $verified_settings;
}

/**
 * Get the access term for 'anyone'
 *
 * @since 1.2
 * @return string The term slug
 */
function bp_docs_get_access_term_anyone() {
	return apply_filters( 'bp_docs_get_access_term_anyone', 'bp_docs_access_anyone' );
}

/**
 * Get the access term for 'loggedin'
 *
 * @since 1.2
 * @return string The term slug
 */
function bp_docs_get_access_term_loggedin() {
	return apply_filters( 'bp_docs_get_access_term_loggedin', 'bp_docs_access_loggedin' );
}

/**
 * Get the access term for a user id
 *
 * @since 1.2
 * @param int|bool $user_id Defaults to logged in user
 * @return string The term slug
 */
function bp_docs_get_access_term_user( $user_id = false ) {
	if ( false === $user_id ) {
		$user_id = bp_loggedin_user_id();
	}

	return apply_filters( 'bp_docs_get_access_term_user', 'bp_docs_access_user_' . intval( $user_id ) );
}

/**
 * Get the access term corresponding to group-members for a given group
 *
 * @since 1.2
 * @param int $group_id
 * @return string The term slug
 */
function bp_docs_get_access_term_group_member( $user_id = false ) {
	return apply_filters( 'bp_docs_get_access_term_group_member', 'bp_docs_access_group_member_' . intval( $user_id ) );
}

/**
 * Get the access term corresponding to admins-mods for a given group
 *
 * @since 1.2
 * @param int $group_id
 * @return string The term slug
 */
function bp_docs_get_access_term_group_adminmod( $user_id = false ) {
	return apply_filters( 'bp_docs_get_access_term_group_adminmod', 'bp_docs_access_group_adminmod_' . intval( $user_id ) );
}

function bp_docs_update_doc_access( $doc_id, $access_setting = 'anyone' ) {

	$doc = get_post( $doc_id );

	if ( ! $doc || is_wp_error( $doc ) ) {
		return false;
	}

	// Convert the access setting to a WP taxonomy term
	switch ( $access_setting ) {
		case 'anyone' :
		case 'loggedin' :
			$access_term = 'bp_docs_access_' . $access_setting;
			break;

		case 'group-members' :
		case 'admins-mods' :
			$associated_group = bp_docs_get_associated_group_id( $doc_id );
			$access_term = 'group-members' == $access_setting ? bp_docs_get_access_term_group_member( $associated_group ) : bp_docs_get_access_term_group_adminmod( $associated_group );
			break;

		case 'creator' :
		case 'no-one' :
			// @todo Don't know how these are different
			$access_term = bp_docs_get_access_term_user( $doc->post_author );
			break;
	}

	if ( isset( $access_term ) ) {
		$retval = wp_set_post_terms( $doc_id, $access_term, bp_docs_get_access_tax_name() );
	}

	if ( empty( $retval ) || is_wp_error( $retval ) ) {
		return false;
	} else {
		return true;
	}

}

/**
 * Should 'hide_sitewide' be true for activity items associated with this Doc?
 *
 * We generalize: mark the activity items as 'hide_sitewide' whenever the
 * 'read' setting is something other than 'anyone'.
 *
 * Note that this gets overridden by the filter in integration-groups.php in
 * the case of group-associated Docs.
 *
 * @since 1.2.8
 * @param int $doc_id
 * @return bool $hide_sitewide
 */
function bp_docs_hide_sitewide_for_doc( $doc_id ) {
	if ( ! $doc_id ) {
		return false;
	}

	$settings = get_post_meta( $doc_id, 'bp_docs_settings', true );
	$hide_sitewide = empty( $settings['read'] ) || 'anyone' != $settings['read'];

	return apply_filters( 'bp_docs_hide_sitewide_for_doc', $hide_sitewide, $doc_id );
}

function bp_docs_get_doc_ids_accessible_to_current_user() {
	global $wpdb;

	// Direct query for speeeeeeed
	$exclude = bp_docs_access_query()->get_doc_ids();
	if ( empty( $exclude ) ) {
		$exclude = array( 0 );
	}
	$exclude_sql = '(' . implode( ',', $exclude ) . ')';
	$items_sql = $wpdb->prepare( "SELECT ID FROM $wpdb->posts WHERE post_type = %s AND ID NOT IN $exclude_sql", bp_docs_get_post_type_name() );
	return $wpdb->get_col( $items_sql );
}

/**
 * Determine how many revisions to retain for Docs.
 *
 * @since 1.8
 *
 * @return int
 */
function bp_docs_revisions_to_keep( $num, $post ) {
	if ( bp_docs_get_post_type_name() !== $post->post_type ) {
		return $num;
	}

	if ( defined( 'BP_DOCS_REVISIONS' ) ) {
		if ( true === BP_DOCS_REVISIONS ) {
			$num = -1;
		} else {
			$num = intval( BP_DOCS_REVISIONS );
		}
	}

	return intval( $num );
}
add_filter( 'wp_revisions_to_keep', 'bp_docs_revisions_to_keep', 10, 2 );

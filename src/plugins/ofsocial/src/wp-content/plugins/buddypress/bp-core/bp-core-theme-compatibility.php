<?php
/**
 * BuddyPress Core Theme Compatibility.
 *
 * @package BuddyPress
 * @subpackage ThemeCompatibility
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/** Theme Compat **************************************************************/

/**
 * What follows is an attempt at intercepting the natural page load process
 * to replace the_content() with the appropriate BuddyPress content.
 *
 * To do this, BuddyPress does several direct manipulations of global variables
 * and forces them to do what they are not supposed to be doing.
 *
 * Don't try anything you're about to witness here, at home. Ever.
 */

/** Base Class ****************************************************************/

/**
 * Theme Compatibility base class.
 *
 * This is only intended to be extended, and is included here as a basic guide
 * for future Theme Packs to use. {@link BP_Legacy} is a good example of
 * extending this class.
 *
 * @since 1.7.0
 *
 * @todo We should probably do something similar to BP_Component::start().
 * @todo If this is only intended to be extended, it should be abstract.
 *
 * @param array $properties {
 *     An array of properties describing the theme compat package.
 *     @type string $id      ID of the package. Must be unique.
 *     @type string $name    Name of the theme. This should match the name given
 *                           in style.css.
 *     @type string $version Theme version. Used for busting script and style
 *                           browser caches.
 *     @type string $dir     Filesystem path of the theme.
 *     @type string $url     Base URL of the theme.
 * }
 */
class BP_Theme_Compat {

	/**
	 * Template package properties, as passed to the constructor.
	 *
	 * @var array
	 */
	protected $_data = array();

	/**
	 * Pass the $properties to the object on creation.
	 *
	 * @since 1.7.0
	 *
	 * @param array $properties Array of properties for BP_Theme_Compat.
	 */
    	public function __construct( Array $properties = array() ) {
		$this->_data = $properties;
	}

	/**
	 * Set up the BuddyPress-specific theme compat methods.
	 *
	 * Themes should use this method in their constructor.
	 *
	 * @since 1.7.0
	 */
	protected function start() {
		// Sanity check
		if ( ! bp_use_theme_compat_with_current_theme() ) {
			return;
		}

		// Setup methods
		$this->setup_globals();
		$this->setup_actions();
	}

	/**
	 * Set up global data for your template package.
	 *
	 * Meant to be overridden in your class. See
	 * {@link BP_Legacy::setup_globals()} for an example.
	 *
	 * @since 1.7.0
	 */
	protected function setup_globals() {}

	/**
	 * Set up theme hooks for your template package.
	 *
	 * Meant to be overridden in your class. See
	 * {@link BP_Legacy::setup_actions()} for an example.
	 *
	 * @since 1.7.0
	 */
	protected function setup_actions() {}

	/**
	 * Set a theme's property.
	 *
	 * @since 1.7.0
	 *
	 * @param string $property Property name.
	 * @param mixed  $value    Property value.
	 *
	 * @return bool True on success, false on failure.
	 */
	public function __set( $property, $value ) {
		return $this->_data[$property] = $value;
	}

	/**
	 * Get a theme's property.
	 *
	 * @since 1.7.0
	 *
	 * @param string $property Property name.
	 *
	 * @return mixed The value of the property if it exists, otherwise an
	 *               empty string.
	 */
	public function __get( $property ) {
		return array_key_exists( $property, $this->_data ) ? $this->_data[$property] : '';
	}
}

/** Functions *****************************************************************/

/**
 * Set up the default theme compat theme.
 *
 * @since 1.7.0
 *
 * @param string $theme Optional. The unique ID identifier of a theme package.
 */
function bp_setup_theme_compat( $theme = '' ) {
	$bp = buddypress();

	// Make sure theme package is available, set to default if not
	if ( ! isset( $bp->theme_compat->packages[$theme] ) || ! is_a( $bp->theme_compat->packages[$theme], 'BP_Theme_Compat' ) ) {
		$theme = 'legacy';
	}

	// Set the active theme compat theme
	$bp->theme_compat->theme = $bp->theme_compat->packages[$theme];
}

/**
 * Get the ID of the theme package being used.
 *
 * This can be filtered or set manually. Tricky theme authors can override the
 * default and include their own BuddyPress compatibility layers for their themes.
 *
 * @since 1.7.0
 *
 * @uses apply_filters()
 *
 * @return string ID of the theme package in use.
 */
function bp_get_theme_compat_id() {

	/**
	 * Filters the ID of the theme package being used.
	 *
	 * @since 1.7.0
	 *
	 * @param string $id ID of the theme package in use.
	 */
	return apply_filters( 'bp_get_theme_compat_id', buddypress()->theme_compat->theme->id );
}

/**
 * Get the name of the theme package being used.
 *
 * This can be filtered or set manually. Tricky theme authors can override the
 * default and include their own BuddyPress compatibility layers for their themes.
 *
 * @since 1.7.0
 *
 * @uses apply_filters()
 *
 * @return string Name of the theme package currently in use.
 */
function bp_get_theme_compat_name() {

	/**
	 * Filters the name of the theme package being used.
	 *
	 * @since 1.7.0
	 *
	 * @param string $name Name of the theme package in use.
	 */
	return apply_filters( 'bp_get_theme_compat_name', buddypress()->theme_compat->theme->name );
}

/**
 * Get the version of the theme package being used.
 *
 * This can be filtered or set manually. Tricky theme authors can override the
 * default and include their own BuddyPress compatibility layers for their themes.
 *
 * @since 1.7.0
 *
 * @uses apply_filters()
 *
 * @return string The version string of the theme package currently in use.
 */
function bp_get_theme_compat_version() {

	/**
	 * Filters the version of the theme package being used.
	 *
	 * @since 1.7.0
	 *
	 * @param string $version The version string of the theme package in use.
	 */
	return apply_filters( 'bp_get_theme_compat_version', buddypress()->theme_compat->theme->version );
}

/**
 * Get the absolute path of the theme package being used.
 *
 * or set manually. Tricky theme authors can override the default and include
 * their own BuddyPress compatibility layers for their themes.
 *
 * @since 1.7.0
 *
 * @uses apply_filters()
 *
 * @return string The absolute path of the theme package currently in use.
 */
function bp_get_theme_compat_dir() {

	/**
	 * Filters the absolute path of the theme package being used.
	 *
	 * @since 1.7.0
	 *
	 * @param string $dir The absolute path of the theme package in use.
	 */
	return apply_filters( 'bp_get_theme_compat_dir', buddypress()->theme_compat->theme->dir );
}

/**
 * Get the URL of the theme package being used.
 *
 * This can be filtered, or set manually. Tricky theme authors can override
 * the default and include their own BuddyPress compatibility layers for their
 * themes.
 *
 * @since 1.7.0
 *
 * @uses apply_filters()
 *
 * @return string URL of the theme package currently in use.
 */
function bp_get_theme_compat_url() {

	/**
	 * Filters the URL of the theme package being used.
	 *
	 * @since 1.7.0
	 *
	 * @param string $url URL of the theme package in use.
	 */
	return apply_filters( 'bp_get_theme_compat_url', buddypress()->theme_compat->theme->url );
}

/**
 * Should we use theme compat for this theme?
 *
 * If the current theme's need for theme compat hasn't yet been detected, we
 * do so using bp_detect_theme_compat_with_current_theme().
 *
 * @since 1.9.0
 *
 * @uses bp_detect_theme_compat_with_current_theme()
 *
 * @return bool True if the current theme needs theme compatibility.
 */
function bp_use_theme_compat_with_current_theme() {
	if ( ! isset( buddypress()->theme_compat->use_with_current_theme ) ) {
		bp_detect_theme_compat_with_current_theme();
	}

	/**
	 * Filters whether or not to use theme compat for the active theme.
	 *
	 * @since 1.9.0
	 *
	 * @param bool $use_with_current_theme True if the current theme needs theme compatibility.
	 */
	return apply_filters( 'bp_use_theme_compat_with_current_theme', buddypress()->theme_compat->use_with_current_theme );
}

/**
 * Set our flag to determine whether theme compat should be enabled.
 *
 * Theme compat is disabled when a theme meets one of the following criteria:
 * 1) It declares BP support with add_theme_support( 'buddypress' )
 * 2) It is bp-default, or a child theme of bp-default
 * 3) A legacy template is found at members/members-loop.php. This is a
 *    fallback check for themes that were derived from bp-default, and have
 *    not been updated for BP 1.7+; we make the assumption that any theme in
 *    this category will have the members-loop.php template, and so use its
 *    presence as an indicator that theme compatibility is not required
 *
 * @since 1.9.0
 *
 * @return bool True if the current theme needs theme compatibility.
 */
function bp_detect_theme_compat_with_current_theme() {
	if ( isset( buddypress()->theme_compat->use_with_current_theme ) ) {
		return buddypress()->theme_compat->use_with_current_theme;
	}

	// theme compat enabled by default
	$theme_compat = true;

	// If the theme supports 'buddypress', bail.
	if ( current_theme_supports( 'buddypress' ) ) {
		$theme_compat = false;

	// If the theme doesn't support BP, do some additional checks
	} else {
		// Bail if theme is a derivative of bp-default
		if ( in_array( 'bp-default', array( get_template(), get_stylesheet() ) ) ) {
			$theme_compat = false;

		// Brute-force check for a BP template
		// Examples are clones of bp-default
		} elseif ( locate_template( 'members/members-loop.php', false, false ) ) {
			$theme_compat = false;
		}
	}

	// set a flag in the buddypress() singleton so we don't have to run this again
	buddypress()->theme_compat->use_with_current_theme = $theme_compat;

	return $theme_compat;
}

/**
 * Is the current page using theme compatibility?
 *
 * @since 1.7.0
 *
 * @return bool True if the current page uses theme compatibility.
 */
function bp_is_theme_compat_active() {
	$bp = buddypress();

	if ( empty( $bp->theme_compat->active ) ) {
		return false;
	}

	return $bp->theme_compat->active;
}

/**
 * Set the flag that tells whether the current page is using theme compatibility.
 *
 * @since 1.7.0
 *
 * @param bool $set True to set the flag to true, false to set it to false.
 *
 * @return bool Returns the value of $set.
 */
function bp_set_theme_compat_active( $set = true ) {
	buddypress()->theme_compat->active = $set;

	return (bool) buddypress()->theme_compat->active;
}

/**
 * Set the theme compat templates global.
 *
 * Stash possible template files for the current query. Useful if plugins want
 * to override them, or see what files are being scanned for inclusion.
 *
 * @since 1.7.0
 *
 * @param array $templates The template stack.
 *
 * @return array The template stack (value of $templates).
 */
function bp_set_theme_compat_templates( $templates = array() ) {
	buddypress()->theme_compat->templates = $templates;

	return buddypress()->theme_compat->templates;
}

/**
 * Set the theme compat template global.
 *
 * Stash the template file for the current query. Useful if plugins want
 * to override it, or see what file is being included.
 *
 * @since 1.7.0
 *
 * @param string $template The template currently in use.
 *
 * @return string The template currently in use (value of $template).
 */
function bp_set_theme_compat_template( $template = '' ) {
	buddypress()->theme_compat->template = $template;

	return buddypress()->theme_compat->template;
}

/**
 * Set the theme compat original_template global.
 *
 * Stash the original template file for the current query. Useful for checking
 * if BuddyPress was able to find a more appropriate template.
 *
 * @since 1.7.0
 *
 * @param string $template The template originally selected by WP.
 *
 * @return string The template originally selected by WP (value of $template).
 */
function bp_set_theme_compat_original_template( $template = '' ) {
	buddypress()->theme_compat->original_template = $template;

	return buddypress()->theme_compat->original_template;
}

/**
 * Set a theme compat feature
 *
 * @since 2.4.0
 *
 * @param  string $theme_id the theme id (eg: legacy)
 * @param  array  $feature  an associative array (eg: array( name => 'feature_name', 'settings' => array() ))
 */
function bp_set_theme_compat_feature( $theme_id, $feature = array() ) {
	if ( empty( $theme_id ) || empty( $feature['name'] ) ) {
		return;
	}

	// Get BuddyPress instance
	$bp = buddypress();

	// Get current theme compat theme
	$theme_compat_theme = $bp->theme_compat->theme;

	// Bail if the Theme Compat theme is not in use
	if ( $theme_id !== bp_get_theme_compat_id() ) {
		return;
	}

	$features = $theme_compat_theme->__get( 'features' );
	if ( empty( $features ) ) {
		$features = array();
	}

	// Bail if the feature is already registered or no settings were provided
	if ( isset( $features[ $feature['name'] ] ) || empty( $feature['settings'] ) ) {
		return;
	}

	// Add the feature
	$features[ $feature['name'] ] = (object) $feature['settings'];

	// The feature is attached to components
	if ( isset( $features[ $feature['name'] ]->components ) ) {
		// Set the feature for each concerned component
		foreach ( (array) $features[ $feature['name'] ]->components as $component ) {
			// The xProfile component is specific
			if ( 'xprofile' === $component ) {
				$component = 'profile';
			}

			if ( isset( $bp->{$component} ) ) {
				if ( isset( $bp->{$component}->features ) ) {
					$bp->{$component}->features[] = $feature['name'];
				} else {
					$bp->{$component}->features = array( $feature['name'] );
				}
			}
		}
	}

	// Finally update the theme compat features
	$theme_compat_theme->__set( 'features', $features );
}

/**
 * Get a theme compat feature
 *
 * @since 2.4.0
 *
 * @param  string $feature the feature (eg: cover_image)
 * @return object          the feature settings.
 */
function bp_get_theme_compat_feature( $feature = '' ) {
	// Get current theme compat theme
	$theme_compat_theme = buddypress()->theme_compat->theme;

	// Get features
	$features = $theme_compat_theme->__get( 'features' );

	if ( ! isset( $features[ $feature ] ) ) {
		return false;
	}

	return $features[ $feature ];
}

/**
 * Setup the theme's features
 *
 * Note: BP Legacy's buddypress-functions.php is not loaded in WP Administration
 * as it's loaded using bp_locate_template(). That's why this function is here.
 *
 * @since 2.4.0
 *
 * @global $content_width the content width of the theme
 */
function bp_register_theme_compat_default_features() {
	global $content_width;

	// Do not set up default features on deactivation
	if ( bp_is_deactivation() ) {
		return;
	}

	// If the current theme doesn't need theme compat, bail at this point.
	if ( ! bp_use_theme_compat_with_current_theme() ) {
		return;
	}

	// Make sure BP Legacy is the Theme Compat in use.
	if ( 'legacy' !== bp_get_theme_compat_id() ) {
		return;
	}

	// Get the theme
	$current_theme = wp_get_theme();
	$theme_handle  = $current_theme->get_stylesheet();
	$parent        = $current_theme->parent();

	if ( $parent ) {
		$theme_handle = $parent->get_stylesheet();
	}

	/**
	 * Since Companion stylesheets, the $content_width is smaller
	 * than the width used by BuddyPress, so we need to manually set the
	 * content width for the concerned themes.
	 *
	 * array( stylesheet => content width used by BuddyPress )
	 */
	$bp_content_widths = array(
		'twentyfifteen'  => 1300,
		'twentyfourteen' => 955,
		'twentythirteen' => 890,
	);

	// Default values
	$bp_content_width = (int) $content_width;
	$bp_handle        = 'bp-legacy-css';

	// Specific to themes having companion stylesheets
	if ( isset( $bp_content_widths[ $theme_handle ] ) ) {
		$bp_content_width = $bp_content_widths[ $theme_handle ];
		$bp_handle        = 'bp-' . $theme_handle;
	}

	if ( is_rtl() ) {
		$bp_handle .= '-rtl';
	}

	$top_offset    = 150;
	$avatar_height = apply_filters( 'bp_core_avatar_full_height', $top_offset );

	if ( $avatar_height > $top_offset ) {
		$top_offset = $avatar_height;
	}

	bp_set_theme_compat_feature( 'legacy', array(
		'name'     => 'cover_image',
		'settings' => array(
			'components'   => array( 'xprofile', 'groups' ),
			'width'        => $bp_content_width,
			'height'       => $top_offset + round( $avatar_height / 2 ),
			'callback'     => 'bp_legacy_theme_cover_image',
			'theme_handle' => $bp_handle,
		),
	) );
}

/**
 * Check whether a given template is the one that WP originally selected to display current page.
 *
 * @since 1.7.0
 *
 * @param string $template The template name to check.
 *
 * @return bool True if the value of $template is the same as the
 *              "original_template" originally selected by WP. Otherwise false.
 */
function bp_is_theme_compat_original_template( $template = '' ) {
	$bp = buddypress();

	if ( empty( $bp->theme_compat->original_template ) ) {
		return false;
	}

	return (bool) ( $bp->theme_compat->original_template == $template );
}

/**
 * Register a new BuddyPress theme package in the active theme packages array.
 *
 * For an example of how this function is used, see:
 * {@link BuddyPress::register_theme_packages()}.
 *
 * @since 1.7.0
 *
 * @see BP_Theme_Compat for a description of the $theme parameter arguments.
 *
 * @param array $theme    See {@link BP_Theme_Compat}.
 * @param bool  $override If true, overrides whatever package is currently set.
 *                        Default: true.
 */
function bp_register_theme_package( $theme = array(), $override = true ) {

	// Create new BP_Theme_Compat object from the $theme array
	if ( is_array( $theme ) ) {
		$theme = new BP_Theme_Compat( $theme );
	}

	// Bail if $theme isn't a proper object
	if ( ! is_a( $theme, 'BP_Theme_Compat' ) ) {
		return;
	}

	// Load up BuddyPress
	$bp = buddypress();

	// Only set if the theme package was not previously registered or if the
	// override flag is set
	if ( empty( $bp->theme_compat->packages[$theme->id] ) || ( true === $override ) ) {
		$bp->theme_compat->packages[$theme->id] = $theme;
	}
}

/**
 * Populate various WordPress globals with dummy data to prevent errors.
 *
 * This dummy data is necessary because theme compatibility essentially fakes
 * WordPress into thinking that there is content where, in fact, there is none
 * (at least, no WordPress post content). By providing dummy data, we ensure
 * that template functions - things like is_page() - don't throw errors.
 *
 * @since 1.7.0
 *
 * @global WP_Query $wp_query WordPress database access object.
 * @global object $post Current post object.
 *
 * @param array $args Array of optional arguments. Arguments parallel the properties
 *                    of {@link WP_Post}; see that class for more details.
 */
function bp_theme_compat_reset_post( $args = array() ) {
	global $wp_query, $post;

	// Switch defaults if post is set
	if ( isset( $wp_query->post ) ) {
		$dummy = wp_parse_args( $args, array(
			'ID'                    => $wp_query->post->ID,
			'post_status'           => $wp_query->post->post_status,
			'post_author'           => $wp_query->post->post_author,
			'post_parent'           => $wp_query->post->post_parent,
			'post_type'             => $wp_query->post->post_type,
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
			'ID'                    => -9999,
			'post_status'           => 'public',
			'post_author'           => 0,
			'post_parent'           => 0,
			'post_type'             => 'page',
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
	 * @see https://bbpress.trac.wordpress.org/ticket/1973
	 */
	if ( ! $wp_query->is_404() ) {
		status_header( 200 );
	}

	// If we are resetting a post, we are in theme compat
	bp_set_theme_compat_active( true );

	// If we are in theme compat, we don't need the 'Edit' post link
	add_filter( 'get_edit_post_link', 'bp_core_filter_edit_post_link', 10, 2 );
}

/**
 * Reset main query vars and filter 'the_content' to output a BuddyPress template part as needed.
 *
 * @since 1.7.0
 *
 * @uses bp_is_single_user() To check if page is single user.
 * @uses bp_get_single_user_template() To get user template.
 * @uses bp_is_single_user_edit() To check if page is single user edit.
 * @uses bp_get_single_user_edit_template() To get user edit template.
 * @uses bp_is_single_view() To check if page is single view.
 * @uses bp_get_single_view_template() To get view template.
 * @uses bp_is_forum_edit() To check if page is forum edit.
 * @uses bp_get_forum_edit_template() To get forum edit template.
 * @uses bp_is_topic_merge() To check if page is topic merge.
 * @uses bp_get_topic_merge_template() To get topic merge template.
 * @uses bp_is_topic_split() To check if page is topic split.
 * @uses bp_get_topic_split_template() To get topic split template.
 * @uses bp_is_topic_edit() To check if page is topic edit.
 * @uses bp_get_topic_edit_template() To get topic edit template.
 * @uses bp_is_reply_edit() To check if page is reply edit.
 * @uses bp_get_reply_edit_template() To get reply edit template.
 * @uses bp_set_theme_compat_template() To set the global theme compat template.
 *
 * @param string $template Template name.
 *
 * @return string $template Template name.
 */
function bp_template_include_theme_compat( $template = '' ) {

	// If the current theme doesn't need theme compat, bail at this point.
	if ( ! bp_use_theme_compat_with_current_theme() ) {
		return $template;
	}

	/**
	 * Fires when resetting main query vars and filtering 'the_content' to output BuddyPress template parts.
	 *
	 * Use this action to execute code that will communicate to BuddyPress's
	 * theme compatibility layer whether or not we're replacing the_content()
	 * with some other template part.
	 *
	 * @since 1.7.0
	 */
	do_action( 'bp_template_include_reset_dummy_post_data' );

	// Bail if the template already matches a BuddyPress template
	if ( ! empty( buddypress()->theme_compat->found_template ) ) {
		return $template;
	}

	/**
	 * If we are relying on BuddyPress's built in theme compatibility to load
	 * the proper content, we need to intercept the_content, replace the
	 * output, and display ours instead.
	 *
	 * To do this, we first remove all filters from 'the_content' and hook
	 * our own function into it, which runs a series of checks to determine
	 * the context, and then uses the built in shortcodes to output the
	 * correct results from inside an output buffer.
	 *
	 * Uses bp_get_theme_compat_templates() to provide fall-backs that
	 * should be coded without superfluous mark-up and logic (prev/next
	 * navigation, comments, date/time, etc...)
	 *
	 * Hook into 'bp_get_buddypress_template' to override the array of
	 * possible templates, or 'bp_buddypress_template' to override the result.
	 */
	if ( bp_is_theme_compat_active() ) {
		$template = bp_get_theme_compat_templates();

		add_filter( 'the_content', 'bp_replace_the_content' );

		// Add BuddyPress's head action to wp_head
		if ( ! has_action( 'wp_head', 'bp_head' ) ) {
			add_action( 'wp_head', 'bp_head' );
		}
	}

	/**
	 * Filters the template name to include.
	 *
	 * @since 1.7.0
	 *
	 * @param string $template Template name.
	 */
	return apply_filters( 'bp_template_include_theme_compat', $template );
}

/**
 * Conditionally replace 'the_content'.
 *
 * Replaces the_content() if the post_type being displayed is one that would
 * normally be handled by BuddyPress, but proper single page templates do not
 * exist in the currently active theme.
 *
 * @since 1.7.0
 *
 * @param string $content Original post content.
 *
 * @return string $content Post content, potentially modified.
 */
function bp_replace_the_content( $content = '' ) {

	// Bail if not the main loop where theme compat is happening
	if ( ! bp_do_theme_compat() ) {
		return $content;
	}

	// Set theme compat to false early, to avoid recursion from nested calls to
	// the_content() that execute before theme compat has unhooked itself.
	bp_set_theme_compat_active( false );

	/**
	 * Filters the content to replace in the post.
	 *
	 * @since 1.7.0
	 *
	 * @param string $content Original post content.
	 */
	$new_content = apply_filters( 'bp_replace_the_content', $content );

	// Juggle the content around and try to prevent unsightly comments
	if ( ! empty( $new_content ) && ( $new_content !== $content ) ) {

		// Set the content to be the new content
		$content = $new_content;

		// Clean up after ourselves
		unset( $new_content );

		// Reset the $post global
		wp_reset_postdata();
	}

	// Return possibly hi-jacked content
	return $content;
}

/**
 * Are we currently replacing the_content?
 *
 * @since 1.8.0
 *
 * @return bool True if the_content is currently in the process of being
 *              filtered and replaced.
 */
function bp_do_theme_compat() {
	return (bool) ( ! bp_is_template_included() && in_the_loop() && bp_is_theme_compat_active() );
}

/** Filters *******************************************************************/

/**
 * Remove all filters from a WordPress filter hook.
 *
 * Removed filters are stashed in the $bp global, in case they need to be
 * restored later.
 *
 * @since 1.7.0
 *
 * @global WP_filter $wp_filter
 * @global array $merged_filters
 *
 * @param string   $tag      The filter tag to remove filters from.
 * @param int|bool $priority Optional. If present, only those callbacks attached
 *                           at a given priority will be removed. Otherwise, all callbacks
 *                           attached to the tag will be removed, regardless of priority.
 *
 * @return bool True on success.
 */
function bp_remove_all_filters( $tag, $priority = false ) {
	global $wp_filter, $merged_filters;

	$bp = buddypress();

	// Filters exist
	if ( isset( $wp_filter[$tag] ) ) {

		// Filters exist in this priority
		if ( ! empty( $priority ) && isset( $wp_filter[$tag][$priority] ) ) {

			// Store filters in a backup
			$bp->filters->wp_filter[$tag][$priority] = $wp_filter[$tag][$priority];

			// Unset the filters
			unset( $wp_filter[$tag][$priority] );

		// Priority is empty
		} else {

			// Store filters in a backup
			$bp->filters->wp_filter[$tag] = $wp_filter[$tag];

			// Unset the filters
			unset( $wp_filter[$tag] );
		}
	}

	// Check merged filters
	if ( isset( $merged_filters[$tag] ) ) {

		// Store filters in a backup
		$bp->filters->merged_filters[$tag] = $merged_filters[$tag];

		// Unset the filters
		unset( $merged_filters[$tag] );
	}

	return true;
}

/**
 * Restore filters that were removed using bp_remove_all_filters().
 *
 * @since 1.7.0
 *
 * @global WP_filter $wp_filter
 * @global array $merged_filters
 *
 * @param string   $tag      The tag to which filters should be restored.
 * @param int|bool $priority Optional. If present, only those filters that were originally
 *                           attached to the tag with $priority will be restored. Otherwise,
 *                           all available filters will be restored, regardless of priority.
 *
 * @return bool True on success.
 */
function bp_restore_all_filters( $tag, $priority = false ) {
	global $wp_filter, $merged_filters;

	$bp = buddypress();

	// Filters exist
	if ( isset( $bp->filters->wp_filter[$tag] ) ) {

		// Filters exist in this priority
		if ( ! empty( $priority ) && isset( $bp->filters->wp_filter[$tag][$priority] ) ) {

			// Store filters in a backup
			$wp_filter[$tag][$priority] = $bp->filters->wp_filter[$tag][$priority];

			// Unset the filters
			unset( $bp->filters->wp_filter[$tag][$priority] );

		// Priority is empty
		} else {

			// Store filters in a backup
			$wp_filter[$tag] = $bp->filters->wp_filter[$tag];

			// Unset the filters
			unset( $bp->filters->wp_filter[$tag] );
		}
	}

	// Check merged filters
	if ( isset( $bp->filters->merged_filters[$tag] ) ) {

		// Store filters in a backup
		$merged_filters[$tag] = $bp->filters->merged_filters[$tag];

		// Unset the filters
		unset( $bp->filters->merged_filters[$tag] );
	}

	return true;
}

/**
 * Force comments_status to 'closed' for BuddyPress post types.
 *
 * @since 1.7.0
 *
 * @param bool $open    True if open, false if closed.
 * @param int  $post_id ID of the post to check.
 *
 * @return bool True if open, false if closed.
 */
function bp_comments_open( $open, $post_id = 0 ) {

	$retval = is_buddypress() ? false : $open;

	/**
	 * Filters whether or not to force comments_status to closed for BuddyPress post types.
	 *
	 * @since 1.7.0
	 *
	 * @param bool $retval  Whether or not we are on a BuddyPress post type.
	 * @param bool $open    True if comments open, false if closed.
	 * @param int  $post_id Post ID for the checked post.
	 */
	return apply_filters( 'bp_force_comment_status', $retval, $open, $post_id );
}

/**
 * Do not allow {@link comments_template()} to render during theme compatibility.
 *
 * When theme compatibility sets the 'is_page' flag to true via
 * {@link bp_theme_compat_reset_post()}, themes that use comments_template()
 * in their page template will run.
 *
 * To prevent comments_template() from rendering, we set the 'is_page' and
 * 'is_single' flags to false since that function looks at these conditionals
 * before querying the database for comments and loading the comments template.
 *
 * This is done during the output buffer as late as possible to prevent any
 * wonkiness.
 *
 * @since 1.9.2
 *
 * @param string $retval The current post content.
 *
 * @return string $retval
 */
function bp_theme_compat_toggle_is_page( $retval = '' ) {
	global $wp_query;

	$wp_query->is_page = false;

	// Set a switch so we know that we've toggled these WP_Query properties
	buddypress()->theme_compat->is_page_toggled = true;

	return $retval;
}
add_filter( 'bp_replace_the_content', 'bp_theme_compat_toggle_is_page', 9999 );

/**
 * Restores the 'is_single' and 'is_page' flags if toggled by BuddyPress.
 *
 * @since 1.9.2
 *
 * @see bp_theme_compat_toggle_is_page()
 * @param object $query The WP_Query object.
 */
function bp_theme_compat_loop_end( $query ) {

	// Get BuddyPress
	$bp = buddypress();

	// Bail if page is not toggled
	if ( ! isset( $bp->theme_compat->is_page_toggled ) ) {
		return;
	}

	// Revert our toggled WP_Query properties
	$query->is_page = true;

	// Unset our switch
	unset( $bp->theme_compat->is_page_toggled );
}
add_action( 'loop_end', 'bp_theme_compat_loop_end' );

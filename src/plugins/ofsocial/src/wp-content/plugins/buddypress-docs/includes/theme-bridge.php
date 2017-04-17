<?php

/**
 * The functions in this file are used to load template files in the non-BP
 * sections of BP Docs
 *
 * Uses BP's theme compatibility layer, when it's available
 *
 * @since 1.2
 */

// Exit if accessed directly
if ( !defined( 'ABSPATH' ) ) exit;

/**
 * Possibly intercept the template being loaded
 *
 * This function does two different things, depending on whether you're using BP
 * 1.7's theme compatibility feature.
 *  - If so, the function runs the 'bp_setup_theme_compat' hook, which tells BP
 *    to run the theme compat layer
 *  - If not, the function checks to see which page you intend to be looking at
 *    and loads the correct top-level bp-docs template
 *
 * The theme compatibility feature kicks in automatically for users running BP
 * 1.7+. If you are running 1.7+, but you do not want theme compat running for
 * a given Docs template type (archive, single, create), you can filter
 * 'bp_docs_do_theme_compat' and return false. This should only be done in the
 * case of legacy templates; if you're customizing new top-level templates for
 * Docs, you may put a file called plugin-buddypress-docs.php into the root of
 * your theme.
 *
 * @since 1.2
 *
 * @param string $template
 *
 * @return string The path to the template file that is being used
 */
function bp_docs_template_include( $template = '' ) {

	if ( ! bp_docs_is_docs_component() ) {
		return $template;
	}

	$do_theme_compat = bp_docs_do_theme_compat();

	if ( $do_theme_compat ) {

		do_action( 'bp_setup_theme_compat' );

	} else {

		if ( bp_docs_is_single_doc() && ( $new_template = bp_docs_locate_template( 'single-bp_doc.php' ) ) ) :

		elseif ( bp_docs_is_doc_create() && ( $new_template = bp_docs_locate_template( 'single-bp_doc.php' ) ) ) :

		elseif ( is_post_type_archive( bp_docs_get_post_type_name() ) && $new_template = bp_docs_locate_template( 'archive-bp_doc.php' ) ) :

		endif;

		$template = !empty( $new_template ) ? $new_template : $template;
	}

	return apply_filters( 'bp_docs_template_include', $template );
}
add_filter( 'template_include', 'bp_docs_template_include', 6 );

/**
 * Should we do theme compatibility?
 *
 * Do it whenever it's available in BuddyPress (whether enabled or not for the
 * theme more generally)
 *
 * @since 1.5.6
 *
 * @return bool
 */
function bp_docs_do_theme_compat( $template = false ) {
	if ( ! class_exists( 'BP_Theme_Compat' ) ) {
		return false;
	}

	// Pre-theme-compat templates are not available for user tabs, so we
	// force theme compat in these cases
	if ( bp_is_user() ) {
		return true;
	}

	return apply_filters( 'bp_docs_do_theme_compat', true, $template );
}

/**
 * Theme Compat
 *
 * @since 1.3
 */
class BP_Docs_Theme_Compat {

	/**
	 * Setup the members component theme compatibility
	 *
	 * @since 1.3
	 */
	public function __construct() {
		add_action( 'bp_setup_theme_compat', array( $this, 'is_docs' ) );
	}

	/**
	 * Are we looking at something that needs docs theme compatability?
	 *
	 * @since 1.3
	 */
	public function is_docs() {

		// Bail if not looking at the docs component
		if ( ! bp_docs_is_docs_component() )
			return;

		add_filter( 'bp_get_template_stack', array( $this, 'add_plugin_templates_to_stack' ) );

		add_filter( 'bp_get_buddypress_template', array( $this, 'query_templates' ) );

		add_filter( 'bp_use_theme_compat_with_current_theme', 'bp_docs_do_theme_compat' );

		if ( bp_docs_is_global_directory() || bp_docs_is_mygroups_directory() ) {

			bp_update_is_directory( true, 'docs' );
			do_action( 'bp_docs_screen_index' );

			add_action( 'bp_template_include_reset_dummy_post_data', array( $this, 'directory_dummy_post' ) );
			add_filter( 'bp_replace_the_content', array( $this, 'directory_content' ) );

		} else if ( bp_docs_is_existing_doc() ) {

			if ( bp_docs_is_doc_history() ) {
				$this->single_content_template = 'docs/single/history';
				add_filter( 'bp_force_comment_status', '__return_false' );
			} else if ( bp_docs_is_doc_edit() ) {
				$this->single_content_template = 'docs/single/edit';
				add_filter( 'bp_force_comment_status', '__return_false' );
			} else {
				$this->single_content_template = 'docs/single/index';
				add_filter( 'bp_docs_allow_comment_section', '__return_false' );

				// Necessary as of BP 1.9.2
				remove_action( 'bp_replace_the_content', 'bp_theme_compat_toggle_is_page', 9999 );
			}

			add_action( 'bp_template_include_reset_dummy_post_data', array( $this, 'single_dummy_post' ) );
			add_filter( 'bp_replace_the_content',                    array( $this, 'single_content'    ) );

		} else if ( bp_docs_is_doc_create() ) {
			add_action( 'bp_template_include_reset_dummy_post_data', array( $this, 'create_dummy_post' ) );
			add_filter( 'bp_replace_the_content',                    array( $this, 'create_content'    ) );
		}
	}

	/**
	 * Add the plugin's template location to the stack
	 *
	 * Docs provides its own templates for fallback support with any theme
	 *
	 * @since 1.3
	 */
	function add_plugin_templates_to_stack( $stack ) {
		$stack[] = BP_DOCS_INCLUDES_PATH . 'templates';
		return $stack;
	}

	/**
	 * Add our custom top-level query template to the top of the query
	 * template stack
	 *
	 * This ensures that users can provide a Docs-specific template at the
	 * top-level of the rendering stack
	 *
	 * @since 1.3
	 */
	function query_templates( $templates ) {
		$templates = array_merge( array( 'plugin-buddypress-docs.php' ), $templates );
		return $templates;
	}

	/** Directory *************************************************************/

	/**
	 * Update the global $post with directory data
	 *
	 * @since 1.3
	 */
	public function directory_dummy_post() {
		bp_theme_compat_reset_post( array(
			'ID'             => 0,
			'post_title'     => __( 'Docs Directory', 'bp-docs' ),
			'post_author'    => 0,
			'post_date'      => 0,
			'post_content'   => '',
			'post_type'      => bp_docs_get_post_type_name(),
			'post_status'    => 'publish',
			'is_archive'     => true,
			'comment_status' => 'closed'
		) );
	}

	/**
	 * Filter the_content with the docs index template part
	 *
	 * @since 1.3
	 */
	public function directory_content() {
		bp_buffer_template_part( 'docs/docs-loop' );
	}

	/** Single ****************************************************************/

	/**
	 * We're not setting a dummy post for our post type, but we do need to
	 * activate theme compat
	 *
	 * @todo This seems very wrong. Figure it out
	 *
	 * @since 1.3
	 */
	public function single_dummy_post() {
		bp_set_theme_compat_active();
	}

	/**
	 * Filter the_content with the single doc template part
	 *
	 * We return ' ' as a hack, to make sure that the_content doesn't
	 * display extra crap at the end of our documents
	 *
	 * @since 1.3
	 */
	public function single_content() {

		bp_buffer_template_part( $this->single_content_template );
		return ' ';
	}

	/** Create ****************************************************************/

	/**
	 * Update the global $post with dummy data regarding doc creation
	 *
	 * @since 1.3
	 */
	public function create_dummy_post() {
		bp_theme_compat_reset_post( array(
			'ID'             => 0,
			'post_title'     => __( 'Create a Doc', 'bp-docs' ),
			'post_author'    => get_current_user_id(),
			'post_date'      => 0,
			'post_content'   => '',
			'post_type'      => bp_docs_get_post_type_name(),
			'post_status'    => 'publish',
			'is_archive'     => true,
			'comment_status' => 'closed'
		) );
	}

	/**
	 * Filter the_content with the doc creation template part
	 *
	 * @since 1.3
	 */
	public function create_content() {
		bp_buffer_template_part( 'docs/single/edit' );
	}
}
new BP_Docs_Theme_Compat();

/**
 * Wrapper function for bp_is_theme_compat_active()
 *
 * Needed for backward compatibility with BP < 1.7
 *
 * @since 1.3
 * @return bool
 */
function bp_docs_is_theme_compat_active() {
	$is_active = false;

	if ( function_exists( 'bp_is_theme_compat_active' ) ) {
		$is_active = bp_is_theme_compat_active();
	}

	return $is_active;
}

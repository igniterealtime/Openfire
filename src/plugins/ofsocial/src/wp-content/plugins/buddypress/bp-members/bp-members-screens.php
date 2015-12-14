<?php
/**
 * BuddyPress Member Screens.
 *
 * Handlers for member screens that aren't handled elsewhere.
 *
 * @package BuddyPress
 * @subpackage MembersScreens
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Handle the display of the profile page by loading the correct template file.
 */
function bp_members_screen_display_profile() {

	/**
	 * Fires right before the loading of the Member profile screen template file.
	 *
	 * @since 1.5.0
	 */
	do_action( 'bp_members_screen_display_profile' );

	/**
	 * Filters the template to load for the Member profile page screen.
	 *
	 * @since 1.5.0
	 *
	 * @param string $template Path to the Member template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_members_screen_display_profile', 'members/single/home' ) );
}

/**
 * Handle the display of the members directory index.
 */
function bp_members_screen_index() {
	if ( bp_is_members_directory() ) {
		bp_update_is_directory( true, 'members' );

		/**
		 * Fires right before the loading of the Member directory index screen template file.
		 *
		 * @since 1.5.0
		 */
		do_action( 'bp_members_screen_index' );

		/**
		 * Filters the template to load for the Member directory page screen.
		 *
		 * @since 1.5.0
		 *
		 * @param string $value Path to the member directory template to load.
		 */
		bp_core_load_template( apply_filters( 'bp_members_screen_index', 'members/index' ) );
	}
}
add_action( 'bp_screens', 'bp_members_screen_index' );

/**
 * Handle the loading of the signup screen.
 */
function bp_core_screen_signup() {
	$bp = buddypress();

	if ( ! bp_is_current_component( 'register' ) || bp_current_action() )
		return;

	// Not a directory.
	bp_update_is_directory( false, 'register' );

	// If the user is logged in, redirect away from here.
	if ( is_user_logged_in() ) {

		$redirect_to = bp_is_component_front_page( 'register' )
			? bp_get_members_directory_permalink()
			: bp_get_root_domain();

		/**
		 * Filters the URL to redirect logged in users to when visiting registration page.
		 *
		 * @since 1.5.1
		 *
		 * @param string $redirect_to URL to redirect user to.
		 */
		bp_core_redirect( apply_filters( 'bp_loggedin_register_page_redirect_to', $redirect_to ) );

		return;
	}

	$bp->signup->step = 'request-details';

 	if ( !bp_get_signup_allowed() ) {
		$bp->signup->step = 'registration-disabled';

	// If the signup page is submitted, validate and save.
	} elseif ( isset( $_POST['signup_submit'] ) && bp_verify_nonce_request( 'bp_new_signup' ) ) {

	    /**
		 * Fires before the validation of a new signup.
		 *
		 * @since 2.0.0
		 */
		do_action( 'bp_signup_pre_validate' );

		// Check the base account details for problems.
		$account_details = bp_core_validate_user_signup( $_POST['signup_username'], $_POST['signup_email'] );

		// If there are errors with account details, set them for display.
		if ( !empty( $account_details['errors']->errors['user_name'] ) )
			$bp->signup->errors['signup_username'] = $account_details['errors']->errors['user_name'][0];

		if ( !empty( $account_details['errors']->errors['user_email'] ) )
			$bp->signup->errors['signup_email'] = $account_details['errors']->errors['user_email'][0];

		// Check that both password fields are filled in.
		if ( empty( $_POST['signup_password'] ) || empty( $_POST['signup_password_confirm'] ) )
			$bp->signup->errors['signup_password'] = __( 'Please make sure you enter your password twice', 'buddypress' );

		// Check that the passwords match.
		if ( ( !empty( $_POST['signup_password'] ) && !empty( $_POST['signup_password_confirm'] ) ) && $_POST['signup_password'] != $_POST['signup_password_confirm'] )
			$bp->signup->errors['signup_password'] = __( 'The passwords you entered do not match.', 'buddypress' );

		$bp->signup->username = $_POST['signup_username'];
		$bp->signup->email = $_POST['signup_email'];

		// Now we've checked account details, we can check profile information.
		if ( bp_is_active( 'xprofile' ) ) {

			// Make sure hidden field is passed and populated.
			if ( isset( $_POST['signup_profile_field_ids'] ) && !empty( $_POST['signup_profile_field_ids'] ) ) {

				// Let's compact any profile field info into an array.
				$profile_field_ids = explode( ',', $_POST['signup_profile_field_ids'] );

				// Loop through the posted fields formatting any datebox values then validate the field.
				foreach ( (array) $profile_field_ids as $field_id ) {
					if ( !isset( $_POST['field_' . $field_id] ) ) {
						if ( !empty( $_POST['field_' . $field_id . '_day'] ) && !empty( $_POST['field_' . $field_id . '_month'] ) && !empty( $_POST['field_' . $field_id . '_year'] ) )
							$_POST['field_' . $field_id] = date( 'Y-m-d H:i:s', strtotime( $_POST['field_' . $field_id . '_day'] . $_POST['field_' . $field_id . '_month'] . $_POST['field_' . $field_id . '_year'] ) );
					}

					// Create errors for required fields without values.
					if ( xprofile_check_is_required_field( $field_id ) && empty( $_POST[ 'field_' . $field_id ] ) && ! bp_current_user_can( 'bp_moderate' ) )
						$bp->signup->errors['field_' . $field_id] = __( 'This is a required field', 'buddypress' );
				}

			// This situation doesn't naturally occur so bounce to website root.
			} else {
				bp_core_redirect( bp_get_root_domain() );
			}
		}

		// Finally, let's check the blog details, if the user wants a blog and blog creation is enabled.
		if ( isset( $_POST['signup_with_blog'] ) ) {
			$active_signup = bp_core_get_root_option( 'registration' );

			if ( 'blog' == $active_signup || 'all' == $active_signup ) {
				$blog_details = bp_core_validate_blog_signup( $_POST['signup_blog_url'], $_POST['signup_blog_title'] );

				// If there are errors with blog details, set them for display.
				if ( !empty( $blog_details['errors']->errors['blogname'] ) )
					$bp->signup->errors['signup_blog_url'] = $blog_details['errors']->errors['blogname'][0];

				if ( !empty( $blog_details['errors']->errors['blog_title'] ) )
					$bp->signup->errors['signup_blog_title'] = $blog_details['errors']->errors['blog_title'][0];
			}
		}

	    /**
		 * Fires after the validation of a new signup.
		 *
		 * @since 1.1.0
		 */
		do_action( 'bp_signup_validate' );

		// Add any errors to the action for the field in the template for display.
		if ( !empty( $bp->signup->errors ) ) {
			foreach ( (array) $bp->signup->errors as $fieldname => $error_message ) {
				/*
				 * The addslashes() and stripslashes() used to avoid create_function()
				 * syntax errors when the $error_message contains quotes.
				 */

				/**
				 * Filters the error message in the loop.
				 *
				 * @since 1.5.0
				 *
				 * @param string $value Error message wrapped in html.
				 */
				add_action( 'bp_' . $fieldname . '_errors', create_function( '', 'echo apply_filters(\'bp_members_signup_error_message\', "<div class=\"error\">" . stripslashes( \'' . addslashes( $error_message ) . '\' ) . "</div>" );' ) );
			}
		} else {
			$bp->signup->step = 'save-details';

			// No errors! Let's register those deets.
			$active_signup = bp_core_get_root_option( 'registration' );

			if ( 'none' != $active_signup ) {

				// Make sure the extended profiles module is enabled.
				if ( bp_is_active( 'xprofile' ) ) {
					// Let's compact any profile field info into usermeta.
					$profile_field_ids = explode( ',', $_POST['signup_profile_field_ids'] );

					// Loop through the posted fields formatting any datebox values then add to usermeta - @todo This logic should be shared with the same in xprofile_screen_edit_profile().
					foreach ( (array) $profile_field_ids as $field_id ) {
						if ( ! isset( $_POST['field_' . $field_id] ) ) {

							if ( ! empty( $_POST['field_' . $field_id . '_day'] ) && ! empty( $_POST['field_' . $field_id . '_month'] ) && ! empty( $_POST['field_' . $field_id . '_year'] ) ) {
								// Concatenate the values.
								$date_value = $_POST['field_' . $field_id . '_day'] . ' ' . $_POST['field_' . $field_id . '_month'] . ' ' . $_POST['field_' . $field_id . '_year'];

								// Turn the concatenated value into a timestamp.
								$_POST['field_' . $field_id] = date( 'Y-m-d H:i:s', strtotime( $date_value ) );
							}
						}

						if ( !empty( $_POST['field_' . $field_id] ) )
							$usermeta['field_' . $field_id] = $_POST['field_' . $field_id];

						if ( !empty( $_POST['field_' . $field_id . '_visibility'] ) )
							$usermeta['field_' . $field_id . '_visibility'] = $_POST['field_' . $field_id . '_visibility'];
					}

					// Store the profile field ID's in usermeta.
					$usermeta['profile_field_ids'] = $_POST['signup_profile_field_ids'];
				}

				// Hash and store the password.
				$usermeta['password'] = wp_hash_password( $_POST['signup_password'] );

				// If the user decided to create a blog, save those details to usermeta.
				if ( 'blog' == $active_signup || 'all' == $active_signup )
					$usermeta['public'] = ( isset( $_POST['signup_blog_privacy'] ) && 'public' == $_POST['signup_blog_privacy'] ) ? true : false;

				/**
				 * Filters the user meta used for signup.
				 *
				 * @since 1.1.0
				 *
				 * @param array $usermeta Array of user meta to add to signup.
				 */
				$usermeta = apply_filters( 'bp_signup_usermeta', $usermeta );

				// Finally, sign up the user and/or blog.
				if ( isset( $_POST['signup_with_blog'] ) && is_multisite() )
					$wp_user_id = bp_core_signup_blog( $blog_details['domain'], $blog_details['path'], $blog_details['blog_title'], $_POST['signup_username'], $_POST['signup_email'], $usermeta );
				else
					$wp_user_id = bp_core_signup_user( $_POST['signup_username'], $_POST['signup_password'], $_POST['signup_email'], $usermeta );

				if ( is_wp_error( $wp_user_id ) ) {
					$bp->signup->step = 'request-details';
					bp_core_add_message( $wp_user_id->get_error_message(), 'error' );
				} else {
					$bp->signup->step = 'completed-confirmation';
				}
			}

			/**
			 * Fires after the completion of a new signup.
			 *
			 * @since 1.1.0
			 */
			do_action( 'bp_complete_signup' );
		}

	}

	/**
	 * Fires right before the loading of the Member registration screen template file.
	 *
	 * @since 1.5.0
	 */
	do_action( 'bp_core_screen_signup' );

	/**
	 * Filters the template to load for the Member registration page screen.
	 *
	 * @since 1.5.0
	 *
	 * @param string $value Path to the Member registration template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_core_template_register', array( 'register', 'registration/register' ) ) );
}
add_action( 'bp_screens', 'bp_core_screen_signup' );

/**
 * Handle the loading of the Activate screen.
 *
 * @todo Move the actual activation process into an action in bp-members-actions.php
 */
function bp_core_screen_activation() {

	// Bail if not viewing the activation page.
	if ( ! bp_is_current_component( 'activate' ) ) {
		return false;
	}

	// If the user is already logged in, redirect away from here.
	if ( is_user_logged_in() ) {

		// If activation page is also front page, set to members directory to
		// avoid an infinite loop. Otherwise, set to root domain.
		$redirect_to = bp_is_component_front_page( 'activate' )
			? bp_get_members_directory_permalink()
			: bp_get_root_domain();

		// Trailing slash it, as we expect these URL's to be.
		$redirect_to = trailingslashit( $redirect_to );

		/**
		 * Filters the URL to redirect logged in users to when visiting activation page.
		 *
		 * @since 1.9.0
		 *
		 * @param string $redirect_to URL to redirect user to.
		 */
		$redirect_to = apply_filters( 'bp_loggedin_activate_page_redirect_to', $redirect_to );

		// Redirect away from the activation page.
		bp_core_redirect( $redirect_to );
	}

	// Grab the key (the old way).
	$key = isset( $_GET['key'] ) ? $_GET['key'] : '';

	// Grab the key (the new way).
	if ( empty( $key ) ) {
		$key = bp_current_action();
	}

	// Get BuddyPress.
	$bp = buddypress();

	// We've got a key; let's attempt to activate the signup.
	if ( ! empty( $key ) ) {

		/**
		 * Filters the activation signup.
		 *
		 * @since 1.1.0
		 *
		 * @param bool|int $value Value returned by activation.
		 *                        Integer on success, boolean on failure.
		 */
		$user = apply_filters( 'bp_core_activate_account', bp_core_activate_signup( $key ) );

		// If there were errors, add a message and redirect.
		if ( ! empty( $user->errors ) ) {
			bp_core_add_message( $user->get_error_message(), 'error' );
			bp_core_redirect( trailingslashit( bp_get_root_domain() . '/' . $bp->pages->activate->slug ) );
		}

		$hashed_key = wp_hash( $key );

		// Check if the signup avatar folder exists. If it does, move the folder to
		// the BP user avatars directory.
		if ( file_exists( bp_core_avatar_upload_path() . '/avatars/signups/' . $hashed_key ) ) {
			@rename( bp_core_avatar_upload_path() . '/avatars/signups/' . $hashed_key, bp_core_avatar_upload_path() . '/avatars/' . $user );
		}

		bp_core_add_message( __( 'Your account is now active!', 'buddypress' ) );
		$bp->activation_complete = true;
	}

	/**
	 * Filters the template to load for the Member activation page screen.
	 *
	 * @since 1.1.1
	 *
	 * @param string $value Path to the Member activation template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_core_template_activate', array( 'activate', 'registration/activate' ) ) );
}
add_action( 'bp_screens', 'bp_core_screen_activation' );

/** Theme Compatibility *******************************************************/

/**
 * The main theme compat class for BuddyPress Members.
 *
 * This class sets up the necessary theme compatibility actions to safely output
 * member template parts to the_title and the_content areas of a theme.
 *
 * @since 1.7.0
 */
class BP_Members_Theme_Compat {

	/**
	 * Set up the members component theme compatibility.
	 *
	 * @since 1.7.0
	 */
	public function __construct() {
		add_action( 'bp_setup_theme_compat', array( $this, 'is_members' ) );
	}

	/**
	 * Are we looking at something that needs members theme compatibility?
	 *
	 * @since 1.7.0
	 */
	public function is_members() {

		// Bail if not looking at the members component or a user's page.
		if ( ! bp_is_members_component() && ! bp_is_user() ) {
			return;
		}

		// Members Directory.
		if ( ! bp_current_action() && ! bp_current_item() ) {
			bp_update_is_directory( true, 'members' );

			/**
			 * Fires if looking at Members directory when needing theme compat.
			 *
			 * @since 1.5.0
			 */
			do_action( 'bp_members_screen_index' );

			add_filter( 'bp_get_buddypress_template',                array( $this, 'directory_template_hierarchy' ) );
			add_action( 'bp_template_include_reset_dummy_post_data', array( $this, 'directory_dummy_post' ) );
			add_filter( 'bp_replace_the_content',                    array( $this, 'directory_content'    ) );

		// User page.
		} elseif ( bp_is_user() ) {

			// If we're on a single activity permalink page, we shouldn't use the members
			// template, so stop here!
			if ( bp_is_active( 'activity' ) && bp_is_single_activity() ) {
				return;
			}

			/**
			 * Fires if looking at Members user page when needing theme compat.
			 *
			 * @since 1.5.0
			 */
			do_action( 'bp_members_screen_display_profile' );

			add_filter( 'bp_get_buddypress_template',                array( $this, 'single_template_hierarchy' ) );
			add_action( 'bp_template_include_reset_dummy_post_data', array( $this, 'single_dummy_post'    ) );
			add_filter( 'bp_replace_the_content',                    array( $this, 'single_dummy_content' ) );

		}
	}

	/** Directory *************************************************************/

	/**
	 * Add template hierarchy to theme compat for the members directory page.
	 *
	 * This is to mirror how WordPress has
	 * {@link https://codex.wordpress.org/Template_Hierarchy template hierarchy}.
	 *
	 * @since 1.8.0
	 *
	 * @param array $templates The templates from bp_get_theme_compat_templates().
	 * @return array $templates Array of custom templates to look for.
	 */
	public function directory_template_hierarchy( $templates = array() ) {

		// Set up the template hierarchy.
		$new_templates = array();
		if ( '' !== bp_get_current_member_type() ) {
			$new_templates[] = 'members/index-directory-type-' . sanitize_file_name( bp_get_current_member_type() ) . '.php';
		}
		$new_templates[] = 'members/index-directory.php';

		/**
		 * Filters the template hierarchy for theme compat and members directory page.
		 *
		 * @since 1.8.0
		 *
		 * @param array $value Array of template paths to add to hierarchy.
		 */
		$new_templates = apply_filters( 'bp_template_hierarchy_members_directory', $new_templates );

		// Merge new templates with existing stack
		// @see bp_get_theme_compat_templates().
		$templates = array_merge( (array) $new_templates, $templates );

		return $templates;
	}

	/**
	 * Update the global $post with directory data.
	 *
	 * @since 1.7.0
	 */
	public function directory_dummy_post() {
		bp_theme_compat_reset_post( array(
			'ID'             => 0,
			'post_title'     => bp_get_directory_title( 'members' ),
			'post_author'    => 0,
			'post_date'      => 0,
			'post_content'   => '',
			'post_type'      => 'page',
			'post_status'    => 'publish',
			'is_page'        => true,
			'comment_status' => 'closed'
		) );
	}

	/**
	 * Filter the_content with the members index template part.
	 *
	 * @since 1.7.0
	 */
	public function directory_content() {
		return bp_buffer_template_part( 'members/index', null, false );
	}

	/** Single ****************************************************************/

	/**
	 * Add custom template hierarchy to theme compat for member pages.
	 *
	 * This is to mirror how WordPress has
	 * {@link https://codex.wordpress.org/Template_Hierarchy template hierarchy}.
	 *
	 * @since 1.8.0
	 *
	 * @param string $templates The templates from
	 *                          bp_get_theme_compat_templates().
	 * @return array $templates Array of custom templates to look for.
	 */
	public function single_template_hierarchy( $templates ) {
		// Setup some variables we're going to reference in our custom templates.
		$user_nicename = buddypress()->displayed_user->userdata->user_nicename;

		/**
		 * Filters the template hierarchy for theme compat and member pages.
		 *
		 * @since 1.8.0
		 *
		 * @param array $value Array of template paths to add to hierarchy.
		 */
		$new_templates = apply_filters( 'bp_template_hierarchy_members_single_item', array(
			'members/single/index-id-'        . sanitize_file_name( bp_displayed_user_id() ) . '.php',
			'members/single/index-nicename-'  . sanitize_file_name( $user_nicename )         . '.php',
			'members/single/index-action-'    . sanitize_file_name( bp_current_action() )    . '.php',
			'members/single/index-component-' . sanitize_file_name( bp_current_component() ) . '.php',
			'members/single/index.php'
		) );

		// Merge new templates with existing stack
		// @see bp_get_theme_compat_templates().
		$templates = array_merge( (array) $new_templates, $templates );

		return $templates;
	}

	/**
	 * Update the global $post with the displayed user's data.
	 *
	 * @since 1.7.0
	 */
	public function single_dummy_post() {
		bp_theme_compat_reset_post( array(
			'ID'             => 0,
			'post_title'     => bp_get_displayed_user_fullname(),
			'post_author'    => 0,
			'post_date'      => 0,
			'post_content'   => '',
			'post_type'      => 'page',
			'post_status'    => 'publish',
			'is_page'        => true,
			'comment_status' => 'closed'
		) );
	}

	/**
	 * Filter the_content with the members' single home template part.
	 *
	 * @since 1.7.0
	 */
	public function single_dummy_content() {
		return bp_buffer_template_part( 'members/single/home', null, false );
	}
}
new BP_Members_Theme_Compat();

/**
 * The main theme compat class for BuddyPress Registration.
 *
 * This class sets up the necessary theme compatibility actions to safely output
 * registration template parts to the_title and the_content areas of a theme.
 *
 * @since 1.7.0
 */
class BP_Registration_Theme_Compat {

	/**
	 * Setup the groups component theme compatibility.
	 *
	 * @since 1.7.0
	 */
	public function __construct() {
		add_action( 'bp_setup_theme_compat', array( $this, 'is_registration' ) );
	}

	/**
	 * Are we looking at either the registration or activation pages?
	 *
	 * @since 1.7.0
	 */
	public function is_registration() {

		// Bail if not looking at the registration or activation page.
		if ( ! bp_is_register_page() && ! bp_is_activation_page() ) {
			return;
		}

		// Not a directory.
		bp_update_is_directory( false, 'register' );

		// Setup actions.
		add_filter( 'bp_get_buddypress_template',                array( $this, 'template_hierarchy' ) );
		add_action( 'bp_template_include_reset_dummy_post_data', array( $this, 'dummy_post'    ) );
		add_filter( 'bp_replace_the_content',                    array( $this, 'dummy_content' ) );
	}

	/** Template ***********************************************************/

	/**
	 * Add template hierarchy to theme compat for registration/activation pages.
	 *
	 * This is to mirror how WordPress has
	 * {@link https://codex.wordpress.org/Template_Hierarchy template hierarchy}.
	 *
	 * @since 1.8.0
	 *
	 * @param string $templates The templates from bp_get_theme_compat_templates().
	 * @return array $templates Array of custom templates to look for.
	 */
	public function template_hierarchy( $templates ) {
		$component = sanitize_file_name( bp_current_component() );

		/**
		 * Filters the template hierarchy for theme compat and registration/activation pages.
		 *
		 * This filter is a variable filter that depends on the current component
		 * being used.
		 *
		 * @since 1.8.0
		 *
		 * @param array $value Array of template paths to add to hierarchy.
		 */
		$new_templates = apply_filters( "bp_template_hierarchy_{$component}", array(
			"members/index-{$component}.php"
		) );

		// Merge new templates with existing stack
		// @see bp_get_theme_compat_templates().
		$templates = array_merge( (array) $new_templates, $templates );

		return $templates;
	}

	/**
	 * Update the global $post with dummy data.
	 *
	 * @since 1.7.0
	 */
	public function dummy_post() {
		// Registration page.
		if ( bp_is_register_page() ) {
			$title = __( 'Create an Account', 'buddypress' );

			if ( 'completed-confirmation' == bp_get_current_signup_step() ) {
				$title = __( 'Check Your Email To Activate Your Account!', 'buddypress' );
			}

		// Activation page.
		} else {
			$title = __( 'Activate Your Account', 'buddypress' );

			if ( bp_account_was_activated() ) {
				$title = __( 'Account Activated', 'buddypress' );
			}
		}

		bp_theme_compat_reset_post( array(
			'ID'             => 0,
			'post_title'     => $title,
			'post_author'    => 0,
			'post_date'      => 0,
			'post_content'   => '',
			'post_type'      => 'page',
			'post_status'    => 'publish',
			'is_page'        => true,
			'comment_status' => 'closed'
		) );
	}

	/**
	 * Filter the_content with either the register or activate templates.
	 *
	 * @since 1.7.0
	 */
	public function dummy_content() {
		if ( bp_is_register_page() ) {
			return bp_buffer_template_part( 'members/register', null, false );
		} else {
			return bp_buffer_template_part( 'members/activate', null, false );
		}
	}
}
new BP_Registration_Theme_Compat();

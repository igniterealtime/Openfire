<?php
/**
 * Plugin Name: Private BuddyPress
 * Description: Protect your BuddyPress Installation from strangers. Only registered users will be allowed to view the installation.
 * Author: Dennis Morhardt
 * Author URI: http://www.dennismorhardt.de/
 * Plugin URI: http://bp-tutorials.de/
 * Version: 1.0.4
 * Text Domain: private-buddypress
 * Domain Path: /languages
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston,
 * MA 02110-1301 USA
 */

define('PRIVATE_BUDDYPRESS_VERSION', '1.0');

class PrivateBuddyPress {
	var $options;
	var $dbVersion;

	function PrivateBuddyPress() {
		// Run action
		do_action('pbp_init');

		// Load options
		$this->options = get_option('private_buddypress');
		$this->dbVersion = get_option('private_buddypress_version');

		// Load textdomain
		load_plugin_textdomain('private-buddypress', 'languages', dirname(plugin_basename(__FILE__)) . '/languages');

		// Add admin options
		add_action('admin_init', array($this, 'AdminInit'));

		// Add login redirect function
		add_action('wp', array($this, 'LoginRedirect'), 1);
	}

	function AdminInit() {
		// Add settings section
		add_settings_section('private-buddypress', __('BuddyPress Protection', 'private-buddypress'), array($this, 'AdminOptions'), 'privacy');
		add_action('load-options.php', array($this, 'SaveAdminOptions'));

		// Run action
		do_action('pbp_admin_init');
	}

	function Install() {
		// Check if a existing installation
		if ( PRIVATE_BUDDYPRESS_VERSION == get_option( 'private_buddypress_version' ) )
			return;

		// Default options
		$options = new stdClass();
		$options->exclude = new stdClass();
		$options->exclude->homepage = false;
		$options->exclude->registration = false;
		$options->exclude->blogpages = false;

		// Add or update options to database
		update_option('private_buddypress', $options);
		update_option('private_buddypress_version', PRIVATE_BUDDYPRESS_VERSION);
	}

	function IsBuddyPressFeed() {
		// Get BuddyPress
		global $bp;

		// Default value
		$isBuddyPressFeed = false;

		// Check if the current BuddyPress page is a feed
		if ( $bp->current_action == 'feed' || $bp->action_variables[0] == 'feed' )
			$isBuddyPressFeed = true;

		// Return false if no BuddyPress feed has been called
		return apply_filters('pbp_is_buddypress_feed', $isBuddyPressFeed);
	}

	function ProtectBlogFeeds() {
		// Default value
		$protectBlogFeeds = false;

		// If blog pages should be protect, add protection to the feeds
		if ( is_feed() && false == $this->options->exclude->blogpages )
			$protection = true;

		// Filter and return the value
		return apply_filters('pbp_protect_blog_feeds', $protection);
	}

	function LoginRedirect() {
		// Get current position

		if (strpos($_SERVER['REQUEST_URI'], "wl-register.php") > 0)
		{
			return;
		}

		$redirect_to = apply_filters('pbp_redirect_to_after_login', $_SERVER['REQUEST_URI']);

		// Check if user is logged in
		if ( false == is_user_logged_in() ):
			// Run action
			do_action('pbp_login_redirect');

			// Check if current page is a feed
			if ( $this->ProtectBlogFeeds() || $this->IsBuddyPressFeed() ):
				// Try to get saved login credentials
				$credentials = array(
					'user_login' => $_SERVER['PHP_AUTH_USER'],
					'user_password' => $_SERVER['PHP_AUTH_PW']
				);

				// Send headers for authentication
				if ( is_wp_error( wp_signon( $credentials ) ) ):
					header('WWW-Authenticate: Basic realm="' . get_option('blogtitle') . '"');
					header('HTTP/1.0 401 Unauthorized');
					die('<h2>You need to be logged in to view this feed!</h2>');
				endif;
			// Redirect to login page if for current page a is required
			elseif ( $this->LoginRequired() ):
				$loginPage = apply_filters('pbp_redirect_login_page', get_option('siteurl') . '/wp-login.php?redirect_to=' . $redirect_to, $redirect_to);
				wp_redirect($loginPage);
				exit;
			endif;
		endif;
	}

	function LoginRequired() {
		// No login required if homepage is excluded
		if ( true == $this->options->exclude->homepage && is_front_page() )
			return false;

		// No login required if registration is excluded
		if ( true == $this->options->exclude->registration && ( bp_is_register_page() || bp_is_activation_page() ) )
			return false;

		// No login required if blog pages are excluded
		if ( true == $this->options->exclude->blogpages && bp_is_blog_page() )
			return false;

		// Login required
		return apply_filters('pbp_login_required_check', true);
	}

	function SaveAdminOptions() {
		// Check for plausibility
		if ( 'yes' != $_POST["bp_protection_options"] )
			return;

		// Exclude homepage from protection
		if ( '1' == $_POST["bp_protection_exclude_home"] )
			$this->options->exclude->homepage = true;
		else
			$this->options->exclude->homepage = false;

		// Exclude registration from protection
		if ( '1' == $_POST["bp_protection_exclude_registration"] )
			$this->options->exclude->registration = true;
		else
			$this->options->exclude->registration = false;

		// Exclude blog pages from protection
		if ( '1' == $_POST["bp_protection_exclude_blogpages"] )
			$this->options->exclude->blogpages = true;
		else
			$this->options->exclude->blogpages = false;

		// Save options
		update_option('private_buddypress', apply_filters('pbp_pre_options', $this->options));

		// Run action
		do_action('pbp_save_options');
	}

	function AdminOptions() { ?>
		<table class="form-table">
			<tr valign="top">
				<th scope="row"><?php _e('Exclude from protection', 'private-buddypress'); ?></th>
				<td>
					<label for="bp_protection_exclude_home"><input name="bp_protection_exclude_home" id="bp_protection_exclude_home" value="1" <?php checked(true, $this->options->exclude->homepage); ?> type="checkbox"> <?php _e('Front page', 'private-buddypress'); ?></label><br />
					<label for="bp_protection_exclude_blogpages"><input name="bp_protection_exclude_blogpages" id="bp_protection_exclude_blogpages" value="1" <?php checked(true, $this->options->exclude->blogpages); ?> type="checkbox"> <?php _e('Blog pages (posts, archives and non-buddypress pages)', 'private-buddypress'); ?></label><br />
					<label for="bp_protection_exclude_registration"><input name="bp_protection_exclude_registration" id="bp_protection_exclude_registration" value="1" <?php checked(true, $this->options->exclude->registration); ?> type="checkbox"> <?php _e('Registration', 'private-buddypress'); ?></label>
					<input name="bp_protection_options" id="bp_protection_options" type="hidden" value="yes" />
					<?php do_action('pbp_options_page'); ?>
				</td>
			</tr>
		</table>
	<?php }
}

// Add activation hook
register_activation_hook(__FILE__, array('PrivateBuddyPress', 'Install'));

// Init the plugin at WordPress startup
$t = new PrivateBuddyPress();
=== Private BuddyPress ===
Contributors: GIGALinux
Tags: buddypress, protection, privacy, private, protect, hide, community
Requires at least: 3.0, BuddyPress 1.2
Tested up to: 3.1, BuddyPress 1.3
Stable tag: 1.0.4

Protect your BuddyPress Installation from strangers. Only registered users will be allowed to view the installation.

== Description ==

Protect your BuddyPress Installation from strangers. Only registered users will be allowed to view the installation and all other users will be redirected to the login page. Users attempting to view blog content via RSS are also authenticated via HTTP Auth.

You can exclude the registration, the homepage and blog pages (e.g. posts, archives and non-buddypress pages) from protection. In combination with the plugin 'Invitation Code Checker' your installation stays private but the registration is for users with a special password open.

The plugin includes a German and Hebrew (thanks to gstupp) translation.

== Installation ==

Use the automatic plugin installation in the backand or install the plugin manuell:

1. Upload `private-buddypress` to the `/wp-content/plugins/` directory
2. Activate the plugin through the 'Plugins' menu in WordPress

== Frequently Asked Questions ==

= Can I exclude the homepage, the registration or blog pages from protection? =

Yes, you can define the excludes on the settings page unter `Settings -> Privacy`.

= Can I change the URL where non-loggedin users are being redirected? =

Yes, currently you need to write a filter function in your functions.php.

`function redirect_nonloggedin_users($current_uri, $redirect_to) {
	// Redirect users to the homepage
	// Caution! Exclude the homepage from 'Private BuddyPress' options
	// to avoid redirection loops!
	return get_option('siteurl') . '/?from=' . $redirect_to;
}

add_filter('pbp_redirect_login_page', 'redirect_nonloggedin_users', 10, 2);`

= Can I exclude e.g. the blog directory from protection? =

Yes, you need to write a filter:

`function make_blog_directory_visible($visibility) {
	global $bp;

	if ( bp_is_directory() && $bp->current_component == $bp->blogs->slug )
		return false;

	return $visibility;
}

add_filter('pbp_login_required_check', 'make_blog_directory_visible');`

= Are there other actions or filters? =

Yes, currently in Private Buddypress are existing 5 actions:

*   **pbp_init**: Fired when Private BuddyPress is initialised
*   **pbp_admin_init**: Fired when Private BuddyPress in the admin area is initialised
*   **pbp_login_redirect**: Fired when the users is not logged in and is being redirected to the login page or when it is a feed asked for a password
*   **pbp_save_options**: Fired when the options of Private BuddyPress has been changed
*   **pbp_options_page**: Fired on the options page to added more fields for custom options

Also in Private BuddyPress are existing 6 filters:

*   **pbp_is_buddypress_feed**: Boolean value if the current page is a BuddyPress feed
*   **pbp_redirect_to_after_login**: Called URI from where the users came from
*   **pbp_redirect_login_page**: URI where nonloggedin users are being redirected
*   **pbp_login_required_check**: Boolean value if for the current page a login is needed
*   **pbp_pre_options**: Object with the new options before they saved
*   **pbp_protect_blog_feeds**: Boolean value if blog feeds should be protected

== Screenshots ==

1. Settings page, you can find it under `Settings -> Privacy`

== Changelog ==

= 1.0.4 =
* Fixed: If blog pages excluded from protection, don't protect the feeds
* Added: New filter: 'pbp_protect_blog_feeds'
* Added: Hebrew translation, thanks to gstupp

= 1.0.3 =
* Fixed: Options no longer disappear suddenly
* Fixed: BuddyPress feeds are now protected
* Added: Filters and actions, see FAQ for more information

= 1.0.2 =
* Fixed: Saving optings haven't worked correctly
* Added: Blog pages (e.g. posts, archives, non-buddypress pages) can now be excluded from protection

= 1.0.1 =
* Notification update for users who downloaded the plugin before it was finished
* Fixed: Some fatal PHP errors
* Added: Plugin is now translatable
* Added: German translation

= 1.0 =
* First release

== Upgrade Notice ==

= 1.0.4 =
Blog feeds are no longer protected if blog pages are excluded from the protection. Added also a Hebrew translation.

= 1.0.3 =
Options no longer disappear suddenly and BuddyPress feeds are now protected. Update is recommended.

= 1.0.2 =
Saving options now work correctly and added an option to exclude normal blog pages (e.g. posts, archives, non-buddypress pages) from protection.

= 1.0.1 =
Notification update for users who downloaded the plugin before it was finished. Fixed the fatal PHP error and added translations.

= 1.0 =
First release
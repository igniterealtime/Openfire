=== Plugin Name ===
Contributors: Mike_Cowobo
Donate link: http://trenvo.nl/
Tags: buddypress, hovercards
Requires at least: WP 3.2.1, BP 1.5
Tested up to: WP 3.4.2, BP 1.6.1
Stable tag: 1.1.3
License: GPLv2 or later
License URI: http://www.gnu.org/licenses/gpl-2.0.html

Add themable hovercards to your BuddyPress installation.

== Description ==

This plugin adds hovercards (like on WordPress,com (Gravatar), Twitter, Facebook, Google+, etc.) to BuddyPress. Whenever a user hovers over a user avatar, the hovercard shows up.

Hovercards are completely themable by adding a `hovercard.php` template to your (child) theme.

Based on imath's blogpost [on BuddyPress xprofile hovercards](http://imath.owni.fr/2010/11/23/buddypress-xprofile-hovercard/) and uses the jQuery [Tipsy](http://onehackoranother.com/projects/jquery/tipsy/) library and [Rrrene's hovercards](https://github.com/rrrene/tipsy.hovercard).

== Installation ==

1. You can download and install BuddyPress hovercards using the built in WordPress plugin installer. If you download BuddyPress Hovercards manually, upload the whole folder to "/wp-content/plugins/".
1. Activate the plugin through the 'Plugins' menu in WordPress

If you want to add a custom hovercard, or change the displayed fields, copy '/bp-hovercards/templates/hovercard.php' to the root of your (child) theme and edit it there to prevent it being overwritten at an update.

== Frequently Asked Questions ==

= How can I disable hovercards for certain avatars? =

Hovercards are disabled for some avatars already, namely the profile badge and the profile header. To add more disabled elements, you can use the folowing filters:

'bphc_parent_filter' to disable hovercards for all children of a certain element id or class, e.g.:

`function disable_bphc_by_parent( $filter ) {
    return $filter . ', .children-of-this-class';
}
add_filter('bphc_parent_filter', 'disable_bphc_by_parent');`

'bphc_element_filter' to disable hovercards for avatar img tag with this class/id, e.g.:

`function disable_bphc_for_elements ( $filter ) {
    return $filter . ', .avatars-with-this-class, #avatar-with-this-id'
}
add_filter('bphc_parent_filter', 'disable_bphc_by_parent');`

= Can I make my own hovercard? =

Yes. If you want to add a custom hovercard, or change the displayed fields, copy '/bp-hovercards/templates/hovercard.php' to the root of your (child) theme and edit it there to prevent it being overwritten at an update.

== Screenshots ==

1. Example hovercard using the template included in the plugin.

== Changelog ==

= 1.1.3 =
* Fix that avatars would show the hovercard as an alt-text in certain cases

= 1.1.2 =
* Better compatibility with BP Social Theme

= 1.1.1 =
* Disable hovercards for profile badge and profile header (thanks Sandy)
* Added filters 'bphc_parent_filter' and 'bphc_element_filter' to disable hovercards for certain avatars

= 1.1 =
* Hovercards are now not reloaded when they're still visible
* Never show two hovercards at the same time
* Hovercards were loaded during AJAX calls (when the mouse was no longer on)

= 1.0 =
* Fixed that non-logged in users got a stylish '0' instead of the hovercard
* Hovercards are now reloaded on _all_ AJAX request (except BP Live Notifications)

= 0.9.6 =
* Hovercards now refresh anytime something is loaded in with AJAX
* BP 1.6 Beta 1 & 2 compatibility

= 0.95 =
* Fixed 'Load More'-bug. Users of the WP admin bar would not get hovercards on posts loaded in using the 'Load More' button.

= 0.9 =
* Initial upload
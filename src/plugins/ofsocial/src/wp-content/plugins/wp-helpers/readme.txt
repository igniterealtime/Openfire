=== WordPress Helpers ===
Contributors: piklist, p51labs, sbruner
Tags: Piklist, Admin Bar, AIM, Autosave, Comments, Close Comments, Dashboard Widgets, Emojis, Excerpt, Excerpts, Exif, Featured Image, Feeds, Google Talk, Howdy, HTML Editor, Image, Jabber, Maintenance, Maintenance Mode, Media, Private, Protected, RSS Feeds, Settings, Under Construction, Visual Editor, Widgets, xmlrpc, Yahoo IM
Tested up to: 4.3
Requires at least: 4.0
Stable tag: 1.9.3
License: GPLv2 or later
License URI: http://www.gnu.org/licenses/gpl-2.0.html

Take control of WordPress. Adds over 50 more settings to your admin.

== Description ==

Take control of WordPress. Adds over 50 more settings to your admin.

> #### Read The Reviews
> <a href="http://wpmu.org/wordpress-helpers-the-missing-settings-page-for-wordpress/">WPMU.org</a><br>
> <a href="https://managewp.com/wordpress-settings-wordpress-helpers/"/>ManageWP.com</a>


[Watch the WordPress Helpers Demo](http://www.youtube.com/watch?v=ZYSUDvodWxI&hd=1):

http://www.youtube.com/watch?v=ZYSUDvodWxI&hd=1

= With WordPress Helpers you can easily: =
* Customize your login screen logo and background color.
* Take control of the WordPress Admin Bar... including "Howdy".
* Show ID's on edit screens for Posts, Pages, Categories, Tags, Users, Media, Custom Post Types and Custom Taxonomies.
* Expose the hidden WordPress settings page.
* Disable the theme switcher.
* Remove the "Screen Options" tab.
* Disable Upgrade Notifications for WordPress, Themes and Plugins (individually).
* Hide Dashboard widgets.
* Set Dashboard columns.
* View Screen Information in the Help Tab.
* Bring back Blogrolls.

= Writing =
* Fully disable Emojis
* Set the default Post editor (Visual or HTML).
* Enable WYSIWYG for excerpts box.
* Set Post Editor columns.
* Totally disable the Visual Editor.
* Increase the height of the Excerpt box when writing a Post.
* Disable Autosave.
* Set the Post Per Page on the edit screen.

= Reading =
* Set Excerpt length by characters or words.
* Remove the "Private" and "Protected" title prefixes.
* Disable RSS Feeds.
* Delay publishing of RSS feeds.
* Add Featured Images to your RSS Feed.
* Include/Exclude Post Types in Search.
* Disable XML-RPC (WordPress 3.5 or later)

= Discussion =
* Do not allow comments on Pages.
* Remove auto linking in comments.
* Disable Self Pinging.

= Appearance =
* Enhanced Body/Post Classes: Browser detect, Taxonomies (including hierarchy levels), post date, has post thumbnail, author information, logged in users, multisite, odd/even (post archives only), post excerpt.
* Remove any/all WordPress default widgets.
* Run any shortcode in a widget.
* Remove WordPress version, Feed Links, RSD Link, wlwmanifest and relational links for the posts adjacent to the current post, from your theme header.

= User Profiles =
* Remove the Admin color scheme option from the User Profiles.
* Remove AIM, Yahoo IM, Jabber/Google Talk.

= User Communication =
* Change WordPress "from" email address.

= Site Visitors =
* Put your site into Maintenance Mode.
* Create a Private website.
* Redirect vistors to the home page after login.
* Display an notice to your users in the admin or on the front of your site.


> #### Powered by Piklist
> WordPress Helpers requires the Piklist framework.   
> <a href="http://wordpress.org/extend/plugins/piklist/">You can download and install Piklist for free.</a>

== Frequently Asked Questions ==

= What does this plugin do? =
WordPress Helpers provides access to all those settings you wish came with WordPress, providing you with an easy way to take control of WordPress.

= Some of the settings don't seem to work =
Though WordPress Helpers attempts to take control over your theme and plugins, sometimes it just can't.  If a setting in WordPress Helpers is not working, another plugin or your theme is overriding the setting.

= I have an idea for another helper! =
Awesome! We're always looking for new ideas. Please submit them on our <a href="http://piklist.com/support/forum/wordpress-helpers/">support forum</a>.

== Installation ==

**This plugin requires <a href="http://piklist.com/">Piklist</a>.**

* Install and activate the <a href="http://wordpress.org/extend/plugins/piklist/">Piklist plugin</a>.
* Install and activate WordPress Helpers like any other plugin.

== Changelog ==

= 1.9.3 =
Release Date: November 13, 2015

* ENHANCED: Update XML-RPC setting to work with Jetpack and WordPress mobile.

= 1.9.2 =
Release Date: November 10, 2015

* ENHANCED: Add Support link on plugins page.
* FIXED: wp_version is a reserved parameter and cannot be passed to piklist::render()
* FIXED: admin-notice.php replaced with notice.php for Piklist 0.9.9.x
* FIXED: Notice when removing Dashboard widgets.

= 1.9.1 =
Release Date: November 4, 2015

* FIXED: Notice when removing widgets.
* FIXED: Redefinition of parameter $value error.

= 1.9.0 =
Release Date: November 2, 2015

* ENHANCED: Moved array_path() from Piklist into WP Helpers.

= 1.8.9 =
Release Date: October 26, 2015

* FIXED: Notices when enabling/disabling widgets.
* Removed notice for v1.8.0

= 1.8.8 =
Release Date: October 19, 2015

* FIXED: Notice on Remove Widgets.

= 1.8.7 =
Release Date: October 19, 2015

* ENHANCED: If Widgets are not supported by the theme, don't show widget options.

= 1.8.6 =
Release Date: October 13, 2015

* ENHANCED: Easy access to database settings for debugging (DEVELOP Tab).

= 1.8.5 =
Release Date: October 9, 2015

* FIXED: Compatible with all versions of Piklist.

= 1.8.4 =
Release Date: October 3, 2015

* ENHANCED: Notices support new Piklist "Dismiss" parameter.

= 1.8.3 =
Release Date: October 2, 2015

* ENHANCED: Only show link to All Options page, if selected.
* FIXED: No more error when removing widgets.

= 1.8.2 =
Release Date: July 20, 2015

* FIXED: Notices for Image sizes.
* FIXED: Login page image works without getimagesize() @props poxtron
* FIXED: Notices on new install and Emoji not set.

= 1.8.1 =
Release Date: July 13, 2015

* FIXED: Allow deletion of Posts without a Featured Image (only when option is set.)

= 1.8 =
Release Date: July 13, 2015

* NEW: Require Featured Images before publishing.
* NEW: Set defaults when embedding your images.
* NEW: Show all available image sizes.
* NEW: Display image Exif data.

= 1.7.2.1 =
Release Date: July 7, 2015

* FIX: Admin notice error.

= 1.7.2 =
Release Date: July 7, 2015

* NEW: Fully disable Emoji support and default option.

= 1.7.1 =
Release Date: June 30, 2015

* ENHANCED: Login logo now links to home page, and title is website name.
* FIXED: Typo in Maintenance Mode links.

= 1.7.0 =
* NEW: Add Featured Image to List Tables.
* ENHANCEMENT: Add more room for longer object ID's in list tables.
* FIX: Remove Dashboard Widgets works as expected.

= 1.6.3 =
* NEW: Enable WYSIWYG for excerpts (Writing)

= 1.6.2 =
* NEW: Schedule WordPress to delete orphaned meta.

= 1.6.1 =
* FIXED: Update script.
* FIXED: Typo.
* Removed default avatar option.

= 1.6.0 =
* ENHANCED: Upgraded Piklist Checker to 0.6.0

= 1.5.9 =
* NEW: Customize your login screen logo and background color. 
* ENHANCED: Better UI for Users > Visitors settings.

= 1.5.8 =
* FIXED: Notice when a user cannot manage_options and the All Options setting is set.

= 1.5.7 =
* FIXED: Widgets and Dashboard Widgets works properly. You may need to resave them.
* Removed deprecated "dashboard_secondary".

= 1.5.6 =
* FIXED: Checkboxes work as expected.
* FIXED: Tabs in correct order.

= 1.5.5 =
* Better compatiblity with Piklist 0.9.4

= 1.5.4 =
* ENHANCED: updated to Piklist Checker 0.5.0

= 1.5.3 =
* Now requires WordPress 3.4.
* Fixed notices.

= 1.5.2 =
* ENHANCED: Better Frontend notice.
* ENHANCED: Visitor messages can be written in Textareas.
* ENHANCED: Allow other plugins to also add to the Terms columns.
* FIX: Maintenance Mode uses your custom message for the 503 response.
* Bug fixes.

= 1.5.1 =
* FIX: Hide Frontend Admin Bar.

= 1.5.0 =
* FIX: Maintenance Mode.

= 1.4.9 =
* NEW: System Information
* NEW: Change WordPress "from" email address.
* NEW: Screen Information in Help Tab.
* ENHANCED: Site ID in Multisite.
* ENHANCED: User message can now be filtered by browser.
* ENHANCED: Remove Dashboard widget: Browser Upgrade Warning

= 1.4.8 =
* ENHANCED: Updated to latest version of Piklist Checker.

= 1.4.7 =
* NEW: Delay publishing of RSS feed.
* FIX: Upgrade issue with Piklist Checker.

= 1.4.6 =
* NEW: Expose the hidden WordPress settings page.
* ENHANCED: Nice new update message.
* FIX: "Change Howdy" markup is now correct.
* FIX: 503 response for Maintenance mode.

= 1.4.5 =
* NEW: Private Site: Force site to login page.
* NEW: Redirect to Home Page after user logs in.
* ENHANCED: Moved settings sections around for better access.
* FIX: Excerpt CSS only triggers on post pages.
* FIX: Link to disable Maintenance Mode is correct.

= 1.4.4 =
* NEW: Enable Link Manager (WordPress 3.5+ only)
* FIX: Show ID's logic is now confined to is_admin.
* FIX: Show ID's does not interfere with other table modifications.

= 1.4.3 =
* NEW: Users and Links are now sortable.
* FIX: "Include in Search" option should not affect admin search.

= 1.4.2 =
* Temporarily removed System Info tab

= 1.4.1 =
* Better conditional fields.
* Fixed notices.

= 1.4.0 =
* New Feature: User notices.
* New Feature: Disable XML-RPC in WordPress 3.5
* Added Maintenance Mode Message to login screen.

= 1.3.0 =
* New Feature: Include/Exclude Post Types in Search.
* Reduce width of ID columns.
* Very basic BuddyPress support for enhanced classes (more BuddyPress support coming!).
* Bugfix: PHP errors on some enhanced classes.
* Piklist Checker v0.4.0.

= 1.2.1 =
* Bugfix: Piklist checker deactivates plugin on Piklist upgrade.

= 1.2.0 =
* New Feature: Disable upgrade notifications for WordPress Core, Plugins and Themes.
* New Feature: Maintenance Mode.
* New Feature: Enhanced class: JS detect in Body tag.

= 1.1.0 =
* Reduced the number of tabs.
* New Feature: Enhanced Body/Post classes.
* New Feature: Do not allow comments on Pages.
* New Feature: Disable Self Pinging.
* Removed Feature: "Close comments for old Posts", since it's already an option in WordPress.
* Bugfix: screen_layout_columns_dashboard should only trigger when default not selected.
* Added some descriptions to settings.
* Replaced remove_menu with remove_node.

= 1.0.1 =
* Removed some left over code.
* Added a space for Howdy!

= 1.0.0 =
* Initial release!

== Screenshots ==
1. Include Admin messages for your logged in users.
2. Show messages to your websites visitors.
3. Easily put your website into Maintenance Mode.
4. Create a Private website.
5. Style your login screen.
6. Add the visual editor to your Excerpt box.
7. Remove widgets, or allow them to run shortcodes.
8. Expose the hidden WordPress "All Settings" page.
9. Add ID's to ALL List Tables, and Featured Images to your Post Type tables.
10. Easily accessible System Information page.
11. Screen Information for easy debugging.
12. Show all available image sizes.
13. Require Featured Images before publishing.
14. Over 50 settings to customize your website!


== Upgrade Notice ==

= 1.2.0 =
NEW FEATURES: Disable upgrade notifications, Maintenance Mode and and JS detect in Body tag.
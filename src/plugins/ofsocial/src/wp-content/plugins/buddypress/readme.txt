=== BuddyPress ===
Contributors: johnjamesjacoby, DJPaul, boonebgorges, r-a-y, imath, mercime, tw2113, dcavins, hnla
Tags: social networking, activity, profiles, messaging, friends, groups, forums, notifications, settings, social, community, networks, networking
Requires at least: 3.8
Tested up to: 4.4
Stable tag: 2.4.3
License: GPLv2 or later
License URI: https://www.gnu.org/licenses/gpl-2.0.html

BuddyPress helps you run any kind of social network on your WordPress, with member profiles, activity streams, user groups, messaging, and more.

== Description ==

Are you looking for modern, robust, and sophisticated social network software? BuddyPress is a suite of components that are common to a typical social network, and allows for great add-on features through WordPress's extensive plugin system.

BuddyPress is focused on ease of integration, ease of use, and extensibility. It is deliberately powerful yet unbelievably simple social network software, built by contributors to WordPress.

Enable registered members to create profiles, have private conversations, make connections, create & interact in groups, and much more. Truly a social network in a box, BuddyPress helps you more easily build a home for your company, school, sports team, or other niche community.

= Extensions =

BuddyPress has an ever-increasing array of extended features developed by an active and thriving plugin development community, with hundreds of free-and-open BuddyPress-compatible plugins available. We list them on both <a href="https://buddypress.org/extend/recommended-plugins/">our plugin directory</a> and <a href="https://wordpress.org/plugins/search.php?q=buddypress">WordPress.org</a>. Any plugin can be conveniently installed using the plugin installer in your WordPress Dashboard.

= More Information =

Visit the <a href="https://buddypress.org/">BuddyPress website</a> for documentation, support, and information on getting involved in the project and community.

== Installation ==

= From your WordPress dashboard =

1. Visit 'Plugins > Add New'
2. Search for 'BuddyPress'
3. Activate BuddyPress from your Plugins page. (You will be greeted with a Welcome page.)

= From WordPress.org =

1. Download BuddyPress.
2. Upload the 'buddypress' directory to your '/wp-content/plugins/' directory, using your favorite method (ftp, sftp, scp, etc...)
3. Activate BuddyPress from your Plugins page. (You will be greeted with a Welcome page.)

= Once Activated =

1. If you do not have pretty permalinks enabled, you will see a notice to enable them. (BuddyPress will not currently work without them.)
2. Visit 'Settings > BuddyPress > Components' and adjust the active components to match your community. (You can always toggle these later.)
3. Visit 'Settings > BuddyPress > Pages' and setup your directories and special pages. We create a few automatically, but suggest you customize these to fit the flow and verbiage of your site.
4. Visit 'Settings > BuddyPress > Settings' and take a moment to match BuddyPress's settings to your expectations. We pick the most common configuration by default, but every community is different.

= Once Configured =

* BuddyPress comes with a robust theme-compatibility API that does its best to make every BuddyPress page look and feel right with just-about any WordPress theme. You may need to adjust some styling on your own to make everything look pristine.
* A few BuddyPress specific themes are readily available for download from WordPress.org, and hundreds more are available from third-party theme authors. BuddyPress themes are just WordPress themes with additional templates for each component, and with a little work you could easily create your own too!
* BuddyPress also comes with built-in support for Akismet and bbPress, two very popular and very powerful WordPress plugins. If you're using either, visit their settings pages and ensure everything is configured to your liking.

= Multisite & Multiple Networks =

BuddyPress can be activated and operate in just about any scope you need for it to.

* Activate at the site level to only load BuddyPress on that site.
* Activate at the network level for full integration with all sites in your network. (This is the most common multisite installation type.)
* Enable <a href="https://codex.buddypress.org/getting-started/customizing/bp_enable_multiblog/">multiblog</a> mode to allow your BuddyPress content to be displayed on any site in your WordPress Multisite network, using the same central data.
* Extend BuddyPress with a third-party multi-network plugin to allow each site or network to have an isolated and dedicated community, all from the same WordPress installation.

Read more about custom BuddyPress activations <a href="https://codex.buddypress.org/getting-started/installation-in-wordpress-multisite/">on our codex page.</a>

= Discussion Forums =

Try <a href="https://wordpress.org/plugins/bbpress/">bbPress</a>. It integrates with BuddyPress Groups, Profiles, and Notifications. Each group on your site can choose to have its own forum, and each user's topics, replies, favorites, and subscriptions appear in their profiles.

== Frequently Asked Questions ==

= Can I use my existing WordPress theme? =

Yes! BuddyPress works out-of-the-box with nearly every WordPress theme.

= Will this work on WordPress multisite? =

Yes! If your WordPress installation has multisite enabled, BuddyPress will support the global tracking of blogs, posts, comments, and even custom post types with a little bit of custom code.

= Where can I get support? =

Our community provides free support at <a href="https://buddypress.org/support/">https://buddypress.org/support/</a>.

For dedicated consultations, see our <a href="https://buddypress.org/consulting/">unofficial list</a> of freelancers, contractors, and agencies offering BuddyPress services.

= Where can I find documentation? =

Our codex can be found at <a href="https://codex.buddypress.org/">https://codex.buddypress.org/</a>.

= Where can I report a bug? =

Report bugs, suggest ideas, and participate in development at <a href="https://buddypress.trac.wordpress.org/">https://buddypress.trac.wordpress.org</a>.

= Where can I get the bleeding edge version of BuddyPress? =

Check out the development trunk of BuddyPress from Subversion at <a href="https://buddypress.svn.wordpress.org/trunk/">https://buddypress.svn.wordpress.org/trunk/</a>, or clone from Git at git://buddypress.git.wordpress.org/.

= Who builds BuddyPress? =

BuddyPress is free software, built by an international community of volunteers. Some contributors to BuddyPress are employed by companies that use BuddyPress, while others are consultants who offer BuddyPress-related services for hire. No one is paid by the BuddyPress project for his or her contributions. If you would like to provide monetary support to BuddyPress, please consider a donation to the <a href="http://wordpressfoundation.org">WordPress Foundation</a>, or ask your favorite contributor how they prefer to have their efforts rewarded.

== Screenshots ==

1. **Activity Streams** - Global, personal, and group activity streams with threaded commenting, direct posting, favoriting and @mentions. All with full RSS feeds and email notification support.
2. **Extended Profiles** - Fully editable profile fields allow you to define the fields users can fill in to describe themselves. Tailor profile fields to suit your audience.
3. **User Settings** - Give your users complete control over profile and notification settings. Settings are fully integrated into your theme, and can be disabled by the administrator.
4. **Extensible Groups** - Powerful public, private or hidden groups allow your users to break the discussion down into specific topics. Extend groups with your own custom features using the group extension API.
5. **Friend Connections** - Let your users make connections so they can track the activity of others, or filter to show only those users they care about the most.
6. **Private Messaging** - Private messaging will allow your users to talk to each other directly and in private. Not just limited to one-on-one discussions, your users can send messages to multiple recipients.
7. **Site Tracking** - Track posts and comments in the activity stream, and allow your users to add their own blogs using WordPress' Multisite feature.
8. **Notifications** - Keep your members up-to-date with relevant activity via toolbar and email notifications.

== Languages ==

BuddyPress is available in many languages thanks to the volunteer efforts of individuals all around the world. Check out our <a href="https://codex.buddypress.org/translations/">translations page</a> on the BuddyPress Codex for more details.

Please consider helping translate BuddyPress at our <a href="https://translate.wordpress.org/projects/wp-plugins/buddypress">GlotPress project</a>. Growing the BuddyPress community means better software for everyone!

== Upgrade Notice ==

= 2.4.3 =
See: https://codex.buddypress.org/releases/version-2-4-3/

= 2.4.2 =
See: https://codex.buddypress.org/releases/version-2-4-2/

= 2.4.1 =
See: https://codex.buddypress.org/releases/version-2-4-1/

= 2.4.0 =
See: https://codex.buddypress.org/releases/version-2-4-0/

= 2.3.5 =
See: https://codex.buddypress.org/releases/version-2-3-5/

= 2.3.4 =
See: https://codex.buddypress.org/releases/version-2-3-4/

= 2.3.3 =
See: https://codex.buddypress.org/releases/version-2-3-3/

= 2.3.2 =
See: https://codex.buddypress.org/releases/version-2-3-2/

= 2.3.1 =
See: https://codex.buddypress.org/releases/version-2-3-1/

= 2.3.0 =
See: https://codex.buddypress.org/releases/version-2-3-0/

= 2.2.3.1 =
See: https://codex.buddypress.org/releases/version-2-2-3-1/

= 2.2.3 =
See: https://codex.buddypress.org/releases/version-2-2-3/

= 2.2.2.1 =
See: https://codex.buddypress.org/releases/version-2-2-2-1/

= 2.2.2 =
See: https://codex.buddypress.org/releases/version-2-2-2/

= 2.2.1 =
See: https://codex.buddypress.org/releases/version-2-2-1/

= 2.2 =
See: https://codex.buddypress.org/releases/version-2-2/

= 2.1 =
See: https://codex.buddypress.org/releases/version-2-1/

= 2.0.3 =
See: https://codex.buddypress.org/releases/version-2-0-3/

= 2.0.2 =
See: https://codex.buddypress.org/releases/version-2-0-2/

= 2.0.1 =
See: https://codex.buddypress.org/releases/version-2-0-1/

= 2.0 =
See: https://codex.buddypress.org/releases/version-2-0/

= 1.9.2 =
See: https://codex.buddypress.org/releases/version-1-9-2/

= 1.9.1 =
See: https://codex.buddypress.org/releases/version-1-9-1/

= 1.9 =
See: https://codex.buddypress.org/releases/version-1-9/

= 1.8.1 =
See: https://codex.buddypress.org/releases/version-1-8-1/

= 1.8 =
See: https://codex.buddypress.org/releases/version-1-8/

= 1.7.3 =
See: https://codex.buddypress.org/releases/version-1-7-3/

= 1.7.2 =
See: https://codex.buddypress.org/releases/version-1-7-2/

= 1.7.1 =
See: https://codex.buddypress.org/releases/version-1-7-1/

= 1.7 =
See: https://codex.buddypress.org/releases/version-1-7/

= 1.6.5 =
See: https://codex.buddypress.org/releases/version-1-6-5/

= 1.6.4 =
See: https://codex.buddypress.org/releases/version-1-6-4/

= 1.6.3 =
See: https://codex.buddypress.org/releases/version-1-6-3/

= 1.6.2 =
Compatibility with WordPress 3.5

= 1.6.1 =
Fixes 4 bugs

= 1.6 =
See: https://codex.buddypress.org/releases/version-1-6/

= 1.5 =
See: https://codex.buddypress.org/releases/version-1-5/

= 1.2.9 =
Compatibility with WordPress 3.2

= 1.2.8 =
Compatibility with WordPress 3.1

= 1.2.7 =
Fixes over 10 bugs.

== Changelog ==

= 2.4.3 =
See: https://codex.buddypress.org/releases/version-2-4-3/

= 2.4.2 =
See: https://codex.buddypress.org/releases/version-2-4-2/

= 2.4.1 =
See: https://codex.buddypress.org/releases/version-2-4-1/

= 2.4.0 =
See: https://codex.buddypress.org/releases/version-2-4-0/

= 2.3.5 =
See: https://codex.buddypress.org/releases/version-2-3-5/

= 2.3.4 =
See: https://codex.buddypress.org/releases/version-2-3-4/

= 2.3.3 =
See: https://codex.buddypress.org/releases/version-2-3-3/

= 2.3.2 =
See: https://codex.buddypress.org/releases/version-2-3-2/

= 2.3.1 =
See: https://codex.buddypress.org/releases/version-2-3-1/

= 2.3.0 =
See: https://codex.buddypress.org/releases/version-2-3-0/

= 2.2.1 =
See: https://codex.buddypress.org/releases/version-2-2-1/

= 2.2 =
See: https://codex.buddypress.org/releases/version-2-2/

= 2.1 =
See: https://codex.buddypress.org/releases/version-2-1/

= 2.0.3 =
See: https://codex.buddypress.org/releases/version-2-0-3/

= 2.0.2 =
See: https://codex.buddypress.org/releases/version-2-0-2/

= 2.0.1 =
See: https://codex.buddypress.org/releases/version-2-0-1/

= 2.0 =
See: https://codex.buddypress.org/releases/version-2-0/

= 1.9 =
See: https://codex.buddypress.org/releases/version-1-9/

= 1.8.1 =
See: https://codex.buddypress.org/releases/version-1-8-1/

= 1.8 =
See: https://codex.buddypress.org/releases/version-1-8/

= 1.7 =
See: https://codex.buddypress.org/releases/version-1-7/

= 1.6 =
See: https://codex.buddypress.org/releases/version-1-6/

= 1.5 =
See: https://codex.buddypress.org/releases/version-1-5/

= Older =
See: https://codex.buddypress.org/releases/

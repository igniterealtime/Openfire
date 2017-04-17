=== BuddyPress Docs  ===
Contributors: boonebgorges, dcavins, cuny-academic-commons
Donate link: http://teleogistic.net/donate
Tags: buddypress, docs, wiki, documents, collaboration
Requires at least: WordPress 3.3, BuddyPress 1.5
Tested up to: WordPress 4.3.1, BuddyPress 2.3.4
Stable tag: 1.8.8

Adds collaborative Docs to BuddyPress.

== Description ==

BuddyPress Docs adds collaborative work spaces to your BuddyPress community. Part wiki, part document editing, part shared dropbox, think of these Docs as a BuddyPress version of the Docs service offered by the Big G *ifyouknowwhatimean*

Features include:

* Docs that can be linked to groups or users, with a variety of privacy levels
* Support for fully-private document uploads
* Doc taxonomy, using tags
* Fully sortable and filterable doc lists
* TinyMCE front-end doc editing
* One-editor-at-a-time prevention against overwrites, plus idle detection/autosave
* Full access to revision history
* Dashboard access and management of Docs for the site admin

This plugin is in active development. For feature requests and bug reports, visit http://github.com/boonebgorges/buddypress-docs. If you have translated the plugin and would like to provide your translation for distribution with BuddyPress Docs, please contact the plugin author.

== Installation ==

1. Install
1. Activate
1. Sit back and watch the jack roll in

== Changelog ==

= 1.8.8 =
* Fixed bug that prevented certain users from having access to the "associated group" panel in edit mode.
* Fixed bug where Doc access settings might not be correct by default.
* Fixed bug where suggested Doc access settings may override values set by the user.

= 1.8.7 =
* Fixed pagination bugs within group and user Doc directories
* Improved accessibility throughout frontend and backend screens.
* Added new action hooks to Doc edit meta area.
* Fixed bug that caused "View All Docs" link to be broken when plugins add filters to Doc directories.
* Added miscellaneous filters for plugin developers.
* Improved performance of access protection functions.

= 1.8.6 =
* Improved compatibility with BuddyPress 2.2+
* New feature: unlink from group button
* Extracted away from WordPress's general discussion settings
* Fixed bug that could cause attachment corruption on download
* Fixed JS error when editing/creating Doc

= 1.8.5 =
* Fixed permalinks for child Docs
* More fixes for JS dependency and load order

= 1.8.4 =
* Improve dependency logic when loading JS files

= 1.8.3 =
* Fix conflict with Events Organizer and certain other plugins using the 'pre_get_posts' hook
* Moar filters

= 1.8.2 =
* Fix performance issue related to the 'check_is_protected()' check for Doc attachments
* Fix bug that caused Docs tab to be enabled for groups in some situations where it was not intended
* Avoid fatal errors if loading Groups integration in an unorthodox order

= 1.8.1 =
* Replace the missing Link feature in the rich text editor with WP's custom link plugin
* Fix bug that could allow non-authenticated user to access /edit page in some cases
* Ensure that the 'js' and 'no-js' body classes work properly across themes

= 1.8.0 =
* Settings boxes default to closed on existing Docs, simplifying the interface, especially on mobile devices
* Improved handling of permissions for uploading attachments
* Overhauled internal capabilities system, for better reliability and customization
* Use submitted values to rerender page after a failed create/edit action
* Better protection against comment spam
* Better suggestions for default access settings when changing Doc group associations
* Fix wikitext-style [[links]] when pointing to non-existent Docs
* Better localization for some strings as used in Javascript

= 1.7.1 =
* Don't run document protection check on AJAX calls
* Fix PHP notice when compiling group terms
* Force theme compatibility mode on User tabs for themes that don't use it for other Docs template
* Fix bug that caused Admins not to meet the "Moderator" minimum role
* Fix potential bug related to overwriting autosave drafts

= 1.7.0 =
* Fix incorrect function usage when generating htaccess files
* Correct some permissions logic that caused Create button to be shown to some logged-out users
* More reliable redirection when attempting to access a protected Doc
* Better compatibility with other plugins using the WP Heartbeat API
* Use tag name rather than slug when displaying tags in directory filters
* Improved compatibility with BuddyPress 2.0 metadata functions
* Refactored activity action generation to work with BP 2.0's new dynamic system
* When group is hidden, group association is no longer advertised in the activity action

= 1.6.1 =
* Fix a bug where group associations could be lost when Docs were edited by non-admins
* Add a filter for auto-generated Apache attachment rewrite rules
* Better error notice for Apache users with AllowOverride issues
* Improved layout of attachment drawer
* Improved appearance and localizability for "and x more" tags link
* More reliable toggling of settings during group creation
* Fix bug that prevented edit lock from being cleared on doc save

= 1.6.0 =
* Overhaul of the way group/user tag clouds work
* Improved support for attachments on nginx and IIS
* Improved doc edit locking mechanisms
* Improved appearance on devices of various sizes
* Support for WordPress 3.9 and TinyMCE 4.x

= 1.5.7 =
* Improve appearance of row actions on mobile devices
* Improve appearance of tags filter on IE < 9
* Fix bug introduced in BP 1.9.2 related to the display of comments
* Update ru_RU

= 1.5.6 =
* Allow current filter to be toggled by reclicking filter name
* Fix bug in "Edited by Me" logic when user has not edited any Docs
* Show deleted Docs on Started By Me tab, so they can be easily restored
* Improved interface for Tags directory filter when many tags are present
* Prevent logged-out user from accessing Create page
* Improved compatibility with BuddyPress 1.9

= 1.5.5 =
* Fix bug with permalinks in groups, introduced in change to is_singular()

= 1.5.4 =
* Fix bug with WP 3.7 that caused single Docs to 404 when Permalinks were set to Page Name.

= 1.5.3 =
* More compatibility with WordPress 3.7

= 1.5.2 =
* Compatibility with WordPress 3.7

= 1.5.1 =
* Fix bug that prevented settings from being populated in some cases, resulting in improper permissions
* Fix debug warnings on Settings page
* Fix bug with Settings page when BP_DOCS_SLUG is set in wp-config.php

= 1.5 =
* New standalone Settings panel, under Dashboard > BuddyPress Docs
* Main Docs slug can now be changed via the admin
* Fix bug that prevented the activity action from being modified for the associated group
* Fix bug that prevented activity from appearing in group activity streams
* Fix bug that prevented attachment uploads on group Doc creation pages
* Recast "minimum role to create Doc in group" in terms of group association
* Fix some textdomain errors
* Fix incorrect form action for "has-attachment" filter in some cases
* Fix My Groups view
* Add Directory Excerpt Length admin option
* Fix "admins and mods of..." permissions setting and prevent non-admin-mods from locking themselves out of Docs
* More accurate list of items on "Edited by..." tab
* Improve the way attachment URLs are built
* Allow Docs with empty content field
* Add hooks to templates
* Improved compatibility with PHP 5.4+

= 1.4.5 =
* Fixes bug in access filter for "logged-in users" setting
* Removes stripslashes() on post content, which was causing problems with LaTeX plugins

= 1.4.4 =
* Fixes recursion problem that caused fatal errors when filtering by has-attachment on some setups
* Fixes incorrect tag directory links at the bottom of individual Docs
* Fixes CSS for hover actions in Docs directory
* Fixes bug that may cause fatal errors when using private attachments on setups other than vanilla Apache
* Removes incidental dependencies on Activity and Groups components
* Makes the plugin dir slug customizable

= 1.4.3 =
* Fixes bug introduced in 1.4.2 that prevents certain sorts of Doc editing
* Improved localization
* Updates ru_RU

= 1.4.2 =
* Fixes problem where nested child Docs would not resolve properly in some cases
* Improves tab navigation on Create Doc screen
* Adds a filter that allows the Attachments component to be disabled
* Removes Delete Attachment link from Read mode
* Updates de_DE

= 1.4.1 =
* Fixes bug that caused fatal errors when using Docs with some upload-related plugins
* Turns off attempts at auto-detecting upgrades, to get rid of erroneous admin notice
* Prevents group affiliation from displaying when group is hidden and user is not a member

= 1.4 =
* Adds support for Doc Attachments, which obey Doc privacy levels
* Directory filters redesigned and streamlined
* Improves appearance across WP themes
* Improves tab navigation on Edit screen
* Adds cascading Doc permissions for new documents
* Fixes bug that hid the Dashboard settings
* Improves performance with custom bp_moderate capability maps
* Reintroduces global directory tag clouds
* Adds nl_NL language pack
* Improves the appearance of edit mode dropdowns for fields with long text

= 1.3.4 =
* Updated italian translation

= 1.3.3 =
* Fixed bug that incorrectly approved some post comments

= 1.3.2 =
* Fixed bug with tab permalinks on some setups
* Fixed bug in the way parent Doc is pre-selected on Edit screen dropdown

= 1.3.1 =
* Fixed issues with Doc creation when groups are disabled
* Fixed several bugs occurring when group association was changed or deleted
* Updated translations: Danish, Spanish

= 1.3 =
* Adds theme compatibility layer, for better formatting with all themes
* Full compatibility with BuddyPress 1.7
* Don't show permissions snapshot to non-logged-in users
* Adds Docs link to My Account toolbar menu
* Delete Doc activity when deleting Doc
* Delete local Doc tags when deleting Doc from any location
* Improved markup for Create New Docs button
* Don't show History quicklink on directories when revisions are disabled

= 1.2.10 =
* Improved compatibility with BP Group Hierarchy
* Fixes for global directory pagination

= 1.2.9 =
* Improved access protection, for better compatibility with bbPress 2.x and other plugins
* Updated Russian translation

= 1.2.8 =
* Fixes problem with group associations and privacy levels of new docs
* Improves access protection in WP searches and elsewhere
* Sets hide_sitewide more carefully when posting Doc activity items
* Prevents some errors related to wp_check_post_lock()
* Adds Russian translation

= 1.2.7 =
* Updates German translation
* Fixes rewrite problem when using custom BP_DOCS_SLUG
* Fixes fatal error when upgrading BuddyPress

= 1.2.6 =
* Updates Danish translation
* Fixes infinite loop bug in upgrader
* Fixes html entity problem in permalinks

= 1.2.5 =
* Fixes comment posting
* Fixes comment display and posting permissions
* Don't show Tags: label when no tags are present

= 1.2.4 =
* Updates .pot file
* Updates German translation
* l18n improvements
* Ensures that doc links are trailingslashed
* Fixes bug that prevented front-end doc deletion
* Removes temporarily non-functional doc counts from group tabs

= 1.2.3 =
* Fixes bug with bp-pages

= 1.2.2 =
* Improves group-association auto-settings when creating via the Create New Doc link in a group
* Fixes bug that erroneously required a directory page

= 1.2.1 =
* Fixes bug with overzealous Create New Doc button
* Fixes some PHP warnings

= 1.2 =
* Major plugin rewrite
* Moves Docs out of groups, making URLs cleaner, interface simpler, and making it possible to have Docs not linked to any group
* Adds a sitewide Docs directory

= 1.1.25 =
* Fixes bug in Javascript that may have caused secondary editor rows not to
  show in some cases
* Fixes bug that broke comment moderation in some cases

= 1.1.24 =
* Moves Table buttons to third row of editor, for better fit on all themes
* Adds Danish translation

= 1.1.23 =
* Adds Delete links to doc actions row
* Fixes an invalid markup issue in a template file

= 1.1.22 =
* Added Romanian translation

= 1.1.21 =
* Show the 'author' panel in the Dashboard

= 1.1.20 =
* Fixes idle timeout javascript
* Fixes bug with timezones on History tab
* Improves data passed to filters
* Cleans up references to WP's fullscreen editing mode
* Fixes potential PHP warnings on the Dashboard

= 1.1.19 =
* Improved WP 3.3 support
* Ensure that groups' can-delete setting defaults to 'member' when not present, to account for legacy groups
* Moved to groups_get_group() for greater efficiency under BP 1.6
* Fixed bug that redirected users to wp-admin when comparing a revision to itself

= 1.1.18 =
* Adds filters to allow site admins and plugin authors to force-enable Docs at group creation, or to remove the Docs step from the group creation process

= 1.1.17 =
* Forced BP Docs activity items to respect bp-disable-blogforum-comments in BP 1.5+
* Added Portuguese translation (pt_PT)

= 1.1.16 =
* Fixed bug that caused comments to be posted to the incorrect blog when using parent and child Docs

= 1.1.15 =
* Fixed bug that allowed doc content to be loaded by slug in the incorrect group
* Limit wikitext linking to docs in the same group
* Fixed bug that prevented group admins from creating a Doc when minimum role was set to Moderators
* Disables buggy fullscreen word count for the moment

= 1.1.14 =
* Fixed bug that prevented users from editing docs when no default settings were provided

= 1.1.13 =
* Switches default setting during group creation so that Docs are enabled
* Adds a filter to default group settings so that plugin authors can modify

= 1.1.12 =
* Adds wiki-like bracket linking
* Improves distraction-free editing JS
* Updates tabindent plugin for better browser support

= 1.1.11 =
* Replaces deprecated function calls
* Internationalizes some missing gettext calls
* Adds an error message when a non-existent Doc is requested

= 1.1.10 =
* Fixes bug that made BP Docs break WP commenting on some setups

= 1.1.9 =
* Closes code tag on Edit page.

= 1.1.8 =
* Filters get_post_permalink() so that Doc permalinks in the Admin point to the proper place
* Ensures that a group's last activity is updated when a Doc is created, edited, or deleted
* Modifies Recent Comments dashboard widget in order to prevent non-allowed people from seeing certain Doc comments
* Adds Print button to TinyMCE
* Adds Brazilian Portuguese localization.

= 1.1.7 =
* Fixes Tab name bug in 1.1.6 that may cause tab to disappear

= 1.1.6 =
* Rolls back group-specific Tab names and puts it in Dashboard > BuddyPress > Settings

= 1.1.5 =
* Better redirect handling using bp_core_no_access(), when available
* Added TinyMCE table plugin
* Added admin field for customizing group tab name
* Added UI for changing the slug of an existing Doc
* Security enhancement regarding comment posting in hidden/private groups
* Fixed issue that may have prevented some users from viewing History tab on some setups
* Clarified force-cancel edit lock interface
* Introduces bp_docs_is_docs_enabled_for_group() for easy checks
* French translation added
* Swedish translation added

= 1.1.4 =
* Make the page title prettier and more descriptive
* Don't show History section if WP_POST_REVISIONS are disabled
* Fixes activity throttle for private and hidden groups
* Fixes PHP warning related to read_comments permissions
* Adds German translation

= 1.1.3 =
* Fixes potential PHP notices related to hide_sitewide activity posting

= 1.1.2 =
* Fixes bug related to group privacy settings and doc comments
* Enables WP 3.2 distraction-free editing. Props Stas
* Fixes markup error that prevented h2 tag from being closed on New Doc screen
* Fixes problems with directory separators on some setups

= 1.1.1 =
* Updated textdomains and pot file for new strings

= 1.1 =
* 'History' tab added, giving full access to a Doc's revision history
* UI improvements to make tabs more wiki-like (Read, Edit, History)
* Fixed bug that caused an error message to appear when saving unchanged settings in the group admin

= 1.0.8 =
* Limited access to custom post type on the Dashboard to admins
* Added group Doc count to group tab
* Added Italian translation - Props Luca Camellini

= 1.0.7 =
* Fixes bug that prevented blog comments from being posted to the activity stream
* Fixes incorrect textdomain in some strings

= 1.0.6 =
* Fixes bug from previous release that prevented certain templates from loading correctly

= 1.0.5 =
* Abstracts out the comment format callback for use with non-bp-default themes
* Fixes bug that prevented some templates from being overridden by child themes
* Fixes bug that limited the number of docs visible in the Parent dropdown

= 1.0.4 =
* Adds controls to allow group admins to limit Doc creation based on group role
* Better performance on MS (plugin is not loaded on non-root-blogs by default)
* Fixes TinyMCE link button in WP 3.1.x by removing wplink internal linking plugin in Docs context

= 1.0.3 =
* Switches Delete to trash action rather than a true delete
* Removes More button from TinyMCE in Docs context
* Fixes bug that allowed doc comments to show up in activity streams incorrectly
* Adds Spanish translation

= 1.0.2 =
* Adds logic for loading textdomain and translations

= 1.0.1 =
* Fixes bug that prevented Doc delete button from working
* Adds POT file for translators
* Re-fixes problem with JS editor that might cause error message on save in some setups

= 1.0 =
* UI improvements on doc meta sliders
* Doc children are now listed in single doc view
* Improved support for TinyMCE loading on custom themes
* More consistent tab highlighting in group subnav
* Fixed bug that prevented reverting to the "no parent" setting
* Improvements in the logic of doc comment display
* Improvements in the way that activity posts respect privacy settings of groups

= 1.0-beta-2 =
* Added pagination for doc list view
* Improvements to the simultaneous edit lock mechanism
* Streamlining of Doc Edit CSS to fit better with custom themes
* Improvements to the way that docs tags are handled on the back end

= 1.0-beta =
* Initial public release

== Upgrade Notice ==

= 1.2 =
* Major plugin rewrite. See http://dev.commons.gc.cuny.edu/2012/11/15/buddypress-docs-1-2/ for more details.

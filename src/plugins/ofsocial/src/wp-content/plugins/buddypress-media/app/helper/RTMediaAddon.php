<?php
/**
 * Description of RTMediaAddon
 *
 * @package    rtMedia
 * @subpackage Admin
 *
 * @author     Gagandeep Singh <gagandeep.singh@rtcamp.com>
 * @author     Joshua Abenazer <joshua.abenazer@rtcamp.com>
 */
if ( ! class_exists( 'RTMediaAddon' ) ) {

	class RTMediaAddon {

		public $enquiry_link = 'http://rtcamp.com/contact/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media';
		// current page
		public static $page;

		/**
		 * Show coming_soon_div.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function coming_soon_div() {
			return '<div class="coming-soon coming-soon-l"></div> <a class="coming-soon coming-soon-r" href="' . $this->enquiry_link . '" target="_blank"></a>';
		}

		/**
		 * Render addons.
		 *
		 * @access public
		 *
		 * @param  type $page
		 *
		 * @return void
		 */
		public static function render_addons( $page = '' ) {
			global $wp_settings_sections, $wp_settings_fields;

			self::$page = $page;

			if ( ! isset( $wp_settings_sections ) || ! isset( $wp_settings_sections[ $page ] ) ) {
				return;
			}

			foreach ( ( array ) $wp_settings_sections[ $page ] as $section ) {

				if ( $section[ 'callback' ] ) {
					call_user_func( $section[ 'callback' ], $section );
				}

				if ( ! isset( $wp_settings_fields ) || ! isset( $wp_settings_fields[ $page ] ) || ! isset( $wp_settings_fields[ $page ][ $section[ 'id' ] ] ) ) {
					continue;
				}

				echo '<table class="form-table">';
				do_settings_fields( $page, $section[ 'id' ] );
				echo '</table>';
			}
		}

		/**
		 * Get addons for Audio/Video  Encoding and Plugins.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function get_addons() {
			$tabs = array();
			global $rtmedia_admin;
			$tabs[] = array(
				'title' => __( 'Plugins', 'buddypress-media' ),
				'name' => __( 'Plugins', 'buddypress-media' ),
				'href' => '#rtm-plugins',
				'icon' => 'dashicons-admin-plugins',
				'callback' => array( $this, 'plugins_content' )
			);

			$tabs[] = array(
				'title' => __( 'Audio/Video  Encoding', 'buddypress-media' ),
				'name' => __( 'Audio/Video  Encoding', 'buddypress-media' ),
				'href' => '#rtm-services',
				'icon' => 'dashicons-playlist-video',
				'callback' => array( $rtmedia_admin->rtmedia_encoding, 'encoding_service_intro' )
			);

			RTMediaAdmin::render_admin_ui( self::$page, $tabs );
		}

		/**
		 * Display plugins in Addons Section.
		 *
		 * @access public
		 *
		 * @param  array $args
		 *
		 * @return void
		 */
		public function plugins_content( $args = '' ) {
			$img_src = RTMEDIA_URL . 'app/assets/admin/img/';
			$rtcamp_upload_url = 'https://rtcamp-481283.c.cdn77.org/wp-content/uploads/';
			$addons = array(
				array(
					'title' => __( 'SEO', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/08/seo-xml.png',
					'product_link' => 'https://rtcamp.com/products/rtmedia-seo/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Generate an XML sitemap for all the public media files uploaded via rtMedia plugin. These sitemaps can be useful to index search engine to improve website SEO.', 'buddypress-media' ) . '</p>',
					'price' => '$29',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-seo/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_SEO_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-seo/index.php' ) ) ? true : false,
				),array(
					'title' => __( 'Moderation', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-moderation.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-moderation/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Report media if they find offensive. Set number of reports to automatically take down media from site.', 'buddypress-media' ) . '</p>',
					'price' => '$49',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-moderation/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_MODERATION_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-moderation/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Custom Attributes', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-custom-attributes.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-custom-attributes/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Categories media based on attributes. Site owner need to create attributes. When user upload a media, can select in which attribute that media can add.', 'buddypress-media' ) . '</p>',
					'price' => '$29',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-custom-attributes/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_ATTRIBUTES_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-custom-attributes/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Docs and Other files', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-docs-files.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-docs-files/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Allow users to upload documents and other file type using rtMedia upload box. This addon support all the file extensions which WordPress allows.', 'buddypress-media' ) . '</p>',
					'price' => '$29',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-docs-files/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_OTHER_FILES_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-docs-files/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Default Albums', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-default-albums.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-default-albums/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'This plugin allows the creation of multiple default albums for rtMedia uploads. One of these albums can be set as the default global album.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-default-albums/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_DEFAULT_ALBUMS_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-default-albums/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Podcast (RSS and Atom feeds)', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-podcast-feed.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-podcast-feed/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Read rtMedia uploads from iTunes as well as any RSS feed-reader/podcasting software.', 'buddypress-media' ) . '</p>',
					'price' => '$29',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-podcast-feed/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_RSS_ATOM_FEED_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-podcast-feed/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Playlists', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-playlists.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-playlists/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Audio can be grouped into playlists. Once the user upload any audio file, can create a playlist or use existing one to manage audio files.', 'buddypress-media' ) . '</p>',
					'price' => '$29',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-playlists/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_PLAYLIST_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-playlists/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Favorites', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-favorites.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-favorites/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Users can create their list of favorite media in which they can add media previously uploaded by any user.', 'buddypress-media' ) . '</p>',
					'price' => '$29',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-favorites/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_FAVORITES_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-favorites/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Restrictions', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-restrictions.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-restrictions/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Site admin can set an upload limit on the basis of time span, file size (MB) and number of files user can upload.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-podcast-feed/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_RSS_ATOM_FEED_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-podcast-feed/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'bbPress Attachments', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-bbpress-attachments.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-bbpress-attachments/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Attach media files to bbPress forum topics and replies.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-bbpress-attachments/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_BBPRESS_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-bbpress-attachments/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'WordPress Sitewide Gallery', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-wordpress-sitewide-gallery.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-wordpress-sitewide-gallery/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Site admin can create and upload media into WordPress album. Create album without being dependent on BuddyPress.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-wordpress-sitewide-gallery/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_WORDPRESS_SITEWIDE_GALLERY_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-wordpress-sitewide-gallery/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'WordPress Comment Attachments', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-wordpress-comment-attachments.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-wordpress-comment-attachments/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Allow users to upload a media file in WordPress comment attachment box. It will display a thumbnail of attached file.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-wordpress-comment-attachments/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_WORDPRESS_COMMENT_ATTACHMENT_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-wordpress-comment-attachments/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Social Sharing', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-social-sharing.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-social-sharing/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Share uploaded media on social network sites like Facebook, twitter, linkedin, Google +. This addon integrate with rtSocial plugin.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-social-sharing/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_SOCIAL_SHARING_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-social-sharing/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Sidebar Widgets', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-sidebar-widgets.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-sidebar-widgets/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'This addon provide widgets to upload media and display gallery for rtMedia plugin.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-sidebar-widgets/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_WIDGETS_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-sidebar-widgets/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( '5 Star Ratings', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-ratings.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-ratings/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Display 5 star rating for all the uploaded media. User can rate the media files from 1 to 5 star.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-ratings/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_RATINGS_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-ratings/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Edit Mp3 Info (ID3 Tags)', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-edit-mp3-info.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-edit-mp3-info/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Allow user to edit MP3 FIle Audio tags (ID 3 tags).', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-edit-mp3-info/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_AUDIO_TAGS_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-edit-mp3-info/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Media Sorting', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-sorting.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-sorting/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Sort uploaded media based on file size, ascending/descending title, upload date of media.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-sorting/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_SORTING_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-sorting/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Bulk Edit', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-bulk-edit.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-bulk-edit/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Bulk edit option will allow user to quickly select media files and do required actions like move files from one album to another, change attributes, change privacy, delete files.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-bulk-edit/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_BULK_EDIT_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-bulk-edit/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'BuddyPress Profile Picture', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-buddypress-profile-picture.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-buddypress-profile-picture/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'User can easily set his/her profile picture from media uploaded via rtMedia.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-buddypress-profile-picture/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_BUDDYPRESS_PROFILE_PICTURE_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-buddypress-profile-picture/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Album Cover Art', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-album-cover-art.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-album-cover-art/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'User can easily set any of the image of the album as album cover photo', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-album-cover-art/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_ALBUM_COVER_ART_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-album-cover-art/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Direct Download Link', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-direct-download-link.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-direct-download-link/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'User can download media from website. Site owner can restrict which media type can be allowed to download.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-direct-download-link/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_DOWNLOADS_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-direct-download-link/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Upload by URL', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-upload-by-url.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-upload-by-url/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Users do not need to download media files from a URL and then upload it with rtMedia. Just provide the absolute URL for the media and it will upload on site.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-upload-by-url/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_DOWNLOADS_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-upload-by-url/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Media Likes', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-likes.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-likes/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'This add-on let you know who liked the media. User can also see which media they liked under their profile.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-likes/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_LIKES_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-likes/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Activity URL Preview', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-activity-url-preview.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-activity-url-preview/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'This addon provides a preview of the URL that is shared in BuddyPress activity. Just enter the URL you want to share on your site and see a preview of it before it is shared.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-activity-url-preview/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_ACTIVITY_URL_PREVIEW_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-activity-url-preview/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'View Counter', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-view-counter.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-view-counter/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Enable view count for all the uploaded media. Whenever user open that media file in lightbox or in single media view, that view count will be calculated and display next to media file.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-view-counter/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_VIEW_COUNT_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-view-counter/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Shortcode Generator', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-shortcode-generator.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-shortcode-generator/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'This add-on will add shortcode generator button in WordPress post and page editor for all the rtMedia shortcodes.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-shortcode-generator/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_SHORTCODE_GENERATOR_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-shortcode-generator/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Album Privacy', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-album-privacy.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-album-privacy/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Set album privacy when user create an album or change album privacy when editing existing albums. The privacy levels are Public, Logged in user, Friends and Private.', 'buddypress-media' ) . '</p>',
					'price' => '$29',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-album-privacy/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_ALBUM_PRIVACY_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-album-privacy/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'BuddyPress Group Media Control', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-buddypress-group-media-control.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-buddypress-group-media-control/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'This add-on allows group owner to manage media upload feature group wise.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-buddypress-group-media-control/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_GROUP_MEDIA_CONTROL_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-buddypress-group-media-control/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Set Custom Thumbnail for Audio/Video', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-set-custom-thumbnail.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-set-custom-thumbnail/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Allow media owner to change the thumbnail of uploaded audio/video files. The File Upload box will be provided to change media thumbnail.', 'buddypress-media' ) . '</p>',
					'price' => '$29',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-set-custom-thumbnail/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_MEDIA_CUSTOM_THUMBNAIL_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-set-custom-thumbnail/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'myCRED', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-mycred.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-mycred/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'This plugin integrates rtMedia and myCRED plugin, users can be can award virtual points for various rtMedia activities, like media upload, likes, deleted etc.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-mycred/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_MYCRED_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-mycred/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Upload terms', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-upload-terms.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-upload-terms/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'User must have to check the terms and conditions checkbox before uploading the media.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-upload-terms/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_UPLOAD_TERMS_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-upload-terms/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'CubePoints', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/06/rtmedia-cubepoints.jpg',
					'product_link' => 'https://rtcamp.com/products/rtmedia-cubepoints/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'If you are using CubePoints plugin on your website than rtMedia CubePoint add-on can be integrate with that plugin to setup point management system for rtMedia related activities.', 'buddypress-media' ) . '</p>',
					'price' => '$19',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-cubepoints/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_CUBEPOINTS_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-cubepoints/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Social Sync', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/08/rtMedia-Social-Sync.png',
					'product_link' => 'https://rtcamp.com/products/rtmedia-social-sync/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'rtMedia Social Sync allows you to import media from your Facebook account.', 'buddypress-media' ) . '</p>',
					'price' => '$49',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-social-sync/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'general',
					'purchased' => ( defined( 'RTMEDIA_SOCIAL_SYNC_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-social-sync/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Photo Watermark', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/08/rtMedia-Photo-Watermark.png',
					'product_link' => 'http://rtcamp.com/products/rtmedia-photo-watermark/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'rtMedia Photo Watermark let you add watermark on your images uploaded using rtMedia.', 'buddypress-media' ) . '</p>',
					'price' => '$49',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-photo-watermark/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'photo',
					'purchased' => ( defined( 'RTMEDIA_WATERMARK_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-photo-watermak/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Photo Tagging', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/08/rtMedia-Photo-Tagging.png',
					'product_link' => 'http://rtcamp.com/products/rtmedia-photo-tagging/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'rtMedia Photo Tagging enable users to tag their friends on photos uploaded using rtMedia.', 'buddypress-media' ) . '</p>',
					'price' => '$49',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-photo-tagging/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'photo',
					'purchased' => ( defined( 'RTMEDIA_PHOTO_TAGGING_URL' ) || file_exists( WP_PLUGIN_DIR . '/bpm-photo-tag/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Photo Filters', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/08/rtMedia-Photo-Filters.png',
					'product_link' => 'http://rtcamp.com/products/rtmedia-photo-filters/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'rtMedia Photo Filters adds Instagram like filters to images uploaded with rtMedia.', 'buddypress-media' ) . '</p>',
					'price' => '$49',
					'demo_link' => 'http://demo.rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-photo-filters/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'photo',
					'purchased' => ( defined( 'RTMEDIA_INSTAGRAM_URL' ) || file_exists( WP_PLUGIN_DIR . '/bpm-instagram/index.php' ) || defined( 'RTMEDIA_PHOTO_FILTERS_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-photo-filters/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Kaltura Add-on', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/08/rtMedia-Kaltura-Add-on.png',
					'product_link' => 'http://rtcamp.com/products/rtmedia-kaltura-add-on/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Add support for more video formats using Kaltura video solution. It works with Kaltura.com, self-hosted Kaltura-CE and Kaltura-on-premise.', 'buddypress-media' ) . '</p>',
					'price' => '$199',
					'demo_link' => 'http://demo.rtcamp.com/bpm-kaltura/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-kaltura/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'video',
					'purchased' => ( defined( 'RTMEDIA_KALTURA_PATH' ) || file_exists( WP_PLUGIN_DIR . '/bpm-kaltura/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'FFMPEG Add-on', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/08/rtMedia-FFMPEG-Addon.png',
					'product_link' => 'http://rtcamp.com/products/buddypress-media-ffmpeg-converter/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'Add supports for more audio & video formats using open-source media-node. Media node comes with automated setup script for Ubuntu/Debian.', 'buddypress-media' ) . '</p>',
					'price' => '$199',
					'demo_link' => 'http://demo.rtcamp.com/bpm-media/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-ffmpeg/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'video',
					'purchased' => ( defined( 'RTMEDIA_FFMPEG_URL' ) || file_exists( WP_PLUGIN_DIR . '/bpm-ffmpeg/index.php' ) ) ? true : false,
				),
				array(
					'title' => __( 'Membership Add-on', 'buddypress-media' ),
					'img_src' => $rtcamp_upload_url . 'edd/2015/08/rtMedia-Membership.png',
					'product_link' => 'https://rtcamp.com/products/rtmedia-membership/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'desc' => '<p>' . __( 'rtMedia Membership add-on provides membership functionality in your site in terms of media upload.', 'buddypress-media' ),
					'price' => '$99',
					'buy_now' => 'https://rtcamp.com/products/rtmedia-membership/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media',
					'category' => 'membership',
					'purchased' => ( defined( 'RTMEDIA_MEMBERSHIP_URL' ) || file_exists( WP_PLUGIN_DIR . '/rtmedia-membership/index.php' ) ) ? true : false,
				),
			);
			$addons = apply_filters( 'rtmedia_addons', $addons );

			foreach ( $addons as $key => $value ) {
				$this->addon( $value );
			}
			?>
				<div class="clear"></div>
			<?php
		}

		/**
		 * services_content.
		 *
		 * @access public
		 *
		 * @param  array $args
		 *
		 * @return void
		 */
		public function services_content( $args = '' ) {
			$objEncoding->encoding_service_intro();
		}

		/**
		 * themes_content.
		 *
		 * @access public
		 *
		 * @param  array $args
		 *
		 * @return void
		 */
		public function themes_content( $args = '' ) {
			echo '<h3>' . __( 'Coming Soon !!', 'buddypress-media' ) . '</h3>';
		}

		/**
		 * Define addon.
		 *
		 * @global type  $rtmedia
		 *
		 * @param  array $args
		 *
		 * @return void
		 */
		public function addon( $args ) {
			global $rtmedia;

			$defaults = array(
				'title' => '',
				'img_src' => '',
				'product_link' => '',
				'desc' => '',
				'price' => '',
				'demo_link' => '',
				'buy_now' => '',
				'coming_soon' => false,
				'category' => 'photo',
				'purchased' => false,
			);
			$args = wp_parse_args( $args, $defaults );
			extract( $args );

			$coming_soon ? ' coming-soon' : '';

			if ( $purchased ) {
				$purchase_link = '<span class="rtm-addon-purchased button-primary disabled alignright product_type_simple">' . __( 'Purchased', 'buddypress-media' ) . '</span>';
			} else {
				$purchase_link = '<a class="button-primary alignright product_type_simple"  href="' . $buy_now . '" target="_blank">' . __( 'Buy Now', 'buddypress-media' ) . '</a>';
			}

			$coming_soon_div = ( $coming_soon ) ? $this->coming_soon_div() : '';
			?>
			<div class="plugin-card clearfix rtm-plugin-card">

				<div class="plugin-card-top">
					<a class="rtm-logo" href="<?php echo $product_link; ?>" title="<?php echo $title; ?>" target="_blank">
						<img width="240" height="184" title="<?php echo $title; ?>" alt="<?php echo $title; ?>" src="<?php echo $img_src; ?>" />
					</a>

					<div class="name column-name">
						<h4><a href="<?php echo $product_link; ?>" title="<?php echo $title; ?>" target="_blank"><?php echo $title; ?></a></h4>
					</div>

					<div class="desc column-description">
						<?php echo $desc; ?>
					</div>
				</div>

				<div class="plugin-card-bottom">
					<span class="price alignleft">
						<span class="amount"><?php echo $price; ?></span>
					</span>
					<?php
					echo $purchase_link;

					if ( $demo_link != '' ) {
						echo '<a class="alignright rtm-live-demo button"  href="' . $demo_link . '" title="' . $title . '" target="_blank">' . __( 'Live Demo', 'buddypress-media' ) . '</a>';
					}
					?>
				</div>
			</div>
			<?php
		}

	}

}

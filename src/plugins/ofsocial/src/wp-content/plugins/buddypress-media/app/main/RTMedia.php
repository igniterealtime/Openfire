<?php
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * rtMedia
 *
 * The main rtMedia Class. This is where everything starts.
 *
 * @package rtMedia
 * @subpackage Main
 *
 * @author Faishal <faishal.saiyed@rtcamp.com>
 */
class RTMedia {

	/**
	 * @var string default thumbnail url fallback for all media types
	 */
	private $default_thumbnail;

	/**
	 *
	 * @var array allowed media types
	 */
	public $allowed_types;

	/**
	 *
	 * @var array privacy settings
	 */
	public $privacy_settings;

	/**
	 *
	 * @var array default media sizes
	 */
	public $default_sizes;

	/**
	 *
	 * @var object default application wide privacy levels
	 */
	public $default_privacy = array(
		'0' => 'Public',
		'20' => 'Users',
		'40' => 'Friends',
		'60' => 'Private'
	);

	/**
	 *
	 * @var string Support forum url
	 */
	public $support_url = 'http://community.rtcamp.com/c/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media';

	/**
	 *
	 * @var int Number of media items to show in one view.
	 */
	public $posts_per_page = 10;

	/**
	 *
	 * @var array The types of activity BuddyPress Media creates
	 */
	public $activity_types = array(
		'media_upload',
		'album_updated',
		'album_created'
	);
	public $options;
	public $render_options;

	/**
	 * Constructs the class
	 * Defines constants and excerpt lengths, initiates admin notices,
	 * loads and initiates the plugin, loads translations.
	 * Initialises media counter
	 *
	 * @global int $bp_media_counter Media counter
	 */
	public function __construct() {
		$this->default_thumbnail = apply_filters( 'rtmedia_default_thumbnail', RTMEDIA_URL . 'app/assets/admin/img/thumb_default.png' );
		add_action( 'init', array( $this, 'check_global_album' ) );
		add_action( 'plugins_loaded', array( $this, 'admin_init' ) );
		add_action( 'plugins_loaded', array( $this, 'load_translation' ), 10 );
		add_action( 'plugins_loaded', array( $this, 'init' ), 20 );
		add_action( 'wp_enqueue_scripts', array( 'RTMediaGalleryShortcode', 'register_scripts' ) );
		add_action( 'wp_enqueue_scripts', array( &$this, 'enqueue_scripts_styles' ), 999 );
		include(RTMEDIA_PATH . 'app/main/controllers/template/rt-template-functions.php');
		add_filter( 'intermediate_image_sizes_advanced', array( $this, 'filter_image_sizes_details' ) );
		add_filter( 'intermediate_image_sizes', array( $this, 'filter_image_sizes' ) );
		add_filter( "site_option_upload_filetypes", array( &$this, "filter_allow_mime_type_mu" ), 1, 1 );
		add_filter( 'image_size_names_choose', array( $this, 'rtmedia_custom_image_sizes_choose' ) );
	}

	function filter_allow_mime_type_mu( $options ) {
		$allowed_types = array();
		$this->allowed_types = apply_filters( 'rtmedia_allowed_types', $this->allowed_types );
		foreach ( $this->allowed_types as $type ) {
			if ( $type[ 'extn' ] != "" && call_user_func( "is_rtmedia_upload_" . $type[ "name" ] . "_enabled" ) ) {
				foreach ( $type[ 'extn' ] as $extn ) {
					$allowed_types[] = $extn;
				}
			}
		}
		$ext = apply_filters( 'rtmedia_plupload_files_filter', array( array( 'title' => "Media Files", 'extensions' => implode( ",", $allowed_types ) ) ) );
		$ext_arr = explode( ",", $ext[ 0 ][ "extensions" ] );
		$options = trim( $options );
		foreach ( $ext_arr as $f_ext ) {
			if ( $f_ext && strpos( $options, $f_ext ) === false ) {
				$options .= " " . $f_ext;
			}
		}
		return $options;
	}

	function fix_parent_id() {
		$site_global = rtmedia_get_site_option( 'rtmedia-global-albums' );
		if ( $site_global && is_array( $site_global ) && isset( $site_global[ 0 ] ) ) {
			$model = new RTMediaModel();
			$album_row = $model->get_by_id( $site_global[ 0 ] );
			if ( isset( $album_row[ "result" ] ) && count( $album_row[ "result" ] ) > 0 ) {
				global $wpdb;
				$row = $album_row[ "result" ][ 0 ];
				if ( isset( $row[ "media_id" ] ) ) {
					$sql = "update $wpdb->posts p
                                left join
                            $model->table_name r ON ( p.ID = r.media_id and blog_id = '" . get_current_blog_id() . "' )
                        set
                            post_parent = {$row[ "media_id" ]}
                        where
                            p.guid like '%/rtMedia/%'
                                and (p.post_parent = 0 or p.post_parent is NULL)
                                and not r.id is NULL
                                and r.media_type <> 'album'";
					$wpdb->query( $sql );
				}
			}
		}
	}

	function fix_privacy() {
		global $wpdb;
		$model = new RTMediaModel();
		$update_sql = "UPDATE $model->table_name SET privacy = '80' where privacy = '-1' ";
		$wpdb->query( $update_sql );
	}

	/*
	 * Update media privacy of the medias having context=group
	 * update privacy of groups medias according to the privacy of the group 0->public, 20-> private/hidden
	 */

	function fix_group_media_privacy() {
		//if buddypress is active and groups are enabled
		global $wpdb;
		$table_exist = false;
		if( $wpdb->query( "SHOW TABLES LIKE '{$wpdb->prefix}bp_groups'" ) ){
			$table_exist = true;
		}
		if ( class_exists( 'BuddyPress' ) && $table_exist ) {
			$model = new RTMediaModel();
			$sql_group = " UPDATE $model->table_name m join {$wpdb->prefix}bp_groups bp on m.context_id = bp.id SET m.privacy = 0 where m.context = 'group' and bp.status = 'public' and m.privacy <> 80 ";
			$wpdb->query( $sql_group );
			$sql_group = " UPDATE $model->table_name m join {$wpdb->prefix}bp_groups bp on m.context_id = bp.id SET m.privacy = 20 where m.context = 'group' and ( bp.status = 'private' OR bp.status = 'hidden' ) and m.privacy <> 80 ";
			$wpdb->query( $sql_group );
		}
	}

	function fix_db_collation() {
		global $wpdb;
		$model = new RTMediaModel();
		$interaction_model = new RTMediaInteractionModel();
		$update_media_sql = "ALTER TABLE " . $model->table_name . " CONVERT TO CHARACTER SET utf8 COLLATE utf8_general_ci";
		$wpdb->query( $update_media_sql );
		$update_media_meta_sql = "ALTER TABLE " . $wpdb->base_prefix . $model->meta_table_name . " CONVERT TO CHARACTER SET utf8 COLLATE utf8_general_ci";
		$wpdb->query( $update_media_meta_sql );
		$update_media_interaction_sql = "ALTER TABLE " . $interaction_model->table_name . " CONVERT TO CHARACTER SET utf8 COLLATE utf8_general_ci";
		$wpdb->query( $update_media_interaction_sql );
	}

	function set_site_options() {

		$rtmedia_options = rtmedia_get_site_option( 'rtmedia-options' );
		$bp_media_options = rtmedia_get_site_option( 'bp_media_options' );

		if ( $rtmedia_options == false ) {
			$this->init_site_options();
		} else {
			/* if new options added via filter then it needs to be updated */
			$this->options = $rtmedia_options;
		}
		$this->add_image_sizes();
		$this->set_image_quality();
	}

	public function set_image_quality() {
		add_filter( 'jpeg_quality', array( $this, 'rtmedia_jpeg_quality' ) );
	}

	public function rtmedia_jpeg_quality( $quality ) {
		$quality = isset( $this->options[ 'general_jpeg_image_quality' ] ) ? $this->options[ 'general_jpeg_image_quality' ] : 90;

		return $quality;
	}

	public function image_sizes() {
		$image_sizes = array();
		$image_sizes[ "thumbnail" ] = array( "width" => $this->options[ "defaultSizes_photo_thumbnail_width" ], "height" => $this->options[ "defaultSizes_photo_thumbnail_height" ], "crop" => ($this->options[ "defaultSizes_photo_thumbnail_crop" ] == "0") ? false : true );
		$image_sizes[ "activity" ] = array( "width" => $this->options[ "defaultSizes_photo_medium_width" ], "height" => $this->options[ "defaultSizes_photo_medium_height" ], "crop" => ($this->options[ "defaultSizes_photo_medium_crop" ] == "0") ? false : true );
		$image_sizes[ "single" ] = array( "width" => $this->options[ "defaultSizes_photo_large_width" ], "height" => $this->options[ "defaultSizes_photo_large_height" ], "crop" => ($this->options[ "defaultSizes_photo_large_crop" ] == "0") ? false : true );
		$image_sizes[ "featured" ] = array( "width" => $this->options[ "defaultSizes_featured_default_width" ], "height" => $this->options[ "defaultSizes_featured_default_height" ], "crop" => ($this->options[ "defaultSizes_featured_default_crop" ] == "0") ? false : true );
		return $image_sizes;
	}

	public function add_image_sizes() {
		$bp_media_sizes = $this->image_sizes();
		add_image_size( "rt_media_thumbnail", $bp_media_sizes[ 'thumbnail' ][ "width" ], $bp_media_sizes[ 'thumbnail' ][ "height" ], $bp_media_sizes[ 'thumbnail' ][ "crop" ] );
		add_image_size( "rt_media_activity_image", $bp_media_sizes[ 'activity' ][ "width" ], $bp_media_sizes[ 'activity' ][ "height" ], $bp_media_sizes[ 'activity' ][ "crop" ] );
		add_image_size( "rt_media_single_image", $bp_media_sizes[ 'single' ][ "width" ], $bp_media_sizes[ 'single' ][ "height" ], $bp_media_sizes[ 'single' ][ "crop" ] );
		add_image_size( "rt_media_featured_image", $bp_media_sizes[ 'featured' ][ "width" ], $bp_media_sizes[ 'featured' ][ "height" ], $bp_media_sizes[ 'featured' ][ "crop" ] );
		add_action( 'wp_head', array( &$this, 'custom_style_for_image_size' ) );
	}

	function custom_style_for_image_size() {
		if ( apply_filters( 'rtmedia_custom_image_style', true ) ) {
			?>
			<style type="text/css">
			<?php
			$this->custom_style_for_activity_image_size();
			global $rtmedia;
			if ( isset( $rtmedia->options[ 'general_masonry_layout' ] ) && $rtmedia->options[ 'general_masonry_layout' ] == '1' ) {
				$this->custom_style_for_gallery_image_size_masonry();
			} else {
				$this->custom_style_for_gallery_image_size();
			}
			do_action( 'rtmedia_custom_styles' );
			?>
			</style>
			<?php
		}
	}

	function custom_style_for_activity_image_size() {
		?>
		.rtmedia-activity-container .media-type-photo .rtmedia-item-thumbnail {
		max-width: <?php echo $this->options[ "defaultSizes_photo_medium_width" ]; ?>px;
		max-height: <?php echo $this->options[ "defaultSizes_photo_medium_height" ]; ?>px;
		}
		.rtmedia-activity-container .mejs-container.mejs-video{
		min-height: <?php echo $this->options[ "defaultSizes_video_activityPlayer_height" ]; ?>px;
		min-width: <?php echo $this->options[ "defaultSizes_video_activityPlayer_width" ]; ?>px;
		}
		<?php
	}

	function custom_style_for_gallery_image_size() {
		?>
		.rtmedia-container ul.rtmedia-list li.rtmedia-list-item div.rtmedia-item-thumbnail {
		width: <?php echo $this->options[ "defaultSizes_photo_thumbnail_width" ]; ?>px;
		height: <?php echo $this->options[ "defaultSizes_photo_thumbnail_height" ]; ?>px;
		line-height: <?php echo $this->options[ "defaultSizes_photo_thumbnail_height" ]; ?>px;
		}
		.rtmedia-container ul.rtmedia-list li.rtmedia-list-item div.rtmedia-item-thumbnail img {
		max-width: <?php echo $this->options[ "defaultSizes_photo_thumbnail_width" ]; ?>px;
		max-height: <?php echo $this->options[ "defaultSizes_photo_thumbnail_height" ]; ?>px;
		}
		.rtmedia-container .rtmedia-list  .rtmedia-list-item {
		width: <?php echo intval( $this->options[ "defaultSizes_photo_thumbnail_width" ] ); ?>px;
		}
		<?php
	}

	function custom_style_for_gallery_image_size_masonry() {
		if ( intval( $this->options[ 'defaultSizes_photo_thumbnail_height' ] ) > 0 ) {
			?>
			.rtmedia-container .rtmedia-list  .rtmedia-list-item .rtmedia-item-thumbnail {
			max-height: <?php echo intval( $this->options[ "defaultSizes_photo_thumbnail_height" ] ); ?>px;
			}
			<?php
		}
		if ( intval( $this->options[ 'defaultSizes_photo_thumbnail_width' ] ) > 0 ) {
			?>
			.rtmedia-container .rtmedia-list  .rtmedia-list-item .rtmedia-item-thumbnail {
			max-width: <?php echo intval( $this->options[ "defaultSizes_photo_thumbnail_width" ] ); ?>px;
			}
			<?php
		}
	}

	/**
	 *  Default allowed media types array
	 */
	function set_allowed_types() {
		$allowed_types = array(
			'photo' => array(
				'name' => 'photo',
				'plural' => 'photos',
				'label' => __( 'Photo', 'buddypress-media' ),
				'plural_label' => __( 'Photos', 'buddypress-media' ),
				'extn' => array( 'jpg', 'jpeg', 'png', 'gif' ),
				'thumbnail' => RTMEDIA_URL . 'app/assets/admin/img/image_thumb.png',
				'settings_visibility' => true
			),
			'video' => array(
				'name' => 'video',
				'plural' => 'videos',
				'label' => __( 'Video', 'buddypress-media' ),
				'plural_label' => __( 'Videos', 'buddypress-media' ),
				'extn' => array( 'mp4' ),
				'thumbnail' => RTMEDIA_URL . 'app/assets/admin/img/video_thumb.png',
				'settings_visibility' => true
			),
			'music' => array(
				'name' => 'music',
				'plural' => 'music',
				'label' => __( 'Music', 'buddypress-media' ),
				'plural_label' => __( 'Music', 'buddypress-media' ),
				'extn' => array( 'mp3' ),
				'thumbnail' => RTMEDIA_URL . 'app/assets/admin/img/audio_thumb.png',
				'settings_visibility' => true
			)
		);

		// filter for hooking additional media types
		$allowed_types = apply_filters( 'rtmedia_allowed_types', $allowed_types );

		// sanitize all the types
		$allowed_types = $this->sanitize_allowed_types( $allowed_types );

		// set the allowed types property
		$this->allowed_types = $allowed_types;
	}

	/**
	 * Sanitize all media sizes after hooking custom media types
	 *
	 * @param array $allowed_types allowed media types after hooking custom types
	 * @return array $allowed_types sanitized media types
	 */
	function sanitize_allowed_types( $allowed_types ) {
		// check if the array is formatted properly
		if ( ! is_array( $allowed_types ) && count( $allowed_types ) < 1 )
			return;

		//loop through each type
		foreach ( $allowed_types as $key => &$type ) {

			if ( ! isset( $type[ 'name' ] ) || // check if a name is set
					empty( $type[ 'name' ] ) ||
					//commented this section for playlist // !isset($type['extn']) || // check if file extensions are set
					//commented this section for playlist  // empty($type['extn']) ||
					strstr( $type[ 'name' ], " " ) || strstr( $type[ 'name' ], "_" ) ) {
				unset( $allowed_types[ $key ] ); // if not unset this type
				continue;
			}
			$slug = strtoupper( $type[ 'name' ] );
			if ( defined( "RTMEDIA_" . $slug . "_LABEL" ) ) {
				$type[ 'label' ] = constant( "RTMEDIA_" . $slug . "_LABEL" );
			}
			if ( defined( "RTMEDIA_" . $slug . "_PLURAL_LABEL" ) ) {
				$type[ 'plural_label' ] = constant( "RTMEDIA_" . $slug . "_PLURAL_LABEL" );
			}
			// if thumbnail is not supplied, use the default thumbnail
			if ( ! isset( $type[ 'thumbnail' ] ) || empty( $type[ 'thumbnail' ] ) ) {
				$type[ 'thumbnail' ] = $this->default_thumbnail;
			}
		}
		return $allowed_types;
	}

	/**
	 * Set the default sizes
	 */
	function set_default_sizes() {
		$this->default_sizes = array(
			'photo' => array(
				'thumbnail' => array( 'width' => 150, 'height' => 150, 'crop' => 1 ),
				'medium' => array( 'width' => 320, 'height' => 240, 'crop' => 1 ),
				'large' => array( 'width' => 800, 'height' => 0, 'crop' => 1 )
			),
			'video' => array(
				'activityPlayer' => array( 'width' => 320, 'height' => 240 ),
				'singlePlayer' => array( 'width' => 640, 'height' => 480 )
			),
			'music' => array(
				'activityPlayer' => array( 'width' => 320 ),
				'singlePlayer' => array( 'width' => 640 )
			),
			'featured' => array(
				'default' => array( 'width' => 100, 'height' => 100, 'crop' => 1 )
			)
		);

		$this->default_sizes = apply_filters( 'rtmedia_allowed_sizes', $this->default_sizes );
	}

	/**
	 * Set privacy options
	 */
	function set_privacy() {

		$this->privacy_settings = array(
			'levels' => array(
				60 => __( 'Private - Visible only to the user', 'buddypress-media' ),
				40 => __( 'Friends - Visible to user\'s friends', 'buddypress-media' ),
				20 => __( 'Logged in Users - Visible to registered users', 'buddypress-media' ),
				0 => __( 'Public - Visible to the world', 'buddypress-media' )
			)
		);
		$this->privacy_settings = apply_filters( 'rtmedia_privacy_levels', $this->privacy_settings );

		if ( function_exists( 'bp_is_active' ) && ! bp_is_active( 'friends' ) ) {
			unset( $this->privacy_settings[ 'levels' ][ 40 ] );
		}
	}

	/**
	 * Load admin screens
	 *
	 * @global RTMediaAdmin $rtmedia_admin Class for loading admin screen
	 */
	function admin_init() {
		global $rtmedia_admin;
		$rtmedia_admin = new RTMediaAdmin();
	}

	function media_screen() {
		return;
	}

	function get_user_link( $user ) {

		if ( function_exists( 'bp_core_get_user_domain' ) ) {
			$parent_link = bp_core_get_user_domain( $user );
		} else {
			$parent_link = get_author_posts_url( $user );
		}

		return $parent_link;
	}

	public function init_buddypress_options() {
		/**
		 * BuddyPress Settings
		 */
		$bp_media_options = rtmedia_get_site_option( 'bp_media_options' );

		$group = 0;
		if ( isset( $bp_media_options[ 'enable_on_group' ] ) && ! empty( $bp_media_options[ 'enable_on_group' ] ) )
			$group = $bp_media_options[ 'enable_on_group' ];
		else if ( function_exists( "bp_is_active" ) )
			$group = bp_is_active( 'groups' );
		$this->options[ 'buddypress_enableOnGroup' ] = $group;

		$activity = 0;
		if ( isset( $bp_media_options[ 'activity_upload' ] ) && ! empty( $bp_media_options[ 'activity_upload' ] ) )
			$activity = $bp_media_options[ 'activity_upload' ];
		else if ( function_exists( "bp_is_active" ) )
			$activity = bp_is_active( 'activity' );
		$this->options[ 'buddypress_enableOnActivity' ] = $activity;

		$this->options[ 'buddypress_enableOnProfile' ] = 1;

		/* Last settings updated in options. Update them in DB & after this no other option would be saved in db */
		rtmedia_update_site_option( 'rtmedia-options', $this->options );
	}

	public function init_site_options() {

		$bp_media_options = rtmedia_get_site_option( 'bp_media_options' );

		$defaults = array(
			'general_enableAlbums' => 1,
			'general_enableComments' => 0,
			'general_downloadButton' => (isset( $bp_media_options[ 'download_enabled' ] )) ? $bp_media_options[ 'download_enabled' ] : 0,
			'general_enableLightbox' => (isset( $bp_media_options[ 'enable_lightbox' ] )) ? $bp_media_options[ 'enable_lightbox' ] : 1,
			'general_perPageMedia' => (isset( $bp_media_options[ 'default_count' ] )) ? $bp_media_options[ 'default_count' ] : 10,
			'general_enableMediaEndPoint' => 0,
			'general_showAdminMenu' => (isset( $bp_media_options[ 'show_admin_menu' ] )) ? $bp_media_options[ 'show_admin_menu' ] : 0,
			'general_videothumbs' => 2,
			'general_jpeg_image_quality' => 90,
			'general_AllowUserData' => 1
		);


		foreach ( $this->allowed_types as $type ) {
			// invalid keys handled in sanitize method
			$defaults[ 'allowedTypes_' . $type[ 'name' ] . '_enabled' ] = 1;
			$defaults[ 'allowedTypes_' . $type[ 'name' ] . '_featured' ] = 0;
		}

		/* Previous Sizes values from buddypress is migrated */
		foreach ( $this->default_sizes as $type => $typeValue ) {
			foreach ( $typeValue as $size => $sizeValue ) {
				foreach ( $sizeValue as $dimension => $value ) {
					switch ( $type ) {
						case 'photo':
							if ( isset( $bp_media_options[ 'sizes' ][ 'image' ][ $size ][ $dimension ] ) && ! empty( $bp_media_options[ 'sizes' ][ 'image' ][ $size ][ $dimension ] ) )
								$value = $bp_media_options[ 'sizes' ][ 'image' ][ $size ][ $dimension ];
							break;
						case 'video':
						case 'music':
							$old = ($type == 'video') ? 'video' : ($type == 'music') ? 'audio' : '';
							switch ( $size ) {
								case 'activityPlayer':
									if ( isset( $bp_media_options[ 'sizes' ][ $old ][ 'medium' ][ $dimension ] ) && ! empty( $bp_media_options[ 'sizes' ][ $old ][ 'medium' ][ $dimension ] ) )
										$value = $bp_media_options[ 'sizes' ][ $old ][ 'medium' ][ $dimension ];
									break;
								case 'singlePlayer':
									if ( isset( $bp_media_options[ 'sizes' ][ $old ][ 'large' ][ $dimension ] ) && ! empty( $bp_media_options[ 'sizes' ][ $old ][ 'large' ][ $dimension ] ) )
										$value = $bp_media_options[ 'sizes' ][ $old ][ 'large' ][ $dimension ];
									break;
							}
							break;
					}
					$defaults[ 'defaultSizes_' . $type . '_' . $size . '_' . $dimension ] = $value;
				}
			}
		}

		/* Privacy */
		$defaults[ 'privacy_enabled' ] = (isset( $bp_media_options[ 'privacy_enabled' ] )) ? $bp_media_options[ 'privacy_enabled' ] : 0;
		$defaults[ 'privacy_default' ] = (isset( $bp_media_options[ 'default_privacy_level' ] )) ? $bp_media_options[ 'default_privacy_level' ] : 0;
		$defaults[ 'privacy_userOverride' ] = (isset( $bp_media_options[ 'privacy_override_enabled' ] )) ? $bp_media_options[ 'privacy_override_enabled' ] : 0;

		$defaults[ 'styles_custom' ] = (isset( $bp_media_options[ 'styles_custom' ] )) ? $bp_media_options[ 'styles_custom' ] : '';
		$defaults[ 'styles_enabled' ] = (isset( $bp_media_options[ 'styles_enabled' ] )) ? $bp_media_options[ 'styles_enabled' ] : 1;

		$this->options = $defaults;

		$this->init_buddypress_options();
	}

	/**
	 * Defines all the constants if undefined. Can be overridden by
	 * defining them elsewhere, say wp-config.php
	 */
	public function constants() {

		/* If the plugin is installed. */
		if ( ! defined( 'RTMEDIA_IS_INSTALLED' ) )
			define( 'RTMEDIA_IS_INSTALLED', 1 );


		/* Required Version  */
		if ( ! defined( 'RTMEDIA_REQUIRED_BP' ) )
			define( 'RTMEDIA_REQUIRED_BP', '1.7' );


		/* Slug Constants for building urls */

		/* Media slugs */

		if ( ! defined( 'RTMEDIA_MEDIA_SLUG' ) )
			define( 'RTMEDIA_MEDIA_SLUG', 'media' );

		if ( ! defined( 'RTMEDIA_MEDIA_LABEL' ) )
			define( 'RTMEDIA_MEDIA_LABEL', __( 'Media', 'buddypress-media' ) );

		if ( ! defined( 'RTMEDIA_ALL_SLUG' ) )
			define( 'RTMEDIA_ALL_SLUG', 'all' );

		if ( ! defined( 'RTMEDIA_ALL_LABEL' ) )
			define( 'RTMEDIA_ALL_LABEL', __( 'All', 'buddypress-media' ) );

		if ( ! defined( 'RTMEDIA_ALBUM_SLUG' ) )
			define( 'RTMEDIA_ALBUM_SLUG', 'album' );

		if ( ! defined( 'RTMEDIA_ALBUM_PLURAL_SLUG' ) )
			define( 'RTMEDIA_ALBUM_PLURAL_SLUG', 'albums' );

		if ( ! defined( 'RTMEDIA_ALBUM_LABEL' ) )
			define( 'RTMEDIA_ALBUM_LABEL', __( 'Album', 'buddypress-media' ) );

		if ( ! defined( 'RTMEDIA_ALBUM_PLURAL_LABEL' ) )
			define( 'RTMEDIA_ALBUM_PLURAL_LABEL', __( 'Albums', 'buddypress-media' ) );

		/* Upload slug */
		if ( ! defined( 'RTMEDIA_UPLOAD_SLUG' ) )
			define( 'RTMEDIA_UPLOAD_SLUG', 'upload' );

		/* Upload slug */
		if ( ! defined( 'RTMEDIA_UPLOAD_LABEL' ) )
			define( 'RTMEDIA_UPLOAD_LABEL', __( 'Upload', 'buddypress-media' ) );

		/* Global Album/Wall Post */
		if ( ! defined( 'RTMEDIA_GLOBAL_ALBUM_LABEL' ) )
			define( 'RTMEDIA_GLOBAL_ALBUM_LABEL', __( 'Wall Post', 'buddypress-media' ) );

		$this->define_type_constants();
	}

	function define_type_constants() {

		if ( ! isset( $this->allowed_types ) )
			return;
		foreach ( $this->allowed_types as $type ) {

			if ( ! isset( $type[ 'name' ] ) || $type[ 'name' ] === '' )
				continue;

			$name = $type[ 'name' ];

			if ( isset( $type[ 'plural' ] ) && $type[ 'plural' ] != '' ) {
				$plural = $type[ 'plural' ];
			} else {
				$plural = $name . 's';
			}

			if ( isset( $type[ 'label' ] ) && $type[ 'label' ] != '' ) {
				$label = $type[ 'label' ];
			} else {
				$label = ucfirst( $name );
			}

			if ( isset( $type[ 'plural_label' ] ) && $type[ 'plural_label' ] != '' ) {
				$label_plural = $type[ 'plural_label' ];
			} else {
				$label_plural = ucfirst( $plural );
			}

			$slug = strtoupper( $name );

			if ( ! defined( 'RTMEDIA_' . $slug . '_SLUG' ) )
				define( 'RTMEDIA_' . $slug . '_SLUG', $name );
			if ( ! defined( 'RTMEDIA_' . $slug . '_PLURAL_SLUG' ) )
				define( 'RTMEDIA_' . $slug . '_PLURAL_SLUG', $plural );
			if ( ! defined( 'RTMEDIA_' . $slug . '_LABEL' ) )
				define( 'RTMEDIA_' . $slug . '_LABEL', $label );
			if ( ! defined( 'RTMEDIA_' . $slug . '_PLURAL_LABEL' ) )
				define( 'RTMEDIA_' . $slug . '_PLURAL_LABEL', $label_plural );
		}
	}

	/**
	 * Hooks the plugin into BuddyPress via 'bp_include' action.
	 * Initialises the plugin's functionalities, options,
	 * loads media for Profiles and Groups.
	 * Creates Admin panels
	 * Loads accessory functions
	 *
	 * @global BPMediaAdmin $bp_media_admin
	 */
	function init() {
		// set metatable in $wpdb
		$this->set_rtmedia_meta_wpdbfix();

		// rtMedia db upgrade
		add_action( 'rt_db_upgrade', array( $this, 'fix_parent_id' ) );
		add_action( 'rt_db_upgrade', array( $this, 'fix_privacy' ) );
		add_action( 'rt_db_upgrade', array( $this, 'fix_group_media_privacy' ) );
		add_action( 'rt_db_upgrade', array( $this, 'fix_db_collation' ) );
		$this->update_db();
		remove_action( 'rt_db_upgrade', array( $this, 'fix_parent_id' ) );
		remove_action( 'rt_db_upgrade', array( $this, 'fix_privacy' ) );
		remove_action( 'rt_db_upgrade', array( $this, 'fix_group_media_privacy' ) );
		remove_action( 'rt_db_upgrade', array( $this, 'fix_db_collation' ) );

		$this->set_allowed_types(); // Define allowed types
		$this->constants(); // Define constants
		$this->redirect_on_change_slug();
		$this->set_default_sizes(); // set default sizes
		$this->set_privacy(); // set privacy

		/**
		 * Load options/settings
		 */
		$this->set_site_options();


		/**
		 * Add a settings link to the Plugin list screen
		 */
//            add_filter('plugin_action_links', array($this, 'settings_link'), 10, 2);

		/**
		 * BuddyPress - Media Navigation Tab Inject
		 *
		 */
		/**
		 * Load accessory functions
		 */
//			new BPMediaActivity();
		$class_construct = array(
			'deprecated' => true,
			'interaction' => true,
			'upload_shortcode' => false,
			'gallery_shortcode' => false,
			'upload_endpoint' => false,
			'privacy' => false,
			'nav' => true,
			'like' => false,
			'featured' => false,
			'GroupFeatured' => false,
			'ViewCount' => false,
			'GalleryItemAction' => false,
            'LoginPopup' => false,
			'CommentNotification' => false,
			'LikeNotification' => false,
		);
		global $rtmedia_nav;

		/** Legacy code for Add-ons * */
		$bp_class_construct = apply_filters( 'bpmedia_class_construct', array() );
		foreach ( $bp_class_construct as $classname => $global_scope ) {
			$class = 'BPMedia' . ucfirst( $classname );
			if ( class_exists( $class ) ) {
				if ( $global_scope == true ) {
					global ${'bp_media_' . $classname};
					${'bp_media_' . $classname} = new $class();
				} else {
					new $class();
				}
			}
		}
		/** ------------------- * */
		$class_construct = apply_filters( 'rtmedia_class_construct', $class_construct );

		$class_construct[ 'Group' ] = false; // will be constructed after rtmedia pro class.

		foreach ( $class_construct as $key => $global_scope ) {
			$classname = '';
			$ck = explode( '_', $key );

			foreach ( $ck as $cn ) {
				$classname .= ucfirst( $cn );
			}

			$class = 'RTMedia' . $classname;

			if ( class_exists( $class ) ) {
				if ( $global_scope == true ) {
					global ${'rtmedia_' . $key};
					${'rtmedia_' . $key} = new $class();
				} else {
					new $class();
				}
			}
		}

		$this->set_allowed_types(); // Define allowed types

		global $rtmedia_buddypress_activity;
		$rtmedia_buddypress_activity = new RTMediaBuddyPressActivity();
		$media = new RTMediaMedia();
		$media->delete_hook();


		global $rtmedia_ajax;
		$rtmedia_ajax = new RTMediaAJAX();
		//API Classes
		global $rtmediajsonapi;
		$rtmediajsonapi = new RTMediaJsonApi();

		do_action( 'bp_media_init' ); // legacy For plugin using this actions
		do_action( 'rtmedia_init' );
	}

	function set_rtmedia_meta_wpdbfix() {
		global $wpdb;
		$media_meta = new RTMediaMeta();
		$wpdb->mediameta = $media_meta->model->table_name;
	}

	function redirect_on_change_slug() {
		$old_slugs = rtmedia_get_site_option( "rtmedia_old_media_slug", false, true );
		$current_slugs = rtmedia_get_site_option( "rtmedia_current_media_slug", false, false );
		if ( $current_slugs === false ) {
			rtmedia_update_site_option( "rtmedia_current_media_slug", RTMEDIA_MEDIA_SLUG );
			return;
		}
		if ( $current_slugs === RTMEDIA_MEDIA_SLUG )
			return;
		if ( $old_slugs === false )
			$old_slugs = array();
		$old_slugs[] = $current_slugs;
		rtmedia_update_site_option( "rtmedia_current_media_slug", RTMEDIA_MEDIA_SLUG );
	}

	/**
	 * Loads translations
	 */
	static
			function load_translation() {
		load_plugin_textdomain( 'buddypress-media', false, basename( RTMEDIA_PATH ) . '/languages/' );
	}

	function check_global_album() {
		$album = new RTMediaAlbum();
		$global_album = $album->get_default();
		//** Hack for plupload default name
		if ( isset( $_POST[ "action" ] ) && isset( $_POST[ "mode" ] ) && $_POST[ "mode" ] == "file_upload" ) {
			unset( $_POST[ "name" ] );
		}

        //**
        global $rtmedia_error;
        if (isset($rtmedia_error) && $rtmedia_error === true)
            return false;
        if (!$global_album) {
            $global_album = $album->add_global( __( "Wall Posts", 'buddypress-media' ) );
        }

	    // fix multisite global album doesn't exist issue.
	    if( is_multisite() && ! rtmedia_get_site_option( 'rtmedia_fix_multisite_global_albums', false ) ){
		    $model = new RTMediaModel();
		    $global_albums = rtmedia_global_albums();
		    $album_objects = $model->get_media( array( 'id' => ( $global_albums ) ), false, false );
		    if( empty( $album_objects ) ){
			    $global_album = $album->add_global( __( "Wall Posts", 'buddypress-media' ) );
		    }
		    rtmedia_update_site_option( 'rtmedia_fix_multisite_global_albums', true );
	    }
    }

	function default_count() {
		$count = $this->posts_per_page;
		if ( array_key_exists( 'default_count', $this->options ) ) {
			$count = $this->options[ 'default_count' ];
		}
		$count = ( ! is_int( $count )) ? 0 : $count;
		return ( ! $count) ? 10 : $count;
	}

	static
			function plugin_get_version( $path = NULL ) {
		require_once(ABSPATH . 'wp-admin/includes/plugin.php');
		$path = ($path) ? $path : RTMEDIA_PATH . 'index.php';
		$plugin_data = get_plugin_data( $path );
		$plugin_version = $plugin_data[ 'Version' ];
		return $plugin_version;
	}

	function update_db() {
		$update = new RTDBUpdate( false, RTMEDIA_PATH . "index.php", RTMEDIA_PATH . "app/schema/", true );
		/* Current Version. */
		if ( ! defined( 'RTMEDIA_VERSION' ) ) {
			define( 'RTMEDIA_VERSION', $update->db_version );
		}
		if ( $update->check_upgrade() ) {
			$update->do_upgrade();
		}
		if ( ! $update->table_exists( $update->genrate_table_name( "rtm_media" ) ) ) {
			delete_site_option( $update->get_db_version_option_name() );
			$update->install_db_version = "0";
			$update->do_upgrade();
		}
	}

	function create_table_error_notice() {
		global $rtmedia_error;
		$rtmedia_error = true;
		echo "<div class='error'><p><strong>rtMedia</strong>" . __( ": Can't Create Database table. Please check create table permission.", 'buddypress-media' ) . "</p></div>";
	}

	function enqueue_scripts_styles() {
		global $rtmedia;
		if ( wp_script_is( 'wp-mediaelement', 'registered' ) ) {
			wp_enqueue_style( 'wp-mediaelement' );
			wp_enqueue_script( 'wp-mediaelement' );
		} else {
			wp_enqueue_script( 'wp-mediaelement', RTMEDIA_URL . 'lib/media-element/mediaelement-and-player.min.js', '', RTMEDIA_VERSION );
			wp_enqueue_style( 'wp-mediaelement', RTMEDIA_URL . 'lib/media-element/mediaelementplayer.min.css', '', RTMEDIA_VERSION );
			wp_enqueue_script( 'wp-mediaelement-start', RTMEDIA_URL . 'lib/media-element/wp-mediaelement.js', 'wp-mediaelement', RTMEDIA_VERSION, true );
		}


		// Dashicons: Needs if not loaded by WP
		wp_enqueue_style( 'dashicons' );

		// Dont enqueue rtmedia.min.css if default styles is checked false in rtmedia settings
		$suffix = ( function_exists( 'rtm_get_script_style_suffix' ) ) ? rtm_get_script_style_suffix() : '.min';

		if ( ! ( isset( $rtmedia->options ) && isset( $rtmedia->options[ 'styles_enabled' ] ) && $rtmedia->options[ 'styles_enabled' ] == 0) ) {
			wp_enqueue_style( 'rtmedia-main', RTMEDIA_URL . 'app/assets/css/rtmedia' . $suffix . '.css', '', RTMEDIA_VERSION );
		}

		if( $suffix === '' ) {
			wp_enqueue_script( 'rtmedia-magnific-popup', RTMEDIA_URL . 'app/assets/js/vendors/magnific-popup.js', array( 'jquery', 'wp-mediaelement' ), RTMEDIA_VERSION );
			wp_enqueue_script( 'rtmedia-admin-tabs', RTMEDIA_URL . 'app/assets/admin/js/vendors/tabs.js', array( 'jquery', 'wp-mediaelement' ), RTMEDIA_VERSION );
			wp_enqueue_script( 'rtmedia-main', RTMEDIA_URL . 'app/assets/js/rtMedia.js', array( 'jquery', 'wp-mediaelement' ), RTMEDIA_VERSION );
		} else {
			wp_enqueue_script( 'rtmedia-main', RTMEDIA_URL . 'app/assets/js/rtmedia.min.js', array( 'jquery', 'wp-mediaelement' ), RTMEDIA_VERSION );
		}

		wp_localize_script( 'rtmedia-main', 'rtmedia_ajax_url', admin_url( 'admin-ajax.php' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_media_slug', RTMEDIA_MEDIA_SLUG );
		wp_localize_script( 'rtmedia-main', 'rtmedia_lightbox_enabled', strval( $this->options[ "general_enableLightbox" ] ) );
        
        $direct_upload = ( isset( $this->options[ "general_direct_upload" ] ) ? $this->options[ "general_direct_upload" ] : '0' );
        
        wp_localize_script( 'rtmedia-main', 'rtmedia_direct_upload_enabled', $direct_upload );
		//gallery reload after media upload, by default true
		wp_localize_script( 'rtmedia-main', 'rtmedia_gallery_reload_on_upload', '1' );

		//javascript messages
		wp_localize_script( 'rtmedia-magnific', 'rtmedia_load_more', __( 'Loading media', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_empty_activity_msg', __( 'Please enter some content to post.', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_empty_comment_msg', __( 'Empty Comment is not allowed.', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_media_delete_confirmation', __( 'Are you sure you want to delete this media?', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_media_comment_delete_confirmation', __( 'Are you sure you want to delete this comment?', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_album_delete_confirmation', __( 'Are you sure you want to delete this Album?', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_drop_media_msg', __( 'Drop files here', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_album_created_msg', ' ' . __( 'album created successfully.', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_something_wrong_msg', __( 'Something went wrong. Please try again.', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_empty_album_name_msg', __( 'Enter an album name.', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_max_file_msg', __( 'Max file Size Limit : ', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_allowed_file_formats', __( 'Allowed File Formats', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_select_all_visible', __( 'Select All Visible', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_unselect_all_visible', __( 'Unselect All Visible', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_no_media_selected', __( 'Please select some media.', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_selected_media_delete_confirmation', __( 'Are you sure you want to delete the selected media?', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_selected_media_move_confirmation', __( 'Are you sure you want to move the selected media?', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_waiting_msg', __( 'Waiting', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_uploaded_msg', __( 'Uploaded', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_uploading_msg', __( 'Uploading', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_upload_failed_msg', __( 'Failed', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_close', __( 'Close', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_edit', __( 'Edit', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_delete', __( 'Delete', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_edit_media', __( 'Edit Media', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_remove_from_queue', __( 'Remove from queue', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_add_more_files_msg', __( 'Add more files', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_file_extension_error_msg', __( 'File not supported', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_more', __( 'more', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_less', __( 'less', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtmedia_delete_uploaded_media', __( 'This media is uploaded. Are you sure you want to delete this media?', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-main', 'rtm_wp_version', get_bloginfo( 'version' ) );
		wp_localize_script( 'rtmedia-backbone', 'rMedia_loading_media', RTMEDIA_URL . "app/assets/admin/img/boxspinner.gif" );
		$rtmedia_media_thumbs = array();
		foreach ( $this->allowed_types as $key_type => $value_type ) {
			$rtmedia_media_thumbs[ $key_type ] = $value_type[ 'thumbnail' ];
		}
		wp_localize_script( 'rtmedia-backbone', 'rtmedia_media_thumbs', $rtmedia_media_thumbs );
		wp_localize_script( 'rtmedia-backbone', 'rtmedia_set_featured_image_msg', __( 'Featured media set successfully.', 'buddypress-media' ) );
		wp_localize_script( 'rtmedia-backbone', 'rtmedia_unset_featured_image_msg', __( 'Featured media removed successfully.', 'buddypress-media' ) );

//      We are not using it anymore and hence commenting
//		global $rtmedia_query;
//		if( class_exists('BuddyPress') ) {
//			$rtmedia_user_domain  = trailingslashit ( bp_displayed_user_domain() . constant('RTMEDIA_MEDIA_SLUG') );
//		} else {
//			$rtmedia_user_domain = trailingslashit( trailingslashit( get_author_posts_url($rtmedia_query->query['context_id'] ) ). constant('RTMEDIA_MEDIA_SLUG') );
//		}
//		wp_localize_script ( 'rtmedia-backbone', 'rtmedia_user_domain', $rtmedia_user_domain );
		// Enqueue touchswipe
		wp_enqueue_script( 'rtmedia-touchswipe', RTMEDIA_URL . 'lib/touchswipe/jquery.touchSwipe.min.js', array( 'jquery' ), RTMEDIA_VERSION, true );

		if ( isset( $rtmedia->options ) && isset( $rtmedia->options[ 'general_masonry_layout' ] ) && $rtmedia->options[ 'general_masonry_layout' ] == 1 ) {
			if ( wp_script_is( "jquery-masonry", "registered" ) ) {
				wp_enqueue_style( 'jquery-masonry' );
				wp_enqueue_script( 'jquery-masonry' );
				wp_localize_script( 'rtmedia-main', 'rtmedia_masonry_layout', 'true' );
			} else {
				wp_localize_script( 'rtmedia-main', 'rtmedia_masonry_layout', 'false' );
			}
		} else {
			wp_localize_script( 'rtmedia-main', 'rtmedia_masonry_layout', 'false' );
		}

		if ( isset( $rtmedia->options[ 'general_display_media' ] ) ) {
			wp_localize_script( 'rtmedia-backbone', 'rtmedia_load_more_or_pagination', ( string ) $rtmedia->options[ 'general_display_media' ] );
		} else {
			wp_localize_script( 'rtmedia-backbone', 'rtmedia_load_more_or_pagination', 'load_more' );
		}

		if ( isset( $rtmedia->options[ 'buddypress_enableOnActivity' ] ) ) {
			wp_localize_script( 'rtmedia-backbone', 'rtmedia_bp_enable_activity', ( string ) $rtmedia->options[ 'buddypress_enableOnActivity' ] );
		} else {
			wp_localize_script( 'rtmedia-backbone', 'rtmedia_bp_enable_activity', '0' );
		}

		wp_localize_script( 'rtmedia-backbone', 'rtmedia_upload_progress_error_message', __( "There are some uploads in progress. Do you want to cancel them?", 'buddypress-media' ) );

		// localise media size config
		$media_size_config = array(
			'photo' => array(
				'thumb' => array(
					'width' => $rtmedia->options[ 'defaultSizes_photo_thumbnail_width' ],
					'height' => $rtmedia->options[ 'defaultSizes_photo_thumbnail_height' ],
					'crop' => $rtmedia->options[ 'defaultSizes_photo_thumbnail_crop' ],
				),
				'medium' => array(
					'width' => $rtmedia->options[ 'defaultSizes_photo_medium_width' ],
					'height' => $rtmedia->options[ 'defaultSizes_photo_medium_height' ],
					'crop' => $rtmedia->options[ 'defaultSizes_photo_medium_crop' ],
				),
				'large' => array(
					'width' => $rtmedia->options[ 'defaultSizes_photo_large_width' ],
					'height' => $rtmedia->options[ 'defaultSizes_photo_large_height' ],
					'crop' => $rtmedia->options[ 'defaultSizes_photo_large_crop' ],
				),
			),
			'video' => array(
				'activity_media' => array(
					'width' => $rtmedia->options[ 'defaultSizes_video_activityPlayer_width' ],
					'height' => $rtmedia->options[ 'defaultSizes_video_activityPlayer_height' ],
				),
				'single_media' => array(
					'width' => $rtmedia->options[ 'defaultSizes_video_singlePlayer_width' ],
					'height' => $rtmedia->options[ 'defaultSizes_video_singlePlayer_height' ],
				),
			),
			'music' => array(
				'activity_media' => array(
					'width' => $rtmedia->options[ 'defaultSizes_music_activityPlayer_width' ],
				),
				'single_media' => array(
					'width' => $rtmedia->options[ 'defaultSizes_music_singlePlayer_width' ],
				),
			),
			'featured' => array(
				'default' => array(
					'width' => $rtmedia->options[ 'defaultSizes_featured_default_width' ],
					'height' => $rtmedia->options[ 'defaultSizes_featured_default_height' ],
					'crop' => $rtmedia->options[ 'defaultSizes_featured_default_crop' ],
				)
			),
		);
		wp_localize_script( 'rtmedia-main', 'rtmedia_media_size_config', $media_size_config );

	}

	function set_bp_bar() {
		remove_action( 'bp_adminbar_menus', 'bp_adminbar_account_menu', 4 );
	}

	function set_friends_object() {
		if ( is_user_logged_in() ) {
			$user = get_current_user_id();
			$friends = friends_get_friend_user_ids( $user );
		} else {
			$user = 0;
		}
	}

	function filter_image_sizes_details( $sizes ) {
		if ( isset( $_REQUEST[ 'post_id' ] ) ) {
			$sizes = $this->unset_bp_media_image_sizes_details( $sizes );
		} elseif ( isset( $_REQUEST[ 'id' ] ) ) { //For Regenerate Thumbnails Plugin
			$model = new RTMediaModel();
			$result = $model->get( array( 'media_id' => $_REQUEST[ 'id' ] ) );
			if ( ! empty( $result ) ) {
				$bp_media_sizes = $this->image_sizes();
				$sizes = array(
					'rt_media_thumbnail' => $bp_media_sizes[ 'thumbnail' ],
					'rt_media_activity_image' => $bp_media_sizes[ 'activity' ],
					'rt_media_single_image' => $bp_media_sizes[ 'single' ],
					'rt_media_featured_image' => $bp_media_sizes[ 'featured' ],
				);
			} else {
				$sizes = $this->unset_bp_media_image_sizes_details( $sizes );
			}
		}
		return $sizes;
	}

	function rtmedia_custom_image_sizes_choose( $sizes ) {
		$custom_sizes = array(
			'rt_media_thumbnail' => "rtMedia Thumbnail",
			'rt_media_activity_image' => "rtMedia Activity Image",
			'rt_media_single_image' => "rtMedia Single Image",
			'rt_media_featured_image' => "rtMedia Fetured Image",
		);
		return array_merge( $sizes, $custom_sizes );
	}

	function filter_image_sizes( $sizes ) {
		if ( isset( $_REQUEST[ 'postid' ] ) ) { //For Regenerate Thumbnails Plugin
			if ( $parent_id = get_post_field( 'post_parent', $_REQUEST[ 'postid' ] ) ) {
				$post_type = get_post_field( 'post_type', $parent_id );
				if ( $post_type == 'rtmedia_album' ) {
					$sizes = array(
						'rt_media_thumbnail', 'rt_media_activity_image', 'rt_media_single_image', 'rt_media_featured_image'
					);
				} else {
					$sizes = $this->unset_bp_media_image_sizes( $sizes );
				}
			} else {
				$sizes = $this->unset_bp_media_image_sizes( $sizes );
			}
		}

		return $sizes;
	}

	function unset_bp_media_image_sizes_details( $sizes ) {
		if ( isset( $sizes[ 'rt_media_thumbnail' ] ) )
			unset( $sizes[ 'rt_media_thumbnail' ] );
		if ( isset( $sizes[ 'rt_media_activity_image' ] ) )
			unset( $sizes[ 'rt_media_activity_image' ] );
		if ( isset( $sizes[ 'rt_media_single_image' ] ) )
			unset( $sizes[ 'rt_media_single_image' ] );
		if ( isset( $sizes[ 'rt_media_featured_image' ] ) )
			unset( $sizes[ 'rt_media_featured_image' ] );
		return $sizes;
	}

	function unset_bp_media_image_sizes( $sizes ) {
		if ( ($key = array_search( 'rt_media_thumbnail', $sizes )) !== false )
			unset( $sizes[ $key ] );
		if ( ($key = array_search( 'rt_media_activity_image', $sizes )) !== false )
			unset( $sizes[ $key ] );
		if ( ($key = array_search( 'rt_media_single_image', $sizes )) !== false )
			unset( $sizes[ $key ] );
		if ( ($key = array_search( 'rt_media_featured_image', $sizes )) !== false )
			unset( $sizes[ $key ] );
		return $sizes;
	}

}

function parentlink_global_album( $id ) {
	$global_albums = RTMediaAlbum::get_globals();
	$parent_link = "";
	if ( is_array( $global_albums ) && $global_albums != "" ) {
		if ( in_array( $id, $global_albums ) && function_exists( "bp_displayed_user_id" ) ) {
			$disp_user = bp_displayed_user_id();
			$curr_user = get_current_user_id();
			if ( $disp_user == $curr_user ) {
				$parent_link = get_rtmedia_user_link( $curr_user );
			} else {
				$parent_link = get_rtmedia_user_link( $disp_user );
			}
			global $rtmedia_query;
			if ( isset( $rtmedia_query->is_gallery_shortcode ) && $rtmedia_query->is_gallery_shortcode == true ) {
				$parent_link = get_rtmedia_user_link( get_current_user_id() );
			}
		}
	}
	return $parent_link;
}

function get_rtmedia_permalink( $id ) {
	$mediaModel = new RTMediaModel();
	$media = $mediaModel->get( array( 'id' => intval( $id ) ) );
	global $rtmedia_query;

	// Adding filter to get permalink for current blog
	add_filter( 'bp_get_root_domain', 'rtmedia_get_current_blog_url' );

	if ( ! isset( $media[ 0 ]->context ) ) {
		if ( function_exists( "bp_get_groups_root_slug" ) && isset( $rtmedia_query->query ) && isset( $rtmedia_query->query[ "context" ] ) && $rtmedia_query->query[ "context" ] == "group" ) {
			$parent_link = get_rtmedia_group_link( $rtmedia_query->query[ "context_id" ] );
		} else {
			// check for global album
			$parent_link = parentlink_global_album( $id );
			if ( $parent_link == "" ) {
				$parent_link = get_rtmedia_user_link( $media[ 0 ]->media_author );
			}
		}
	} else {
		if ( function_exists( "bp_get_groups_root_slug" ) && $media[ 0 ]->context == 'group' ) {
			$parent_link = get_rtmedia_group_link( $media[ 0 ]->context_id );
		} else {
			// check for global album
			$parent_link = parentlink_global_album( $id );
			if ( $parent_link == "" ) {
				$parent_link = get_rtmedia_user_link( $media[ 0 ]->media_author );
			}
		}
	}

	$parent_link = trailingslashit( $parent_link );

	// Removing filter so that doesn't affect other calls to this function
	remove_filter( 'bp_get_root_domain', 'rtmedia_get_current_blog_url' );

	return trailingslashit( $parent_link . RTMEDIA_MEDIA_SLUG . '/' . $id );
}

function get_rtmedia_user_link( $id ) {
	if ( function_exists( 'bp_core_get_user_domain' ) ) {
		$parent_link = bp_core_get_user_domain( $id );
	} else {
		$parent_link = get_author_posts_url( $id );
	}
	return $parent_link;
}

function rtmedia_update_site_option( $option_name, $option_value ) {
	if ( is_multisite() ) {
		return update_option( $option_name, $option_value );
	} else {
		return update_site_option( $option_name, $option_value );
	}
}

function get_rtmedia_group_link( $group_id ) {
	$group = groups_get_group( array( 'group_id' => $group_id ) );
	return apply_filters( 'rtmedia_get_group_link', bp_get_group_permalink( $group ) );
}

function rtmedia_get_site_option( $option_name, $default = false ) {
	if ( is_multisite() ) {
		$return_val = get_option( $option_name, $default );
		if ( $return_val === false ) {
			$return_val = get_site_option( $option_name, $default );
			if ( $return_val === false ) {
				if ( function_exists( "bp_get_option" ) ) {
					$return_val = bp_get_option( $option_name, $default );
				}
			}
			rtmedia_update_site_option( $option_name, $return_val );
		}
	} else {
		$return_val = get_site_option( $option_name, $default );
		if ( $return_val === false ) {
			if ( function_exists( "bp_get_option" ) ) {
				$return_val = bp_get_option( $option_name, $default );
				rtmedia_update_site_option( $option_name, $return_val );
			}
		}
	}
	if ( $default !== false && $return_val === false ) {
		$return_val = $default;
	}
	return $return_val;
}

/**
 * This wraps up the main rtMedia class. Three important notes:
 *
 * 1. All the constants can be overridden.
 *    So, you could use, 'portfolio' instead of 'media'
 * 2. The default thumbnail and display sizes can be filtered
 *    using 'bpmedia_media_sizes' hook
 * 3. The excerpts and string sizes can be filtered
 *    using 'bpmedia_excerpt_lengths' hook
 *
 */


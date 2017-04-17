<?php
/**
 * Description of RTMediaAdmin
 *
 * @package    RTMedia
 * @subpackage Admin
 *
 */
if ( ! class_exists( 'RTMediaAdmin' ) ) {

	/**
	 * RTMediaAdmin class.
	 */
	class RTMediaAdmin {

		public $rtmedia_upgrade;
		public $rtmedia_settings;
		public $rtmedia_encoding;
		public $rtmedia_support;
		public $rtmedia_feed;

		/**
		 * Constructor - get the plugin hooked in and ready
		 *
		 * @access public
		 * @return void
		 */
		public function __construct() {
			global $rtmedia;

			// Actions and filters
			add_action( 'init', array( $this, 'video_transcoding_survey_response' ) );
//			add_action( 'admin_init', array( $this, 'presstrends_plugin' ) );
			//$rtmedia_feed = new RTMediaFeed();
			add_filter( 'plugin_action_links_' . RTMEDIA_BASE_NAME, array( &$this, 'plugin_add_settings_link' ) );
			//add_action ( 'wp_ajax_rtmedia_fetch_feed', array( $rtmedia_feed, 'fetch_feed' ), 1 );
			$this->rtmedia_support = new RTMediaSupport();
			add_action( 'wp_ajax_rtmedia_select_request', array( $this->rtmedia_support, 'get_form' ), 1 );
			add_action( 'wp_ajax_rtmedia_cancel_request', create_function( '', 'do_settings_sections("rtmedia-support"); die();' ), 1 );
			add_action( 'wp_ajax_rtmedia_submit_request', array( $this->rtmedia_support, 'submit_request' ), 1 );
			//add_action ( 'wp_ajax_rtmedia_fetch_feed', array( $rtmedia_feed, 'fetch_feed' ), 1 );
			add_action( 'wp_ajax_rtmedia_linkback', array( $this, 'linkback' ), 1 );
			add_action( 'wp_ajax_rtmedia_rt_album_deactivate', 'BPMediaAlbumimporter::bp_album_deactivate', 1 );
			add_action( 'wp_ajax_rtmedia_rt_album_import', 'BPMediaAlbumimporter::bpmedia_ajax_import_callback', 1 );
			add_action( 'wp_ajax_rtmedia_rt_album_import_favorites', 'BPMediaAlbumimporter::bpmedia_ajax_import_favorites', 1 );
			add_action( 'wp_ajax_rtmedia_rt_album_import_step_favorites', 'BPMediaAlbumimporter::bpmedia_ajax_import_step_favorites', 1 );
			add_action( 'wp_ajax_rtmedia_rt_album_cleanup', 'BPMediaAlbumimporter::cleanup_after_install' );
			add_action( 'wp_ajax_rtmedia_convert_videos_form', array( $this, 'convert_videos_mailchimp_send' ), 1 );
			add_action( 'wp_ajax_rtmedia_correct_upload_filetypes', array( $this, 'correct_upload_filetypes' ), 1 );
			add_filter( 'plugin_row_meta', array( $this, 'plugin_meta_premium_addon_link' ), 1, 4 );
			add_action( 'wp_dashboard_setup', array( &$this, 'add_dashboard_widgets' ), 0 );
			add_filter( 'attachment_fields_to_edit', array( $this, 'edit_video_thumbnail' ), null, 2 );
			add_filter( 'attachment_fields_to_save', array( $this, 'save_video_thumbnail' ), null, 2 );
			add_action( 'wp_ajax_rtmedia_hide_video_thumb_admin_notice', array( $this, 'rtmedia_hide_video_thumb_admin_notice' ), 1 );
			add_action( 'wp_ajax_rtmedia_hide_addon_update_notice', array( $this, 'rtmedia_hide_addon_update_notice' ), 1 );
                        add_filter( 'media_row_actions', array( $this, 'modify_medialibrary_permalink' ), 10, 3 );

			$obj_encoding = new RTMediaEncoding( true );

			if ( $obj_encoding->api_key ) {
				add_filter( 'media_row_actions', array( $this, 'add_reencode_link' ), null, 2 );
				add_action( 'admin_head-upload.php', array( $this, 'add_bulk_actions_regenerate' ) );
				add_action( 'admin_footer', array( $this, 'rtmedia_regenerate_thumb_js' ) );
				add_action( 'admin_action_bulk_video_regenerate_thumbnails', array( $this, 'bulk_action_handler' ) );
				add_action( 'admin_action_-1', array( $this, 'bulk_action_handler' ) );
			}

			add_action( 'wp_ajax_rt_media_regeneration', array( $this, 'rt_media_regeneration' ), 1 );

			if ( ! isset( $rtmedia->options ) ) {
				$rtmedia->options = rtmedia_get_site_option( 'rtmedia-options' );
			}

			if ( isset( $_POST[ 'rtmedia-options' ] ) ) {
				if ( isset( $_POST[ 'rtmedia-options' ][ 'general_showAdminMenu' ] ) && '1' == $_POST[ 'rtmedia-options' ][ 'general_showAdminMenu' ] ) {
					add_action( 'admin_bar_menu', array( $this, 'admin_bar_menu' ), 100, 1 );
				}
			} else {
				if ( 1 == intval( $rtmedia->options[ 'general_showAdminMenu' ] ) ) {
					add_action( 'admin_bar_menu', array( $this, 'admin_bar_menu' ), 100, 1 );
				}
			}

			if ( is_admin() ) {
				add_action( 'admin_enqueue_scripts', array( $this, 'ui' ) );
				//bp_core_admin_hook();
				add_action( 'admin_menu', array( $this, 'menu' ), 1 );
				add_action( 'init', array( $this, 'bp_admin_tabs' ) );

				if ( is_multisite() ) {
					add_action( 'network_admin_edit_rtmedia', array( $this, 'save_multisite_options' ) );
				}
			}

			$this->rtmedia_settings = new RTMediaSettings();
			$this->rtmedia_encoding = new RTMediaEncoding();
			//	    show rtmedia advertisement
			//            if(! defined("RTMEDIA_PRO_VERSION") )
			//                add_action ( 'rtmedia_before_default_admin_widgets', array( $this, 'rtmedia_advertisement' ),1);
			if ( ! class_exists( 'BuddyPress' ) ) {
				add_action( 'admin_init', array( $this, 'check_permalink_admin_notice' ) );
			}

			add_action( 'wp_ajax_rtmedia_hide_template_override_notice', array( $this, 'rtmedia_hide_template_override_notice' ), 1 );
			add_action( 'admin_init', array( $this, 'rtmedia_bp_add_update_type' ) );
			add_action( 'wp_ajax_rtmedia_hide_inspirebook_release_notice', array( $this, 'rtmedia_hide_inspirebook_release_notice' ), 1 );
			add_action( 'wp_ajax_rtmedia_hide_social_sync_notice', array( $this, 'rtmedia_hide_social_sync_notice' ), 1 );
            add_action( 'wp_ajax_rtmedia_hide_pro_split_notice', array( $this, 'rtmedia_hide_pro_split_notice' ), 1 );
			$rtmedia_media_import = new RTMediaMediaSizeImporter(); // do not delete this line. We only need to create object of this class if we are in admin section
			if ( class_exists( 'BuddyPress' ) ) {
				$rtmedia_activity_upgrade = new RTMediaActivityUpgrade();
			}
			add_action( 'admin_notices', array( $this, 'rtmedia_admin_notices' ) );
			add_action( 'network_admin_notices', array( $this, 'rtmedia_network_admin_notices' ) );
			add_action( 'admin_init', array( $this, 'rtmedia_addon_license_save_hook' ) );
			add_action( 'admin_init', array( $this, 'rtmedia_migration' ) );

			add_filter( 'removable_query_args', array( $this, 'removable_query_args'), 10, 1 );
		}
                
                function modify_medialibrary_permalink( $action, $post, $detached ) {
                    $rtm_id = rtmedia_id( $post->ID );
                    
                    if ( $rtm_id ) {
                        $link = get_rtmedia_permalink( $rtm_id );                  
                        $title =_draft_or_post_title( $post->post_parent );
                        $action[ 'view' ] = '<a href="' . $link . '" title="' . esc_attr( sprintf( __( 'View &#8220;%s&#8221;', 'buddypress-media' ), $title ) ) . '" rel="permalink">' . __( 'View', 'buddypress-media' ) . '</a>';
                    }            
                    
                    return $action;
                }

		function rtmedia_migration() {
			$rtMigration = new RTMediaMigration();
		}

		function rtmedia_addon_license_save_hook() {
			do_action( 'rtmedia_addon_license_save_hook' );
		}

		/**
		 * Show rtmedia network admin notices.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rtmedia_network_admin_notices() {
			if ( is_multisite() ) {
				$this->upload_filetypes_error();
			}
		}

		/**
		 * Show rtmedia admin notices.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rtmedia_admin_notices() {
			if ( current_user_can( 'list_users' ) ) {
				$this->upload_filetypes_error();
				$this->rtmedia_regenerate_thumbnail_notice();
				$this->rtmedia_addon_update_notice();
				$this->rtmedia_update_template_notice();
				$this->rtmedia_inspirebook_release_notice();
				$this->rtmedia_social_sync_release_notice();
                
                if( !defined( 'RTMEDIA_PRO_PATH' ) ) {
                    $this->rtmedia_pro_split_release_notice();
                }
			}
		}
        
        /*
         * rtMedia Pro split release admin notice
         */
        public function rtmedia_pro_split_release_notice() {
            $site_option = rtmedia_get_site_option( 'rtmedia_pro_split_release_notice' );
			
			if( ( !$site_option || 'hide' != $site_option ) ) {
				rtmedia_update_site_option( 'rtmedia_pro_split_release_notice', 'show' );
				?>
				<div class="updated rtmedia-pro-split-notice">
					<p>
						<span>
                            <b>rtMedia: </b>We have released 30+ premium add-ons for rtMedia plugin. Read more about it <a href="https://rtcamp.com/blog/rtmedia-pro-splitting-major-change/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media" target="_blank">here</a>.                         
						</span>
						<a href="#" onclick="rtmedia_hide_pro_split_notice();" style="float:right">Dismiss</a>
					</p>
				</div>
				<script type="text/javascript">
					function rtmedia_hide_pro_split_notice() {
						var data = { action: 'rtmedia_hide_pro_split_notice' };
						jQuery.post( ajaxurl, data, function ( response ) {
							response = response.trim();
							
                            if( response === "1" )
								jQuery( '.rtmedia-pro-split-notice' ).remove();
						} );
					}
				</script>
				<?php
			}
        }
        
        /*
		 * Hide pro split release notice
		 */

		function rtmedia_hide_pro_split_notice() {
			if ( rtmedia_update_site_option( 'rtmedia_pro_split_release_notice', 'hide' ) ) {
				echo '1';
			} else {
				echo '0';
			}
			die();
		}

		/*
		 *  Show social sync release notice admin notice.
		 */

		function rtmedia_social_sync_release_notice() {
			$site_option = rtmedia_get_site_option( 'rtmedia_social_sync_release_notice' );
			$check_rtmedia_social_sync_installed = file_exists( trailingslashit( WP_PLUGIN_DIR ) . 'rtmedia-social-sync/index.php' );

			if ( ( ! $site_option || 'hide' != $site_option ) && ! $check_rtmedia_social_sync_installed ) {
				rtmedia_update_site_option( 'rtmedia_social_sync_release_notice', 'show' );
				?>
				<div class="updated rtmedia-social-sync-notice">
					<p>
						<span>
							<b>rtMedia: </b> Meet
							<a href="https://rtcamp.com/products/rtmedia-social-sync/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media" target="_blank">
								<b>rtMedia Social Sync</b>
							</a> which allows you to import media from your Facebook account.
						</span>
						<a href="#" onclick="rtmedia_hide_social_sync_notice()" style="float:right">Dismiss</a>
					</p>
				</div>
				<script type="text/javascript">
					function rtmedia_hide_social_sync_notice() {
						var data = { action: 'rtmedia_hide_social_sync_notice' };
						jQuery.post( ajaxurl, data, function ( response ) {
							response = response.trim();
							if ( response === "1" )
								jQuery( '.rtmedia-social-sync-notice' ).remove();
						} );
					}
				</script>
				<?php
			}
		}

		/*
		 * Hide social sync release notice
		 */

		function rtmedia_hide_social_sync_notice() {
			if ( rtmedia_update_site_option( 'rtmedia_social_sync_release_notice', 'hide' ) ) {
				echo '1';
			} else {
				echo '0';
			}
			die();
		}

		/**
		 * Show rtmedia inspirebook release notice.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rtmedia_inspirebook_release_notice() {
			$site_option = rtmedia_get_site_option( 'rtmedia_inspirebook_release_notice' );
			if ( ( ! $site_option || 'hide' != $site_option ) && ( 'inspirebook' != get_stylesheet() ) ) {
				rtmedia_update_site_option( 'rtmedia_inspirebook_release_notice', 'show' );
				?>
				<div class="updated rtmedia-inspire-book-notice">
					<p>
						<span><a href="https://rtcamp.com/products/inspirebook/" target="_blank"><b>Meet
									InspireBook</b></a> - First official rtMedia premium theme.</span>
						<a href="#" onclick="rtmedia_hide_inspirebook_notice()" style="float:right">Dismiss</a>
					</p>
				</div>
				<script type="text/javascript">
					function rtmedia_hide_inspirebook_notice() {
						var data = { action: 'rtmedia_hide_inspirebook_release_notice' };
						jQuery.post( ajaxurl, data, function ( response ) {
							response = response.trim();
							if ( response === "1" )
								jQuery( '.rtmedia-inspire-book-notice' ).remove();
						} );
					}
				</script>
				<?php
			}
		}

		/**
		 * Hide rtmedia inspirebook release notice.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rtmedia_hide_inspirebook_release_notice() {
			if ( rtmedia_update_site_option( 'rtmedia_inspirebook_release_notice', 'hide' ) ) {
				echo '1';
			} else {
				echo '0';
			}
			die();
		}

		/**
		 * Set rtmedia buddypress update type.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rtmedia_bp_add_update_type() {
			if ( class_exists( 'BuddyPress' ) && function_exists( 'bp_activity_set_action' ) ) {
				bp_activity_set_action( 'rtmedia_update', 'rtmedia_update', 'rtMedia Update' );
			}
		}

		/**
		 * Show rtmedia check permalink admin notice.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function check_permalink_admin_notice() {
			global $wp_rewrite;
			if ( empty( $wp_rewrite->permalink_structure ) ) {
				add_action( 'admin_notices', array( $this, 'rtmedia_permalink_notice' ) );
			}
		}

		/**
		 * Define rtmedia permalink notice.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rtmedia_permalink_notice() {
			echo '<div class="error rtmedia-permalink-change-notice">
		    <p> <b>rtMedia:</b> ' . __( ' You must', 'buddypress-media' ) . ' <a href="' . admin_url( 'options-permalink.php' ) . '">' . __( 'update permalink structure', 'buddypress-media' ) . '</a> ' . __( 'to something other than the default for it to work.', 'buddypress-media' ) . ' </p>
		    </div>';
		}

		/**
		 * Define rtmedia addon update notice.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rtmedia_addon_update_notice() {

			$site_option = rtmedia_get_site_option( 'rtmedia-addon-update-notice-3_8' );
			if( is_rt_admin()
				&& ( ! $site_option || 'hide' != $site_option ) ){

				if ( ! $this->check_for_addon_update_notice() ) {
					return;
				}
				rtmedia_update_site_option( 'rtmedia-addon-update-notice-3_8', 'show' );
			?>
				<div class="error rtmedia-addon-upate-notice">
					<p>
						<strong><?php _e( 'rtMedia:', 'buddypress-media' ) ?></strong> <?php _e( 'Please update all premium add-ons that you have purchased from rtCamp from', 'buddypress-media' ) ?> <a href="https://rtcamp.com/my-account/" target="_blank"><?php _e( 'your account', 'buddypress-media' ) ?></a>. <a href="#" onclick="rtmedia_hide_addon_update_notice()" style="float:right"><?php _e( 'Dismiss', 'buddypress-media' ) ?></a>
					</p>
				</div>
				<script type="text/javascript">
					function rtmedia_hide_addon_update_notice() {
						var data = {
							action: 'rtmedia_hide_addon_update_notice'
						};
						jQuery.post( ajaxurl, data, function ( response ) {
							response = response.trim();
							if ( response === "1" )
								jQuery( '.rtmedia-addon-upate-notice' ).remove();
						} );
					}
				</script>
			<?php
			}
		}

		/**
		 * Show rtmedia addon update notice.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return bool $return_falg
		 */
		public function check_for_addon_update_notice() {
			$return_falg = false;

			// check for rtMedia Instagram version
			if ( defined( 'RTMEDIA_INSTAGRAM_PATH' ) ) {
				$plugin_info = get_plugin_data( RTMEDIA_INSTAGRAM_PATH . 'index.php' );
				if ( isset( $plugin_info[ 'Version' ] ) && ( -1 === version_compare( $plugin_info[ 'Version' ], '2.1.14' ) ) ) {
					$return_falg = true;
				}
			} elseif( defined( 'RTMEDIA_PHOTO_TAGGING_PATH' ) ){
				// check for rtMedia Photo Tagging version
				$plugin_info = get_plugin_data( RTMEDIA_PHOTO_TAGGING_PATH . 'index.php' );
				if ( isset( $plugin_info[ 'Version' ] ) && ( -1 === version_compare( $plugin_info[ 'Version' ], '2.2.14' ) ) ) {
					$return_falg = true;
				}
			} elseif( defined( 'RTMEDIA_FFMPEG_PATH' ) ){
				// check for rtMedia FFPMEG version
				$plugin_info = get_plugin_data( RTMEDIA_FFMPEG_PATH . 'index.php' );
				if ( isset( $plugin_info[ 'Version' ] ) && ( -1 === version_compare( $plugin_info[ 'Version' ], '2.1.14' ) ) ) {
					$return_falg = true;
				}
			} elseif( defined( 'RTMEDIA_KALTURA_PATH' ) ){
				// check for rtMedia Kaltura version
				$plugin_info = get_plugin_data( RTMEDIA_KALTURA_PATH . 'index.php' );
				if ( isset( $plugin_info[ 'Version' ] ) && ( -1 === version_compare( $plugin_info[ 'Version' ], '3.0.16' ) ) ) {
					$return_falg = true;
				}
			} elseif( defined( 'RTMEDIA_PRO_PATH' ) ){
				// check for rtMedia Pro version
				$plugin_info = get_plugin_data( RTMEDIA_PRO_PATH . 'index.php' );
				if ( isset( $plugin_info[ 'Version' ] ) && ( -1 === version_compare( $plugin_info[ 'Version' ], '2.6' ) ) ) {
					$return_falg = true;
				}
			} elseif( defined( 'RTMEDIA_SOCIAL_SYNC_PATH' ) ){
				// check for rtMedia Social Sync version
				$plugin_info = get_plugin_data( RTMEDIA_SOCIAL_SYNC_PATH . 'index.php' );
				if ( isset( $plugin_info[ 'Version' ] ) && ( -1 === version_compare( $plugin_info[ 'Version' ], '1.3.1' ) ) ) {
					$return_falg = true;
				}
			} elseif( defined( 'RTMEDIA_MEMBERSHIP_PATH' ) ){
				// check for rtMedia Membership version
				$plugin_info = get_plugin_data( RTMEDIA_MEMBERSHIP_PATH . 'index.php' );
				if ( isset( $plugin_info[ 'Version' ] ) && ( -1 === version_compare( $plugin_info[ 'Version' ], '2.1.5' ) ) ) {
					$return_falg = true;
				}
			} elseif( defined( 'RTMEDIA_WATERMARK_PATH' ) ){
				// check for rtMedia Photo Watermak version
				$plugin_info = get_plugin_data( RTMEDIA_WATERMARK_PATH . 'index.php' );
				if ( isset( $plugin_info[ 'Version' ] ) && ( -1 === version_compare( $plugin_info[ 'Version' ], '1.1.8' ) ) ) {
					$return_falg = true;
				}
			}

			return $return_falg;
		}

		/**
		 * Show buddypress admin tabs.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function bp_admin_tabs() {
			if ( current_user_can( 'manage_options' ) ) {
				add_action( 'bp_admin_tabs', array( $this, 'tab' ) );
			}
		}

		/**
		 * Show rtmedia advertisement.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rtmedia_advertisement() {
			$src = RTMEDIA_URL . 'app/assets/admin/img/rtMedia-pro-ad.png'
			?>
			<div class='rtmedia-admin-ad'>
				<a href='http://rtcamp.com/products/rtmedia-pro/' target='_blank' title='rtMedia Pro'>
					<img src='<?php echo $src; ?>' alt="<?php _e( 'rtMedia Pro is released', 'buddypress-media' ); ?>"/>
				</a>
			</div>
			<?php
		}

		/**
		 * Create the function to output the contents of our Dashboard Widget
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rtmedia_dashboard_widget_function() {
			?>

			<div class="clearfix">

				<div class="rtm-column alignleft">
					<h4 class="sub"><?php _e( 'Media Stats', 'buddypress-media' ); ?></h4>

					<table>
						<tbody>
							<?php
							$rtMedia_model = new RTMediaModel();
							$sql = "select media_type, count(id) as count from {$rtMedia_model->table_name} where blog_id='" . get_current_blog_id() . "' group by media_type";
							global $wpdb;
							$results = $wpdb->get_results( $sql );
							if ( $results ) {
								foreach ( $results as $media ) {
									if ( defined( strtoupper( 'RTMEDIA_' . $media->media_type . '_PLURAL_LABEL' ) ) ) {
										?>
										<tr>
											<td class="b"> <?php echo $media->count; ?> </td>
											<td class="t"><?php echo constant( strtoupper( 'RTMEDIA_' . $media->media_type . '_PLURAL_LABEL' ) ); ?></td>
										</tr>
										<?php
									}
								}
							}
							?>
						</tbody>
					</table>
				</div>

				<div class="rtm-column alignright">
					<h4 class="sub"><?php _e( 'Usage Stats', 'buddypress-media' ); ?></h4>

					<table>
						<tbody> <?php
							$sql = "select count(*) from {$wpdb->users}";
							$results = $wpdb->get_var( $sql );
							?>
							<tr>
								<td class="b"> <?php echo $results; ?> </td>
								<td class="t"><?php _e( 'Total ', 'buddypress-media' ) ?></td>
							</tr>
							<?php
							$sql = "select count(distinct media_author) from {$rtMedia_model->table_name}";
							$results = $wpdb->get_var( $sql );
							?>
							<tr>
								<td class="b"> <?php echo $results; ?> </td>
								<td class="t"><?php _e( 'With Media', 'buddypress-media' ) ?></td>
							</tr>
							<?php
							$sql = "select count(*) from $wpdb->comments where comment_post_ID in (select media_id from {$rtMedia_model->table_name})";
							$results = $wpdb->get_var( $sql );
							?>
							<tr>
								<td class="b"> <?php echo $results; ?> </td>
								<td class="t"><?php _e( 'Comments ', 'buddypress-media' ) ?></td>
							</tr>
							<?php
							$sql = "select sum(likes) from {$rtMedia_model->table_name}";
							$results = $wpdb->get_var( $sql );
							?>
							<tr>
								<td class="b"> <?php echo $results; ?> </td>
								<td class="t"><?php _e( 'Likes', 'buddypress-media' ) ?></td>
							</tr>

						</tbody>
					</table>
				</div>

			</div>

			<div class="rtm-meta-container">
				<ul class="rtm-meta-links">
					<li><b><?php _e( 'rtMedia Links:', 'buddypress-media' ); ?></b></li>
					<li><a href="https://rtcamp.com/rtmedia/"><?php _e( 'Homepage', 'buddypress-media' ); ?></a></li>
					<li><a href="admin.php?page=rtmedia-support#rtmedia-general"><?php _e( 'Free Support', 'buddypress-media' ); ?></a></li>
					<li><a href="https://rtcamp.com/products/category/rtmedia/"><?php _e( 'Premium Addons', 'buddypress-media' ); ?></a></li>
				</ul>
			</div>
			<?php
		}

		/**
		 * Create the function use in the action hook
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function add_dashboard_widgets() {
			wp_add_dashboard_widget( 'rtmedia_dashboard_widget', __( 'Right Now in rtMedia', 'buddypress-media' ), array( &$this, 'rtmedia_dashboard_widget_function' ) );
			global $wp_meta_boxes;

			// Get the regular dashboard widgets array
			// (which has our new widget already but at the end)

			$normal_dashboard = $wp_meta_boxes[ 'dashboard' ][ 'normal' ][ 'core' ];

			// Backup and delete our new dashboard widget from the end of the array

			$example_widget_backup = array( 'rtmedia_dashboard_widget' => $normal_dashboard[ 'rtmedia_dashboard_widget' ] );
			unset( $normal_dashboard[ 'rtmedia_dashboard_widget' ] );

			// Merge the two arrays together so our widget is at the beginning

			$sorted_dashboard = array_merge( $example_widget_backup, $normal_dashboard );

			// Save the sorted array back into the original metaboxes

			$wp_meta_boxes[ 'dashboard' ][ 'normal' ][ 'core' ] = $sorted_dashboard;
		}

		/**
		 * Add the plugin settings links
		 *
		 * @access public
		 *
		 * @param  array $links
		 *
		 * @return array $links
		 */
		public function plugin_add_settings_link( $links ) {
			$settings_link = '<a href="' . admin_url( 'admin.php?page=rtmedia-settings' ) . '">Settings</a>';
			array_push( $links, $settings_link );
			$settings_link = '<a href="' . admin_url( 'admin.php?page=rtmedia-support' ) . '">Support</a>';
			array_push( $links, $settings_link );

			return $links;
		}

		/**
		 * Add the reencode link
		 *
		 * @access public
		 *
		 * @param  array  $actions
		 * @param  object $post
		 *
		 * @return array $actions
		 */
		public function add_reencode_link( $actions, $post ) {

			$mime_type_array = explode( '/', $post->post_mime_type );
			if ( is_array( $mime_type_array ) && '' != $mime_type_array && 'video' == $mime_type_array[ 0 ] ) {
				$actions[ 'reencode' ] = '<a class="submitdelete" onclick="return rtmedia_regenerate_thumbs(' . $post->ID . ')" href="#">' . __( 'Regenerate Thumbnail', 'buddypress-media' ) . '</a>';
			}

			return $actions;
		}

		/**
		 * Do the bulk video/media handler.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function bulk_action_handler() {
			if ( 'bulk_video_regenerate_thumbnails' == $_REQUEST[ 'action' ] && '' != $_REQUEST[ 'media' ] ) {
				wp_safe_redirect( esc_url_raw( add_query_arg( array( 'media_ids' => urlencode( implode( ',', $_REQUEST[ 'media' ] ) ) ), admin_url( 'admin.php?page=rtmedia-regenerate' ) ) ) );
				exit;
			}
		}

		/**
		 * Add admin bar menu
		 *
		 * @access public
		 *
		 * @param  type $admin_bar
		 *
		 * @return void
		 */
		public function admin_bar_menu( $admin_bar ) {
			if ( ! current_user_can( 'manage_options' ) ) {
				return;
			}

			$admin_bar->add_menu( array(
				'id' => 'rtMedia',
				'title' => 'rtMedia',
				'href' => admin_url( 'admin.php?page=rtmedia-settings' ),
				'meta' => array(
					'title' => __( 'rtMedia', 'buddypress-media' ),
				),
					)
			);
			$admin_bar->add_menu( array(
				'id' => 'rt-media-dashborad',
				'parent' => 'rtMedia',
				'title' => __( 'Settings', 'buddypress-media' ),
				'href' => admin_url( 'admin.php?page=rtmedia-settings' ),
				'meta' => array(
					'title' => __( 'Settings', 'buddypress-media' ),
					'target' => '_self',
				),
					)
			);
			$admin_bar->add_menu( array(
				'id' => 'rt-media-addons',
				'parent' => 'rtMedia',
				'title' => __( 'Addons', 'buddypress-media' ),
				'href' => admin_url( 'admin.php?page=rtmedia-addons' ),
				'meta' => array(
					'title' => __( 'Addons', 'buddypress-media' ),
					'target' => '_self',
				),
					)
			);
			$admin_bar->add_menu( array(
				'id' => 'rt-media-support',
				'parent' => 'rtMedia',
				'title' => __( 'Support', 'buddypress-media' ),
				'href' => admin_url( 'admin.php?page=rtmedia-support' ),
				'meta' => array(
					'title' => __( 'Support', 'buddypress-media' ),
					'target' => '_self',
				),
					)
			);
			$admin_bar->add_menu( array(
				'id' => 'rt-media-themes',
				'parent' => 'rtMedia',
				'title' => __( 'Themes', 'buddypress-media' ),
				'href' => admin_url( 'admin.php?page=rtmedia-themes' ),
				'meta' => array(
					'title' => __( 'Themes', 'buddypress-media' ),
					'target' => '_self',
				),
					)
			);
			$admin_bar->add_menu( array(
				'id' => 'rt-media-hire-us',
				'parent' => 'rtMedia',
				'title' => __( 'Hire Us', 'buddypress-media' ),
				'href' => admin_url( 'admin.php?page=rtmedia-hire-us' ),
				'meta' => array(
					'title' => __( 'Hire Us', 'buddypress-media' ),
					'target' => '_self',
				),
					)
			);
			if ( has_filter( 'rtmedia_license_tabs' ) || has_action( 'rtmedia_addon_license_details' ) ) {
				$admin_bar->add_menu( array(
					'id' => 'rt-media-license',
					'parent' => 'rtMedia',
					'title' => __( 'Licenses', 'buddypress-media' ),
					'href' => admin_url( 'admin.php?page=rtmedia-license' ),
					'meta' => array(
						'title' => __( 'Licenses', 'buddypress-media' ),
						'target' => '_self',
					),
						)
				);
			}
		}

		/**
		 * Generates the Admin UI.
		 *
		 * @access public
		 *
		 * @param  string $hook
		 *
		 * @return void
		 */
		public function ui( $hook ) {
			$admin_pages = array(
				'rtmedia_page_rtmedia-migration',
				'rtmedia_page_rtmedia-kaltura-settings',
				'rtmedia_page_rtmedia-ffmpeg-settings',
				'toplevel_page_rtmedia-settings',
				'rtmedia_page_rtmedia-addons',
				'rtmedia_page_rtmedia-support',
				'rtmedia_page_rtmedia-themes',
				'rtmedia_page_rtmedia-hire-us',
				'rtmedia_page_rtmedia-importer',
				'rtmedia_page_rtmedia-regenerate',
			);

			if ( has_filter( 'rtmedia_license_tabs' ) || has_action( 'rtmedia_addon_license_details' ) ) {
				$admin_pages[] = 'rtmedia_page_rtmedia-license';
			}

			$admin_pages = apply_filters( 'rtmedia_filter_admin_pages_array', $admin_pages );
			$suffix = ( function_exists( 'rtm_get_script_style_suffix' ) ) ? rtm_get_script_style_suffix() : '.min';

			if ( in_array( $hook, $admin_pages ) || strpos( $hook, 'rtmedia-migration' ) ) {

				$admin_ajax = admin_url( 'admin-ajax.php' );

				/* Only one JS file should enqueue */
				if( $suffix === '' ) {
					wp_enqueue_script( 'rtmedia-admin-tabs', RTMEDIA_URL . 'app/assets/admin/js/vendors/tabs.js', array( 'backbone' ), RTMEDIA_VERSION );
					wp_enqueue_script( 'rtmedia-admin-scripts', RTMEDIA_URL . 'app/assets/admin/js/scripts.js', array( 'backbone' ), RTMEDIA_VERSION );
					wp_enqueue_script( 'rtmedia-admin', RTMEDIA_URL . 'app/assets/admin/js/settings.js', array( 'backbone' ), RTMEDIA_VERSION );
				} else {
					wp_enqueue_script( 'rtmedia-admin', RTMEDIA_URL . 'app/assets/admin/js/admin.min.js', array( 'backbone' ), RTMEDIA_VERSION );
				}

				wp_localize_script( 'rtmedia-admin', 'rtmedia_on_label', __( 'ON', 'buddypress-media' ) );
				wp_localize_script( 'rtmedia-admin', 'rtmedia_off_label', __( 'OFF', 'buddypress-media' ) );
				wp_localize_script( 'rtmedia-admin', 'rtmedia_admin_ajax', $admin_ajax );
				wp_localize_script( 'rtmedia-admin', 'rtmedia_admin_url', admin_url() );
				wp_localize_script( 'rtmedia-admin', 'rtmedia_admin_url', admin_url() );

				$rtmedia_admin_strings = array(
					'no_refresh' => __( 'Please do not refresh this page.', 'buddypress-media' ),
					'something_went_wrong' => __( 'Something went wrong. Please <a href onclick="location.reload();">refresh</a> page.', 'buddypress-media' ),
					'are_you_sure' => __( 'This will subscribe you to the free plan.', 'buddypress-media' ),
					'disable_encoding' => __( 'Are you sure you want to disable the encoding service?', 'buddypress-media' ),
					'enable_encoding' => __( 'Are you sure you want to enable the encoding service?', 'buddypress-media' ),
				);

				wp_localize_script( 'rtmedia-admin', 'rtmedia_admin_strings', $rtmedia_admin_strings );
				wp_localize_script( 'rtmedia-admin', 'settings_url', esc_url( add_query_arg( array( 'page' => 'rtmedia-settings' ), ( is_multisite() ? network_admin_url( 'admin.php' ) : admin_url( 'admin.php' ) ) ) ) . '#privacy_enabled' );
				wp_localize_script( 'rtmedia-admin', 'settings_rt_album_import_url', esc_url( add_query_arg( array( 'page' => 'rtmedia-settings' ), ( is_multisite() ? network_admin_url( 'admin.php' ) : admin_url( 'admin.php' ) ) ) ) );

				/* Only one CSS file should enqueue */
				wp_enqueue_style( 'rtmedia-admin', RTMEDIA_URL . 'app/assets/admin/css/admin' . $suffix . '.css', '', RTMEDIA_VERSION );
			} else {

				/* This CSS is using for "Right Now in rtMedia" Widget section on Dashboard */
				wp_enqueue_style( 'rtmedia-widget', RTMEDIA_URL . 'app/assets/admin/css/widget' . $suffix . '.css', '', RTMEDIA_VERSION );
			}
		}

		/**
		 * Add Admin Menu.
		 *
		 * @access public
		 * @global string 'buddypress-media'
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function menu() {
			add_menu_page( 'rtMedia', 'rtMedia', 'manage_options', 'rtmedia-settings', array( $this, 'settings_page' ), RTMEDIA_URL . 'app/assets/admin/img/rtmedia-logo.png', '40.1111' );
			add_submenu_page( 'rtmedia-settings', __( 'Settings', 'buddypress-media' ), __( 'Settings', 'buddypress-media' ), 'manage_options', 'rtmedia-settings', array( $this, 'settings_page' ) );
			add_submenu_page( 'rtmedia-settings', __( 'Addons', 'buddypress-media' ), __( 'Addons', 'buddypress-media' ), 'manage_options', 'rtmedia-addons', array( $this, 'addons_page' ) );
			add_submenu_page( 'rtmedia-settings', __( 'Support', 'buddypress-media' ), __( 'Support', 'buddypress-media' ), 'manage_options', 'rtmedia-support', array( $this, 'support_page' ) );
			add_submenu_page( 'rtmedia-settings', __( 'Themes', 'buddypress-media' ), __( 'Themes', 'buddypress-media' ), 'manage_options', 'rtmedia-themes', array( $this, 'theme_page' ) );
			add_submenu_page( 'rtmedia-settings', __( 'Hire Us', 'buddypress-media' ), __( 'Hire Us', 'buddypress-media' ), 'manage_options', 'rtmedia-hire-us', array( $this, 'hire_us_page' ) );
			if ( has_filter( 'rtmedia_license_tabs' ) || has_action( 'rtmedia_addon_license_details' ) ) {
				add_submenu_page( 'rtmedia-settings', __( 'Licenses', 'buddypress-media' ), __( 'Licenses', 'buddypress-media' ), 'manage_options', 'rtmedia-license', array( $this, 'license_page' ) );
			}

			$obj_encoding = new RTMediaEncoding( true );

			if ( $obj_encoding->api_key ) {
				add_submenu_page( 'rtmedia-settings', __( 'Regenerate Thumbnail', 'buddypress-media' ), __( 'Regen. Thumbnail ', 'buddypress-media' ), 'manage_options', 'rtmedia-regenerate', array( $this, 'rt_regenerate_thumbnail' ) );
			}
		}

		/**
		 * Define regenerate thumbnail functionality.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rt_regenerate_thumbnail() {
			$prog = new rtProgress();
			$done = 0;
			?>
			<div class="wrap">
				<h2> rtMedia: <?php _e( 'Regenerate Video Thumbnails', 'buddypress-media' ); ?> </h2>
				<?php
				if ( isset( $_REQUEST[ 'media_ids' ] ) && trim( $_REQUEST[ 'media_ids' ] ) != '' ) {
					$requested = false;
					$media_ids = explode( ',', $_REQUEST[ 'media_ids' ] );
					$total = count( $media_ids );
				} else {
					$media_ids = $this->get_video_without_thumbs();
					$total = count( $media_ids );
				}
				?>
				<script>
					var rt_thumb_all_media = <?php echo json_encode( $media_ids ); ?>;
				</script>
				<?php
				if ( ! isset( $requested ) ) {
					?>
					<br/>
					<p>You can see this page because you have <a href="<?php echo admin_url( 'admin.php?page=rtmedia-addons' ) ?>">subscribed</a> for <a href="https://rtcamp.com/rtmedia/docs/admin/addons/audio-video-encoding/" target="_blank">rtMedia audio/video encoding service</a>.</p> <p>You can regenerate thumbnails of a specific video by visiting <a href="<?php echo admin_url( 'upload.php?post_mime_type=video' ); ?>">media page</a> and clicking the <b>Regenerate Thumbnail</b> option for that particular video.</p> <p>Click <b>Regenerate Pending Thumbnails</b> to regenerate thumbnails of pending videos.</p> <p><input type="button" class="button button-primary" id="rt-start-media-regenerate" value="<?php echo __( 'Regenerate Pending Thumbnails', 'buddypress-media' ) . ' (' . $total . ')'; ?>"/> </p>
					<?php
				}
				?>
				<div id="rt-migration-progress">
					<br/> <br/>
					<?php
					$temp = $prog->progress( $done, $total );
					$prog->progress_ui( $temp, true );
					?>
					<p> <?php _e( 'Total Videos', 'buddypress-media' ) ?> : <span class='rt-total'><?php echo $total; ?></span>
					</p>

					<p> <?php _e( 'Sent of regenerate thumbails', 'buddypress-media' ) ?> : <span class='rt-done'>0</span></p>

					<p> <?php _e( 'Fail to regenerate thumbails', 'buddypress-media' ) ?> : <span class='rt-fail'>0</span></p>

				</div>
				<script>

					var db_done = 0;
					var db_fail = 0;
					var db_total = <?php echo $total; ?>;
					var indx = 0;
					function db_start_regenrate() {
						if ( indx < db_total ) {
							jQuery.ajax( {
								url: rtmedia_admin_ajax,
								type: 'post',
								data: {
									"action": "rt_media_regeneration",
									"media_id": rt_thumb_all_media[indx ++]
								},
								success: function ( data ) {
									data = JSON.parse( data );

									if ( data.status == false ) {
										handle_regenrate_fail();
									} else {
										db_done ++;
										var progw = Math.ceil( ( db_done / db_total ) * 100 );
										if ( progw > 100 ) {
											progw = 100;
										}
										jQuery( '#rtprogressbar>div' ).css( 'width', progw + '%' );
										jQuery( 'span.rt-done' ).html( db_done );
										db_start_regenrate();
									}
								},
								error: function () {
									handle_regenrate_fail();
								}
							} );
						} else {
							alert( "<?php _e( 'Regenerate Video Thumbnails Done', 'buddypress-media' ); ?>" );
						}
					}
					function handle_regenrate_fail() {
						db_fail ++;
						jQuery( 'span.rt-fail' ).html( db_fail );
						db_start_regenrate();
					}
					if ( jQuery( "#rt-start-media-regenerate" ).length > 0 ) {
						jQuery( "#rt-migration-progress" ).hide()
						jQuery( "#rt-start-media-regenerate" ).click( function () {
							jQuery( this ).hide();
							jQuery( "#rt-migration-progress" ).show()
							db_start_regenrate();
						} )
					} else {
						db_start_regenrate();
					}

				</script>


			</div> <?php
		}

		/**
		 * Generate rtmedia thumbnail notice.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rtmedia_regenerate_thumbnail_notice() {
			$obj_encoding = new RTMediaEncoding( true );
			if ( $obj_encoding->api_key ) {
				$site_option = rtmedia_get_site_option( 'rtmedia-video-thumb-notice' );
				if ( ! $site_option || 'hide' != $site_option ) {
					rtmedia_update_site_option( 'rtmedia-video-thumb-notice', 'show' );
					$videos_without_thumbs = get_video_without_thumbs();
					if ( isset( $videos_without_thumbs ) && is_array( $videos_without_thumbs ) && sizeof( $videos_without_thumbs ) > 0 ) {
						echo '<div class="error rtmedia-regenerate-video-thumb-error">
								<p>
								' . sprintf( __( "You have %s videos without thumbnails. Click <a href='%s'> here </a> to generate thumbnails. <a href='#' onclick='rtmedia_hide_video_thumb_notice()' style='float:right'>Hide</a>", 'buddypress-media' ), sizeof( $videos_without_thumbs ), admin_url( 'admin.php?page=rtmedia-regenerate' ) ) . '
								</p>
								</div>';
						?>
						<script type="text/javascript">
							function rtmedia_hide_video_thumb_notice() {
								var data = { action: 'rtmedia_hide_video_thumb_admin_notice' };
								jQuery.post( ajaxurl, data, function ( response ) {
									response = response.trim();
									if ( response === "1" )
										jQuery( '.rtmedia-regenerate-video-thumb-error' ).remove();
								} );
							}
						</script>
						<?php
					}
				}
			}
		}

		/**
		 * Hide rtmedia video thumb admin notice.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rtmedia_hide_video_thumb_admin_notice() {
			if ( rtmedia_update_site_option( 'rtmedia-video-thumb-notice', 'hide' ) ) {
				echo '1';
			} else {
				echo '0';
			}
			die();
		}

		/**
		 * Hide rtmedia addon update notice.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rtmedia_hide_addon_update_notice() {
			if ( rtmedia_update_site_option( 'rtmedia-addon-update-notice-3_8', 'hide' ) ) {
				echo '1';
			} else {
				echo '0';
			}
			die();
		}

		/**
		 * Define rt_media_regeneration.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rt_media_regeneration() {
			if ( isset( $_POST[ 'media_id' ] ) ) {
				$model = new RTMediaModel();
				$media = $model->get_media( array( 'media_id' => $_POST[ 'media_id' ] ), 0, 1 );
				$media_type = $media[ 0 ]->media_type;
				$response = array();
				if ( 'video' == $media_type ) {
					$objRTMediaEncoding = new RTMediaEncoding( true );
					$autoformat = 'thumbnails';
					$objRTMediaEncoding->reencoding( intval( $_POST[ 'media_id' ] ), $autoformat );
					$response[ 'status' ] = true;
				} else {
					$response[ 'status' ] = false;
					$response[ 'message' ] = __( 'not a video ...', 'buddypress-media' );
				}
				echo json_encode( $response );
				die();
			}
		}

		/**
		 * Get video without thumbs.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return object $results
		 */
		public function get_video_without_thumbs() {
			$rtmedia_model = new RTMediaModel();
			$sql = "select media_id from {$rtmedia_model->table_name} where media_type = 'video' and blog_id = '" . get_current_blog_id() . "' and cover_art is null";
			global $wpdb;
			$results = $wpdb->get_col( $sql );

			return $results;
		}

		/**
		 * Render the BuddyPress Media Settings page.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function settings_page() {
			$this->render_page( 'rtmedia-settings', 'buddypress-media' );
		}

		/**
		 * Render the BuddyPress Privacy Settings page.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function privacy_page() {
			$this->render_page( 'rtmedia-privacy' );
		}

		/**
		 * Render the rtmedia Importer Page.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rt_importer_page() {
			$this->render_page( 'rtmedia-importer' );
		}

		/**
		 * Render the rtmedia convert videos page.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function convert_videos_page() {
			$this->render_page( 'rtmedia-convert-videos' );
		}

		/**
		 * Render the BuddyPress Media Addons page.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function addons_page() {
			$this->render_page( 'rtmedia-addons' );
		}

		/**
		 * Render the BuddyPress Media Support page.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function support_page() {
			$this->render_page( 'rtmedia-support' );
		}

		/**
		 * Render the rtmedia theme page.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function theme_page() {
			$this->render_page( 'rtmedia-themes' );
		}

		/**
		 * Render the rtmedia hire us page.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function hire_us_page() {
			$this->render_page( 'rtmedia-hire-us' );
		}

		public function license_page() {
			$this->render_page( 'rtmedia-license' );
		}

		/**
		 * Render the rtmedia hire us page.
		 *
		 * @access static
		 *
		 * @param  void
		 *
		 * @return type
		 */
		static function get_current_tab() {
			return isset( $_GET[ 'page' ] ) ? $_GET[ 'page' ] : 'rtmedia-settings';
		}

		/**
		 * Render BPMedia Settings.
		 *
		 * @access public
		 * @global      string 'buddypress-media'
		 *
		 * @param  type $page
		 * @param  type $option_group
		 *
		 * @return void
		 */
		public function render_page( $page, $option_group = null ) {
			?>

			<div class="wrap bp-media-admin <?php echo $this->get_current_tab(); ?>">
				<div id="icon-buddypress-media" class="icon32"><br></div>
				<div>
					<h2 class="nav-tab-wrapper"><?php $this->rtmedia_tabs(); ?>
						<span class="alignright by">
							<a class="rt-link" href="http://rtcamp.com/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media" target="_blank" title="rtCamp : <?php _e( 'Empowering The Web With WordPress', 'buddypress-media' ); ?>">
								<img src="<?php echo RTMEDIA_URL; ?>app/assets/admin/img/rtcamp-logo.png" alt="rtCamp" />
							</a>
						</span>
					</h2>
				</div>

				<div class="clearfix rtm-row-container">

					<div id="bp-media-settings-boxes" class="bp-media-settings-boxes-container rtm-setting-container">

						<?php
						$settings_url = ( is_multisite() ) ? network_admin_url( 'edit.php?action=' . $option_group ) : 'options.php';
						if ( $page == 'rtmedia-settings' ) {
							?>
							<form id="bp_media_settings_form" name="bp_media_settings_form" method="post"
								  enctype="multipart/form-data">
								<div class="bp-media-metabox-holder">
									<div class="rtm-button-container top">
										<?php if ( isset( $_GET[ 'settings-saved' ] ) && $_GET[ 'settings-saved' ] ) { ?>
											<div class="rtm-success rtm-fly-warning rtm-save-settings-msg"><?php _e( 'Settings saved successfully!', 'buddypress-media' ); ?></div>
										<?php } ?>
										<input type="hidden" name="rtmedia-options-save" value="true">
										<input type="submit" class="rtmedia-settings-submit button button-primary button-big" value="<?php _e( 'Save Settings', 'buddypress-media' ); ?>">
									</div>
									<?php
									settings_fields( $option_group );
									if ( 'rtmedia-settings' == $page ) {
										echo '<div id="rtm-settings-tabs">';
										$sub_tabs = $this->settings_sub_tabs();
										RTMediaFormHandler::rtForm_settings_tabs_content( $page, $sub_tabs );
										echo '</div>';
									} else {
										do_settings_sections( $page );
									}
									?>

									<div class="rtm-button-container bottom">
										<div class="rtm-social-links alignleft">
											<a href="http://twitter.com/rtcamp" class="twitter" target= "_blank"><span class="dashicons dashicons-twitter"></span></a>
											<a href="https://www.facebook.com/rtCamp.solutions" class="facebook" target="_blank"><span class="dashicons dashicons-facebook"></span></a>
											<a href="http://profiles.wordpress.org/rtcamp" class="wordpress" target= "_blank"><span class="dashicons dashicons-wordpress"></span></a>
											<a href="https://rtcamp.com/feed" class="rss" target="_blank"><span class="dashicons dashicons-rss"></span></a>
										</div>

										<input type="hidden" name="rtmedia-options-save" value="true">
										<input type="submit" class="rtmedia-settings-submit button button-primary button-big" value="<?php _e( 'Save Settings', 'buddypress-media' ); ?>">
									</div>
								</div>
							</form><?php
						} else {
							?>
							<div class="bp-media-metabox-holder">
								<?php
								if ( 'rtmedia-addons' == $page ) {
									RTMediaAddon::render_addons( $page );
								} else if ( 'rtmedia-support' == $page ) {
									$rtmedia_support = new RTMediaSupport( false );
									$rtmedia_support->render_support( $page );
								} else if ( 'rtmedia-themes' == $page ) {
									RTMediaThemes::render_themes( $page );
								} else {
									if ( 'rtmedia-license' == $page ) {
										RTMediaLicense::render_license( $page );
									} else {
										do_settings_sections( $page );
									}
								}
								do_action( 'rtmedia_admin_page_insert', $page );
								?>
							</div>
							<?php
							do_action( 'rtmedia_admin_page_append', $page );
						}
						?>
					</div>

					<div class="metabox-holder bp-media-metabox-holder rtm-sidebar">
						<?php $this->admin_sidebar(); ?>
					</div>

				</div>

			</div><!-- .bp-media-admin --><?php
		}

		/**
		 * Adds a tab for Media settings in the BuddyPress settings page
		 *
		 * @access public
		 * @global type $bp_media
		 *
		 * @param       void
		 * @param       void
		 *
		 * @return type $tabs_html
		 */
		public function tab() {

			$tabs_html = '';
			$idle_class = 'nav-tab';
			$active_class = 'nav-tab nav-tab-active';
			$tabs = array();

			// Check to see which tab we are on
			$tab = $this->get_current_tab();
			/* rtMedia */
			$tabs[] = array(
				'href' => get_admin_url( null, esc_url( add_query_arg( array( 'page' => 'rtmedia-settings' ), 'admin.php' ) ) ),
				'title' => __( 'rtMedia', 'buddypress-media' ),
				'name' => __( 'rtMedia', 'buddypress-media' ),
				'class' => ( $tab == 'rtmedia-settings' || $tab == 'rtmedia-addons' || $tab == 'rtmedia-support' || $tab == 'rtmedia-importer' ) ? $active_class : $idle_class,
			);

			foreach ( $tabs as $tab ) {
				$tabs_html .= '<a id="bp-media" title= "' . $tab[ 'title' ] . '"  href="' . $tab[ 'href' ] . '" class="' . $tab[ 'class' ] . '">' . $tab[ 'name' ] . '</a>';
			}
			echo $tabs_html;
		}

		/**
		 * Create core admin tabs.
		 *
		 * @access public
		 *
		 * @param  type $active_tab
		 *
		 * @return void
		 */
		public function rtmedia_tabs( $active_tab = '' ) {
			// Declare local variables
			$tabs_html = '';
			$idle_class = 'nav-tab';
			$active_class = 'nav-tab nav-tab-active';

			// Setup core admin tabs
			$tabs = array(
				array(
					'href' => get_admin_url( null, esc_url( add_query_arg( array( 'page' => 'rtmedia-settings' ), 'admin.php' ) ) ),
					'name' => __( 'Settings', 'buddypress-media' ),
					'slug' => 'rtmedia-settings',
				), array(
					'href' => get_admin_url( null, esc_url( add_query_arg( array( 'page' => 'rtmedia-addons' ), 'admin.php' ) ) ),
					'name' => __( 'Addons', 'buddypress-media' ),
					'slug' => 'rtmedia-addons',
				), array(
					'href' => get_admin_url( null, esc_url( add_query_arg( array( 'page' => 'rtmedia-themes' ), 'admin.php' ) ) ),
					'name' => __( 'Themes', 'buddypress-media' ),
					'slug' => 'rtmedia-themes',
				), array(
					'href' => get_admin_url( null, esc_url( add_query_arg( array( 'page' => 'rtmedia-hire-us' ), 'admin.php' ) ) ),
					'name' => __( 'Hire Us', 'buddypress-media' ),
					'slug' => 'rtmedia-hire-us',
				), array(
					'href' => get_admin_url( null, esc_url( add_query_arg( array( 'page' => 'rtmedia-support' ), 'admin.php' ) ) ),
					'name' => __( 'Support', 'buddypress-media' ),
					'slug' => 'rtmedia-support',
				),
			);

			if ( has_filter( 'rtmedia_license_tabs' ) || has_action( 'rtmedia_addon_license_details' ) ) {
				$tabs[] = array(
					'href' => get_admin_url( null, esc_url( add_query_arg( array( 'page' => 'rtmedia-license' ), 'admin.php' ) ) ),
					'name' => __( 'Licenses', 'buddypress-media' ),
					'slug' => 'rtmedia-license',
				);
			}

			$tabs = apply_filters( 'media_add_tabs', $tabs );

			// Loop through tabs and build navigation
			foreach ( array_values( $tabs ) as $tab_data ) {
				$is_current = ( bool ) ( $tab_data[ 'slug' ] == $this->get_current_tab() );
				$tab_class = $is_current ? $active_class : $idle_class;

				if ( isset( $tab_data[ 'class' ] ) && is_array( $tab_data[ 'class' ] ) ) {
					$tab_class .= ' ' . implode( ' ', $tab_data[ 'class' ] );
				}

				$tabs_html .= '<a href="' . $tab_data[ 'href' ] . '" class="' . $tab_class . '">' . $tab_data[ 'name' ] . '</a>';
			}

			// Output the tabs
			echo $tabs_html;

			//            // Do other fun things
			//            do_action('bp_media_admin_tabs');
		}

		/**
		 * Create settings content tabs.
		 *
		 * @access public
		 *
		 * @param  type $page
		 *
		 * @return void
		 */
		public function settings_content_tabs( $page ) {
			global $wp_settings_sections, $wp_settings_fields;

			if ( ! isset( $wp_settings_sections ) || ! isset( $wp_settings_sections[ $page ] ) ) {
				return;
			}

			foreach ( ( array ) $wp_settings_sections[ $page ] as $section ) {
				if ( $section[ 'title' ] ) {
					echo "<h3>{$section[ 'title' ]}</h3>\n";
				}

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
		 * Adds a sub tabs to the BuddyPress Media settings page
		 *
		 * @access public
		 * @global type $bp_media
		 *
		 * @param       void
		 *
		 * @return array $tabs
		 */
		public function settings_sub_tabs() {
			$tabs_html = '';
			$tabs = array();

			// Check to see which tab we are on
			$tab = $this->get_current_tab();
			/* rtMedia */

			$tabs[ 7 ] = array(
				'href' => '#rtmedia-display',
				'icon' => 'dashicons-desktop',
				'title' => __( 'Display', 'buddypress-media' ),
				'name' => __( 'Display', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'display_content' )
			);

			if ( class_exists( 'BuddyPress' ) ) {
				$tabs[ 20 ] = array(
					'href' => '#rtmedia-bp',
					'icon' => 'dashicons-groups',
					'title' => __( 'rtMedia BuddyPress', 'buddypress-media' ),
					'name' => __( 'BuddyPress', 'buddypress-media' ),
					'callback' => array( 'RTMediaFormHandler', 'buddypress_content' ) //change it to BuddyPress Content
				);
			}

			$tabs[ 30 ] = array(
				'href' => '#rtmedia-types',
				'icon' => 'dashicons-editor-video',
				'title' => __( 'rtMedia Types', 'buddypress-media' ),
				'name' => __( 'Types', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'types_content' )
			);

			$tabs[ 40 ] = array(
				'href' => '#rtmedia-sizes',
				'icon' => 'dashicons-editor-expand',
				'title' => __( 'rtMedia Sizes', 'buddypress-media' ),
				'name' => __( 'Media Sizes', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'sizes_content' )
			);

			$tabs[ 50 ] = array(
				'href' => '#rtmedia-privacy',
				'icon' => 'dashicons-lock',
				'title' => __( 'rtMedia Privacy', 'buddypress-media' ),
				'name' => __( 'Privacy', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'privacy_content' )
			);
			$tabs[ 60 ] = array(
				'href' => '#rtmedia-custom-css-settings',
				'icon' => 'dashicons-clipboard',
				'title' => __( 'rtMedia Custom CSS', 'buddypress-media' ),
				'name' => __( 'Custom CSS', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'custom_css_content' )
			);

			$tabs = apply_filters( 'rtmedia_add_settings_sub_tabs', $tabs, $tab );

			$tabs[] = array(
				'href' => '#rtmedia-general',
				'icon' => 'dashicons-admin-tools',
				'title' => __( 'Other Settings', 'buddypress-media' ),
				'name' => __( 'Other Settings', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'general_content' )
			);

			return $tabs;
		}

		/**
		 * Updates the media count of all users.
		 *
		 * @access public
		 * @global type $wpdb
		 *
		 * @param       void
		 *
		 * @return boolean
		 */
		public function update_count() {
			global $wpdb;

			$query = "SELECT
		        p.post_author,pmp.meta_value,
		        SUM(CASE WHEN post_mime_type LIKE 'image%' THEN 1 ELSE 0 END) as Images,
		        SUM(CASE WHEN post_mime_type LIKE 'music%' THEN 1 ELSE 0 END) as Music,
		        SUM(CASE WHEN post_mime_type LIKE 'video%' THEN 1 ELSE 0 END) as Videos,
		        SUM(CASE WHEN post_type LIKE 'bp_media_album' THEN 1 ELSE 0 END) as Albums
		    FROM
		        $wpdb->posts p inner join $wpdb->postmeta  pm on pm.post_id = p.id INNER JOIN $wpdb->postmeta pmp
		    on pmp.post_id = p.id  WHERE
		        pm.meta_key = 'bp-media-key' AND
		        pm.meta_value > 0 AND
		        pmp.meta_key = 'bp_media_privacy' AND
		        ( post_mime_type LIKE 'image%' OR post_mime_type LIKE 'music%' OR post_mime_type LIKE 'video%' OR post_type LIKE 'bp_media_album')
		    GROUP BY p.post_author,pmp.meta_value order by p.post_author";

			$result = $wpdb->get_results( $query );

			if ( ! is_array( $result ) ) {
				return false;
			}

			$formatted = array();

			foreach ( $result as $obj ) {
				$formatted[ $obj->post_author ][ $obj->meta_value ] = array(
					'image' => $obj->Images, 'video' => $obj->Videos, 'music' => $obj->Music, 'album' => $obj->Albums,
				);
			}

			foreach ( $formatted as $user => $obj ) {
				update_user_meta( $user, 'rtmedia_count', $obj );
			}

			return true;
		}

		/**
		 * Multisite Save Options - http://wordpress.stackexchange.com/questions/64968/settings-api-in-multisite-missing-update-message#answer-72503
		 *
		 * @access public
		 * @global type $rtmedia_admin
		 *
		 * @param       void
		 *
		 * @return void
		 */
		public function save_multisite_options() {
			global $rtmedia_admin;
			if ( isset( $_POST[ 'refresh-count' ] ) ) {
				$rtmedia_admin->update_count();
			}
			do_action( 'rtmedia_sanitize_settings', $_POST );

			if ( isset( $_POST[ 'rtmedia_options' ] ) ) {
				rtmedia_update_site_option( 'rtmedia_options', $_POST[ 'rtmedia_options' ] );
				//
				// redirect to settings page in network
				wp_redirect( esc_url_raw( add_query_arg( array( 'page' => 'rtmedia-settings', 'updated' => 'true' ), ( is_multisite() ? network_admin_url( 'admin.php' ) : admin_url( 'admin.php' ) ) ) ) );
				exit;
			}
		}

		/**
		 * Admin Sidebar
		 *
		 * @access public
		 * @global type $bp_media
		 *
		 * @param       void
		 *
		 * @return void
		 */
		public function admin_sidebar() {
			do_action( 'rtmedia_before_default_admin_widgets' );
			$current_user = wp_get_current_user();
			$message = sprintf( __( 'I use @rtMediaWP http://rt.cx/rtmedia on %s', 'buddypress-media' ), home_url() );
			$addons = '<div id="social" class="rtm-social-share">
											<p><a href="http://twitter.com/home/?status=' . $message . '" class="button twitter" target= "_blank" title="' . __( 'Post to Twitter Now', 'buddypress-media' ) . '">' . __( 'Post to Twitter', 'buddypress-media' ) . '<span class="dashicons dashicons-twitter"></span></a></p>
											<p><a href="https://www.facebook.com/sharer/sharer.php?u=http://rtcamp.com/rtmedia/" class="button facebook" target="_blank" title="' . __( 'Share on Facebook Now', 'buddypress-media' ) . '">' . __( 'Share on Facebook', 'buddypress-media' ) . '<span class="dashicons dashicons-facebook"></span></a></p>
											<p><a href="http://wordpress.org/support/view/plugin-reviews/buddypress-media?rate=5#postform" class="button wordpress" target= "_blank" title="' . __( 'Rate rtMedia on Wordpress.org', 'buddypress-media' ) . '">' . __( 'Rate on Wordpress.org', 'buddypress-media' ) . '<span class="dashicons dashicons-wordpress"></span></a></p>
											<p><a href="' . sprintf( '%s', 'https://rtcamp.com/feed/' ) . '" class="button rss" target="_blank" title="' . __( 'Subscribe to our Feeds', 'buddypress-media' ) . '">' . __( 'Subscribe to our Feeds', 'buddypress-media' ) . '<span class="dashicons dashicons-rss"></span></a></p>
								</div>';

			new RTMediaAdminWidget( 'spread-the-word', __( 'Spread the Word', 'buddypress-media' ), $addons );

			$branding = '<form action="http://rtcamp.us1.list-manage1.com/subscribe/post?u=85b65c9c71e2ba3fab8cb1950&amp;id=9e8ded4470" method="post" id="mc-embedded-subscribe-form" name="mc-embedded-subscribe-form" class="validate" target="_blank" novalidate>
                            <div class="mc-field-group">
                                    <input type="email" value="' . $current_user->user_email . '" name="EMAIL" placeholder="Email" class="required email" id="mce-EMAIL">
                                    <input style="display:none;" type="checkbox" checked="checked" value="1" name="group[1721][1]" id="mce-group[1721]-1721-0">
									<input type="submit" value="' . __( 'Subscribe', 'buddypress-media' ) . '" name="subscribe" id="mc-embedded-subscribe" class="button">
                                    <div id="mce-responses" class="clear">
                                        <div class="response" id="mce-error-response" style="display:none"></div>
                                        <div class="response" id="mce-success-response" style="display:none"></div>
                                    </div>
                            </div>
                        </form>';
			new RTMediaAdminWidget( 'branding', __( 'Subscribe', 'buddypress-media' ), $branding );

			$news = '<img src ="' . admin_url( '/images/wpspin_light.gif' ) . '" /> Loading...';
			//new RTMediaAdminWidget ( 'latest-news', __( 'Latest News', 'buddypress-media' ), $news );
			do_action( 'rtmedia_after_default_admin_widgets' );
		}

		public function linkback() {
			if ( isset( $_POST[ 'linkback' ] ) && $_POST[ 'linkback' ] ) {
				return rtmedia_update_site_option( 'rtmedia-add-linkback', true );
			} else {
				return rtmedia_update_site_option( 'rtmedia-add-linkback', false );
			}
			die;
		}

		public function convert_videos_mailchimp_send() {
			if ( 'Yes' == $_POST[ 'interested' ] && ! empty( $_POST[ 'choice' ] ) ) {
				wp_remote_get( esc_url_raw( add_query_arg( array( 'rtmedia-convert-videos-form' => 1, 'choice' => $_POST[ 'choice' ], 'url' => urlencode( $_POST[ 'url' ] ), 'email' => $_POST[ 'email' ] ), 'http://rtcamp.com/' ) ) );
			} else {
				rtmedia_update_site_option( 'rtmedia-survey', 0 );
			}
			_e( 'Thank you for your time.', 'buddypress-media' );
			die;
		}

		public function video_transcoding_survey_response() {
			if ( isset( $_GET[ 'survey-done' ] ) && ( $_GET[ 'survey-done' ] == md5( 'survey-done' ) ) ) {
				rtmedia_update_site_option( 'rtmedia-survey', 0 );
			}
		}

		public function plugin_meta_premium_addon_link( $plugin_meta, $plugin_file, $plugin_data, $status ) {
			if ( plugin_basename( RTMEDIA_PATH . 'index.php' ) == $plugin_file ) {
				$plugin_meta[] = '<a href="https://rtcamp.com/rtmedia/addons/?utm_source=dashboard&#038;utm_medium=plugin&#038;utm_campaign=buddypress-media" title="' . __( 'Premium Add-ons', 'buddypress-media' ) . '">' . __( 'Premium Add-ons', 'buddypress-media' ) . '</a>';
			}

			return $plugin_meta;
		}

		public function upload_filetypes_error() {
			global $rtmedia;
			$upload_filetypes = rtmedia_get_site_option( 'upload_filetypes', 'jpg jpeg png gif' );
			$upload_filetypes = explode( ' ', $upload_filetypes );
			$flag = false;
			if ( isset( $rtmedia->options[ 'images_enabled' ] ) && $rtmedia->options[ 'images_enabled' ] ) {
				$not_supported_image = array_diff( array( 'jpg', 'jpeg', 'png', 'gif' ), $upload_filetypes );
				if ( ! empty( $not_supported_image ) ) {
					echo '<div class="error upload-filetype-network-settings-error">
                        <p>
                        ' . sprintf( __( 'You have images enabled on rtMedia but your network allowed filetypes do not permit uploading of %s. Click <a href="%s">here</a> to change your settings manually.', 'buddypress-media' ), implode( ', ', $not_supported_image ), network_admin_url( 'settings.php#upload_filetypes' ) ) . '
                            <br /><strong>' . __( 'Recommended', 'buddypress-media' ) . ':</strong> <input type="button" class="button update-network-settings-upload-filetypes" class="button" value="' . __( 'Update Network Settings Automatically', 'buddypress-media' ) . '"> <img style="display:none;" src="' . admin_url( 'images/wpspin_light.gif' ) . '" />
                        </p>
                        </div>';
					$flag = true;
				}
			}
			if ( isset( $rtmedia->options[ 'videos_enabled' ] ) && $rtmedia->options[ 'videos_enabled' ] ) {
				if ( ! in_array( 'mp4', $upload_filetypes ) ) {
					echo '<div class="error upload-filetype-network-settings-error">
                        <p>
                        ' . sprintf( __( 'You have video enabled on BuddyPress Media but your network allowed filetypes do not permit uploading of mp4. Click <a href="%s">here</a> to change your settings manually.', 'buddypress-media' ), network_admin_url( 'settings.php#upload_filetypes' ) ) . '
                            <br /><strong>' . __( 'Recommended', 'buddypress-media' ) . ':</strong> <input type="button" class="button update-network-settings-upload-filetypes" class="button" value="' . __( 'Update Network Settings Automatically', 'buddypress-media' ) . '"> <img style="display:none;" src="' . admin_url( 'images/wpspin_light.gif' ) . '" />
                        </p>
                        </div>';
					$flag = true;
				}
			}
			if ( isset( $rtmedia->options[ 'audio_enabled' ] ) && $rtmedia->options[ 'audio_enabled' ] ) {
				if ( ! in_array( 'mp3', $upload_filetypes ) ) {
					echo '<div class="error upload-filetype-network-settings-error"><p>' . sprintf( __( 'You have audio enabled on BuddyPress Media but your network allowed filetypes do not permit uploading of mp3. Click <a href="%s">here</a> to change your settings manually.', 'buddypress-media' ), network_admin_url( 'settings.php#upload_filetypes' ) ) . '
                            <br /><strong>' . __( 'Recommended', 'buddypress-media' ) . ':</strong> <input type="button" class="button update-network-settings-upload-filetypes" class="button" value="' . __( 'Update Network Settings Automatically', 'buddypress-media' ) . '"> <img style="display:none;" src="' . admin_url( 'images/wpspin_light.gif' ) . '" />
                        </p>
                        </div>';
					$flag = true;
				}
			}
			if ( $flag ) {
				?>
				<script type="text/javascript">
					jQuery( '.upload-filetype-network-settings-error' ).on( 'click', '.update-network-settings-upload-filetypes', function () {
						jQuery( '.update-network-settings-upload-filetypes' ).siblings( 'img' ).show();
						jQuery( '.update-network-settings-upload-filetypes' ).prop( 'disabled', true );
						jQuery.post( ajaxurl, { action: 'rtmedia_correct_upload_filetypes' }, function ( response ) {
							if ( response ) {
								jQuery( '.upload-filetype-network-settings-error:first' ).after( '<div style="display: none;" class="updated rtmedia-network-settings-updated-successfully"><p><?php _e( 'Network settings updated successfully.', 'buddypress-media' ); ?></p></div>' )
								jQuery( '.upload-filetype-network-settings-error' ).remove();
								jQuery( '.bp-media-network-settings-updated-successfully' ).show();
							}
						} );
					} );
				</script>
				<?php
			}
		}

		public function correct_upload_filetypes() {
			global $rtmedia;
			$upload_filetypes_orig = $upload_filetypes = rtmedia_get_site_option( 'upload_filetypes', 'jpg jpeg png gif' );
			$upload_filetypes = explode( ' ', $upload_filetypes );
			if ( isset( $rtmedia->options[ 'images_enabled' ] ) && $rtmedia->options[ 'images_enabled' ] ) {
				$not_supported_image = array_diff( array( 'jpg', 'jpeg', 'png', 'gif' ), $upload_filetypes );
				if ( ! empty( $not_supported_image ) ) {
					$update_image_support = null;
					foreach ( $not_supported_image as $ns ) {
						$update_image_support .= ' ' . $ns;
					}
					if ( $update_image_support ) {
						$upload_filetypes_orig .= $update_image_support;
						rtmedia_update_site_option( 'upload_filetypes', $upload_filetypes_orig );
					}
				}
			}
			if ( isset( $rtmedia->options[ 'videos_enabled' ] ) && $rtmedia->options[ 'videos_enabled' ] ) {
				if ( ! in_array( 'mp4', $upload_filetypes ) ) {
					$upload_filetypes_orig .= ' mp4';
					rtmedia_update_site_option( 'upload_filetypes', $upload_filetypes_orig );
				}
			}
			if ( isset( $rtmedia->options[ 'audio_enabled' ] ) && $rtmedia->options[ 'audio_enabled' ] ) {
				if ( ! in_array( 'mp3', $upload_filetypes ) ) {
					$upload_filetypes_orig .= ' mp3';
					rtmedia_update_site_option( 'upload_filetypes', $upload_filetypes_orig );
				}
			}
			echo true;
			die();
		}

		function edit_video_thumbnail( $form_fields, $post ) {
			if ( isset( $post->post_mime_type ) ) {
				$media_type = explode( '/', $post->post_mime_type );
				if ( is_array( $media_type ) && 'video' == $media_type[ 0 ] ) {
					$media_id = $post->ID;
					$thumbnail_array = get_post_meta( $media_id, 'rtmedia_media_thumbnails', true );
					$rtmedia_model = new RTMediaModel();
					$rtmedia_media = $rtmedia_model->get( array( 'media_id' => $media_id ) );
					$video_thumb_html = '';
					if ( is_array( $thumbnail_array ) ) {
						$video_thumb_html .= '<ul> ';

						foreach ( $thumbnail_array as $key => $thumbnail_src ) {
							$checked = checked( $thumbnail_src, $rtmedia_media[ 0 ]->cover_art, false );
							$count = $key + 1;
							$video_thumb_html .= '<li style="width: 150px;display: inline-block;">
                                    <label for="rtmedia-upload-select-thumbnail-' . $count . '">
                                    <input type="radio" ' . $checked . ' id="rtmedia-upload-select-thumbnail-' . $count . '" value="' . $thumbnail_src . '" name="rtmedia-thumbnail" />
                                    <img src=" ' . $thumbnail_src . '" style="max-height: 120px;max-width: 120px; vertical-align: middle;" />
                                    </label>
                                </li> ';
						}

						$video_thumb_html .= '  </ul>';
						$form_fields[ 'rtmedia_video_thumbnail' ] = array(
							'label' => 'Video Thumbnails', 'input' => 'html', 'html' => $video_thumb_html,
						);
					}
				}
			}

			return $form_fields;
		}

		function save_video_thumbnail( $post, $attachment ) {
			if ( isset( $post[ 'rtmedia-thumbnail' ] ) ) {
				$rtmedia_model = new RTMediaModel();
				$model = new RTMediaModel();
				$media = $model->get( array( 'media_id' => $post[ 'ID' ] ) );
				$media_id = $media[ 0 ]->id;
				$rtmedia_model->update( array( 'cover_art' => $post[ 'rtmedia-thumbnail' ] ), array( 'media_id' => $post[ 'ID' ] ) );
				update_activity_after_thumb_set( $media_id );
			}

			return $post;
		}

		function rtmedia_regenerate_thumb_js() {
			global $pagenow;

			if ( 'upload.php' == $pagenow ) {
				?>
				<script type="text/javascript">
					function rtmedia_regenerate_thumbs( post_id ) {
						if ( post_id != "" ) {
							var data = {
								action: 'rt_media_regeneration',
								media_id: post_id
							};
							jQuery.post( ajaxurl, data, function ( data ) {
								data = JSON.parse( data );
								if ( data.status === true ) {
									alert( "<?php _e( 'Video is sent to generate thumbnails.', 'buddypress-media' ) ?>" );
								} else {
									alert( "<?php _e( 'Video cannot be sent to generate thumbnails.', 'buddypress-media' ) ?>" );
								}
							} );
						}
					}
				</script>
				<?php
			}
		}

		function add_bulk_actions_regenerate() {
			?>
			<script type="text/javascript">
				jQuery( document ).ready( function ( $ ) {
					$( 'select[name^="action"] option:last-child' ).before( '<option value="bulk_video_regenerate_thumbnails"><?php esc_attr_e( 'Regenerate Video Thumbnails', 'buddypress-media' ); ?></option>' );
				} );
			</script>
			<?php
		}

		function presstrends_plugin() {
			global $rtmedia;
			$option = $rtmedia->options;
			if ( ! isset( $option[ 'general_AllowUserData' ] ) ) {
				return;
			}
			if ( '0' == $option[ 'general_AllowUserData' ] ) {
				return;
			}
			// PressTrends Account API Key
			$api_key = 'djbzu1no2tdz4qq4u2fpgaemuup2zzmtjulb';
			$auth = 'o3w063qppl7ha022jyc3bjpi7usrmczho';
			// Start of Metrics
			global $wpdb;
			$data = get_transient( 'presstrends_cache_data' );

			if ( ! $data || $data == '' ) {
				$api_base = 'http://api.presstrends.io/index.php/api/pluginsites/update?auth=';
				$url = $api_base . $auth . '&api=' . $api_key . '';
				$count_posts = wp_count_posts();
				$count_pages = wp_count_posts( 'page' );
				$comments_count = wp_count_comments();

				if ( function_exists( 'wp_get_theme' ) ) {
					$theme_data = wp_get_theme();
					$theme_name = urlencode( $theme_data->Name );
				} else {
					$theme_data = get_theme_data( get_stylesheet_directory() . '/style.css' );
					$theme_name = $theme_data[ 'Name' ];
				}

				$plugin_name = '&';

				foreach ( get_plugins() as $plugin_info ) {
					$plugin_name .= $plugin_info[ 'Name' ] . '&';
				}

				// CHANGE __FILE__ PATH IF LOCATED OUTSIDE MAIN PLUGIN FILE
				$plugin_data = get_plugin_data( __FILE__ );
				$posts_with_comments = $wpdb->get_var( "SELECT COUNT(*) FROM $wpdb->posts WHERE post_type='post' AND comment_count > 0" );
				$data = array(
					'url' => base64_encode( site_url() ), 'posts' => $count_posts->publish, 'pages' => $count_pages->publish, 'comments' => $comments_count->total_comments, 'approved' => $comments_count->approved, 'spam' => $comments_count->spam, 'pingbacks' => $wpdb->get_var( "SELECT COUNT(comment_ID) FROM $wpdb->comments WHERE comment_type = 'pingback'" ), 'post_conversion' => ( $count_posts->publish > 0 && $posts_with_comments > 0 ) ? number_format( ( $posts_with_comments / $count_posts->publish ) * 100, 0, '.', '' ) : 0, 'theme_version' => $plugin_data[ 'Version' ], 'theme_name' => $theme_name, 'site_name' => str_replace( ' ', '', get_bloginfo( 'name' ) ), 'plugins' => count( get_option( 'active_plugins' ) ), 'plugin' => urlencode( $plugin_name ), 'wpversion' => get_bloginfo( 'version' ),
				);

				foreach ( $data as $k => $v ) {
					$url .= '&' . $k . '=' . $v . '';
				}

				wp_remote_get( $url );
				set_transient( 'presstrends_cache_data', $data, 60 * 60 * 24 );
			}
		}

		function rtmedia_update_template_notice() {
			$site_option = rtmedia_get_site_option( 'rtmedia-update-template-notice-v3_9_4' );

			if ( ! $site_option || 'hide' != $site_option ) {
				rtmedia_update_site_option( 'rtmedia-update-template-notice-v3_9_4', 'show' );
				if ( is_dir( get_template_directory() . '/rtmedia' ) ) {
					echo '<div class="error rtmedia-update-template-notice"><p>' . __( 'Please update rtMedia template files if you have overridden the default rtMedia templates in your theme. If not, you can ignore and hide this notice.', 'buddypress-media' ) . '<a href="#" onclick="rtmedia_hide_template_override_notice()" style="float:right">' . __( 'Hide', 'buddypress-media' ) . '</a>' . ' </p></div>';
					?>
					<script type="text/javascript">
						function rtmedia_hide_template_override_notice() {
							var data = { action: 'rtmedia_hide_template_override_notice' };
							jQuery.post( ajaxurl, data, function ( response ) {
								response = response.trim();
								if ( '1' === response )
									jQuery( '.rtmedia-update-template-notice' ).remove();
							} );
						}
					</script>
					<?php
				}
			}
		}

		function rtmedia_hide_template_override_notice() {

			if ( rtmedia_update_site_option( 'rtmedia-update-template-notice-v3_9_4', 'hide' ) ) {
				echo '1';
			} else {
				echo '0';
			}
			die();
		}

		public static function render_admin_ui( $page, $sub_tabs, $args = array() ) {

			// wrapper class
			$wrapper_class = '';
			if ( ! empty( $args[ 'wrapper_class' ] ) && is_array( $args[ 'wrapper_class' ] ) ) {
				$wrapper_class = implode( ' ', $args[ 'wrapper_class' ] );
			}

			// tabs
			if ( $page == 'rtmedia-settings' ) {
				$sub_tabs = apply_filters( 'rtmedia_pro_settings_tabs_content', $sub_tabs );
				ksort( $sub_tabs );
			}
			$tab_position_class = 'rtm-vertical-tabs';
			if( $page == 'rtmedia-addons' ){
				$tab_position_class = 'rtm-horizotanl-tabs';
			}
			?>

			<div class="clearfix <?php echo $tab_position_class; ?> rtm-admin-tab-container <?php echo $wrapper_class; ?>">
				<ul class="rtm-tabs">
					<?php
					$i = 1;
					foreach ( $sub_tabs as $tab ) {

						// tab status
						$active_class = '';
						if ( 1 == $i ) {
							$active_class = 'active';
						}

						// tab icon
						$icon = '';
						if ( isset( $tab[ 'icon' ] ) && ! empty( $tab[ 'icon' ] ) ) {
							$icon = '<i class="' . $tab[ 'icon' ] . ' dashicons rtmicon"></i>';
						}
						?>
						<li class="<?php echo $active_class ?>">
							<a id="tab-<?php echo substr( $tab[ 'href' ], 1 ) ?>" title="<?php echo $tab[ 'title' ] ?>" href="<?php echo $tab[ 'href' ] ?>" class="rtmedia-tab-title <?php echo sanitize_title( $tab[ 'name' ] ) ?>">
								<?php echo $icon ?><span><?php echo $tab[ 'name' ] ?></span>
							</a>
						</li>
						<?php
						$i ++;
					}
					?>
				</ul>

				<div class="tabs-content rtm-tabs-content">
					<?php
					$k = 1;
					foreach ( $sub_tabs as $tab ) {
						$active_class = '';
						if ( 1 == $k ) {
							$active_class = ' active';
						}
						$k ++;
						if ( isset( $tab[ 'icon' ] ) && ! empty( $tab[ 'icon' ] ) ) {
							$icon = '<i class="' . $tab[ 'icon' ] . '"></i>';
						}
						$tab_without_hash = explode( '#', $tab[ 'href' ] );
						$tab_without_hash = $tab_without_hash[ 1 ];
						echo '<div class="rtm-content' . $active_class . '" id="' . $tab_without_hash . '">';
						if ( isset( $tab[ 'args' ] ) ) {
							call_user_func( $tab[ 'callback' ], $page, $tab[ 'args' ] );
						} else {
							call_user_func( $tab[ 'callback' ], $page );
						}
						echo '</div>';
					}
					?>
				</div>

			</div>
			<?php
		}

		/**
		 * To remove setting saved parameter from url once satting saved
		 * Add parameter to this array WP will remove variable from Query string
		 * @param $removable_query_args
		 * @return array $removable_query_args
		 */
		function removable_query_args( $removable_query_args ) {
			if ( isset( $_GET['page'] ) && $_GET['page'] == 'rtmedia-settings' ) {
				$removable_query_args[] = 'settings-saved';
			}
			return $removable_query_args;
		}

	}

}

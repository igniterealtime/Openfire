<?php

/**
 * Description of RTMediaSettings
 *
 * @author Gagandeep Singh <gagandeep.singh@rtcamp.com>
 * @author Joshua Abenazer <joshua.abenazer@rtcamp.com>
 */
if ( ! class_exists( 'RTMediaSettings' ) ){

	class RTMediaSettings {

		/**
		 * Constructor
		 *
		 * @access public
		 * @return void
		 */
		public function __construct(){
			if ( ! ( defined( 'DOING_AJAX' ) && DOING_AJAX ) ){
				add_action( 'admin_init', array( $this, 'settings' ) );
				if ( isset( $_POST['rtmedia-options-save'] ) ){
					add_action( 'init', array( $this, 'settings' ) );
				}
			}
		}

		/**
		 * Get default options.
		 *
		 * @access public
		 * @global string 'buddypress-media'
		 *
		 * @param  void
		 *
		 * @return array  $defaults
		 */
		public function get_default_options(){
			global $rtmedia;
			$options = $rtmedia->options;

			$defaults = array(
				'general_enableAlbums' => 1,
				'general_enableComments' => 0,
				'general_downloadButton' => 0,
				'general_enableLightbox' => 1,
				'general_perPageMedia' => 10,
				'general_display_media' => 'load_more',
				'general_enableMediaEndPoint' => 0,
				'general_showAdminMenu' => 0,
				'general_videothumbs' => 2,
                'general_jpeg_image_quality' => 90,
				'general_uniqueviewcount' => 0,
				'general_viewcount' => 0,
				'general_AllowUserData' => 1,
				'rtmedia_add_linkback' => 0,
				'rtmedia_affiliate_id' => '',
				'rtmedia_enable_api' => 0,
				'general_masonry_layout' => 0,
                'general_direct_upload' => 0,
			);

			foreach ( $rtmedia->allowed_types as $type ) {
				// invalid keys handled in sanitize method
				$defaults[ 'allowedTypes_' . $type['name'] . '_enabled' ]  = 0;
				$defaults[ 'allowedTypes_' . $type['name'] . '_featured' ] = 0;
			}

			/* Previous Sizes values from buddypress is migrated */
			foreach ( $rtmedia->default_sizes as $type => $typeValue ) {
				foreach ( $typeValue as $size => $sizeValue ) {
					foreach ( $sizeValue as $dimension => $value ) {
						$defaults[ 'defaultSizes_' . $type . '_' . $size . '_' . $dimension ] = 0;
					}
				}
			}

			/* Privacy */
			$defaults['privacy_enabled']      = 0;
			$defaults['privacy_default']      = 0;
			$defaults['privacy_userOverride'] = 0;

			$defaults['buddypress_enableOnGroup']    = 1;
			$defaults['buddypress_enableOnActivity'] = 1;
			$defaults['buddypress_enableOnProfile']  = 1;
			$defaults['buddypress_limitOnActivity']  = 0;
                        $defaults['buddypress_enableNotification'] = 0;
			$defaults['styles_custom']               = '';
			$defaults['styles_enabled']              = 1;

			if ( isset( $options['general_videothumbs'] ) && is_numeric( $options['general_videothumbs'] ) && intval( $options['general_videothumbs'] ) > 10 ){
				$defaults['general_videothumbs'] = 10;
			}
            
            if( isset( $options['general_jpeg_image_quality'] ) ) {
                if( is_numeric( $options['general_jpeg_image_quality'] ) ) {
                    if( $options['general_jpeg_image_quality'] > 100 ) {
                        $defaults['general_jpeg_image_quality'] = 100;
                    } else if( $options['general_jpeg_image_quality'] < 1 ) {
                        $defaults['general_jpeg_image_quality'] = 90;
                    }
                } else {
                    $defaults['general_jpeg_image_quality'] = 90;
                }                
            }

			$defaults = apply_filters( 'rtmedia_general_content_default_values', $defaults );

			return $defaults;
		}

		/**
		 * Register Settings.
		 *
		 * @access public
		 *
		 * @param  type $options
		 *
		 * @return type $options
		 */
		public function sanitize_options( $options ){
			$defaults = $this->get_default_options();
			$options  = wp_parse_args( $options, $defaults );

			return $options;
		}

		/**
		 * Sanitize before saving the options.
		 *
		 * @access public
		 *
		 * @param  type $options
		 *
		 * @return type $options
		 */
		public function sanitize_before_save_options( $options ){
			$defaults = $this->get_default_options();

			foreach ( $defaults as $key => $value ) {
				if ( ! isset( $options[ $key ] ) ){
					$options[ $key ] = '0';
				}
			}

			if ( isset( $options['general_videothumbs'] ) && intval( $options['general_videothumbs'] ) > 10 ){
				$options['general_videothumbs'] = 10;
			}
            
            // Checking if video_thumbnails value is less then 0
            if ( isset( $options['general_videothumbs'] ) && intval( $options['general_videothumbs'] ) <= 0 ){
				$options['general_videothumbs'] = 2;
			}

			// Checking if number of media perpage is integer or not
			if( isset( $options[ 'general_perPageMedia' ] ) ) {
				if( $options[ 'general_perPageMedia' ] < 1 ) {
					$options[ 'general_perPageMedia' ] = 10;
				} else if( !is_int( $options[ 'general_perPageMedia' ] ) ) {
					$options[ 'general_perPageMedia' ] = round( $options[ 'general_perPageMedia' ] );
				}
			}
            
			return $options;
		}

		/**
		 * rtmedia settings.
		 *
		 * @access public
		 * @global BPMediaAddon $rtmedia_addon
		 *
		 * @param               void
		 *
		 * @return void
		 */
		public function settings(){
			global $rtmedia, $rtmedia_addon, $rtmedia_save_setting_single;
			$options          = rtmedia_get_site_option( 'rtmedia-options' );
			$options          = $this->sanitize_options( $options );
			$rtmedia->options = $options;
			// Save Settings first then proceed.
			if ( isset( $_POST['rtmedia-options-save'] ) ){
				$options               = $_POST['rtmedia-options'];
				$options               = $this->sanitize_before_save_options( $options );
				$options               = apply_filters( 'rtmedia_pro_options_save_settings', $options );
				$is_rewrite_rule_flush = apply_filters( 'rtmedia_flush_rewrite_rule', false );
				rtmedia_update_site_option( 'rtmedia-options', $options );
				do_action( 'rtmedia_save_admin_settings', $options );
				if ( $is_rewrite_rule_flush ){
					flush_rewrite_rules( false );
				}
                $settings_saved = '';
                if( !isset( $_GET[ 'settings-saved' ] ) ) {
                    $settings_saved = '&settings-saved=true';
                }
				if( isset( $_SERVER['HTTP_REFERER'] ) ){
					wp_redirect( $_SERVER['HTTP_REFERER'] . $settings_saved );
				}
				global $rtmedia;
				$rtmedia->options = $options;
			}

			if ( function_exists( 'add_settings_section' ) ){
				$rtmedia_addon = new RTMediaAddon();
				add_settings_section( 'rtm-addons', __( 'BuddyPress Media Addons for Photos', 'buddypress-media' ), array( $rtmedia_addon, 'get_addons' ), 'rtmedia-addons' );
				$rtmedia_support = new RTMediaSupport( false );
				add_settings_section( 'rtm-support', __( 'Support', 'buddypress-media' ), array( $rtmedia_support, 'get_support_content' ), 'rtmedia-support' );
				$rtmedia_themes = new RTMediaThemes();
				add_settings_section( 'rtm-themes', __( 'rtMedia Themes', 'buddypress-media' ), array( $rtmedia_themes, 'get_themes' ), 'rtmedia-themes' );
			}

			//            if (!BPMediaPrivacy::is_installed()) {
			//                $rtmedia_privacy = new BPMediaPrivacySettings();
			//                add_filter('rtmedia_add_sub_tabs', array($rtmedia_privacy, 'ui'), 99, 2);
			//                add_settings_section('rtm-privacy', __('Update Database', 'buddypress-media'), array($rtmedia_privacy, 'init'), 'rtmedia-privacy');
			//            }
			//$rtmedia_album_importer = new BPMediaAlbumimporter();
			//add_settings_section('rtm-rt-album-importer', __('BP-Album Importer', 'buddypress-media'), array($rtmedia_album_importer, 'ui'), 'rtmedia-importer');
			//register_setting('buddypress-media', 'rtmedia_options', array($this, 'sanitize'));
			if ( ! isset( $rtmedia_save_setting_single ) ){
				$rtmedia_save_setting_single = true;
			}
		}

		/**
		 * Show network notices.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function network_notices(){
			$flag = 1;
			if ( rtmedia_get_site_option( 'rtm-media-enable', false ) ){
				echo '<div id="setting-error-bpm-media-enable" class="error"><p><strong>' . rtmedia_get_site_option( 'rtm-media-enable' ) . '</strong></p></div>';
				delete_site_option( 'rtm-media-enable' );
				$flag = 0;
			}
			if ( rtmedia_get_site_option( 'rtm-media-type', false ) ){
				echo '<div id="setting-error-bpm-media-type" class="error"><p><strong>' . rtmedia_get_site_option( 'rtm-media-type' ) . '</strong></p></div>';
				delete_site_option( 'rtm-media-type' );
				$flag = 0;
			}
			if ( rtmedia_get_site_option( 'rtm-media-default-count', false ) ){
				echo '<div id="setting-error-bpm-media-default-count" class="error"><p><strong>' . rtmedia_get_site_option( 'rtm-media-default-count' ) . '</strong></p></div>';
				delete_site_option( 'rtm-media-default-count' );
				$flag = 0;
			}

			if ( rtmedia_get_site_option( 'rtm-recount-success', false ) ){
				echo '<div id="setting-error-bpm-recount-success" class="updated"><p><strong>' . rtmedia_get_site_option( 'rtm-recount-success' ) . '</strong></p></div>';
				delete_site_option( 'rtm-recount-success' );
				$flag = 0;
			} elseif ( rtmedia_get_site_option( 'rtm-recount-fail', false ) ) {
				echo '<div id="setting-error-bpm-recount-fail" class="error"><p><strong>' . rtmedia_get_site_option( 'rtm-recount-fail' ) . '</strong></p></div>';
				delete_site_option( 'rtm-recount-fail' );
				$flag = 0;
			}

			if ( get_site_option( 'rtm-settings-saved' ) && $flag ){
				echo '<div id="setting-error-bpm-settings-saved" class="updated"><p><strong>' . get_site_option( 'rtm-settings-saved' ) . '</strong></p></div>';
			}

			delete_site_option( 'rtm-settings-saved' );
		}

		/**
		 * Show allowed types.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function allowed_types(){
			$allowed_types = rtmedia_get_site_option( 'upload_filetypes', 'jpg jpeg png gif' );
			$allowed_types = explode( ' ', $allowed_types );
			$allowed_types = implode( ', ', $allowed_types );
			echo '<span class="description">' . sprintf( __( 'Currently your network allows uploading of the following file types. You can change the settings <a href="%s">here</a>.<br /><code>%s</code></span>', 'buddypress-media' ), network_admin_url( 'settings.php#upload_filetypes' ), $allowed_types );
		}

		/**
		 * Sanitizes the settings
		 *
		 * @access public
		 * @global type $rtmedia_admin
		 *
		 * @param  type $input
		 *
		 * @return type $input
		 */
		public function sanitize( $input ){
			global $rtmedia_admin;
			if ( isset( $_POST['refresh-count'] ) ){
				if ( $rtmedia_admin->update_count() ){
					if ( is_multisite() ){
						rtmedia_update_site_option( 'rtm-recount-success', __( 'Recounting of media files done successfully', 'buddypress-media' ) );
					} else {
						add_settings_error( __( 'Recount Success', 'buddypress-media' ), 'rtm-recount-success', __( 'Recounting of media files done successfully', 'buddypress-media' ), 'updated' );
					}
				} else {
					if ( is_multisite() ){
						rtmedia_update_site_option( 'rtm-recount-fail', __( 'Recounting Failed', 'buddypress-media' ) );
					} else {
						add_settings_error( __( 'Recount Fail', 'buddypress-media' ), 'rtm-recount-fail', __( 'Recounting Failed', 'buddypress-media' ) );
					}
				}
			}
			//            if (!isset($_POST['rtmedia_options']['enable_on_profile']) && !isset($_POST['rtmedia_options']['enable_on_group'])) {
			//                if (is_multisite())
			//                    update_site_option('rtm-media-enable', __('Enable BuddyPress Media on either User Profiles or Groups or both. Atleast one should be selected.', 'buddypress-media'));
			//                else
			//                    add_settings_error(__('Enable BuddyPress Media', 'buddypress-media'), 'rtm-media-enable', __('Enable BuddyPress Media on either User Profiles or Groups or both. Atleast one should be selected.', 'buddypress-media'));
			//                $input['enable_on_profile'] = 1;
			//            }
			if ( ! isset( $_POST['rtmedia_options']['videos_enabled'] ) && ! isset( $_POST['rtmedia_options']['audio_enabled'] ) && ! isset( $_POST['rtmedia_options']['images_enabled'] ) ){
				if ( is_multisite() ){
					rtmedia_update_site_option( 'rtm-media-type', __( 'Atleast one Media Type Must be selected', 'buddypress-media' ) );
				} else {
					add_settings_error( __( 'Media Type', 'buddypress-media' ), 'rtm-media-type', __( 'Atleast one Media Type Must be selected', 'buddypress-media' ) );
				}
				$input['images_enabled'] = 1;
			}

			$input['default_count'] = intval( $_POST['rtmedia_options']['default_count'] );

			if ( ! is_int( $input['default_count'] ) || ( $input['default_count'] < 0 ) || empty( $input['default_count'] ) ){
				if ( is_multisite() ){
					rtmedia_update_site_option( 'rtm-media-default-count', __( '"Number of media" count value should be numeric and greater than 0.', 'buddypress-media' ) );
				} else {
					add_settings_error( __( 'Default Count', 'buddypress-media' ), 'rtm-media-default-count', __( '"Number of media" count value should be numeric and greater than 0.', 'buddypress-media' ) );
				}
				$input['default_count'] = 10;
			}
			if ( is_multisite() ){
				rtmedia_update_site_option( 'rtm-settings-saved', __( 'Settings saved.', 'buddypress-media' ) );
			}
			do_action( 'rtmedia_sanitize_settings', $_POST, $input );

			return $input;
		}

		/**
		 * Show image settings intro.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function image_settings_intro(){
			if ( is_plugin_active( 'regenerate-thumbnails/regenerate-thumbnails.php' ) ){
				$regenerate_link = admin_url( '/tools.php?page=regenerate-thumbnails' );
			} elseif ( array_key_exists( 'regenerate-thumbnails/regenerate-thumbnails.php', get_plugins() ) ) {
				$regenerate_link = admin_url( '/plugins.php#regenerate-thumbnails' );
			} else {
				$regenerate_link = wp_nonce_url( admin_url( 'update.php?action=install-plugin&plugin=regenerate-thumbnails' ), 'install-plugin_regenerate-thumbnails' );
			}
			echo '<span class="description">' . sprintf( __( 'If you make changes to width, height or crop settings, you must use "<a href="%s">Regenerate Thumbnail Plugin</a>" to regenerate old images."', 'buddypress-media' ), $regenerate_link ) . '</span>';
			echo '<div class="clearfix">&nbsp;</div>';
		}

		/**
		 * Output a checkbox for privacy_notice.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return string $notice
		 */
		public function privacy_notice(){
			if ( current_user_can( 'create_users' ) ){
				//                if (BPMediaPrivacy::is_installed())
				//                    return;
				$url = esc_url( add_query_arg( array( 'page' => 'rtmedia-privacy' ), ( is_multisite() ? network_admin_url( 'admin.php' ) : admin_url( 'admin.php' ) ) ) );

				$notice = '
                <div class="error">
                <p>' . __( 'BuddyPress Media 2.6 requires a database upgrade. ', 'buddypress-media' ) . '<a href="' . $url . '">' . __( 'Update Database', 'buddypress-media' ) . '.</a></p>
                </div>
                ';
				echo $notice;
			}
		}

		/**
		 * Output rtmedia_support_intro.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function rtmedia_support_intro(){
			echo '<p>' . __( 'If your site has some issues due to BuddyPress Media and you want one on one support then you can create a support topic on the <a target="_blank" href="http://community.rtcamp.com/c/rtmedia?utm_source=dashboard&utm_medium=plugin&utm_campaign=rtmedia">rtCamp Support Forum</a>.', 'buddypress-media' ) . '</p>';
			echo '<p>' . __( 'If you have any suggestions, enhancements or bug reports, then you can open a new issue on <a target="_blank" href="https://github.com/rtCamp/rtmedia/issues/new">GitHub</a>.', 'buddypress-media' ) . '</p>';
		}

	}

}

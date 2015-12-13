<?php
/**
 * Description of RTMediaSupport
 *
 * @author Gagandeep Singh <gagandeep.singh@rtcamp.com>
 * @author Joshua Abenazer <joshua.abenazer@rtcamp.com>
 */
if ( ! class_exists( 'RTMediaSupport' ) ) {

	class RTMediaSupport {

		var $debug_info;
		var $curr_sub_tab;
		// current page
		public static $page;

		/**
		 * Constructor
		 *
		 * @access public
		 *
		 * @param  bool $init
		 *
		 * @return void
		 */
		public function __construct( $init = true ) {

			if ( ! is_admin() ) {
				return;
			}

			$this->curr_sub_tab = 'support';
			if ( isset( $_REQUEST[ 'tab' ] ) ) {
				$this->curr_sub_tab = $_REQUEST[ 'tab' ];
			}
		}

		/**
		 * Get support content.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function get_support_content() {
			$tabs = array();
			global $rtmedia_admin;
			$tabs[] = array(
				'title' => __( 'Support', 'buddypress-media' ),
				'name' => __( 'Support', 'buddypress-media' ),
				'href' => '#support',
				'icon' => 'dashicons-businessman',
				'callback' => array( $this, 'call_get_form' ),
			);
			$tabs[] = array(
				'title' => __( 'Debug Info', 'buddypress-media' ),
				'name' => __( 'Debug Info', 'buddypress-media' ),
				'href' => '#debug',
				'icon' => 'dashicons-admin-tools',
				'callback' => array( $this, 'debug_info_html' ),
			);
			if ( $this->is_migration_required() ) { //if any un-migrated media is there
				$tabs[] = array(
					'title' => __( 'Migration', 'buddypress-media' ),
					'name' => __( 'Migration', 'buddypress-media' ),
					'href' => '#migration',
					'callback' => array( $this, 'migration_html' ),
				);
			}
			?>
			<div id="rtm-support">
				<?php RTMediaAdmin::render_admin_ui( self::$page, $tabs ); ?>
			</div>
			<?php
		}

		/**
		 * Render support.
		 *
		 * @access public
		 *
		 * @param  type $page
		 *
		 * @return void
		 */
		public function render_support( $page = '' ) {

			self::$page = $page;

			global $wp_settings_sections, $wp_settings_fields;

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
		 * Define Service Selector.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function service_selector() {
			?>
			<div>
				<form name="rtmedia_service_select_form" method="post">
					<p>
						<label class="bp-media-label" for="select_support"><?php _e( 'Service', 'buddypress-media' ); ?>:</label>
						<select name="rtmedia_service_select">
							<option
								value="premium_support" <?php
								if ( 'premium_support' == $_POST[ 'form' ] ) {
									echo 'selected';
								}
								?>><?php _e( 'Premium Support', 'buddypress-media' ); ?></option>
							<option
								value="bug_report" <?php
								if ( 'bug_report' == $_POST[ 'form' ] ) {
									echo 'selected';
								}
								?>><?php _e( 'Bug Report', 'buddypress-media' ); ?></option>
							<option
								value="new_feature" <?php
								if ( 'new_feature' == $_POST[ 'form' ] ) {
									echo 'selected';
								}
								?>><?php _e( 'New Feature', 'buddypress-media' ); ?></option>
						</select>
						<input name="support_submit" value="<?php esc_attr_e( 'Submit', 'buddypress-media' ); ?>" type="submit" class="button"/>
					</p>
				</form>
			</div>
			<?php
		}

		/**
		 * Call rtmedia admin support form.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function call_get_form( $page = '' ) {
			if ( isset( $_REQUEST[ 'page' ] ) && 'rtmedia-support' == $_REQUEST[ 'page' ] ) {
				//echo "<h2 class='nav-tab-wrapper'>".$this->rtmedia_support_sub_tabs()."</h2>";
				if ( 'support' == $this->curr_sub_tab ) {
					echo "<div id='rtmedia_service_contact_container' class='rtm-support-container'><form name='rtmedia_service_contact_detail' method='post'>";
					$this->get_form( 'premium_support' );
					echo '</form></div>';
				}
			}
		}

		/**
		 * Get plugin_info.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return array $rtmedia_plugins
		 */
		public function get_plugin_info() {
			$active_plugins = ( array ) get_option( 'active_plugins', array() );
			if ( is_multisite() ) {
				$active_plugins = array_merge( $active_plugins, rtmedia_get_site_option( 'active_sitewide_plugins', array() ) );
			}
			$rtmedia_plugins = array();
			foreach ( $active_plugins as $plugin ) {
				$plugin_data = @get_plugin_data( WP_PLUGIN_DIR . '/' . $plugin );
				$version_string = '';
				if ( ! empty( $plugin_data[ 'Name' ] ) ) {
					$rtmedia_plugins[] = $plugin_data[ 'Name' ] . ' ' . __( 'by', 'buddypress-media' ) . ' ' . $plugin_data[ 'Author' ] . ' ' . __( 'version', 'buddypress-media' ) . ' ' . $plugin_data[ 'Version' ] . $version_string;
				}
			}
			if ( 0 == sizeof( $rtmedia_plugins ) ) {
				return false;
			} else {
				return implode( ', <br/>', $rtmedia_plugins );
			}
		}

		/**
		 * Scan the rtmedia template files.
		 *
		 * @access public
		 *
		 * @param  string $template_path
		 *
		 * @return array  $result
		 */
		public function rtmedia_scan_template_files( $template_path ) {
			$files = scandir( $template_path );
			$result = array();
			if ( $files ) {
				foreach ( $files as $key => $value ) {
					if ( ! in_array( $value, array( '.', '..' ) ) ) {
						if ( is_dir( $template_path . DIRECTORY_SEPARATOR . $value ) ) {
							$sub_files = $this->rtmedia_scan_template_files( $template_path . DIRECTORY_SEPARATOR . $value );
							foreach ( $sub_files as $sub_file ) {
								$result[] = str_replace( ABSPATH . 'wp-content/', '', RTMediaTemplate::locate_template( substr( $sub_file, 0, ( sizeof( $sub_file ) - 5 ) ) ) );
							}
						} else {
							if ( 'main.php' != $value ) {
								$result[] = $value;
							}
						}
					}
				}
			}

			return $result;
		}

		/**
		 * Show debug_info.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function debug_info() {
			global $wpdb, $wp_version, $bp;
			$debug_info = array();
			$debug_info[ 'Home URL' ] = home_url();
			$debug_info[ 'Site URL' ] = site_url();
			$debug_info[ 'PHP' ] = PHP_VERSION;
			$debug_info[ 'MYSQL' ] = $wpdb->db_version();
			$debug_info[ 'WordPress' ] = $wp_version;
			$debug_info[ 'BuddyPress' ] = ( isset( $bp->version ) ) ? $bp->version : '-NA-';
			$debug_info[ 'rtMedia' ] = RTMEDIA_VERSION;
			$debug_info[ 'OS' ] = PHP_OS;
			if ( extension_loaded( 'imagick' ) ) {
				$imagickobj = new Imagick();
                $imagick = $message = preg_replace( " #((http|https|ftp)://(\S*?\.\S*?))(\s|\;|\)|\]|\[|\{|\}|,|\"|'|:|\<|$|\.\s)#i", "'<a href=\"$1\" target=\"_blank\">$3</a>$4'", $imagickobj->getversion() );
			} else {
				$imagick[ 'versionString' ] = 'Not Installed';
			}
			$debug_info[ 'Imagick' ] = $imagick[ 'versionString' ];
			if ( extension_loaded( 'gd' ) ) {
				$gd = gd_info();
			} else {
				$gd[ 'GD Version' ] = 'Not Installed';
			}
			$debug_info[ 'GD' ] = $gd[ 'GD Version' ];
			$debug_info[ '[php.ini] post_max_size' ] = ini_get( 'post_max_size' );
			$debug_info[ '[php.ini] upload_max_filesize' ] = ini_get( 'upload_max_filesize' );
			$debug_info[ '[php.ini] memory_limit' ] = ini_get( 'memory_limit' );
			$debug_info[ 'Installed Plugins' ] = $this->get_plugin_info();
			$active_theme = wp_get_theme();
			$debug_info[ 'Theme Name' ] = $active_theme->Name;
			$debug_info[ 'Theme Version' ] = $active_theme->Version;
			$debug_info[ 'Author URL' ] = $active_theme->{'Author URI'};
			$debug_info[ 'Template Overrides' ] = implode( ', <br/>', $this->rtmedia_scan_template_files( RTMEDIA_PATH . '/templates/' ) );

			$rtMedia_model = new RTMediaModel();
			$sql = "select media_type, count(id) as count from {$rtMedia_model->table_name} where blog_id = '" . get_current_blog_id() . "' group by media_type";
			global $wpdb;
			$results = $wpdb->get_results( $sql );
			if ( $results ) {
				foreach ( $results as $media ) {
					$debug_info[ 'Total ' . ucfirst( $media->media_type ) . 's' ] = $media->count;
				}
			}
			$this->debug_info = $debug_info;
		}

		/**
		 * Generate debug_info html.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return void
		 */
		public function debug_info_html( $page = '' ) {
			$this->debug_info();
			?>
			<div id="debug-info" class="rtm-option-wrapper">
				<h3 class="rtm-option-title"><?php _e( 'Debug Info', 'buddypress-media' ); ?></h3>
				<table class="form-table rtm-debug-info">
					<tbody>
						<?php
						if ( $this->debug_info ) {
							foreach ( $this->debug_info as $configuration => $value ) {
								?>
								<tr>
									<th scope="row"><?php echo $configuration; ?></th>
									<td><?php echo $value; ?></td>
								</tr><?php
							}
						}
						?>
					</tbody>
				</table>
			</div><?php
		}

		/**
		 * Check for migration_required.
		 *
		 * @access public
		 *
		 * @param  void
		 *
		 * @return bool
		 */
		public function is_migration_required() {
			$pending_rtmedia_migrate = rtmedia_get_site_option( 'rtMigration-pending-count' );
			if ( ( false === $pending_rtmedia_migrate || 0 == $pending_rtmedia_migrate ) ) {
				return false;
			}

			return true;
		}

		/**
		 * Generate migration_html.
		 *
		 * @access public
		 *
		 * @param  type $page
		 *
		 * @return bool
		 */
		public function migration_html( $page = '' ) {
			$pending_rtmedia_migrate = rtmedia_get_site_option( 'rtMigration-pending-count' );

			$content = ' ';
			$flag = true;
			if ( ( false === $pending_rtmedia_migrate || 0 == $pending_rtmedia_migrate ) ) {
				$content .= __( 'There is no media found to migrate.', 'buddypress-media' );
				$flag = false;
			}
			$content = apply_filters( 'rtmedia_migration_content_filter', $content );
			if ( $flag ) {
				$content .= ' <div class="rtmedia-migration-support">';
				$content .= ' <p>' . __( 'Click', 'buddypress-media' ) . ' <a href="' . get_admin_url() . 'admin.php?page=rtmedia-migration">' . __( 'here', 'buddypress-media' ) . '</a>' . __( 'here to migrate media from rtMedia 2.x to rtMedia 3.0+.', 'buddypress-media' ) . '</p>';
				$content .= '</div>';
			}
			?>
			<div id="rtmedia-migration-html">
				<?php echo $content; ?>
			</div>
			<?php
		}

		/**
		 * Generate rtmedia admin form.
		 *
		 * @global type   $current_user
		 *
		 * @param  string $form
		 *
		 * @return void
		 */
		public function get_form( $form = '' ) {
			if ( empty( $form ) ) {
				$form = ( isset( $_POST[ 'form' ] ) ) ? $_POST[ 'form' ] : '';
			}
			if ( $form == '' ) {
				$form = 'premium_support';
			}
			global $current_user;
			switch ( $form ) {
				case 'bug_report':
					$meta_title = __( 'Submit a Bug Report', 'buddypress-media' );
					break;
				case 'new_feature':
					$meta_title = __( 'Submit a New Feature Request', 'buddypress-media' );
					break;
				case 'premium_support':
					$meta_title = __( 'Submit Support Request', 'buddypress-media' );
					break;
			}

			if ( 'premium_support' == $form ) {
				if ( ! defined( 'RTMEDIA_PRO_VERSION' ) ) {
					$content = '<h3 class="rtm-option-title">' . $meta_title . '</h3>';
					$content .= '<p>' . __( 'If your site has some issues due to rtMedia and you want support, feel free to create a support topic on <a target="_blank" href="http://community.rtcamp.com/c/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media">Community Forum</a>.', 'buddypress-media' ) . '</p>';
					$content .= '<p>' . __( 'If you have any suggestions, enhancements or bug reports, you can open a new issue on <a target="_blank" href="https://github.com/rtCamp/rtMedia/issues/new">GitHub</a>.', 'buddypress-media' ) . '</p>';

					echo $content;
				} else {
					?>
					<h3 class="rtm-option-title"><?php echo $meta_title; ?></h3>
					<div id="support-form" class="bp-media-form rtm-support-form rtm-option-wrapper">

						<div class="rtm-form-filed clearfix">
							<label class="bp-media-label" for="name"><?php _e( 'Name', 'buddypress-media' ); ?></label>
							<input class="bp-media-input" id="name" type="text" name="name" value="" required />
							<span class="rtm-tooltip">
								<i class="dashicons dashicons-info rtmicon"></i>
								<span class="rtm-tip">
									<?php _e( 'Use actual user name which used during purchased.', 'buddypress-media' ); ?>
								</span>
							</span>
						</div>

						<div class="rtm-form-filed clearfix">
							<label class="bp-media-label" for="email"><?php _e( 'Email', 'buddypress-media' ); ?></label>
							<input id="email" class="bp-media-input" type="text" name="email" value="" required />
							<span class="rtm-tooltip">
								<i class="dashicons dashicons-info rtmicon"></i>
								<span class="rtm-tip">
									<?php _e( 'Use email id which used during purchased', 'buddypress-media' ); ?>
								</span>
							</span>
						</div>

						<div class="rtm-form-filed clearfix">
							<label class="bp-media-label" for="website"><?php _e( 'Website', 'buddypress-media' ); ?></label>
							<input id="website" class="bp-media-input" type="text" name="website" value="<?php echo ( isset( $_REQUEST[ 'website' ] ) ) ? esc_attr( stripslashes( trim( $_REQUEST[ 'website' ] ) ) ) : get_bloginfo( 'url' ); ?>" required />
						</div>

						<div class="rtm-form-filed clearfix">
							<label class="bp-media-label" for="subject"><?php _e( 'Subject', 'buddypress-media' ); ?></label>
							<input id="subject" class="bp-media-input" type="text" name="subject" value="<?php echo ( isset( $_REQUEST[ 'subject' ] ) ) ? esc_attr( stripslashes( trim( $_REQUEST[ 'subject' ] ) ) ) : ''; ?>" required />
						</div>

						<div class="rtm-form-filed clearfix">
							<label class="bp-media-label" for="details"><?php _e( 'Details', 'buddypress-media' ); ?></label>
							<textarea id="details" class="bp-media-textarea" name="details" required><?php echo ( isset( $_REQUEST[ 'details' ] ) ) ? esc_textarea( stripslashes( trim( $_REQUEST[ 'details' ] ) ) ) : ''; ?></textarea>

							<input type="hidden" name="request_type" value="<?php echo $form; ?>" />
							<input type="hidden" name="request_id" value="<?php echo wp_create_nonce( date( 'YmdHis' ) ); ?>" />
							<input type="hidden" name="server_address" value="<?php echo $_SERVER[ 'SERVER_ADDR' ]; ?>" />
							<input type="hidden" name="ip_address" value="<?php echo $_SERVER[ 'REMOTE_ADDR' ]; ?>" />
							<input type="hidden" name="server_type" value="<?php echo $_SERVER[ 'SERVER_SOFTWARE' ]; ?>" />
							<input type="hidden" name="user_agent" value="<?php echo $_SERVER[ 'HTTP_USER_AGENT' ]; ?>" />
						</div>
					</div><!-- .submit-bug-box -->

					<div class="rtm-form-filed rtm-button-wrapper clearfix">
						<?php submit_button( 'Submit', 'primary', 'rtmedia-submit-request', false ); ?>
						<?php submit_button( 'Cancel', 'secondary', 'cancel-request', false ); ?>
					</div>

					<?php
				}
			}
		}

		/**
		 * Now submit request.
		 *
		 * @global type $rtmedia
		 *
		 * @param       void
		 *
		 * @return void
		 */
		public function submit_request() {
			$this->debug_info();
			global $rtmedia;
			$form_data = wp_parse_args( $_POST[ 'form_data' ] );
			foreach ( $form_data as $key => $formdata ) {
				if ( '' == $formdata && 'phone' != $key ) {
					echo 'false';
					die();
				}
			}
			if ( 'premium_support' == $form_data[ 'request_type' ] ) {
				$mail_type = 'Premium Support';
				$title = __( 'rtMedia Premium Support Request from', 'buddypress-media' );
			} elseif ( 'new_feature' == $form_data[ 'request_type' ] ) {
				$mail_type = 'New Feature Request';
				$title = __( 'rtMedia New Feature Request from', 'buddypress-media' );
			} elseif ( 'bug_report' == $form_data[ 'request_type' ] ) {
				$mail_type = 'Bug Report';
				$title = __( 'rtMedia Bug Report from', 'buddypress-media' );
			} else {
				$mail_type = 'Bug Report';
				$title = __( 'rtMedia Contact from', 'buddypress-media' );
			}
			$message = '<html>
                            <head>
                                    <title>' . $title . get_bloginfo( 'name' ) . '</title>
                            </head>
                            <body>
				<table>
                                    <tr>
                                        <td>Name</td><td>' . strip_tags( $form_data[ 'name' ] ) . '</td>
                                    </tr>
                                    <tr>
                                        <td>Email</td><td>' . strip_tags( $form_data[ 'email' ] ) . '</td>
                                    </tr>
                                    <tr>
                                        <td>Website</td><td>' . strip_tags( $form_data[ 'website' ] ) . '</td>
                                    </tr>
                                    <tr>
                                        <td>Phone</td><td>' . strip_tags( $form_data[ 'phone' ] ) . '</td>
                                    </tr>
                                    <tr>
                                        <td>Subject</td><td>' . strip_tags( $form_data[ 'subject' ] ) . '</td>
                                    </tr>
                                    <tr>
                                        <td>Details</td><td>' . strip_tags( $form_data[ 'details' ] ) . '</td>
                                    </tr>
                                    <tr>
                                        <td>Request ID</td><td>' . strip_tags( $form_data[ 'request_id' ] ) . '</td>
                                    </tr>
                                    <tr>
                                        <td>Server Address</td><td>' . strip_tags( $form_data[ 'server_address' ] ) . '</td>
                                    </tr>
                                    <tr>
                                        <td>IP Address</td><td>' . strip_tags( $form_data[ 'ip_address' ] ) . '</td>
                                    </tr>
                                    <tr>
                                        <td>Server Type</td><td>' . strip_tags( $form_data[ 'server_type' ] ) . '</td>
                                    </tr>
                                    <tr>
                                        <td>User Agent</td><td>' . strip_tags( $form_data[ 'user_agent' ] ) . '</td>
                                    </tr>';
			if ( 'bug_report' == $form_data[ 'request_type' ] ) {
				$message .= '<tr>
                                        <td>WordPress Admin Username</td><td>' . strip_tags( $form_data[ 'wp_admin_username' ] ) . '</td>
                                    </tr>
                                    <tr>
                                        <td>WordPress Admin Password</td><td>' . strip_tags( $form_data[ 'wp_admin_pwd' ] ) . '</td>
                                    </tr>
                                    <tr>
                                        <td>SSH FTP Host</td><td>' . strip_tags( $form_data[ 'ssh_ftp_host' ] ) . '</td>
                                    </tr>
                                    <tr>
                                        <td>SSH FTP Username</td><td>' . strip_tags( $form_data[ 'ssh_ftp_username' ] ) . '</td>
                                    </tr>
                                    <tr>
                                        <td>SSH FTP Password</td><td>' . strip_tags( $form_data[ 'ssh_ftp_pwd' ] ) . '</td>
                                    </tr>
                                    ';
			}
			$message .= '</table>';
			if ( $this->debug_info ) {
				$message .= '<h3>' . __( 'Debug Info', 'buddypress-media' ) . '</h3>';
				$message .= '<table>';
				foreach ( $this->debug_info as $configuration => $value ) {
					$message .= '<tr>
                                    <td style="vertical-align:top">' . $configuration . '</td><td>' . $value . '</td>
                                </tr>';
				}
				$message .= '</table>';
			}
			$message .= '</body>
                </html>';
			add_filter( 'wp_mail_content_type', create_function( '', 'return "text/html";' ) );
			$headers = 'From: ' . $form_data[ 'name' ] . ' <' . $form_data[ 'email' ] . '>' . "\r\n";
			$support_email = 'support@rtcamp.com';
			if ( wp_mail( $support_email, '[rtmedia] ' . $mail_type . ' from ' . str_replace( array( 'http://', 'https://' ), '', $form_data[ 'website' ] ), stripslashes( $message ), $headers ) ) {
				echo '<div class="rtmedia-success" style="margin:10px 0;">';
				if ( 'new_feature' == $form_data[ 'request_type' ] ) {
					echo '<p>' . __( 'Thank you for your Feedback/Suggestion.', 'buddypress-media' ) . '</p>';
				} else {
					echo '<p>' . __( 'Thank you for posting your support request.', 'buddypress-media' ) . '</p>';
					echo '<p>' . __( 'We will get back to you shortly.', 'buddypress-media' ) . '</p>';
				}
				echo '</div>';
			} else {
				echo '<div class="rtmedia-error">';
				echo '<p>' . __( 'Your server failed to send an email.', 'buddypress-media' ) . '</p>';
				echo '<p>' . __( 'Kindly contact your server support to fix this.', 'buddypress-media' ) . '</p>';
				echo '<p>' . __( 'You can alternatively create a support request <a href="https://rtcamp.com/premium-support/" target="_blank">here</a>', 'buddypress-media' ) . '</p>';
				echo '</div>';
			}
			die();
		}

	}

}

<?php
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of RTMediaFormHandler
 *
 * @author udit
 */
class RTMediaFormHandler {

	public static function selectBox( $args ) {
		global $rtmedia;
		$options = $rtmedia->options;
		$defaults = array(
			'key' => '',
			'desc' => '',
			'default' => '',
			'show_desc' => false,
			'selects' => array(),
		);
		$args = wp_parse_args( $args, $defaults );
		extract( $args );

		if ( ! empty( $key ) ) {
			$args[ 'name' ] = 'rtmedia-options[' . $key . ']';
		}

		$args[ 'rtForm_options' ] = array();
		foreach ( $selects as $value => $key ) {
			$args[ 'rtForm_options' ][] = array(
				$key => $value,
				'selected' => ( $default == $value ) ? true : false,
			);
		}

		$chkObj = new rtForm();
		echo $chkObj->get_select( $args );
	}

	/**
	 * Show rtmedia textarea in admin options.
	 *
	 * @access static
	 *
	 * @param  array $args
	 * @param  bool  $echo
	 *
	 * @return string $chkObj->get_textarea( $args )
	 */
	public static function textarea( $args, $echo = true ) {
		global $rtmedia;
		$options = $rtmedia->options;
		$defaults = array(
			'key' => '',
			'desc' => '',
			'show_desc' => false,
		);
		$args = wp_parse_args( $args, $defaults );
		extract( $args );

		if ( ! isset( $value ) ) {
			trigger_error( __( 'Please provide a "value" in the argument.', 'buddypress-media' ) );

			return;
		}

		if ( ! empty( $key ) ) {
			$args[ 'name' ] = 'rtmedia-options[' . $key . ']';
		}

		$args[ 'rtForm_options' ] = array( array( '' => 1, 'checked' => $value ) );

		$chkObj = new rtForm();

		if ( $echo ) {
			echo $chkObj->get_textarea( $args );
		} else {
			return $chkObj->get_textarea( $args );
		}
	}

	/**
	 * Show rtmedia checkbox in admin options.
	 *
	 * @access static
	 *
	 * @param  array $args
	 * @param  bool  $echo
	 *
	 * @return string $chkObj->get_switch( $args )
	 */
	public static function checkbox( $args, $echo = true ) {
		global $rtmedia;
		$options = $rtmedia->options;
		$defaults = array(
			'key' => '',
			'desc' => '',
			'show_desc' => false,
		);
		$args = wp_parse_args( $args, $defaults );
		extract( $args );

		if ( ! isset( $value ) ) {
			trigger_error( __( 'Please provide a "value" in the argument.', 'buddypress-media' ) );

			return;
		}

		if ( ! empty( $key ) ) {
			$args[ 'name' ] = 'rtmedia-options[' . $key . ']';
		}

		$args[ 'rtForm_options' ] = array( array( '' => 1, 'checked' => $value ) );

		$chkObj = new rtForm();
		//		echo $chkObj->get_checkbox($args);
		if ( $echo ) {
			echo $chkObj->get_switch( $args );
		} else {
			return $chkObj->get_switch( $args );
		}
		//		echo $chkObj->get_switch_square($args);
	}

	/**
	 * Show rtmedia radio in admin options.
	 *
	 * @access static
	 *
	 * @param  array $args
	 *
	 * @return void
	 */
	public static function radio( $args ) {
		global $rtmedia;
		$options = $rtmedia->options;
		$defaults = array(
			'key' => '',
			'radios' => array(),
			'default' => '',
			'show_desc' => false,
		);
		$args = wp_parse_args( $args, $defaults );
		extract( $args );

		if ( 2 > count( $radios ) ) {
			trigger_error( __( 'Need to specify atleast two radios, else use a checkbox instead', 'buddypress-media' ) );

			return;
		}

		if ( ! empty( $key ) ) {
			$args[ 'name' ] = 'rtmedia-options[' . $key . ']';
		}

		$args[ 'rtForm_options' ] = array();
		foreach ( $radios as $value => $key ) {
			$args[ 'rtForm_options' ][] = array(
				$key => $value,
				'checked' => ( $default == $value ) ? true : false,
			);
		}

		$objRad = new rtForm();
		echo $objRad->get_radio( $args );
	}

	/**
	 * Show rtmedia dimensions in admin options.
	 *
	 * @access static
	 * @return void
	 */
	public static function dimensions( $args ) {
		$dmnObj = new rtDimensions();
		echo $dmnObj->get_dimensions( $args );
	}

	/**
	 * Show rtmedia number in admin options.
	 *
	 * @access static
	 *
	 * @param  array $args
	 *
	 * @return void
	 */
	public static function number( $args ) {
		global $rtmedia;
		$options = $rtmedia->options;
		$defaults = array(
			'key' => '',
			'desc' => '',
		);
		$args = wp_parse_args( $args, $defaults );
		extract( $args );

		if ( ! isset( $value ) ) {
			trigger_error( __( 'Please provide a "value" in the argument.', 'buddypress-media' ) );

			return;
		}

		if ( ! empty( $key ) ) {
			$args[ 'name' ] = 'rtmedia-options[' . $key . ']';
		}

		$args[ 'value' ] = $value;

		$numObj = new rtForm();
		echo $numObj->get_number( $args );
	}

	/**
	 * Show rtmedia textbox in admin options.
	 *
	 * @access static
	 *
	 * @param  array $args
	 *
	 * @return void
	 */
	public static function textbox( $args ) {
		global $rtmedia;
		$options = $rtmedia->options;
		$defaults = array(
			'key' => '',
			'desc' => '',
		);
		$args = wp_parse_args( $args, $defaults );
		extract( $args );

		if ( ! isset( $value ) ) {
			trigger_error( __( 'Please provide a "value" in the argument.', 'buddypress-media' ) );

			return;
		}

		if ( ! empty( $key ) ) {
			$args[ 'name' ] = 'rtmedia-options[' . $key . ']';
		}

		$args[ 'value' ] = $value;

		$numObj = new rtForm();
		echo $numObj->get_textbox( $args );
	}

	/**
	 * extract settings.
	 *
	 * @access static
	 *
	 * @param  array  $options
	 * @param  string $section_name
	 *
	 * @return array  $section
	 */
	static function extract_settings( $section_name, $options ) {
		$section = array();
		foreach ( $options as $key => $value ) {
			$compare = strncmp( $key, $section_name, strlen( $section_name ) );
			if ( 0 == $compare ) {
				$section[ $key ] = $value;
			}
		}

		return $section;
	}

	/**
	 * display render options.
	 *
	 * @access static
	 *
	 * @param  array $options
	 *
	 * @return array  $render
	 */
	static function display_render_options( $options ){
		$radios                 = array();
		$radios['load_more']  = '<strong>' . __( 'Load More', 'buddypress-media' ) .'</strong>';
		$radios['pagination'] = '<strong>' . __( 'Pagination', 'buddypress-media' ) .'</strong>';

		if ( is_plugin_active( 'regenerate-thumbnails/regenerate-thumbnails.php' ) ) {
			$regenerate_link = admin_url( '/tools.php?page=regenerate-thumbnails' );
		} elseif ( array_key_exists( 'regenerate-thumbnails/regenerate-thumbnails.php', get_plugins() ) ) {
			$regenerate_link = admin_url( '/plugins.php#regenerate-thumbnails' );
		} else {
			$regenerate_link = wp_nonce_url( admin_url( 'update.php?action=install-plugin&plugin=regenerate-thumbnails' ), 'install-plugin_regenerate-thumbnails' );
		}

		$render = array(
			'general_enableComments' => array(
				'title' => __( 'Allow user to comment on uploaded media', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'key' => 'general_enableComments',
					'value' => $options[ 'general_enableComments' ],
					'desc' => __( 'This will display the comment form and comment listing on single media pages as well as inside lightbox (if lightbox is enabled).', 'buddypress-media' ),
				),
				'group' => '10',
			),
			'general_enableLightbox' => array(
				'title' => __( 'Use lightbox to display media', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'key' => 'general_enableLightbox',
					'value' => $options[ 'general_enableLightbox' ],
					'desc' => __( 'View single media in facebook style lightbox.', 'buddypress-media' ),
				),
				'group' => '15',
			),
			'general_perPageMedia' => array(
				'title' => __( 'Number of media per page', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'number' ),
				'args' => array(
					'key' => 'general_perPageMedia',
					'value' => $options[ 'general_perPageMedia' ],
					'class' => array( 'rtmedia-setting-text-box' ),
					'desc' => __( 'Number of media items you want to show per page on front end.', 'buddypress-media' ),
					'min' => 1,
				),
				'group' => '15',
			),
			'general_display_media' => array(
				'title' => __( 'Media display pagination option', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'radio' ),
				'args' => array(
					'key' => 'general_display_media',
					'radios' => $radios,
					'default' => $options[ 'general_display_media' ],
					'desc' => __( 'Choose whether you want the load more button or pagination buttons.', 'buddypress-media' ),
					'class' => array( 'rtmedia-load-more-radio' ),
				),
				'group' => '15',
			), 'general_masonry_layout' => array(
				'title' => __( 'Enable', 'buddypress-media' ) . ' <a href="http://masonry.desandro.com/" target="_blank">Masonry</a> ' . __( 'Cascading grid layout', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'key' => 'general_masonry_layout',
					'value' => $options[ 'general_masonry_layout' ],
					'desc' => __( 'If you enable masonry view, it is advisable to', 'buddypress-media' ) . ' <a href="' . $regenerate_link . '">regenerate thumbnail</a> ' . __( 'for masonry view.', 'buddypress-media' ),
					'class' => array( 'rtm_enable_masonry_view' ),
				),
				'group' => '18',
				'after_content' => __( 'You might need to', 'buddypress-media' ) . ' <a id="rtm-masonry-change-thumbnail-info" href="' . get_admin_url() . 'admin.php?page=rtmedia-settings#rtmedia-sizes">' . __( 'change thumbnail size', 'buddypress-media' ) . '</a> ' . __( 'and uncheck the crop box for thumbnails.', 'buddypress-media' ) . '<br /><br />' . __( 'To set gallery for fixed width, set image height to 0 and width as per your requirement and vice-versa.', 'buddypress-media' ),
			),
            'general_direct_upload' => array(
				'title' => __( 'Enable Direct Upload', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'key' => 'general_direct_upload',
					'value' => $options[ 'general_direct_upload' ],
					'desc' => __( 'Uploading media directly as soon as it gets selected.', 'buddypress-media' ),
				),
				'group' => '19',
			),
		);

		return $render;
	}

	/**
	 * display content.
	 *
	 * @access static
	 *
	 * @param  void
	 *
	 * @return void
	 */
	public static function display_content() {
		global $rtmedia;
		//		$options = self::extract_settings('general', $rtmedia->options);
		$options = $rtmedia->options;
		$render_options = self::display_render_options( $options );
		//		$render_options = apply_filters('rtmedia_general_content_single_view_add_itmes',$render_options, $options);
		$render_options      = apply_filters( 'rtmedia_display_content_add_itmes', $render_options, $options );
		$general_group       = array();
		$general_group[10] = __( 'Single Media View', 'buddypress-media' );
		$general_group[15] = __( 'List Media View', 'buddypress-media' );
		$general_group[18] = __( 'Masonry View', 'buddypress-media' );
        $general_group[19] = __( 'Direct Upload', 'buddypress-media' );
		$general_group       = apply_filters( 'rtmedia_display_content_groups', $general_group );
		ksort( $general_group );
		self::render_tab_content( $render_options, $general_group, 20 );
	}

	/**
	 * render general content.
	 *
	 * @access static
	 *
	 * @param  array $options
	 *
	 * @return array $render
	 */
	static function render_general_content( $options ) {
		$render = array(
			'general_AllowUserData' => array(
				'title' => __( 'Allow usage data tracking', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'key' => 'general_AllowUserData',
					'value' => $options[ 'general_AllowUserData' ],
					'desc' => __( 'To make rtMedia better compatible with your sites, you can help the rtMedia team learn what themes and plugins you are using. No private information about your setup will be sent during tracking.', 'buddypress-media' ),
				)
			),
			'general_showAdminMenu' => array(
				'title' => __( 'Admin bar menu integration', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'key' => 'general_showAdminMenu',
					'value' => $options[ 'general_showAdminMenu' ],
					'desc' => __( 'Add rtMedia menu to WordPress admin bar for easy access to settings and moderation page (if enabled).', 'buddypress-media' ),
				),
				'group' => 10,
			), //
			'rtmedia_add_linkback' => array(
				'title' => __( 'Add a link to rtMedia in footer', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'key' => 'rtmedia_add_linkback',
					'value' => $options[ 'rtmedia_add_linkback' ],
					'desc' => __( 'Help us promote rtMedia.', 'buddypress-media' ),
				),
				'group' => 100,
			), //
			'rtmedia_affiliate_id' => array(
				'title' => __( 'Also add my affiliate-id to rtMedia footer link', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'textbox' ),
				'args' => array(
					'key' => 'rtmedia_affiliate_id',
					'value' => $options[ 'rtmedia_affiliate_id' ],
					'desc' => __( 'Add your affiliate-id along with footer link and get rewarded by our affiliation program.', 'buddypress-media' ),
				),
				'group' => 100,
				'depends' => 'rtmedia_add_linkback',
				'after_content' => __( 'Signup for', 'buddypress-media' ) . ' rtMedia ' . __( 'affiliate program', 'buddypress-media' ) . ' <a href="https://rtcamp.com/affiliates">' . __( 'here', 'buddypress-media' ) . '</a>',
			), //
			'rtmedia_enable_api' => array(
				'title' => __( 'Enable JSON API', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'key' => 'rtmedia_enable_api',
					'value' => $options[ 'rtmedia_enable_api' ],
					'desc' => __( 'This will allow handling API requests for rtMedia sent through any mobile app.', 'buddypress-media' ),
				),
				'group' => 80,
				'after_content' => __( 'You can refer to the API document from', 'buddypress-media' ) . ' <a href="http://docs.rtcamp.com/rtmedia/developers/json-api.html">' . __( 'here', 'buddypress-media' ) . '</a>',
			), //
		);

		return $render;
	}

	/**
	 * Define general_content
	 *
	 * @access static
	 *
	 * @param  array $options
	 *
	 * @return void
	 */
	static function general_content( $options ) {
		global $rtmedia;
		//		$options = self::extract_settings('general', $rtmedia->options);
		$options              = $rtmedia->options;
		$render_options       = self::render_general_content( $options );
		$render_options       = apply_filters( 'rtmedia_general_content_add_itmes', $render_options, $options );
		$general_group        = array();
		$general_group[10]  = __( 'Admin Settings' ,'buddypress-media' );
		$general_group[80]  = __( 'API Settings', 'buddypress-media' );
		$general_group[90]  = __( 'Miscellaneous', 'buddypress-media' );
		$general_group[100] = __( 'Footer Link', 'buddypress-media' );
		$general_group        = apply_filters( 'rtmedia_general_content_groups', $general_group );
		ksort( $general_group );
		$html = '';
		self::render_tab_content( $render_options, $general_group, 90 );
	}

	/**
	 * Get type details
	 *
	 * @access static
	 *
	 * @param  array  $allowed_types
	 * @param  string $key
	 *
	 * @return array  $data
	 */
	static function get_type_details( $allowed_types, $key ) {
		foreach ( $allowed_types as $type ) {
			if ( $type[ 'name' ] == $key ) {
				$data = array(
					'name' => $type[ 'label' ],
					'extn' => $type[ 'extn' ],
				);
				if ( isset( $type[ 'settings_visibility' ] ) ) {
					$data[ 'settings_visibility' ] = $type[ 'settings_visibility' ];
				}

				return $data;
			}
		}
	}

	/**
	 * Define types_render_options.
	 *
	 * @access static
	 *
	 * @param  array $options
	 *
	 * @return array  $render
	 */
	static function types_render_options( $options ) {
		$render = array();
		$allowed_media_type = rtmedia_get_allowed_types();

		foreach ( $options as $key => $value ) {
			$data = explode( '_', $key );
			if ( ! isset( $render[ $data[ 1 ] ] ) ) {
				$render[ $data[ 1 ] ] = self::get_type_details( $allowed_media_type, $data[ 1 ] );
			}
		}

		foreach ( $options as $key => $value ) {
			$data = explode( '_', $key );
			$render[ $data[ 1 ] ][ $data[ 2 ] ] = $value;
		}

		return $render;
	}

	/**
	 * Define types_content.
	 *
	 * @access static
	 *
	 * @param  void
	 *
	 * @return void
	 */
	public static function types_content() {
		global $rtmedia;
		$options = self::extract_settings( 'allowedTypes', $rtmedia->options );

		$render_data = self::types_render_options( $options );
		?>
		<div class="rtm-option-wrapper">
			<?php do_action( 'rtmedia_media_type_setting_message' ); ?>

			<h3 class="rtm-option-title">
				<?php _e( 'Media Types Settings', 'buddypress-media' ); ?>
			</h3>

			<table class="form-table">

				<?php do_action( 'rtmedia_type_settings_before_heading' ); ?>

				<tr>
					<th><strong><?php _e( 'Media Type', 'buddypress-media' ) ?></strong></th>

					<th>

						<span class="rtm-tooltip bottom">
							<strong class="rtm-title"><?php _e( 'Allow Upload', 'buddypress-media' ); ?></strong>
							<span class="rtm-tip-top">
								<?php _e( 'Allows you to upload a particular media type on your post.', 'buddypress-media' ); ?>
							</span>
						</span>
					</th>

					<th>

						<span class="rtm-tooltip bottom">
							<strong class="rtm-title"><?php _e( 'Set Featured', 'buddypress-media' ); ?></strong>
							<span class="rtm-tip-top">
								<?php _e( 'Place a specific media as a featured content on the post.', 'buddypress-media' ); ?>
							</span>
						</span>
					</th>

					<?php do_action( 'rtmedia_type_setting_columns_title' ) ?>
				</tr>

				<?php
				do_action( 'rtmedia_type_settings_after_heading' );

				foreach ( $render_data as $key => $section ) {
					if ( isset( $section[ 'settings_visibility' ] ) && true == $section[ 'settings_visibility' ] ) {
						do_action( 'rtmedia_type_settings_before_body' );

						// allow upload
						$uplaod_args = array( 'key' => 'allowedTypes_' . $key . '_enabled', 'value' => $section[ 'enabled' ] );
						$allow_upload_checkbox = self::checkbox( $uplaod_args, $echo = false );
						$allow_upload_checkbox = apply_filters( 'rtmedia_filter_allow_upload_checkbox', $allow_upload_checkbox, $key, $uplaod_args );

						// allow featured
						$featured_args = array( 'key' => 'allowedTypes_' . $key . '_featured', 'value' => $section[ 'featured' ] );
						$featured_checkbox = self::checkbox( $featured_args, $echo = false );
						$featured_checkbox = apply_filters( 'rtmedia_filter_featured_checkbox', $featured_checkbox, $key );

						if ( ! isset( $section[ 'extn' ] ) || ! is_array( $section[ 'extn' ] ) ) {
							$section[ 'extn' ] = array();
						}

						$extensions = implode( ', ', $section[ 'extn' ] );
						?>

						<tr>
							<td>
								<?php
								echo $section[ 'name' ];

								if ( $key != 'other' ) {
									?>
									<span class="rtm-tooltip rtm-extensions">
										<i class="dashicons dashicons-info rtmicon"></i>
										<span class="rtm-tip">
											<strong><?php echo __( 'File Extensions', 'buddypress-media' ); ?></strong><br />
											<hr />
											<?php echo $extensions; ?>
										</span>
									</span>
									<?php
								}
								?>
							</td>

							<td>
								<span class="rtm-field-wrap">
									<?php echo $allow_upload_checkbox; ?></span>
							</td>

							<td>
								<?php echo $featured_checkbox; ?>
							</td>

							<?php do_action( 'rtmedia_type_setting_columns_body', $key, $section ) ?>
						</tr>

						<?php do_action( 'rtmedia_other_type_settings_textarea', $key ); ?>

						<?php
						do_action( 'rtmedia_type_settings_after_body', $key, $section );
					} else {
						echo '<tr class="hide">';
						echo '<td colspan="3">';
						echo "<input type='hidden' value='1' name='rtmedia-options[allowedTypes_" . $key . "_enabled]'>";
						echo "<input type='hidden' value='0' name='rtmedia-options[allowedTypes_" . $key . "_featured]'>";
						echo '</td>';
						echo '</tr>';
					}
				}
				?>
			</table>
		</div>
		<?php
		do_action( 'rtmedia_after_bp_settings' );
		do_action( 'rtmedia_after_media_types_settings' );
	}

	/**
	 * Define sizes_render_options.
	 *
	 * @access static
	 *
	 * @param  array $options
	 *
	 * @return array $render
	 */
	static function sizes_render_options( $options ) {
		$render = array();
		foreach ( $options as $key => $value ) {
			$data = explode( '_', $key );
			if ( ! isset( $render[ $data[ 1 ] ] ) ) {
				$render[ $data[ 1 ] ] = array();
				$render[ $data[ 1 ] ][ 'title' ] = __( $data[ 1 ], 'buddypress-media' );
			}
			if ( ! isset( $render[ $data[ 1 ] ][ $data[ 2 ] ] ) ) {
				$render[ $data[ 1 ] ][ $data[ 2 ] ] = array();
				$render[ $data[ 1 ] ][ $data[ 2 ] ][ 'title' ] = __( $data[ 2 ], 'buddypress-media' );
			}

			$render[ $data[ 1 ] ][ $data[ 2 ] ][ $data[ 3 ] ] = $value;
		}

		return $render;
	}

	/**
	 * Define sizes_content.
	 *
	 * @access static
	 *
	 * @param  void
	 *
	 * @return void
	 */
	public static function sizes_content() {
		global $rtmedia;
		$options = self::extract_settings( 'defaultSizes', $rtmedia->options );
		$render_data = self::sizes_render_options( $options );
		?>

		<div class="rtm-option-wrapper rtm-img-size-setting">
			<h3 class="rtm-option-title">
				<?php _e( 'Media Size Settings', 'buddypress-media' ); ?>
			</h3>

			<table class="form-table">
				<tr>
					<th><strong><?php _e( 'Category', 'buddypress-media' ) ?></strong></th>
					<th><strong><?php _e( 'Entity', 'buddypress-media' ); ?></strong></th>
					<th><strong><?php _e( 'Width', 'buddypress-media' ); ?></strong></th>
					<th><strong><?php _e( 'Height', 'buddypress-media' ); ?></strong></th>
					<th><strong><?php _e( 'Crop', 'buddypress-media' ); ?></strong></th>
				</tr>

				<?php
				foreach ( $render_data as $parent_key => $section ) {
					$entities = $section;
					unset( $entities[ 'title' ] );
					$count = 0;
					$row_span = sizeof( $entities );
					foreach ( $entities as $entity ) {
						?>
						<tr>
							<?php
							if ( $count == 0 ) {
								?>
								<td class="rtm-row-title" rowspan="<?php echo $row_span; ?>">
									<?php echo ucfirst( $section[ 'title' ] ); ?>
								</td>
								<?php
							}
							?>
							<td>
								<?php echo ucfirst( $entity[ 'title' ] ); ?>
							</td>
							<?php
							$args = array(
								'key' => 'defaultSizes_' . $parent_key . '_' . $entity[ 'title' ],
							);
							foreach ( $entity as $child_key => $value ) {
								if ( 'title' != $child_key ) {
									$args[ $child_key ] = $value;
								}
							}
							self::dimensions( $args );
							?>
						</tr>
						<?php
						$count ++;
					}
				}
				?>
			</table>

		</div>

		<?php
		$options = $rtmedia->options;
        
		// Checking if user has subscribed any plan for encoding
		$rtmedia_encoding_api_key = get_rtmedia_encoding_api_key();

		if ( isset( $rtmedia_encoding_api_key ) && $rtmedia_encoding_api_key != '' && $rtmedia_encoding_api_key ) {
			$render_video_thumb = array(
				'title' => __( 'Number of thumbnails to generate on video upload', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'number' ),
				'args' => array(
					'key' => 'general_videothumbs',
					'value' => $options[ 'general_videothumbs' ],
					'class' => array( 'rtmedia-setting-text-box' ),
					'desc' => __( ' If you choose more than 1 thumbnail, your users will be able to change the thumbnail by going to video \'edit\' section. Maximum value is 10.', 'buddypress-media' ),
					'min' => 1,
					'max' => 10,
				)
			);
			?>

			<div class="rtm-option-wrapper">
				<?php self::render_option_group( __( 'Encoding Settings', 'buddypress-media' ) ); ?>
                <?php self::render_option_content( $render_video_thumb ); ?>
			</div>
			<?php
		}

		$render_jpeg_image_quality = array(
			'title' => __( 'JPEG/JPG image quality (1-100)', 'buddypress-media' ),
			'callback' => array( 'RTMediaFormHandler', 'number' ),
			'args' => array(
				'key' => 'general_jpeg_image_quality',
				'value' => $options[ 'general_jpeg_image_quality' ],
				'class' => array( 'rtmedia-setting-text-box' ),
				'desc' => __( 'Enter JPEG/JPG Image Quality. Minimum value is 1. 100 is original quality.', 'buddypress-media' ),
				'min' => 1,
				'max' => 100,
			)
		);
		?>

		<div class="rtm-option-wrapper">
			<?php self::render_option_group( __( 'Image Quality', 'buddypress-media' ) ); ?>
			<?php self::render_option_content( $render_jpeg_image_quality ); ?>
		</div>

		<?php
	}

	/**
	 * Define custom css content.
	 *
	 * @access static
	 *
	 * @param  void
	 *
	 * @return void
	 */
	public static function custom_css_content() {
		global $rtmedia;
		$options = self::extract_settings( 'styles', $rtmedia->options );
		$render_data = self::custom_css_render_options( $options );

		$render_groups = array();
		$render_groups[ 10 ] = __( 'Custom CSS settings', 'buddypress-media' );

		self::render_tab_content( $render_data, $render_groups, 10 );
	}

	/**
	 * Render custom css options.
	 *
	 * @access static
	 *
	 * @param  array $options
	 *
	 * @return array $render
	 */
	static function custom_css_render_options( $options ) {
		global $rtmedia;

		$render = array(
			'disable_styles' => array(
				'title' => __( 'rtMedia default styles', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'id' => 'rtmedia-disable-styles',
					'key' => 'styles_enabled',
					'value' => $options[ 'styles_enabled' ],
					'desc' => __( 'Load default rtMedia styles. You need to write your own style for rtMedia if you disable it.', 'buddypress-media' ),
				),
				'group' => 10,
			),
			'custom_styles' => array(
				'title' => __( 'Paste your CSS code', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'textarea' ),
				'args' => array(
					'id' => 'rtmedia-custom-css',
					'key' => 'styles_custom',
					'value' => stripcslashes( $options[ 'styles_custom' ] ),
					'desc' => __( 'Custom rtMedia CSS container', 'buddypress-media' ),
				),
				'group' => 10,
			),
		);

		return $render;
	}

	/**
	 * Render privacy options.
	 *
	 * @access static
	 *
	 * @param  array $options
	 *
	 * @return array $render
	 */
	static function privacy_render_options( $options ) {
		global $rtmedia;

		$render = array(
			'enable' => array(
				'title' => __( 'Enable privacy', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'id' => 'rtmedia-privacy-enable',
					'key' => 'privacy_enabled',
					'value' => $options[ 'privacy_enabled' ],
					'desc' => __( 'Enable privacy in rtMedia', 'buddypress-media' ),
				),
				'group' => 10,
			),
			'default' => array(
				'title' => __( 'Default privacy', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'radio' ),
				'args' => array(
					'key' => 'privacy_default',
					'radios' => $rtmedia->privacy_settings[ 'levels' ],
					'default' => $options[ 'privacy_default' ],
					'desc' => __( 'Set default privacy for media', 'buddypress-media' ),
				),
				'group' => 10,
				'depends' => 'privacy_enabled'
			),
			'user_override' => array(
				'title' => __( 'Allow users to set privacy for their content', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'key' => 'privacy_userOverride',
					'value' => $options[ 'privacy_userOverride' ],
					'desc' => __( 'If you choose this, users will be able to change privacy of their own uploads.', 'buddypress-media' ),
				),
				'group' => 10,
				'depends' => 'privacy_enabled',
				'after_content' => __( 'For group uploads, BuddyPress groups privacy is used.', 'buddypress-media' ),
			),
		);

		return $render;
	}

	/**
	 * Render privacy content.
	 *
	 * @access static
	 *
	 * @param  void
	 *
	 * @return void
	 */
	public static function privacy_content() {
		global $rtmedia;

		$general_group = array();
		$general_group[ 10 ] = 'Privacy Settings';
		$general_group = apply_filters( 'rtmedia_privacy_settings_groups', $general_group );

		$options = self::extract_settings( 'privacy', $rtmedia->options );
		$render_options = self::privacy_render_options( $options );
		$render_options = apply_filters( 'rtmedia_privacy_settings_options', $render_options );

		self::render_tab_content( $render_options, $general_group, 10 );
	}

	/**
	 * Render buddypress options.
	 *
	 * @access static
	 *
	 * @param  array $options
	 *
	 * @return array $render
	 */
	static function buddypress_render_options( $options ) {
		$render = array(
			'rtmedia-enable-on-profile' => array(
				'title' => __( 'Enable media in profile', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'key' => 'buddypress_enableOnProfile',
					'value' => $options[ 'buddypress_enableOnProfile' ],
					'desc' => __( 'Enable Media on BuddyPress Profile', 'buddypress-media' ),
				),
				'group' => 10,
			),
			'rtmedia-enable-on-group' => array(
				'title' => __( 'Enable media in group', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'key' => 'buddypress_enableOnGroup',
					'value' => $options[ 'buddypress_enableOnGroup' ],
					'desc' => __( 'Enable Media on BuddyPress Groups', 'buddypress-media' ),
				),
				'group' => 10,
			),
			'rtmedia-enable-on-activity' => array(
				'title' => __( 'Allow upload from activity stream', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'key' => 'buddypress_enableOnActivity',
					'value' => $options[ 'buddypress_enableOnActivity' ],
					'desc' => __( 'Allow upload using status update box present on activity stream page', 'buddypress-media' ),
					'id' => 'rtmedia-bp-enable-activity',
				),
				'group' => 10,
			),
			'rtmedia-activity-feed-limit' => array(
				'title' => __( 'Number of media items to show in activity stream', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'number' ),
				'args' => array(
					'key' => 'buddypress_limitOnActivity',
					'value' => $options[ 'buddypress_limitOnActivity' ],
					'desc' => __( 'With bulk uploads activity, the stream may get flooded. You can control the maximum number of media items or files per activity. This limit will not affect the actual number of uploads. This is only for display. <em>0</em> means unlimited.', 'buddypress-media' ),
					'class' => array( 'rtmedia-setting-text-box rtmedia-bp-activity-setting' ),
					'min' => 0,
				),
				'group' => 10,
			),
                        'rtmedia-enable-notification' => array(
				'title' => __( 'Enable media notification', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'key' => 'buddypress_enableNotification',
					'value' => $options[ 'buddypress_enableNotification' ],
					'desc' => __( 'This will enable notifications to media authors for media likes and comments.', 'buddypress-media' ),
					
				),
				'group' => 10,
			),
			'general_enableAlbums' => array(
				'title' => __( 'Organize media into albums', 'buddypress-media' ),
				'callback' => array( 'RTMediaFormHandler', 'checkbox' ),
				'args' => array(
					'id' => 'rtmedia-album-enable',
					'key' => 'general_enableAlbums',
					'value' => $options[ 'general_enableAlbums' ],
					'desc' => __( 'This will add \'album\' tab to BuddyPress profile and group depending on the ^above^ settings.', 'buddypress-media' ),
				),
				'group' => 50,
			),
		);

		return $render;
	}

	/**
	 * Define buddypress content.
	 *
	 * @access static
	 *
	 * @param  void
	 *
	 * @return void
	 */
	public static function buddypress_content() {
		global $rtmedia;

		$general_group = array();
		$general_group[ 10 ] = 'Integration With BuddyPress Features';
		$general_group[ 50 ] = 'Album Settings';
		$general_group = apply_filters( 'rtmedia_buddypress_setting_group', $general_group );

		$render_options = self::buddypress_render_options( $rtmedia->options );
		$render_options = apply_filters( 'rtmedia_album_control_setting', $render_options, $rtmedia->options );

		$render_options = apply_filters( 'rtmedia_buddypress_setting_options', $render_options );

		self::render_tab_content( $render_options, $general_group, 10 );

		do_action( 'rtmedia_buddypress_setting_content' );
	}

	/**
	 * Define rtForm settings tabs content.
	 *
	 * @access static
	 *
	 * @param  type  $page
	 * @param  array $sub_tabs
	 *
	 * @return void
	 */
	public static function rtForm_settings_tabs_content( $page, $sub_tabs ) {
		$args = array(
			'wrapper_class' => array(
				'rtm-settings-tab-container',
			),
		);
		RTMediaAdmin::render_admin_ui( $page, $sub_tabs, $args );
	}

	/**
	 * Define rtForm do_settings_fields.
	 *
	 * @access static
	 *
	 * @param  type $page
	 * @param  type $section
	 *
	 * @return void
	 */
	public static function rtForm_do_settings_fields( $page, $section ) {
		global $wp_settings_fields;

		if ( ! isset( $wp_settings_fields ) || ! isset( $wp_settings_fields[ $page ] ) || ! isset( $wp_settings_fields[ $page ][ $section ] ) ) {
			return;
		}

		foreach ( ( array ) $wp_settings_fields[ $page ][ $section ] as $field ) {
			echo '<div class="row">';
			echo '<div class="large-11 columns">';

			if ( isset( $field[ 'args' ][ 'label_for' ] ) && ! empty( $field[ 'args' ][ 'label_for' ] ) ) {
				call_user_func( $field[ 'callback' ], array_merge( $field[ 'args' ], array( 'label' => $field[ 'args' ][ 'label_for' ] ) ) );
			} else {
				if ( isset( $field[ 'title' ] ) && ! empty( $field[ 'title' ] ) ) {
					call_user_func( $field[ 'callback' ], array_merge( $field[ 'args' ], array( 'label' => $field[ 'title' ] ) ) );
				} else {
					call_user_func( $field[ 'callback' ], $field[ 'args' ] );
				}
			}
			echo '</div>';
			echo '</div>';
		}
	}

	/*
	 * render each tab content
	 *
	 * @param array $option
	 * @param array $groups
	 * @param int $default_group
	 */

	public static function render_tab_content( $options, $groups = array(), $default_group = 0 ) {
		if ( ! empty( $groups ) ) {
			foreach ( $groups as $key => $value ) {
				?>
				<div class="rtm-option-wrapper">
					<?php
					self::render_option_group( $value );
					foreach ( $options as $tab => $option ) {

						if ( ! isset( $option[ 'group' ] ) ) {
							$option[ 'group' ] = $default_group;
						}

						if ( $option[ 'group' ] != $key ) {
							continue;
						}
						self::render_option_content( $option );
					}
					?>
				</div>
				<?php
			}
		} else {
			?>
			<div class="rtm-option-wrapper">
				<?php
				foreach ( $options as $tab => $option ) {
					self::render_option_content( $option );
				}
				?>
			</div>
			<?php
		}
	}

	/*
	 * render option group title inside single tab
	 *
	 * @param string $group
	 */

	public static function render_option_group( $group ) {
		?>
		<h3 class="rtm-option-title"><?php echo $group; ?></h3>
		<?php
	}

	/*
	 * render options
	 * @param array $option
	 */

	public static function render_option_content( $option ) {
		?>

		<table class="form-table" <?php
		if ( isset( $option[ 'depends' ] ) && $option[ 'depends' ] != '' ) {
			echo 'data-depends="' . $option[ 'depends' ] . '"';
		}
		?>>
			<tr>
				<th>
					<?php echo $option[ 'title' ]; ?>
					<?php if ( isset( $option[ 'after_content' ] ) ) { ?>
					<?php } ?>
				</th>
				<td>
					<fieldset>
						<span class="rtm-field-wrap"><?php call_user_func( $option[ 'callback' ], $option[ 'args' ] ); ?></span>
						<span class="rtm-tooltip">
							<i class="dashicons dashicons-info rtmicon"></i>
							<span class="rtm-tip">
								<?php echo ( isset( $option[ 'args' ][ 'desc' ] ) ) ? $option[ 'args' ][ 'desc' ] : 'NA'; ?>
							</span>
						</span>
					</fieldset>
				</td>
			</tr>
		</table>

		<?php
		if ( isset( $option[ 'after_content' ] ) && $option[ 'after_content' ] != '' ) {
			?>
			<div class="rtm-message rtm-notice"><?php echo wpautop( $option[ 'after_content' ] ); ?></div><?php
		}
	}

}

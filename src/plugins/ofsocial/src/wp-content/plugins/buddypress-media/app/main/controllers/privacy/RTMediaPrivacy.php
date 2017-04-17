<?php
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of RTMediaPrivacy
 *
 * @author saurabh
 */
class RTMediaPrivacy {

	/**
	 *
	 * @var object default application wide privacy levels
	 */
	public
			$default_privacy;

	function __construct( $flag = true ) {
		if ( is_rtmedia_privacy_enable() && $flag ) {
			//add_action ( 'rtmedia_after_file_upload_ui' , array ( $this , 'uploader_privacy_ui' ) ) ;
			//add_action ( 'rtmedia_add_edit_fields' , array ( $this , 'edit_media_privacy_ui' ),2 ) ;
			add_action( 'bp_init', array( $this, 'add_nav' ) );
			add_action( 'bp_template_content', array( $this, 'content' ) );
			add_filter( 'bp_activity_get_user_join_filter', array( $this, 'activity_privacy' ), 10, 6 );
			add_filter( 'bp_use_legacy_activity_query', array( $this, 'enable_buddypress_privacy' ), 10, 3 );
			add_filter( 'bp_activity_has_more_items', array( $this, 'enable_buddypress_load_more' ), 10, 1 );
			add_action( 'bp_actions', array( $this,'rt_privacy_settings_action' ) );
		}
		add_action( 'friends_friendship_accepted', array( 'RTMediaFriends', 'refresh_friends_cache' ) );
		add_action( 'friends_friendship_deleted', array( 'RTMediaFriends', 'refresh_friends_cache' ) );
	}

	function enable_buddypress_load_more( $has_more_items ) {
		global $activities_template;
		return true;
	}

	function enable_buddypress_privacy( $flag, $method, $func_args ) {
		global $rtmedia;
		$option = $rtmedia->options;
		if ( isset( $option[ 'privacy_enabled' ] ) && $option[ 'privacy_enabled' ] != '0' ) {
			if ( $method == "BP_Activity_Activity::get" ) {
				$flag = true;
			}
		}
		return $flag;
	}

	function edit_media_privacy_ui( $echo = true ) {
		$privacy = "";
		$privacy = $this->select_privacy_ui( $echo = false );
		if ( $privacy != "" ) {
			if ( $echo )
				echo "<div class='rtmedia-edit-privacy rtm-field-wrap'><label for='privacy'>Privacy : </label>" . $privacy . "</div>";
			else
				return "<div class='rtmedia-edit-privacy rtm-field-wrap'><label for='privacy'>Privacy : </label>" . $privacy . "</div>";
		}
	}

	function uploader_privacy_ui( $attr ) {
		if ( ! isset( $attr[ 'privacy' ] ) ) {
			$this->select_privacy_ui();
		}
	}

	function select_privacy_ui( $echo = true, $select_id = false ) {
		global $rtmedia;

		if ( ! is_rtmedia_privacy_enable() )
			return false;

		if ( ! is_rtmedia_privacy_user_overide() )
			return false;

		global $rtmedia_media;
		$default = 0;
		if ( isset( $rtmedia_media->privacy ) )
			$default = $rtmedia_media->privacy;
		else {
			$default = get_user_meta( get_current_user_id(), 'rtmedia-default-privacy', true );

			if ( ( $default === false ) || $default === '' ) {
				$default = get_rtmedia_default_privacy();
			}
		}


		$form = new rtForm();
		$attributes = array(
			'name' => 'privacy',
			'class' => array( 'privacy' )
		);
		if ( $select_id && $select_id != "" ) {
			$attributes[ 'id' ] = $select_id;
		}
		global $rtmedia;
		$privacy_levels = $rtmedia->privacy_settings[ 'levels' ];
		if ( class_exists( 'BuddyPress' ) ) {
			if ( ! bp_is_active( 'friends' ) ) {
				unset( $privacy_levels[ 40 ] );
			}
		} else {
			unset( $privacy_levels[ 40 ] );
		}
		foreach ( $privacy_levels as $key => $value ) {
			$privacy = explode( ' - ', $value );
			$attributes[ 'rtForm_options' ][] = array(
				$privacy[ 0 ] => $key,
				'selected' => ($key == $default) ? 1 : 0
			);
		}

		if ( $echo )
			echo $form->get_select( $attributes );
		else
			return $form->get_select( $attributes );
	}

	public
			function system_default() {
		return 0;
	}

	public
			function site_default() {
		global $rtmedia;

		return rtmedia_get_site_option( 'privacy_settings' );
	}

	public
			function user_default() {
		return;
	}

	public
			function get_default() {
		$default_privacy = $this->user_default();

		if ( $default_privacy === false ) {
			$default_privacy = $this->site_default();
		}

		if ( ! $default_privacy === false ) {
			$default_privacy = $this->system_default();
		}
	}

	static
			function is_enabled() {
		global $bp_media;
		$options = $bp_media->options;
		if ( ! array_key_exists( 'privacy_enabled', $options ) ) {
			return false;
		} else {
			if ( $options[ 'privacy_enabled' ] != true ) {
				return false;
			}
		}
		return true;
	}

	static
			function save_user_default( $level = 0, $user_id = false ) {
		if ( $user_id == false ) {
			global $bp;
			$user_id = $bp->loggedin_user->id;
		}
		return update_user_meta( $user_id, 'bp_media_privacy', $level );
	}

	static function get_user_default( $user_id = false ) {
		if ( $user_id == false ) {
			global $bp;
			$user_id = $bp->loggedin_user->id;
		}
		$user_privacy = get_user_meta( $user_id, 'bp_media_privacy', true );
		if ( $user_privacy === false ) {

		}
		return $user_privacy;
	}

	static
			function required_access( $object_id = false ) {
		if ( BPMediaPrivacy::is_enabled() == false )
			return;
		if ( $object_id == false )
			return;
		$privacy = BPMediaPrivacy::get_privacy( $object_id );
		$parent = get_post_field( 'post_parent', $object_id, 'raw' );
		$parent_privacy = BPMediaPrivacy::get_privacy( $parent );

		if ( $privacy === false ) {
			if ( $parent_privacy !== false ) {
				$privacy = $parent_privacy;
			} else {
				$privacy = BPMediaPrivacy::default_privacy();
			}
		}
		return $privacy;
	}

	function add_nav() {

		if ( bp_displayed_user_domain() ) {
			$user_domain = bp_displayed_user_domain();
		} elseif ( bp_loggedin_user_domain() ) {
			$user_domain = bp_loggedin_user_domain();
		} else {
			return;
		}

		if ( ! is_rtmedia_profile_media_enable() ) {
			return;
		}
		if ( ! is_rtmedia_privacy_enable() ) {
			return;
		}
		if ( ! is_rtmedia_privacy_user_overide() ) {
			return;
		}

		$settings_link = trailingslashit( $user_domain . 'settings' );

		$defaults = array(
			'name' => $this->title(), // Display name for the nav item
			'slug' => 'privacy', // URL slug for the nav item
			'parent_slug' => 'settings', // URL slug of the parent nav item
			'parent_url' => $settings_link, // URL of the parent item
			'item_css_id' => 'rtmedia-privacy-settings', // The CSS ID to apply to the HTML of the nav item
			'user_has_access' => true, // Can the logged in user see this nav item?
			'site_admin_only' => false, // Can only site admins see this nav item?
			'position' => 80, // Index of where this nav item should be positioned
			'screen_function' => array( $this, 'settings_ui' ), // The name of the function to run when clicked
			'link' => ''  // The link for the subnav item; optional, not usually required.
		);
		bp_core_new_subnav_item( $defaults );
	}

	function settings_ui() {
		if ( bp_action_variables() ) {
			bp_do_404();
			return;
		}


		// Load the template
		bp_core_load_template( apply_filters( 'bp_settings_screen_delete_account', 'members/single/plugins' ) );
	}

	/**
	 * changing and saving of privacy setting save action
	 */
	function rt_privacy_settings_action() {
		if ( buddypress()->current_action != 'privacy' )
			return;

		if ( isset( $_POST[ "rtmedia-default-privacy" ] ) ) {
			$status=false;
			if ( wp_verify_nonce( $_POST['rtmedia_member_settings_privacy'],'rtmedia_member_settings_privacy' ) ) {
				$status = update_user_meta(get_current_user_id(), 'rtmedia-default-privacy', $_POST["rtmedia-default-privacy"]);
			}
			if(false == $status ) {
				$feedback = __( 'No changes were made to your account.', 'buddypress-media' );
				$feedback_type = 'error';
			} else if ( true == $status ) {
				$feedback = __( 'Your default privacy settings saved successfully.', 'buddypress-media' );
				$feedback_type = 'success';
			}
			bp_core_add_message( $feedback, $feedback_type );
			do_action( 'bp_core_general_settings_after_save' );
			bp_core_redirect( bp_displayed_user_domain() . bp_get_settings_slug() . '/privacy/' );
		}
	}

	function content() {
		if ( buddypress()->current_action != 'privacy' )
			return;

		$default_privacy = get_user_meta( get_current_user_id(), 'rtmedia-default-privacy', true );
		if ( $default_privacy === false || $default_privacy === '' ) {
			$default_privacy = get_rtmedia_default_privacy();
		}
		global $rtmedia;
		?>
		<form method="post">
			<div class="rtm_bp_default_privacy">
				<?php wp_nonce_field( 'rtmedia_member_settings_privacy', 'rtmedia_member_settings_privacy' ); ?>
				<div class="section">
					<div class="rtm-title"><h3><?php _e( 'Default Privacy', 'buddypress-media' ); ?></h3></div>
					<div class="rtm-privacy-levels">
						<?php foreach ( $rtmedia->privacy_settings[ 'levels' ] as $level => $data ) { ?>
							<label><input type='radio' value='<?php echo $level; ?>' name ='rtmedia-default-privacy' <?php echo ($default_privacy == $level) ? "checked" : ""; ?> /> <?php echo $data; ?></label><br/>
						<?php } ?>
					</div>
				</div>
			</div>
			<div class="submit">
				<input type="submit" name="submit" value="<?php esc_attr_e( 'Save Changes', 'buddypress-media' ); ?>" id="submit" class="auto">
			</div>
		</form>
		<?php
	}

	function title() {
		return __( 'Privacy', 'buddypress-media' );
	}

	function activity_privacy( $sql, $select_sql, $from_sql, $where_sql, $sort, $pag_sql = '' ) {
		if ( is_rt_admin() )
			return $sql;

		$sql = '';
		$where = '';
		global $bp, $wpdb;
		$rtmedia_model = new RTMediaModel();

		if ( is_user_logged_in() ) {
			$user = get_current_user_id();
		} else {
			$user = 0;
		}

		$activity_upgrade_done = rtmedia_get_site_option( 'rtmedia_activity_done_upgrade' );

		// admin has upgraded rtmedia activity so we can use rt_rtm_activity table for rtmedia related activity filters
		if ( $activity_upgrade_done ) {
			$rtmedia_activity_model = new RTMediaActivityModel();
			$where .= " (ra.privacy is NULL OR ra.privacy <= 0) ";
			if ( $user ) {
				$where .= "OR ((ra.privacy=20)";
				$where .= " OR (a.user_id={$user} AND ra.privacy >= 40)";
				if ( class_exists( 'BuddyPress' ) ) {
					if ( bp_is_active( 'friends' ) ) {
						$friendship = new RTMediaFriends();
						$friends = $friendship->get_friends_cache( $user );
						if ( isset( $friends ) && ! empty( $friends ) != "" ) {
							$where .= " OR (ra.privacy=40 AND a.user_id IN ('" . implode( "','", $friends ) . "'))";
						}
					}
				}
				$where .= ')';
			}
			if ( function_exists( "bp_core_get_table_prefix" ) ) {
				$bp_prefix = bp_core_get_table_prefix();
			} else {
				$bp_prefix = "";
			}
			if ( strpos( $select_sql, "SELECT DISTINCT" ) === false ) {
				$select_sql = str_replace( "SELECT", "SELECT DISTINCT", $select_sql );
			}
			$from_sql = " FROM {$bp->activity->table_name} a LEFT JOIN {$wpdb->users} u ON a.user_id = u.ID LEFT JOIN {$rtmedia_model->table_name} m ON ( a.id = m.activity_id AND m.blog_id = '" . get_current_blog_id() . "' ) LEFT JOIN {$rtmedia_activity_model->table_name} ra ON ( a.id = ra.activity_id and ra.blog_id = '" . get_current_blog_id() . "' ) ";
			$where_sql = $where_sql . " AND (NOT EXISTS (SELECT m.activity_id FROM {$bp_prefix}bp_activity_meta m WHERE m.meta_key='rtmedia_privacy' AND m.activity_id=a.id) OR ( {$where} ) )";
			$newsql = "{$select_sql} {$from_sql} {$where_sql} ORDER BY a.date_recorded {$sort} {$pag_sql}";
		} else {
			$where .= " (m.max_privacy is NULL OR m.max_privacy <= 0) ";
			if ( $user ) {
				$where .= "OR ((m.max_privacy=20)";
				$where .= " OR (a.user_id={$user} AND m.max_privacy >= 40)";
				if ( class_exists( 'BuddyPress' ) ) {
					if ( bp_is_active( 'friends' ) ) {
						$friendship = new RTMediaFriends();
						$friends = $friendship->get_friends_cache( $user );
						if ( isset( $friends ) && ! empty( $friends ) != "" ) {
							$where .= " OR (m.max_privacy=40 AND a.user_id IN ('" . implode( "','", $friends ) . "'))";
						}
					}
				}
				$where .= ')';
			}
			if ( function_exists( "bp_core_get_table_prefix" ) ) {
				$bp_prefix = bp_core_get_table_prefix();
			} else {
				$bp_prefix = "";
			}
			if ( strpos( $select_sql, "SELECT DISTINCT" ) === false ) {
				$select_sql = str_replace( "SELECT", "SELECT DISTINCT", $select_sql );
			}
			$media_table = "SELECT *, max( privacy ) as max_privacy from {$rtmedia_model->table_name} group by activity_id";
			$from_sql = " FROM {$bp->activity->table_name} a LEFT JOIN {$wpdb->users} u ON a.user_id = u.ID LEFT JOIN ( $media_table ) m ON ( a.id = m.activity_id AND m.blog_id = '" . get_current_blog_id() . "' ) ";
			$where_sql = $where_sql . " AND (NOT EXISTS (SELECT m.activity_id FROM {$bp_prefix}bp_activity_meta m WHERE m.meta_key='rtmedia_privacy' AND m.activity_id=a.id) OR ( {$where} ) )";
			$newsql = "{$select_sql} {$from_sql} {$where_sql} ORDER BY a.date_recorded {$sort} {$pag_sql}";
		}
		return $newsql;
	}

}

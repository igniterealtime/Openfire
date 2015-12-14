<?php
/**
 * Signups Management class.
 *
 * @since 2.0.0
 *
 * @package BuddyPress
 * @subpackage coreClasses
 */

/**
 * Class used to handle Signups.
 */
class BP_Signup {

	/**
	 * ID of the signup which the object relates to.
	 *
	 * @var integer
	 */
	public $id;

	/**
	 * The URL to the full size of the avatar for the user.
	 *
	 * @var string
	 */
	public $avatar;

	/**
	 * The username for the user.
	 *
	 * @var string
	 */
	public $user_login;

	/**
	 * The email for the user.
	 *
	 * @var string
	 */
	public $user_email;

	/**
	 * The full name of the user.
	 *
	 * @var string
	 */
	public $user_name;

	/**
	 * Metadata associated with the signup.
	 *
	 * @var array
	 */
	public $meta;

	/**
	 * The registered date for the user.
	 *
	 * @var string
	 */
	public $registered;

	/**
	 * The activation key for the user.
	 *
	 * @var string
	 */
	public $activation_key;


	/** Public Methods *******************************************************/

	/**
	 * Class constructor.
	 *
	 * @since 2.0.0
	 *
	 * @param integer $signup_id The ID for the signup being queried.
	 */
	public function __construct( $signup_id = 0 ) {
		if ( !empty( $signup_id ) ) {
			$this->id = $signup_id;
			$this->populate();
		}
	}

	/**
	 * Populate the instantiated class with data based on the signup_id provided.
	 *
	 * @since 2.0.0
	 */
	public function populate() {
		global $wpdb;

		$signups_table = buddypress()->members->table_name_signups;
		$signup        = $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$signups_table} WHERE signup_id = %d AND active = 0", $this->id ) );

		$this->avatar         = get_avatar( $signup->user_email, 32 );
		$this->user_login     = $signup->user_login;
		$this->user_email     = $signup->user_email;
		$this->meta           = maybe_unserialize( $signup->meta );
		$this->user_name      = ! empty( $this->meta['field_1'] ) ? wp_unslash( $this->meta['field_1'] ) : '';
		$this->registered     = $signup->registered;
		$this->activation_key = $signup->activation_key;
	}

	/** Static Methods *******************************************************/

	/**
	 * Fetch signups based on parameters.
	 *
	 * @since 2.0.0
	 *
	 * @param array $args the argument to retrieve desired signups.
	 * @return array {
	 *     @type array $signups Located signups.
	 *     @type int   $total   Total number of signups matching params.
	 * }
	 */
	public static function get( $args = array() ) {
		global $wpdb;

		$r = bp_parse_args( $args,
			array(
				'offset'         => 0,
				'number'         => 1,
				'usersearch'     => false,
				'orderby'        => 'signup_id',
				'order'          => 'DESC',
				'include'        => false,
				'activation_key' => '',
				'user_login'     => '',
			),
			'bp_core_signups_get_args'
		);

		// @todo whitelist sanitization
		if ( $r['orderby'] !== 'signup_id' ) {
			$r['orderby'] = 'user_' . $r['orderby'];
		}

		$r['orderby'] = sanitize_title( $r['orderby'] );

		$sql = array();
		$signups_table  = buddypress()->members->table_name_signups;
		$sql['select']  = "SELECT * FROM {$signups_table}";
		$sql['where']   = array();
		$sql['where'][] = "active = 0";

		if ( empty( $r['include'] ) ) {

			// Search terms.
			if ( ! empty( $r['usersearch'] ) ) {
				$search_terms_like = '%' . bp_esc_like( $r['usersearch'] ) . '%';
				$sql['where'][]    = $wpdb->prepare( "( user_login LIKE %s OR user_email LIKE %s OR meta LIKE %s )", $search_terms_like, $search_terms_like, $search_terms_like );
			}

			// Activation key.
			if ( ! empty( $r['activation_key'] ) ) {
				$sql['where'][] = $wpdb->prepare( "activation_key = %s", $r['activation_key'] );
			}

			// User login.
			if ( ! empty( $r['user_login'] ) ) {
				$sql['where'][] = $wpdb->prepare( "user_login = %s", $r['user_login'] );
			}

			$sql['orderby'] = "ORDER BY {$r['orderby']}";
			$sql['order']	= bp_esc_sql_order( $r['order'] );
			$sql['limit']	= $wpdb->prepare( "LIMIT %d, %d", $r['offset'], $r['number'] );
		} else {
			$in = implode( ',', wp_parse_id_list( $r['include'] ) );
			$sql['in'] = "AND signup_id IN ({$in})";
		}

		// Implode WHERE clauses.
		$sql['where'] = 'WHERE ' . implode( ' AND ', $sql['where'] );

		/**
		 * Filters the Signups paged query.
		 *
		 * @since 2.0.0
		 *
		 * @param string $value SQL statement.
		 * @param array  $sql   Array of SQL statement parts.
		 * @param array  $args  Array of original arguments for get() method.
		 * @param array  $r     Array of parsed arguments for get() method.
		 */
		$paged_signups = $wpdb->get_results( apply_filters( 'bp_members_signups_paged_query', join( ' ', $sql ), $sql, $args, $r ) );

		if ( empty( $paged_signups ) ) {
			return array( 'signups' => false, 'total' => false );
		}

		// Used to calculate a diff between now & last
		// time an activation link has been resent.
		$now = current_time( 'timestamp', true );

		foreach ( (array) $paged_signups as $key => $signup ) {

			$signup->id   = intval( $signup->signup_id );

			$signup->meta = ! empty( $signup->meta ) ? maybe_unserialize( $signup->meta ) : false;

			$signup->user_name = '';
			if ( ! empty( $signup->meta['field_1'] ) ) {
				$signup->user_name = wp_unslash( $signup->meta['field_1'] );
			}

			// Sent date defaults to date of registration.
			if ( ! empty( $signup->meta['sent_date'] ) ) {
				$signup->date_sent = $signup->meta['sent_date'];
			} else {
				$signup->date_sent = $signup->registered;
			}

			$sent_at = mysql2date('U', $signup->date_sent );
			$diff    = $now - $sent_at;

			/**
			 * Add a boolean in case the last time an activation link
			 * has been sent happened less than a day ago.
			 */
			if ( $diff < 1 * DAY_IN_SECONDS ) {
				$signup->recently_sent = true;
			}

			if ( ! empty( $signup->meta['count_sent'] ) ) {
				$signup->count_sent = absint( $signup->meta['count_sent'] );
			} else {
				$signup->count_sent = 1;
			}

			$paged_signups[ $key ] = $signup;
		}

		unset( $sql['limit'] );
		$sql['select'] = preg_replace( "/SELECT.*?FROM/", "SELECT COUNT(*) FROM", $sql['select'] );

		/**
		 * Filters the Signups count query.
		 *
		 * @since 2.0.0
		 *
		 * @param string $value SQL statement.
		 * @param array  $sql   Array of SQL statement parts.
		 * @param array  $args  Array of original arguments for get() method.
		 * @param array  $r     Array of parsed arguments for get() method.
		 */
		$total_signups = $wpdb->get_var( apply_filters( 'bp_members_signups_count_query', join( ' ', $sql ), $sql, $args, $r ) );

		return array( 'signups' => $paged_signups, 'total' => $total_signups );
	}

	/**
	 * Add a signup.
	 *
	 * @since 2.0.0
	 *
	 * @param array $args Array of arguments for signup addition.
	 * @return int|bool ID of newly created signup on success, false on
	 *                  failure.
	 */
	public static function add( $args = array() ) {
		global $wpdb;

		$r = bp_parse_args( $args,
			array(
				'domain'         => '',
				'path'           => '',
				'title'          => '',
				'user_login'     => '',
				'user_email'     => '',
				'registered'     => current_time( 'mysql', true ),
				'activation_key' => '',
				'meta'           => '',
			),
			'bp_core_signups_add_args'
		);

		$r['meta'] = maybe_serialize( $r['meta'] );

		$inserted = $wpdb->insert(
			buddypress()->members->table_name_signups,
			$r,
			array( '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s' )
		);

		if ( $inserted ) {
			$retval = $wpdb->insert_id;
		} else {
			$retval = false;
		}

		/**
		 * Filters the result of a signup addition.
		 *
		 * @since 2.0.0
		 *
		 * @param int|bool $retval Newly added user ID on success, false on failure.
		 */
		return apply_filters( 'bp_core_signups_add', $retval );
	}

	/**
	 * Create a WP user at signup.
	 *
	 * Since BP 2.0, non-multisite configurations have stored signups in
	 * the same way as Multisite configs traditionally have: in the
	 * wp_signups table. However, because some plugins may be looking
	 * directly in the wp_users table for non-activated signups, we
	 * mirror signups there by creating "phantom" users, mimicking WP's
	 * default behavior.
	 *
	 * @since 2.0.0
	 *
	 * @param string $user_login    User login string.
	 * @param string $user_password User password.
	 * @param string $user_email    User email address.
	 * @param array  $usermeta      Metadata associated with the signup.
	 * @return int User id.
	 */
	public static function add_backcompat( $user_login = '', $user_password = '', $user_email = '', $usermeta = array() ) {
		global $wpdb;

		$user_id = wp_insert_user( array(
			'user_login'   => $user_login,
			'user_pass'    => $user_password,
			'display_name' => sanitize_title( $user_login ),
			'user_email'   => $user_email
		) );

		if ( is_wp_error( $user_id ) || empty( $user_id ) ) {
			return $user_id;
		}

		// Update the user status to '2', ie "not activated"
		// (0 = active, 1 = spam, 2 = not active).
		$wpdb->query( $wpdb->prepare( "UPDATE {$wpdb->users} SET user_status = 2 WHERE ID = %d", $user_id ) );

		// WordPress creates these options automatically on
		// wp_insert_user(), but we delete them so that inactive
		// signups don't appear in various user counts.
		delete_user_option( $user_id, 'capabilities' );
		delete_user_option( $user_id, 'user_level'   );

		// Set any profile data.
		if ( bp_is_active( 'xprofile' ) ) {
			if ( ! empty( $usermeta['profile_field_ids'] ) ) {
				$profile_field_ids = explode( ',', $usermeta['profile_field_ids'] );

				foreach ( (array) $profile_field_ids as $field_id ) {
					if ( empty( $usermeta["field_{$field_id}"] ) ) {
						continue;
					}

					$current_field = $usermeta["field_{$field_id}"];
					xprofile_set_field_data( $field_id, $user_id, $current_field );

					// Save the visibility level.
					$visibility_level = ! empty( $usermeta['field_' . $field_id . '_visibility'] ) ? $usermeta['field_' . $field_id . '_visibility'] : 'public';
					xprofile_set_field_visibility_level( $field_id, $user_id, $visibility_level );
				}
			}
		}

		/**
		 * Filters the user ID for the backcompat functionality.
		 *
		 * @since 2.0.0
		 *
		 * @param int $user_id User ID being registered.
		 */
		return apply_filters( 'bp_core_signups_add_backcompat', $user_id );
	}

	/**
	 * Check a user status (from wp_users) on a non-multisite config.
	 *
	 * @since 2.0.0
	 *
	 * @param int $user_id ID of the user being checked.
	 * @return int|bool The status if found, otherwise false.
	 */
	public static function check_user_status( $user_id = 0 ) {
		global $wpdb;

		if ( empty( $user_id ) ) {
			return false;
		}

		$user_status = $wpdb->get_var( $wpdb->prepare( "SELECT user_status FROM {$wpdb->users} WHERE ID = %d", $user_id ) );

		/**
		 * Filters the user status of a provided user ID.
		 *
		 * @since 2.0.0
		 *
		 * @param int $value User status of the provided user ID.
		 */
		return apply_filters( 'bp_core_signups_check_user_status', intval( $user_status ) );
	}

	/**
	 * Activate a signup.
	 *
	 * @since 2.0.0
	 *
	 * @param string $key Activation key.
	 * @return bool True on success, false on failure.
	 */
	public static function validate( $key = '' ) {
		global $wpdb;

		if ( empty( $key ) ) {
			return;
		}

		$activated = $wpdb->update(
			// Signups table.
			buddypress()->members->table_name_signups,
			array(
				'active' => 1,
				'activated' => current_time( 'mysql', true ),
			),
			array(
				'activation_key' => $key,
			),
			// Data sanitization format.
			array(
				'%d',
				'%s',
			),
			// WHERE sanitization format.
			array(
				'%s',
			)
		);

		/**
		 * Filters the status of the activated user.
		 *
		 * @since 2.0.0
		 *
		 * @param bool $activated Whether or not the activation was successful.
		 */
		return apply_filters( 'bp_core_signups_validate', $activated );
	}

	/**
	 * How many inactive signups do we have?
	 *
	 * @since 2.0.0
	 *
	 * @return int The number of signups.
	 */
	public static function count_signups() {
		global $wpdb;

		$signups_table = buddypress()->members->table_name_signups;
		$count_signups = $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(*) AS total FROM {$signups_table} WHERE active = %d", 0 ) );

		/**
		 * Filters the total inactive signups.
		 *
		 * @since 2.0.0
		 *
		 * @param int $count_signups How many total signups there are.
		 */
		return apply_filters( 'bp_core_signups_count', (int) $count_signups );
	}

	/**
	 * Update the meta for a signup.
	 *
	 * This is the way we use to "trace" the last date an activation
	 * email was sent and how many times activation was sent.
	 *
	 * @since 2.0.0
	 *
	 * @param array $args Array of arguments for the signup update.
	 * @return int The signup id.
	 */
	public static function update( $args = array() ) {
		global $wpdb;

		$r = bp_parse_args( $args,
			array(
				'signup_id'  => 0,
				'meta'       => array(),
			),
			'bp_core_signups_update_args'
		);

		if ( empty( $r['signup_id'] ) || empty( $r['meta'] ) ) {
			return false;
		}

		$wpdb->update(
			// Signups table.
			buddypress()->members->table_name_signups,
			// Data to update.
			array(
				'meta' => serialize( $r['meta'] ),
			),
			// WHERE.
			array(
				'signup_id' => $r['signup_id'],
			),
			// Data sanitization format.
			array(
				'%s',
			),
			// WHERE sanitization format.
			array(
				'%d',
			)
		);

		/**
		 * Filters the signup ID which received a meta update.
		 *
		 * @since 2.0.0
		 *
		 * @param int $value The signup ID.
		 */
		return apply_filters( 'bp_core_signups_update', $r['signup_id'] );
	}

	/**
	 * Resend an activation email.
	 *
	 * @since 2.0.0
	 *
	 * @param array $signup_ids Single ID or list of IDs to resend.
	 * @return array
	 */
	public static function resend( $signup_ids = array() ) {
		if ( empty( $signup_ids ) || ! is_array( $signup_ids ) ) {
			return false;
		}

		$to_resend = self::get( array(
			'include' => $signup_ids,
		) );

		if ( ! $signups = $to_resend['signups'] ) {
			return false;
		}

		$result = array();

		/**
		 * Fires before activation emails are resent.
		 *
		 * @since 2.0.0
		 *
		 * @param array $signup_ids Array of IDs to resend activation emails to.
		 */
		do_action( 'bp_core_signup_before_resend', $signup_ids );

		foreach ( $signups as $signup ) {

			$meta               = $signup->meta;
			$meta['sent_date']  = current_time( 'mysql', true );
			$meta['count_sent'] = $signup->count_sent + 1;

			// Send activation email.
			if ( is_multisite() ) {
				wpmu_signup_user_notification( $signup->user_login, $signup->user_email, $signup->activation_key, serialize( $meta ) );
			} else {

				// Check user status before sending email.
				$user_id = email_exists( $signup->user_email );

				if ( ! empty( $user_id ) && 2 != self::check_user_status( $user_id ) ) {

					// Status is not 2, so user's account has been activated.
					$result['errors'][ $signup->signup_id ] = array( $signup->user_login, esc_html__( 'the sign-up has already been activated.', 'buddypress' ) );

					// Repair signups table.
					self::validate( $signup->activation_key );

					continue;

				// Send the validation email.
				} else {
					bp_core_signup_send_validation_email( false, $signup->user_email, $signup->activation_key );
				}
			}

			// Update metas.
			$result['resent'][] = self::update( array(
				'signup_id' => $signup->signup_id,
				'meta'      => $meta,
			) );
		}

		/**
		 * Fires after activation emails are resent.
		 *
		 * @since 2.0.0
		 *
		 * @param array $signup_ids Array of IDs to resend activation emails to.
		 * @param array $result     Updated metadata related to activation emails.
		 */
		do_action( 'bp_core_signup_after_resend', $signup_ids, $result );

		/**
		 * Filters the result of the metadata for signup activation email resends.
		 *
		 * @since 2.0.0
		 *
		 * @param array $result Updated metadata related to activation emails.
		 */
		return apply_filters( 'bp_core_signup_resend', $result );
	}

	/**
	 * Activate a pending account.
	 *
	 * @since 2.0.0
	 *
	 * @param array $signup_ids Single ID or list of IDs to activate.
	 * @return array
	 */
	public static function activate( $signup_ids = array() ) {
		if ( empty( $signup_ids ) || ! is_array( $signup_ids ) ) {
			return false;
		}

		$to_activate = self::get( array(
			'include' => $signup_ids,
		) );

		if ( ! $signups = $to_activate['signups'] ) {
			return false;
		}

		$result = array();

		/**
		 * Fires before activation of user accounts.
		 *
		 * @since 2.0.0
		 *
		 * @param array $signup_ids Array of IDs to activate.
		 */
		do_action( 'bp_core_signup_before_activate', $signup_ids );

		foreach ( $signups as $signup ) {

			$user = bp_core_activate_signup( $signup->activation_key );

			if ( ! empty( $user->errors ) ) {

				$user_id = username_exists( $signup->user_login );

				if ( 2 !== self::check_user_status( $user_id ) ) {
					$user_id = false;
				}

				if ( empty( $user_id ) ) {

					// Status is not 2, so user's account has been activated.
					$result['errors'][ $signup->signup_id ] = array( $signup->user_login, esc_html__( 'the sign-up has already been activated.', 'buddypress' ) );

					// Repair signups table.
					self::validate( $signup->activation_key );

				// We have a user id, account is not active, let's delete it.
				} else {
					$result['errors'][ $signup->signup_id ] = array( $signup->user_login, $user->get_error_message() );
				}

			} else {
				$result['activated'][] = $user;
			}
		}

		/**
		 * Fires after activation of user accounts.
		 *
		 * @since 2.0.0
		 *
		 * @param array $signup_ids Array of IDs activated activate.
		 * @param array $result     Array of data for activated accounts.
		 */
		do_action( 'bp_core_signup_after_activate', $signup_ids, $result );

		/**
		 * Filters the result of the metadata after user activation.
		 *
		 * @since 2.0.0
		 *
		 * @param array $result Updated metadata related to user activation.
		 */
		return apply_filters( 'bp_core_signup_activate', $result );
	}

	/**
	 * Delete a pending account.
	 *
	 * @since 2.0.0
	 *
	 * @param array $signup_ids Single ID or list of IDs to delete.
	 * @return array
	 */
	public static function delete( $signup_ids = array() ) {
		global $wpdb;

		if ( empty( $signup_ids ) || ! is_array( $signup_ids ) ) {
			return false;
		}

		$to_delete = self::get( array(
			'include' => $signup_ids,
		) );

		if ( ! $signups = $to_delete['signups'] ) {
			return false;
		}

		$result = array();

		/**
		 * Fires before deletion of pending accounts.
		 *
		 * @since 2.0.0
		 *
		 * @param array $signup_ids Array of pending IDs to delete.
		 */
		do_action( 'bp_core_signup_before_delete', $signup_ids );

		foreach ( $signups as $signup ) {
			$user_id = username_exists( $signup->user_login );

			if ( ! empty( $user_id ) && $signup->activation_key == wp_hash( $user_id ) ) {

				if ( 2 != self::check_user_status( $user_id ) ) {

					// Status is not 2, so user's account has been activated.
					$result['errors'][ $signup->signup_id ] = array( $signup->user_login, esc_html__( 'the sign-up has already been activated.', 'buddypress' ) );

					// Repair signups table.
					self::validate( $signup->activation_key );

				// We have a user id, account is not active, let's delete it.
				} else {
					bp_core_delete_account( $user_id );
				}
			}

			if ( empty( $result['errors'][ $signup->signup_id ] ) ) {
				$wpdb->delete(
					// Signups table.
					buddypress()->members->table_name_signups,
					// Where.
					array( 'signup_id' => $signup->signup_id, ),
					// WHERE sanitization format.
					array( '%d', )
				);

				$result['deleted'][] = $signup->signup_id;
			}
		}

		/**
		 * Fires after deletion of pending accounts.
		 *
		 * @since 2.0.0
		 *
		 * @param array $signup_ids Array of pending IDs to delete.
		 * @param array $result     Array of data for deleted accounts.
		 */
		do_action( 'bp_core_signup_after_delete', $signup_ids, $result );

		/**
		 * Filters the result of the metadata for deleted pending accounts.
		 *
		 * @since 2.0.0
		 *
		 * @param array $result Updated metadata related to deleted pending accounts.
		 */
		return apply_filters( 'bp_core_signup_delete', $result );
	}
}

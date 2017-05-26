<?php
// Last sync [WP11537]

/**
 * BackPress User class.
 *
 * @since 2.0.0
 * @package BackPress
 * @subpackage User
 */
class BP_User {
	/**
	 * User data container.
	 *
	 * This will be set as properties of the object.
	 *
	 * @since 2.0.0
	 * @access private
	 * @var array
	 */
	var $data;

	/**
	 * The user's ID.
	 *
	 * @since 2.1.0
	 * @access public
	 * @var int
	 */
	var $ID = 0;

	/**
	 * The deprecated user's ID.
	 *
	 * @since 2.0.0
	 * @access public
	 * @deprecated Use BP_User::$ID
	 * @see BP_User::$ID
	 * @var int
	 */
	var $id = 0;

	/**
	 * The individual capabilities the user has been given.
	 *
	 * @since 2.0.0
	 * @access public
	 * @var array
	 */
	var $caps = array();

	/**
	 * User metadata option name.
	 *
	 * @since 2.0.0
	 * @access public
	 * @var string
	 */
	var $cap_key;

	/**
	 * The roles the user is part of.
	 *
	 * @since 2.0.0
	 * @access public
	 * @var array
	 */
	var $roles = array();

	/**
	 * All capabilities the user has, including individual and role based.
	 *
	 * @since 2.0.0
	 * @access public
	 * @var array
	 */
	var $allcaps = array();

	/**
	 * First name of the user.
	 *
	 * Created to prevent notices.
	 *
	 * @since 2.7.0
	 * @access public
	 * @var string
	 */
	var $first_name = '';

	/**
	 * Last name of the user.
	 *
	 * Created to prevent notices.
	 *
	 * @since 2.7.0
	 * @access public
	 * @var string
	 */
	var $last_name = '';

	/**
	 * PHP4 Constructor - Sets up the object properties.
	 *
	 * Retrieves the userdata and then assigns all of the data keys to direct
	 * properties of the object. Calls {@link BP_User::_init_caps()} after
	 * setting up the object's user data properties.
	 *
	 * @since 2.0.0
	 * @access public
	 *
	 * @param int|string $id User's ID or username
	 * @param int $name Optional. User's username
	 * @return BP_User
	 */
	function BP_User( $id, $name = '' ) {
		global $wp_users_object;

		if ( empty( $id ) && empty( $name ) )
			return;

		if ( ! is_numeric( $id ) ) {
			$name = $id;
			$id = 0;
		}

		if ( ! empty( $id ) )
			$this->data = $wp_users_object->get_user( $id );
		else
			$this->data = $wp_users_object->get_user( $name, array( 'by' => 'login' ) );

		if ( empty( $this->data->ID ) )
			return;

		foreach ( get_object_vars( $this->data ) as $key => $value ) {
			$this->{$key} = $value;
		}

		$this->id = $this->ID;
		$this->_init_caps();
	}

	/**
	 * Setup capability object properties.
	 *
	 * Will set the value for the 'cap_key' property to current database table
	 * prefix, followed by 'capabilities'. Will then check to see if the
	 * property matching the 'cap_key' exists and is an array. If so, it will be
	 * used.
	 *
	 * @since 2.1.0
	 * @access protected
	 */
	function _init_caps() {
		global $wp_users_object;
		$this->cap_key = $wp_users_object->db->prefix . 'capabilities';
		$this->caps = &$this->{$this->cap_key};
		if ( ! is_array( $this->caps ) )
			$this->caps = array();
		$this->get_role_caps();
	}

	/**
	 * Retrieve all of the role capabilities and merge with individual capabilities.
	 *
	 * All of the capabilities of the roles the user belongs to are merged with
	 * the users individual roles. This also means that the user can be denied
	 * specific roles that their role might have, but the specific user isn't
	 * granted permission to.
	 *
	 * @since 2.0.0
	 * @uses $wp_roles
	 * @access public
	 */
	function get_role_caps() {
		global $wp_roles, $wp_users_object;

		if ( ! isset( $wp_roles ) )
			$wp_roles = new BP_Roles( $wp_users_object->db );

		//Filter out caps that are not role names and assign to $this->roles
		if ( is_array( $this->caps ) )
			$this->roles = array_filter( array_keys( $this->caps ), array( &$wp_roles, 'is_role' ) );

		//Build $allcaps from role caps, overlay user's $caps
		$this->allcaps = array();
		foreach ( (array) $this->roles as $role ) {
			$role = $wp_roles->get_role( $role );
			$this->allcaps = array_merge( (array) $this->allcaps, (array) $role->capabilities );
		}
		$this->allcaps = array_merge( (array) $this->allcaps, (array) $this->caps );
	}

	/**
	 * Add role to user.
	 *
	 * Updates the user's meta data option with capabilities and roles.
	 *
	 * @since 2.0.0
	 * @access public
	 *
	 * @param string $role Role name.
	 */
	function add_role( $role ) {
		$this->caps[$role] = true;
		$this->update_user();
	}

	/**
	 * Remove role from user.
	 *
	 * @since 2.0.0
	 * @access public
	 *
	 * @param string $role Role name.
	 */
	function remove_role( $role ) {
		if ( empty( $this->caps[$role] ) || ( count( $this->caps ) <= 1 ) )
			return;
		unset( $this->caps[$role] );
		$this->update_user();
	}

	/**
	 * Set the role of the user.
	 *
	 * This will remove the previous roles of the user and assign the user the
	 * new one. You can set the role to an empty string and it will remove all
	 * of the roles from the user.
	 *
	 * @since 2.0.0
	 * @access public
	 *
	 * @param string $role Role name.
	 */
	function set_role( $role ) {
		foreach ( (array) $this->roles as $oldrole )
			unset( $this->caps[$oldrole] );
		if ( !empty( $role ) ) {
			$this->caps[$role] = true;
			$this->roles = array( $role => true );
		} else {
			$this->roles = false;
		}
		$this->update_user();
	}

	function update_user() {
		global $wp_users_object;
		$wp_users_object->update_meta( array( 'id' => $this->ID, 'meta_key' => $this->cap_key, 'meta_value' => $this->caps ) );
		$this->get_role_caps();
		//$this->update_user_level_from_caps(); // WP
	}

	/**
	 * Choose the maximum level the user has.
	 *
	 * Will compare the level from the $item parameter against the $max
	 * parameter. If the item is incorrect, then just the $max parameter value
	 * will be returned.
	 *
	 * Used to get the max level based on the capabilities the user has. This
	 * is also based on roles, so if the user is assigned the Administrator role
	 * then the capability 'level_10' will exist and the user will get that
	 * value.
	 *
	 * @since 2.0.0
	 * @access public
	 *
	 * @param int $max Max level of user.
	 * @param string $item Level capability name.
	 * @return int Max Level.
	 */
/*
	function level_reduction( $max, $item ) {
		if ( preg_match( '/^level_(10|[0-9])$/i', $item, $matches ) ) {
			$level = intval( $matches[1] );
			return max( $max, $level );
		} else {
			return $max;
		}
	}
*/

	/**
	 * Update the maximum user level for the user.
	 *
	 * Updates the 'user_level' user metadata (includes prefix that is the
	 * database table prefix) with the maximum user level. Gets the value from
	 * the all of the capabilities that the user has.
	 *
	 * @since 2.0.0
	 * @access public
	 */
/*
	function update_user_level_from_caps() {
		global $wp_users_object;
		$this->user_level = array_reduce( array_keys( $this->allcaps ), array( &$this, 'level_reduction' ), 0 );
		update_usermeta( $this->ID, $wpdb->prefix.'user_level', $this->user_level );
	}
*/

/*
	function translate_level_to_cap($level) {
		return 'level_' . $level;
	}
*/

	/**
	 * Add capability and grant or deny access to capability.
	 *
	 * @since 2.0.0
	 * @access public
	 *
	 * @param string $cap Capability name.
	 * @param bool $grant Whether to grant capability to user.
	 */
	function add_cap( $cap, $grant = true ) {
		$this->caps[$cap] = $grant;
		$this->update_user();
	}

	/**
	 * Remove capability from user.
	 *
	 * @since 2.0.0
	 * @access public
	 *
	 * @param string $cap Capability name.
	 */
	function remove_cap( $cap ) {
		if ( empty( $this->caps[$cap] ) ) return;
		unset( $this->caps[$cap] );
		$this->update_user();
	}

	/**
	 * Remove all of the capabilities of the user.
	 *
	 * @since 2.1.0
	 * @access public
	 */
	function remove_all_caps() {
		global $wp_users_object;
		$this->caps = array();
		$wp_users_object->delete_meta( $this->ID, $this->cap_key );
		$this->get_role_caps();
	}

	/**
	 * Whether user has capability or role name.
	 *
	 * This is useful for looking up whether the user has a specific role
	 * assigned to the user. The second optional parameter can also be used to
	 * check for capabilities against a specfic post.
	 *
	 * @since 2.0.0
	 * @access public
	 *
	 * @param string|int $cap Capability or role name to search.
	 * @param int $post_id Optional. Post ID to check capability against specific post.
	 * @return bool True, if user has capability; false, if user does not have capability.
	 */
	function has_cap( $cap ) {
		global $wp_roles;

		if ( is_numeric( $cap ) )
			$cap = $this->translate_level_to_cap( $cap );

		$args = array_slice( func_get_args(), 1 );
		$args = array_merge( array( $cap, $this->ID ), $args );
		$caps = call_user_func_array( array(&$wp_roles, 'map_meta_cap'), $args );
		// Must have ALL requested caps
		$capabilities = apply_filters( 'user_has_cap', $this->allcaps, $caps, $args );
		foreach ( (array) $caps as $cap ) {
			//echo "Checking cap $cap<br />";
			if ( empty( $capabilities[$cap] ) || !$capabilities[$cap] )
				return false;
		}

		return true;
	}

	/**
	 * Convert numeric level to level capability name.
	 *
	 * Prepends 'level_' to level number.
	 *
	 * @since 2.0.0
	 * @access public
	 *
	 * @param int $level Level number, 1 to 10.
	 * @return string
	 */
	function translate_level_to_cap( $level ) {
		return 'level_' . $level;
	}

}

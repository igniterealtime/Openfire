<?php

class BP_Roles {
	var $role_objects = array();
	var $role_names = array();

	function BP_Roles() {
		$this->__construct();
	}

	function __construct() {
		do_action_ref_array('init_roles', array(&$this) );
	}

	function add_role($role, $display_name, $capabilities = '') {
		if ( isset($this->role_objects[$role]) )
			return;

		$this->role_objects[$role] = new BP_Role($role, $capabilities, $this);
		$this->role_names[$role] = $display_name;
		return $this->role_objects[$role];
	}

	function remove_role($role) {
		if ( ! isset($this->role_objects[$role]) )
			return;

		unset($this->role_objects[$role]);
		unset($this->role_names[$role]);
	}

	function add_cap($role, $cap, $grant = true) {
		if ( isset($this->role_objects[$role]) )
			$this->role_objects[$role]->add_cap($cap, $grant);
	}

	function remove_cap($role, $cap) {
		if ( isset($this->role_objects[$role]) )
			$this->role_objects[$role]->remove_cap($cap, $grant);
	}

	function &get_role($role) {
		if ( isset($this->role_objects[$role]) )
			return $this->role_objects[$role];
		else
			return null;
	}

	function get_names() {
		return $this->role_names;
	}

	function is_role($role) {
		return isset($this->role_names[$role]);
	}

	function map_meta_cap( $cap, $user_id ) {
		$args = array_slice(func_get_args(), 2);
		return apply_filters( 'map_meta_cap', array( $cap ), $cap, $user_id, $args );
	}
}

class BP_Role {
	var $name;
	var $capabilities;

	function BP_Role($role, $capabilities) {
		$this->name = $role;
		$this->capabilities = $capabilities;
	}

	function add_cap($cap, $grant = true) {
		$this->capabilities[$cap] = $grant;
	}

	function remove_cap($cap) {
		unset($this->capabilities[$cap]);
	}

	function has_cap($cap) {
		$capabilities = apply_filters('role_has_cap', $this->capabilities, $cap, $this->name);
		$grant = !empty( $capabilities[$cap] );
		return apply_filters("{$this->name}_has_cap", $grant);
	}

}

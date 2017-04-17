<?php
/**
 * BuddyPress XProfile Classes.
 *
 * @package BuddyPress
 * @subpackage XProfileClasses
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * A placeholder xprofile field type. Doesn't do anything.
 *
 * Used if an existing field has an unknown type (e.g. one provided by a missing third-party plugin).
 *
 * @since 2.0.1
 */
class BP_XProfile_Field_Type_Placeholder extends BP_XProfile_Field_Type {

	/**
	 * Constructor for the placeholder field type.
	 *
	 * @since 2.0.1
	 */
	public function __construct() {
		$this->set_format( '/.*/', 'replace' );
	}

	/**
	 * Prevent any HTML being output for this field type.
	 *
	 * @since 2.0.1
	 *
	 * @param array $raw_properties Optional key/value array of
	 *                              {@link http://dev.w3.org/html5/markup/input.text.html permitted attributes}
	 *                              that you want to add.
	 */
	public function edit_field_html( array $raw_properties = array() ) {
	}

	/**
	 * Prevent any HTML being output for this field type.
	 *
	 * @since 2.0.1
	 *
	 * @param array $raw_properties Optional key/value array of permitted attributes that you want to add.
	 */
	public function admin_field_html( array $raw_properties = array() ) {
	}

	/**
	 * Prevent any HTML being output for this field type.
	 *
	 * @since 2.0.1
	 *
	 * @param BP_XProfile_Field $current_field The current profile field on the add/edit screen.
	 * @param string            $control_type  Optional. HTML input type used to render the current
	 *                                         field's child options.
	 */
	public function admin_new_field_html( BP_XProfile_Field $current_field, $control_type = '' ) {}
}

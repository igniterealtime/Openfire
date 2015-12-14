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
 * URL xprofile field type.
 *
 * @since 2.1.0
 */
class BP_XProfile_Field_Type_URL extends BP_XProfile_Field_Type {

	/**
	 * Constructor for the URL field type
	 *
	 * @since 2.1.0
	 */
	public function __construct() {
		parent::__construct();

		$this->category = _x( 'Single Fields', 'xprofile field type category', 'buddypress' );
		$this->name     = _x( 'URL', 'xprofile field type', 'buddypress' );

		$this->set_format( '_^(?:(?:https?|ftp)://)(?:\S+(?::\S*)?@)?(?:(?!10(?:\.\d{1,3}){3})(?!127(?:\.\d{1,3}){3})(?!169\.254(?:\.\d{1,3}){2})(?!192\.168(?:\.\d{1,3}){2})(?!172\.(?:1[6-9]|2\d|3[0-1])(?:\.\d{1,3}){2})(?:[1-9]\d?|1\d\d|2[01]\d|22[0-3])(?:\.(?:1?\d{1,2}|2[0-4]\d|25[0-5])){2}(?:\.(?:[1-9]\d?|1\d\d|2[0-4]\d|25[0-4]))|(?:(?:[a-z\x{00a1}-\x{ffff}0-9]+-?)*[a-z\x{00a1}-\x{ffff}0-9]+)(?:\.(?:[a-z\x{00a1}-\x{ffff}0-9]+-?)*[a-z\x{00a1}-\x{ffff}0-9]+)*(?:\.(?:[a-z\x{00a1}-\x{ffff}]{2,})))(?::\d{2,5})?(?:/[^\s]*)?$_iuS', 'replace' );

		/**
		 * Fires inside __construct() method for BP_XProfile_Field_Type_URL class.
		 *
		 * @since 2.0.0
		 *
		 * @param BP_XProfile_Field_Type_URL $this Current instance of
		 *                                         the field type URL.
		 */
		do_action( 'bp_xprofile_field_type_url', $this );
	}

	/**
	 * Output the edit field HTML for this field type.
	 *
	 * Must be used inside the {@link bp_profile_fields()} template loop.
	 *
	 * @param array $raw_properties Optional key/value array of
	 *                              {@link http://dev.w3.org/html5/markup/input.number.html permitted attributes}
	 *                              that you want to add.
	 *
	 * @since 2.1.0
	 */
	public function edit_field_html( array $raw_properties = array() ) {

		// `user_id` is a special optional parameter that certain other
		// fields types pass to {@link bp_the_profile_field_options()}.
		if ( isset( $raw_properties['user_id'] ) ) {
			unset( $raw_properties['user_id'] );
		}

		$r = bp_parse_args( $raw_properties, array(
			'type'      => 'text',
			'inputmode' => 'url',
			'value'     => esc_url( bp_get_the_profile_field_edit_value() ),
		) ); ?>

		<label for="<?php bp_the_profile_field_input_name(); ?>">
			<?php bp_the_profile_field_name(); ?>
			<?php bp_the_profile_field_required_label(); ?>
		</label>

		<?php

		/** This action is documented in bp-xprofile/bp-xprofile-classes */
		do_action( bp_get_the_profile_field_errors_action() ); ?>

		<input <?php echo $this->get_edit_field_html_elements( $r ); ?>>

		<?php
	}

	/**
	 * Output HTML for this field type on the wp-admin Profile Fields screen.
	 *
	 * Must be used inside the {@link bp_profile_fields()} template loop.
	 *
	 * @param array $raw_properties Optional key/value array of permitted
	 *                              attributes that you want to add.
	 * @since 2.1.0
	 */
	public function admin_field_html( array $raw_properties = array() ) {

		$r = bp_parse_args( $raw_properties, array(
			'type' => 'url'
		) ); ?>

		<label for="<?php bp_the_profile_field_input_name(); ?>" class="screen-reader-text"><?php esc_html_e( 'URL', 'buddypress' ); ?></label>
		<input <?php echo $this->get_edit_field_html_elements( $r ); ?>>

		<?php
	}

	/**
	 * This method usually outputs HTML for this field type's children options
	 * on the wp-admin Profile Fields "Add Field" and "Edit Field" screens, but
	 * for this field type, we don't want it, so it's stubbed out.
	 *
	 * @since 2.1.0
	 *
	 * @param BP_XProfile_Field $current_field The current profile field on the add/edit screen.
	 * @param string            $control_type  Optional. HTML input type used to render the current
	 *                                         field's child options.
	 */
	public function admin_new_field_html( BP_XProfile_Field $current_field, $control_type = '' ) {}

	/**
	 * Modify submitted URL values before validation.
	 *
	 * The URL validation regex requires a http(s) protocol, so that all
	 * values saved in the database are fully-formed URLs. However, we
	 * still want to allow users to enter URLs without a protocol, for a
	 * better user experience. So we catch submitted URL values, and if
	 * the protocol is missing, we prepend 'http://' before passing to
	 * is_valid().
	 *
	 * @since 2.1.0
	 * @since 2.4.0 Added the `$field_id` parameter.
	 *
	 * @param string $submitted_value Raw value submitted by the user.
	 * @param int    $field_id        Optional. ID of the field.
	 *
	 * @return string
	 */
	public static function pre_validate_filter( $submitted_value = '', $field_id = '' ) {

		// Allow empty URL values.
		if ( empty( $submitted_value ) ) {
			return '';
		}

		// Run some checks on the submitted value.
		if ( false === strpos( $submitted_value, ':'  )
		     && substr( $submitted_value, 0, 1 ) !== '/'
		     && substr( $submitted_value, 0, 1 ) !== '#'
		     && ! preg_match( '/^[a-z0-9-]+?\.php/i', $submitted_value )
		) {
			$submitted_value = 'http://' . $submitted_value;
		}

		return $submitted_value;
	}

	/**
	 * Format URL values for display.
	 *
	 * @since 2.1.0
	 * @since 2.4.0 Added the `$field_id` parameter.
	 *
	 * @param string $field_value The URL value, as saved in the database.
	 * @param int    $field_id    Optional. ID of the field.
	 *
	 * @return string URL converted to a link.
	 */
	public static function display_filter( $field_value, $field_id = '' ) {
		$link      = strip_tags( $field_value );
		$no_scheme = preg_replace( '#^https?://#', '', rtrim( $link, '/' ) );
		$url_text  = str_replace( $link, $no_scheme, $field_value );
		return '<a href="' . esc_url( $field_value ) . '" rel="nofollow">' . esc_html( $url_text ) . '</a>';
	}
}

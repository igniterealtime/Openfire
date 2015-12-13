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
 * Datebox xprofile field type.
 *
 * @since 2.0.0
 */
class BP_XProfile_Field_Type_Datebox extends BP_XProfile_Field_Type {

	/**
	 * Constructor for the datebox field type.
	 *
	 * @since 2.0.0
	 */
	public function __construct() {
		parent::__construct();

		$this->category = _x( 'Single Fields', 'xprofile field type category', 'buddypress' );
		$this->name     = _x( 'Date Selector', 'xprofile field type', 'buddypress' );

		$this->set_format( '/^\d{4}-\d{1,2}-\d{1,2} 00:00:00$/', 'replace' ); // "Y-m-d 00:00:00"

		/**
		 * Fires inside __construct() method for BP_XProfile_Field_Type_Datebox class.
		 *
		 * @since 2.0.0
		 *
		 * @param BP_XProfile_Field_Type_Datebox $this Current instance of
		 *                                             the field type datebox.
		 */
		do_action( 'bp_xprofile_field_type_datebox', $this );
	}

	/**
	 * Output the edit field HTML for this field type.
	 *
	 * Must be used inside the {@link bp_profile_fields()} template loop.
	 *
	 * @since 2.0.0
	 *
	 * @param array $raw_properties Optional key/value array of
	 *                              {@link http://dev.w3.org/html5/markup/input.html permitted attributes}
	 *                              that you want to add.
	 */
	public function edit_field_html( array $raw_properties = array() ) {

		// User_id is a special optional parameter that we pass to.
		// {@link bp_the_profile_field_options()}.
		if ( isset( $raw_properties['user_id'] ) ) {
			$user_id = (int) $raw_properties['user_id'];
			unset( $raw_properties['user_id'] );
		} else {
			$user_id = bp_displayed_user_id();
		}

		$day_r = bp_parse_args( $raw_properties, array(
			'id'   => bp_get_the_profile_field_input_name() . '_day',
			'name' => bp_get_the_profile_field_input_name() . '_day'
		) );

		$month_r = bp_parse_args( $raw_properties, array(
			'id'   => bp_get_the_profile_field_input_name() . '_month',
			'name' => bp_get_the_profile_field_input_name() . '_month'
		) );

		$year_r = bp_parse_args( $raw_properties, array(
			'id'   => bp_get_the_profile_field_input_name() . '_year',
			'name' => bp_get_the_profile_field_input_name() . '_year'
		) ); ?>

		<div class="datebox">

			<label for="<?php bp_the_profile_field_input_name(); ?>_day">
				<?php bp_the_profile_field_name(); ?>
				<?php bp_the_profile_field_required_label(); ?>
			</label>

			<?php

			/**
			 * Fires after field label and displays associated errors for the field.
			 *
			 * This is a dynamic hook that is dependent on the associated
			 * field ID. The hooks will be similar to `bp_field_12_errors`
			 * where the 12 is the field ID. Simply replace the 12 with
			 * your needed target ID.
			 *
			 * @since 1.8.0
			 */
			do_action( bp_get_the_profile_field_errors_action() ); ?>

			<select <?php echo $this->get_edit_field_html_elements( $day_r ); ?>>
				<?php bp_the_profile_field_options( array(
					'type'    => 'day',
					'user_id' => $user_id
				) ); ?>
			</select>

			<select <?php echo $this->get_edit_field_html_elements( $month_r ); ?>>
				<?php bp_the_profile_field_options( array(
					'type'    => 'month',
					'user_id' => $user_id
				) ); ?>
			</select>

			<select <?php echo $this->get_edit_field_html_elements( $year_r ); ?>>
				<?php bp_the_profile_field_options( array(
					'type'    => 'year',
					'user_id' => $user_id
				) ); ?>
			</select>

		</div>
	<?php
	}

	/**
	 * Output the edit field options HTML for this field type.
	 *
	 * BuddyPress considers a field's "options" to be, for example, the items in a selectbox.
	 * These are stored separately in the database, and their templating is handled separately.
	 *
	 * This templating is separate from {@link BP_XProfile_Field_Type::edit_field_html()} because
	 * it's also used in the wp-admin screens when creating new fields, and for backwards compatibility.
	 *
	 * Must be used inside the {@link bp_profile_fields()} template loop.
	 *
	 * @since 2.0.0
	 *
	 * @param array $args Optional. The arguments passed to {@link bp_the_profile_field_options()}.
	 */
	public function edit_field_options_html( array $args = array() ) {

		$date       = BP_XProfile_ProfileData::get_value_byid( $this->field_obj->id, $args['user_id'] );
		$day        = 0;
		$month      = 0;
		$year       = 0;
		$html       = '';
		$eng_months = array( 'January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December' );

		// Set day, month, year defaults.
		if ( ! empty( $date ) ) {

			// If Unix timestamp.
			if ( is_numeric( $date ) ) {
				$day   = date( 'j', $date );
				$month = date( 'F', $date );
				$year  = date( 'Y', $date );

			// If MySQL timestamp.
			} else {
				$day   = mysql2date( 'j', $date );
				$month = mysql2date( 'F', $date, false ); // Not localized, so that selected() works below.
				$year  = mysql2date( 'Y', $date );
			}
		}

		// Check for updated posted values, and errors preventing them from
		// being saved first time.
		if ( ! empty( $_POST['field_' . $this->field_obj->id . '_day'] ) ) {
			$new_day = (int) $_POST['field_' . $this->field_obj->id . '_day'];
			$day     = ( $day != $new_day ) ? $new_day : $day;
		}

		if ( ! empty( $_POST['field_' . $this->field_obj->id . '_month'] ) ) {
			if ( in_array( $_POST['field_' . $this->field_obj->id . '_month'], $eng_months ) ) {
				$new_month = $_POST['field_' . $this->field_obj->id . '_month'];
			} else {
				$new_month = $month;
			}

			$month = ( $month !== $new_month ) ? $new_month : $month;
		}

		if ( ! empty( $_POST['field_' . $this->field_obj->id . '_year'] ) ) {
			$new_year = (int) $_POST['field_' . $this->field_obj->id . '_year'];
			$year     = ( $year != $new_year ) ? $new_year : $year;
		}

		// $type will be passed by calling function when needed.
		switch ( $args['type'] ) {
			case 'day':
				$html = sprintf( '<option value="" %1$s>%2$s</option>', selected( $day, 0, false ), /* translators: no option picked in select box */ __( '----', 'buddypress' ) );

				for ( $i = 1; $i < 32; ++$i ) {
					$html .= sprintf( '<option value="%1$s" %2$s>%3$s</option>', (int) $i, selected( $day, $i, false ), (int) $i );
				}
			break;

			case 'month':
				$months = array(
					__( 'January',   'buddypress' ),
					__( 'February',  'buddypress' ),
					__( 'March',     'buddypress' ),
					__( 'April',     'buddypress' ),
					__( 'May',       'buddypress' ),
					__( 'June',      'buddypress' ),
					__( 'July',      'buddypress' ),
					__( 'August',    'buddypress' ),
					__( 'September', 'buddypress' ),
					__( 'October',   'buddypress' ),
					__( 'November',  'buddypress' ),
					__( 'December',  'buddypress' )
				);

				$html = sprintf( '<option value="" %1$s>%2$s</option>', selected( $month, 0, false ), /* translators: no option picked in select box */ __( '----', 'buddypress' ) );

				for ( $i = 0; $i < 12; ++$i ) {
					$html .= sprintf( '<option value="%1$s" %2$s>%3$s</option>', esc_attr( $eng_months[$i] ), selected( $month, $eng_months[$i], false ), $months[$i] );
				}
			break;

			case 'year':
				$html = sprintf( '<option value="" %1$s>%2$s</option>', selected( $year, 0, false ), /* translators: no option picked in select box */ __( '----', 'buddypress' ) );

				for ( $i = 2037; $i > 1901; $i-- ) {
					$html .= sprintf( '<option value="%1$s" %2$s>%3$s</option>', (int) $i, selected( $year, $i, false ), (int) $i );
				}
			break;
		}

		/**
		 * Filters the output for the profile field datebox.
		 *
		 * @since 1.1.0
		 *
		 * @param string $html  HTML output for the field.
		 * @param string $value Which date type is being rendered for.
		 * @param string $day   Date formatted for the current day.
		 * @param string $month Date formatted for the current month.
		 * @param string $year  Date formatted for the current year.
		 * @param int    $id    ID of the field object being rendered.
		 * @param string $date  Current date.
		 */
		echo apply_filters( 'bp_get_the_profile_field_datebox', $html, $args['type'], $day, $month, $year, $this->field_obj->id, $date );
	}

	/**
	 * Output HTML for this field type on the wp-admin Profile Fields screen.
	 *
	 * Must be used inside the {@link bp_profile_fields()} template loop.
	 *
	 * @since 2.0.0
	 *
	 * @param array $raw_properties Optional key/value array of permitted attributes that you want to add.
	 */
	public function admin_field_html( array $raw_properties = array() ) {

		$day_r = bp_parse_args( $raw_properties, array(
			'id'   => bp_get_the_profile_field_input_name() . '_day',
			'name' => bp_get_the_profile_field_input_name() . '_day'
		) );

		$month_r = bp_parse_args( $raw_properties, array(
			'id'   => bp_get_the_profile_field_input_name() . '_month',
			'name' => bp_get_the_profile_field_input_name() . '_month'
		) );

		$year_r = bp_parse_args( $raw_properties, array(
			'id'   => bp_get_the_profile_field_input_name() . '_year',
			'name' => bp_get_the_profile_field_input_name() . '_year'
		) ); ?>

		<label for="<?php bp_the_profile_field_input_name(); ?>_day" class="screen-reader-text"><?php esc_html_e( 'Select day', 'buddypress' ); ?></label>
		<select <?php echo $this->get_edit_field_html_elements( $day_r ); ?>>
			<?php bp_the_profile_field_options( array( 'type' => 'day' ) ); ?>
		</select>

		<label for="<?php bp_the_profile_field_input_name(); ?>_month" class="screen-reader-text"><?php esc_html_e( 'Select month', 'buddypress' ); ?></label>
		<select <?php echo $this->get_edit_field_html_elements( $month_r ); ?>>
			<?php bp_the_profile_field_options( array( 'type' => 'month' ) ); ?>
		</select>

		<label for="<?php bp_the_profile_field_input_name(); ?>_year" class="screen-reader-text"><?php esc_html_e( 'Select year', 'buddypress' ); ?></label>
		<select <?php echo $this->get_edit_field_html_elements( $year_r ); ?>>
			<?php bp_the_profile_field_options( array( 'type' => 'year' ) ); ?>
		</select>

	<?php
	}

	/**
	 * This method usually outputs HTML for this field type's children options on the wp-admin Profile Fields
	 * "Add Field" and "Edit Field" screens, but for this field type, we don't want it, so it's stubbed out.
	 *
	 * @since 2.0.0
	 *
	 * @param BP_XProfile_Field $current_field The current profile field on the add/edit screen.
	 * @param string            $control_type  Optional. HTML input type used to render the current
	 *                                         field's child options.
	 */
	public function admin_new_field_html( BP_XProfile_Field $current_field, $control_type = '' ) {}

	/**
	 * Format Date values for display.
	 *
	 * @since 2.1.0
	 * @since 2.4.0 Added the `$field_id` parameter.
	 *
	 * @param string $field_value The date value, as saved in the database. Typically, this is a MySQL-formatted
	 *                            date string (Y-m-d H:i:s).
	 * @param int    $field_id    Optional. ID of the field.
	 *
	 * @return string Date formatted by bp_format_time().
	 */
	public static function display_filter( $field_value, $field_id = '' ) {

		// If Unix timestamp.
		if ( ! is_numeric( $field_value ) ) {
			$field_value = strtotime( $field_value );
		}

		return bp_format_time( $field_value, true, false );
	}
}

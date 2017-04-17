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
 * Represents a type of XProfile field and holds meta information about the type of value that it accepts.
 *
 * @since 2.0.0
 */
abstract class BP_XProfile_Field_Type {

	/**
	 * Validation regex rules for field type.
	 * @since 2.0.0
	 * @var array Field type validation regexes.
	 */
	protected $validation_regex = array();

	/**
	 * Whitelisted values for field type.
	 *
	 * @since 2.0.0
	 * @var array Field type whitelisted values.
	 */
	protected $validation_whitelist = array();

	/**
	 * Name for field type.
	 *
	 * @since 2.0.0
	 * @var string The name of this field type.
	 */
	public $name = '';

	/**
	 * The name of the category that this field type should be grouped with. Used on the [Users > Profile Fields] screen in wp-admin.
	 *
	 * @since 2.0.0
	 * @var string
	 */
	public $category = '';

	/**
	 * If allowed to store null/empty values.
	 *
	 * @since 2.0.0
	 * @var bool If this is set, allow BP to store null/empty values for this field type.
	 */
	public $accepts_null_value = false;

	/**
	 * If this is set, BP will set this field type's validation whitelist from the field's options (e.g checkbox, selectbox).
	 *
	 * @since 2.0.0
	 * @var bool Does this field support options? e.g. selectbox, radio buttons, etc.
	 */
	public $supports_options = false;

	/**
	 * If allowed to support multiple options as default.
	 *
	 * @since 2.0.0
	 * @var bool Does this field type support multiple options being set as default values? e.g. multiselectbox, checkbox.
	 */
	public $supports_multiple_defaults = false;

	/**
	 * If the field type supports rich text by default.
	 *
	 * @since 2.4.0
	 * @var bool
	 */
	public $supports_richtext = false;

	/**
	 * If object is created by an BP_XProfile_Field object.
	 *
	 * @since 2.0.0
	 * @var BP_XProfile_Field If this object is created by instantiating a {@link BP_XProfile_Field},
	 *                        this is a reference back to that object.
	 */
	public $field_obj = null;

	/**
	 * Constructor.
	 *
	 * @since 2.0.0
	 */
	public function __construct() {

		/**
		 * Fires inside __construct() method for BP_XProfile_Field_Type class.
		 *
		 * @since 2.0.0
		 *
		 * @param BP_XProfile_Field_Type $this Current instance of
		 *                                     the field type class.
		 */
		do_action( 'bp_xprofile_field_type', $this );
	}

	/**
	 * Set a regex that profile data will be asserted against.
	 *
	 * You can call this method multiple times to set multiple formats. When validation is performed,
	 * it's successful as long as the new value matches any one of the registered formats.
	 *
	 * @since 2.0.0
	 *
	 * @param string $format         Regex string.
	 * @param string $replace_format Optional; if 'replace', replaces the format instead of adding to it.
	 *                               Defaults to 'add'.
	 *
	 * @return BP_XProfile_Field_Type
	 */
	public function set_format( $format, $replace_format = 'add' ) {

		/**
		 * Filters the regex format for the field type.
		 *
		 * @since 2.0.0
		 *
		 * @param string                 $format         Regex string.
		 * @param string                 $replace_format Optional replace format If "replace", replaces the
		 *                                               format instead of adding to it. Defaults to "add".
		 * @param BP_XProfile_Field_Type $this           Current instance of the BP_XProfile_Field_Type class.
		 */
		$format = apply_filters( 'bp_xprofile_field_type_set_format', $format, $replace_format, $this );

		if ( 'add' === $replace_format ) {
			$this->validation_regex[] = $format;
		} elseif ( 'replace' === $replace_format ) {
			$this->validation_regex = array( $format );
		}

		return $this;
	}

	/**
	 * Add a value to this type's whitelist that profile data will be asserted against.
	 *
	 * You can call this method multiple times to set multiple formats. When validation is performed,
	 * it's successful as long as the new value matches any one of the registered formats.
	 *
	 * @since 2.0.0
	 *
	 * @param string|array $values Whitelisted values.
	 *
	 * @return BP_XProfile_Field_Type
	 */
	public function set_whitelist_values( $values ) {
		foreach ( (array) $values as $value ) {

			/**
			 * Filters values for field type's whitelist that profile data will be asserted against.
			 *
			 * @since 2.0.0
			 *
			 * @param string                 $value  Field value.
			 * @param array                  $values Original array of values.
			 * @param BP_XProfile_Field_Type $this   Current instance of the BP_XProfile_Field_Type class.
			 */
			$this->validation_whitelist[] = apply_filters( 'bp_xprofile_field_type_set_whitelist_values', $value, $values, $this );
		}

		return $this;
	}

	/**
	 * Check the given string against the registered formats for this field type.
	 *
	 * This method doesn't support chaining.
	 *
	 * @since 2.0.0
	 *
	 * @param string|array $values Value to check against the registered formats.
	 *
	 * @return bool True if the value validates
	 */
	public function is_valid( $values ) {
		$validated = false;

		// Some types of field (e.g. multi-selectbox) may have multiple values to check.
		foreach ( (array) $values as $value ) {

			// Validate the $value against the type's accepted format(s).
			foreach ( $this->validation_regex as $format ) {
				if ( 1 === preg_match( $format, $value ) ) {
					$validated = true;
					continue;

				} else {
					$validated = false;
				}
			}
		}

		// Handle field types with accepts_null_value set if $values is an empty array.
		if ( ( false === $validated ) && is_array( $values ) && empty( $values ) && $this->accepts_null_value ) {
			$validated = true;
		}

		// If there's a whitelist set, also check the $value.
		if ( ( true === $validated ) && ! empty( $values ) && ! empty( $this->validation_whitelist ) ) {
			foreach ( (array) $values as $value ) {
				$validated = in_array( $value, $this->validation_whitelist, true );
			}
		}

		/**
		 * Filters whether or not field type is a valid format.
		 *
		 * @since 2.0.0
		 *
		 * @param bool                   $validated Whether or not the field type is valid.
		 * @param string|array           $values    Value to check against the registered formats.
		 * @param BP_XProfile_Field_Type $this      Current instance of the BP_XProfile_Field_Type class.
		 */
		return (bool) apply_filters( 'bp_xprofile_field_type_is_valid', $validated, $values, $this );
	}

	/**
	 * Output the edit field HTML for this field type.
	 *
	 * Must be used inside the {@link bp_profile_fields()} template loop.
	 *
	 * @since 2.0.0
	 *
	 * @param array $raw_properties Optional key/value array of permitted attributes that you want to add.
	 */
	abstract public function edit_field_html( array $raw_properties = array() );

	/**
	 * Output HTML for this field type on the wp-admin Profile Fields screen.
	 *
	 * Must be used inside the {@link bp_profile_fields()} template loop.
	 *
	 * @param array $raw_properties Optional key/value array of permitted attributes that you want to add.
	 * @since 2.0.0
	 */
	abstract public function admin_field_html( array $raw_properties = array() );

	/**
	 * Output the edit field options HTML for this field type.
	 *
	 * BuddyPress considers a field's "options" to be, for example, the items in a selectbox.
	 * These are stored separately in the database, and their templating is handled separately.
	 * Populate this method in a child class if it's required. Otherwise, you can leave it out.
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
	public function edit_field_options_html( array $args = array() ) {}

	/**
	 * Output HTML for this field type's children options on the wp-admin Profile Fields "Add Field" and "Edit Field" screens.
	 *
	 * You don't need to implement this method for all field types. It's used in core by the
	 * selectbox, multi selectbox, checkbox, and radio button fields, to allow the admin to
	 * enter the child option values (e.g. the choices in a select box).
	 *
	 * Must be used inside the {@link bp_profile_fields()} template loop.
	 *
	 * @since 2.0.0
	 *
	 * @param BP_XProfile_Field $current_field The current profile field on the add/edit screen.
	 * @param string            $control_type  Optional. HTML input type used to render the current
	 *                          field's child options.
	 */
	public function admin_new_field_html( BP_XProfile_Field $current_field, $control_type = '' ) {
		$type = array_search( get_class( $this ), bp_xprofile_get_field_types() );
		if ( false === $type ) {
			return;
		}

		$class            = $current_field->type != $type ? 'display: none;' : '';
		$current_type_obj = bp_xprofile_create_field_type( $type );
		?>

		<div id="<?php echo esc_attr( $type ); ?>" class="postbox bp-options-box" style="<?php echo esc_attr( $class ); ?> margin-top: 15px;">
			<h3><?php esc_html_e( 'Please enter options for this Field:', 'buddypress' ); ?></h3>
			<div class="inside">
				<p>
					<label for="sort_order_<?php echo esc_attr( $type ); ?>"><?php esc_html_e( 'Sort Order:', 'buddypress' ); ?></label>
					<select name="sort_order_<?php echo esc_attr( $type ); ?>" id="sort_order_<?php echo esc_attr( $type ); ?>" >
						<option value="custom" <?php selected( 'custom', $current_field->order_by ); ?>><?php esc_html_e( 'Custom',     'buddypress' ); ?></option>
						<option value="asc"    <?php selected( 'asc',    $current_field->order_by ); ?>><?php esc_html_e( 'Ascending',  'buddypress' ); ?></option>
						<option value="desc"   <?php selected( 'desc',   $current_field->order_by ); ?>><?php esc_html_e( 'Descending', 'buddypress' ); ?></option>
					</select>
				</p>

				<?php

				// Does option have children?
				$options = $current_field->get_children( true );

				// If no children options exists for this field, check in $_POST
				// for a submitted form (e.g. on the "new field" screen).
				if ( empty( $options ) ) {

					$options = array();
					$i       = 1;

					while ( isset( $_POST[$type . '_option'][$i] ) ) {

						// Multiselectbox and checkboxes support MULTIPLE default options; all other core types support only ONE.
						if ( $current_type_obj->supports_options && ! $current_type_obj->supports_multiple_defaults && isset( $_POST["isDefault_{$type}_option"][$i] ) && (int) $_POST["isDefault_{$type}_option"] === $i ) {
							$is_default_option = true;
						} elseif ( isset( $_POST["isDefault_{$type}_option"][$i] ) ) {
							$is_default_option = (bool) $_POST["isDefault_{$type}_option"][$i];
						} else {
							$is_default_option = false;
						}

						// Grab the values from $_POST to use as the form's options.
						$options[] = (object) array(
							'id'                => -1,
							'is_default_option' => $is_default_option,
							'name'              => sanitize_text_field( stripslashes( $_POST[$type . '_option'][$i] ) ),
						);

						++$i;
					}

					// If there are still no children options set, this must be the "new field" screen, so add one new/empty option.
					if ( empty( $options ) ) {
						$options[] = (object) array(
							'id'                => -1,
							'is_default_option' => false,
							'name'              => '',
						);
					}
				}

				// Render the markup for the children options.
				if ( ! empty( $options ) ) {
					$default_name = '';

					for ( $i = 0, $count = count( $options ); $i < $count; ++$i ) :
						$j = $i + 1;

						// Multiselectbox and checkboxes support MULTIPLE default options; all other core types support only ONE.
						if ( $current_type_obj->supports_options && $current_type_obj->supports_multiple_defaults ) {
							$default_name = '[' . $j . ']';
						}
						?>

						<div id="<?php echo esc_attr( "{$type}_div{$j}" ); ?>" class="bp-option sortable">
							<span class="bp-option-icon grabber"></span>
							<label for="<?php echo esc_attr( "{$type}_option{$j}" ); ?>" class="screen-reader-text"><?php esc_html_e( 'Add an option', 'buddypress' ); ?></label>
							<input type="text" name="<?php echo esc_attr( "{$type}_option[{$j}]" ); ?>" id="<?php echo esc_attr( "{$type}_option{$j}" ); ?>" value="<?php echo esc_attr( stripslashes( $options[$i]->name ) ); ?>" />
							<label for="<?php echo esc_attr( "{$type}_option{$default_name}" ); ?>">
								<input type="<?php echo esc_attr( $control_type ); ?>" id="<?php echo esc_attr( "{$type}_option{$default_name}" ); ?>" name="<?php echo esc_attr( "isDefault_{$type}_option{$default_name}" ); ?>" <?php checked( $options[$i]->is_default_option, true ); ?> value="<?php echo esc_attr( $j ); ?>" />
								<?php _e( 'Default Value', 'buddypress' ); ?>
							</label>

							<?php if ( 1 !== $j ) : ?>
								<div class ="delete-button">
									<a href='javascript:hide("<?php echo esc_attr( "{$type}_div{$j}" ); ?>")' class="delete"><?php esc_html_e( 'Delete', 'buddypress' ); ?></a>
								</div>
							<?php endif; ?>

						</div>

					<?php endfor; ?>

					<input type="hidden" name="<?php echo esc_attr( "{$type}_option_number" ); ?>" id="<?php echo esc_attr( "{$type}_option_number" ); ?>" value="<?php echo esc_attr( $j + 1 ); ?>" />
				<?php } ?>

				<div id="<?php echo esc_attr( "{$type}_more" ); ?>"></div>
				<p><a href="javascript:add_option('<?php echo esc_js( $type ); ?>')"><?php esc_html_e( 'Add Another Option', 'buddypress' ); ?></a></p>

				<?php

				/**
				 * Fires at the end of the new field additional settings area.
				 *
				 * @since 2.3.0
				 *
				 * @param BP_XProfile_Field $current_field Current field being rendered.
				 */
				do_action( 'bp_xprofile_admin_new_field_additional_settings', $current_field ) ?>
			</div>
		</div>

		<?php
	}

	/**
	 * Allow field types to modify submitted values before they are validated.
	 *
	 * In some cases, it may be appropriate for a field type to catch
	 * submitted values and modify them before they are passed to the
	 * is_valid() method. For example, URL validation requires the
	 * 'http://' scheme (so that the value saved in the database is always
	 * a fully-formed URL), but in order to allow users to enter a URL
	 * without this scheme, BP_XProfile_Field_Type_URL prepends 'http://'
	 * when it's not present.
	 *
	 * By default, this is a pass-through method that does nothing. Only
	 * override in your own field type if you need this kind of pre-
	 * validation filtering.
	 *
	 * @since 2.1.0
	 * @since 2.4.0 Added the `$field_id` parameter.
	 *
	 * @param mixed $field_value Submitted field value.
	 * @param int   $field_id    Optional. ID of the field.
	 *
	 * @return mixed
	 */
	public static function pre_validate_filter( $field_value, $field_id = '' ) {
		return $field_value;
	}

	/**
	 * Allow field types to modify the appearance of their values.
	 *
	 * By default, this is a pass-through method that does nothing. Only
	 * override in your own field type if you need to provide custom
	 * filtering for output values.
	 *
	 * @since 2.1.0
	 * @since 2.4.0 Added `$field_id` parameter.
	 *
	 * @param mixed $field_value Field value.
	 * @param int   $field_id    ID of the field.
	 *
	 * @return mixed
	 */
	public static function display_filter( $field_value, $field_id = '' ) {
		return $field_value;
	}

	/** Protected *************************************************************/

	/**
	 * Get a sanitised and escaped string of the edit field's HTML elements and attributes.
	 *
	 * Must be used inside the {@link bp_profile_fields()} template loop.
	 * This method was intended to be static but couldn't be because php.net/lsb/ requires PHP >= 5.3.
	 *
	 * @since 2.0.0
	 *
	 * @param array $properties Optional key/value array of attributes for this edit field.
	 *
	 * @return string
	 */
	protected function get_edit_field_html_elements( array $properties = array() ) {

		$r = bp_parse_args( $properties, array(
			'id'   => bp_get_the_profile_field_input_name(),
			'name' => bp_get_the_profile_field_input_name(),
		) );

		if ( bp_get_the_profile_field_is_required() ) {
			$r['aria-required'] = 'true';
		}

		/**
		 * Filters the edit html elements and attributes.
		 *
		 * @since 2.0.0
		 *
		 * @param array  $r     Array of parsed arguments.
		 * @param string $value Class name for the current class instance.
		 */
		$r = (array) apply_filters( 'bp_xprofile_field_edit_html_elements', $r, get_class( $this ) );

		return bp_get_form_field_attributes( sanitize_key( bp_get_the_profile_field_name() ), $r );
	}
}

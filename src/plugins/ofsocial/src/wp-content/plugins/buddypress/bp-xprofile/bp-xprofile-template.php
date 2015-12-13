<?php
/**
 * BuddyPress XProfile Template Tags.
 *
 * @package BuddyPress
 * @subpackage XProfileTemplate
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * The main profile template loop class.
 *
 * This is responsible for loading profile field, group, and data and displaying it.
 *
 * @since 1.0.0
 */
class BP_XProfile_Data_Template {

	/**
	 * The loop iterator.
	 *
	 * @since 1.5.0
	 * @var int
	 */
	public $current_group = -1;

	/**
	 * The number of groups returned by the paged query.
	 *
	 * @since 1.5.0
	 * @var int
	 */
	public $group_count;

	/**
	 * Array of groups located by the query.
	 *
	 * @since 1.5.0
	 * @var array
	 */
	public $groups;

	/**
	 * The group object currently being iterated on.
	 *
	 * @since 1.5.0
	 * @var object
	 */
	public $group;

	/**
	 * The current field.
	 *
	 * @since 1.5.0
	 * @var int
	 */
	public $current_field = -1;

	/**
	 * The field count.
	 *
	 * @since 1.5.0
	 * @var int
	 */
	public $field_count;

	/**
	 * Field has data.
	 *
	 * @since 1.5.0
	 * @var bool
	 */
	public $field_has_data;

	/**
	 * The field.
	 *
	 * @since 1.5.0
	 * @var int
	 */
	public $field;

	/**
	 * A flag for whether the loop is currently being iterated.
	 *
	 * @since 1.5.0
	 * @var bool
	 */
	public $in_the_loop;

	/**
	 * The user ID.
	 *
	 * @since 1.5.0
	 * @var int
	 */
	public $user_id;

	/**
	 * Get activity items, as specified by parameters.
	 *
	 * @see BP_XProfile_Group::get() for more details about parameters.
	 *
	 * @since 2.4.0 Introduced `$member_type` argument.
	 *
	 * @param array|string $args {
	 *     An array of arguments. All items are optional.
	 *
	 *     @type int          $user_id                 Fetch field data for this user ID.
	 *     @type string|array $member_type             Limit results to those matching member type(s).
	 *     @type int          $profile_group_id        Field group to fetch fields & data for.
	 *     @type int|bool     $hide_empty_groups       Should empty field groups be skipped.
	 *     @type int|bool     $fetch_fields            Fetch fields for field group.
	 *     @type int|bool     $fetch_field_data        Fetch field data for fields in group.
	 *     @type array        $exclude_groups          Exclude these field groups.
	 *     @type array        $exclude_fields          Exclude these fields.
	 *     @type int|bool     $hide_empty_fields       Should empty fields be skipped.
	 *     @type int|bool     $fetch_visibility_level  Fetch visibility levels.
	 *     @type int|bool     $update_meta_cache       Should metadata cache be updated.
	 * }
	 */
	public function __construct( $args = '' ) {

		// Backward compatibility with old method of passing arguments.
		if ( ! is_array( $args ) || func_num_args() > 1 ) {
			_deprecated_argument( __METHOD__, '2.3.0', sprintf( __( 'Arguments passed to %1$s should be in an associative array. See the inline documentation at %2$s for more details.', 'buddypress' ), __METHOD__, __FILE__ ) );

			$old_args_keys = array(
				0 => 'user_id',
				1 => 'profile_group_id',
				2 => 'hide_empty_groups',
				3 => 'fetch_fields',
				4 => 'fetch_field_data',
				5 => 'exclude_groups',
				6 => 'exclude_fields',
				7 => 'hide_empty_fields',
				8 => 'fetch_visibility_level',
				9 => 'update_meta_cache'
			);

			$func_args = func_get_args();
			$args      = bp_core_parse_args_array( $old_args_keys, $func_args );
		}

		$r = wp_parse_args( $args, array(
			'profile_group_id'       => false,
			'user_id'                => false,
			'member_type'            => 'any',
			'hide_empty_groups'      => false,
			'hide_empty_fields'      => false,
			'fetch_fields'           => false,
			'fetch_field_data'       => false,
			'fetch_visibility_level' => false,
			'exclude_groups'         => false,
			'exclude_fields'         => false,
			'update_meta_cache'      => true
		) );

		$this->groups      = bp_xprofile_get_groups( $r );
		$this->group_count = count( $this->groups );
		$this->user_id     = $r['user_id'];
	}

	public function has_groups() {
		if ( ! empty( $this->group_count ) ) {
			return true;
		}

		return false;
	}

	public function next_group() {
		$this->current_group++;

		$this->group       = $this->groups[ $this->current_group ];
		$this->field_count = 0;

		if ( ! empty( $this->group->fields ) ) {

			/**
			 * Filters the group fields for the next_group method.
			 *
			 * @since 1.1.0
			 *
			 * @param array $fields Array of fields for the group.
			 * @param int   $id     ID of the field group.
			 */
			$this->group->fields = apply_filters( 'xprofile_group_fields', $this->group->fields, $this->group->id );
			$this->field_count   = count( $this->group->fields );
		}

		return $this->group;
	}

	public function rewind_groups() {
		$this->current_group = -1;
		if ( $this->group_count > 0 ) {
			$this->group = $this->groups[0];
		}
	}

	public function profile_groups() {
		if ( $this->current_group + 1 < $this->group_count ) {
			return true;
		} elseif ( $this->current_group + 1 == $this->group_count ) {

			/**
			 * Fires right before the rewinding of profile groups.
			 *
			 * @since 1.1.0
			 */
			do_action( 'xprofile_template_loop_end' );

			// Do some cleaning up after the loop.
			$this->rewind_groups();
		}

		$this->in_the_loop = false;
		return false;
	}

	public function the_profile_group() {
		global $group;

		$this->in_the_loop = true;
		$group = $this->next_group();

		// Loop has just started.
		if ( 0 === $this->current_group ) {

			/**
			 * Fires if the current group is the first in the loop.
			 *
			 * @since 1.1.0
			 */
			do_action( 'xprofile_template_loop_start' );
		}
	}

	/** Fields ****************************************************************/

	public function next_field() {
		$this->current_field++;

		$this->field = $this->group->fields[ $this->current_field ];

		return $this->field;
	}

	public function rewind_fields() {
		$this->current_field = -1;
		if ( $this->field_count > 0 ) {
			$this->field = $this->group->fields[0];
		}
	}

	public function has_fields() {
		$has_data = false;

		for ( $i = 0, $count = count( $this->group->fields ); $i < $count; ++$i ) {
			$field = &$this->group->fields[ $i ];

			if ( ! empty( $field->data ) && ( $field->data->value != null ) ) {
				$has_data = true;
			}
		}

		return $has_data;
	}

	public function profile_fields() {
		if ( $this->current_field + 1 < $this->field_count ) {
			return true;
		} elseif ( $this->current_field + 1 == $this->field_count ) {
			// Do some cleaning up after the loop.
			$this->rewind_fields();
		}

		return false;
	}

	public function the_profile_field() {
		global $field;

		$field = $this->next_field();

		// Valid field values of 0 or '0' get caught by empty(), so we have an extra check for these. See #BP5731.
		if ( ! empty( $field->data ) && ( ! empty( $field->data->value ) || ( '0' === $field->data->value ) ) ) {
			$value = maybe_unserialize( $field->data->value );
		} else {
			$value = false;
		}

		if ( ! empty( $value ) || ( '0' === $value ) ) {
			$this->field_has_data = true;
		} else {
			$this->field_has_data = false;
		}
	}
}

/**
 * Query for XProfile groups and fields.
 *
 * @since 1.0.0
 *
 * @global object $profile_template
 * @see BP_XProfile_Group::get() for full description of `$args` array.
 *
 * @param array|string $args {
 *     Array of arguments. See BP_XProfile_Group::get() for full description. Those arguments whose defaults differ
 *     from that method are described here:
 *     @type string|array $member_type            Default: 'any'.
 *     @type bool         $hide_empty_groups      Default: true.
 *     @type bool         $hide_empty_fields      Defaults to true on the Dashboard, on a user's Edit Profile page,
 *                                                or during registration. Otherwise false.
 *     @type bool         $fetch_visibility_level Defaults to true when an admin is viewing a profile, or when a user is
 *                                                viewing her own profile, or during registration. Otherwise false.
 *     @type bool         $fetch_fields           Default: true.
 *     @type bool         $fetch_field_data       Default: true.
 * }
 *
 * @return bool
 */
function bp_has_profile( $args = '' ) {
	global $profile_template;

	// Only show empty fields if we're on the Dashboard, or we're on a user's
	// profile edit page, or this is a registration page.
	$hide_empty_fields_default = ( ! is_network_admin() && ! is_admin() && ! bp_is_user_profile_edit() && ! bp_is_register_page() );

	// We only need to fetch visibility levels when viewing your own profile.
	if ( bp_is_my_profile() || bp_current_user_can( 'bp_moderate' ) || bp_is_register_page() ) {
		$fetch_visibility_level_default = true;
	} else {
		$fetch_visibility_level_default = false;
	}

	// Parse arguments.
	$r = bp_parse_args( $args, array(
		'user_id'                => bp_displayed_user_id(),
		'member_type'            => 'any',
		'profile_group_id'       => false,
		'hide_empty_groups'      => true,
		'hide_empty_fields'      => $hide_empty_fields_default,
		'fetch_fields'           => true,
		'fetch_field_data'       => true,
		'fetch_visibility_level' => $fetch_visibility_level_default,
		'exclude_groups'         => false, // Comma-separated list of profile field group IDs to exclude.
		'exclude_fields'         => false, // Comma-separated list of profile field IDs to exclude.
		'update_meta_cache'      => true,
	), 'has_profile' );

	// Populate the template loop global.
	$profile_template = new BP_XProfile_Data_Template( $r );

	/**
	 * Filters whether or not a group has a profile to display.
	 *
	 * @since 1.1.0
	 *
	 * @param bool   $has_groups       Whether or not there are group profiles to display.
	 * @param string $profile_template Current profile template being used.
	 */
	return apply_filters( 'bp_has_profile', $profile_template->has_groups(), $profile_template );
}

function bp_profile_groups() {
	global $profile_template;
	return $profile_template->profile_groups();
}

function bp_the_profile_group() {
	global $profile_template;
	return $profile_template->the_profile_group();
}

function bp_profile_group_has_fields() {
	global $profile_template;
	return $profile_template->has_fields();
}

function bp_field_css_class( $class = false ) {
	echo bp_get_field_css_class( $class );
}
	function bp_get_field_css_class( $class = false ) {
		global $profile_template;

		$css_classes = array();

		if ( ! empty( $class ) ) {
			$css_classes[] = sanitize_title( esc_attr( $class ) );
		}

		// Set a class with the field ID.
		$css_classes[] = 'field_' . $profile_template->field->id;

		// Set a class with the field name (sanitized).
		$css_classes[] = 'field_' . sanitize_title( $profile_template->field->name );

		// Set a class indicating whether the field is required or optional.
		if ( ! empty( $profile_template->field->is_required ) ) {
			$css_classes[] = 'required-field';
		} else {
			$css_classes[] = 'optional-field';
		}

		// Add the field visibility level.
		$css_classes[] = 'visibility-' . esc_attr( bp_get_the_profile_field_visibility_level() );

		if ( $profile_template->current_field % 2 == 1 ) {
			$css_classes[] = 'alt';
		}

		$css_classes[] = 'field_type_' . sanitize_title( $profile_template->field->type );

		/**
		 * Filters the field classes to be applied to a field.
		 *
		 * @since 1.1.0
		 *
		 * @param array $css_classes Array of classes to be applied to field. Passed by reference.
		 */
		$css_classes = apply_filters_ref_array( 'bp_field_css_classes', array( &$css_classes ) );

		/**
		 * Filters the class HTML attribute to be used on a field.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value class HTML attribute with imploded classes.
		 */
		return apply_filters( 'bp_get_field_css_class', ' class="' . implode( ' ', $css_classes ) . '"' );
	}

function bp_field_has_data() {
	global $profile_template;
	return $profile_template->field_has_data;
}

function bp_field_has_public_data() {
	global $profile_template;

	if ( ! empty( $profile_template->field_has_data ) ) {
		return true;
	}

	return false;
}

function bp_the_profile_group_id() {
	echo bp_get_the_profile_group_id();
}
	function bp_get_the_profile_group_id() {
		global $group;

		/**
		 * Filters the profile group ID.
		 *
		 * @since 1.1.0
		 *
		 * @param int $id ID for the profile group.
		 */
		return apply_filters( 'bp_get_the_profile_group_id', $group->id );
	}

function bp_the_profile_group_name() {
	echo bp_get_the_profile_group_name();
}
	function bp_get_the_profile_group_name() {
		global $group;

		/**
		 * Filters the profile group name.
		 *
		 * @since 1.0.0
		 *
		 * @param string $name Name for the profile group.
		 */
		return apply_filters( 'bp_get_the_profile_group_name', $group->name );
	}

function bp_the_profile_group_slug() {
	echo bp_get_the_profile_group_slug();
}
	function bp_get_the_profile_group_slug() {
		global $group;

		/**
		 * Filters the profile group slug.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value Slug for the profile group.
		 */
		return apply_filters( 'bp_get_the_profile_group_slug', sanitize_title( $group->name ) );
	}

function bp_the_profile_group_description() {
	echo bp_get_the_profile_group_description();
}
	function bp_get_the_profile_group_description() {
		global $group;

		/**
		 * Filters the profile group description.
		 *
		 * @since 1.0.0
		 *
		 * @param string $description Description for the profile group.
		 */
		return apply_filters( 'bp_get_the_profile_group_description', $group->description );
	}

function bp_the_profile_group_edit_form_action() {
	echo bp_get_the_profile_group_edit_form_action();
}
	function bp_get_the_profile_group_edit_form_action() {
		global $group;

		// Build the form action URL.
		$form_action = trailingslashit( bp_displayed_user_domain() . bp_get_profile_slug() . '/edit/group/' . $group->id );

		/**
		 * Filters the action for the profile group edit form.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value URL for the action attribute on the
		 *                      profile group edit form.
		 */
		return apply_filters( 'bp_get_the_profile_group_edit_form_action', $form_action );
	}

function bp_the_profile_group_field_ids() {
	echo bp_get_the_profile_group_field_ids();
}
	function bp_get_the_profile_group_field_ids() {
		global $group;

		$field_ids = '';

		if ( !empty( $group->fields ) ) {
			foreach ( (array) $group->fields as $field ) {
				$field_ids .= $field->id . ',';
			}
		}

		return substr( $field_ids, 0, -1 );
	}

/**
 * Output a comma-separated list of field IDs that are to be submitted on profile edit.
 *
 * @since 2.1.0
 */
function bp_the_profile_field_ids() {
	echo bp_get_the_profile_field_ids();
}
	/**
	 * Generate a comma-separated list of field IDs that are to be submitted on profile edit.
	 *
	 * @since 2.1.0
	 *
	 * @return string
	 */
	function bp_get_the_profile_field_ids() {
		global $profile_template;

		$field_ids = array();
		foreach ( $profile_template->groups as $group ) {
			if ( ! empty( $group->fields ) ) {
				$field_ids = array_merge( $field_ids, wp_list_pluck( $group->fields, 'id' ) );
			}
		}

		$field_ids = implode( ',', wp_parse_id_list( $field_ids ) );

		/**
		 * Filters the comma-separated list of field IDs.
		 *
		 * @since 2.1.0
		 *
		 * @param string $field_ids Comma-separated field IDs.
		 */
		return apply_filters( 'bp_get_the_profile_field_ids', $field_ids );
	}

function bp_profile_fields() {
	global $profile_template;
	return $profile_template->profile_fields();
}

function bp_the_profile_field() {
	global $profile_template;
	return $profile_template->the_profile_field();
}

function bp_the_profile_field_id() {
	echo bp_get_the_profile_field_id();
}
	function bp_get_the_profile_field_id() {
		global $field;

		/**
		 * Filters the profile field ID.
		 *
		 * @since 1.1.0
		 *
		 * @param int $id ID for the profile field.
		 */
		return apply_filters( 'bp_get_the_profile_field_id', $field->id );
	}

function bp_the_profile_field_name() {
	echo bp_get_the_profile_field_name();
}
	function bp_get_the_profile_field_name() {
		global $field;

		/**
		 * Filters the profile field name.
		 *
		 * @since 1.0.0
		 *
		 * @param string $name Name for the profile field.
		 */
		return apply_filters( 'bp_get_the_profile_field_name', $field->name );
	}

function bp_the_profile_field_value() {
	echo bp_get_the_profile_field_value();
}
	function bp_get_the_profile_field_value() {
		global $field;

		$field->data->value = bp_unserialize_profile_field( $field->data->value );

		/**
		 * Filters the profile field value.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value Value for the profile field.
		 * @param string $type  Type for the profile field.
		 * @param int    $id    ID for the profile field.
		 */
		return apply_filters( 'bp_get_the_profile_field_value', $field->data->value, $field->type, $field->id );
	}

function bp_the_profile_field_edit_value() {
	echo bp_get_the_profile_field_edit_value();
}
	function bp_get_the_profile_field_edit_value() {
		global $field;

		/**
		 * Check to see if the posted value is different, if it is re-display this
		 * value as long as it's not empty and a required field.
		 */
		if ( ! isset( $field->data ) ) {
			$field->data = new stdClass;
		}

		if ( ! isset( $field->data->value ) ) {
			$field->data->value = '';
		}

		if ( isset( $_POST['field_' . $field->id] ) && $field->data->value != $_POST['field_' . $field->id] ) {
			if ( ! empty( $_POST['field_' . $field->id] ) ) {
				$field->data->value = $_POST['field_' . $field->id];
			} else {
				$field->data->value = '';
			}
		}

		$field_value = isset( $field->data->value ) ? bp_unserialize_profile_field( $field->data->value ) : '';

		/**
		 * Filters the profile field edit value.
		 *
		 * @since 1.1.0
		 *
		 * @param string $field_value Current field edit value.
		 * @param string $type        Type for the profile field.
		 * @param int    $id          ID for the profile field.
		 */
		return apply_filters( 'bp_get_the_profile_field_edit_value', $field_value, $field->type, $field->id );
	}

function bp_the_profile_field_type() {
	echo bp_get_the_profile_field_type();
}
	function bp_get_the_profile_field_type() {
		global $field;

		/**
		 * Filters the profile field type.
		 *
		 * @since 1.1.0
		 *
		 * @param string $type Type for the profile field.
		 */
		return apply_filters( 'bp_the_profile_field_type', $field->type );
	}

function bp_the_profile_field_description() {
	echo bp_get_the_profile_field_description();
}
	function bp_get_the_profile_field_description() {
		global $field;

		/**
		 * Filters the profile field description.
		 *
		 * @since 1.1.0
		 *
		 * @param string $description Description for the profile field.
		 */
		return apply_filters( 'bp_get_the_profile_field_description', $field->description );
	}

function bp_the_profile_field_input_name() {
	echo bp_get_the_profile_field_input_name();
}
	function bp_get_the_profile_field_input_name() {
		global $field;

		/**
		 * Filters the profile field input name.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value Value used for the name attribute on an input.
		 */
		return apply_filters( 'bp_get_the_profile_field_input_name', 'field_' . $field->id );
	}

/**
 * Returns the action name for any signup errors related to this profile field.
 *
 * In the registration templates, signup errors are pulled from the global
 * object and rendered at actions that look like 'bp_field_12_errors'. This
 * function allows the action name to be easily concatenated and called in the
 * following fashion:
 *   do_action( bp_get_the_profile_field_errors_action() );
 *
 * @since 1.8.0
 *
 * @return string The _errors action name corresponding to this profile field.
 */
function bp_get_the_profile_field_errors_action() {
	global $field;
	return 'bp_field_' . $field->id . '_errors';
}

/**
 * Displays field options HTML for field types of 'selectbox', 'multiselectbox',
 * 'radio', 'checkbox', and 'datebox'.
 *
 * @since 1.1.0
 *
 * @uses bp_get_the_profile_field_options()
 *
 * @param array $args Specify type for datebox. Allowed 'day', 'month', 'year'.
 */
function bp_the_profile_field_options( $args = array() ) {
	echo bp_get_the_profile_field_options( $args );
}
	/**
	 * Retrieves field options HTML for field types of 'selectbox', 'multiselectbox', 'radio', 'checkbox', and 'datebox'.
	 *
	 * @since 1.1.0
	 *
	 * @uses BP_XProfile_Field::get_children()
	 * @uses BP_XProfile_ProfileData::get_value_byid()
	 *
	 * @param array $args {
	 *     Array of optional arguments.
	 *     @type string|bool $type    Type of datebox. False if it's not a
	 *                                datebox, otherwise 'day, 'month', or 'year'. Default: false.
	 *     @type int         $user_id ID of the user whose profile values should be
	 *                                used when rendering options. Default: displayed user.
	 * }
	 *
	 * @return string $vaue Field options markup.
	 */
	function bp_get_the_profile_field_options( $args = array() ) {
		global $field;

		$args = bp_parse_args( $args, array(
			'type'    => false,
			'user_id' => bp_displayed_user_id(),
		), 'get_the_profile_field_options' );

		/**
		 * In some cases, the $field global is not an instantiation of the BP_XProfile_Field class.
		 * However, we have to make sure that all data originally in $field gets merged back in, after reinstantiation.
		 */
		if ( ! method_exists( $field, 'get_children' ) ) {
			$field_obj = xprofile_get_field( $field->id );

			foreach ( $field as $field_prop => $field_prop_value ) {
				if ( ! isset( $field_obj->{$field_prop} ) ) {
					$field_obj->{$field_prop} = $field_prop_value;
				}
			}

			$field = $field_obj;
		}

		ob_start();
		$field->type_obj->edit_field_options_html( $args );
		$html = ob_get_contents();
		ob_end_clean();

		return $html;
	}

function bp_the_profile_field_is_required() {
	echo bp_get_the_profile_field_is_required();
}
	function bp_get_the_profile_field_is_required() {
		global $field;

		$retval = false;

		if ( isset( $field->is_required ) ) {
			$retval = $field->is_required;
		}

		/**
		 * Filters whether or not a profile field is required.
		 *
		 * @since 1.1.0
		 *
		 * @param bool $retval Whether or not the field is required.
		 */
		return apply_filters( 'bp_get_the_profile_field_is_required', (bool) $retval );
	}

/**
 * Echo the visibility level of this field.
 */
function bp_the_profile_field_visibility_level() {
	echo bp_get_the_profile_field_visibility_level();
}
	/**
	 * Return the visibility level of this field.
	 */
	function bp_get_the_profile_field_visibility_level() {
		global $field;

		// On the registration page, values stored in POST should take
		// precedence over default visibility, so that submitted values
		// are not lost on failure.
		if ( bp_is_register_page() && ! empty( $_POST['field_' . $field->id . '_visibility'] ) ) {
			$retval = esc_attr( $_POST['field_' . $field->id . '_visibility'] );
		} else {
			$retval = ! empty( $field->visibility_level ) ? $field->visibility_level : 'public';
		}

		/**
		 * Filters the profile field visibility level.
		 *
		 * @since 1.6.0
		 *
		 * @param string $retval Field visibility level.
		 */
		return apply_filters( 'bp_get_the_profile_field_visibility_level', $retval );
	}

/**
 * Echo the visibility level label of this field.
 */
function bp_the_profile_field_visibility_level_label() {
	echo bp_get_the_profile_field_visibility_level_label();
}
	/**
	 * Return the visibility level label of this field.
	 */
	function bp_get_the_profile_field_visibility_level_label() {
		global $field;

		// On the registration page, values stored in POST should take
		// precedence over default visibility, so that submitted values
		// are not lost on failure.
		if ( bp_is_register_page() && ! empty( $_POST['field_' . $field->id . '_visibility'] ) ) {
			$level = esc_html( $_POST['field_' . $field->id . '_visibility'] );
		} else {
			$level = ! empty( $field->visibility_level ) ? $field->visibility_level : 'public';
		}

		$fields = bp_xprofile_get_visibility_levels();

		/**
		 * Filters the profile field visibility level label.
		 *
		 * @since 1.6.0
		 *
		 * @param string $retval Field visibility level label.
		 */
		return apply_filters( 'bp_get_the_profile_field_visibility_level_label', $fields[ $level ]['label'] );
	}


function bp_unserialize_profile_field( $value ) {
	if ( is_serialized($value) ) {
		$field_value = maybe_unserialize($value);
		$field_value = implode( ', ', $field_value );
		return $field_value;
	}

	return $value;
}

function bp_profile_field_data( $args = '' ) {
	echo bp_get_profile_field_data( $args );
}
	function bp_get_profile_field_data( $args = '' ) {

		$r = wp_parse_args( $args, array(
			'field'   => false, // Field name or ID.
			'user_id' => bp_displayed_user_id()
		) );

		/**
		 * Filters the profile field data.
		 *
		 * @since 1.2.0
		 *
		 * @param mixed $value Profile data for a specific field for the user.
		 */
		return apply_filters( 'bp_get_profile_field_data', xprofile_get_field_data( $r['field'], $r['user_id'] ) );
	}

/**
 * Get all profile field groups.
 *
 * @since 2.1.0
 *
 * @return array $groups
 */
function bp_profile_get_field_groups() {

	$groups = wp_cache_get( 'all', 'bp_xprofile_groups' );
	if ( false === $groups ) {
		$groups = bp_xprofile_get_groups( array( 'fetch_fields' => true ) );
		wp_cache_set( 'all', $groups, 'bp_xprofile' );
	}

	/**
	 * Filters all profile field groups.
	 *
	 * @since 2.1.0
	 *
	 * @param array $groups Array of available profile field groups.
	 */
	return apply_filters( 'bp_profile_get_field_groups', $groups );
}

/**
 * Check if there is more than one group of fields for the profile being edited.
 *
 * @since 2.1.0
 *
 * @return bool True if there is more than one profile field group.
 */
function bp_profile_has_multiple_groups() {
	$has_multiple_groups = count( (array) bp_profile_get_field_groups() ) > 1;

	/**
	 * Filters if there is more than one group of fields for the profile being edited.
	 *
	 * @since 2.1.0
	 *
	 * @param bool $has_multiple_groups Whether or not there are multiple groups.
	 */
	return (bool) apply_filters( 'bp_profile_has_multiple_groups', $has_multiple_groups );
}

/**
 * Output the tabs to switch between profile field groups.
 *
 * @since 1.0.0
 */
function bp_profile_group_tabs() {
	echo bp_get_profile_group_tabs();

	/**
	 * Fires at the end of the tab output for switching between profile field
	 * groups. This action is in a strange place for legacy reasons.
	 *
	 * @since 1.0.0
	 */
	do_action( 'xprofile_profile_group_tabs' );
}

/**
 * Return the XProfile group tabs.
 *
 * @since 2.3.0
 *
 * @return string
 */
function bp_get_profile_group_tabs() {

	// Get field group data.
	$groups     = bp_profile_get_field_groups();
	$group_name = bp_get_profile_group_name();
	$tabs       = array();

	// Loop through field groups and put a tab-lst together.
	for ( $i = 0, $count = count( $groups ); $i < $count; ++$i ) {

		// Setup the selected class.
		$selected = '';
		if ( $group_name === $groups[ $i ]->name ) {
			$selected = ' class="current"';
		}

		// Skip if group has no fields.
		if ( empty( $groups[ $i ]->fields ) ) {
			continue;
		}

		// Build the profile field group link.
		$link   = trailingslashit( bp_displayed_user_domain() . bp_get_profile_slug() . '/edit/group/' . $groups[ $i ]->id );

		// Add tab to end of tabs array.
		$tabs[] = sprintf(
			'<li %1$s><a href="%2$s">%3$s</a></li>',
			$selected,
			esc_url( $link ),
			esc_html( apply_filters( 'bp_get_the_profile_group_name', $groups[ $i ]->name ) )
		);
	}

	/**
	 * Filters the tabs to display for profile field groups.
	 *
	 * @since 1.5.0
	 *
	 * @param array  $tabs       Array of tabs to display.
	 * @param array  $groups     Array of profile groups.
	 * @param string $group_name Name of the current group displayed.
	 */
	$tabs = apply_filters( 'xprofile_filter_profile_group_tabs', $tabs, $groups, $group_name );

	return join( '', $tabs );
}

function bp_profile_group_name( $deprecated = true ) {
	if ( !$deprecated ) {
		return bp_get_profile_group_name();
	} else {
		echo bp_get_profile_group_name();
	}
}
	function bp_get_profile_group_name() {

		// Check action variable.
		$group_id = bp_action_variable( 1 );
		if ( empty( $group_id ) || ! is_numeric( $group_id ) ) {
			$group_id = 1;
		}

		// Check for cached group.
		$group = new BP_XProfile_Group( $group_id );

		/**
		 * Filters the profile group name.
		 *
		 * @since 1.0.0
		 *
		 * @param string $name Name of the profile group.
		 */
		return apply_filters( 'bp_get_profile_group_name', $group->name );
	}

function bp_profile_last_updated() {

	$last_updated = bp_get_profile_last_updated();

	if ( empty( $last_updated ) ) {
		_e( 'Profile not recently updated.', 'buddypress' );
	} else {
		echo $last_updated;
	}
}
	function bp_get_profile_last_updated() {

		$last_updated = bp_get_user_meta( bp_displayed_user_id(), 'profile_last_updated', true );

		if ( ! empty( $last_updated ) ) {

			/**
			 * Filters the formatted string used to display when a profile was last updated.
			 *
			 * @since 1.0.0
			 *
			 * @param string $value Formatted last updated indicator string.
			 */
			return apply_filters( 'bp_get_profile_last_updated', sprintf( __( 'Profile updated %s', 'buddypress' ), bp_core_time_since( strtotime( $last_updated ) ) ) );
		}

		return false;
	}

function bp_current_profile_group_id() {
	echo bp_get_current_profile_group_id();
}
	function bp_get_current_profile_group_id() {
		$profile_group_id = bp_action_variable( 1 );
		if ( empty( $profile_group_id ) ) {
			$profile_group_id = 1;
		}

		/**
		 * Filters the current profile group ID.
		 *
		 * Possible values are admin/profile/edit/[group-id].
		 *
		 * @since 1.1.0
		 *
		 * @param string $profile_group_id Current profile group ID.
		 */
		return apply_filters( 'bp_get_current_profile_group_id', $profile_group_id );
	}

function bp_avatar_delete_link() {
	echo bp_get_avatar_delete_link();
}
	function bp_get_avatar_delete_link() {

		/**
		 * Filters the link used for deleting an avatar.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value Nonced URL used for deleting an avatar.
		 */
		return apply_filters( 'bp_get_avatar_delete_link', wp_nonce_url( bp_displayed_user_domain() . bp_get_profile_slug() . '/change-avatar/delete-avatar/', 'bp_delete_avatar_link' ) );
	}

function bp_edit_profile_button() {
	bp_button( array(
		'id'                => 'edit_profile',
		'component'         => 'xprofile',
		'must_be_logged_in' => true,
		'block_self'        => true,
		'link_href'         => trailingslashit( bp_displayed_user_domain() . bp_get_profile_slug() . '/edit' ),
		'link_class'        => 'edit',
		'link_text'         => __( 'Edit Profile', 'buddypress' ),
		'link_title'        => __( 'Edit Profile', 'buddypress' ),
	) );
}

/** Visibility ****************************************************************/

/**
 * Echo the field visibility radio buttons.
 *
 * @param array|string $args Args for the radio buttons.
 */
function bp_profile_visibility_radio_buttons( $args = '' ) {
	echo bp_profile_get_visibility_radio_buttons( $args );
}
	/**
	 * Return the field visibility radio buttons.
	 *
	 * @param array|string $args Args for the radio buttons.
	 *
	 * @return string $retval
	 */
	function bp_profile_get_visibility_radio_buttons( $args = '' ) {

		// Parse optional arguments.
		$r = bp_parse_args( $args, array(
			'field_id'     => bp_get_the_profile_field_id(),
			'before'       => '<ul class="radio">',
			'after'        => '</ul>',
			'before_radio' => '<li class="%s">',
			'after_radio'  => '</li>',
			'class'        => 'bp-xprofile-visibility'
		), 'xprofile_visibility_radio_buttons' );

		// Empty return value, filled in below if a valid field ID is found.
		$retval = '';

		// Only do-the-do if there's a valid field ID.
		if ( ! empty( $r['field_id'] ) ) :

			// Start the output buffer.
			ob_start();

			// Output anything before.
			echo $r['before']; ?>

			<?php if ( bp_current_user_can( 'bp_xprofile_change_field_visibility' ) ) : ?>

				<?php foreach( bp_xprofile_get_visibility_levels() as $level ) : ?>

					<?php printf( $r['before_radio'], esc_attr( $level['id'] ) ); ?>

					<label for="<?php echo esc_attr( 'see-field_' . $r['field_id'] . '_' . $level['id'] ); ?>">
						<input type="radio" id="<?php echo esc_attr( 'see-field_' . $r['field_id'] . '_' . $level['id'] ); ?>" name="<?php echo esc_attr( 'field_' . $r['field_id'] . '_visibility' ); ?>" value="<?php echo esc_attr( $level['id'] ); ?>" <?php checked( $level['id'], bp_get_the_profile_field_visibility_level() ); ?> />
						<span class="field-visibility-text"><?php echo esc_html( $level['label'] ); ?></span>
					</label>

					<?php echo $r['after_radio']; ?>

				<?php endforeach; ?>

			<?php endif;

			// Output anything after.
			echo $r['after'];

			// Get the output buffer and empty it.
			$retval = ob_get_clean();
		endif;

		/**
		 * Filters the radio buttons for setting visibility.
		 *
		 * @since 1.6.0
		 *
		 * @param string $retval HTML output for the visibility radio buttons.
		 * @param array  $r      Parsed arguments to be used with display.
		 * @param array  $args   Original passed in arguments to be used with display.
		 */
		return apply_filters( 'bp_profile_get_visibility_radio_buttons', $retval, $r, $args );
	}

/**
 * Output the XProfile field visibility select list for settings.
 *
 * @since 2.0.0
 *
 * @param array|string $args Args for the select list.
 */
function bp_profile_settings_visibility_select( $args = '' ) {
	echo bp_profile_get_settings_visibility_select( $args );
}
	/**
	 * Return the XProfile field visibility select list for settings.
	 *
	 * @since 2.0.0
	 *
	 * @param array|string $args Args for the select list.
	 *
	 * @return string $retval
	 */
	function bp_profile_get_settings_visibility_select( $args = '' ) {

		// Parse optional arguments.
		$r = bp_parse_args( $args, array(
			'field_id' => bp_get_the_profile_field_id(),
			'before'   => '',
			'after'    => '',
			'class'    => 'bp-xprofile-visibility'
		), 'xprofile_settings_visibility_select' );

		// Empty return value, filled in below if a valid field ID is found.
		$retval = '';

		// Only do-the-do if there's a valid field ID.
		if ( ! empty( $r['field_id'] ) ) :

			// Start the output buffer.
			ob_start();

			// Output anything before.
			echo $r['before']; ?>

			<?php if ( bp_current_user_can( 'bp_xprofile_change_field_visibility' ) ) : ?>

				<label for="<?php echo esc_attr( 'field_' . $r['field_id'] ) ; ?>_visibility" class="bp-screen-reader-text"><?php _e( 'Select visibility', 'buddypress' ); ?></label>
				<select class="<?php echo esc_attr( $r['class'] ); ?>" name="<?php echo esc_attr( 'field_' . $r['field_id'] ) ; ?>_visibility" id="<?php echo esc_attr( 'field_' . $r['field_id'] ) ; ?>_visibility">

					<?php foreach ( bp_xprofile_get_visibility_levels() as $level ) : ?>

						<option value="<?php echo esc_attr( $level['id'] ); ?>" <?php selected( $level['id'], bp_get_the_profile_field_visibility_level() ); ?>><?php echo esc_html( $level['label'] ); ?></option>

					<?php endforeach; ?>

				</select>

			<?php else : ?>

				<span class="field-visibility-settings-notoggle" title="<?php esc_attr_e( "This field's visibility cannot be changed.", 'buddypress' ); ?>"><?php bp_the_profile_field_visibility_level_label(); ?></span>

			<?php endif;

			// Output anything after.
			echo $r['after'];

			// Get the output buffer and empty it.
			$retval = ob_get_clean();
		endif;

		/**
		 * Filters the dropdown list for setting visibility.
		 *
		 * @since 2.0.0
		 *
		 * @param string $retval HTML output for the visibility dropdown list.
		 * @param array  $r      Parsed arguments to be used with display.
		 * @param array  $args   Original passed in arguments to be used with display.
		 */
		return apply_filters( 'bp_profile_settings_visibility_select', $retval, $r, $args );
	}

/**
 * Output the 'required' markup in extended profile field labels.
 *
 * @since 2.4.0
 *
 * @return string HTML for the required label.
 */
function bp_the_profile_field_required_label() {
	echo bp_get_the_profile_field_required_label();
}

	/**
	 * Return the 'required' markup in extended profile field labels.
	 *
	 * @since 2.4.0
	 *
	 * @return string HTML for the required label.
	 */
	function bp_get_the_profile_field_required_label() {
		$retval = '';

		if ( bp_get_the_profile_field_is_required() ) {
			$translated_string = __( '(required)', 'buddypress' );

			$retval = ' <span class="bp-required-field-label">';
			$retval .= apply_filters( 'bp_get_the_profile_field_required_label', $translated_string, bp_get_the_profile_field_id() );
			$retval .= '</span>';

		}

		return $retval;
	}

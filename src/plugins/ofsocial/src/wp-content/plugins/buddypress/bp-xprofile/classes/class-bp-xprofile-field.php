<?php
/**
 * BuddyPress XProfile Classes.
 *
 * @package BuddyPress
 * @subpackage XProfileClasses
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

class BP_XProfile_Field {

	/**
	 * Field ID.
	 *
	 * @since 1.0.0
	 *
	 * @var int ID of field.
	 */
	public $id;

	/**
	 * Field group ID.
	 *
	 * @since 1.0.0
	 *
	 * @var int Field group ID for field.
	 */
	public $group_id;

	/**
	 * Field parent ID.
	 *
	 * @since 1.0.0
	 *
	 * @var int Parent ID of field.
	 */
	public $parent_id;

	/**
	 * Field type.
	 *
	 * @since 1.0.0
	 *
	 * @var string Field type.
	 */
	public $type;

	/**
	 * Field name.
	 *
	 * @since 1.0.0
	 *
	 * @var string Field name.
	 */
	public $name;

	/**
	 * Field description.
	 *
	 * @since 1.0.0
	 *
	 * @var string Field description.
	 */
	public $description;

	/**
	 * Required field?
	 *
	 * @since 1.0.0
	 *
	 * @var bool Is field required to be filled out?
	 */
	public $is_required;

	/**
	 * Deletable field?
	 *
	 * @since 1.0.0
	 *
	 * @var int Can field be deleted?
	 */
	public $can_delete = '1';

	/**
	 * Field position.
	 *
	 * @since 1.0.0
	 *
	 * @var int Field position.
	 */
	public $field_order;

	/**
	 * Option order.
	 *
	 * @since 1.0.0
	 *
	 * @var int Option order.
	 */
	public $option_order;

	/**
	 * Order child fields.
	 *
	 * @since 1.0.0
	 *
	 * @var string Order child fields by.
	 */
	public $order_by;

	/**
	 * Is this the default option?
	 *
	 * @since 1.0.0
	 *
	 * @var bool Is this the default option for this field?
	 */
	public $is_default_option;

	/**
	 * Field data visibility.
	 *
	 * @since 1.9.0
	 * @since 2.4.0 Property marked protected. Now accessible by magic method or by `get_default_visibility()`.
	 *
	 * @var string Default field data visibility.
	 */
	protected $default_visibility;

	/**
	 * Is the visibility able to be modified?
	 *
	 * @since 2.3.0
	 * @since 2.4.0 Property marked protected. Now accessible by magic method or by `get_allow_custom_visibility()`.
	 *
	 * @var string Members are allowed/disallowed to modify data visibility.
	 */
	protected $allow_custom_visibility;

	/**
	 * Field type option.
	 *
	 * @since 2.0.0
	 *
	 * @var BP_XProfile_Field_Type Field type object used for validation.
	 */
	public $type_obj = null;

	/**
	 * Field data for user ID.
	 *
	 * @since 2.0.0
	 *
	 * @var BP_XProfile_ProfileData Field data for user ID.
	 */
	public $data;

	/**
	 * Member types to which the profile field should be applied.
	 *
	 * @since 2.4.0
	 * @var array Array of member types.
	 */
	protected $member_types;

	/**
	 * Initialize and/or populate profile field.
	 *
	 * @since 1.1.0
	 *
	 * @param int|null $id Field ID.
	 * @param int|null $user_id User ID.
	 * @param bool     $get_data Get data.
	 */
	public function __construct( $id = null, $user_id = null, $get_data = true ) {

		if ( ! empty( $id ) ) {
			$this->populate( $id, $user_id, $get_data );

		// Initialise the type obj to prevent fatals when creating new profile fields.
		} else {
			$this->type_obj            = bp_xprofile_create_field_type( 'textbox' );
			$this->type_obj->field_obj = $this;
		}
	}

	/**
	 * Populate a profile field object.
	 *
	 * @since 1.1.0
	 *
	 * @global object $wpdb
	 * @global object $userdata
	 *
	 * @param int  $id Field ID.
	 * @param int  $user_id User ID.
	 * @param bool $get_data Get data.
	 */
	public function populate( $id, $user_id = null, $get_data = true ) {
		global $wpdb, $userdata;

		if ( empty( $user_id ) ) {
			$user_id = isset( $userdata->ID ) ? $userdata->ID : 0;
		}

		$bp    = buddypress();
		$field = $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$bp->profile->table_name_fields} WHERE id = %d", $id ) );

		$this->fill_data( $field );

		if ( ! empty( $get_data ) && ! empty( $user_id ) ) {
			$this->data = $this->get_field_data( $user_id );
		}
	}

	/**
	 * Retrieve a `BP_XProfile_Field` instance.
	 *
	 * @static
	 *
	 * @param int $field_id ID of the field.
	 * @return BP_XProfile_Field|false Field object if found, otherwise false.
	 */
	public static function get_instance( $field_id ) {
		global $wpdb;

		$field_id = (int) $field_id;
		if ( ! $field_id ) {
			return false;
		}

		$field = wp_cache_get( $field_id, 'bp_xprofile_fields' );
		if ( false === $field ) {
			$bp = buddypress();

			$field = $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$bp->profile->table_name_fields} WHERE id = %d", $field_id ) );

			wp_cache_add( $field->id, $field, 'bp_xprofile_fields' );

			if ( ! $field ) {
				return false;
			}
		}

		$_field = new BP_XProfile_Field();
		$_field->fill_data( $field );

		return $_field;
	}

	/**
	 * Fill object vars based on data passed to the method.
	 *
	 * @since 2.4.0
	 *
	 * @param array|object $args Array or object representing the `BP_XProfile_Field` properties.
	 *                           Generally, this is a row from the fields database table.
	 */
	public function fill_data( $args ) {
		if ( is_object( $args ) ) {
			$args = (array) $args;
		}

		foreach ( $args as $k => $v ) {
			if ( 'name' === $k || 'description' === $k ) {
				$v = stripslashes( $v );
			}
			$this->{$k} = $v;
		}

		// Create the field type and store a reference back to this object.
		$this->type_obj            = bp_xprofile_create_field_type( $this->type );
		$this->type_obj->field_obj = $this;;
	}

	/**
	 * Magic getter.
	 *
	 * @since 2.4.0
	 *
	 * @param string $key Property name.
	 * @return mixed
	 */
	public function __get( $key ) {
		switch ( $key ) {
			case 'default_visibility' :
				return $this->get_default_visibility();
				break;

			case 'allow_custom_visibility' :
				return $this->get_allow_custom_visibility();
				break;
		}
	}

	/**
	 * Magic issetter.
	 *
	 * @since 2.4.0
	 *
	 * @param string $key Property name.
	 * @return bool
	 */
	public function __isset( $key ) {
		switch ( $key ) {
			// Backward compatibility for when these were public methods.
			case 'allow_custom_visibility' :
			case 'default_visibility' :
				return true;
				break;
		}
	}

	/**
	 * Delete a profile field.
	 *
	 * @since 1.1.0
	 *
	 * @global object $wpdb
	 *
	 * @param boolean $delete_data Whether or not to delete data.
	 *
	 * @return boolean
	 */
	public function delete( $delete_data = false ) {
		global $wpdb;

		// Prevent deletion if no ID is present.
		// Prevent deletion by url when can_delete is false.
		// Prevent deletion of option 1 since this invalidates fields with options.
		if ( empty( $this->id ) || empty( $this->can_delete ) || ( $this->parent_id && $this->option_order == 1 ) ) {
			return false;
		}

		$bp  = buddypress();
		$sql = $wpdb->prepare( "DELETE FROM {$bp->profile->table_name_fields} WHERE id = %d OR parent_id = %d", $this->id, $this->id );

		if ( ! $wpdb->query( $sql ) ) {
			return false;
		}

		// Delete the data in the DB for this field.
		if ( true === $delete_data ) {
			BP_XProfile_ProfileData::delete_for_field( $this->id );
		}

		return true;
	}

	/**
	 * Save a profile field.
	 *
	 * @since 1.1.0
	 *
	 * @global object $wpdb
	 *
	 * @return boolean
	 */
	public function save() {
		global $wpdb;

		$bp = buddypress();

		$this->group_id     = apply_filters( 'xprofile_field_group_id_before_save',     $this->group_id,     $this->id );
		$this->parent_id    = apply_filters( 'xprofile_field_parent_id_before_save',    $this->parent_id,    $this->id );
		$this->type         = apply_filters( 'xprofile_field_type_before_save',         $this->type,         $this->id );
		$this->name         = apply_filters( 'xprofile_field_name_before_save',         $this->name,         $this->id );
		$this->description  = apply_filters( 'xprofile_field_description_before_save',  $this->description,  $this->id );
		$this->is_required  = apply_filters( 'xprofile_field_is_required_before_save',  $this->is_required,  $this->id );
		$this->order_by	    = apply_filters( 'xprofile_field_order_by_before_save',     $this->order_by,     $this->id );
		$this->field_order  = apply_filters( 'xprofile_field_field_order_before_save',  $this->field_order,  $this->id );
		$this->option_order = apply_filters( 'xprofile_field_option_order_before_save', $this->option_order, $this->id );
		$this->can_delete   = apply_filters( 'xprofile_field_can_delete_before_save',   $this->can_delete,   $this->id );
		$this->type_obj     = bp_xprofile_create_field_type( $this->type );

		/**
		 * Fires before the current field instance gets saved.
		 *
		 * Please use this hook to filter the properties above. Each part will be passed in.
		 *
		 * @since 1.0.0
		 *
		 * @param BP_XProfile_Field $this Current instance of the field being saved.
		 */
		do_action_ref_array( 'xprofile_field_before_save', array( $this ) );

		$is_new_field = is_null( $this->id );

		if ( ! $is_new_field ) {
			$sql = $wpdb->prepare( "UPDATE {$bp->profile->table_name_fields} SET group_id = %d, parent_id = 0, type = %s, name = %s, description = %s, is_required = %d, order_by = %s, field_order = %d, option_order = %d, can_delete = %d, is_default_option = %d WHERE id = %d", $this->group_id, $this->type, $this->name, $this->description, $this->is_required, $this->order_by, $this->field_order, $this->option_order, $this->can_delete, $this->is_default_option, $this->id );
		} else {
			$sql = $wpdb->prepare( "INSERT INTO {$bp->profile->table_name_fields} (group_id, parent_id, type, name, description, is_required, order_by, field_order, option_order, can_delete, is_default_option ) VALUES ( %d, %d, %s, %s, %s, %d, %s, %d, %d, %d, %d )", $this->group_id, $this->parent_id, $this->type, $this->name, $this->description, $this->is_required, $this->order_by, $this->field_order, $this->option_order, $this->can_delete, $this->is_default_option );
		}

		/**
		 * Check for null so field options can be changed without changing any
		 * other part of the field. The described situation will return 0 here.
		 */
		if ( $wpdb->query( $sql ) !== null ) {

			if ( $is_new_field ) {
				$this->id = $wpdb->insert_id;
			}

			// Only do this if we are editing an existing field.
			if ( ! $is_new_field ) {

				/**
				 * Remove any radio or dropdown options for this
				 * field. They will be re-added if needed.
				 * This stops orphan options if the user changes a
				 * field from a radio button field to a text box.
				 */
				$this->delete_children();
			}

			/**
			 * Check to see if this is a field with child options.
			 * We need to add the options to the db, if it is.
			 */
			if ( $this->type_obj->supports_options ) {

				$parent_id = $this->id;

				// Allow plugins to filter the field's child options (i.e. the items in a selectbox).
				$post_option  = ! empty( $_POST["{$this->type}_option"]           ) ? $_POST["{$this->type}_option"]           : '';
				$post_default = ! empty( $_POST["isDefault_{$this->type}_option"] ) ? $_POST["isDefault_{$this->type}_option"] : '';

				/**
				 * Filters the submitted field option value before saved.
				 *
				 * @since 1.5.0
				 *
				 * @param string            $post_option Submitted option value.
				 * @param BP_XProfile_Field $type        Current field type being saved for.
				 */
				$options      = apply_filters( 'xprofile_field_options_before_save', $post_option,  $this->type );

				/**
				 * Filters the default field option value before saved.
				 *
				 * @since 1.5.0
				 *
				 * @param string            $post_default Default option value.
				 * @param BP_XProfile_Field $type         Current field type being saved for.
				 */
				$defaults     = apply_filters( 'xprofile_field_default_before_save', $post_default, $this->type );

				$counter = 1;
				if ( !empty( $options ) ) {
					foreach ( (array) $options as $option_key => $option_value ) {
						$is_default = 0;

						if ( is_array( $defaults ) ) {
							if ( isset( $defaults[ $option_key ] ) ) {
								$is_default = 1;
							}
						} else {
							if ( (int) $defaults == $option_key ) {
								$is_default = 1;
							}
						}

						if ( '' != $option_value ) {
							$sql = $wpdb->prepare( "INSERT INTO {$bp->profile->table_name_fields} (group_id, parent_id, type, name, description, is_required, option_order, is_default_option) VALUES (%d, %d, 'option', %s, '', 0, %d, %d)", $this->group_id, $parent_id, $option_value, $counter, $is_default );
							if ( ! $wpdb->query( $sql ) ) {
								return false;
							}
						}

						$counter++;
					}
				}
			}

			/**
			 * Fires after the current field instance gets saved.
			 *
			 * @since 1.0.0
			 *
			 * @param BP_XProfile_Field $this Current instance of the field being saved.
			 */
			do_action_ref_array( 'xprofile_field_after_save', array( $this ) );

			// Recreate type_obj in case someone changed $this->type via a filter
			$this->type_obj            = bp_xprofile_create_field_type( $this->type );
			$this->type_obj->field_obj = $this;

			return $this->id;
		} else {
			return false;
		}
	}

	/**
	 * Get field data for a user ID.
	 *
	 * @since 1.2.0
	 *
	 * @param int $user_id ID of the user to get field data for.
	 *
	 * @return object
	 */
	public function get_field_data( $user_id = 0 ) {
		return new BP_XProfile_ProfileData( $this->id, $user_id );
	}

	/**
	 * Get all child fields for this field ID.
	 *
	 * @since 1.2.0
	 *
	 * @global object $wpdb
	 *
	 * @param bool $for_editing Whether or not the field is for editing.
	 *
	 * @return array
	 */
	public function get_children( $for_editing = false ) {
		global $wpdb;

		// This is done here so we don't have problems with sql injection.
		if ( empty( $for_editing ) && ( 'asc' === $this->order_by ) ) {
			$sort_sql = 'ORDER BY name ASC';
		} elseif ( empty( $for_editing ) && ( 'desc' === $this->order_by ) ) {
			$sort_sql = 'ORDER BY name DESC';
		} else {
			$sort_sql = 'ORDER BY option_order ASC';
		}

		// This eliminates a problem with getting all fields when there is no
		// id for the object.
		if ( empty( $this->id ) ) {
			$parent_id = -1;
		} else {
			$parent_id = $this->id;
		}

		$bp  = buddypress();
		$sql = $wpdb->prepare( "SELECT * FROM {$bp->profile->table_name_fields} WHERE parent_id = %d AND group_id = %d {$sort_sql}", $parent_id, $this->group_id );

		$children = $wpdb->get_results( $sql );

		/**
		 * Filters the found children for a field.
		 *
		 * @since 1.2.5
		 *
		 * @param object $children    Found children for a field.
		 * @param bool   $for_editing Whether or not the field is for editing.
		 */
		return apply_filters( 'bp_xprofile_field_get_children', $children, $for_editing );
	}

	/**
	 * Delete all field children for this field.
	 *
	 * @since 1.2.0
	 *
	 * @global object $wpdb
	 */
	public function delete_children() {
		global $wpdb;

		$bp  = buddypress();
		$sql = $wpdb->prepare( "DELETE FROM {$bp->profile->table_name_fields} WHERE parent_id = %d", $this->id );

		$wpdb->query( $sql );
	}

	/**
	 * Gets the member types to which this field should be available.
	 *
	 * Will not return inactive member types, even if associated metadata is found.
	 *
	 * 'null' is a special pseudo-type, which represents users that do not have a member type.
	 *
	 * @since 2.4.0
	 *
	 * @return array Array of member type names.
	 */
	public function get_member_types() {
		if ( ! is_null( $this->member_types ) ) {
			return $this->member_types;
		}

		$raw_types = bp_xprofile_get_meta( $this->id, 'field', 'member_type', false );

		// If `$raw_types` is not an array, it probably means this is a new field (id=0).
		if ( ! is_array( $raw_types ) ) {
			$raw_types = array();
		}

		// If '_none' is found in the array, it overrides all types.
		$types = array();
		if ( ! in_array( '_none', $raw_types ) ) {
			$registered_types = bp_get_member_types();

			// Eliminate invalid member types saved in the database.
			foreach ( $raw_types as $raw_type ) {
				// 'null' is a special case - it represents users without a type.
				if ( 'null' === $raw_type || isset( $registered_types[ $raw_type ] ) ) {
					$types[] = $raw_type;
				}
			}

			// If no member types have been saved, intepret as *all* member types.
			if ( empty( $types ) ) {
				$types = array_values( $registered_types );

				// + the "null" type, ie users without a type.
				$types[] = 'null';
			}
		}

		/**
		 * Filters the member types to which an XProfile object should be applied.
		 *
		 * @since 2.4.0
		 *
		 * @param array             $types Member types.
		 * @param BP_XProfile_Field $field Field object.
		 */
		$this->member_types = apply_filters( 'bp_xprofile_field_member_types', $types, $this );

		return $this->member_types;
	}

	/**
	 * Sets the member types for this field.
	 *
	 * @since 2.4.0
	 *
	 * @param array $member_types Array of member types. Can include 'null' (users with no type) in addition to any
	 *                            registered types.
	 * @param bool  $append       Whether to append to existing member types. If false, all existing member type
	 *                            associations will be deleted before adding your `$member_types`. Default false.
	 *
	 * @return array Member types for the current field, after being saved.
	 */
	public function set_member_types( $member_types, $append = false ) {
		// Unset invalid member types.
		$types = array();
		foreach ( $member_types as $member_type ) {
			// 'null' is a special case - it represents users without a type.
			if ( 'null' === $member_type || bp_get_member_type_object( $member_type ) ) {
				$types[] = $member_type;
			}
		}

		// When `$append` is false, delete all existing types before adding new ones.
		if ( ! $append ) {
			bp_xprofile_delete_meta( $this->id, 'field', 'member_type' );

			/*
			 * We interpret an empty array as disassociating the field from all types. This is
			 * represented internally with the '_none' flag.
			 */
			if ( empty( $types ) ) {
				bp_xprofile_add_meta( $this->id, 'field', 'member_type', '_none' );
			}
		}

		/*
		 * Unrestricted fields are represented in the database as having no 'member_type'.
		 * We detect whether a field is being set to unrestricted by checking whether the
		 * list of types passed to the method is the same as the list of registered types,
		 * plus the 'null' pseudo-type.
		 */
		$_rtypes  = bp_get_member_types();
		$rtypes   = array_values( $_rtypes );
		$rtypes[] = 'null';

		sort( $types );
		sort( $rtypes );

		// Only save if this is a restricted field.
		if ( $types !== $rtypes ) {
			// Save new types.
			foreach ( $types as $type ) {
				bp_xprofile_add_meta( $this->id, 'field', 'member_type', $type );
			}
		}

		// Reset internal cache of member types.
		$this->member_types = null;

		/**
		 * Fires after a field's member types have been updated.
		 *
		 * @since 2.4.0
		 *
		 * @param BP_XProfile_Field $this Field object.
		 */
		do_action( 'bp_xprofile_field_set_member_type', $this );

		// Refetch fresh items from the database.
		return $this->get_member_types();
	}

	/**
	 * Gets a label representing the field's member types.
	 *
	 * This label is displayed alongside the field's name on the Profile Fields Dashboard panel.
	 *
	 * @since 2.4.0
	 *
	 * @return string
	 */
	public function get_member_type_label() {
		// Field 1 is always displayed to everyone, so never gets a label.
		if ( 1 == $this->id ) {
			return '';
		}

		// Return an empty string if no member types are registered.
		$all_types = bp_get_member_types();
		if ( empty( $all_types ) ) {
			return '';
		}

		$member_types = $this->get_member_types();

		// If the field applies to all member types, show no message.
		$all_types[] = 'null';
		if ( array_values( $all_types ) == $member_types ) {
			return '';
		}

		$label = '';
		if ( ! empty( $member_types ) ) {
			$has_null = false;
			$member_type_labels = array();
			foreach ( $member_types as $member_type ) {
				if ( 'null' === $member_type ) {
					$has_null = true;
					continue;
				} else {
					$mt_obj = bp_get_member_type_object( $member_type );
					$member_type_labels[] = $mt_obj->labels['name'];
				}
			}

			// Alphabetical sort.
			natcasesort( $member_type_labels );
			$member_type_labels = array_values( $member_type_labels );

			// Add the 'null' option to the end of the list.
			if ( $has_null ) {
				$member_type_labels[] = __( 'Users with no member type', 'buddypress' );
			}

			$label = sprintf( __( '(Member types: %s)', 'buddypress' ), implode( ', ', array_map( 'esc_html', $member_type_labels ) ) );
		} else {
			$label = '<span class="member-type-none-notice">' . __( '(Unavailable to all members)', 'buddypress' ) . '</span>';
		}

		return $label;
	}

	/**
	 * Get the field's default visibility setting.
	 *
	 * Lazy-loaded to reduce overhead.
	 *
	 * Defaults to 'public' if no visibility setting is found in the database.
	 *
	 * @since 2.4.0
	 *
	 * @return string
	 */
	public function get_default_visibility() {
		if ( ! isset( $this->default_visibility ) ) {
			$this->default_visibility = bp_xprofile_get_meta( $this->id, 'field', 'default_visibility' );

			if ( ! $this->default_visibility ) {
				$this->default_visibility = 'public';
			}
		}

		return $this->default_visibility;
	}

	/**
	 * Get whether the field's default visibility can be overridden by users.
	 *
	 * Lazy-loaded to reduce overhead.
	 *
	 * Defaults to 'allowed'.
	 *
	 * @since 4.4.0
	 *
	 * @return string 'disabled' or 'allowed'.
	 */
	public function get_allow_custom_visibility() {
		if ( ! isset( $this->allow_custom_visibility ) ) {
			$allow_custom_visibility = bp_xprofile_get_meta( $this->id, 'field', 'allow_custom_visibility' );

			if ( 'disabled' === $allow_custom_visibility ) {
				$this->allow_custom_visibility = 'disabled';
			} else {
				$this->allow_custom_visibility = 'allowed';
			}
		}

		return $this->allow_custom_visibility;
	}

	/** Static Methods ********************************************************/

	public static function get_type( $field_id = 0 ) {
		global $wpdb;

		// Bail if no field ID.
		if ( empty( $field_id ) ) {
			return false;
		}

		$bp   = buddypress();
		$sql  = $wpdb->prepare( "SELECT type FROM {$bp->profile->table_name_fields} WHERE id = %d", $field_id );
		$type = $wpdb->get_var( $sql );

		// Return field type.
		if ( ! empty( $type ) ) {
			return $type;
		}

		return false;
	}

	/**
	 * Delete all fields in a field group.
	 *
	 * @since 1.2.0
	 *
	 * @global object $wpdb
	 *
	 * @param int $group_id ID of the field group to delete fields from.
	 *
	 * @return boolean
	 */
	public static function delete_for_group( $group_id = 0 ) {
		global $wpdb;

		// Bail if no group ID.
		if ( empty( $group_id ) ) {
			return false;
		}

		$bp      = buddypress();
		$sql     = $wpdb->prepare( "DELETE FROM {$bp->profile->table_name_fields} WHERE group_id = %d", $group_id );
		$deleted = $wpdb->get_var( $sql );

		// Return true if fields were deleted.
		if ( false !== $deleted ) {
			return true;
		}

		return false;
	}

	/**
	 * Get field ID from field name.
	 *
	 * @since 1.5.0
	 *
	 * @global object $wpdb
	 *
	 * @param string $field_name Name of the field to query the ID for.
	 *
	 * @return boolean
	 */
	public static function get_id_from_name( $field_name = '' ) {
		global $wpdb;

		$bp = buddypress();

		if ( empty( $bp->profile->table_name_fields ) || empty( $field_name ) ) {
			return false;
		}

		$sql = $wpdb->prepare( "SELECT id FROM {$bp->profile->table_name_fields} WHERE name = %s AND parent_id = 0", $field_name );

		return $wpdb->get_var( $sql );
	}

	/**
	 * Update field position and/or field group when relocating.
	 *
	 * @since 1.5.0
	 *
	 * @global object $wpdb
	 *
	 * @param int      $field_id       ID of the field to update.
	 * @param int|null $position       Field position to update.
	 * @param int|null $field_group_id ID of the field group.
	 *
	 * @return boolean
	 */
	public static function update_position( $field_id, $position = null, $field_group_id = null ) {
		global $wpdb;

		// Bail if invalid position or field group.
		if ( ! is_numeric( $position ) || ! is_numeric( $field_group_id ) ) {
			return false;
		}

		// Get table name and field parent.
		$table_name = buddypress()->profile->table_name_fields;
		$sql        = $wpdb->prepare( "UPDATE {$table_name} SET field_order = %d, group_id = %d WHERE id = %d", $position, $field_group_id, $field_id );
		$parent     = $wpdb->query( $sql );

		// Update $field_id with new $position and $field_group_id.
		if ( ! empty( $parent ) && ! is_wp_error( $parent ) ) {

			// Update any children of this $field_id.
			$sql = $wpdb->prepare( "UPDATE {$table_name} SET group_id = %d WHERE parent_id = %d", $field_group_id, $field_id );
			$wpdb->query( $sql );

			return $parent;
		}

		return false;
	}

	/**
	 * Gets the IDs of fields applicable for a given member type or array of member types.
	 *
	 * @since 2.4.0
	 *
	 * @param string|array $member_types Member type or array of member types. Use 'any' to return unrestricted
	 *                                   fields (those available for anyone, regardless of member type).
	 *
	 * @return array Multi-dimensional array, with field IDs as top-level keys, and arrays of member types
	 *               associated with each field as values.
	 */
	public static function get_fields_for_member_type( $member_types ) {
		global $wpdb;

		$fields = array();

		if ( empty( $member_types ) ) {
			$member_types = array( 'any' );
		} elseif ( ! is_array( $member_types ) ) {
			$member_types = array( $member_types );
		}

		$bp = buddypress();

		// Pull up all recorded field member type data.
		$mt_meta = wp_cache_get( 'field_member_types', 'bp_xprofile' );
		if ( false === $mt_meta ) {
			$mt_meta = $wpdb->get_results( "SELECT object_id, meta_value FROM {$bp->profile->table_name_meta} WHERE meta_key = 'member_type' AND object_type = 'field'" );
			wp_cache_set( 'field_member_types', $mt_meta, 'bp_xprofile' );
		}

		// Keep track of all fields with recorded member_type metadata.
		$all_recorded_field_ids = wp_list_pluck( $mt_meta, 'object_id' );

		// Sort member_type matches in arrays, keyed by field_id.
		foreach ( $mt_meta as $_mt_meta ) {
			if ( ! isset( $fields[ $_mt_meta->object_id ] ) ) {
				$fields[ $_mt_meta->object_id ] = array();
			}

			$fields[ $_mt_meta->object_id ][] = $_mt_meta->meta_value;
		}

		/*
		 * Filter out fields that don't match any passed types, or those marked '_none'.
		 * The 'any' type is implicitly handled here: it will match no types.
		 */
		foreach ( $fields as $field_id => $field_types ) {
			if ( ! array_intersect( $field_types, $member_types ) ) {
				unset( $fields[ $field_id ] );
			}
		}

		// Any fields with no member_type metadata are available to all member types.
		if ( ! in_array( '_none', $member_types ) ) {
			if ( ! empty( $all_recorded_field_ids ) ) {
				$all_recorded_field_ids_sql = implode( ',', array_map( 'absint', $all_recorded_field_ids ) );
				$unrestricted_field_ids = $wpdb->get_col( "SELECT id FROM {$bp->profile->table_name_fields} WHERE id NOT IN ({$all_recorded_field_ids_sql})" );
			} else {
				$unrestricted_field_ids = $wpdb->get_col( "SELECT id FROM {$bp->profile->table_name_fields}" );
			}

			// Append the 'null' pseudo-type.
			$all_member_types   = bp_get_member_types();
			$all_member_types   = array_values( $all_member_types );
			$all_member_types[] = 'null';

			foreach ( $unrestricted_field_ids as $unrestricted_field_id ) {
				$fields[ $unrestricted_field_id ] = $all_member_types;
			}
		}

		return $fields;
	}

	/**
	 * Validate form field data on sumbission.
	 *
	 * @since 2.2.0
	 *
	 * @global $message
	 *
	 * @return boolean
	 */
	public static function admin_validate() {
		global $message;

		// Check field name.
		if ( ! isset( $_POST['title'] ) || ( '' === $_POST['title'] ) ) {
			$message = esc_html__( 'Profile fields must have a name.', 'buddypress' );
			return false;
		}

		// Check field requirement.
		if ( ! isset( $_POST['required'] ) ) {
			$message = esc_html__( 'Profile field requirement is missing.', 'buddypress' );
			return false;
		}

		// Check field type.
		if ( empty( $_POST['fieldtype'] ) ) {
			$message = esc_html__( 'Profile field type is missing.', 'buddypress' );
			return false;
		}

		// Check that field is of valid type.
		if ( ! in_array( $_POST['fieldtype'], array_keys( bp_xprofile_get_field_types() ), true ) ) {
			$message = sprintf( esc_html__( 'The profile field type %s is not registered.', 'buddypress' ), '<code>' . esc_attr( $_POST['fieldtype'] ) . '</code>' );
			return false;
		}

		// Get field type so we can check for and lavidate any field options.
		$field_type = bp_xprofile_create_field_type( $_POST['fieldtype'] );

		// Field type requires options.
		if ( true === $field_type->supports_options ) {

			// Build the field option key.
			$option_name = sanitize_key( $_POST['fieldtype'] ) . '_option';

			// Check for missing or malformed options.
			if ( empty( $_POST[ $option_name ] ) || ! is_array( $_POST[ $option_name ] ) ) {
				$message = esc_html__( 'These field options are invalid.', 'buddypress' );
				return false;
			}

			// Trim out empty field options.
			$field_values  = array_values( $_POST[ $option_name ] );
			$field_options = array_map( 'sanitize_text_field', $field_values );
			$field_count   = count( $field_options );

			// Check for missing or malformed options.
			if ( 0 === $field_count ) {
				$message = sprintf( esc_html__( '%s require at least one option.', 'buddypress' ), $field_type->name );
				return false;
			}

			// If only one option exists, it cannot be an empty string.
			if ( ( 1 === $field_count ) && ( '' === $field_options[0] ) ) {
				$message = sprintf( esc_html__( '%s require at least one option.', 'buddypress' ), $field_type->name );
				return false;
			}
		}

		return true;
	}

	/**
	 * This function populates the items for radio buttons checkboxes and drop
	 * down boxes.
	 */
	public function render_admin_form_children() {
		foreach ( array_keys( bp_xprofile_get_field_types() ) as $field_type ) {
			$type_obj = bp_xprofile_create_field_type( $field_type );
			$type_obj->admin_new_field_html( $this );
		}
	}

	/**
	 * Oupput the admin form for this field.
	 *
	 * @since 1.9.0
	 *
	 * @param string $message Message to display.
	 */
	public function render_admin_form( $message = '' ) {
		if ( empty( $this->id ) ) {
			$title  = __( 'Add New Field', 'buddypress' );
			$action	= "users.php?page=bp-profile-setup&amp;group_id=" . $this->group_id . "&amp;mode=add_field#tabs-" . $this->group_id;
			$button	= __( 'Save', 'buddypress' );

			if ( !empty( $_POST['saveField'] ) ) {
				$this->name        = $_POST['title'];
				$this->description = $_POST['description'];
				$this->is_required = $_POST['required'];
				$this->type        = $_POST['fieldtype'];
				$this->field_order = $_POST['field_order'];

				if ( ! empty( $_POST["sort_order_{$this->type}"] ) ) {
					$this->order_by = $_POST["sort_order_{$this->type}"];
				}
			}
		} else {
			$title  = __( 'Edit Field', 'buddypress' );
			$action = "users.php?page=bp-profile-setup&amp;mode=edit_field&amp;group_id=" . $this->group_id . "&amp;field_id=" . $this->id . "#tabs-" . $this->group_id;
			$button	= __( 'Update', 'buddypress' );
		} ?>

		<div class="wrap">

			<h2><?php echo esc_html( $title ); ?></h2>

			<?php if ( !empty( $message ) ) : ?>

				<div id="message" class="error fade">
					<p><?php echo esc_html( $message ); ?></p>
				</div>

			<?php endif; ?>

			<form id="bp-xprofile-add-field" action="<?php echo esc_url( $action ); ?>" method="post">
				<div id="poststuff">
					<div id="post-body" class="metabox-holder columns-<?php echo ( 1 == get_current_screen()->get_columns() ) ? '1' : '2'; ?>">
						<div id="post-body-content">

							<?php

							// Output the name & description fields.
							$this->name_and_description(); ?>

						</div><!-- #post-body-content -->

						<div id="postbox-container-1" class="postbox-container">

							<?php

							// Output the sumbit metabox.
							$this->submit_metabox( $button );

							// Output the required metabox.
							$this->required_metabox();

							// Output the Member Types metabox.
							$this->member_type_metabox();

							// Output the field visibility metaboxes.
							$this->visibility_metabox();

							/**
							 * Fires after XProfile Field sidebar metabox.
							 *
							 * @since 2.2.0
							 *
							 * @param BP_XProfile_Field $this Current XProfile field.
							 */
							do_action( 'xprofile_field_after_sidebarbox', $this ); ?>

						</div>

						<div id="postbox-container-2" class="postbox-container">

							<?php

							/**
							 * Fires before XProfile Field content metabox.
							 *
							 * @since 2.3.0
							 *
							 * @param BP_XProfile_Field $this Current XProfile field.
							 */
							do_action( 'xprofile_field_before_contentbox', $this );

							// Output the field attributes metabox.
							$this->type_metabox();

							// Output hidden inputs for default field.
							$this->default_field_hidden_inputs();

							/**
							 * Fires after XProfile Field content metabox.
							 *
							 * @since 2.2.0
							 *
							 * @param BP_XProfile_Field $this Current XProfile field.
							 */
							do_action( 'xprofile_field_after_contentbox', $this ); ?>

						</div>
					</div><!-- #post-body -->
				</div><!-- #poststuff -->
			</form>
		</div>

	<?php
	}

	/**
	 * Private method used to display the submit metabox.
	 *
	 * @since 2.3.0
	 *
	 * @param string $button_text Text to put on button.
	 */
	private function submit_metabox( $button_text = '' ) {

		/**
		 * Fires before XProfile Field submit metabox.
		 *
		 * @since 2.1.0
		 *
		 * @param BP_XProfile_Field $this Current XProfile field.
		 */
		do_action( 'xprofile_field_before_submitbox', $this ); ?>

		<div id="submitdiv" class="postbox">
			<h3><?php esc_html_e( 'Submit', 'buddypress' ); ?></h3>
			<div class="inside">
				<div id="submitcomment" class="submitbox">
					<div id="major-publishing-actions">

						<?php

						/**
						 * Fires at the beginning of the XProfile Field publishing actions section.
						 *
						 * @since 2.1.0
						 *
						 * @param BP_XProfile_Field $this Current XProfile field.
						 */
						do_action( 'xprofile_field_submitbox_start', $this ); ?>

						<input type="hidden" name="field_order" id="field_order" value="<?php echo esc_attr( $this->field_order ); ?>" />

						<?php if ( ! empty( $button_text ) ) : ?>

							<div id="publishing-action">
								<input type="submit" name="saveField" value="<?php echo esc_attr( $button_text ); ?>" class="button-primary" />
							</div>

						<?php endif; ?>

						<div id="delete-action">
							<a href="users.php?page=bp-profile-setup" class="deletion"><?php esc_html_e( 'Cancel', 'buddypress' ); ?></a>
						</div>

						<?php wp_nonce_field( 'xprofile_delete_option' ); ?>

						<div class="clear"></div>
					</div>
				</div>
			</div>
		</div>

		<?php

		/**
		 * Fires after XProfile Field submit metabox.
		 *
		 * @since 2.1.0
		 *
		 * @param BP_XProfile_Field $this Current XProfile field.
		 */
		do_action( 'xprofile_field_after_submitbox', $this );
	}

	/**
	 * Private method used to output field name and description fields.
	 *
	 * @since 2.3.0
	 */
	private function name_and_description() {
	?>

		<div id="titlediv">
			<div class="titlewrap">
				<label id="title-prompt-text" for="title"><?php echo esc_html_x( 'Name', 'XProfile admin edit field', 'buddypress' ); ?></label>
				<input type="text" name="title" id="title" value="<?php echo esc_attr( $this->name ); ?>" autocomplete="off" />
			</div>
		</div>

		<div class="postbox">
			<h3><?php echo esc_html_x( 'Description', 'XProfile admin edit field', 'buddypress' ); ?></h3>
			<div class="inside">
				<label for="description" class="screen-reader-text"><?php esc_html_e( 'Add description', 'buddypress' ); ?></label>
				<textarea name="description" id="description" rows="8" cols="60"><?php echo esc_textarea( $this->description ); ?></textarea>
			</div>
		</div>

	<?php
	}

	/**
	 * Private method used to output field Member Type metabox.
	 *
	 * @since 2.4.0
	 */
	private function member_type_metabox() {

		// The primary field is for all, so bail.
		if ( 1 === (int) $this->id ) {
			return;
		}

		// Bail when no member types are registered.
		if ( ! $member_types = bp_get_member_types( array(), 'objects' ) ) {
			return;
		}

		$field_member_types = $this->get_member_types();

		?>

		<div id="member-types-div" class="postbox">
			<h3><?php _e( 'Member Types', 'buddypress' ); ?></h3>
			<div class="inside">
				<p class="description"><?php _e( 'This field should be available to:', 'buddypress' ); ?></p>

				<ul>
					<?php foreach ( $member_types as $member_type ) : ?>
					<li>
						<label for="member-type-<?php echo $member_type->labels['name']; ?>">
							<input name="member-types[]" id="member-type-<?php echo $member_type->labels['name']; ?>" class="member-type-selector" type="checkbox" value="<?php echo $member_type->name; ?>" <?php checked( in_array( $member_type->name, $field_member_types ) ); ?>/>
							<?php echo $member_type->labels['name']; ?>
						</label>
					</li>
					<?php endforeach; ?>

					<li>
						<label for="member-type-none">
							<input name="member-types[]" id="member-type-none" class="member-type-selector" type="checkbox" value="null" <?php checked( in_array( 'null', $field_member_types ) ); ?>/>
							<?php _e( 'Users with no member type', 'buddypress' ); ?>
						</label>
					</li>

				</ul>
				<p class="description member-type-none-notice<?php if ( ! empty( $field_member_types ) ) : ?> hide<?php endif; ?>"><?php _e( 'Unavailable to all members.', 'buddypress' ) ?></p>
			</div>

			<input type="hidden" name="has-member-types" value="1" />
		</div>

		<?php
	}

	/**
	 * Private method used to output field visibility metaboxes.
	 *
	 * @since 2.3.0
	 *
	 * @return void If default field id 1.
	 */
	private function visibility_metabox() {

		// Default field cannot have custom visibility.
		if ( true === $this->is_default_field() ) {
			return;
		} ?>

		<div class="postbox">
			<h3><label for="default-visibility"><?php esc_html_e( 'Visibility', 'buddypress' ); ?></label></h3>
			<div class="inside">
				<div>
					<select name="default-visibility" id="default-visibility">

						<?php foreach( bp_xprofile_get_visibility_levels() as $level ) : ?>

							<option value="<?php echo esc_attr( $level['id'] ); ?>" <?php selected( $this->get_default_visibility(), $level['id'] ); ?>>
								<?php echo esc_html( $level['label'] ); ?>
							</option>

						<?php endforeach ?>

					</select>
				</div>

				<div>
					<ul>
						<li>
							<input type="radio" id="allow-custom-visibility-allowed" name="allow-custom-visibility" value="allowed" <?php checked( $this->get_allow_custom_visibility(), 'allowed' ); ?> />
							<label for="allow-custom-visibility-allowed"><?php esc_html_e( 'Allow members to override', 'buddypress' ); ?></label>
						</li>
						<li>
							<input type="radio" id="allow-custom-visibility-disabled" name="allow-custom-visibility" value="disabled" <?php checked( $this->get_allow_custom_visibility(), 'disabled' ); ?> />
							<label for="allow-custom-visibility-disabled"><?php esc_html_e( 'Enforce field visibility', 'buddypress' ); ?></label>
						</li>
					</ul>
				</div>
			</div>
		</div>

		<?php
	}

	/**
	 * Output the metabox for setting if field is required or not.
	 *
	 * @since 2.3.0
	 *
	 * @return void If default field.
	 */
	private function required_metabox() {

		// Default field is always required.
		if ( true === $this->is_default_field() ) {
			return;
		} ?>

		<div class="postbox">
			<h3><label for="required"><?php esc_html_e( 'Requirement', 'buddypress' ); ?></label></h3>
			<div class="inside">
				<select name="required" id="required">
					<option value="0"<?php selected( $this->is_required, '0' ); ?>><?php esc_html_e( 'Not Required', 'buddypress' ); ?></option>
					<option value="1"<?php selected( $this->is_required, '1' ); ?>><?php esc_html_e( 'Required',     'buddypress' ); ?></option>
				</select>
			</div>
		</div>

	<?php
	}

	/**
	 * Output the metabox for setting what type of field this is.
	 *
	 * @since 2.3.0
	 *
	 * @return void If default field.
	 */
	private function type_metabox() {

		// Default field cannot change type.
		if ( true === $this->is_default_field() ) {
			return;
		} ?>

		<div class="postbox">
			<h3><label for="fieldtype"><?php esc_html_e( 'Type', 'buddypress'); ?></label></h3>
			<div class="inside">
				<select name="fieldtype" id="fieldtype" onchange="show_options(this.value)" style="width: 30%">

					<?php bp_xprofile_admin_form_field_types( $this->type ); ?>

				</select>

				<?php

				// Deprecated filter, don't use. Go look at {@link BP_XProfile_Field_Type::admin_new_field_html()}.
				do_action( 'xprofile_field_additional_options', $this );

				$this->render_admin_form_children(); ?>

			</div>
		</div>

	<?php
	}

	/**
	 * Output hidden fields used by default field.
	 *
	 * @since 2.3.0
	 *
	 * @return void If not default field.
	 */
	private function default_field_hidden_inputs() {

		// Field 1 is the fullname field, which cannot have custom visibility.
		if ( false === $this->is_default_field() ) {
			return;
		} ?>

		<input type="hidden" name="required"  id="required"  value="1"       />
		<input type="hidden" name="fieldtype" id="fieldtype" value="textbox" />

		<?php
	}

	/**
	 * Return if a field ID is the default field.
	 *
	 * @since 2.3.0
	 *
	 * @param int $field_id ID of field to check.
	 *
	 * @return bool
	 */
	private function is_default_field( $field_id = 0 ) {

		// Fallback to current field ID if none passed.
		if ( empty( $field_id ) ) {
			$field_id = $this->id;
		}

		// Compare & return.
		return (bool) ( 1 === (int) $field_id );
	}
}

<?php
// Exit if accessed directly
if ( ! defined( 'ABSPATH' ) )
    exit;

/**
 * @author Stergatu Eleni
 * @since  0.5
 */
if ( class_exists( 'BP_Group_Extension' ) ) : // Recommended, to prevent problems during upgrade or when Groups are disabled

    class BP_Group_Documents_Plugin_Extension extends BP_Group_Extension {

	var $visibility = 'private';
	var $format_notification_function;
	var $enable_edit_item = true;
	var $admin_metabox_context = 'side'; // The context of your admin metabox. See add_meta_box()
	var $admin_metabox_priority = 'default'; // The priority of your admin metabox. See add_meta_box()

	function __construct() {
	    $bp = buddypress();

	    $nav_page_name = get_option( 'bp_group_documents_nav_page_name' );

	    $this->name = ! empty( $nav_page_name ) ? $nav_page_name : __( 'Documents', 'bp-group-documents' );
	    $this->slug = BP_GROUP_DOCUMENTS_SLUG;

	    /* For internal identification */
	    $this->id = 'group_documents';
	    $this->format_notification_function = 'bp_group_documents_format_notifications';

	    if ( $bp->groups->current_group ) {
		$this->nav_item_name = $this->name . ' <span>' . BP_Group_Documents::get_total( $bp->groups->current_group->id ) . '</span>';
		$this->nav_item_position = 51;
	    }

	    $this->admin_name = ! empty( $nav_page_name ) ? $nav_page_name : __( 'Documents', 'bp-group-documents' );
	    $this->admin_slug = BP_GROUP_DOCUMENTS_SLUG;

	    if ( 'mods_decide' != get_option( 'bp_group_documents_upload_permission' ) ) {
		$this->enable_create_step = false;
	    } else {
		$this->create_step_position = 21;
	    }
	}

	/**
	 * The content of the BP group documents tab of the group creation process
	 *
	 */
	function create_screen( $group_id = null ) {
	    $bp = buddypress();
	    if ( ! bp_is_group_creation_step( $this->slug ) ) {
		return false;
	    }
	    $this->edit_create_markup( $bp->groups->new_group_id );
	    wp_nonce_field( 'groups_create_save_' . $this->slug );
	}

	/**
	 * The routine run after the user clicks Continue from the creation step
	 * @version 1, 29/4/2013, stergatu
	 * @since 0.5
	 */
	function create_screen_save( $group_id = null ) {
	    $bp = buddypress();

	    check_admin_referer( 'groups_create_save_' . $this->slug );

	    do_action( 'bp_group_documents_group_create_save' );
	    $success = false;


	    //Update permissions
	    $valid_permissions = array( 'members', 'mods_only' );
	    if ( isset( $_POST['bp_group_documents_upload_permission'] ) && in_array( $_POST['bp_group_documents_upload_permission'], $valid_permissions ) ) {
		$success = groups_update_groupmeta( $bp->groups->new_group_id, 'bp_group_documents_upload_permission', $_POST['bp_group_documents_upload_permission'] );
	    }


	    /* To post an error/success message to the screen, use the following */
	    if ( ! $success )
		bp_core_add_message( __( 'There was an error saving, please try again', 'buddypress' ), 'error' );
	    else
		bp_core_add_message( __( 'Settings Saved.', 'buddypress' ) );
	    do_action( 'bp_group_documents_group_after_create_save' );
	}

	/**
	 * The content of the Group Documents page of the group admin
	 * @author Stergatu Eleni <stergatu@cti.gr>
	 * @since 0.5
	 * @version 4 18/10/2013, fix the $action_link
	 * v3, 21/5/2013, fix the edit category
	 */
	function edit_screen( $group_id = null ) {
	    $bp = buddypress();
	    if ( ! bp_is_group_admin_screen( $this->slug ) ) {
		return false;
	    }
	    //useful ur for submits & links
	    $action_link = get_bloginfo( 'url' ) . '/' . bp_get_groups_root_slug() . '/' . $bp->current_item . '/' . $bp->current_action . '/' . $this->slug;
	    $this->edit_create_markup( $bp->groups->current_group->id );
	    //only show categories if site admin chooses to
	    if ( get_option( 'bp_group_documents_use_categories' ) ) {
		$parent_id = BP_Group_Documents_Template::get_parent_category_id();
		$group_categories = get_terms( 'group-documents-category', array( 'parent' => $parent_id, 'hide_empty' => false ) );
		?>
				<!-- #group-documents-group-admin-categories -->
				<div id="group-documents-group-admin-categories">
				    <label><?php
			_e( 'Category List for', 'bp-group-documents' );
			echo ' ' . $this->name;
			?></label>
				    <div>
					<ul>
			    <?php
			    foreach ( $group_categories as $category ) {
				if ( isset( $_GET['edit'] ) && ( $_GET['edit'] == $category->term_id ) ) {
				    ?>
							    <li id="category-<?php echo $category->term_id; ?>"><input type="text" name="group_documents_category_edit" value="<?php echo $category->name; ?>" />
								<input type="hidden" name="group_documents_category_edit_id" value="<?php echo $category->term_id; ?>" />
								<input type="submit" id="editCat" name="editCat" class="button" value="<?php _e( 'Update' ); ?>" />
										    </li>
				    <?php
				} elseif ( isset( $_GET['delete'] ) && ( $_GET['delete'] == $category->term_id ) ) {
				    ?>
							    <div class="bp_group_documents_question" ><?php printf( __( 'Are you sure you want to delete category <b>%s</b>?', 'bp-group-documents' ), $category->name ); ?>
								<br/>
					<?php
					printf( __( 'Any %s in the category will be left with no category.', 'bp-group-documents' ), mb_strtolower( $this->name ) );
					?>
								<br/>
					<?php
					_e( 'You can later assign them to another  category.', 'bp-group-documents' );
					?>
								<input type="hidden" name="group_documents_category_del_id" value="<?php echo $category->term_id; ?>" />
								<input type="submit" value="<?php _e( 'Delete', 'buddypress' ); ?>" id="delCat" name="delCat"/>
							    </div>
				    <?php
				} else {
				    $edit_link = wp_nonce_url( $action_link . '?edit=' . $category->term_id, 'group_documents_category_edit' );
				    $delete_link = wp_nonce_url( $action_link . '?delete=' . $category->term_id, 'group_documents_category_delete' );
				    ?>
							    <li id="category-<?php echo $category->term_id; ?>"><strong><?php echo $category->name; ?></strong>
								<div class="generic-button">&nbsp; <a class="group-documents-category-edit" href="<?php echo $edit_link; ?>"><?php _e( 'Edit', 'bp-group-documents' ); ?></a></div>
											<div class="generic-button"><a class="group-documents-category-delete" href="<?php echo $delete_link; ?>"><?php _e( 'Delete', 'bp-group-documents' ); ?></a></div>
										    </li>
				    <?php
				}
			    }
			    ?>
					    <li><input type="text" name="bp_group_documents_new_category" class="bp-group-documents-new-category" />
						<input type="submit" value="<?php _e( 'Add' ); ?>" id="addCat" name="addCat"/></li>
					</ul>
				    </div>
				</div><!-- end #group-documents-group-admin-categories -->
		<?php
	    }
	    do_action( 'bp_group_documents_group_admin_edit' );
	    ?>
	    	    &nbsp;<p>
	    	        <input type="submit" value="<?php _e( 'Save Changes', 'buddypress' ) ?> &rarr;" id="save" name="save" />
	    	        <input type="hidden" name="delCat" value="" />
	    	    </p>
	    <?php
	    wp_nonce_field( 'groups_edit_save_' . $this->slug );
	}

	function edit_create_markup( $gid ) {
	    $bp = buddypress();

	    //only show the upload persmissions if the site admin allows this to be changed at group-level
	    ?>
	    	    <p><label><?php _e( 'Upload Permissions:', 'bp-group-documents' ); ?></label></p>
	    	    <p>
		<?php
		$netadminDecision = get_option( 'bp_group_documents_upload_permission' );
		switch ( $netadminDecision ) {
		    case 'mods_decide':
			$upload_permission = groups_get_groupmeta( $gid, 'bp_group_documents_upload_permission' );
			?><input type="radio" name="bp_group_documents_upload_permission" value="members"
			       <?php if ( 'members' == $upload_permission ) echo 'checked="checked"'; ?> />
			<?php _e( 'All Group Members', 'bp-group-documents' ); ?><br />
		    		        <input type="radio" name="bp_group_documents_upload_permission" value="mods_only"
			       <?php if ( ! ('members' == $upload_permission) ) echo 'checked="checked"'; ?> />
			       <?php
			       _e( "Only Group's Administrators and Moderators", 'bp-group-documents' );
			       break;
			   case 'members':
			       _e( 'All Group Members', 'bp-group-documents' );
			       break;
			   case 'mods_only':
			   default:
			       _e( "Only Group's Administrators and Moderators", 'bp-group-documents' );
			       break;
		       }
		       ?>
	    	    </p>
	    <?php
	}

	/**
	 * The routine run after the user clicks Save from your admin tab
	 * @version v1.4, 31/10/2013, fix some notices
	 * v3,  27/8/2013, fix the messages
	 * v2, 21/5/2013, fix the edit and delete category bug, Stergatu Eleni
	 * @since 0.5
	 */
	function edit_screen_save( $group_id = null ) {
	    $bp = buddypress();
	    do_action( 'bp_group_documents_group_admin_save' );
	    $message = false;
	    $type = '';

	    $parent_id = BP_Group_Documents_Template::get_parent_category_id();
	    if ( ( ! isset( $_POST['save'] )) && ( ! isset( $_POST['addCat'] )) && ( ! isset( $_POST['editCat'] )) && ( ! isset( $_POST['delCat'] )) ) {
		return false;
	    }

	    check_admin_referer( 'groups_edit_save_' . $this->slug );
	    //check if category was deleted
	    if ( isset( $_POST['group_documents_category_del_id'] ) &&
		    ctype_digit( $_POST['group_documents_category_del_id'] ) &&
		    term_exists( ( int ) $_POST['group_documents_category_del_id'], 'group-documents-category' ) ) {
		if ( true == wp_delete_term( ( int ) $_POST['group_documents_category_del_id'], 'group-documents-category' ) ) {
		    $message = sprintf( __( 'Group %s category deleted successfully', 'bp-group-documents' ), mb_strtolower( $this->name ) );
		}
	    }
	    //check if category was updatedsuccessfully
	    elseif ( (array_key_exists( 'group_documents_category_edit', $_POST )) && (ctype_digit( $_POST['group_documents_category_edit_id'] )) && (term_exists( ( int ) $_POST['group_documents_category_edit_id'], 'group-documents-category' )) ) {
		if ( term_exists( $_POST['group_documents_category_edit'], 'group-documents-category', $parent_id ) ) {
		    $message = sprintf( __( 'No changes were made. This %s category name is used already', 'bp-group-documents' ), mb_strtolower( $this->name ) );
		    $type = 'error';
		} else {
		    if ( true == wp_update_term( ( int ) $_POST['group_documents_category_edit_id'], 'group-documents-category', array( 'name' => $_POST['group_documents_category_edit'] ) ) ) {
			$message = sprintf( __( 'Group %s category renamed successfully', 'bp-group-documents' ), mb_strtolower( $this->name ) );
		    }
		}
	    }

	    //check if new category was added, if so, append to current list
	    elseif ( $_POST['bp_group_documents_new_category'] ) {

		if ( ! term_exists( $_POST['bp_group_documents_new_category'], 'group-documents-category', $parent_id ) ) {
		    if ( true == wp_insert_term( $_POST['bp_group_documents_new_category'], 'group-documents-category', array( 'parent' => $parent_id ) ) ) {
			$message = $_POST['bp_group_documents_new_category'] . ': ' . sprintf( __( 'New group %s category created', 'bp-group-documents' ), mb_strtolower( $this->name ) );
		    }
		} else {
		    $message = sprintf( __( 'No changes were made. This %s category name is used already', 'bp-group-documents' ), mb_strtolower( $this->name ) );
		    $type = 'error';
		}
	    } else {
		$valid_permissions = array( 'members', 'mods_only' );
		//check if group upload permision has chanced
		if ( isset( $_POST['bp_group_documents_upload_permission'] ) && in_array( $_POST['bp_group_documents_upload_permission'], $valid_permissions ) ) {
		    if ( true == groups_update_groupmeta( $bp->groups->current_group->id, 'bp_group_documents_upload_permission', $_POST['bp_group_documents_upload_permission'] ) ) {
			$message = __( 'Upload Permissions changed successfully', 'bp-group-documents' );
		    }
		}
	    }

	    /* Post an error/success message to the screen */

	    if ( ! $message )
		bp_core_add_message( __( 'No changes were made. Either error or you didn\'t change anything', 'bp-group-documents' ), 'error' );
	    else
		bp_core_add_message( $message, $type );

	    do_action( 'bp_group_documents_group_admin_after_save' );
	    bp_core_redirect( bp_get_group_permalink( $bp->groups->current_group ) . 'admin/' . $this->slug );
	}

	/**
	 * @version 1, 25/4/2013
	 * @since version 0.5
	 * @author Stergatu
	 */
	function display( $group_id = null ) {
	    do_action( 'bp_group_documents_display' );
	    add_action( 'bp_template_content_header', 'bp_group_documents_display_header' );
	    add_action( 'bp_template_title', 'bp_group_documents_display_title' );
	    bp_group_documents_display();
	}

	/**
	 * Add a metabox to the admin Edit group screen
	 * @since 0.5
	 * @version 1, 30/4/2013, stergatu
	 *
	 */
	function admin_screen( $group_id = null ) {
	    $this->edit_create_markup( $group_id );
	}

	/**
	 * The routine run after the group is saved on the Dashboard group admin screen
	 * @version 2, 17/9/2013, stergatu
	 * @param type $group_id
	 */
	function admin_screen_save( $group_id = null ) {
	    // Grab your data out of the $_POST global and save as necessary
	    //Update permissions
	    $valid_permissions = array( 'members', 'mods_only' );

	    if ( isset( $_POST['bp_group_documents_upload_permission'] ) && in_array( $_POST['bp_group_documents_upload_permission'], $valid_permissions ) ) {
		$previous_upload_permission = groups_get_groupmeta( $group_id, 'bp_group_documents_upload_permission' );
		if ( $_POST['bp_group_documents_upload_permission'] != $previous_upload_permission ) {
		    groups_update_groupmeta( $group_id, 'bp_group_documents_upload_permission', $_POST['bp_group_documents_upload_permission'] );
		}
	    }
	}

	function widget_display() {
	    ?>
	    	    <div class="info-group">
	    	        <h4><?php echo esc_attr( $this->name ) ?></h4>
	    	        <p>
	    	    	Not yet implemented
	    	        </p>
	    	    </div>
	    <?php
	}

	/**
	 * @author Stergatu Eleni
	 * @since 0.5
	 * @version 1, 6/3/2013
	 * @deprecated since version 1.5
	 */
	function register_textdomain() {
	    //load i18n files if present
	    $plugin_dir = dirname( plugin_basename( __FILE__ ) ) . '/languages/';
	    if ( file_exists( dirname( __FILE__ ) . '/languages/bp-group-documents-' . get_locale() . '.mo' ) ) {
		load_plugin_textdomain( 'bp-group-documents', false, $plugin_dir );
	    }
	}

    }

    /**
     * @author Stergatu Eleni
     * @since 0.5
     * @version 1.3, 25/10/2013 Makes sure the get_home_path function is defined before trying to use it
     * v1.2.2 remove admin-uploads.php file
     * v1, 5/3/2013
     */
    function bp_group_documents_include_files() {

// Makes sure the get_home_path function is defined before trying to use it
	if ( ! function_exists( 'get_home_path' ) ) {
	    require_once( ABSPATH . '/wp-admin/includes/file.php' );
	}
	require_once( ABSPATH . "wp-admin/includes/misc.php" );
	require ( dirname( __FILE__ ) . '/include/bp_group_documents_functions.php' );
	require ( dirname( __FILE__ ) . '/include/admin.php' );
	require ( dirname( __FILE__ ) . '/include/classes.php' );
	require ( dirname( __FILE__ ) . '/include/cssjs.php' );
	require ( dirname( __FILE__ ) . '/include/widgets.php' );
	require ( dirname( __FILE__ ) . '/include/notifications.php' );
	require ( dirname( __FILE__ ) . '/include/activity.php' );
	require ( dirname( __FILE__ ) . '/include/templatetags.php' );
	require ( dirname( __FILE__ ) . '/include/filters.php' );


//only get the forum extension if it's been specified by the admin
	if ( get_option( 'bp_group_documents_forum_attachments' ) ) {
	    require( dirname( __FILE__ ) . '/include/group-forum-attachments.php' );
	}
    }

    bp_register_group_extension( 'BP_Group_Documents_Plugin_Extension' );



endif; // class_exists( 'BP_Group_Documents_Extension' )
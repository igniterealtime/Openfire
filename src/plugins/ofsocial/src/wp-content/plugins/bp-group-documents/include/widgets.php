<?php
// Exit if accessed directly
if ( ! defined( 'ABSPATH' ) )
    exit;

/**
 * Register the BP Group documents widgets.
 * @version 2, 22/4/2014
 */
function bp_group_documents_register_widgets() {
    if ( ! bp_is_active( 'groups' ) ) {
	return;
    }
    register_widget( 'BP_Group_Documents_Newest_Widget' );
    register_widget( 'BP_Group_Documents_Popular_Widget' );
    register_widget( 'BP_Group_Documents_Usergroups_Widget' );

//    add_action( 'widgets_init' , create_function( '' , 'register_widget( "BP_Group_Documents_Newest_Widget" );' ) ) ;
//    add_action( 'widgets_init' , create_function( '' , 'register_widget("BP_Group_Documents_Popular_Widget");' ) ) ;
//    add_action( 'widgets_init' , create_function( '' , 'register_widget("BP_Group_Documents_Usergroups_Widget");' ) ) ;

    if ( (is_active_widget( false, false, 'bp_group_documents_newest_widget' )) || (is_active_widget( false, false, 'bp_group_documents_popular_widget' )) ) {
	add_action( 'wp_enqueue_scripts', 'bp_group_documents_add_my_stylesheet' );
    }
    // The BP_Group_Documents_CurrentGroup widget works only when looking at a group page,
    // and the concept of "current group " doesn't exist on non-root blogs,
    // so we don't register the widget there.
    if ( ! bp_is_root_blog() ) {
	return;
    }
    register_widget( 'BP_Group_Documents_CurrentGroup_Widget' );
    // add_action( 'widgets_init' , create_function( '' , 'register_widget("BP_Group_Documents_CurrentGroup_Widget");' ) ) ;
}

add_action( 'widgets_init', 'bp_group_documents_register_widgets' );

/**
 * Enqueue plugin style-file
 */
function bp_group_documents_add_my_stylesheet() {
    // Respects SSL, Style.css is relative to the current file
    wp_register_style( 'bp-group-documents', plugins_url( BP_GROUP_DOCUMENTS_DIR ) . '/css/style.css', false, BP_GROUP_DOCUMENTS_VERSION );
    wp_enqueue_style( 'bp-group-documents' );
}

/**
 * @version 2, 1/5/2013, stergatu
 *
 */
class BP_Group_Documents_Newest_Widget extends WP_Widget {

    var $bp_group_documents_name;

    public function __construct() {
	$bp = buddypress();
	$nav_page_name = get_option( 'bp_group_documents_nav_page_name' );
	$this->bp_group_documents_name = mb_convert_case(  ! empty( $nav_page_name ) ? $nav_page_name : __( 'Documents', 'bp-group-documents' ), MB_CASE_LOWER );
	parent::__construct(
		'bp_group_documents_newest_widget', '(BP Group Documents) ' . sprintf( __( 'Recent Group %s', 'bp-group-documents' ), $this->bp_group_documents_name ), // Name
		array( 'description' => sprintf( __( 'The most recently uploaded group %s. Only from public groups', 'bp-group-documents' ), $this->bp_group_documents_name ),
	    'classname' => 'bp_group_documents_widget', ) // Args
	);
    }

    /**
     * @version 3, 22/4/2014 add sanitize_text_field
     * v2, 1/5/2013, stergatu
     *
     */
    function widget( $args, $instance ) {
	$bp = buddypress();
	extract( $args );
	$title = apply_filters( 'widget_title', empty( $instance['title'] ) ? sprintf( __( 'Recent Group %s', 'bp-group-documents' ), $this->bp_group_documents_name ) : sanitize_text_field( $instance['title'] )  );
	echo $before_widget . $before_title . esc_html( $title ) . $after_title;

	do_action( 'bp_group_documents_newest_widget_before_html' );

//	eleni comment on 1/5/2013
	/*        $group_id = $bp->groups->current_group->id;
	  //        if ($group_id > 0) {
	  //            $instance['group_filter'] = $group_id;
	  //        }
	 */
	$document_list = BP_Group_Documents::get_list_for_newest_widget( absint( $instance['num_items'] ), $instance['group_filter'], ( bool ) $instance['featured'] );
	if ( $document_list && count( $document_list ) >= 1 ) {
	    echo '<ul id="bp-group-documents-recent" class="bp-group-documents-list" >';
	    foreach ( $document_list as $item ) {
		$document = new BP_Group_Documents( $item['id'] );
		$group = new BP_Groups_Group( $document->group_id );
		echo '<li>';
		if ( get_option( 'bp_group_documents_display_icons' ) ) {
		    $document->icon();
		}
		?>
				<a class="bp-group-documents-title" id="group-document-link-<?php echo $document->id; ?>" href="<?php $document->url(); ?>" target="_blank">
		    <?php echo str_replace( "\\", "", esc_html( stripslashes( $document->name ) ) ); ?></a>
		<?php
		if ( ! $instance['group_filter'] ) {
		    echo sprintf( __( 'posted in %s', 'bp-group-documents' ), '<a href="' . esc_url( bp_get_group_permalink( $group ) ) . '">' . esc_attr( $group->name ) . '</a>' );
		}
		echo '</li>';
	    }
	    echo '</ul>';
	} else {
	    echo '<div class="widget-error">' . sprintf( __( 'There are no %s to display.', 'bp-group-documents' ), $this->bp_group_documents_name ) . '</div></p>';
	}
	echo $after_widget;
    }

    /**
     *
     * @param type $new_instance
     * @param type $old_instance
     * @return type
     * @todo, 25/4/2013, stergatu, add functionality for documents_category selection (minor)
     */
    function update( $new_instance, $old_instance ) {
	do_action( 'bp_group_documents_widget_update' );

	$default_title = sprintf( __( 'Recent Group %s', 'bp-group-documents' ), $this->bp_group_documents_name );

	$new_title = sanitize_text_field( $new_instance['title'] );

	$instance = $old_instance;
	$instance['title'] = empty( $new_title ) ? sanitize_text_field( $default_title ) : $new_title;
	$instance['group_filter'] = absint( $new_instance['group_filter'] );
	$instance['featured'] = intval( ( bool ) $new_instance['featured'] );
	$instance['num_items'] = empty( $num_items ) ? 5 : absint( $new_instance['num_items'] );

	return $instance;
    }

    /**
     *
     * @param type $instance
     * @todo, 25/4/2013, stergatu, add functionality for documents_category selection (minor)
     */
    function form( $instance ) {
	do_action( 'bp_group_documents_newest_widget_form' );

	$defaults = array(
	    'download_count' => true,
	    'featured' => false,
	    'group_filter' => 0,
	    'num_items' => 5,
	    'title' => sprintf( __( 'Recent Group %s', 'bp-group-documents' ), $this->bp_group_documents_name ),
	);

	$instance = wp_parse_args( ( array ) $instance, $defaults );
	$title = esc_attr( $instance['title'] );
	$group_filter = absint( $instance['group_filter'] );
	$featured = ( bool ) $instance['featured'];
	$num_items = empty( $instance['num_items'] ) ? 5 : absint( $instance['num_items'] );
	?>
		<p><label><?php _e( 'Title:', 'bp-group-documents' ); ?></label><input class="widefat" id="<?php echo $this->get_field_id( 'title' ); ?>" name="<?php echo $this->get_field_name( 'title' ); ?>" type="text" value="<?php echo $title; ?>" /></p>
	<?php if ( BP_GROUP_DOCUMENTS_WIDGET_GROUP_FILTER ) { ?>
	    <p><label><?php _e( 'Filter by Group:', 'bp-group-documents' ); ?></label>
	        <select class="widefat" id="<?php echo $this->get_field_id( 'group_filter' ); ?>" name="<?php echo $this->get_field_name( 'group_filter' ); ?>" >
	    	<option value="0"><?php _e( 'Select Group...', 'bp-group-documents' ); ?></option>
		    <?php
		    $groups_list = BP_Groups_Group::get( 'alphabetical' );
		    foreach ( $groups_list['groups'] as $group ) {
			echo '<option value="' . esc_attr( $group->id ) . '" ';
			if ( $group->id == $group_filter )
			    echo 'selected="selected"';
			echo '>' . esc_html( stripslashes( $group->name ) ) . '</option>';
		    }
		    ?>
	    	    		</select></p>
			<?php
		    }

	if ( BP_GROUP_DOCUMENTS_FEATURED ) {
	    ?>
	    	    		<p><label><?php printf( __( 'Show featured %s only', 'bp-group-documents' ), $this->bp_group_documents_name ); ?></label> <input type="checkbox" id="<?php echo $this->get_field_id( 'featured' ); ?>" name="<?php echo $this->get_field_name( 'featured' ); ?>" value="1" <?php checked( $featured ); ?>>
	    		</p>
			<?php } ?>
			<p><label><?php _e( 'Number of items to show:', 'bp-group-documents' ); ?></label> <input class="widefat" id="<?php echo $this->get_field_id( 'num_items' ); ?>" name="<?php echo $this->get_field_name( 'num_items' ); ?>" type="text" value="<?php echo esc_attr( $num_items ); ?>" style="width: 30%" /></p>
			<?php
		    }

}

/**
 * @version 3, 13/5/2013, stergatu
 */
class BP_Group_Documents_Popular_Widget extends WP_Widget {

    var $bp_group_documents_name;

    function __construct() {
	$bp = buddypress();
	$nav_page_name = get_option( 'bp_group_documents_nav_page_name' );
	$this->bp_group_documents_name = mb_convert_case(  ! empty( $nav_page_name ) ? $nav_page_name : __( 'Documents', 'bp-group-documents' ), MB_CASE_LOWER );
		parent::__construct(
		'bp_group_documents_popular_widget', '(BP Group Documents) ' . sprintf( __( 'Popular Group %s', 'bp-group-documents' ), $this->bp_group_documents_name ), // Name
		array( 'description' => sprintf( __( 'The most commonly downloaded group %s. Only for public groups', 'bp-group-documents' ), $this->bp_group_documents_name ),
	    'classname' => 'bp_group_documents_widget' )
	);

	if ( is_active_widget( false, false, $this->id_base ) ) {
	    add_action( '', 'bp_group_documents_add_my_stylesheet' );
	}
    }

    function widget( $args, $instance ) {
	$bp = buddypress();

	extract( $args );
	$title = apply_filters( 'widget_title', empty( $instance['title'] ) ? sprintf( __( 'Popular Group %s', 'bp-group-documents' ), $this->bp_group_documents_name ) : sanitize_text_field( $instance['title'] )  );

		echo $before_widget . $before_title . esc_html( $title ) . $after_title;

	do_action( 'bp_group_documents_popular_widget_before_html' );

	/*	 * *
	 * Main HTML Display
	 */
	$document_list = BP_Group_Documents::get_list_for_popular_widget( absint( $instance['num_items'] ), $instance['group_filter'], ( bool ) $instance['featured'] );

	if ( $document_list && count( $document_list ) >= 1 ) {
	    echo '<ul id="bp-group-documents-recent" class="bp-group-documents-list">';
	    foreach ( $document_list as $item ) {
		$document = new BP_Group_Documents( $item['id'] );
		$group = new BP_Groups_Group( $document->group_id );
		echo '<li>';
		if ( get_option( 'bp_group_documents_display_icons' ) ) {
		    $document->icon();
		}
		?>
	<a class="bp-group-documents-title" id="group-document-link-<?php echo esc_attr( $document->id ); ?>" href="<?php $document->url(); ?>" target="_blank">
			    <?php echo str_replace( "\\", "", esc_html( stripslashes( $document->name ) ) ); ?></a>

	<br>
			<?php
			if ( ! $instance['group_filter'] ) {
		    echo sprintf( __( 'posted in %s', 'bp-group-documents' ), '<a href="' . esc_url( bp_get_group_permalink( $group ) ) . '">' . esc_html( $group->name ) . '</a>.' );
		}
		if ( $instance['download_count'] ) {
		    echo ' <span class="group-documents-download-count">' .
		    esc_html( $document->download_count ) . ' ' . __( 'downloads', 'bp-group-documents' ) .
		    '</span>';
		}
		echo '</li>';
	    }
	    echo '</ul>';
	} else {
	    echo '<div class="widget-error">' . sprintf( __( 'There are no %s to display.', 'bp-group-documents' ), $this->bp_group_documents_name ) . '</div></p>';
	}
	echo $after_widget;
    }

    function update( $new_instance, $old_instance ) {
	do_action( 'bp_group_documents_newest_widget_update' );

	$default_title = sprintf( __( 'Popular Group %s', 'bp-group-documents' ), $this->bp_group_documents_name );

	$new_title = sanitize_text_field( $new_instance['title'] );

	$instance = $old_instance;
	$instance['title'] = empty( $new_title ) ? sanitize_text_field( $default_title ) : $new_title;
	$instance['group_filter'] = absint( $new_instance['group_filter'] );
	$instance['featured'] = intval( ( bool ) $new_instance['featured'] );
	$instance['num_items'] = empty( $num_items ) ? 5 : absint( $new_instance['num_items'] );
	$instance['download_count'] = ( bool ) $new_instance['download_count'];

	return $instance;
    }

    function form( $instance ) {
	do_action( 'bp_group_documents_newest_widget_form' );

	$defaults = array(
	    'download_count' => true,
	    'featured' => false,
	    'group_filter' => 0,
	    'num_items' => 5,
	    'title' => sprintf( __( 'Popular Group %s', 'bp-group-documents' ), $this->bp_group_documents_name ),
	);

	$instance = wp_parse_args( ( array ) $instance, $defaults );
	$title = esc_attr( $instance['title'] );
	$group_filter = absint( $instance['group_filter'] );
	$featured = ( bool ) $instance['featured'];
	$num_items = empty( $instance['num_items'] ) ? 5 : absint( $instance['num_items'] );
	$download_count = ( bool ) $instance['download_count'];
	?>

		<p><label><?php _e( 'Title:', 'bp-group-documents' ); ?></label><input class="widefat" id="<?php echo $this->get_field_id( 'title' ); ?>" name="<?php echo $this->get_field_name( 'title' ); ?>" type="text" value="<?php echo $title; ?>" /></p>
			<?php if ( BP_GROUP_DOCUMENTS_WIDGET_GROUP_FILTER ) { ?>
	    		<p><label><?php _e( 'Filter by Group:', 'bp-group-documents' ); ?></label>
	    		    <select class="widefat" id="<?php echo $this->get_field_id( 'group_filter' ); ?>" name="<?php echo $this->get_field_name( 'group_filter' ); ?>" >
	    			<option value="0"><?php _e( 'Select Group...', 'bp-group-documents' ); ?></option>
				    <?php
				    $groups_list = BP_Groups_Group::get( 'alphabetical' );
//                                get_alphabetically();
		    foreach ( $groups_list['groups'] as $group ) {
			echo '<option value="' . esc_attr( $group->id ) . '" ';
			if ( $group->id == $group_filter )
			    echo 'selected="selected"';
			echo '>' . esc_html( stripslashes( $group->name ) ) . '</option>';
		    }
		    ?>
	    	    		</select></p>
			<?php
		    }
	if ( BP_GROUP_DOCUMENTS_FEATURED ) {
	    ?>
	    	    		<p><label><?php printf( __( 'Show featured %s only', 'bp-group-documents' ), $this->bp_group_documents_name ); ?></label>
	    		    <input type="checkbox" id="<?php echo $this->get_field_id( 'featured' ); ?>" name="<?php echo $this->get_field_name( 'featured' ); ?>" value="1" <?php
				       checked( $featured );
		?>>
	    </p>
		    <?php } ?>

	    <p><label><?php _e( 'Number of items to show:', 'bp-group-documents' ); ?></label> <input class="widefat" id="<?php echo $this->get_field_id( 'num_items' ); ?>" name="<?php echo $this->get_field_name( 'num_items' ); ?>" type="text" value="<?php echo absint( $num_items ); ?>" style="width: 30%" /></p>
		    <p><input type="checkbox" id="<?php echo $this->get_field_id( 'download_count' ); ?>" name="<?php echo $this->get_field_name( 'download_count' ); ?>" value="1" <?php
			      if ( $download_count ) {
		echo 'checked="checked"';
	    }
	    ?>>
	    <label><?php printf( __( 'Show downloads', 'bp-group-documents' ), $this->bp_group_documents_name ); ?></label></p>
		<?php
	    }

}

/**
 * @version 4, 22/4/2014, add sanitize_text_field
 * v3, 13/5/2013, stergatu
 */
class BP_Group_Documents_Usergroups_Widget extends WP_Widget {

    var $bp_group_documents_name;

    function __construct() {
	$bp = buddypress();
	$nav_page_name = get_option( 'bp_group_documents_nav_page_name' );
	$this->bp_group_documents_name = ! empty( $nav_page_name ) ? $nav_page_name : __( 'Documents', 'bp-group-documents' );
	parent::__construct(
		'bp_group_documents_usergroups_widget', '(BP Group Documents) ' . sprintf( __( '%s in your groups', 'bp-group-documents' ), $this->bp_group_documents_name ), // Name
		array( 'description' => sprintf( __( '%s for a logged in user\'s groups.', 'bp-group-documents' ), $this->bp_group_documents_name ),
	    'classname' => 'bp_group_documents_widget' )
	);

	if ( is_active_widget( false, false, $this->id_base ) ) {
	    add_action( '', 'bp_group_documents_add_my_stylesheet' );
	}
    }

    function widget( $args, $instance ) {
	$bp = buddypress();
	//only show widget to logged in users
	if ( ! is_user_logged_in() )
	    return;

	//get the groups the user is part of
	$results = groups_get_user_groups( get_current_user_id() );
	//don't show widget if user doesn't have any groups
	if ( $results['total'] == 0 )
	    return;
	extract( $args );
	$title = apply_filters( 'widget_title', empty( $instance['title'] ) ? sprintf( __( 'Recent %s from your Groups', 'bp-group-documents' ), $this->bp_group_documents_name ) : $instance['title']  );

		echo $before_widget . $before_title . esc_html( $title ) . $after_title;

	do_action( 'bp_group_documents_usergroups_widget_before_html' );
	$document_list = BP_Group_Documents::get_list_for_usergroups_widget( absint( $instance['num_items'] ), ( bool ) $instance['featured'] );

	if ( $document_list && count( $document_list ) >= 1 ) {
	    echo '<ul id="bp-group-documents-usergroups" class="bp-group-documents-list">';
	    foreach ( $document_list as $item ) {
		$document = new BP_Group_Documents( $item['id'] );
		$group = new BP_Groups_Group( $document->group_id );
		echo '<li>';
		if ( get_option( 'bp_group_documents_display_icons' ) ) {
		    $document->icon();
		}
		?>
	<a class="bp-group-documents-title" id="group-document-link-<?php echo esc_attr( $document->id ); ?>"
			   href="<?php $document->url(); ?>" target="_blank">
			    <?php echo str_replace( "\\", "", esc_html( stripslashes( $document->name ) ) ); ?></a>
			<?php
			echo sprintf( __( 'posted in %s', 'bp-group-documents' ), '<a href="' . esc_url( bp_get_group_permalink( $group ) ) . '">' . esc_html( $group->name ) . '</a>' );

		echo '</li>';
	    }
	    echo '</ul>';
	} else {
	    echo '<div class="widget-error">' . sprintf( __( 'There are no %s to display.', 'bp-group-documents' ), $this->bp_group_documents_name ) . '</div></p>';
	}
	echo $after_widget;
    }

    function update( $new_instance, $old_instance ) {
	do_action( 'bp_group_documents_usergroups_widget_update' );

	$default_title = sprintf( __( '%s in your groups', 'bp-group-documents' ), $this->bp_group_documents_name );

	$new_title = sanitize_text_field( $new_instance['title'] );
	$num_items = empty( $new_instance['num_items'] ) ? 5 : absint( $new_instance['num_items'] );

	$instance = $old_instance;
	$instance['title'] = empty( $new_title ) ? sanitize_text_field( $default_title ) : $new_title;
	$instance['featured'] = intval( ( bool ) $new_instance['featured'] );
	$instance['num_items'] = empty( $num_items ) ? 5 : absint( $new_instance['num_items'] );

	return $instance;
    }

    function form( $instance ) {
	do_action( 'bp_group_documents_usergroups_widget_form' );

	$defaults = array(
	    'featured' => false,
	    'num_items' => 5,
	    'title' => sprintf( __( '%s in your groups', 'bp-group-documents' ), $this->bp_group_documents_name ),
	);

	$instance = wp_parse_args( ( array ) $instance, $defaults );
	$title = esc_attr( $instance['title'] );
	$featured = ( bool ) $instance['featured'];
	$num_items = empty( $instance['num_items'] ) ? 5 : absint( $instance['num_items'] );
	?>

	<p><label><?php _e( 'Title:', 'bp-group-documents' ); ?></label><input class="widefat" id="<?php echo $this->get_field_id( 'title' ); ?>" name="<?php echo $this->get_field_name( 'title' ); ?>" type="text" value="<?php echo $title; ?>" /></p>
		<?php if ( BP_GROUP_DOCUMENTS_FEATURED ) { ?>
	    	<p><label><?php printf( __( 'Show featured %s only', 'bp-group-documents' ), $this->bp_group_documents_name ); ?></label> <input type="checkbox" id="<?php echo $this->get_field_id( 'featured' ); ?>" name="<?php echo $this->get_field_name( 'featured' ); ?>" value="1" <?php
																		     checked( $featured );
		?>>
	    </p>
		    <?php } ?>

	    <p><label><?php _e( 'Number of items to show:', 'bp-group-documents' ); ?></label> <input class="widefat" id="<?php echo $this->get_field_id( 'num_items' ); ?>" name="<?php echo $this->get_field_name( 'num_items' ); ?>" type="text" value="<?php echo $num_items; ?>" style="width: 30%" /></p>
		    <?php
		}

}

/**
 * Current displayed group documents widget
 * @version 1, 22/4/2014, stergatu
 */
class BP_Group_Documents_CurrentGroup_Widget extends WP_Widget {

    var $bp_group_documents_name;

    function __construct() {
	$bp = buddypress();
	$nav_page_name = get_option( 'bp_group_documents_nav_page_name' );
	$this->bp_group_documents_name = ! empty( $nav_page_name ) ? $nav_page_name : __( 'Documents', 'bp-group-documents' );
	parent::__construct(
		'bp_group_documents_current_group_widget', '(BP Group Documents) ' . sprintf( __( '%s in this group', 'bp-group-documents' ), $this->bp_group_documents_name ), // Name
		array( 'description' => sprintf( __( '%s for the current group.', 'bp-group-documents' ), $this->bp_group_documents_name ),
	    'classname' => 'bp_group_documents_widget' )
	);

	if ( is_active_widget( false, false, $this->id_base ) ) {
	    add_action( '', 'bp_group_documents_add_my_stylesheet' );
	}
    }

    /**
     *
     * @param type $args
     * @param array $instance
     * @version 3, 6/4/2015 fix for hidden groups
     * v2, 24/4/2014
     */
    function widget( $args, $instance ) {
	$bp = buddypress();
	$instance['group_id'] = bp_get_current_group_id();

	if ( $instance['group_id'] > 0 ) {
	    $group = $bp->groups->current_group;
	    // If the group  public, or the user is super_admin or the user is member of group
	    if ( ($group->status == 'public') || (is_super_admin()) || (groups_is_user_member( bp_loggedin_user_id(), $instance['group_id'] )) ) {
		extract( $args );
		$title = apply_filters( 'widget_title', empty( $instance['title'] ) ? sprintf( __( 'Recent %s for the group', 'bp-group-documents' ), $this->bp_group_documents_name ) : $instance['title']  );

			echo $before_widget . $before_title . esc_html( $title ) . $after_title;

		do_action( 'bp_group_documents_current_group_widget_before_html' );
		$document_list = BP_Group_Documents::get_list_for_newest_widget( absint( $instance['num_items'] ), $instance['group_id'], ( bool ) $instance['featured'] );
		if ( $document_list && count( $document_list ) >= 1 ) {
		    echo '<ul id="bp-group-documents-current-group" class="bp-group-documents-list">';
		    foreach ( $document_list as $item ) {
			$document = new BP_Group_Documents( $item['id'] );
			echo '<li>';
			if ( get_option( 'bp_group_documents_display_icons' ) ) {
			    $document->icon();
			}
			?>
	<a class="bp-group-documents-title" id="group-document-link-<?php echo esc_attr( $document->id ); ?>" href="<?php $document->url(); ?>" target="_blank"><?php echo str_replace( "\\", "", esc_html( stripslashes( $document->name ) ) ); ?>

						    <?php
						    if ( get_option( 'bp_group_documents_display_file_size' ) ) {
				echo ' <span class="group-documents-filesize">(' . esc_html( get_file_size( $document ) ) . ')</span>';
			    }
			    ?></a> &nbsp;<div class="bp-group-documents-meta">
							<?php
							$document->categories();

			    printf( __( 'Uploaded by %s on %s', 'bp-group-documents' ), bp_core_get_userlink( $document->user_id ), date_i18n( get_option( 'date_format' ), $document->created_ts ) );
						    ?>
						    <?php
						    echo '</li>';
			}
			echo '</ul>';
		    } else {
			echo '<div class="widget-error">' . sprintf( __( 'There are no %s to display.', 'bp-group-documents' ), $this->bp_group_documents_name ) . '</div></p>';
		    }
		    if ( is_user_logged_in() ) {
			if ( BP_Group_Documents::current_user_can( 'add', $instance['group_id'] ) ) {
			    $url = bp_get_group_permalink( $bp->groups->current_group ) . BP_GROUP_DOCUMENTS_SLUG . '/add';
			    ?>
										<div class="generic-button group-button public"><a href="<?php echo esc_url( $url ); ?>" class="generic-button"><?php _e( "Add New", 'buddypress' ); ?></a></div>
							<?php
						    }
		    }
		    echo '<div class="view-all"><a href="' . esc_url( bp_get_group_permalink( $bp->groups->current_group ) ) . BP_GROUP_DOCUMENTS_SLUG . '#object-nav">' . __( "View all", 'bp-group-documents' ) . '</a></div>';
		    echo $after_widget;
		}
	    }
	}

	function update( $new_instance, $old_instance ) {
	    do_action( 'bp_group_documents_current_group_widget_update' );

	    $default_title = sprintf( __( 'Recent %s for the group', 'bp-group-documents' ), $this->bp_group_documents_name );

	    $new_title = sanitize_text_field( $new_instance['title'] );
	    $num_items = empty( $new_instance['num_items'] ) ? 5 : absint( $new_instance['num_items'] );

	    $instance = $old_instance;
	    $instance['title'] = empty( $new_title ) ? sanitize_text_field( $default_title ) : $new_title;
	    $instance['featured'] = intval( ( bool ) $new_instance['featured'] );
	    $instance['num_items'] = empty( $num_items ) ? 5 : absint( $new_instance['num_items'] );

	    return $instance;
	}

	function form( $instance ) {
	    do_action( 'bp_group_documents_current_group_widget_form' );

	    $defaults = array(
		'featured' => false,
		'num_items' => 5,
		'title' => sprintf( __( 'Recent %s for the group', 'bp-group-documents' ), $this->bp_group_documents_name ),
	    );

	    $instance = wp_parse_args( ( array ) $instance, $defaults );
	    $title = esc_attr( $instance['title'] );
	    $featured = ( bool ) $instance['featured'];
	    $num_items = empty( $instance['num_items'] ) ? 5 : absint( $instance['num_items'] );
	    ?>

					    <p><label><?php _e( 'Title:', 'bp-group-documents' ); ?></label><input class="widefat" id="<?php echo $this->get_field_id( 'title' ); ?>" name="<?php echo $this->get_field_name( 'title' ); ?>" type="text" value="<?php echo $title; ?>" /></p>
				    <?php if ( BP_GROUP_DOCUMENTS_FEATURED ) { ?>
	    			    <p>
	    				<label><?php printf( __( 'Show featured %s only', 'bp-group-documents' ), $this->bp_group_documents_name ); ?></label>
	    				<input type="checkbox" id="<?php echo $this->get_field_id( 'featured' ); ?>" name="<?php echo $this->get_field_name( 'featured' ); ?>" value="1" <?php checked( $featured ); ?>>
	    			    </p>
				    <?php } ?>

				<p><label><?php _e( 'Number of items to show:', 'bp-group-documents' ); ?></label> <input class="widefat" id="<?php echo $this->get_field_id( 'num_items' ); ?>" name="<?php echo $this->get_field_name( 'num_items' ); ?>" type="text" value="<?php echo $num_items; ?>" style="width: 30%" /></p>
			<?php
		    }

    }

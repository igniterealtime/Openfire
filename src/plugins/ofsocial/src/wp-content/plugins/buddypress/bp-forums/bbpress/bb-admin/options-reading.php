<?php

require_once( 'admin.php' );

if ( 'post' == strtolower( $_SERVER['REQUEST_METHOD'] ) && $_POST['action'] == 'update') {
	
	bb_check_admin_referer( 'options-reading-update' );
	
	foreach ( (array) $_POST as $option => $value ) {
		if ( !in_array( $option, array('_wpnonce', '_wp_http_referer', 'action', 'submit') ) ) {
			$option = trim( $option );
			$value = is_array( $value ) ? $value : trim( $value );
			$value = stripslashes_deep( $value );
			if ( $value ) {
				bb_update_option( $option, $value );
			} else {
				bb_delete_option( $option );
			}
		}
	}
	
	$goback = add_query_arg('updated', 'true', wp_get_referer());
	bb_safe_redirect($goback);
	exit;
}

if ( !empty( $_GET['updated'] ) )
	bb_admin_notice( '<strong>' . __( 'Settings saved.' ) . '</strong>' );

$reading_options = array(
	'page_topics' => array(
		'title' => __( 'Items per page' ),
		'class' => 'short',
		'note' => __( 'Number of topics, posts or tags to show per page.' )
	),
	'name_link_profile' => array(
		'title' => __( 'Link name to' ),
		'type' => 'radio',
		'options' => array(
			0 => __( 'Website' ),
			1 => __( 'Profile' )
		),
		'note' => __( 'What should the user\'s name link to on the topic page? The user\'s title would automatically get linked to the option you don\'t choose. By default, the user\'s name is linked to his/her website.' )
	)
);

$bb_admin_body_class = ' bb-admin-settings';

bb_get_admin_header();

?>

<div class="wrap">

<h2><?php _e( 'Reading Settings' ); ?></h2>
<?php do_action( 'bb_admin_notices' ); ?>

<form class="settings" method="post" action="<?php bb_uri( 'bb-admin/options-reading.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN ); ?>">
	<fieldset>
<?php
foreach ( $reading_options as $option => $args ) {
	bb_option_form_element( $option, $args );
}
?>
	</fieldset>
	<fieldset class="submit">
		<?php bb_nonce_field( 'options-reading-update' ); ?>
		<input type="hidden" name="action" value="update" />
		<input class="submit" type="submit" name="submit" value="<?php _e( 'Save Changes' ) ?>" />
	</fieldset>
</form>

</div>

<?php

bb_get_admin_footer();

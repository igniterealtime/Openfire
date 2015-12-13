<?php

require_once('admin.php');

if ( 'post' == strtolower( $_SERVER['REQUEST_METHOD'] ) && $_POST['action'] == 'update' ) {
	
	bb_check_admin_referer( 'options-writing-update' );
	
	// Deal with xmlrpc checkbox when it isn't checked
	if ( !isset( $_POST['enable_xmlrpc'] ) ) {
		$_POST['enable_xmlrpc'] = false;
	}
	
	foreach ( (array) $_POST as $option => $value ) {
		if ( !in_array( $option, array( '_wpnonce', '_wp_http_referer', 'action', 'submit' ) ) ) {
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
	
	$goback = add_query_arg( 'updated', 'true', wp_get_referer() );
	bb_safe_redirect( $goback );
	exit;
}

if ( !empty($_GET['updated']) ) {
	bb_admin_notice( __( '<strong>Settings saved.</strong>' ) );
}

$general_options = array(
	'edit_lock' => array(
		'title' => __( 'Lock post editing after' ),
		'class' => 'short',
		'after' => __( 'minutes' ),
		'note' => __( 'A user can edit a post for this many minutes after submitting.' ),
	),
	'throttle_time' => array(
		'title' => __( 'Throttle time' ),
		'class' => 'short',
		'after' => __( 'seconds' ),
		'note' => __( 'Users must wait this many seconds between posts. By default, moderators, administrators and keymasters are not throttled.' )
	)
);

$remote_options = array(
	'enable_xmlrpc' => array(
		'title' => __( 'XML-RPC' ),
		'type' => 'checkbox',
		'options' => array(
			1 => __( 'Enable the bbPress XML-RPC publishing protocol.' )
		)
	)
);

$bb_admin_body_class = ' bb-admin-settings';

bb_get_admin_header();

?>

<div class="wrap">

<h2><?php _e( 'Writing Settings' ); ?></h2>
<?php do_action( 'bb_admin_notices' ); ?>

<form class="settings" method="post" action="<?php bb_uri( 'bb-admin/options-writing.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN ); ?>">
	<fieldset><?php foreach ( $general_options as $option => $args ) bb_option_form_element( $option, $args ); ?></fieldset>
	<fieldset>
		<legend><?php _e( 'Remote Publishing' ); ?></legend>
		<p>
			<?php _e( 'To interact with bbPress from a desktop client or remote website that uses the XML-RPC publishing interface you must enable it below.' ); ?>
		</p>
<?php		foreach ( $remote_options as $option => $args ) bb_option_form_element( $option, $args ); ?>
	</fieldset>
	<fieldset class="submit">
		<?php bb_nonce_field( 'options-writing-update' ); ?>
		<input type="hidden" name="action" value="update" />
		<input class="submit" type="submit" name="submit" value="<?php _e( 'Save Changes' ); ?>" />
	</fieldset>
</form>

</div>

<?php

bb_get_admin_footer();

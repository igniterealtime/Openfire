<?php
require('./bb-load.php');

$error = false;

if ( $_POST ) {
	$action = 'send_key';
	$user_login = sanitize_user( $_POST['user_login'], true );
	if ( empty( $user_login ) ) {
		$error = __('No username specified');
	} else {
		$send_key_result = bb_reset_email( $user_login );
		if ( is_wp_error( $send_key_result ) )
			$error = $send_key_result->get_error_message();
	}
} elseif ( isset( $_GET['key'] ) ) {
	$action = 'reset_password';
	$reset_pasword_result = bb_reset_password( $_GET['key'] );
	if ( is_wp_error( $reset_pasword_result ) )
		$error = $reset_pasword_result->get_error_message();
}

bb_load_template( 'password-reset.php', array('action', 'error') );
?>

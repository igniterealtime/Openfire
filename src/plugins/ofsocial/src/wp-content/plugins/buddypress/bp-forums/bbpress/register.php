<?php
require('./bb-load.php');

bb_ssl_redirect();

$profile_info_keys = bb_get_profile_info_keys();

unset($profile_info_keys['first_name']);
unset($profile_info_keys['last_name']);
unset($profile_info_keys['display_name']);

$user_login = '';
$user_safe = true;

$bb_register_error = new WP_Error;

$_globals = array('profile_info_keys', 'user_safe', 'user_login', 'user_email', 'user_url', 'bad_input', 'bb_register_error');
$_globals = array_merge($_globals, array_keys($profile_info_keys));

if ( $_POST && 'post' == strtolower($_SERVER['REQUEST_METHOD']) ) {
	$_POST = stripslashes_deep( $_POST );
	$_POST['user_login'] = trim( $_POST['user_login'] );
	$user_login = sanitize_user( $_POST['user_login'], true );
	if ( $user_login !== $_POST['user_login'] ) {
		$bad_input = true;
		if ( $user_login ) {
			$bb_register_error->add( 'user_login', sprintf( __( '%s is an invalid username. How\'s this one?' ), esc_html( $_POST['user_login'] ) ) );
		} else {
			$bb_register_error->add( 'user_login', sprintf( __( '%s is an invalid username.' ), esc_html( $_POST['user_login'] ) ) );
		}
	}

	foreach ( $profile_info_keys as $key => $label ) {
		if ( is_string($$key) )
			$$key = esc_attr( $$key );
		elseif ( is_null($$key) )
			$$key = esc_attr( $_POST[$key] );

		if ( !$$key && $label[0] == 1 ) {
			$bad_input = true;
			$$key = false;
			$bb_register_error->add( $key, sprintf( __( '%s is required' ), $label[1] ) );
		}
	}

	if ( !$bad_input ) {
		$user_id = bb_new_user( $user_login, $_POST['user_email'], $_POST['user_url'] );
		if ( is_wp_error( $user_id ) ) { // error
			foreach ( $user_id->get_error_codes() as $code )
				$bb_register_error->add( $code, $user_id->get_error_message( $code ) );
			if ( $bb_register_error->get_error_message( 'user_login' ) )
				$user_safe = false;
		} elseif ( $user_id ) { // success
			foreach( $profile_info_keys as $key => $label )
				if ( strpos($key, 'user_') !== 0 && $$key !== '' )
					bb_update_usermeta( $user_id, $key, $$key );
			do_action('register_user', $user_id);

			bb_load_template( 'register-success.php', $_globals );
			exit;	
		} // else failure
	}
}

if ( isset( $_GET['user'] ) )
	$user_login = sanitize_user( $_GET['user'], true ) ;
elseif ( isset( $_POST['user_login'] ) && !is_string($user_login) )
	$user_login = '';

bb_load_template( 'register.php', $_globals );

?>

<?php
define( 'BB_IS_ADMIN', true );

require_once('../bb-load.php');

bb_ssl_redirect();

bb_auth();

if ( bb_get_option( 'bb_db_version' ) > bb_get_option_from_db( 'bb_db_version' ) ) {
	bb_safe_redirect( 'upgrade.php' );
	die();
}

require_once( BB_PATH . 'bb-admin/includes/functions.bb-admin.php' );

$bb_admin_page = bb_find_filename( $_SERVER['PHP_SELF'] );

$_check_callback = false;
if ( $bb_admin_page == 'admin-base.php' ) {
	$bb_admin_page = (string) @$_GET['plugin'];
	$_check_callback = true;
}

wp_enqueue_script( 'common' );

bb_user_settings();
if ( isset( $_GET['foldmenu'] ) ) {
	if ( $_GET['foldmenu'] ) {
		bb_update_user_setting( 'fm', 'f' );
	} else {
		bb_delete_user_setting( 'fm' );
	}
	bb_safe_redirect( remove_query_arg( 'foldmenu', stripslashes( $_SERVER['REQUEST_URI'] ) ) );
	die;
}
bb_admin_menu_generator();
bb_get_current_admin_menu();

if ( $_check_callback ) {
	if ( empty( $bb_registered_plugin_callbacks ) || empty( $bb_admin_page ) || !in_array( $bb_admin_page, $bb_registered_plugin_callbacks ) ) {
		unset( $bb_admin_page );
	}
}
?>

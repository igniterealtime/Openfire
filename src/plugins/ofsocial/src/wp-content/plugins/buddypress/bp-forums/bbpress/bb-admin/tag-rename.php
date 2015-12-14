<?php

require( 'admin.php' );

if ( !bb_current_user_can( 'manage_tags' ) )
	bb_die( __( 'You are not allowed to manage tags.' ) );

$tag_id = (int) $_POST['id' ];
$tag    = stripslashes( $_POST['tag'] );

bb_check_admin_referer( 'rename-tag_' . $tag_id );

if ( !$old_tag = bb_get_tag( $tag_id ) )
	bb_die( __( 'Tag not found.' ) );

if ( $tag = bb_rename_tag( $tag_id, $tag ) )
	wp_redirect( bb_get_tag_link() );
else
	bb_die( printf( __( 'There already exists a tag by that name or the name is invalid. <a href="%s">Try Again</a>'), wp_get_referer() ) );

exit;

?>

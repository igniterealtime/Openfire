<?php

require( 'admin.php' );

if ( !bb_current_user_can( 'manage_tags' ) )
	bb_die( __( 'You are not allowed to manage tags.' ) );

$tag_id = (int) $_POST['id' ];

bb_check_admin_referer( 'destroy-tag_' . $tag_id );

if ( !$old_tag = bb_get_tag( $tag_id ) )
	bb_die( __( 'Tag not found.' ) );

if ( bb_destroy_tag( $tag_id ) )
	bb_die( __( 'That tag was successfully destroyed' ) );
else
	bb_die( printf( __( "Something odd happened when attempting to destroy that tag.<br />\n<a href=\"%s\">Try Again?</a>" ), wp_get_referer() ) ) ;

?>

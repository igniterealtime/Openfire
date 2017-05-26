<?php
require_once('admin.php');

if ( !bb_current_user_can('manage_forums') )
	bb_die(__("You don't have the authority to mess with the forums."));

if ( !isset($_POST['action']) ) {
	wp_redirect( bb_get_uri('bb-admin/forums.php', null, BB_URI_CONTEXT_HEADER + BB_URI_CONTEXT_BB_ADMIN) );
	exit;
}

$sent_from = wp_get_referer();

switch ( $_POST['action'] ) :
case 'add' :
	if ( !isset($_POST['forum_name']) || '' === $_POST['forum_name'] )
		bb_die(__('Bad forum name.  Go back and try again.'));

	bb_check_admin_referer( 'add-forum' );

	if ( false !== bb_new_forum( $_POST ) ) :
		bb_safe_redirect( $sent_from );
		exit;
	else :
		bb_die(__('The forum was not added'));
	endif;
	break;
case 'update' :
	bb_check_admin_referer( 'update-forum' );

	if ( !$forums = bb_get_forums() )
		bb_die(__('No forums to update!'));
	if ( (int) $_POST['forum_id'] && isset($_POST['forum_name']) && '' !== $_POST['forum_name'] )
		bb_update_forum( $_POST );
	foreach ( array('action', 'id') as $arg )
		$sent_from = remove_query_arg( $arg, $sent_from );
	bb_safe_redirect( add_query_arg( 'message', 'updated', $sent_from ) );
	exit;
	break;
case 'delete' :
	bb_check_admin_referer( 'delete-forums' );

	$forum_id = (int) $_POST['forum_id'];
	$move_topics_forum = (int) $_POST['move_topics_forum'];

	if ( !bb_current_user_can( 'delete_forum', $forum_id ) )
		bb_die(__("You don't have the authority to kill off the forums."));

	if ( isset($_POST['move_topics']) && $_POST['move_topics'] != 'delete' )
		bb_move_forum_topics( $forum_id, $move_topics_forum );

	if ( !bb_delete_forum( $forum_id ) )
		bb_die( __('Error occured while trying to delete forum') );

	foreach ( array('action', 'id') as $arg )
		$sent_from = remove_query_arg( $arg, $sent_from );
	bb_safe_redirect( add_query_arg( 'message', 'deleted', $sent_from ) );
	exit;
	break;
endswitch;
?>

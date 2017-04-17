<?php
require_once('admin.php');

$forums = bb_get_forums();
$forums_count = $forums ? count($forums) : 0;

if ( isset($_GET['action']) && 'delete' == $_GET['action'] ) {
	$forum_to_delete = (int) $_GET['id'];
	$deleted_forum = bb_get_forum( $forum_to_delete );
	if ( !$deleted_forum || $forums_count < 2 || !bb_current_user_can( 'delete_forum', $forum_to_delete ) ) {
		bb_safe_redirect( add_query_arg( array('action' => false, 'id' => false) ) );
		exit;
	}
}

if ( isset($_GET['message']) ) {
	switch ( $_GET['message'] ) :
	case 'updated' :
		bb_admin_notice( __( '<strong>Forum Updated.</strong>' ) );
		break;
	case 'deleted' :
		bb_admin_notice( sprintf(
			__( '<strong>Forum deleted.</strong>  You should <a href="%s">recount your site information</a>.' ),
			bb_get_uri('bb-admin/tools-recount.php', null, BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN)
		) );
		break;
	endswitch;
}

if ( !isset($_GET['action']) )
	wp_enqueue_script( 'admin-forums' );
elseif ( 'delete' == @$_GET['action'] )
	bb_admin_notice( sprintf( __( 'Are you sure you want to delete the "<strong>%s</strong>" forum?' ), $deleted_forum->forum_name ) );

$bb_admin_body_class = ' bb-admin-forums';

bb_get_admin_header();
?>

<div class="wrap">

<h2><?php _e('Forums'); ?></h2>
<?php do_action( 'bb_admin_notices' ); ?>
<?php switch ( @$_GET['action'] ) : ?>
<?php case 'edit' : ?>
<?php bb_forum_form( (int) $_GET['id'] ); ?>
<?php break; case 'delete' : ?>

<form class="settings" method="post" id="delete-forums" action="<?php bb_uri('bb-admin/bb-forum.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN); ?>">
	<fieldset>
		<legend><?php _e('Delete Forum'); ?></legend>
		<p><?php _e('This forum contains:'); ?></p>
		<ul>
			<li><?php printf(__ngettext('%d topic', '%d topics', $deleted_forum->topics), $deleted_forum->topics); ?></li>
			<li><?php printf(__ngettext('%d post', '%d posts', $deleted_forum->posts), $deleted_forum->posts); ?></li>
		</ul>
		<div id="option-forum-delete-contents">
			<div class="label"><?php _e( 'Action' ); ?></div>
			<div class="inputs">
				<label class="radios">
					<input type="radio" name="move_topics" id="move-topics-delete" value="delete" /> <?php _e('Delete all topics and posts in this forum. <em>This can never be undone.</em>'); ?>
				</label>
				<label class="radios">
					<input type="radio" name="move_topics" id="move-topics-move" value="move" checked="checked" /> <?php _e('Move topics from this forum into the replacement forum below.'); ?>
				</label>
			</div>
		</div>
		<div id="option-forum-delete-contents">
			<label for="move-topics-forum"><?php _e( 'Replacement forum' ); ?></label>
			<div class="inputs">
				<?php bb_forum_dropdown( array('id' => 'move_topics_forum', 'callback' => 'strcmp', 'callback_args' => array($deleted_forum->forum_id), 'selected' => $deleted_forum->forum_parent) ); ?>
			</div>
		</div>
	</fieldset>
	<fieldset class="submit">
		<?php bb_nonce_field( 'delete-forums' ); ?>
		<input type="hidden" name="action" value="delete" />
		<input type="hidden" name="forum_id" value="<?php echo $deleted_forum->forum_id; ?>" />
		<a href="<?php bb_uri('bb-admin/forums.php', null, BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN); ?>" class="cancel"><?php _e('Cancel') ?></a>
		<input class="submit delete" type="submit" name="submit" value="<?php _e('Delete Forum') ?>" />
	</fieldset>
</form>

<?php break; default : ?>


<?php if ( bb_forums( 'type=list&walker=BB_Walker_ForumAdminlistitems' ) ) : ?>
<ul id="forum-list" class="list:forum list-block holder">
	<li class="thead list-block"><?php _e('Forum'); ?></li>
<?php while ( bb_forum() ) : ?>
<?php bb_forum_row(); ?>
<?php endwhile; ?>
</ul>
<?php endif; // bb_forums() ?>

<hr class="settings" />

<?php bb_forum_form(); ?>

<?php break; endswitch; // action ?>

<div id="ajax-response"></div>

</div>

<?php bb_get_admin_footer(); ?>

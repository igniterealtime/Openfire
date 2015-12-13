<?php
require_once( 'admin.php' );
require_once( 'includes/functions.bb-recount.php' );

if ( 'post' == strtolower( $_SERVER['REQUEST_METHOD'] ) ) {
	bb_check_admin_referer( 'do-counts' );

	// Stores messages
	$messages = array();

	if ( !empty( $_POST['topic-posts'] ) ) {
		$message = bb_recount_topic_posts();
		$messages[] = $message[1];
	}

	if ( !empty( $_POST['topic-voices'] ) ) {
		$message = bb_recount_topic_voices();
		$messages[] = $message[1];
	}

	if ( !empty( $_POST['topic-deleted-posts'] ) ) {
		$message = bb_recount_topic_deleted_posts();
		$messages[] = $message[1];
	}

	if ( !empty( $_POST['forums'] ) ) {
		$message = bb_recount_forum_topics();
		$messages[] = $message[1];
		$message = bb_recount_forum_posts();
		$messages[] = $message[1];
	}

	if ( !empty( $_POST['topics-replied'] ) ) {
		$message = bb_recount_user_topics_replied();
		$messages[] = $message[1];
	}

	if ( !empty( $_POST['topic-tag-count'] ) ) {
		$message = bb_recount_topic_tags();
		$messages[] = $message[1];
	}

	if ( !empty( $_POST['tags-tag-count'] ) ) {
		$message = bb_recount_tag_topics();
		$messages[] = $message[1];
	}

	if ( !empty( $_POST['tags-delete-empty'] ) ) {
		$message = bb_recount_tag_delete_empty();
		$messages[] = $message[1];
	}

	if ( !empty( $_POST['clean-favorites'] ) ) {
		$message = bb_recount_clean_favorites();
		$messages[] = $message[1];
	}

	bb_recount_list();
	foreach ( (array) $recount_list as $item ) {
		if ( isset($item[2]) && isset($_POST[$item[0]]) && 1 == $_POST[$item[0]] && is_callable($item[2]) ) {
			$message = call_user_func( $item[2] );
			if ( is_array( $message ) ) {
				$messages[] = $message[1];
			} else {
				$messages[] = $message;
			}
		}
	}

	wp_cache_flush();

	if ( count( $messages ) ) {
		$messages = join( '</p>' . "\n" . '<p>', $messages );
		bb_admin_notice( $messages );
	}
}


$bb_admin_body_class = ' bb-admin-tools';

bb_get_admin_header();
?>
<h2><?php _e('Tools') ?></h2>
<?php do_action( 'bb_admin_notices' ); ?>

<form class="settings" method="post" action="<?php bb_uri('bb-admin/tools-recount.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN); ?>">
	<fieldset>
		<legend><?php _e( 'Re-count' ) ?></legend>
		<p><?php _e( 'To minimize database queries, bbPress keeps it\'s own count of various items like posts in each topic and topics in each forum. Occasionally these internal counters may become incorrect, you can manually re-count these items using this form.' ) ?></p>
		<p><?php _e( 'You can also clean out some stale items here, like empty tags.' ) ?></p>
<?php
bb_recount_list();
if ( $recount_list ) {
?>
		<div id="option-counts">
			<div class="label">
				<?php _e( 'Items to re-count' ); ?>
			</div>
			<div class="inputs">
<?php
	foreach ( $recount_list as $item ) {
		echo '<label class="checkboxs"><input type="checkbox" class="checkbox" name="' . esc_attr( $item[0] ) . '" id="' . esc_attr( str_replace( '_', '-', $item[0] ) ) . '" value="1" /> ' . esc_html( $item[1] ) . '</label>' . "\n";
	}
?>
			</div>
		</div>
<?php
} else {
?>
		<p><?php _e( 'There are no re-count tools available.' ) ?></p>
<?php
}
?>
	</fieldset>
	<fieldset class="submit">
		<?php bb_nonce_field( 'do-counts' ); ?>
		<input class="submit" type="submit" name="submit" value="<?php _e('Recount Items') ?>" />
	</fieldset>
</form>

<?php bb_get_admin_footer(); ?>

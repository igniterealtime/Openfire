<?php
require_once('admin.php');

if ( 'post' == strtolower( $_SERVER['REQUEST_METHOD'] ) ) {
	bb_check_admin_referer( 'topic-bulk' );

	$topic_ids = array_map( 'absint', $_POST['topic'] );

	$affected = 0;

	$action = trim( $_POST['action'] );

	switch ( $action ) {
	case 'close' :
		foreach ( $topic_ids as $topic_id ) {
			$affected += bb_close_topic( $topic_id );
		}
		$query_vars = array( 'message' => 'closed', 'count' => $affected );
		break;
	case 'open' :
		foreach ( $topic_ids as $topic_id ) {
			$affected += bb_open_topic( $topic_id );
		}
		$query_vars = array( 'message' => 'opened', 'count' => $affected );
		break;
	case 'delete' :
		foreach ( $topic_ids as $topic_id ) {
			$affected += (int) (bool) bb_delete_topic( $topic_id, 1 );
		}
		$query_vars = array( 'message' => 'deleted', 'count' => $affected );
		break;
	case 'undelete' :
		foreach ( $topic_ids as $topic_id ) {
			$affected += (int) (bool) bb_delete_topic( $topic_id, 0 );
		}
		$query_vars = array( 'message' => 'undeleted', 'count' => $affected );
		break;
	default :
		if ( $action )
			$query_vars = apply_filters( "bulk_topic__$action", array(), $topic_ids );
		break;
	}

	bb_safe_redirect( add_query_arg( $query_vars ) );
	exit;
}

if ( !empty( $_GET['message'] ) ) {
	$message_count = isset( $_GET['count'] ) ? (int) $_GET['count'] : 1;

	switch ( (string) $_GET['message'] ) {
		case 'undeleted':
			bb_admin_notice( sprintf( _n( '<strong>Topic undeleted.</strong>', '<strong>%s topics undeleted.</strong>', $message_count ), bb_number_format_i18n( $message_count ) ) );
			break;
		case 'deleted':
			bb_admin_notice( sprintf( _n( '<strong>Topic deleted.</strong>', '<strong>%s topics deleted.</strong>', $message_count ), bb_number_format_i18n( $message_count ) ) );
			break;
		case 'opened':
			bb_admin_notice( sprintf( _n( '<strong>Topic opened.</strong>', '<strong>%s topics opened.</strong>', $message_count ), bb_number_format_i18n( $message_count ) ) );
			break;
		case 'closed':
			bb_admin_notice( sprintf( _n( '<strong>Topic closed.</strong>', '<strong>%s topics closed.</strong>', $message_count ), bb_number_format_i18n( $message_count ) ) );
			break;
	}
}

$bb_admin_body_class = ' bb-admin-topics';

bb_get_admin_header();

if ( !bb_current_user_can('browse_deleted') )
	die(__("Now how'd you get here?  And what did you think you'd being doing?")); //This should never happen.
add_filter( 'topic_link', 'bb_make_link_view_all' );
add_filter( 'topic_last_post_link', 'bb_make_link_view_all' );
$topic_query_vars = array( 'topic_status' => 'normal', 'open' => 'open', 'count' => true, 'per_page' => 20 );
if ( isset($_POST['search']) && $_POST['search'] ) {
	$topic_query_vars['post_status'] = 'all';
} elseif ( isset($_GET['search']) && $_GET['search'] ) {
	$topic_query_vars['post_status'] = 'all';
}
$topic_query = new BB_Query_Form( 'topic', $topic_query_vars );
$topics = $topic_query->results;
?>

<div class="wrap">

<h2><?php _e( 'Topics' ); ?>
<?php
$h2_search = $topic_query->get( 'search' );
$h2_forum  = $topic_query->get( 'forum_id' );
$h2_tag    = $topic_query->get( 'tag_id' );
$h2_author = $topic_query->get( 'topic_author_id' );

$h2_search = $h2_search ? ' ' . sprintf( __('containing &#8220;%s&#8221;'), esc_html( $h2_search ) ) : '';
$h2_forum  = $h2_forum  ? ' ' . sprintf( __('in &#8220;%s&#8221;')      , get_forum_name( $h2_forum ) ) : '';
$h2_tag    = $h2_tag    ? ' ' . sprintf( __('with tag &#8220;%s&#8221;'), esc_html( bb_get_tag_name( $h2_tag ) ) ) : '';
$h2_author = $h2_author ? ' ' . sprintf( __('by %s')                    , esc_html( get_user_name( $h2_author ) ) ) : '';

if ( $h2_search || $h2_forum || $h2_tag || $h2_author ) {
	echo '<span class="subtitle">';
	
	printf( __( '%1$s%2$s%3$s%4$s' ), $h2_search, $h2_forum, $h2_tag, $h2_author );
	
	echo '</span>';
}
?>
</h2>
<?php do_action( 'bb_admin_notices' ); ?>

<?php $topic_query->form( array('tag' => true, 'topic_author' => true, 'topic_status' => true, 'open' => true, 'submit' => __('Filter')) ); ?>

<div class="clear"></div>

<?php if ( !$topics ) : ?>

<p class="no-results"><?php _e('No topics found.'); ?></p>

<?php

else :
	bb_cache_first_posts( $topics ); bb_cache_last_posts( $topics );

	$bulk_actions = array(
		'delete' => __( 'Delete' ),
		'undelete' => __( 'Undelete' ),
		'open' => __( 'Open' ),
		'close' => __( 'Close' ),
	);

	if ( is_numeric( $bulk_action = $topic_query->get( 'topic_status' ) ) )
		unset( $bulk_actions[ $bulk_action ? 'delete' : 'undelete' ] );


	if ( is_numeric( $bulk_action = $topic_query->get( 'open' ) ) )
		unset( $bulk_actions[ $bulk_action ? 'open' : 'close' ] );

	unset( $bulk_action );

	do_action_ref_array( 'bulk_topic_actions', array( &$bulk_actions, $topic_query ) );
?>

<form class="table-form bulk-form" method="post" action="">
<fieldset>
	<select name="action">
		<option><?php _e( 'Bulk Actions' ); ?></option>
<?php	foreach ( $bulk_actions as $value => $label ) : ?>

		<option value="<?php echo esc_attr( $value ); ?>"><?php echo esc_html( $label ); ?></option>
<?php	endforeach; ?>
	</select>
	<input type="submit" value="<?php esc_attr_e( 'Apply' ); ?>" class="button submit-input" />
	<?php bb_nonce_field( 'topic-bulk' ); ?>
</fieldset>

<div class="tablenav">
<?php if ( $topic_query->found_rows ) : ?>
	<div class="tablenav-pages">
		<span class="displaying-num"><?php echo $displaying_num = sprintf(
			__( '%1$s to %2$s of %3$s' ),
			bb_number_format_i18n( ( $page - 1 ) * $topic_query->get( 'per_page' ) + 1 ),
			$page * $topic_query->get( 'per_page' ) < $topic_query->found_rows ? bb_number_format_i18n( $page * $topic_query->get( 'per_page' ) ) : '<span class="total-type-count">' . bb_number_format_i18n( $topic_query->found_rows ) . '</span>',
			'<span class="total-type-count">' . bb_number_format_i18n( $topic_query->found_rows ) . '</span>'
		); ?></span><span class="displaying-pages">
<?php
$_page_link_args = array(
	'page' => $page,
	'total' => $topic_query->found_rows,
	'per_page' => $topic_query->get( 'per_page' ),
	'mod_rewrite' => false,
	'prev_text' => __( '&laquo;' ),
	'next_text' => __( '&raquo;' )
);
echo $page_number_links = get_page_number_links( $_page_link_args );
?></span>
		<div class="clear"></div>
	</div>
<?php endif; ?>
</div>

<div class="clear"></div>

<table id="topics-list" class="widefat">
<thead>
<tr>
	<th scope="col" class="check-column"><input type="checkbox" /></th>
	<th scope="col"><?php _e('Topic') ?></th>
	<th scope="col"><?php _e('Author') ?></th>
	<th scope="col"><?php _e('Posts') ?></th>
	<th scope="col"><?php _e('Date') ?></th>
	<th scope="col"><?php _e('Freshness') ?></th>
</tr>
</thead>
<tfoot>
<tr>
	<th scope="col" class="check-column"><input type="checkbox" /></th>
	<th scope="col"><?php _e('Topic') ?></th>
	<th scope="col"><?php _e('Author') ?></th>
	<th scope="col"><?php _e('Posts') ?></th>
	<th scope="col"><?php _e('Date') ?></th>
	<th scope="col"><?php _e('Freshness') ?></th>
</tr>
</thead>

<tbody>
<?php foreach ( $topics as $topic ) : $first_post = bb_get_first_post( $topic ); ?>
<tr id="topic-<?php echo $topic->topic_id; ?>"<?php topic_class(); ?>>
	<td class="check-column"><input type="checkbox" name="topic[]" value="<?php echo $topic->topic_id; ?>" /></td>
	<td class="topic">
		<span class="row-title"><?php bb_topic_labels(); ?> <a href="<?php topic_link(); ?>"><?php topic_title(); ?></a></span>
		<div>
		<span class="row-actions">
			<a href="<?php topic_link(); ?>"><?php _e( 'View' ); ?></a> |
			<?php if ( $first_post ) : post_edit_link( $first_post->post_id ); ?> |
			<?php endif; topic_close_link( array( 'id' => $topic->topic_id, 'before' => '', 'after' => '', 'close_text' => __( 'Close' ), 'open_text' => _x( 'Open', 'action' ) ) ); ?> |
			<?php topic_delete_link( array( 'id' => $topic->topic_id, 'before' => '', 'after' => '', 'delete_text' => __( 'Delete' ), 'undelete_text' => __( 'Undelete' ) ) ); ?>
		</span>&nbsp;
		</div>
	</td>
	<td class="author">
		<a class="author-link" href="<?php user_profile_link( $topic->topic_poster ); ?>">
			<?php echo bb_get_avatar( $topic->topic_poster, '16' ); ?>
			<?php topic_author(); ?>
		</a>
	</td>
	<td class="posts num"><?php topic_posts(); ?></td>
	<td class="date num">
<?php
	if ( get_topic_start_time( 'U' ) < ( time() - 86400 ) ) {
		topic_start_time( 'Y/m/d\<\b\r \/\>H:i:s' );
	} else {
		printf( __( '%s ago' ), get_topic_start_time( 'since' ) );
	}
?>
	</td>
	<td class="freshness num"><a href="<?php topic_last_post_link(); ?>" title="<?php echo esc_attr( sprintf( __( 'Last post by %s' ), get_topic_last_poster() ) ); ?>">
<?php
	if ( get_topic_time( 'U' ) < ( time() - 86400 ) ) {
		topic_time( 'Y/m/d\<\b\r \/\>H:i:s' );
	} else {
		printf( __( '%s ago' ), get_topic_time( 'since' ) );
	}
?>
	
	<?php //topic_time( bb_get_datetime_formatstring_i18n() ); ?>
	
	
	</a></td>
</tr>
<?php endforeach; ?>
</tbody>
</table>
</form>
<?php endif; ?>

<div class="tablenav bottom">
<?php if ( $topic_query->found_rows ) : ?>
	<div class="tablenav-pages">
		<span class="displaying-pages"><?php echo $page_number_links; ?></span>
		<div class="clear"></div>
	</div>
<?php endif; ?>
</div>
<div class="clear"></div>

</div>

<?php bb_get_admin_footer(); ?>

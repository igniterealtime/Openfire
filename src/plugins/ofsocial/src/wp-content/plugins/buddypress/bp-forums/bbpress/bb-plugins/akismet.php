<?php
/*
Plugin Name: Akismet
Plugin URI: http://akismet.com/
Description: Akismet checks posts against the Akismet web service to see if they look like spam or not. You need a <a href="http://wordpress.com/api-keys/">WordPress.com API key</a> to use this service.
Author: Michael Adams
Version: 1.1
Author URI: http://blogwaffe.com/
*/



$bb_ksd_api_host = bb_get_option( 'akismet_key' ) . '.rest.akismet.com';
$bb_ksd_api_port = 80;
$bb_ksd_user_agent = 'bbPress/' . bb_get_option( 'version' ) . ' | bbAkismet/'. bb_get_option( 'version' );

function bb_akismet_verify_key( $key )
{
	global $bb_ksd_api_port;
	$blog = urlencode( bb_get_uri( null, null, BB_URI_CONTEXT_TEXT + BB_URI_CONTEXT_AKISMET ) );
	$response = bb_ksd_http_post( 'key=' . $key . '&blog=' . $blog, 'rest.akismet.com', '/1.1/verify-key', $bb_ksd_api_port );
	if ( 'valid' == $response[1] ) {
		return true;
	} else {
		return false;
	}
}

// Returns array with headers in $response[0] and entity in $response[1]
function bb_ksd_http_post( $request, $host, $path, $port = 80 )
{
	global $bb_ksd_user_agent;

	$http_request  = 'POST ' . $path . ' HTTP/1.0' . "\r\n";
	$http_request .= 'Host: ' . $host . "\r\n";
	$http_request .= 'Content-Type: application/x-www-form-urlencoded; charset=utf-8' . "\r\n"; // for now
	$http_request .= 'Content-Length: ' . strlen($request) . "\r\n";
	$http_request .= 'User-Agent: ' . $bb_ksd_user_agent . "\r\n";
	$http_request .= "\r\n";
	$http_request .= $request;
	$response = '';
	if ( false != ( $fs = @fsockopen( $host, $port, $errno, $errstr, 10 ) ) ) {
		fwrite( $fs, $http_request );

		while ( !feof( $fs ) ) {
			$response .= fgets( $fs, 1160 ); // One TCP-IP packet
		}
		fclose( $fs );
		$response = explode( "\r\n\r\n", $response, 2 );
	}
	return $response;
}

function bb_ksd_configuration_page()
{
?>
<h2><?php _e( 'Akismet Settings' ); ?></h2>
<?php do_action( 'bb_admin_notices' ); ?>

<form class="settings" method="post" action="<?php bb_uri( 'bb-admin/admin-base.php', array( 'plugin' => 'bb_ksd_configuration_page'), BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN ); ?>">
	<fieldset>
		<p><?php printf( __( 'For many people, <a href="%s">Akismet</a> will greatly reduce or even completely eliminate the spam you get on your site. If one does happen to get through, simply mark it as "spam" and Akismet will learn from the mistakes.' ), 'http://akismet.com/' ); ?></p>

<?php
	$after = '';
	if ( false !== $key = bb_get_option( 'akismet_key' ) ) {
		if ( bb_akismet_verify_key( $key ) ) {
			$after = __( 'This key is valid' );
		} else {
			bb_delete_option( 'akismet_key' );
		}
	}

	bb_option_form_element( 'akismet_key', array(
		'title' => __( 'WordPress.com API Key' ),
		'attributes' => array( 'maxlength' => 12 ),
		'after' => $after,
		'note' => sprintf( __( 'If you don\'t have a WordPress.com API Key, you can get one at <a href="%s">WordPress.com</a>' ), 'http://wordpress.com/api-keys/' )
	) );

	bb_option_form_element( 'akismet_stats', array(
		'title' => __( 'Enable stats page' ),
		'type' => 'checkbox',
		'options' => array(
			1 => __( 'Create a page that shows spam statistics.' )
		),
		'note' => __( 'This page will be viewable by moderators or higher.' )
	) );
?>

	</fieldset>
	<fieldset class="submit">
		<?php bb_nonce_field( 'options-akismet-update' ); ?>
		<input type="hidden" name="action" value="update-akismet-settings" />
		<input class="submit" type="submit" name="submit" value="<?php _e('Save Changes') ?>" />
	</fieldset>
</form>
<?php
}

function bb_ksd_configuration_page_add()
{
	bb_admin_add_submenu( __( 'Akismet' ), 'use_keys', 'bb_ksd_configuration_page', 'options-general.php' );
}
add_action( 'bb_admin_menu_generator', 'bb_ksd_configuration_page_add' );

function bb_ksd_configuration_page_process()
{
	if ( 'post' == strtolower( $_SERVER['REQUEST_METHOD'] ) && $_POST['action'] == 'update-akismet-settings') {
		bb_check_admin_referer( 'options-akismet-update' );

		$goback = remove_query_arg( array( 'invalid-akismet', 'updated-akismet' ), wp_get_referer() );

		if ( !isset( $_POST['akismet_stats'] ) ) {
			$_POST['akismet_stats'] = false;
		}

		if ( true === (bool) $_POST['akismet_stats'] ) {
			bb_update_option( 'akismet_stats', 1 );
		} else {
			bb_delete_option( 'akismet_stats' );
		}

		if ( $_POST['akismet_key'] ) {
			$value = stripslashes_deep( trim( $_POST['akismet_key'] ) );
			if ( $value ) {
				if ( bb_akismet_verify_key( $value ) ) {
					bb_update_option( 'akismet_key', $value );
				} else {
					$goback = add_query_arg( 'invalid-akismet', 'true', $goback );
					bb_safe_redirect( $goback );
					exit;
				}
			} else {
				bb_delete_option( 'akismet_key' );
			}
		} else {
			bb_delete_option( 'akismet_key' );
		}

		$goback = add_query_arg( 'updated-akismet', 'true', $goback );
		bb_safe_redirect( $goback );
		exit;
	}

	if ( !empty( $_GET['updated-akismet'] ) ) {
		bb_admin_notice( __( '<strong>Settings saved.</strong>' ) );
	}

	if ( !empty( $_GET['invalid-akismet'] ) ) {
		bb_admin_notice( __( '<strong>The key you attempted to enter is invalid. Reverting to previous setting.</strong>' ), 'error' );
	}

	global $bb_admin_body_class;
	$bb_admin_body_class = ' bb-admin-settings';
}
add_action( 'bb_ksd_configuration_page_pre_head', 'bb_ksd_configuration_page_process' );

// Bail here if no key is set
if ( !bb_get_option( 'akismet_key' ) ) {
	return;
}

function bb_ksd_stats_script()
{
?>
<style>
	#bb-ksd-stats-frame {
		-moz-box-shadow: 0 0 15px rgb(255, 255, 255);
		-webkit-box-shadow: 0 0 15px rgb(255, 255, 255);
		box-shadow: 0 0 15px rgb(255, 255, 255);
		margin-top: 16px;
		width: 100%;
		height: 700px;
		border-width: 0;
	}
</style>
<script type="text/javascript">
	function resizeIframe() {
		var height = document.documentElement.clientHeight;
		height -= document.getElementById('bb-ksd-stats-frame').offsetTop;
		height -= 60;
		document.getElementById('bb-ksd-stats-frame').style.height = height +"px";
	};
	function resizeIframeInit() {
		document.getElementById('bb-ksd-stats-frame').onload = resizeIframe;
		window.onresize = resizeIframe;
	}
	addLoadEvent(resizeIframeInit);
</script>
<?php
}

function bb_ksd_stats_display_pre_head()
{
	if ( !bb_get_option( 'akismet_stats' ) ) {
		return;
	}
	add_action( 'bb_admin_head', 'bb_ksd_stats_script' );
}
add_action( 'bb_ksd_stats_display_pre_head', 'bb_ksd_stats_display_pre_head' );

function bb_ksd_stats_display()
{
	$site = urlencode( bb_get_uri( null, null, BB_URI_CONTEXT_TEXT + BB_URI_CONTEXT_AKISMET ) );
	$url = "http://".bb_get_option( 'akismet_key' ).".web.akismet.com/1.0/user-stats.php?blog={$site}&amp;type=forum";
?>
	<iframe src="<?php echo $url; ?>" id="bb-ksd-stats-frame"></iframe>
<?php
}

function bb_ksd_stats_page()
{
	if ( !bb_get_option( 'akismet_stats' ) ) {
		return;
	}
	if ( function_exists( 'bb_admin_add_submenu' ) ) {
		bb_admin_add_submenu( __( 'Akismet Stats' ), 'moderate', 'bb_ksd_stats_display', 'index.php' );
	}
}
add_action( 'bb_admin_menu_generator', 'bb_ksd_stats_page' );

function bb_ksd_submit( $submit, $type = false )
{
	global $bb_ksd_api_host;
	global $bb_ksd_api_port;

	switch ( $type ) {
		case 'ham':
		case 'spam':
			$path = '/1.1/submit-' . $type;

			$bb_post = bb_get_post( $submit );
			if ( !$bb_post ) {
				return;
			}
			$user = bb_get_user( $bb_post->poster_id );
			if ( bb_is_trusted_user( $user->ID ) ) {
				return;
			}

			$_submit = array(
				'blog' => bb_get_uri( null, null, BB_URI_CONTEXT_TEXT + BB_URI_CONTEXT_AKISMET ),
				'user_ip' => $bb_post->poster_ip,
				'permalink' => get_topic_link( $bb_post->topic_id ), // First page
				'comment_type' => 'forum',
				'comment_author' => get_user_name( $user->ID ),
				'comment_author_email' =>  bb_get_user_email( $user->ID ),
				'comment_author_url' => get_user_link( $user->ID ),
				'comment_content' => $bb_post->post_text,
				'comment_date_gmt' => $bb_post->post_time
			);
			break;

		case 'hammer':
		case 'spammer':
			$path = '/1.1/submit-' . substr( $type, 0, -3 );

			$user = bb_get_user( $submit );
			if ( !$user ) {
				return;
			}
			if ( bb_is_trusted_user( $user->ID ) ) {
				return;
			}

			$_submit = array(
				'blog' => bb_get_uri( null, null, BB_URI_CONTEXT_TEXT + BB_URI_CONTEXT_AKISMET ),
				'permalink' => get_user_profile_link( $user->ID ),
				'comment_type' => 'profile',
				'comment_author' => get_user_name( $user->ID ),
				'comment_author_email' =>  bb_get_user_email( $user->ID ),
				'comment_author_url' => get_user_link( $user->ID ),
				'comment_content' => $user->occ . ' ' . $user->interests,
				'comment_date_gmt' => $user->user_registered
			);
			break;

		default:
			if ( bb_is_trusted_user( bb_get_current_user() ) ) {
				return;
			}

			$path = '/1.1/comment-check';

			$_submit = array(
				'blog' => bb_get_uri( null, null, BB_URI_CONTEXT_TEXT + BB_URI_CONTEXT_AKISMET ),
				'user_ip' => preg_replace( '/[^0-9., ]/', '', $_SERVER['REMOTE_ADDR'] ),
				'user_agent' => $_SERVER['HTTP_USER_AGENT'],
				'referrer' => $_SERVER['HTTP_REFERER'],
				'comment_type' => isset($_POST['topic_id']) ? 'forum' : 'profile',
				'comment_author' => bb_get_current_user_info( 'name' ),
				'comment_author_email' => bb_get_current_user_info( 'email' ),
				'comment_author_url' => bb_get_current_user_info( 'url' ),
				'comment_content' => $submit
			);
			if ( isset( $_POST['topic_id'] ) ) {
				$_submit['permalink'] = get_topic_link( $_POST['topic_id'] ); // First page
			}
			break;
	}

	$query_string = '';
	foreach ( $_submit as $key => $data ) {
		$query_string .= $key . '=' . urlencode( stripslashes( $data ) ) . '&';
	}
	return bb_ksd_http_post( $query_string, $bb_ksd_api_host, $path, $bb_ksd_api_port );
}

function bb_ksd_submit_ham( $post_id )
{
	bb_ksd_submit( $post_id, 'ham' );
}

function bb_ksd_submit_spam( $post_id )
{
	bb_ksd_submit( $post_id, 'spam' );
}

function bb_ksd_check_post( $post_text )
{
	global $bb_ksd_pre_post_status, $bb_ksd_pre_post;

	$bb_ksd_pre_post = $post_text;

	return $post_text;
}
add_action( 'pre_post', 'bb_ksd_check_post', 1 );

function bb_ksd_check_profile( $user_id )
{
	global $bb_current_user, $user_obj;
	$bb_current_id = bb_get_current_user_info( 'id' );
	bb_set_current_user( $user_id );
	if ( $bb_current_id && $bb_current_id != $user_id ) {
		if ( $user_obj->data->is_bozo && !$bb_current_user->data->is_bozo ) {
			bb_ksd_submit( $user_id, 'hammer' );
		}
		if ( !$user_obj->data->is_bozo && $bb_current_user->data->is_bozo ) {
			bb_ksd_submit( $user_id, 'spammer' );
		}
	} else {
		$response = bb_ksd_submit( $bb_current_user->data->occ . ' ' . $bb_current_user->data->interests );
		if ( 'true' == $response[1] && function_exists( 'bb_bozon' ) ) {
			bb_bozon( bb_get_current_user_info( 'id' ) );
		}
	}
	bb_set_current_user( (int) $bb_current_id );
}
add_action( 'register_user', 'bb_ksd_check_profile', 1);
add_action( 'profile_edited', 'bb_ksd_check_profile', 1);

function bb_ksd_new_post( $post_id )
{
	global $bb_ksd_pre_post_status;
	if ( '2' != $bb_ksd_pre_post_status ) {
		return;
	}
	$bb_post = bb_get_post( $post_id );
	$topic = get_topic( $bb_post->topic_id );
	if ( 0 == $topic->topic_posts ) {
		bb_delete_topic( $topic->topic_id, 2 );
	}
}
add_filter( 'bb_new_post', 'bb_ksd_new_post' );

function bb_akismet_delete_old()
{
	// Delete old every 20
	$n = mt_rand( 1, 20 );
	if ( $n % 20 ) {
		return;
	}
	global $bbdb;
	$now = bb_current_time( 'mysql' );
	$posts = (array) $bbdb->get_col( $bbdb->prepare(
		"SELECT post_id FROM $bbdb->posts WHERE DATE_SUB(%s, INTERVAL 15 DAY) > post_time AND post_status = '2'",
		$now
	) );
	foreach ( $posts as $post ) {
		bb_delete_post( $post, 1 );
	}
}

function bb_ksd_pre_post_status( $post_status, $post_ID )
{
	global $bb_current_user, $bb_ksd_pre_post_status, $bb_ksd_pre_post;

	// Don't filter content from users with a trusted role
	if ( in_array( $bb_current_user->roles[0], bb_trusted_roles() ) ) {
		return $post_status;
	}

	$response = bb_ksd_submit( $bb_ksd_pre_post );

	if ( isset( $response[1] ) ) {
		bb_update_postmeta( $post_ID, 'akismet_response', $response[1] );
	}

	if ( 'true' == $response[1] ) {
		$bb_ksd_pre_post_status = '2';
		return $bb_ksd_pre_post_status;
	}
	return $post_status;
}
add_filter( 'pre_post_status', 'bb_ksd_pre_post_status', 10, 2 );

function bb_ksd_delete_post( $post_id, $new_status, $old_status )
{
	// Don't report post deletion
	if ( 1 == $new_status ) {
		return;
	}
	// Don't report no change in post status
	if ( $new_status == $old_status ) {
		return;
	}
	// It's being marked as spam, so report it
	if ( 2 == $new_status ) {
		bb_ksd_submit_spam( $post_id );
		return;
	}
	// It's not spam (and not being deleted), so it's ham now
	if ( 2 == $old_status ) {
		bb_ksd_submit_ham( $post_id );
		return;
	}
}
add_action( 'bb_delete_post', 'bb_ksd_delete_post', 10, 3);

function bb_ksd_post_delete_link( $parts, $args )
{
	if ( !bb_current_user_can( 'moderate' ) ) {
		return $parts;
	}
	$bb_post = bb_get_post( get_post_id( $args['post_id'] ) );

	if ( 2 == $bb_post->post_status ) {
		$query = array(
			'id'     => $bb_post->post_id,
			'status' => 0,
			'view'   => 'all'
		);
		$display = __('Not Spam');
	} else {
		$query = array(
			'id'     => $bb_post->post_id,
			'status' => 2
		);
		$display = __('Spam');
	}
	$uri = bb_get_uri( 'bb-admin/delete-post.php', $query, BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN );
	$uri = esc_url( bb_nonce_url( $uri, 'delete-post_' . $bb_post->post_id ) );
	if ( !is_array( $parts ) ) {
		$parts = array();
		$before = '';
		$after = '';
	} else {
		$before = $args['last_each']['before'];
		$after = $args['last_each']['after'];
	}

	// Make sure that the last tag in $before gets a class (if it's there)
	if ( preg_match( '/.*(<[^>]+>)[^<]*/', $before, $_node ) ) {
		if ( preg_match( '/class=(\'|")(.*)\1/U', $_node[1], $_class ) ) {
			$before = str_replace( $_class[0], 'class=' . $_class[1] . 'before-post-spam-link ' . $_class[2] . $_class[1], $before );
		} else {
			$before = preg_replace( '/(.*)<([a-z0-9_-]+)(\s?)([^>]*)>([^<]*)/i', '$1<$2 class="before-post-spam-link"$3$4>$5', $before, 1 );
		}
	}

	$parts[] = $before . '<a class="post-spam-link" href="' . $uri . '" >' . $display . '</a>' . $after;
	return $parts;
}
add_filter( 'bb_post_admin', 'bb_ksd_post_delete_link', 10, 2 );

function bb_ksd_bulk_post_actions( &$bulk_actions, &$post_query ) {
	$status = $post_query->get( 'post_status' );

	$bulk_actions['unspam'] = __( 'Not Spam' );
	$bulk_actions['spam'] = __( 'Mark as Spam' );

	if ( 2 == $status )
		unset( $bulk_actions['undelete'], $bulk_actions['spam'] );
	elseif ( is_numeric( $status ) )
		unset( $bulk_actions['unspam'] );
}

add_action( 'bulk_post_actions', 'bb_ksd_bulk_post_actions', 10, 2 );

function bb_ksd_bulk_post__action( $query_vars, $post_ids, $action ) {
	$count = 0;

	switch ( $action ) {
	case 'spam' :
		foreach ( $post_ids as $post_id ) {
			$count += (int) (bool) bb_delete_post( $post_id, 2 );
		}
		return array( 'message' => 'spammed', 'count' => $count );
	case 'unspam' :
		foreach ( $post_ids as $post_id ) {
			$count += (int) (bool) bb_delete_post( $post_id, 0 );
		}
		return array( 'message' => 'unspammed-normal', 'count' => $count );
	}
}

add_action( 'bulk_post__spam', 'bb_ksd_bulk_post__action', 10, 3 );
add_action( 'bulk_post__unspam', 'bb_ksd_bulk_post__action', 10, 3 );

function bb_ksd_add_post_status_to_forms( $stati, $type )
{
	if ( 'post' === $type ) {
		$stati['2'] = __( 'Spam' );
	}
	return $stati;
}
add_filter( 'bb_query_form_post_status', 'bb_ksd_add_post_status_to_forms', 10, 2 );

function bb_ksd_post_del_class( $classes, $post_id, $post )
{
	if ( '2' === (string) $post->post_status ) {
		if ( $classes ) {
			return $classes . ' spam';
		}
		return 'spam';
	}
	return $classes;
}
add_filter( 'post_del_class', 'bb_ksd_post_del_class', 10, 3 );

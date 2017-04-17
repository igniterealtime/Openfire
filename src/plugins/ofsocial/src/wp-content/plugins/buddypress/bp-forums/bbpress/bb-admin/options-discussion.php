<?php

require_once('admin.php');

if ( 'post' == strtolower( $_SERVER['REQUEST_METHOD'] ) && $_POST['action'] == 'update') {
	
	bb_check_admin_referer( 'options-discussion-update' );
	
	// Deal with pingbacks checkbox when it isn't checked
	if ( !isset( $_POST['enable_pingback'] ) ) {
		$_POST['enable_pingback'] = false;
	}

	if ( !isset( $_POST['enable_loginless'] ) ) {
		$_POST['enable_loginless'] = false;
	}
	
	if ( !isset( $_POST['enable_subscriptions'] ) ) {
		$_POST['enable_subscriptions'] = false;
	}
	
	// Deal with avatars checkbox when it isn't checked
	if ( !isset( $_POST['avatars_show'] ) ) {
		$_POST['avatars_show'] = false;
	}
	
	foreach ( (array) $_POST as $option => $value ) {
		if ( !in_array( $option, array( '_wpnonce', '_wp_http_referer', 'action', 'submit' ) ) ) {
			$option = trim( $option );
			$value = is_array( $value ) ? $value : trim( $value );
			$value = stripslashes_deep( $value );
			if ( $value ) {
				bb_update_option( $option, $value );
			} else {
				bb_delete_option( $option );
			}
		}
	}
	
	$goback = add_query_arg('updated', 'true', wp_get_referer());
	bb_safe_redirect($goback);
	exit;
}

if ( !empty($_GET['updated']) ) {
	bb_admin_notice( __( '<strong>Settings saved.</strong>' ) );
}

$general_options = array(
	'enable_pingback' => array(
		'title' => __( 'Enable Pingbacks' ),
		'type' => 'checkbox',
		'options' => array(
			1 => __( 'Allow link notifications from other sites.' )
		)
	),

	'enable_loginless' => array(
		'title' => __( 'Enable Login-less Posting' ),
		'type' => 'checkbox',
		'options' => array(
			1 => __( 'Allow users to create topics and posts without logging in.' )
		),
	),
	
	'enable_subscriptions' => array(
		'title' => __( 'Enable Subscriptions' ),
		'type' => 'checkbox',
		'options' => array(
			1 => __( 'Allow users to subscribe to topics and receive new posts via email.' )
		),
	),
);

$bb_get_option_avatars_show = create_function( '$a', 'return 1;' );
add_filter( 'bb_get_option_avatars_show', $bb_get_option_avatars_show );
$avatar_options = array(
	'avatars_show' => array(
		'title' => __( 'Avatar display' ),
		'type' => 'radio',
		'options' => array(
			0 => __( 'Don&#8217;t show avatars' ),
			1 => __( 'Show avatars' )
		)
	),
	'avatars_rating' => array(
		'title' => __( 'Maximum rating' ),
		'type' => 'radio',
		'options' => array(
			'g' => __( 'G &#8212; Suitable for all audiences' ),
			'pg' => __( 'PG &#8212; Possibly offensive, usually for audiences 13 and above' ),
			'r' => __( 'R &#8212; Intended for adult audiences above 17' ),
			'x' => __( 'X &#8212; Even more mature than above' )
		)
	),
	'avatars_default' => array(
		'title' => __( 'Default avatar' ),
		'type' => 'radio',
		'options' => array(
			'default'   => bb_get_avatar( '',             32, 'default' )   . ' ' . __( 'Mystery Man' ),
			'blank'     => bb_get_avatar( '',             32, 'blank' )     . ' ' . __( 'Blank' ),
			'logo'      => bb_get_avatar( '',             32, 'logo' )      . ' ' . __( 'Gravatar Logo' ),
			'identicon' => bb_get_avatar( rand( 0, 999 ), 32, 'identicon' ) . ' ' . __( 'Identicon (Generated)' ),
			'wavatar'   => bb_get_avatar( rand( 0, 999 ), 32, 'wavatar' )   . ' ' . __( 'Wavatar (Generated)' ),
			'monsterid' => bb_get_avatar( rand( 0, 999 ), 32, 'monsterid' ) . ' ' . __( 'MonsterID  (Generated)' ),
			'retro'     => bb_get_avatar( rand( 0, 999 ), 32, 'retro' )     . ' ' . __( 'Retro  (Generated)' )
		),
		'note' => array(
			__( 'For users without a custom avatar of their own, you can either display a generic logo or a generated one based on their email address.' )
		),
	)
);
remove_filter( 'bb_get_option_avatars_show', $bb_get_option_avatars_show );

$bb_admin_body_class = ' bb-admin-settings';

bb_get_admin_header();

?>

<div class="wrap">

<h2><?php _e('Discussion Settings'); ?></h2>
<?php do_action( 'bb_admin_notices' ); ?>

<form class="settings" method="post" action="<?php bb_uri( 'bb-admin/options-discussion.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN ); ?>">
	<fieldset>
<?php
foreach ( $general_options as $option => $args ) {
	bb_option_form_element( $option, $args );
}
?>
	</fieldset>
	<fieldset>
		<legend><?php _e('Avatars'); ?></legend>
		<p>
			<?php _e('bbPress includes built-in support for <a href="http://gravatar.com/">Gravatars</a>. A Gravatar is an image that follows you from site to site, appearing beside your name when you comment on Gravatar enabled sites. Here you can enable the display of Gravatars on your site.'); ?>
		</p>
<?php
foreach ( $avatar_options as $option => $args ) {
	bb_option_form_element( $option, $args );
}
?>
	</fieldset>
	<fieldset class="submit">
		<?php bb_nonce_field( 'options-discussion-update' ); ?>
		<input type="hidden" name="action" value="update" />
		<input class="submit" type="submit" name="submit" value="<?php _e('Save Changes') ?>" />
	</fieldset>
</form>

</div>

<?php

bb_get_admin_footer();

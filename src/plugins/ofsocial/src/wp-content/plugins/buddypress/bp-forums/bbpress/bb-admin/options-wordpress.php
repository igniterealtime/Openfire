<?php

require_once('admin.php');

if ( 'post' == strtolower( $_SERVER['REQUEST_METHOD'] ) )
	$action = @$_POST['action'];
else
	$action = false;

if ( in_array( $action, array('update-users', 'update-options') ) ) {
	bb_check_admin_referer( 'options-wordpress-' . $action );
	
	// Deal with advanced user database checkbox when it isn't checked
	if (!isset($_POST['user_bbdb_advanced'])) {
		$_POST['user_bbdb_advanced'] = false;
	}
	
	foreach ( (array) $_POST as $option => $value ) {
		if ( !in_array( $option, array('_wpnonce', '_wp_http_referer', 'action', 'submit') ) ) {
			$option = trim( $option );
			$value = is_array( $value ) ? $value : trim( $value );
			$value = stripslashes_deep( $value );
			if ( ( $option == 'wp_siteurl' || $option == 'wp_home' ) && !empty( $value ) ) {
				$value = rtrim( $value, " \t\n\r\0\x0B/" ) . '/';
			}
			if ( $value ) {
				bb_update_option( $option, $value );
			} else {
				bb_delete_option( $option );
			}
		}
	}
	
	if ($action == 'update-users') {
		bb_apply_wp_role_map_to_orphans();
	}
	
	$goback = add_query_arg('updated', $action, wp_get_referer());
	bb_safe_redirect($goback);
	exit;
}

switch (@$_GET['updated']) {
	case 'update-users':
		bb_admin_notice( __( '<strong>User role mapping saved.</strong>' ) );
		break;
	case 'update-options':
		bb_admin_notice( __( '<strong>User integration settings saved.</strong>' ) );
		break;
}



$bb_role_names[''] = _c( 'none|no bbPress role' );
$bb_role_names = array_merge( $bb_role_names, array_map( create_function( '$a', 'return sprintf( _c( "bbPress %s|bbPress role" ), $a );' ), $wp_roles->get_names() ) );

$wpRoles = array(
	'administrator' => __('WordPress Administrator'),
	'editor'        => __('WordPress Editor'),
	'author'        => __('WordPress Author'),
	'contributor'   => __('WordPress Contributor'),
	'subscriber'    => __('WordPress Subscriber')
);

$wpRoles = apply_filters( 'role_map_wp_roles', $wpRoles );

$cookie_options = array(
	'wp_siteurl' => array(
		'title' => __( 'WordPress address (URL)' ),
		'class' => 'long',
		'note' => __( 'This value should exactly match the <strong>WordPress address (URL)</strong> setting in your WordPress general settings.' )
	),
	'wp_home' => array(
		'title' => __( 'Blog address (URL)' ),
		'class' => 'long',
		'note' => __( 'This value should exactly match the <strong>Blog address (URL)</strong> setting in your WordPress general settings.' )
	),
	'bb_auth_salt' => array(
		'title' => __( 'WordPress "auth" cookie salt' ),
		'note' => __( 'This must match the value of the WordPress setting named "auth_salt" in your WordPress site. Look for the option labeled "auth_salt" in <a href="#" id="getAuthSaltOption" onclick="window.open(this.href); return false;">this WordPress admin page</a>.' )
	),
	'bb_secure_auth_salt' => array(
		'title' => __( 'WordPress "secure auth" cookie salt' ),
		'note' => __( 'This must match the value of the WordPress setting named "secure_auth_salt" in your WordPress site. Look for the option labeled "secure_auth_salt" in <a href="#" id="getSecureAuthSaltOption" onclick="window.open(this.href); return false;">this WordPress admin page</a>. Sometimes this value is not set in WordPress, in that case you can leave this setting blank as well.' )
	),
	'bb_logged_in_salt' => array(
		'title' => __( 'WordPress "logged in" cookie salt' ),
		'note' => __( 'This must match the value of the WordPress setting named "logged_in_salt" in your WordPress site. Look for the option labeled "logged_in_salt" in <a href="#" id="getLoggedInSaltOption" onclick="window.open(this.href); return false;">this WordPress admin page</a>.' )
	)
);

foreach ( array( 'bb_auth_salt', 'bb_secure_auth_salt', 'bb_logged_in_salt' ) as $salt_constant ) {
	if ( defined( strtoupper( $salt_constant ) ) ) {
		$cookie_options[$salt_constant]['note'] = array(
			sprintf( __( 'You have defined the "%s" constant which locks this setting.' ), strtoupper( $salt_constant ) ),
			$cookie_options[$salt_constant]['note'],
		);
		$cookie_options[$salt_constant]['value'] = constant( strtoupper( $salt_constant ) );
		$bb_hardcoded[$salt_constant] = true;
	}
}

$user_db_options = array(
	'wp_table_prefix' => array(
		'title' => __( 'User database table prefix' ),
		'note'  => __( 'If your bbPress and WordPress sites share the same database, then this is the same value as <code>$table_prefix</code> in your WordPress <code>wp-config.php</code> file. It is usually <strong>wp_</strong>.' )
	),
	'wordpress_mu_primary_blog_id' => array(
		'title' => __( 'WordPress MU primary blog ID' ),
		'note'  => __( 'If you are integrating with a WordPress MU site you need to specify the primary blog ID for that site. It is usually <strong>1</strong>. You should probably leave this blank if you are integrating with a standard WordPress site' )
	),
	'user_bbdb_advanced' => array(
		'title' => __( 'Show advanced database settings' ),
		'type' => 'checkbox',
		'options' => array(
			1 => array(
				'label' => __( 'If your bbPress and WordPress site do not share the same database, then you will need to add advanced settings.' ),
				'attributes' => array( 'onclick' => 'toggleAdvanced(this);' )
			)
		)
	)
);

$advanced_user_db_options = array(
	'user_bbdb_name' => array(
		'title' => __( 'User database name' ),
		'note' => __( 'The name of the database in which your user tables reside.' )
	),
	'user_bbdb_user' => array(
		'title' => __( 'User database user' ),
		'note' => __( 'The database user that has access to that database.' )
	),
	'user_bbdb_password' => array(
		'title' => __( 'User database password' ),
		'note' => __( 'That database user\'s password.' )
	),
	'user_bbdb_host' => array(
		'title' => __( 'User database host' ),
		'note' => __( 'The domain name or IP address of the server where the database is located. If the database is on the same server as the web site, then this probably should be <strong>localhost</strong>.' )
	),
	'user_bbdb_charset' => array(
		'title' => __( 'User database character set' ),
		'note' => __( 'The best choice is <strong>utf8</strong>, but you will need to match the character set which you created the database with.' )
	),
	'user_bbdb_collate' => array(
		'title' => __( 'User database character collation' ),
		'note' => __( 'The character collation value set when the user database was created.' )
	)
);

$custom_table_options = array(
	'custom_user_table' => array(
		'title' => __( 'User database "user" table' ),
		'note' => __( 'The complete table name, including any prefix.' ),
	),
	'custom_user_meta_table' => array(
		'title' => __( 'User database "user meta" table' ),
		'note' => __( 'The complete table name, including any prefix.' ),
	),
);

$advanced_display = bb_get_option( 'user_bbdb_advanced' ) ? 'block' : 'none';

$bb_admin_body_class = ' bb-admin-settings';

bb_get_admin_header();

?>

<div class="wrap">

<h2><?php _e( 'WordPress Integration Settings' ); ?></h2>
<?php do_action( 'bb_admin_notices' ); ?>

<form class="settings" method="post" action="<?php bb_uri('bb-admin/options-wordpress.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN); ?>">
	<fieldset>
		<legend><?php _e('User Role Map'); ?></legend>
		<p><?php _e('Here you can match WordPress roles to bbPress roles.'); ?></p>
		<p><?php _e('This will have no effect until your user tables are integrated below. Only standard WordPress roles are supported. Changes do not affect users with existing roles in both WordPress and bbPress.'); ?></p>
<?php foreach ( $wpRoles as $wpRole => $wpRoleName ) bb_option_form_element( "wp_roles_map[$wpRole]", array( 'title' => $wpRoleName, 'type' => 'select', 'options' => $bb_role_names ) ); ?>
	</fieldset>
	<fieldset class="submit">
		<?php bb_nonce_field( 'options-wordpress-update-users' ); ?>
		<input type="hidden" name="action" value="update-users" />
		<input class="submit" type="submit" name="submit" value="<?php _e('Save Changes') ?>" />
	</fieldset>
</form>

<hr class="settings" />

<div class="settings">
	<h3><?php _e('User Integration'); ?></h3>
	<p><?php _e('Usually, you will have to specify both cookie integration and user database integration settings. Make sure you have a "User role map" setup above before trying to add user integration.'); ?></p>
	<p><?php _e('<em><strong>Note:</strong> changing the settings below may cause you to be logged out!</em>'); ?></p>
</div>

<form class="settings" method="post" action="<?php bb_uri('bb-admin/options-wordpress.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN); ?>">
	<fieldset>
		<legend><?php _e('Cookies'); ?></legend>
		<p><?php _e('Cookie sharing allows users to log in to either your bbPress or your WordPress site, and have access to both.'); ?></p>
	<?php foreach ( $cookie_options as $option => $args ) bb_option_form_element( $option, $args ); ?>
		<script type="text/javascript" charset="utf-8">
/* <![CDATA[ */
			function updateWordPressOptionURL () {
				var siteURLInputValue = document.getElementById('wp-siteurl').value;
				if (siteURLInputValue && siteURLInputValue.substr(-1,1) != '/') {
					siteURLInputValue += '/';
				}
				var authSaltAnchor = document.getElementById('getAuthSaltOption');
				var secureAuthSaltAnchor = document.getElementById('getSecureAuthSaltOption');
				var loggedInSaltAnchor = document.getElementById('getLoggedInSaltOption');
				if (siteURLInputValue) {
					authSaltAnchor.href = siteURLInputValue + 'wp-admin/options.php';
					secureAuthSaltAnchor.href = siteURLInputValue + 'wp-admin/options.php';
					loggedInSaltAnchor.href = siteURLInputValue + 'wp-admin/options.php';
				} else {
					authSaltAnchor.href = '';
					secureAuthSaltAnchor.href = '';
					loggedInSaltAnchor.href = '';
				}
			}
			var siteURLInput = document.getElementById('wp-siteurl');
			if (siteURLInput.value) {
				updateWordPressOptionURL();
			}
			siteURLInput.onkeyup = updateWordPressOptionURL;
			siteURLInput.onblur = updateWordPressOptionURL;
			siteURLInput.onclick = updateWordPressOptionURL;
			siteURLInput.onchange = updateWordPressOptionURL;
/* ]]> */
		</script>
<?php
$cookie_settings = array(
	'cookiedomain' => 'COOKIE_DOMAIN',
	'cookiepath' => 'COOKIEPATH'
);
$wp_settings = '';
foreach ($cookie_settings as $bb_setting => $wp_setting) {
	if ( isset($bb->$bb_setting) ) {
		$wp_settings .= 'define(\'' . $wp_setting . '\', \'' . $bb->$bb_setting . '\');' . "\n";
	}
}
?>
		<p><?php printf(__('To complete cookie integration, you will need to add some settings to your <code>wp-config.php</code> file in the root directory of your WordPress installation. To get those settings, you will need to install and configure the <a href="%s">"bbPress Integration" plugin for WordPress</a>.'), 'http://wordpress.org/extend/plugins/bbpress-integration/'); ?></p>
		<p><?php _e('You will also have to manually ensure that the following constants are equivalent in WordPress\' and bbPress\' respective config files.'); ?></p>
		<div class="table">
			<table>
				<tr>
					<th><?php _e('WordPress'); ?></th>
					<td></td>
					<th><?php _e('bbPress'); ?></th>
				</tr>
				<tr>
					<td>AUTH_KEY</td>
					<td>&lt;=&gt;</td>
					<td>BB_AUTH_KEY</td>
				</tr>
				<tr>
					<td>SECURE_AUTH_KEY</td>
					<td>&lt;=&gt;</td>
					<td>BB_SECURE_AUTH_KEY</td>
				</tr>
				<tr>
					<td>LOGGED_IN_KEY</td>
					<td>&lt;=&gt;</td>
					<td>BB_LOGGED_IN_KEY</td>
				</tr>
			</table>
		</div>
	</fieldset>

	<fieldset>
		<legend><?php _e('User database'); ?></legend>
		<p><?php _e('User database sharing allows you to store user data in your WordPress database.'); ?></p>
		<p><?php _e('You should setup a "User role map" before'); ?></p>
		<script type="text/javascript" charset="utf-8">
			function toggleAdvanced(checkedObj) {
				var advanced1 = document.getElementById('advanced1');
				var advanced2 = document.getElementById('advanced2');
				if (checkedObj.checked) {
					advanced1.style.display = 'block';
					advanced2.style.display = 'block';
				} else {
					advanced1.style.display = 'none';
					advanced2.style.display = 'none';
				}
			}
		</script>
	<?php foreach ( $user_db_options as $option => $args ) bb_option_form_element( $option, $args ); ?>
	</fieldset>
	<fieldset id="advanced1" style="display:<?php echo $advanced_display; ?>">
		<legend><?php _e('Separate user database settings'); ?></legend>
		<p><?php _e('Most of the time these settings are <em>not</em> required. Look before you leap!'); ?></p>
		<p><?php _e('All settings except for the character set must be specified.'); ?></p>
	<?php foreach ( $advanced_user_db_options as $option => $args ) bb_option_form_element( $option, $args ); ?>
	</fieldset>
	<fieldset id="advanced2" style="display:<?php echo $advanced_display; ?>">
		<legend><?php _e('Custom user tables'); ?></legend>
		<p><?php _e('Only set these values if your user tables differ from the default WordPress naming convention.'); ?></p>
	<?php foreach ( $custom_table_options as $option => $args ) bb_option_form_element( $option, $args ); ?>
	</fieldset>
	<fieldset class="submit">
		<?php bb_nonce_field( 'options-wordpress-update-options' ); ?>
		<input type="hidden" name="action" value="update-options" />
		<input class="submit" type="submit" name="submit" value="<?php _e('Save Changes') ?>" />
	</fieldset>
</form>

</div>

<?php
bb_get_admin_footer();
?>

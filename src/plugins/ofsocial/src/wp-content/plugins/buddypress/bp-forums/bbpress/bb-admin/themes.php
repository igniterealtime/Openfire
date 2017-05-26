<?php
require_once('admin.php');

$themes = bb_get_themes();

$activetheme = bb_get_option('bb_active_theme');
if (!$activetheme) {
	$activetheme = BB_DEFAULT_THEME;
}

if ( isset($_GET['theme']) ) {
	if ( !bb_current_user_can( 'manage_themes' ) ) {
		wp_redirect( bb_get_uri(null, null, BB_URI_CONTEXT_HEADER) );
		exit;
	}
	
	bb_check_admin_referer( 'switch-theme' );
	do_action( 'bb_deactivate_theme_' . $activetheme );
	
	$theme = stripslashes($_GET['theme']);
	$theme_data = bb_get_theme_data( $theme );
	if ($theme_data['Name']) {
		$name = $theme_data['Name'];
	} else {
		$name = preg_replace( '/^([a-z0-9_-]+#)/i', '', $theme);
	}
	if ($theme == BB_DEFAULT_THEME) {
		bb_delete_option( 'bb_active_theme' );
	} else {
		bb_update_option( 'bb_active_theme', $theme );
	}
	do_action( 'bb_activate_theme_' . $theme );
	wp_redirect( bb_get_uri('bb-admin/themes.php', array('activated' => 1, 'name' => urlencode( $name ) ), BB_URI_CONTEXT_HEADER + BB_URI_CONTEXT_BB_ADMIN ) );
	exit;
}

if ( isset($_GET['activated']) )
	$theme_notice = bb_admin_notice( sprintf( __( '<strong>Theme "%s" activated</strong>' ), esc_attr($_GET['name'])) );

if ( !in_array($activetheme, $themes) ) {
	if ($activetheme == BB_DEFAULT_THEME) {
		remove_action( 'bb_admin_notices', $theme_notice );
		bb_admin_notice( __( '<strong>Default theme is missing.</strong>' ), 'error' );
	} else {
		bb_delete_option( 'bb_active_theme' );
		remove_action( 'bb_admin_notices', $theme_notice );
		bb_admin_notice( __( '<strong>Theme not found.  Default theme applied.</strong>' ), 'error' );
	}
}

function bb_admin_theme_row( $theme, $position ) {
	$theme_directory = bb_get_theme_directory( $theme );
	$theme_data = file_exists( $theme_directory . 'style.css' ) ? bb_get_theme_data( $theme ) : false;
	$screen_shot = file_exists( $theme_directory . 'screenshot.png' ) ? esc_url( bb_get_theme_uri( $theme ) . 'screenshot.png' ) : false;
	$activation_url = bb_get_uri('bb-admin/themes.php', array('theme' => urlencode($theme)), BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN);
	$activation_url = esc_url( bb_nonce_url( $activation_url, 'switch-theme' ) );

	if ( 1 === $position || 0 === $position ) {
		echo '<tr>';
	}
?>
	<td class="position-<?php echo( (int) $position ); ?>">
		<div class="screen-shot"><?php if ( $screen_shot ) : ?><a href="<?php echo $activation_url; ?>" title="<?php echo esc_attr( sprintf( __( 'Activate "%s"' ), $theme_data['Title'] ) ); ?>"><img alt="<?php echo esc_attr( $theme_data['Title'] ); ?>" src="<?php echo $screen_shot; ?>" /></a><?php endif; ?></div>
		<div class="description">
			<h3 class="themes">
<?php
	printf(
		__( '%1$s %2$s by <cite>%3$s</cite>' ),
		$theme_data['Title'],
		$theme_data['Version'],
		$theme_data['Author']
	);
?>
			</h3>
			
<?php
	if ( $theme_data['Porter'] ) {
?>
			<p>
<?php
	printf(
		__( 'Ported by <cite>%s</cite>' ),
		$theme_data['Porter']
	);
?>
			</p>
<?php
	}
?>
			
			<?php echo $theme_data['Description']; // Description is autop'ed ?>
<?php
	if ( 0 !== $position ) {
?>
			<div class="actions">
				<a href="<?php echo $activation_url; ?>" title="<?php echo esc_attr( sprintf( __( 'Activate "%s"' ), $theme_data['Title'] ) ); ?>"><?php _e( 'Activate' ); ?></a>
			</div>
<?php
	}
?>
			<p class="location"><?php printf(__('All of this theme\'s files are located in the "%s" themes directory.'), $theme_data['Location']); ?></p>
		</div>
	</td>
<?php

	if ( 3 === $position || 0 === $position ) {
		echo '</tr>';
	}
}

if ( isset( $bb->safemode ) && $bb->safemode === true ) {
	bb_admin_notice( __( '<strong>"Safe mode" is on, the default theme will be used instead of the active theme indicated below.</strong>' ), 'error' );
}

$bb_admin_body_class = ' bb-admin-appearance';

bb_get_admin_header();
?>

<h2><?php _e('Manage Themes'); ?></h2>
<?php do_action( 'bb_admin_notices' ); ?>

<h3 class="themes"><?php _e('Current Theme'); ?></h3>
<div>
<table class="theme-list-active">
<?php bb_admin_theme_row( $themes[$activetheme], 0 ); unset($themes[$activetheme] ); ?>
</table>
</div>

<?php if ( !empty($themes) ) : ?>

<h3 class="themes"><?php _e('Available Themes'); ?></h3>
<div>
<table class="theme-list">
<?php
$i = 0;
foreach ( $themes as $theme ) {
	$position = 1 + ( $i % 3 );

	bb_admin_theme_row( $theme, $position );

	$i++;
}

switch ( $position ) {
	case 1:
		echo '<td class="position-2"></td><td class="position-3"></td></tr>';
		break;
	case 2:
		echo '<td class="position-3"></td></tr>';
		break;
	case 3:
		break;
}
?>
</table>
</div>

<?php endif; bb_get_admin_footer(); ?>

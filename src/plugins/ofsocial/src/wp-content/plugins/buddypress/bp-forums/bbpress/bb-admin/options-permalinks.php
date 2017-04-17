<?php

require_once('admin.php');

$file_source = BB_PATH . 'bb-admin/includes/defaults.bb-htaccess.php';
$file_target = BB_PATH . '.htaccess';
include( $file_source );
$file_source_rules = $_rules; // This is a string

if ( 'post' == strtolower( $_SERVER['REQUEST_METHOD'] ) && $_POST['action'] == 'update') {

	bb_check_admin_referer( 'options-permalinks-update' );

	foreach ( (array) $_POST as $option => $value ) {
		if ( !in_array( $option, array('_wpnonce', '_wp_http_referer', 'action', 'submit') ) ) {
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

	$mod_rewrite = (string) bb_get_option( 'mod_rewrite' );

	$goback = remove_query_arg( array( 'updated', 'notapache', 'notmodrewrite' ), wp_get_referer() );

	// Make sure mod_rewrite is possible on the server
	if ( !$is_apache ) {
		bb_delete_option( 'mod_rewrite_writable' );
		$goback = add_query_arg( 'notapache', 'true', $goback );
		bb_safe_redirect( $goback );
		exit;
	} elseif ( '0' !== $mod_rewrite && !apache_mod_loaded( 'mod_rewrite', true ) ) {
		bb_delete_option( 'mod_rewrite_writable' );
		bb_update_option( 'mod_rewrite', '0' );
		$goback = add_query_arg( 'notmodrewrite', 'true', $goback );
		bb_safe_redirect( $goback );
		exit;
	}

	$file_target_rules = array();

	$file_target_exists = false;
	$file_target_writable = true;
	if ( file_exists( $file_target ) ) {
		if ( is_readable( $file_target ) ) {
			$file_target_rules = explode( "\n", implode( '', file(  $file_target ) ) );
		}
		$file_target_exists = true;
		if ( !is_writable( $file_target ) ) {
			$file_target_writable = false;
		}
	} else {
		$file_target_dir = dirname( $file_target );
		if ( file_exists( $file_target_dir ) ) {
			if ( !is_writable( $file_target_dir ) || !is_dir( $file_target_dir ) ) {
				$file_target_writable = false;
			}
		} else {
			$file_target_writable = false;
		}
	}

	// Strip out existing bbPress rules
	$_keep_rule = true;
	$_kept_rules = array();
	foreach ( $file_target_rules as $_rule ) {
		if ( false !== strpos( $_rule, '# BEGIN bbPress' ) ) {
			$_keep_rule = false;
			continue;
		} elseif ( false !== strpos( $_rule, '# END bbPress' ) ) {
			$_keep_rule = true;
			continue;
		}
		if ( $_keep_rule ) {
			$_kept_rules[] = $_rule;
		}
	}

	$file_target_rules = join( "\n", $_kept_rules ) . "\n" . $file_source_rules;
	
	$file_target_written = 0;
	if ( $file_target_writable ) {
		// Open the file for writing - rewrites the whole file
		if ( $file_target_handle = fopen( $file_target, 'w' ) ) {
			if ( fwrite( $file_target_handle, $file_target_rules ) ) {
				$file_target_written = 1;
			}
			// Close the file
			fclose( $file_target_handle );
			@chmod( $file_target, 0666 );
		}
	}

	bb_update_option( 'mod_rewrite_writable', $file_target_writable );
	$goback = add_query_arg( 'updated', 'true', $goback );
	bb_safe_redirect( $goback );
	exit;
}

if ( $is_apache && bb_get_option( 'mod_rewrite' ) && !bb_get_option( 'mod_rewrite_writable' ) ) {
	$manual_instructions = true;
}

if ( !empty( $_GET['notmodrewrite'] ) ) {
	$manual_instructions = false;
	bb_admin_notice( __( '<strong>It appears that your server does not support custom permalink structures.</strong>' ), 'error' );
}

if ( !empty( $_GET['notapache'] ) ) {
	$manual_instructions = false;
	bb_admin_notice( __( '<strong>Rewriting on webservers other than Apache using mod_rewrite is currently unsupported, but we won&#8217;t stop you from trying.</strong>' ), 'error' );
}

if ( !empty( $_GET['updated'] ) ) {
	if ( $manual_instructions ) {
		bb_admin_notice( __( '<strong>You should update your .htaccess now.</strong>' ) );
	} else {
		bb_admin_notice( __( '<strong>Permalink structure updated.</strong>' ) );
	}
}

$permalink_options = array(
	'mod_rewrite' => array(
		'title' => __( 'Permalink type' ),
		'type' => 'radio',
		'options' => array(
			'0' => sprintf( __( '<span>None</span> <code>%s</code>' ), bb_get_uri( 'forum.php', array( 'id' => 1 ), BB_URI_CONTEXT_TEXT ) ),
			'1' => sprintf( __( '<span>Numeric</span> <code>%s</code>' ), bb_get_uri( 'forum/1', null, BB_URI_CONTEXT_TEXT ) ),
			'slugs' => sprintf( __( '<span>Name based</span> <code>%s</code>' ), bb_get_uri( '/forum/first-forum', null, BB_URI_CONTEXT_TEXT ) )
		)
	)
);

$bb_admin_body_class = ' bb-admin-settings';

bb_get_admin_header();

?>

<div class="wrap">

<h2><?php _e( 'Permalink Settings' ); ?></h2>
<?php do_action( 'bb_admin_notices' ); ?>

<form class="settings" method="post" action="<?php bb_uri( 'bb-admin/options-permalinks.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN ); ?>">
	<fieldset>
		<p>
			<?php _e( 'By default bbPress uses web URLs which have question marks and lots of numbers in them, however bbPress offers you the ability to choose an alternative URL structure for your permalinks. This can improve the aesthetics, usability, and forward-compatibility of your links.' ); ?>
		</p>
<?php
foreach ( $permalink_options as $option => $args ) { 
	bb_option_form_element( $option, $args );
}
?>
	</fieldset>
	<fieldset class="submit">
		<?php bb_nonce_field( 'options-permalinks-update' ); ?>
		<input type="hidden" name="action" value="update" />
		<input class="submit" type="submit" name="submit" value="<?php _e('Save Changes') ?>" />
	</fieldset>
</form>

<?php
if ( $manual_instructions ) {
?>
<form class="settings" method="post" action="<?php bb_uri( 'bb-admin/options-permalinks.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN ); ?>">
	<fieldset>
		<p>
			<?php _e( 'If your <code>.htaccess</code> file were <a href="http://codex.wordpress.org/Changing_File_Permissions">writable</a>, we could do this automatically, but it isn&#8217;t so these are the mod_rewrite rules you should have in your <code>.htaccess</code> file. Click in the field and press <kbd>CTRL + a</kbd> to select all.' ); ?>
		</p>
		<textarea dir="ltr" id="rewrite-rules" class="readonly" readonly="readonly" rows="6"><?php echo esc_html( trim( $file_source_rules ) ); ?></textarea>
	</fieldset>
</form>

<?php
}
?>

</div>

<?php

bb_get_admin_footer();

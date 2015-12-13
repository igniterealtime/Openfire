<?php
require_once('admin.php');

$edit_user   = bb_get_user( bb_get_user_id( $_GET['user_id'] ) );
$user_fields = bb_manage_user_fields( $edit_user );

// Let it rip!

// Header
$bb_admin_body_class = 'bb-admin-user-manage';
bb_get_admin_header();

?>

<div class="wrap">
	<h2><?php _e( 'Add a new user' ); ?></h2>

	<?php do_action( 'bb_admin_notices' ); ?>

	<form class="settings" method="post" action="">
		<fieldset>
			<?php
			foreach ( $user_fields as $field => $args ) {
				bb_option_form_element( $field, $args );
			}
			?>
			<noscript>
				<?php _e( 'Disabled (requires JavaScript)' ); ?>
			</noscript>
			<script type="text/javascript" charset="utf-8">
				if (typeof jQuery != 'undefined') {
					jQuery('#user-login').attr( 'id', 'user_login' );
					var meter = ('<div id="pass-strength-result">' + pwsL10n.short + '</div>');
					jQuery('#option-pass-strength-fake-input div.inputs input').before( meter );
				} else {
					document.writeln('<?php echo str_replace( "'", "\'", __( 'Disabled.' ) ); ?>')
				}
			</script>
		</fieldset>
		<fieldset class="submit">
			<?php bb_nonce_field( 'user-manage' ); ?>

			<input type="hidden" name="action" value="create" />
			<input class="submit" type="submit" name="submit" value="<?php _e( 'Create user' ); ?>" />
		</fieldset>
	</form>
</div>

<?php bb_get_admin_footer(); ?>
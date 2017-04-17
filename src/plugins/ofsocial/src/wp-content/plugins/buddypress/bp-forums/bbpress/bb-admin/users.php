<?php
require_once('admin.php');

// Get all errors and print as notice
if ( isset( $_GET['created'] ) )
	bb_admin_notice( __( '<strong>User Created.</strong>' ) );

if ( isset( $_GET['updated'] ) )
	bb_admin_notice( __( '<strong>User Updated.</strong>' ) );

if ( isset( $_GET['action'] ) || isset( $_GET['user_id'] ) && ( 'edit' == $_GET['action'] || 'create' == $_GET['action'] ) ) {

	$edit_user   = bb_get_user( bb_get_user_id( $_GET['user_id'] ) );
	$user_fields = bb_manage_user_fields( $edit_user );

	$bb_admin_body_class = 'bb-admin-user-manage';
	bb_get_admin_header(); ?>

	<div class="wrap">
		<h2><?php _e( 'Edit user' ); ?></h2>

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

				<input type="hidden" name="action" value="update" />
				<input class="submit" type="submit" name="submit" value="<?php _e( 'Update user' ); ?>" />
			</fieldset>
		</form>
	</div>

<?php } else {
	// Query the users
	$bb_user_search = new BB_User_Search( @$_GET['usersearch'], @$_GET['page'], @$_GET['userrole'] );

	$bb_admin_body_class = ' bb-admin-users';
	bb_get_admin_header(); ?>

	<div class="wrap">

		<?php $bb_user_search->display( true, bb_current_user_can( 'edit_users' ) ); ?>

	</div>

<?php } ?>

<?php bb_get_admin_footer(); ?>

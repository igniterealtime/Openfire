<?php
/**
 * BuddyPress Forums Deprecated Functions.
 *
 * This file contains all the deprecated functions for BuddyPress forums since
 * version 1.6. This was a major update for the forums component, moving from
 * bbPress 1.x to bbPress 2.x.
 *
 * @package BuddyPress
 * @subpackage Forums
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Outputs the markup for the bb-forums-admin panel
 */
function bp_forums_bbpress_admin() {

	// The text and URL of the Site Wide Forums button differs depending on whether bbPress
	// is running.
	if ( is_plugin_active( 'bbpress/bbpress.php' ) ) {
		// The bbPress admin page will always be on the root blog. switch_to_blog() will
		// pass through if we're already there.
		switch_to_blog( bp_get_root_blog_id() );
		$button_url = admin_url( add_query_arg( array( 'page' => 'bbpress' ), 'options-general.php' ) );
		restore_current_blog();

		$button_text = __( 'Configure bbPress', 'buddypress' );
	} else {
		$button_url = bp_get_admin_url( add_query_arg( array( 'tab' => 'plugin-information', 'plugin' => 'bbpress', 'TB_iframe' => 'true', 'width' => '640', 'height' => '500' ), 'plugin-install.php' ) );
		$button_text = __( 'Install bbPress', 'buddypress' );
	}

	$action = bp_get_admin_url( 'admin.php?page=bb-forums-setup&reinstall=1' ); ?>

	<div class="wrap">
		<h2 class="nav-tab-wrapper"><?php bp_core_admin_tabs( __( 'Forums', 'buddypress' ) ); ?></h2>

		<?php if ( isset( $_POST['submit'] ) ) : ?>

			<div id="message" class="updated fade">
				<p><?php _e( 'Settings Saved.', 'buddypress' ) ?></p>
			</div>

		<?php endif; ?>

		<?php

		if ( isset( $_REQUEST['reinstall'] ) || !bp_forums_is_installed_correctly() ) :

			// Delete the bb-config.php location option.
			bp_delete_option( 'bb-config-location' );

			// Now delete the bb-config.php file.
			@unlink( ABSPATH . 'bb-config.php' );

			// Show the updated wizard.
			bp_forums_bbpress_install_wizard();

		else : ?>

			<div style="width: 45%; float: left; margin-top: 20px;">
				<h3><?php _e( '(Installed)', 'buddypress' ); ?> <?php _e( 'Forums for Groups', 'buddypress' ) ?></h3>

				<p><?php _e( 'Give each individual group its own discussion forum. Choose this if you\'d like to keep your members\' conversations separated into distinct areas.' , 'buddypress' ); ?></p>

				<p><?php _e( 'Note: This component is retired and will not be receiving any updates in the future.  Only use this component if your current site relies on it.' , 'buddypress' ); ?></p>

				<h4 style="margin-bottom: 10px;"><?php _e( 'Features', 'buddypress' ); ?></h4>
				<ul class="description" style="list-style: square; margin-left: 30px;">
					<li><?php _e( 'Group Integration',           'buddypress' ); ?></p></li>
					<li><?php _e( 'Member Profile Integration',  'buddypress' ); ?></p></li>
					<li><?php _e( 'Activity Stream Integration', 'buddypress' ); ?></p></li>
					<li><?php _e( '@ Mention Integration',       'buddypress' ); ?></p></li>
				</ul>

				<div>
					<a class="button button-primary confirm" href="<?php echo $action ?>"><?php _e( 'Uninstall Group Forums', 'buddypress' ) ?></a> &nbsp;
				</div>
			</div>

			<div style="width: 45%; float: left; margin: 20px 0 20px 20px; padding: 0 20px 20px 20px; border: 1px solid #ddd; background-color: #fff;">
				<h3><?php _e( 'New! bbPress', 'buddypress' ) ?></h3>
				<p><?php _e( 'bbPress is a brand-new forum plugin from one of the lead developers of BuddyPress.', 'buddypress' ) ?></p>

				<p><?php _e( 'It boasts a bunch of cool features that the BP Legacy Discussion Forums does not have including:', 'buddypress' ) ?></p>

				<ul class="description" style="list-style: square; margin-left: 30px;">
					<li><?php _e( 'Non-group specific forum creation', 'buddypress' ); ?></p></li>
					<li><?php _e( 'Moderation via the WP admin dashboard', 'buddypress' ); ?></p></li>
					<li><?php _e( 'Topic splitting', 'buddypress' ); ?></p></li>
					<li><?php _e( 'Revisions', 'buddypress' ); ?></p></li>
					<li><?php _e( 'Spam management', 'buddypress' ); ?></p></li>
					<li><?php _e( 'Subscriptions', 'buddypress' ); ?></p></li>
					<li><?php _e( 'And more!', 'buddypress' ); ?></p></li>
				</ul>

				<p><?php printf( __( 'If you decide to use bbPress, you will need to deactivate the legacy group forum component.  For more info, <a href="%s">read this codex article</a>.', 'buddypress' ), 'https://codex.buddypress.org/legacy/getting-started/using-bbpress-2-2-with-buddypress/' ) ?></p>

				<div>
					<a class="button thickbox button-primary" href="<?php echo esc_url( $button_url ) ?>"><?php echo esc_html( $button_text ) ?></a> &nbsp;
				</div>
			</div>

		<?php endif; ?>

	</div>
<?php
}

function bp_forums_bbpress_install_wizard() {
	$post_url                 = bp_get_admin_url( 'admin.php?page=bb-forums-setup' );
	$bbpress_plugin_is_active = false;

	$step = isset( $_REQUEST['step'] ) ? $_REQUEST['step'] : '';

	// The text and URL of the Site Wide Forums button differs depending on whether bbPress
	// is running.
	if ( is_plugin_active( 'bbpress/bbpress.php' ) ) {
		$bbpress_plugin_is_active = true;

		// The bbPress admin page will always be on the root blog. switch_to_blog() will
		// pass through if we're already there.
		switch_to_blog( bp_get_root_blog_id() );
		$button_url = admin_url( add_query_arg( array( 'page' => 'bbpress' ), 'options-general.php' ) );
		restore_current_blog();

		$button_text = __( 'Configure bbPress', 'buddypress' );
	} else {
		$button_url = bp_get_admin_url( add_query_arg( array( 'tab' => 'plugin-information', 'plugin' => 'bbpress', 'TB_iframe' => 'true', 'width' => '640', 'height' => '500' ), 'plugin-install.php' ) );
		$button_text = __( 'Install bbPress', 'buddypress' );
	}

	switch( $step ) {
		case 'existing':
			if ( isset( $_REQUEST['doinstall'] ) && ( 1 == (int) $_REQUEST['doinstall'] ) ) {
				if ( !bp_forums_configure_existing_install() ) {
					_e( 'The bb-config.php file was not found at that location. Please try again.', 'buddypress' );
				} else {
					?>
					<h3><?php _e( 'Forums were set up correctly using your existing bbPress install!', 'buddypress' ) ?></h3>
					<p><?php _e( 'BuddyPress will now use its internal copy of bbPress to run the forums on your site. If you wish, you can remove your old bbPress installation files, as long as you keep the bb-config.php file in the same location.', 'buddypress' ) ?></p><?php
				}
			} else { ?>

					<form action="" method="post">
						<h3><?php _e( 'Existing bbPress Installation', 'buddypress' ) ?></h3>
						<p><?php _e( "BuddyPress can make use of your existing bbPress install. Just provide the location of your <code>bb-config.php</code> file, and BuddyPress will do the rest.", 'buddypress' ) ?></p>
						<p><label><code>bb-config.php</code> file location:</label><br /><input style="width: 50%" type="text" name="bbconfigloc" id="bbconfigloc" value="<?php echo str_replace( 'buddypress', '', $_SERVER['DOCUMENT_ROOT'] ) ?>" /></p>
						<p><input type="submit" class="button-primary" value="<?php esc_attr_e( 'Complete Installation', 'buddypress' ) ?>" /></p>
						<input type="hidden" name="step" value="existing" />
						<input type="hidden" name="doinstall" value="1" />
						<?php wp_nonce_field( 'bp_forums_existing_install_init' ) ?>
					</form>

				<?php
			}
		break;

		case 'new':
			if ( isset( $_REQUEST['doinstall'] ) && 1 == (int)$_REQUEST['doinstall'] ) {
				$result = bp_forums_bbpress_install();

				switch ( $result ) {
					case 1:
						echo '<p>';
						_e( 'All done! Configuration settings have been saved to the file <code>bb-config.php</code> in the root of your WordPress install.', 'buddypress' );
						echo '</p>';
						break;
					default:
						// Just write the contents to screen.
						echo '<p>';
						_e( 'A configuration file could not be created. No problem, but you will need to save the text shown below into a file named <code>bb-config.php</code> in the root directory of your WordPress installation before you can start using the forum functionality.', 'buddypress' );
						echo '</p>';
						?>
						<textarea style="display:block; margin-top: 30px; width: 80%;" rows="50"><?php echo esc_textarea( $result ); ?></textarea>
						<?php
						break;
				}
			} else { ?>

				<h3><?php _e( 'New bbPress Installation', 'buddypress' ) ?></h3>
				<p><?php _e( "You've decided to set up a new installation of bbPress for forum management in BuddyPress. This is very simple and is usually just a one click
				process. When you're ready, hit the link below.", 'buddypress' ) ?></p>
				<p><a class="button-primary" href="<?php echo esc_url( wp_nonce_url( $post_url . '&step=new&doinstall=1', 'bp_forums_new_install_init' ) ); ?>"><?php _e( 'Complete Installation', 'buddypress' ) ?></a></p>

				<?php
			}
		break;

		default:
			if ( !file_exists( buddypress()->plugin_dir . '/bp-forums/bbpress/' ) ) { ?>

				<div id="message" class="error">
					<p><?php printf( __( 'bbPress files were not found. To install the forums component you must download a copy of bbPress and make sure it is in the folder: "%s"', 'buddypress' ), 'wp-content/plugins/buddypress/bp-forums/bbpress/' ) ?></p>
				</div>

			<?php } else {

				// Include the plugin install.
				add_thickbox();
				wp_enqueue_script( 'plugin-install' );
				wp_admin_css( 'plugin-install' );
			?>

				<div style="width: 45%; float: left;  margin-top: 20px;">
					<h3><?php _e( 'Forums for Groups', 'buddypress' ) ?></h3>

					<p><?php _e( 'Give each individual group its own discussion forum. Choose this if you\'d like to keep your members\' conversations separated into distinct areas.' , 'buddypress' ); ?></p>

					<p><?php _e( 'Note: This component is retired and will not be receiving any updates in the future.  Only use this component if your current site relies on it.' , 'buddypress' ); ?></p>

					<h4 style="margin-bottom: 10px;"><?php _e( 'Features', 'buddypress' ); ?></h4>
					<ul class="description" style="list-style: square; margin-left: 30px;">
						<li><?php _e( 'Group Integration',           'buddypress' ); ?></p></li>
						<li><?php _e( 'Member Profile Integration',  'buddypress' ); ?></p></li>
						<li><?php _e( 'Activity Stream Integration', 'buddypress' ); ?></p></li>
						<li><?php _e( '@ Mention Integration',       'buddypress' ); ?></p></li>
					</ul>

					<div>
						<a class="button button-primary" href="<?php echo esc_url( $post_url ) . '&step=new' ?>"><?php _e( 'Install Group Forums', 'buddypress' ) ?></a> &nbsp;
						<a class="button" href="<?php echo esc_url( $post_url ) . '&step=existing' ?>"><?php _e( 'Use Existing Installation', 'buddypress' ) ?></a>
					</div>
				</div>

				<div style="width: 45%; float: left; margin: 20px 0 20px 20px; padding: 0 20px 20px 20px; border: 1px solid #ddd; background-color: #fff;">
					<h3><?php _e( 'New! bbPress', 'buddypress' ) ?></h3>
					<p><?php _e( 'bbPress is a brand-new forum plugin from one of the lead developers of BuddyPress.', 'buddypress' ) ?></p>

					<p><?php _e( 'It boasts a bunch of cool features that the BP Legacy Discussion Forums does not have including:', 'buddypress' ) ?></p>

					<ul class="description" style="list-style: square; margin-left: 30px;">
						<li><?php _e( 'Non-group specific forum creation', 'buddypress' ); ?></p></li>
						<li><?php _e( 'Moderation via the WP admin dashboard', 'buddypress' ); ?></p></li>
						<li><?php _e( 'Topic splitting', 'buddypress' ); ?></p></li>
						<li><?php _e( 'Revisions', 'buddypress' ); ?></p></li>
						<li><?php _e( 'Spam management', 'buddypress' ); ?></p></li>
						<li><?php _e( 'Subscriptions', 'buddypress' ); ?></p></li>
						<li><?php _e( 'And more!', 'buddypress' ); ?></p></li>
					</ul>

					<p><?php printf( __( 'If you decide to use bbPress, you will need to deactivate the legacy group forum component.  For more info, <a href="%s">read this codex article</a>.', 'buddypress' ), 'https://codex.buddypress.org/legacy/getting-started/using-bbpress-2-2-with-buddypress/' ) ?></p>
					<div>
						<a class="button button-primary <?php if ( ! $bbpress_plugin_is_active ) { echo esc_attr( 'thickbox' ); }?>" href="<?php echo esc_url( $button_url ) ?>"><?php echo esc_html( $button_text ) ?></a> &nbsp;
					</div>
				</div>

			<?php }
		break;
	}
}

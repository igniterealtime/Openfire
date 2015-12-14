<?php
// Remove these lines if you want to upgrade and are using safe mode
if ( ini_get('safe_mode') )
	die("You're running in safe mode which does not allow this upgrade
	script to set a running time limit.  Depending on the size of your
	database and on which parts of the script you are running, the script
	can take quite some time to run (or it could take just a few seconds).
	To throw caution to the wind and run the script in safe mode anyway,
	remove the first few lines of code in the <code>bb-admin/upgrade.php</code>
	file. Backups are always a good idea.");
// Stop removing lines

// Very old (pre 0.7) installs may need further upgrade utilities.
// Post to http://lists.bbpress.org/mailman/listinfo/bbdev if needed

require('../bb-load.php');
require( BB_PATH . 'bb-admin/includes/functions.bb-upgrade.php' );

$step = 'unrequired';

$forced = false;
if ( isset( $_POST['force'] ) && 1 == $_POST['force'] ) {
	$forced = true;
} elseif ( isset( $_GET['force'] ) && 1 == $_GET['force'] ) {
	$forced = true;
}

if ( bb_get_option( 'bb_db_version' ) > bb_get_option_from_db( 'bb_db_version' ) || $forced ) {
	
	$forced_input = '';
	if ( $forced ) {
		$forced_input = '<input type="hidden" name="force" value="1" />';
	}
	
	$step = 'required';
	
	if ( strtolower( $_SERVER['REQUEST_METHOD']) == 'post' ) {
		
		bb_check_admin_referer( 'bbpress-upgrader' );
		
		define('BB_UPGRADING', true);
		
		$bbdb->hide_errors();
		
		$messages = bb_upgrade_all();
		
		$bbdb->show_errors();
		
		$upgrade_log = array(__('Beginning upgrade&hellip;'));
		if (is_array($messages['messages'])) {
			$upgrade_log = array_merge($upgrade_log, $messages['messages']);
		}
		$upgrade_log[] = '>>> ' . __('Done');
		
		$error_log = array();
		if (is_array($messages['errors'])) {
			$error_log = $messages['errors'];
		}
		
		if ( bb_get_option( 'bb_db_version' ) === bb_get_option_from_db( 'bb_db_version' ) && !count($error_log) ) {
			$step = 'complete';
		} else {
			$step = 'error';
		}
		
		wp_cache_flush();
	}
	
}

bb_install_header( __('bbPress database upgrade'), false, true );
?>
		<script type="text/javascript" charset="utf-8">
			function toggleAdvanced(toggle, target) {
				var toggleObj = document.getElementById(toggle);
				var targetObj = document.getElementById(target);
				if (toggleObj.checked) {
					targetObj.style.display = 'block';
				} else {
					targetObj.style.display = 'none';
				}
			}
		</script>
<?php
switch ($step) {
	case 'unrequired':
?>
		<p class="last">
			<?php printf( __('Nothing to upgrade.  <a href="%s">Get back to work!</a>'), bb_get_uri('bb-admin/', null, BB_URI_CONTEXT_A_HREF + BB_URI_CONTEXT_BB_ADMIN) ); ?>
		</p>
<?php
		break;
	
	case 'required'
?>
		<div class="open">
			<h2><?php _e('Database upgrade required'); ?></h2>
			<div>
				<form action="<?php bb_uri('bb-admin/upgrade.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN); ?>" method="post">
					<p class="error">
						<?php _e('It looks like your database is out-of-date. You can upgrade it here.'); ?>
					</p>
					<fieldset class="buttons">
						<?php bb_nonce_field( 'bbpress-upgrader' ); ?>
						<?php echo $forced_input; ?>
						<label for="upgrade_next" class="forward">
							<input class="button" id="upgrade_next" type="submit" value="<?php _e( 'Upgrade database' ); ?>" />
						</label>
					</fieldset>
				</form>
			</div>
		</div>
<?php
		break;
	
	case 'complete':
?>
		<div class="open">
			<h2><?php _e('Database upgrade complete'); ?></h2>
			<div>
				<form action="<?php bb_uri('bb-admin/', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN); ?>" method="get">
					<p class="message">
						<?php _e('Your database has been successfully upgraded, enjoy!'); ?>
					</p>
					<fieldset>
						<label class="has-label for-toggle" for="upgrade_log_container_toggle">
							<span>
								<?php _e('Show upgrade messages'); ?>
								<input class="checkbox" type="checkbox" id="upgrade_log_container_toggle" value="1" onclick="toggleAdvanced('upgrade_log_container_toggle', 'upgrade_log_container');" />
							</span>
							<div class="clear"></div>
						</label>
					</fieldset>
					<div class="toggle" id="upgrade_log_container" style="display:none;">
						<fieldset>
							<label class="has-label for-textarea" for="upgrade_log">
								<span><?php _e('Upgrade log'); ?></span>
								<textarea id="upgrade_log" class="short"><?php echo(join("\n", $upgrade_log)); ?></textarea>
							</label>
						</fieldset>
					</div>
					<fieldset class="buttons">
						<label for="upgrade_next" class="back">
							<input class="button" id="upgrade_back" type="button" value="<?php _e( '&laquo; Go back to forums' ); ?>" onclick="location.href='<?php echo esc_js( bb_get_uri() ); ?>'; return false;" />
						</label>
						<label for="upgrade_next" class="forward">
							<input class="button" id="upgrade_next" type="submit" value="<?php _e( 'Go to admin' ); ?>" />
						</label>
					</fieldset>
				</form>
			</div>
		</div>
<?php
		break;
	
	case 'error':
?>
		<div class="open">
			<h2><?php _e('Database upgrade failed'); ?></h2>
			<div>
				<form action="<?php bb_uri('bb-admin/upgrade.php', null, BB_URI_CONTEXT_FORM_ACTION + BB_URI_CONTEXT_BB_ADMIN); ?>" method="post">
					<p class="error">
						<?php _e('The upgrade process seems to have failed. Check the upgrade messages below for more information.<br /><br />Attempting to go to the admin area without resolving the listed errors will return you to this upgrade page.'); ?>
					</p>
					<fieldset>
						<?php bb_nonce_field( 'bbpress-upgrader' ); ?>
						<?php echo $forced_input; ?>
						<label class="has-label for-toggle" for="upgrade_log_container_toggle" style="margin-bottom: 1.9em;">
							<span>
								<?php _e('Show upgrade messages'); ?>
								<input class="checkbox" type="checkbox" id="upgrade_log_container_toggle" value="1" onclick="toggleAdvanced('upgrade_log_container_toggle', 'upgrade_log_container');" />
							</span>
							<div class="clear"></div>
						</label>
					</fieldset>
					<div class="toggle" id="upgrade_log_container" style="display:none;">
						<fieldset>
<?php
		if (count($error_log)) {
?>
							<label class="has-label for-textarea" for="error_log">
								<span><?php _e('Error log'); ?></span>
								<textarea id="error_log" class="short"><?php echo(join("\n", $error_log)); ?></textarea>
							</label>
<?php
		}
?>
							<label class="has-label for-textarea" for="upgrade_log">
								<span><?php _e('Upgrade log'); ?></span>
								<textarea id="upgrade_log" class="short"><?php echo(join("\n", $upgrade_log)); ?></textarea>
							</label>
						</fieldset>
					</div>
					<fieldset class="buttons">
						<label for="upgrade_next" class="back">
							<input class="button" id="upgrade_back" type="button" value="<?php _e( '&laquo; Go back to forums' ); ?>" onclick="location.href='<?php echo esc_js( bb_get_uri() ); ?>'; return false;" />
						</label>
						<label for="upgrade_next" class="forward">
							<input class="button" id="upgrade_next" type="submit" value="<?php _e( 'Try again' ); ?>" />
						</label>
					</fieldset>
				</form>
			</div>
		</div>
<?php
		break;
}

bb_install_footer();
?>
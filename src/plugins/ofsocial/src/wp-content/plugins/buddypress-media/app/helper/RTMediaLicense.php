<?php

/**
 * Created by PhpStorm.
 * User: ritz
 * Date: 18/9/14
 * Time: 5:05 PM
 */
class RTMediaLicense {

	static $page;

	static function render_license( $page = '' ) {

		self::$page = $page;

		global $wp_actions;

		$tabs = apply_filters( 'rtmedia_license_tabs', array() );
		$addon_installed = false;
		if ( ! empty( $tabs ) && is_array( $tabs ) ) {
			$addon_installed = true;
			foreach ( $tabs as $key => $tab ) {
				$tabs[ $key ][ 'callback' ] = array( 'RTMediaLicense', 'render_license_section' );
			}
			?>
			<div id="rtm-licenses">
				<?php RTMediaAdmin::render_admin_ui( self::$page, $tabs ); ?>
			</div>
			<?php
		}

		// For add-on which aren't updated with the latest code
		if ( did_action( 'rtmedia_addon_license_details' ) ) {
			$addon_installed = true;
			?>
			<div id="rtm-licenses">
				<?php do_action( 'rtmedia_addon_license_details' ); ?>
			</div>
			<?php
		}
		if ( ! $addon_installed ) {
			?>
			<div class="rtm-license-404">You may be interested in <a href="<?php echo admin_url( 'admin.php?page=rtmedia-addons' ); ?>">rtMedia Addons</a>.</div>
			<?php
		}
	}

	static function render_license_section( $page = '', $args = '' ) {

		$license = ( isset( $args[ 'license_key' ] ) ) ? $args[ 'license_key' ] : false;
		$status = ( isset( $args[ 'status' ] ) ) ? $args[ 'status' ] : false;

		if ( $status !== false && $status == 'valid' ) {
			$status_class = 'activated rtm-success';
			$status_value = __( 'Activated', 'buddypress-media' );
		} else {
			$status_class = 'deactivated rtm-warning';
			$status_value = __( 'Deactivated', 'buddypress-media' );
		}

		$el_id = $args[ 'addon_id' ];
		$license_key_id = $args[ 'key_id' ];
		$license_status_id = $args[ 'status_id' ];
		?>
		<div class="rtm-addon-license">
			<div class="rtm-license-status-wrap <?php echo $status_class ?>">
				<span class="rtm-addon-license-status-label"><?php _e( 'Status: ', 'buddypress-media' ); ?></span>
				<span class="rtm-addon-license-status"><?php echo $status_value; ?></span>
			</div>

			<form method="post">
				<table class="form-table">
					<tbody>
						<tr>
							<th scope="row">
								<?php _e( 'License Key', 'buddypress-media' ); ?>
							</th>
							<td>
								<input id="<?php echo $license_key_id ?>" name="<?php echo $license_key_id ?>" type="text"
									   class="regular-text" value="<?php echo $license; ?>"/>
							</td>
						</tr>

						<?php if ( false !== $license ) { ?>
							<tr>
								<th scope="row">
									<?php _e( 'Activate / Deactivate License', 'buddypress-media' ); ?>
								</th>
								<td>
									<?php
									$nonce_action = 'edd_' . $el_id . '_nonce';
									$nonce_name = 'edd_' . $el_id . '_nonce';
									if ( $status !== false && $status == 'valid' ) {
										$btn_name = 'edd_' . $el_id . '_license_deactivate';
										$btn_val = __( 'Deactivate License', 'buddypress-media' );
									} else {
										$btn_name = 'edd_' . $el_id . '_license_activate';
										$btn_val = __( 'Activate License', 'buddypress-media' );
									}
									?>
									<?php wp_nonce_field( $nonce_action, $nonce_name ); ?>
									<input type="submit" class="button-secondary" name="<?php echo $btn_name; ?>" value="<?php echo $btn_val; ?>"/>
								</td>
							</tr>
						<?php } ?>
					</tbody>
				</table>
				<?php submit_button( 'Save Key' ); ?>
			</form>
		</div>
		<?php
	}

}

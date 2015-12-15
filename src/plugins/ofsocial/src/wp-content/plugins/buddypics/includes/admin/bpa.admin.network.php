<?php

/**
 * BP-ALBUM ADMIN CLASS
 * This file is used to add site administration menus to the WordPress network admin backend.
 *
 * @since 0.1.8.0
 * @package BP-Album
 * @subpackage Admin
 * @license GPL v2.0
 * @link http://code.google.com/p/buddypress-media/
 *
 * ========================================================================================================
 */

/**
 * bp_album_admin()
 *
 * Checks for form submission, saves component settings and outputs admin screen HTML.
 *
 * @since 0.1.8.0
 */
function bp_album_admin() {

	global $bp;

	// If the form has been submitted and the admin referrer checks out, save the settings
	if ( isset( $_POST['submit'] )  ) {

		check_admin_referer('bpa-settings');

		if( current_user_can('install_plugins') ) {

			update_site_option( 'bp_album_slug', $_POST['bp_album_slug'] );
			update_site_option( 'bp_album_max_pictures', $_POST['bp_album_max_pictures']=='' ? false : intval($_POST['bp_album_max_pictures']) );

			foreach(array(0,2,4,6) as $i){
				$option_name = "bp_album_max_priv{$i}_pictures";
				$option_value = $_POST[$option_name]=='' ? false : intval($_POST[$option_name]);
				update_site_option($option_name , $option_value);
			}

			update_site_option( 'bp_album_max_upload_size', $_POST['bp_album_max_upload_size'] );
			update_site_option( 'bp_album_keep_original', $_POST['bp_album_keep_original'] );
			update_site_option( 'bp_album_require_description', $_POST['bp_album_require_description'] );
			update_site_option( 'bp_album_enable_comments', $_POST['bp_album_enable_comments'] );
			update_site_option( 'bp_album_enable_wire', $_POST['bp_album_enable_wire'] );
			update_site_option( 'bp_album_middle_size', $_POST['bp_album_middle_size'] );
			update_site_option( 'bp_album_thumb_size', $_POST['bp_album_thumb_size'] );
			update_site_option( 'bp_album_per_page', $_POST['bp_album_per_page'] );
			update_site_option( 'bp_album_url_remap', $_POST['bp_album_url_remap'] );
			update_site_option( 'bp_album_base_url', $_POST['bp_album_base_url'] );

			$updated = true;

			if($_POST['bp_album_rebuild_activity'] && !$_POST['bp_album_undo_rebuild_activity']){
			    bp_album_rebuild_activity();
			}

			if( !$_POST['bp_album_rebuild_activity'] && $_POST['bp_album_undo_rebuild_activity']){
			    bp_album_undo_rebuild_activity();
			}
		}
		else {
			die("You do not have the required permissions to view this page");
		}

	}

        $bp_album_slug = get_site_option( 'bp_album_slug' );
        $bp_album_max_pictures = get_site_option( 'bp_album_max_pictures' );
        $bp_album_max_upload_size = get_site_option( 'bp_album_max_upload_size' );
        $bp_album_max_priv0_pictures = get_site_option( 'bp_album_max_priv0_pictures' );
        $bp_album_max_priv2_pictures = get_site_option( 'bp_album_max_priv2_pictures' );
        $bp_album_max_priv4_pictures = get_site_option( 'bp_album_max_priv4_pictures' );
        $bp_album_max_priv6_pictures = get_site_option( 'bp_album_max_priv6_pictures' );
        $bp_album_keep_original = get_site_option( 'bp_album_keep_original' );
        $bp_album_require_description = get_site_option( 'bp_album_require_description' );
        $bp_album_enable_comments = get_site_option( 'bp_album_enable_comments' );
        $bp_album_enable_wire = get_site_option( 'bp_album_enable_wire' );
        $bp_album_middle_size = get_site_option( 'bp_album_middle_size' );
        $bp_album_thumb_size = get_site_option( 'bp_album_thumb_size' );
        $bp_album_per_page = get_site_option( 'bp_album_per_page' );
	$bp_album_url_remap = get_site_option( 'bp_album_url_remap' );
	$bp_album_base_url = get_site_option( 'bp_album_base_url' );
	$bp_album_rebuild_activity = false;
	$bp_album_undo_rebuild_activity = false;



	?>

	<div class="wrap">

		<h2><?php _e('BP Album - ', 'bp-album' ) ?> <?php echo BP_ALBUM_DISPLAY_VERSION ?><?php _e(' - [Network Mode]', 'bp-album' ) ?></h2>
		<br />

		<?php if ( isset($updated) ) : ?><?php echo "<div id='message' class='updated fade'><p>" . __('Settings Updated.', 'bp-album' ) . "</p></div>" ?><?php endif; ?>

		<p>
		<?php
			echo "<a href='http://bit.ly/f5zPJ9'>";
			_e("Project Calendar", 'bp-album' );
			echo "</a> - ";
			_e("View our development schedule in real-time using Pivotal Tracker.", 'bp-album' )
		?>
		</p>

		<p>
		<?php
			echo "<a href='http://buddypress.org/community/groups/bp-album/forum/'>";
			_e("Support Forum", 'bp-album' );
			echo "</a> - ";
			_e("Need help with the plugin? There are <i>hundreds of other users</i> that can answer your questions on our BuddyPress support forum.", 'bp-album' )
		?>
		</p>

		<p>
		<?php
			echo "<a href='http://code.google.com/p/buddypress-media/'>";
			_e("Official Website", 'bp-album' );
			echo "</a> - ";
			_e("Our base of operations on the internet and the <i>only</i> source of info that's been checked by our dev team.", 'bp-album' )
		?>
		</p>

		<p>
		<?php
			echo "<a href='http://code.google.com/p/buddypress-media/wiki/BuddyPressMediaFeaturesList'>";
			_e("Features List", 'bp-album' );
			echo "</a> - ";
			_e("See what features we're adding to upcoming versions of the plugin.", 'bp-album' )
		?>
		</p>

		<p>
		<?php
			echo "<a href='http://code.google.com/p/buddypress-media/updates/list'>";
			_e("Activity Feed", 'bp-album' );
			echo "</a> - ";
			_e("Up-to-the-second bug reports and code updates via Google Code.", 'bp-album' )
		?>
		</p>


		<p>
		    <br>
		</p>

		<?php // The address in this line of code determines where the form will be sent to // ?>
		<form action="<?php echo site_url() . '/wp-admin/network/admin.php?page=bp-album-settings' ?>" name="example-settings-form" id="example-settings-form" method="post">

                    <h3><?php _e('Slug Name', 'bp-album' ) ?></h3>

		    <p>
		    <?php
			_e("Bad slug names will disable the plugin. No Spaces. No Punctuation. No Special Characters. No Accents.", 'bp-album' );
			echo " <br> ";
			_e("{ abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890_- } ONLY.", 'bp-album' )
		    ?>
		    </p>

			<table class="form-table">
				<tr valign="top">
					<th scope="row"><label for="target_uri"><?php _e('Name of BP Album slug', 'bp-album' ) ?></label></th>
					<td>
						<input name="bp_album_slug" type="text" id="bp_album_slug" value="<?php echo esc_attr($bp_album_slug ); ?>" size="10" />
					</td>
				</tr>

			</table>

                    <h3><?php _e('General', 'bp-album' ) ?></h3>

                                                         <table class="form-table">
                                <tr>
					<th scope="row"><?php _e('Force members to enter a description for each image', 'bp-album' ) ?></th>
					<td>
						<input type="radio" name="bp_album_require_description" type="text" id="bp_album_require_description"<?php if ($bp_album_require_description == true ) : ?> checked="checked"<?php endif; ?>  value="1" /> <?php _e( 'Yes', 'bp-album' ) ?> &nbsp;
						<input type="radio" name="bp_album_require_description" type="text" id="bp_album_require_description"<?php if ($bp_album_require_description == false) : ?> checked="checked"<?php endif; ?>  value="0" /> <?php _e( 'No', 'bp-album' ) ?>
					</td>
				</tr>
                                <tr>
					<th scope="row"><?php _e('Allow site members to post comments on album images', 'bp-album' ) ?></th>
					<td>
						<input type="radio" name="bp_album_enable_comments" type="text" id="bp_album_enable_comments"<?php if ($bp_album_enable_comments == true ) : ?> checked="checked"<?php endif; ?>  value="1" /> <?php _e( 'Yes', 'bp-album' ) ?> &nbsp;
						<input type="radio" name="bp_album_enable_comments" type="text" id="bp_album_enable_comments"<?php if ($bp_album_enable_comments == false) : ?> checked="checked"<?php endif; ?>  value="0" /> <?php _e( 'No', 'bp-album' ) ?>
					</td>
				</tr>
                                <tr>
					<th scope="row"><?php _e('Post image thumbnails to members activity stream', 'bp-album' ) ?></th>
					<td>
						<input type="radio" name="bp_album_enable_wire" type="text" id="bp_album_enable_wire"<?php if ($bp_album_enable_wire == true ) : ?> checked="checked"<?php endif; ?>  value="1" /> <?php _e( 'Yes', 'bp-album' ) ?> &nbsp;
						<input type="radio" name="bp_album_enable_wire" type="text" id="bp_album_enable_wire"<?php if ($bp_album_enable_wire == false) : ?> checked="checked"<?php endif; ?>  value="0" /> <?php _e( 'No', 'bp-album' ) ?>
					</td>
				</tr>

			</table>

                    <h3><?php _e( 'Album Size Limits', 'bp-album' ) ?></h3>

                    <p>
		    <?php _e( "<b>Accepted values:</b> EMPTY (no limit), NUMBER (value you set), 0 (disabled). The first option does not accept 0. The last option only accepts a number.", 'bp-album' ) ?>
		    </p>

			<table class="form-table">
				<tr valign="top">
					<th scope="row"><label for="target_uri"><?php _e('Max total images allowed in a members album', 'bp-album' ) ?></label></th>
					<td>
						<input name="bp_album_max_pictures" type="text" id="example-setting-one" value="<?php echo esc_attr( $bp_album_max_pictures ); ?>" size="10" />
					</td>
				</tr>
                                <tr>
					<th scope="row"><label for="target_uri"><?php _e('Max images visible to public allowed in a members album', 'bp-album' ) ?></label></th>
					<td>
						<input name="bp_album_max_priv0_pictures" type="text" id="bp_album_max_priv0_pictures" value="<?php echo esc_attr( $bp_album_max_priv0_pictures ); ?>" size="10" />
					</td>
				</tr>
                                <tr>
					<th scope="row"><label for="target_uri"><?php _e('Max images visible only to members in a members album', 'bp-album' ) ?></label></th>
					<td>
						<input name="bp_album_max_priv2_pictures" type="text" id="bp_album_max_priv2_pictures" value="<?php echo esc_attr( $bp_album_max_priv2_pictures ); ?>" size="10" />
					</td>
				</tr>
                                 <tr>
					<th scope="row"><label for="target_uri"><?php _e('Max images visible only to friends in a members album', 'bp-album' ) ?></label></th>
					<td>
						<input name="bp_album_max_priv4_pictures" type="text" id="bp_album_max_priv4_pictures" value="<?php echo esc_attr( $bp_album_max_priv4_pictures ); ?>" size="10" />
					</td>
				</tr>
                                <tr>
					<th scope="row"><label for="target_uri"><?php _e('Max private images in a members album', 'bp-album' ) ?></label></th>
					<td>
						<input name="bp_album_max_priv6_pictures" type="text" id="bp_album_max_priv6_pictures" value="<?php echo esc_attr( $bp_album_max_priv6_pictures ); ?>" size="10" />
					</td>
				</tr>
                                <tr>
					<th scope="row"><label for="target_uri"><?php _e('Images per album page', 'bp-album' ) ?></label></th>
					<td>
						<input name="bp_album_per_page" type="text" id="bp_album_per_page" value="<?php echo esc_attr( $bp_album_per_page ); ?>" size="10" />
					</td>
				</tr>
			</table>

			<h3><?php _e('Image Size Limits', 'bp-album' ) ?></h3>

			<p>
			<?php _e( "Uploaded images will be re-sized to the values you set here. Values are for both X and Y size in pixels. We <i>strongly</i> suggest you keep the original image files so BP-Media 0.1.9 can re-render your images during the upgrade process.", 'bp-album' ) ?>
			</p>

			<table class="form-table">
			    <tr valign="top">
					<th scope="row"><label for="target_uri"><?php _e('Maximum file (mb) size that can be uploaded', 'bp-album' ) ?></label></th>
					<td>
						<input name="bp_album_max_upload_size" type="text" id="bp_album_max_upload_size" value="<?php echo esc_attr( $bp_album_max_upload_size ); ?>" size="10" />
					</td>
				</tr>
	              <tr>
					<th scope="row"><label for="target_uri"><?php _e('Album Image Size', 'bp-album' ) ?></label></th>
					<td>
						<input name="bp_album_middle_size" type="text" id="bp_album_middle_size" value="<?php echo esc_attr( $bp_album_middle_size ); ?>" size="10" />
					</td>
				</tr>
                                <tr>
					<th scope="row"><label for="target_uri"><?php _e('Thumbnail Image Size', 'bp-album' ) ?></label></th>
					<td>
						<input name="bp_album_thumb_size" type="text" id="bp_album_thumb_size" value="<?php echo esc_attr( $bp_album_thumb_size ); ?>" size="10" />
					</td>
				</tr>
                                <tr>
					<th scope="row"><?php _e('Keep original image files', 'bp-album' ) ?></th>
					<td>
						<input type="radio" name="bp_album_keep_original" type="text" id="bp_album_keep_original"<?php if ( $bp_album_keep_original == true ) : ?> checked="checked"<?php endif; ?> id="bp-disable-account-deletion" value="1" /> <?php _e( 'Yes', 'bp-album' ) ?> &nbsp;
						<input type="radio" name="bp_album_keep_original" type="text" id="bp_album_keep_original"<?php if ($bp_album_keep_original == false) : ?> checked="checked"<?php endif; ?> id="bp-disable-account-deletion" value="0" /> <?php _e( 'No', 'bp-album' ) ?>
					</td>
				</tr>

			</table>

			<h3><?php _e('Image URL Mapping', 'bp-album' ) ?></h3>

			<p>
			<?php
			    _e( "If you get broken links when viewing images in BP-Album, it means your server is sending the wrong base URL to the plugin. You can use the image URL re-mapping function to fix this.",'bp-album' );
			    echo "<a href='http://code.google.com/p/buddypress-media/wiki/UsingTheURLRemapper'> ";
			    _e("DOCUMENTATION",'bp-album' );
			    echo "</a>";
			?>
			</p>

			<table class="form-table">
                                <tr>
					<th scope="row"><?php _e('Use image URL re-mapping', 'bp-album' ) ?></th>
					<td>
						<input type="radio" name="bp_album_url_remap" type="text" id="bp_album_url_remap"<?php if ($bp_album_url_remap == true ) : ?> checked="checked"<?php endif; ?>  value="1" /> <?php _e( 'Yes', 'bp-album' ) ?> &nbsp;
						<input type="radio" name="bp_album_url_remap" type="text" id="bp_album_url_remap"<?php if ($bp_album_url_remap == false) : ?> checked="checked"<?php endif; ?>  value="0" /> <?php _e( 'No', 'bp-album' ) ?>
					</td>
				</tr>
				<tr valign="top">
					<th scope="row"><label for="target_uri"><?php _e('Base URL', 'bp-album' ) ?></label></th>
					<td>
						<input name="bp_album_base_url" type="text" id="bp_album_base_url" value="<?php echo esc_attr( $bp_album_base_url ); ?>" size="70" />
						/userID/filename.xxx
					</td>
				</tr>

			</table>

			<h3><?php _e('Activity Stream Rebuild', 'bp-album' ) ?></h3>

			<p>
			<?php _e("A defect in plugin versions before 0.1.8.5 caused all bp-album activity posts to be deleted from the site when the administrator deleted a user. Set 'Rebuild Posts' to 'yes' to add <b>EVERY PHOTO ON YOUR SITE</b> that users have marked as <b>PUBLIC</b> to the site activity stream. This will also <b>ALLOW COMMENTS</b> on the photos. The created posts will have random dates to avoid flooding the activity stream. Set 'UNDO Rebuild Posts' to 'yes' to remove the posts <i>this function</i> creates <u>it will not harm posts that users created</u>.", 'bp-album' ) ?>
			</p>

			<table class="form-table">
                                <tr>
					<th scope="row"><?php _e('Rebuild posts', 'bp-album' ) ?></th>
					<td>
						<input type="radio" name="bp_album_rebuild_activity" type="text" id="bp_album_rebuild_activity"<?php if ($bp_album_rebuild_activity == true ) : ?> checked="checked"<?php endif; ?>  value="1" /> <?php _e( 'Yes', 'bp-album' ) ?> &nbsp;
						<input type="radio" name="bp_album_rebuild_activity" type="text" id="bp_album_rebuild_activity"<?php if ($bp_album_rebuild_activity == false) : ?> checked="checked"<?php endif; ?>  value="0" /> <?php _e( 'No', 'bp-album' ) ?>
					</td>
				</tr>
			</table>

			<table class="form-table">
                                <tr>
					<th scope="row"><?php _e('UNDO rebuild posts', 'bp-album' ) ?></th>
					<td>
						<input type="radio" name="bp_album_undo_rebuild_activity" type="text" id="bp_album_undo_rebuild_activity"<?php if ($bp_album_undo_rebuild_activity == true ) : ?> checked="checked"<?php endif; ?>  value="1" /> <?php _e( 'Yes', 'bp-album' ) ?> &nbsp;
						<input type="radio" name="bp_album_undo_rebuild_activity" type="text" id="bp_album_undo_rebuild_activity"<?php if ($bp_album_undo_rebuild_activity == false) : ?> checked="checked"<?php endif; ?>  value="0" /> <?php _e( 'No', 'bp-album' ) ?>
					</td>
				</tr>
			</table>

			<p class="submit">
				<input type="submit" name="submit" value="<?php _e( 'Save Settings', 'bp-album' ) ?>"/>
			</p>

			<?php
			// This is very important, don't leave it out.
			wp_nonce_field( 'bpa-settings' );
			?>
		</form>
	</div>
<?php
}

?>
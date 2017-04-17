<?php
global $rtmedia_query;

$model = new RTMediaModel();

$media = $model->get_media( array( 'id' => $rtmedia_query->media_query[ 'album_id' ] ), false, false );
global $rtmedia_media;
$rtmedia_media = $media[ 0 ];
?>
<div class="rtmedia-container rtmedia-single-container rtmedia-media-edit">
	<?php if ( rtmedia_is_global_album( $rtmedia_query->media_query[ 'album_id' ] ) ) { ?>
		<h2><?php echo __( 'Edit Album : ', 'buddypress-media' ) . esc_attr( $media[ 0 ]->media_title ); ?></h2>

		<div class="rtmedia-edit-media-tabs rtmedia-editor-main">
			<ul class="rtm-tabs clearfix">
				<li class="active"><a href="#details-tab"><i class='dashicons dashicons-edit rtmicon'></i><?php _e( 'Details', 'buddypress-media' ); ?></a></li>
				<?php if ( ! is_rtmedia_group_album() ) { ?>
					<li class=""><a href="#manage-media-tab"><i class='dashicons dashicons-list-view rtmicon'></i><?php _e( 'Manage Media', 'buddypress-media' ); ?></a></li>
				<?php } ?>
				<!-- use this hook to add title of a new tab-->
				<?php do_action( 'rtmedia_add_edit_tab_title', 'album' ); ?>
			</ul>

			<div class="rtm-tabs-content">
				<div class="content active" id="details-tab">
					<form method="post" class="rtm-form">
						<?php
						RTMediaMedia::media_nonce_generator( $rtmedia_query->media_query[ 'album_id' ] );
						?>

						<div class="rtmedia-edit-title rtm-field-wrap">
							<label for="media_title"><?php _e( 'Title : ', 'buddypress-media' ); ?></label>
							<?php rtmedia_title_input(); ?>
						</div>

						<div class="rtmedia-editor-description rtm-field-wrap">
							<label for='description'><?php _e( 'Description: ', 'buddypress-media' ) ?></label>
							<?php
							echo rtmedia_description_input( $editor = false );
							RTMediaMedia::media_nonce_generator( rtmedia_id() );
							?>
						</div>
						
						<?php do_action( "rtmedia_album_edit_fields", 'album-edit' ); ?>
						
						<div>
							<input type="submit" name="submit" class='rtmedia-save-album' value="<?php _e( 'Save Changes', 'buddypress-media' ); ?>" />
							<a class="button rtm-button rtm-button-back" href="<?php rtmedia_permalink(); ?>"><?php _e( 'Back', 'buddypress-media' ); ?></a>
						</div>
					</form>
				</div>

				<!--media management tab-->
				<?php if ( ! is_rtmedia_group_album() ) { ?>

					<div class="content" id="manage-media-tab">
						<?php if ( have_rtmedia() ) { ?>
							<form class="rtmedia-album-edit rtmedia-bulk-actions" method="post" name="rtmedia_album_edit">
								<?php wp_nonce_field( 'rtmedia_bulk_delete_nonce', 'rtmedia_bulk_delete_nonce' ); ?>
								<?php RTMediaMedia::media_nonce_generator( $rtmedia_query->media_query[ 'album_id' ] ); ?>
								<p>
									<span><input type="checkbox" name="rtm-select-all" class="select-all" title="<?php _e( 'Select All Visible', 'buddypress-media' ); ?>" /></span>
									<button class="button rtmedia-move" type='button' title='<?php echo __( 'Move Selected media to another album.', 'buddypress-media' ); ?>' ><?php _e( 'Move', 'buddypress-media' ); ?></button>
									<input type="hidden" name="move-selected" value="move">
									<button type="button" name="delete-selected" class="button rtmedia-delete-selected" title='<?php echo __( 'Delete Selected media from the album.', 'buddypress-media' ); ?>'><?php _e( 'Delete', 'buddypress-media' ); ?></button>
								</p>

								<p class="rtmedia-move-container">
									<?php $global_albums = rtmedia_get_site_option( 'rtmedia-global-albums' ); ?>
									<span><?php _e( 'Move selected media to the album : ', 'buddypress-media' ); ?></span>
									<?php echo '<select name="album" class="rtmedia-user-album-list">' . rtmedia_user_album_list() . '</select>'; ?>
									<input type="button" class="rtmedia-move-selected" name="move-selected" value="<?php _e( 'Move Selected', 'buddypress-media' ); ?>" />
								</p>

								<ul class="rtmedia-list  large-block-grid-4 ">

									<?php while ( have_rtmedia() ) : rtmedia(); ?>

										<?php include ('media-gallery-item.php'); ?>

									<?php endwhile; ?>

								</ul>


								<!-- these links will be handled by backbone -->
								<?php
								$display = '';
								if ( rtmedia_offset() != 0 )
									$display = 'style="display:block;"';
								else
									$display = 'style="display:none;"';
								?>
								<a id="rtMedia-galary-prev" <?php echo $display; ?> href="<?php echo rtmedia_pagination_prev_link(); ?>"><?php _e( 'Prev', 'buddypress-media' ); ?></a>

								<?php
								$display = '';
								if ( rtmedia_offset() + rtmedia_per_page_media() < rtmedia_count() )
									$display = 'style="display:block;"';
								else
									$display = 'style="display:none;"';
								?>
								<a id="rtMedia-galary-next" <?php echo $display; ?> href="<?php echo rtmedia_pagination_next_link(); ?>"><?php _e( 'Next', 'buddypress-media' ); ?></a>
							</form>
						<?php } else { ?>
							<p><?php _e( 'The album is empty.', 'buddypress-media' ); ?></p>
						<?php } ?>
					</div>
				<?php } ?>

				<!-- use this hook to add content of a new tab-->
				<?php do_action( 'rtmedia_add_edit_tab_content', 'album' ); ?>
			</div>
		</div>
	<?php } else { ?>
		<p><?php echo __( "Sorry !! You can not edit this album.", 'buddypress-media' ); ?></p>
	<?php } ?>
</div>
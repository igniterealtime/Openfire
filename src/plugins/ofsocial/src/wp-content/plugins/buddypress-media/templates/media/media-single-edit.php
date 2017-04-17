<div class="rtmedia-container rtmedia-single-container rtmedia-media-edit">
	<?php
	if ( have_rtmedia() ) : rtmedia();
		if ( rtmedia_edit_allowed() ) {
			global $rtmedia_media;
			?>
			<div class="rtmedia-single-edit-title-container">
				<h2 class="rtmedia-title"><?php echo __( 'Edit Media', 'buddypress-media' ); ?></h2>
			</div>
			<form method="post" action="" name="rtmedia_media_single_edit" id="rtmedia_media_single_edit" class="rtm-form">
				<div class="rtmedia-editor-main">
					<ul class="rtm-tabs clearfix">
						<li class="active">
							<a href="#panel1">
								<i class='dashicons dashicons-edit rtmicon'></i>
								<?php _e( 'Details', 'buddypress-media' ); ?>
							</a>
						</li>
						<!-- use this hook to add title of a new tab-->
						<?php do_action( 'rtmedia_add_edit_tab_title', rtmedia_type() ); ?>
					</ul>
					<div class="rtm-tabs-content">
						<div class="content" id="panel1">
							<!-- First tab i.e Details tab. Active by default-->
							<div class="rtmedia-edit-title rtm-field-wrap">
								<label><?php _e( 'Title : ', 'buddypress-media' ); ?></label>
								<?php rtmedia_title_input(); ?>
							</div>
							<!--This function shows the privacy dropdown-->
							<?php echo rtmedia_edit_media_privacy_ui(); ?>
							<div class="rtmedia-editor-description rtm-field-wrap">
								<label><?php _e( 'Description: ', 'buddypress-media' ) ?></label>
								<?php
								echo rtmedia_description_input( $editor = false );

								RTMediaMedia::media_nonce_generator( rtmedia_id() );
								?>
							</div>
							<!-- Use this hook to add new fields to the edit form-->
							<?php do_action( 'rtmedia_add_edit_fields', rtmedia_type() ); ?>
						</div>
						<!-- use this hook to add content of a new tab-->
						<?php do_action( 'rtmedia_add_edit_tab_content', rtmedia_type() ); ?>
					</div>
					<div class="rtmedia-editor-buttons">
						<input type="submit" class="button rtm-button rtm-button-save" value="<?php _e( 'Save', 'buddypress-media' ); ?>" />
						<a class="button rtm-button rtm-button-back" href="<?php rtmedia_permalink(); ?>"><?php _e( 'Back', 'buddypress-media' ); ?></a>
					</div>
				</div>
			</form>
			<?php
		} else {
			?>
			<p><?php echo __( "Sorry !! You do not have rights to edit this media", 'buddypress-media' ); ?></p>
			<?php
		}
	else:
		?>
		<p class="rtmedia-no-media-found">
			<?php
			$message = __( "Sorry !! There's no media found for the request !!", 'buddypress-media' );

			echo apply_filters( 'rtmedia_no_media_found_message_filter', $message );
			?>
		</p>
		<?php
	endif;
	?>
</div>

<div class="rtmedia-container">
	<?php
	do_action( 'rtmedia_before_album_gallery' );

	$title = get_rtmedia_gallery_title();
	?>

    <div id="rtm-gallery-title-container" class="clearfix">
        <h2 class="rtm-gallery-title">
			<?php
			if ( $title ) {
				echo $title;
			} else {
				_e( 'Album List', 'buddypress-media' );
			}
			?>
        </h2>

        <div id="rtm-media-options" class="rtm-media-options">
			<?php do_action( 'rtmedia_album_gallery_actions' ); ?>
        </div>
    </div>

	<?php do_action( 'rtmedia_after_album_gallery_title' ); ?>

    <div id="rtm-media-gallery-uploader" class="rtm-media-gallery-uploader">
		<?php rtmedia_uploader( array( 'is_up_shortcode' => false ) ); ?>
    </div>

	<?php if ( have_rtmedia() ) { ?>

		<ul class="rtmedia-list rtmedia-album-list clearfix">
			<?php while ( have_rtmedia() ) : rtmedia(); ?>
				<?php include ('album-gallery-item.php'); ?>
			<?php endwhile; ?>
		</ul>

		<div class="rtmedia_next_prev rtm-load-more clearfix">
			<!-- these links will be handled by backbone -->
			<?php
			global $rtmedia;

			$general_options = $rtmedia->options;

			if ( isset( $rtmedia->options[ 'general_display_media' ] ) && $general_options[ 'general_display_media' ] == 'pagination' ) {
				echo rtmedia_media_pagination();
			} else {
				$display = '';

				if ( rtmedia_offset() + rtmedia_per_page_media() < rtmedia_count() ) {
					$display = 'style="display:block;"';
				} else {
					$display = 'style="display:none;"';
				}
				?>
				<a id="rtMedia-galary-next" <?php echo $display; ?> href="<?php echo rtmedia_pagination_next_link(); ?>"><?php echo __( 'Load More', 'buddypress-media' ); ?></a>
				<?php
			}
			?>
		</div><!--/.rtmedia_next_prev-->
	<?php } else { ?>
		<p class="rtmedia-no-media-found">
			<?php
			$message = __( "Sorry !! There's no media found for the request !!", 'buddypress-media' );

			echo apply_filters( 'rtmedia_no_media_found_message_filter', $message );
			?>
		</p>
	<?php } ?>

	<?php do_action( 'rtmedia_after_album_gallery' ); ?>
</div>

<!-- template for single media in gallery -->
<script id="rtmedia-gallery-item-template" type="text/template">
    <div class="rtmedia-item-thumbnail">
    <a href ="media/<%= id %>">
    <img src="<%= guid %>">
    </a>
    </div>

    <div class="rtmedia-item-title">
    <h4 title="<%= media_title %>">
    <a href="media/<%= id %>">
    <%= media_title %>
    </a>
    </h4>
    </div>
</script>
<!-- rtmedia_actions remained in script tag -->
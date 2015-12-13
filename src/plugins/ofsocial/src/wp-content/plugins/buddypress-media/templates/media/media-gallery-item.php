<?php
/** That's all, stop editing from here * */
global $rtmedia_backbone;

$rtmedia_backbone = array(
	'backbone' => false,
	'is_album' => false,
	'is_edit_allowed' => false
);

if ( isset( $_POST[ 'backbone' ] ) ) {
	$rtmedia_backbone[ 'backbone' ] = $_POST[ 'backbone' ];
}

if ( isset( $_POST[ 'is_album' ] ) ) {
	$rtmedia_backbone[ 'is_album' ] = $_POST[ 'is_album' ][ 0 ];
}

if ( isset( $_POST[ 'is_edit_allowed' ] ) ) {
	$rtmedia_backbone[ 'is_edit_allowed' ] = $_POST[ 'is_edit_allowed' ][ 0 ];
}
?>

<li class="rtmedia-list-item" id="<?php echo rtmedia_id(); ?>">

	<?php do_action( 'rtmedia_before_item' ); ?>

    <a href ="<?php rtmedia_permalink(); ?>" title="<?php echo rtmedia_title(); ?>" class="<?php echo apply_filters( 'rtmedia_gallery_list_item_a_class', 'rtmedia-list-item-a' ); ?>">

		<div class="rtmedia-item-thumbnail">
			<?php echo rtmedia_duration(); ?>
			<img src="<?php rtmedia_image( "rt_media_thumbnail" ); ?>" alt="<?php rtmedia_image_alt(); ?>" >
		</div>

		<?php if ( apply_filters( 'rtmedia_media_gallery_show_media_title', true ) ) { ?>
			<div class="rtmedia-item-title">
				<h4 title="<?php echo rtmedia_title(); ?>">
					<?php echo rtmedia_title(); ?>
				</h4>
			</div>
		<?php } ?>

	</a>

	<?php do_action( 'rtmedia_after_item' ); ?>
</li>

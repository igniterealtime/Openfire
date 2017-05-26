<?php
/**
 * The template for displaying article headers
 *
 * @since 1.0.0
 */
$format = get_post_format();
$icon = array( 'audio' => 'fa-music', 'video' => 'fa-film', '0' => 'fa-file', 'gallery' => 'fa-camera-retro', 'image' => 'fa-picture-o', 'chat' => 'fa-bullhorn', 'link' => 'fa-link', 'quote' => 'fa-quote-left', 'aside' => 'fa-asterisk', 'status' => 'fa-plus-square' );
$class = ( 'quote' == $format || 'aside' == $format || 'status' == $format || 'link' == $format ) ? ' post-format-header' : '';
?>
	<header class="entry-header<?php echo $class; ?>">
		<h1 class="entry-title taggedlink">
			<?php if ( is_single() ) : ?>
				<i class="fa <?php echo esc_attr( $icon[$format] ); ?>"></i> <?php the_title(); ?>
			<?php else : ?>
				<a href="<?php the_permalink(); ?>" title="<?php echo esc_attr( the_title_attribute( 'echo=0' ) ); ?>" rel="bookmark"><i class="fa <?php echo esc_attr( $icon[$format] ); ?>"></i> <?php the_title(); ?></a>
			<?php endif; // is_single() ?>
		</h1>
	</header>
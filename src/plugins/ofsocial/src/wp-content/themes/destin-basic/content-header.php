<?php
/**
 * The template for displaying article headers
 *
 * @since 1.0.0
 */
$format = get_post_format();
$icon = array( 'audio' => 'fa-music', 'video' => 'fa-film', '0' => 'fa-file', 'gallery' => 'fa-camera-retro', 'image' => 'fa-picture-o', 'chat' => 'fa-bullhorn' );
?>

	<h1 class="entry-title taggedlink">
		<i class="fa <?php echo $icon[$format]; ?>"></i>
		<?php if ( is_single() ) : ?>
			<?php the_title(); ?>
		<?php else : ?>
			<a href="<?php the_permalink(); ?>" title="<?php echo esc_attr( the_title_attribute( 'echo=0' ) ); ?>" rel="bookmark"><?php the_title(); ?></a>
		<?php endif; // is_single() ?>
	</h1>
	<div class="entry-meta top-entry-meta">
		<?php
		echo '<i class="fa fa-bookmark"></i>Posted in ';
	    the_category( ', ' );

		if ( comments_open() )
			echo '&nbsp;&nbsp;&nbsp;&nbsp;<i class="fa fa-comments"></i> ';

		comments_popup_link( __( '0 Comments', 'destin' ), __( '1 Comment', 'destin' ), __( '% Comments', 'destin' ), '', '' );
		?>
	</div>
	<div class="entry-meta">
		<?php
		printf( __( 'by %s on %s', 'destin' ),
			'<span class="vcard author"><span class="fn"><a href="' . get_author_posts_url( get_the_author_meta( 'ID' ) ) . '" title="' . esc_attr( sprintf( __( 'Posts by %s', 'destin' ), get_the_author() ) ) . '" rel="author">' . get_the_author() . '</a></span></span>', '<a href="' . get_permalink() . '" class="time"><time class="date published updated" datetime="' . esc_attr( get_the_date( 'Y-m-d' ) ) . '">' . get_the_date() . '</time></a>'
			);
		?>
	</div>
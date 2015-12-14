<?php
/**
 * The template for displaying posts in the Status post format
 *
 * @since 1.0.0
 */
?>
	<article id="post-<?php the_ID(); ?>" <?php post_class(); ?>>
	    <?php get_template_part( 'content', 'header-pf' ); ?>

		<?php echo get_avatar( get_the_author_meta( 'ID' ), 60 ); ?>
        <h1 class="author"><span class="vcard author"><span class="fn"><?php the_author(); ?></span></span></h1>

		<div class="entry-content description">
			<time class="date published updated" datetime="<?php echo esc_attr( get_the_date( 'Y-m-d' ) ) . 'T' . esc_attr( get_the_time( 'H:i' ) ) . 'Z'; ?>">
				<?php printf( __( 'Posted on %1$s at %2$s', 'destin' ), get_the_date(), get_the_time() );	?>
			</time>

			<?php the_content( __( 'Continue reading <span class="meta-nav">&rarr;</span>', 'destin') ); ?>
	    </div><!-- .entry-content -->

	    <?php get_template_part( 'content', 'footer' ); ?>
    </article>
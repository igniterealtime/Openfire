<?php
/**
 * The template for displaying posts in the Image post format
 *
 * @since 1.0.0
 */
?>
	<article id="post-<?php the_ID(); ?>" <?php post_class(); ?>>
	    <?php get_template_part( 'content', 'header-pf' ); ?>

	    <div class="entry-content description clearfix">
	        <?php
			if( has_post_thumbnail() && ! is_single() )
				the_post_thumbnail( 'large', array( 'class' => 'alignnone' ) );
			else
				the_content( __( 'Continue reading <span class="meta-nav">&rarr;</span>', 'destin') );
			?>
	    </div><!-- .entry-content -->

	    <?php get_template_part( 'content', 'footer' ); ?>
	</article>
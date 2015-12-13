<?php
/**
 * The Template for displaying all single posts.
 *
 * @since 1.0.0
 */
get_header(); ?>

<div class="container">
	<div class="row">
		<div id="primary" <?php bavotasan_primary_attr(); ?>>
			<?php while ( have_posts() ) : the_post(); ?>
	
				<?php get_template_part( 'content', get_post_format() ); ?>
	
				<div id="posts-pagination" class="clearfix">
					<h3 class="sr-only"><?php _e( 'Post navigation', 'destin' ); ?></h3>
					<div class="previous pull-left"><?php previous_post_link( '%link', __( '<span class="meta-nav">&larr;</span> %title', 'destin' ) ); ?></div>
					<div class="next pull-right"><?php next_post_link( '%link', __( '%title <span class="meta-nav">&rarr;</span>', 'destin' ) ); ?></div>
				</div><!-- #posts-pagination -->
	
				<?php comments_template( '', true ); ?>
	
			<?php endwhile; // end of the loop. ?>
		</div>
	
		<?php get_sidebar(); ?>
	</div>
</div>

<?php get_footer(); ?>
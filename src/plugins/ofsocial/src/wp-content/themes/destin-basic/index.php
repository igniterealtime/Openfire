<?php
/**
 * The main template file.
 *
 * This is the most generic template file in a WordPress theme
 * and one of the two required files for a theme (the other being style.css).
 * It is used to display a page when nothing more specific matches a query.
 * For example, it puts together the home page when no home.php file exists.
 *
 * Learn more: http://codex.wordpress.org/Template_Hierarchy
 *
 * @since 1.0.0
 */
get_header(); ?>

<div class="container">
	<div class="row">
		<div id="primary" <?php bavotasan_primary_attr(); ?>>
			<?php
			if ( have_posts() ) :
				while ( have_posts() ) : the_post();
					get_template_part( 'content', get_post_format() );
				endwhile;
	
				bavotasan_pagination();
			else :
				get_template_part( 'content', 'none' );
			endif;
			?>
		</div>
	
		<?php get_sidebar(); ?>
	</div>
</div>

<?php get_footer(); ?>
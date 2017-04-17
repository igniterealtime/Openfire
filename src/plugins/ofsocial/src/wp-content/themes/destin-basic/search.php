<?php
/**
 * The template for displaying Search Results pages.
 *
 * @since 1.0.0
 */
get_header(); ?>

<div class="container">
	<div class="row"> 
	    <section id="primary" <?php bavotasan_primary_attr(); ?>>
			<?php if ( have_posts() ) : ?>
	
				<header id="archive-header">
					<h1 class="page-title"><?php bavotasan_search_title(); ?></h1>
				</header>
				<?php
				while ( have_posts() ) : the_post();
					get_template_part( 'content', get_post_format() );
				endwhile;
	
				bavotasan_pagination();
			else :
				get_template_part( 'content', 'none' );
			endif;
			?>
		</section><!-- #primary.c8 -->
	
		<?php get_sidebar(); ?>
	</div>
</div>

<?php get_footer(); ?>
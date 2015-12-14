<?php

/**
 * BuddyMobile - Archive Page
 *
 * This template displays a list of posts.
 *
 * @package buddymobile
 *
 */

?>

<?php get_header(); ?>

	<div id="content">

		<div id="blog-page" class="page">

			<div class="page-header">
			<h4><?php printf( __( 'You are browsing the archive for %1$s.', 'buddymobile' ), wp_title( false, false ) ); ?></h4>
			</div>

		<?php if (have_posts()) : while (have_posts()) : the_post(); ?>

			<div class="post" id="post-<?php the_ID(); ?>">
				<div class="entry-header">
				<h2 class="pagetitle"><a href="<?php the_permalink() ?>" rel="bookmark" title="<?php _e( 'Permanent Link to', 'buddymobile' ) ?> <?php the_title_attribute(); ?>"><?php the_title(); ?></a></h2>
				</div>

					<div class="entry">

						<?php the_excerpt() ; ?>

						<?php wp_link_pages( array( 'before' => __( '<p><strong>Pages:</strong> ', 'buddymobile' ), 'after' => '</p>', 'next_or_number' => 'number')); ?>

					</div>
					<div class="entry-meta">
						<div class="post-tags"><?php the_tags(); ?></div>
					</div>

			</div>

		<?php endwhile;  ?>

				<div class="navigation">

					<div class="alignleft"><?php next_posts_link( __( '&larr; Previous Entries', 'buddymobile' ) ) ?></div>
					<div class="alignright"><?php previous_posts_link( __( 'Next Entries &rarr;', 'buddymobile' ) ) ?></div>

				</div>

			<?php else : ?>

				<h2 class="center"><?php _e( 'Not Found', 'buddymobile' ) ?></h2>
				<?php locate_template( array( 'searchform.php' ), true ) ?>

			<?php endif; ?>

		</div>

	</div><!-- #content -->

<?php get_footer(); ?>

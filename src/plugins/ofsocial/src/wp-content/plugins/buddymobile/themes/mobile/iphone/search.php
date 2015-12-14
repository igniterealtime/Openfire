<?php

/**
 * BuddyMobile - search post Page
 *
 * This template displays a search post form.
 *
 * @package BuddyMobile
 *
 */

?>

<?php get_header() ?>

	<div id="content">

		<div id="blog-search" class="page">

			<h2 class="pagetitle"><?php _e( 'Blog', 'buddymobile' ) ?></h2>

			<?php if (have_posts()) : ?>

				<h3 class="pagetitle"><?php _e( 'Search Results', 'buddymobile' ) ?></h3>

				<div class="navigation">
					<div class="alignleft"><?php next_posts_link( __( '&larr; Previous Entries', 'buddymobile' ) ) ?></div>
					<div class="alignright"><?php previous_posts_link( __( 'Next Entries &rarr;', 'buddymobile' ) ) ?></div>
				</div>

				<?php while (have_posts()) : the_post(); ?>

					<div class="post" id="post-<?php the_ID(); ?>">

						<div class="author-box">
							<?php echo get_avatar( get_the_author_email(), '50' ); ?>
							<p><?php printf( __( 'by %s', 'buddymobile' ), bp_core_get_userlink( $post->post_author ) ) ?></p>
						</div>

						<div class="post-content">
							<h2 class="posttitle"><a href="<?php the_permalink() ?>" rel="bookmark" title="<?php _e( 'Permanent Link to', 'buddymobile' ) ?> <?php the_title_attribute(); ?>"><?php the_title(); ?></a></h2>

							<p class="date"><?php the_time() ?> <em><?php _e( 'in', 'buddymobile' ) ?> <?php the_category(', ') ?> <?php printf( __( 'by %s', 'buddymobile' ), bp_core_get_userlink( $post->post_author ) ) ?></em></p>

							<div class="entry">
								<?php the_content( __( 'Read the rest of this entry &rarr;', 'buddymobile' ) ); ?>
							</div>

							<p class="postmetadata"><span class="tags"><?php the_tags( __( 'Tags: ', 'buddymobile' ), ', ', '<br />'); ?></span> <span class="comments"><?php comments_popup_link( __( 'No Comments &#187;', 'buddymobile' ), __( '1 Comment &#187;', 'buddymobile' ), __( '% Comments &#187;', 'buddymobile' ) ); ?></span></p>
						</div>

					</div>

				<?php endwhile; ?>

				<div class="navigation">
					<div class="alignleft"><?php next_posts_link( __( '&larr; Previous Entries', 'buddymobile' ) ) ?></div>
					<div class="alignright"><?php previous_posts_link( __( 'Next Entries &rarr;', 'buddymobile' ) ) ?></div>
				</div>

			<?php else : ?>

				<h2 class="center"><?php _e( 'No posts found. Try a different search?', 'buddymobile' ) ?></h2>
				<?php locate_template( array( '/searchform.php'), true ) ?>

			<?php endif; ?>

		</div>

	</div><!-- #content -->

<?php get_footer() ?>

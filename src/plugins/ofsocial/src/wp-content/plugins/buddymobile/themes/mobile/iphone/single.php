<?php get_header() ?>

	<div id="content">

		<div id="blog-single" class="page">

			<?php if (have_posts()) : while (have_posts()) : the_post(); ?>

				<div class="item-options">

					<div class="alignleft"><?php next_posts_link( __( '&larr; Previous Entries', 'buddymobile' ) ) ?></div>
					<div class="alignright"><?php previous_posts_link( __( 'Next Entries &rarr;', 'buddymobile' ) ) ?></div>

				</div>

				<div id="post-<?php the_ID(); ?>" class="post">
					<div class="entry">

						<h2 class="posttitle"><a href="<?php the_permalink() ?>" rel="bookmark" title="<?php _e( 'Permanent Link to', 'buddymobile' ) ?> <?php the_title_attribute(); ?>"><?php the_title(); ?></a></h2>

						<p class="date"><?php the_time() ?> <?php _e( 'in', 'buddymobile' ) ?> <?php the_category(', ') ?> <?php printf( __( 'by %s', 'buddymobile' ), bp_core_get_userlink( $post->post_author ) ) ?></p>


							<?php the_content( __( 'Read the rest of this entry &rarr;', 'buddymobile' ) ); ?>

							<?php wp_link_pages(array('before' => __( '<p><strong>Pages:</strong> ', 'buddymobile' ), 'after' => '</p>', 'next_or_number' => 'number')); ?>


						<p class="postmetadata">
						<div class="tags"><?php the_tags( __( 'Tags: ', 'buddymobile' ), ', ', '<br />'); ?></div>
						</p>
					</div>

				</div>

			<?php comments_template(); ?>

			<?php endwhile; else: ?>

				<p><?php _e( 'Sorry, no posts matched your criteria.', 'buddymobile' ) ?></p>

			<?php endif; ?>

		</div>


	</div><!-- #content -->

<?php get_footer() ?>
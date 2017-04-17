<?php get_header() ?>

	<div id="content">

		<div id="blog-latest" class="page">

			<?php if ( have_posts() ) : ?>

				<?php while (have_posts()) : the_post(); ?>

					<div class="post pageitem" id="post-<?php the_ID(); ?>">

						<div class="post-content">
							<h2 class="posttitle"><a href="<?php the_permalink() ?>" rel="bookmark" title="<?php _e( 'Permanent Link to', 'buddymobile' ) ?> <?php the_title_attribute(); ?>"><?php the_title(); ?></a></h2>

							<p class="date"><?php the_time() ?> <?php _e( 'in', 'buddymobile' ) ?> <?php the_category(', ') ?> <?php printf( __( 'by %s', 'buddymobile' ), bp_core_get_userlink( $post->post_author ) ) ?></p>
						<?php if(has_post_thumbnail()) :?>
							<div class="post-thumb">
								<?php the_post_thumbnail('mobile-thumb'); ?>
							</div>
						<?php endif;?>

							<div class="entry">
								<?php the_excerpt(); ?>
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

				<h2 class="center"><?php _e( 'Not Found', 'buddymobile' ) ?></h2>
				<p class="center"><?php _e( 'Sorry, but you are looking for something that isn\'t here.', 'buddymobile' ) ?></p>

				<?php locate_template( array( 'searchform.php' ), true ) ?>

			<?php endif; ?>
		</div>

	</div><!-- #content -->


<?php get_footer() ?>

<?php get_header() ?>

	<div id="content">
		<div id="blog-latest" class="blog">

	<?php $temp = $wp_query; $wp_query= null;
		$wp_query = new WP_Query(); $wp_query->query('showposts=10' . '&paged='.$paged);
		while ($wp_query->have_posts()) : $wp_query->the_post(); ?>

					<div class="post pageitem" id="post-<?php the_ID(); ?>">

						<div class="post-content">
							<h2 class="posttitle"><a href="<?php the_permalink() ?>" rel="bookmark" title="<?php _e( 'Permanent Link to', 'buddymobile' ) ?> <?php the_title_attribute(); ?>"><?php the_title(); ?></a></h2>

							<p class="date"><?php the_time() ?> <em><?php _e( 'in', 'buddymobile' ) ?> <?php the_category(', ') ?> <?php printf( __( 'by %s', 'buddymobile' ), bp_core_get_userlink( $post->post_author ) ) ?></em></p>
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

					<?php if ($paged > 1) { ?>

						<nav id="nav-posts">
							<div class="prev"><?php next_posts_link('&laquo; Previous Posts'); ?></div>
							<div class="next"><?php previous_posts_link('Newer Posts &raquo;'); ?></div>
						</nav>

						<?php } else { ?>

						<nav id="nav-posts">
							<div class="prev"><?php next_posts_link('&laquo; Previous Posts'); ?></div>
						</nav>

					<?php } ?>

				</div>

				<?php wp_reset_postdata(); ?>


		</div>

	</div><!-- #content -->


<?php get_footer() ?>

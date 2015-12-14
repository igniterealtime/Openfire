<?php get_header(); ?>

	<div id="content">
		<div class="padder">

			<?php do_action( 'bp_before_attachment' ); ?>

			<div class="page" id="attachments-page" role="main">

				<?php if (have_posts()) : while (have_posts()) : the_post(); ?>

					<?php do_action( 'bp_before_blog_post' ); ?>

					<div id="post-<?php the_ID(); ?>" <?php post_class(); ?>>

						<div class="author-box">
							<?php echo get_avatar( get_the_author_meta( 'user_email' ), '50' ); ?>
							<p><?php printf( _x( 'by %s', 'Post written by...', 'buddypress' ), str_replace( '<a href=', '<a rel="author" href=', bp_core_get_userlink( $post->post_author ) ) ); ?></p>
						</div>

						<div class="post-content">
							<h2 class="posttitle"><a href="<?php the_permalink(); ?>" rel="bookmark" title="<?php esc_attr_e( 'Permanent Link to', 'buddypress' ); ?> <?php the_title_attribute(); ?>"><?php the_title(); ?></a></h2>

							<p class="date">
								<?php the_date(); ?>
								<span class="post-utility alignright"><?php edit_post_link( __( 'Edit this entry', 'buddypress' ) ); ?></span>
							</p>

							<div class="entry">
								<?php echo wp_get_attachment_image( $post->ID, 'large', false, array( 'class' => 'size-large aligncenter' ) ); ?>

								<div class="entry-caption"><?php if ( !empty( $post->post_excerpt ) ) the_excerpt(); ?></div>
								<?php the_content(); ?>
							</div>

							<p class="postmetadata">
								<?php
									if ( wp_attachment_is_image() ) :
										$metadata = wp_get_attachment_metadata();
										printf( __( 'Full size is %s pixels', 'buddypress' ),
											sprintf( '<a href="%1$s" title="%2$s">%3$s &times; %4$s</a>',
												wp_get_attachment_url(),
												esc_attr( __( 'Link to full size image', 'buddypress' ) ),
												$metadata['width'],
												$metadata['height']
											)
										);
									endif;
								?>
								&nbsp;
							</p>
						</div>

					</div>

					<?php do_action( 'bp_after_blog_post' ); ?>

					<?php comments_template(); ?>

				<?php endwhile; else: ?>

					<p><?php _e( 'Sorry, no attachments matched your criteria.', 'buddypress' ); ?></p>

				<?php endif; ?>

			</div>

		<?php do_action( 'bp_after_attachment' ); ?>

		</div><!-- .padder -->
	</div><!-- #content -->

	<?php get_sidebar(); ?>

<?php get_footer(); ?>
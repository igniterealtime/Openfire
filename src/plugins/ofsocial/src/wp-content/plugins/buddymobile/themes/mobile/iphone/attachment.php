<?php

/**
 * BuddyMobile - File attachment Page
 *
 * This template displays a file attachment.
 *
 * @package BuddyMobile
 *
 */

?>

<?php get_header(); ?>

	<div id="content">

		<div id="attachments-page" class="page">

			<h2 class="pagetitle"><?php _e( 'Blog', 'buddypress' ) ?></h2>

				<?php if (have_posts()) : while (have_posts()) : the_post(); ?>

					<?php do_action( 'bp_before_blog_post' ) ?>

					<?php $attachment_link = get_the_attachment_link($post->ID, true, array(450, 800)); // This also populates the iconsize for the next line ?>
					<?php $_post = &get_post($post->ID); $classname = ($_post->iconsize[0] <= 128 ? 'small' : '') . 'attachment'; // This lets us style narrow icons specially ?>

					<div class="post" id="post-<?php the_ID(); ?>">

						<h2 class="posttitle"><a href="<?php echo get_permalink($post->post_parent); ?>" rev="attachment"><?php echo get_the_title($post->post_parent); ?></a> &rarr; <a href="<?php echo get_permalink() ?>" rel="bookmark" title="Permanent Link: <?php the_title(); ?>"><?php the_title(); ?></a></h2>

						<div class="entry">
							<p class="<?php echo $classname; ?>"><?php echo $attachment_link; ?><br /><?php echo basename($post->guid); ?></p>

							<?php the_content( __('<p class="serif">Read the rest of this entry &rarr;</p>', 'buddypress' ) ); ?>

							<?php wp_link_pages( array( 'before' => __( '<p><strong>Pages:</strong> ', 'buddypress' ), 'after' => '</p>', 'next_or_number' => 'number')); ?>
						</div>

					</div>

				<?php comments_template(); ?>

				<?php endwhile; else: ?>

					<p><?php _e( 'Sorry, no attachments matched your criteria.', 'buddypress' ) ?></p>

				<?php endif; ?>
		</div>

	</div>

<?php get_footer(); ?>

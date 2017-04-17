<?php get_header() ?>

<div id="content">

		<div class="page" id="blog-page">

		<?php if ( 'page' == get_option('show_on_front') && is_front_page() ) {?>

			<?php query_posts('pagename=mobile') ?>

			<?php if (have_posts()) : while (have_posts()) : the_post(); ?>

				<div class="post" id="post-<?php the_ID(); ?>">

					<div class="entry">

						<?php the_content(); ?>

					</div>

				</div>

			<?php endwhile; endif; ?>

		<?php }else{  ?>

			<?php if (have_posts()) : while (have_posts()) : the_post(); ?>

			<div class="post" id="post-<?php the_ID(); ?>">
				<?php if ( function_exists( 'bp_is_active' ) ) : ?>
					<?php if ( !bp_current_component() ) : ?>
						<h2 class="pagetitle"><?php the_title(); ?></h2>
					<?php endif; ?>
				<?php else: ?>
					<h2 class="pagetitle"><?php the_title(); ?></h2>
				<?php endif; ?>

					<div class="entry">


						<?php the_content( __( '<p class="serif">Read the rest of this page &rarr;</p>', 'buddypress' ) ); ?>

						<?php wp_link_pages( array( 'before' => __( '<p><strong>Pages:</strong> ', 'buddypress' ), 'after' => '</p>', 'next_or_number' => 'number')); ?>
						<?php edit_post_link( __( 'Edit this entry.', 'buddypress' ), '<p>', '</p>'); ?>

					</div>

				</div>



			<?php endwhile; endif;  ?>

		</div><!-- .page -->

		<div id="blog-page-comments">
		<?php comments_template(); ?>
		</div>
		<?php }  ?>

</div><!-- #content -->
<?php get_footer(); ?>

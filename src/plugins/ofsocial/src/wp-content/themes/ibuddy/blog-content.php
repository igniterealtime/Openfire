<?php
/*
 * Main Blog page content file.
 *
 * This file is used when a page is using Blog page template.
 *
 * @package iBuddy
 */
?>
<header class="entry-header">
		<h1 class="entry-title"><?php the_title(); ?></h1>
</header><!-- .entry-header -->
	<?php 
		$args = array(
 	 	'post_type' => 'post',
  		);
		
		query_posts($args); 
	?>
	<?php if ( have_posts() ) : ?>

			<?php /* Start the Loop */ ?>
			<?php while ( have_posts() ) : the_post(); ?>

				<?php get_template_part( 'content', 'index' ); ?>

			<?php endwhile; ?>
	
		<?php ibuddy_content_nav( 'nav-below' ); ?>
	
	<?php else : ?>

			<?php get_template_part( 'no-results', 'index' ); ?>

	<?php endif; ?>
<?php wp_reset_query(); ?>

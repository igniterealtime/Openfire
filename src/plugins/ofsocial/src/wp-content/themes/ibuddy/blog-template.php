<?php
/*
Template Name: Blog
 
 * Blog page template.
 *
 * This template can be selected when creating new page.
 *
 * @package iBuddy
 */
?>

<?php get_header(); ?>
<div id="primary" class="content-area">
		<div id="content" class="site-content" role="main">
				<?php get_template_part( 'blog', 'content' ); ?>
		</div><!-- #content -->
	</div><!-- #primary -->
<?php get_blog_sidebar(); ?>
<?php get_footer(); ?>

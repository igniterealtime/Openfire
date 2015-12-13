<?php
/**
 * Page
 *
 * @package Status
 * @since 1.0
 */
?>

<?php get_header(); ?>
<div id="content" class="primary" role="main">
	<?php do_action( 'bp_before_blog_page' ) ?>
	<?php the_post(); ?>
	<?php get_template_part( 'content', 'page' );?>
	<?php comments_template( '', true ); ?>
	<?php do_action( 'bp_after_blog_page' ) ?>
</div>
<?php get_sidebar(); ?>
<?php get_footer(); ?>
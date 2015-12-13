<?php
/**
 * Search
 *
 * @package Status
 * @since 1.0
 */
?>

<?php get_header(); ?>
<div id="content" class="primary" role="main">
	<?php do_action( 'bp_before_blog_search' ) ?>
	<?php if ( have_posts() ) : ?>
	<header class="post-header">
		<h1 class="post-title"><?php printf( __( 'Search Results for: %s', 'status'), '<span>' . get_search_query() . '</span>' ); ?></h1>
	</header>
	<?php bp_dtheme_content_nav( 'nav-above' ); ?>
	<?php while ( have_posts() ) : the_post(); 
			do_action( 'bp_before_blog_post' );
			get_template_part( 'content', get_post_format() );
			do_action( 'bp_after_blog_post' );
		endwhile;
			bp_dtheme_content_nav( 'nav-below' );
		else : ?>
	<article id="post-0" class="post no-results not-found">
		<header class="post-header">
			<h2 class="post-title"><?php _e( 'Nothing Found', 'status'); ?></h2>
		</header>
		<div class="post-body">
			<p><?php _e( 'Sorry, but nothing matched your search criteria. Please try again with some different keywords.', 'status'); ?></p>
			<?php get_search_form(); ?>
		</div>
	</article>
	<?php endif; ?>
	<?php do_action( 'bp_after_blog_search' ) ?>
</div>
<?php get_sidebar(); ?>
<?php get_footer() ?>
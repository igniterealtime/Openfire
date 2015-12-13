<?php
/**
 * 404
 *
 * @package Status
 * @since 1.0
 */
?>

<?php get_header(); ?>
<div id="content-404" class="primary" role="main">
	<?php do_action( 'bp_before_404' ); ?>
	<article class="post error404 not-found">
		<header class="post-header">
			<h1 class="post-title"><?php _e( 'Not Found', 'status'); ?></h1>
		</header>
		<div class="post-body">
			<p><?php _e( 'Sorry we could not find that page.', 'status'); ?></p>
				<?php get_search_form(); ?>
				<?php do_action( 'bp_404' ); ?>
		</div>
	</article>
	<?php do_action( 'bp_after_404' ) ?>
</div>
<?php get_footer(); ?>
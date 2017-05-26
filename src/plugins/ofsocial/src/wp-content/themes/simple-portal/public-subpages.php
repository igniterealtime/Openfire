<?php
/*
Template Name: Public Subpage Gallery
*
* Shows subpages of the parent page.
*
*/
?>
<?php get_header(); ?>
<div id="primary" class="content-area">
		<div id="content" class="site-content" role="main">
					<?php /* The loop */ ?>
			<?php while ( have_posts() ) : the_post(); ?>

				<article id="post-<?php the_ID(); ?>" <?php post_class(); ?>>
					<header class="entry-header">
						<?php if ( has_post_thumbnail() && ! post_password_required() ) : ?>
						<div class="entry-thumbnail">
							<?php the_post_thumbnail(); ?>
						</div>
						<?php endif; ?>

						<h1 class="entry-title"><?php the_title(); ?></h1>
					</header><!-- .entry-header -->

					<div class="entry-content">
						<?php the_content(); ?>
						<?php wp_link_pages( array( 'before' => '<div class="page-links"><span class="page-links-title">' . __( 'Pages:', 'simpleportal' ) . '</span>', 'after' => '</div>', 'link_before' => '<span>', 'link_after' => '</span>' ) ); ?>
					</div><!-- .entry-content -->

					<footer class="entry-meta">
						<?php edit_post_link( __( 'Edit', 'simpleportal' ), '<span class="edit-link">', '</span>' ); ?>
					</footer><!-- .entry-meta -->
				</article><!-- #post -->

				<?php comments_template(); ?>
			<?php endwhile; ?>
	<div class="subpages">
	<ul class="thumbnails">
	<?php
	$page_id = $posts[0]->ID;
	$args=array(
	'post_parent' => $page_id,
	'post_type' => 'page',
	'post_status' => 'publish',
	'orderby' => 'menu_order',
	'order' => 'asc',
	'posts_per_page' => -1,
	'ignore_sticky_posts'=> 1
	);
	$my_query = null;
	$my_query = new WP_Query($args);
	if( $my_query->have_posts() ) {
	while ($my_query->have_posts()) : $my_query->the_post(); $data = get_post_meta( $post->ID, 'Portfolio', true ); ?>
		<li class="col-md-2" id="post-<?php the_ID(); ?>">
			<div class="thumbnail">
			<a href="<?php the_permalink(); ?>" title="<?php the_title(); ?>">
			<?php if ( has_post_thumbnail()) {
			$full_image_url = wp_get_attachment_image_src( get_post_thumbnail_id($post->ID), 'full');
			echo get_the_post_thumbnail($post->ID, 'thumbnail');
			 } else { ?>
			<img src="<?php echo get_stylesheet_directory_uri() ?>/images/default-image.png" alt="<?php the_title(); ?>" width="150" height="150" class="attachment-medium" />
			<?php } ?>
			</a>
			
			</div>
		</li>
	<?php
	endwhile;
	}
	wp_reset_query();  // Restore global post data stomped by the_post().
	?>
	</ul>
	</div>

		</div><!-- #content -->
	</div><!-- #primary -->

<?php get_sidebar(); ?>
<?php get_footer(); ?>
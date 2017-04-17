<?php
/*
Template Name: Public Gallery Page
*
* For pages that are assigned as sub pages of the parent page set to one of the sub page gallery views. Displays the full image if set in the custom write panel for a page and a link back to the parent page.
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
							<?php the_post_thumbnail('full'); ?>
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

	<div class="clear"></div>

<?php
$pagelist = get_pages("child_of=".$post->post_parent."&parent=".$post->post_parent."&sort_column=menu_order&sort_order=asc");
$pages = array();
foreach ($pagelist as $page) {
   $pages[] += $page->ID;
}

$current = array_search($post->ID, $pages);
if (isset($pages[$current-1])) $prevID = $pages[$current-1];
if (isset($pages[$current+1])) $nextID = $pages[$current+1];
?>
	
	<ul class="pager">
	<?php if (!empty($prevID)) : ?>
	<li class="previous">
	<a href="<?php echo get_permalink($prevID); ?>"
	title="<?php echo get_the_title($prevID); ?>">Previous</a>
	</li>
	<?php else : ?>
	<li class="previous disabled">
	<a href="#">Previous</a>
	</li>
	<?php endif; ?>
	<?php if (!empty($nextID)) : ?>
	<li class="next">
	<a href="<?php echo get_permalink($nextID); ?>"
	title="<?php echo get_the_title($nextID); ?>">Next</a>
	</li>
	<?php else : ?>
	<li class="next disabled">
	<a href="#">Next</a>
	</li>
	<?php endif; ?>
	</ul>

<?php wp_reset_query(); ?>


		</div><!-- #content -->
	</div><!-- #primary -->

<?php get_sidebar(); ?>
<?php get_footer(); ?>
<?php
/**
 * The template used for displaying page content in page.php
 *
 * @package _tk
 */
$show_title = true;
$display_page_title_props = get_theme_mod('display_page_title', array() );

// search
if( isset( $display_page_title_props['pages'] ) && $display_page_title_props['pages'] != 1 ) {
	$show_title = false;
}
?>

<article id="post-<?php the_ID(); ?>" <?php post_class(); ?>>
	<?php if( $show_title != false ) : ?>
	<header class="page-header">
		<h1 class="page-title"><?php the_title(); ?></h1>
	</header><!-- .entry-header -->
	<?php endif; ?>

	<div class="entry-content">
		<?php the_content(); ?>
		<?php
			wp_link_pages( array(
				'before' => '<div class="page-links">' . __( 'Pages:', 'cc2' ),
				'after'  => '</div>',
			) );
		?>
	</div><!-- .entry-content -->
	<?php edit_post_link( __( 'Edit', 'cc2' ), '<footer class="entry-meta"><span class="edit-link">', '</span></footer>' ); ?>
</article><!-- #post-## -->

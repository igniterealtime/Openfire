<?php
/**
 * @package _tk
 */
 
// customizer title display handling
$show_title = true; // default
$display_page_title_props = get_theme_mod('display_page_title', array() );
$author_image_settings = get_theme_mod('show_author_image' );
$post_class = get_post_class();
//$author_avatar = false;

// search
if( is_search() && isset( $display_page_title_props['search'] ) && $display_page_title_props['search'] != 1 ) {
	$show_title = false;
}

// archive
if( is_archive() && isset( $display_page_title_props['archive'] ) && $display_page_title_props['archive'] != 1 ) {
	$show_title = false;
}

// front page
if( is_home() && isset( $display_page_title_props['home'] ) && $display_page_title_props['home'] != 1 ) {
	$show_title = false;
}

if( 'post' == get_post_type() && $author_image_settings['archive'] != false ) {
	$author_avatar = cc2_get_author_image();
	$post_class[] = 'has-author-avatar';
}

?>

<?php // Styling Tip! 

// Want to wrap for example the post content in blog listings with a thin outline in Bootstrap style?
// Just add the class "panel" to the article tag here that starts below. 
// Simply replace post_class() with post_class('panel') and check your site!   
// Remember to do this for all content templates you want to have this, 
// for example content-single.php for the post single view. ?>

<article id="post-<?php the_ID(); ?>" <?php post_class( $post_class ); ?>>
	

	<header class="page-header">
	<?php if( $show_title != false ) : ?>
		<h1 class="page-title"><a href="<?php the_permalink(); ?>" rel="bookmark"><?php the_title(); ?></a></h1>
	<?php endif; ?>


	<?php if( !empty($author_avatar) ) : ?>
	<div class="entry-meta-author pull-left">
		<?php echo ( !empty($author_avatar['linked_image']) ? $author_avatar['linked_image'] : $author_avatar['image'] ); ?>
	</div>
	<?php endif; ?>
		<?php if ( 'post' == get_post_type() ) : ?>
		<div class="entry-meta">
			<?php _tk_posted_on(); ?>
		</div><!-- .entry-meta -->
		<?php endif; ?>
	</header><!-- .entry-header -->

	<?php if ( is_search() || is_archive() ) : // Only display Excerpts for Search and Archive Pages ?>
	<div class="entry-summary">
		<?php the_excerpt(); ?>
	</div><!-- .entry-summary -->
	<?php else : ?>
	<div class="entry-content">
		<?php the_content( __( 'Continue reading <span class="meta-nav">&rarr;</span>', 'cc2' ) ); ?>
		<?php
			wp_link_pages( array(
				'before' => '<div class="page-links">' . __( 'Pages:', 'cc2' ),
				'after'  => '</div>',
			) );
		?>
	</div><!-- .entry-content -->
	<?php endif; ?>

	<footer class="entry-meta">
		<?php if ( 'post' == get_post_type() ) : // Hide category and tag text for pages on Search ?>
			<?php
				/* translators: used between list items, there is a space after the comma */
				$categories_list = get_the_category_list( __( ', ', 'cc2' ) );
				if ( $categories_list && _tk_categorized_blog() ) :
			?>
			<span class="cat-links">
				<?php printf( __( 'Posted in %1$s', 'cc2' ), $categories_list ); ?>
			</span>
			<?php endif; // End if categories ?>

			<?php
				/* translators: used between list items, there is a space after the comma */
				$tags_list = get_the_tag_list( '', __( ', ', 'cc2' ) );
				if ( $tags_list ) :
			?>
			<span class="tags-links">
				<?php printf( __( 'Tagged %1$s', 'cc2' ), $tags_list ); ?>
			</span>
			<?php endif; // End if $tags_list ?>
		<?php endif; // End if 'post' == get_post_type() ?>

		<?php if ( ! post_password_required() && ( comments_open() || '0' != get_comments_number() ) ) : ?>
		<span class="comments-link"><?php comments_popup_link( __( 'Leave a comment', 'cc2' ), __( '1 Comment', 'cc2' ), __( '% Comments', 'cc2' ) ); ?></span>
		<?php endif; ?>

		<?php edit_post_link( __( 'Edit', 'cc2' ), '<span class="edit-link">', '</span>' ); ?>
	</footer><!-- .entry-meta -->
</article><!-- #post-## -->

<?php
/**
 * The front page template.
 *
 * This is the template that displays all pages by default.
 * Please note that this is the WordPress construct of pages
 * and that other 'pages' on your WordPress site will use a
 * different template.
 *
 * @since 1.0.0
 */
get_header();

$bavotasan_theme_options = bavotasan_theme_options();
global $wp_query;
	if ( have_posts() ) {
		if ( 'page' == get_option( 'show_on_front' ) ) {
			include( get_page_template() );
		} else {
			?>
			<div id="primary" class="clearfix">
				<div id="content" class="sticky-container">
					<?php bavotasan_front_page_render(); ?>
					<?php if ( is_active_sidebar( 'home-page-corner' ) ) { ?>
					<div class="stamp">
						<?php dynamic_sidebar( 'home-page-corner' ); ?>
					</div>
					<?php } ?>
				</div>
				<?php bavotasan_pagination(); ?>
			</div>
			<?php
		}
	}
?>

<?php get_footer(); ?>
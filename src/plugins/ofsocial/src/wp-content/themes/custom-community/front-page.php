<?php
/**
 * Front Page - also static front page if selected.
 * 
 * Learn more! -> http://codex.wordpress.org/Template_Hierarchy
 *
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 * 
 */
// content on static front page
$show_front_page_content = true;
if( get_theme_mod( 'hide_front_page_content', false) != false ) {
	$show_front_page_content = false;
}

get_header(); 

if( $show_front_page_content ) :
	$content_class = array('main-content-inner');
?>

    <div class="main-content">
        <div id="container" class="container">
            <div class="row">

                <?php do_action( 'cc_first_inside_main_content'); ?>

				<?php if( cc2_display_sidebar( 'left' ) ) : ?>
					<?php get_sidebar( 'left' ); ?>
				<?php endif; ?>

                <div id="content" class="<?php echo apply_filters('cc2_content_class', $content_class ); ?>">

                    <?php do_action( 'cc_first_inside_main_content_inner'); ?>

                    <?php

                    /* This is a Loop Designer Ready Theme:
                     * before we enter the loop we should check if TK Loop Designer is activated
                     * and if another Loop Template is chosen than the default one (named "blog style").
                     */
                    if( function_exists( 'tk_loop_designer_the_loop' ) && 'blog-style' !== get_theme_mod( 'cc_list_post_style' ) ) {

                        // If we got this far, the Loop Designer is active and we call the TK Loop Designer Loop.
                        // Let's do it.
                        tk_loop_designer_the_loop( get_theme_mod( 'cc_list_post_style' ), 'index', 'show' );

                    } else {

                        /* If we landed here, the Loop Designer is not active
                         * or we wan to display the blog style loop anyway. Here it goes..
                         */
                        if ( have_posts() ) : ?>

                            <div id="featured_posts_index">
                                <div id="list_posts_index" class="loop-designer list-posts-all">
                                    <div class="index">

                                        <?php /* Start the loop */ ?>
                                        <?php while ( have_posts() ) : the_post(); ?>

                                            <?php
                                                /* Include the post-format-specific template for the content.
                                                 * If you want to overload this in a child theme then include a file
                                                 * called content-___.php (where ___ is the Post Format name) and that will be used instead.
                                                 */
                                                get_template_part( 'content', get_post_format() );
                                            ?>

                                        <?php endwhile; ?>

                                    </div>

									<?php // Get the page navigation for older and newer posts
									do_action('cc2_have_posts_after_loop_front_page' );
									?>

                                </div>
                            </div>

                        <?php else : ?>

                            <?php // If we ended up here, we got no posts to show. ?>
                            <?php get_template_part( 'no-results', 'index' ); ?>

                        <?php endif; ?>

                    <?php } // we're through! that was the whole loop thing! let's move on to the sidebar.. ?>

                    <?php do_action('tk_sidebars_index'); ?>

                </div><!-- close #content -->

				<?php if( cc2_display_sidebar( 'right' ) ) : ?>
					<?php get_sidebar( 'right' ); ?>
				<?php endif; ?>

            </div><!-- close .row -->
        </div><!-- close .container -->
    </div><!-- close .main-content -->
<?php 
	endif; // end show_front_page_content != false
?>

<?php get_footer();

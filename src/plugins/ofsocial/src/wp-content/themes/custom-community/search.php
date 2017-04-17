<?php
/**
 * The template for displaying Search Results pages.
 *
 * @package _tk
 */
$content_class = array('main-content-inner');

get_header(); ?>

    <div class="main-content">
        <div id="container" class="container">
            <div class="row">

                <?php do_action( 'cc_first_inside_main_content'); ?>

                <?php
                // get the left sidebar if it should be displayed
                if( cc2_display_sidebar( 'left' ) )
                    get_sidebar( 'left' ); ?>

                <div id="content" class="<?php echo apply_filters( 'cc2_content_class', $content_class ); ?>">

                    <?php do_action( 'cc_first_inside_main_content_inner'); ?>

                    <?php if ( have_posts() ) : ?>

                        <header class="page-header">
                            <h2 class="page-title"><?php printf( __( 'Search Results for: %s', 'cc2' ), '<span>' . get_search_query() . '</span>' ); ?></h2>
                        </header><!-- .page-header -->

                        <?php // start the loop. ?>
                        <?php while ( have_posts() ) : the_post(); ?>

                            <?php get_template_part( 'content', 'search' ); ?>

                        <?php endwhile; ?>

                        <?php _tk_content_nav( 'nav-below' ); ?>

                    <?php else : ?>

                        <?php get_template_part( 'no-results', 'search' ); ?>

                    <?php endif; // end of loop. ?>

                </div><!-- close #content -->

                <?php if( cc2_display_sidebar( 'right' ) )
                        get_sidebar( 'right' ); ?>

            </div><!-- close .row -->
        </div><!-- close .container -->
    </div><!-- close .main-content -->


<?php get_footer(); ?>

<?php
/**
 * The template for displaying 404 pages (Not Found).
 *
 * @package _tk
 */
?>

<?php get_header(); ?>

    <div class="main-content">
        <div id="container" class="container">
            <div class="row">

                <?php do_action( 'cc_first_inside_main_content'); ?>

                <?php
                // get the left sidebar if it should be displayed
                if( cc2_display_sidebar( 'left' ) )
                    get_sidebar( 'left' ); ?>

                <div id="content" class="<?php echo apply_filters( 'cc2_content_class', array('main-content-inner') ); ?>">

                    <?php do_action( 'cc_first_inside_main_content_inner'); ?>


                    <?php // add the class "panel" below here to wrap the content-padder in Bootstrap style ;) ?>
                    <section class="content-padder error-404 not-found">

						<?php if( get_theme_mod('display_page_title[error]', false ) != false ) : ?>
                        <header class="page-header">
                            <h2 class="page-title"><?php _e( 'Oops! Something went wrong here.', 'cc2' ); ?></h2>
                        </header><!-- .page-header -->
						<?php endif; ?>
						
                        <div class="page-content">

                            <p><?php _e( 'Nothing could be found at this location. Maybe try a search?', 'cc2' ); ?></p>

                            <?php get_search_form(); ?>

                        </div><!-- .page-content -->

                    </section><!-- .content-padder -->

                </div><!-- close #content -->

                <?php if( cc2_display_sidebar( 'right' ) )
                    get_sidebar( 'right' ); ?>

            </div><!-- close .row -->
        </div><!-- close .container -->
    </div><!-- close .main-content -->


<?php get_footer(); ?>

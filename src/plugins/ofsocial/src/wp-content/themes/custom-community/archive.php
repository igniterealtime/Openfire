<?php
/**
 * The template for displaying Archive pages.
 *
 * Learn more: http://codex.wordpress.org/Template_Hierarchy
 *
 * @package _tk
 */
$content_class = array('main-content-inner');
?>

<?php get_header(); ?>

    <div class="main-content">
        <div id="container" class="container">
            <div class="row">

                <?php do_action( 'cc_first_inside_main_content'); ?>

                <?php
                // get the left sidebar if it should be displayed
                if( cc2_display_sidebar( 'left' ) ) :
                    get_sidebar( 'left' ); 
                endif; ?>
                    

                <div id="content" class="<?php echo apply_filters('cc2_content_class', $content_class ); ?>">

                    <?php do_action( 'cc_first_inside_main_content_inner'); ?>

                    <?php get_template_part('content','archive'); ?>

                </div><!-- close #content -->

                <?php if( cc2_display_sidebar( 'right' ) ) :
                    get_sidebar( 'right' );
				endif; ?>

            </div><!-- close .row -->
        </div><!-- close .container -->
    </div><!-- close .main-content -->


<?php get_footer(); ?>

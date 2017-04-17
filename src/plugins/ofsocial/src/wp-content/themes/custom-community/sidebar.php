<?php
/**
 * The sidebar containing the main widget area
 *
 * @package cc2
 * @since 2.0
 */
$sidebar_class = 'sidebar sidebar-right';
?>

<?php /*<div class="sidebar sidebar-right <?php do_action( 'cc_sidebar_right_class' ) ?> ">*/
?>
<div class="<?php echo apply_filters( 'cc_sidebar_right_class', $sidebar_class ); ?>">


    <?php // add the class "panel" below here to wrap the sidebar in Bootstrap style! *in your child theme of course* ;) ?>
    <div class="sidebar-padder">

        <?php do_action( 'before_sidebar' ); ?>
        <?php if ( ! dynamic_sidebar( 'sidebar-right' ) ) : ?>

            <aside id="search" class="widget widget_search">
                <?php get_search_form(); ?>
            </aside>

            <aside id="archives" class="widget widget_archive">
                <h3 class="widget-title"><?php _e( 'Archives', 'cc2' ); ?></h3>
                <ul>
                    <?php wp_get_archives( array( 'type' => 'monthly' ) ); ?>
                </ul>
            </aside>

            <aside id="meta" class="widget widget_meta">
                <h3 class="widget-title"><?php _e( 'Meta', 'cc2' ); ?></h3>
                <ul>
                    <?php wp_register(); ?>
                    <li><?php wp_loginout(); ?></li>
                    <?php wp_meta(); ?>
                </ul>
            </aside>

        <?php endif; ?>

    </div>

</div><!-- close .sidebar-right -->

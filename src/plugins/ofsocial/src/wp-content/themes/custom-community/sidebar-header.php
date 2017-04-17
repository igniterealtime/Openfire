<?php
/**
 * The sidebar in the header
 *
 * @package cc2
 * @since 2.0
 */
$sidebar_class = 'sidebar sidebar-header row';
?>

<?php if( is_active_sidebar( 'sidebar-header' ) || !is_active_sidebar( 'sidebar-header' ) && current_user_can('edit_theme_options') ) : ?>

	<div class="<?php echo apply_filters( 'cc2_sidebar_header_class', $sidebar_class ); ?>">

		<?php // add the class "panel" below here to wrap the sidebar in Bootstrap style! ;) ?>
		<div class="col-12">

			<?php do_action( 'before_sidebar' ); ?>
			<?php if ( ! dynamic_sidebar( 'sidebar-header' ) && current_user_can('edit_theme_options') ) : ?>
				
				<div class="widgetarea">
					<h3 class="widgettitle" ><?php _e('Add a widget', 'cc2'); ?> <a href="<?php echo admin_url('widgets.php') ?>"><?php _e('here', 'cc2'); ?></a>.</h3>
					<p><em><?php _e('* only visible for admins ;)', 'cc2' ); ?></em></p>
				</div>

			<?php endif; ?>
			
			<?php do_action( 'after_sidebar' ); ?>

		</div>

	</div><!-- /.sidebar-header -->
	
<?php endif; ?>

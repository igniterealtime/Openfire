	<?php if (is_404() || is_page_template('public-full.php') || is_page_template('public-subpages-4-column.php') || is_page_template('public-subpages.php') || is_page_template('gallery-public.php') ) : ?>
	</div>
	</div>
	</div>
	</div>
	</div>
	<?php elseif (function_exists('bp_is_active') && bp_is_activation_page() || function_exists('bp_is_active') && bp_is_user() || function_exists('bp_is_active') && bp_is_register_page() || function_exists('bp_is_active') &&  bp_is_current_component( 'groups' ) || function_exists('bp_is_active') && bp_is_current_component( 'activity' ) || function_exists('bp_is_active') && bp_is_current_component( 'members' ) || class_exists('bbPress') && is_bbpress()) : ?>
	</div>
	</div>
	</div>
	</div>
	</div>
	<?php else : ?>
	</div>
	</div>
	</div>
	</div>
	</div>
	</div>
	<?php endif; ?>
	
	<div class="clear"></div>
	<div class="container">
	<?php if (is_front_page()) : ?>
	<div class="row">
	<div class="col-md-6">
	<?php if ( !function_exists('dynamic_sidebar')
	|| !dynamic_sidebar('Footer Area Home 1') ) : ?>
	<?php endif; ?>
	</div>
	<div class="col-md-6">
	<?php if ( !function_exists('dynamic_sidebar')
	|| !dynamic_sidebar('Footer Area Home 2') ) : ?>
	<?php endif; ?>
	</div>
	</div>
	<div class="clear"></div>
	<?php else : ?>
	<?php endif; ?>
	<div class="row">
	<div class="col-md-4">
	<?php if ( !function_exists('dynamic_sidebar')
	|| !dynamic_sidebar('Footer Area1') ) : ?>
	<?php endif; ?>
	</div>
	<div class="col-md-4">
	<?php if ( !function_exists('dynamic_sidebar')
	|| !dynamic_sidebar('Footer Area2') ) : ?>
	<?php endif; ?>
	</div>
	<div class="col-md-4">
	<?php if ( !function_exists('dynamic_sidebar')
	|| !dynamic_sidebar('Footer Area3') ) : ?>
	<?php endif; ?>
	</div>
	</div>
	<div class="clear"></div>
	
	<nav class="navbar navbar-default navbar-static-bottom" role="navigation">
  <div class="container-fluid">
  <p class="navbar-text navbar-right"><?php bloginfo('name'); ?> <?php printf( __( 'content &copy; Copyright', 'simpleportal' ) ); ?>  <?php echo date('Y'); ?>.</p>
  </div>
</nav>

	</div>

	</div>
	</div>
<?php wp_footer(); ?>

</body>

</html>
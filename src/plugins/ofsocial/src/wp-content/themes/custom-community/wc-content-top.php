<?php
/**
 * WooCommerce Integration: Before Content
 *
 * @package cc2
 * @since 2.0.25
 * @author Fabian Wolf
 */

$content_class = array('main-content-inner');
?>

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


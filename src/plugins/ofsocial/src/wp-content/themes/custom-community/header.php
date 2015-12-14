<?php
/**
 * The Header for our theme.
 *
 * Displays all of the <head> section and everything up till <div id="main">
 *
 * @package cc2
 * @since 2.0
 * 
 */
?><!DOCTYPE html>
<html <?php language_attributes(); ?> class="no-js">
	<head>
		<meta charset="<?php bloginfo( 'charset' ); ?>" />
		<meta name="viewport" content="width=device-width, initial-scale=1.0" />
		<title><?php wp_title( '|', true, 'right' ); ?></title>
		<link rel="profile" href="http://gmpg.org/xfn/11" />
		<link rel="pingback" href="<?php bloginfo( 'pingback_url' ); ?>" />
<?php wp_head(); ?>
</head>

<body <?php body_class(); ?>>

<?php do_action( 'cc_before_header'); // @hooked -> add_top_nav() -> includes/template-tags.php ?>

<!-- The Site's Main Header -->	
<header id="masthead" class="site-header" role="banner">
	<div class="container">
		<div class="row">

			<div class="site-header-inner md-col-12">
				
				<!-- The Header Image goes here -->
				<?php do_action( 'cc_header_image'); // @hooked -> cc_add_header_image() -> includes/template-tags.php ?>
				
				<!-- The Site's Header Branding -->
				<div class="site-branding">
					<h1 class="site-title"><a href="<?php echo esc_url( home_url( '/' ) ); ?>" title="<?php echo esc_attr( get_bloginfo( 'name', 'display' ) ); ?>" rel="home"><?php bloginfo( 'name' ); ?></a></h1>
					<p class="site-description"><?php bloginfo( 'description' ); ?></p>
				</div>

				<?php do_action( 'cc_header_last'); // wanna hook something here? ;) ?>

			</div>
		</div>
		
		<?php get_sidebar('header'); ?>
	</div><!-- .container -->
</header><!-- #masthead -->

<?php 

do_action( 'cc_after_header'); // @hooked -> add_default_nav() -> includes/template-tags.php 

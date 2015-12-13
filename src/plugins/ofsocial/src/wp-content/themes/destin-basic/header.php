<?php
/**
 * The Header for our theme.
 *
 * Displays all of the <head> section and everything up till <main>
 * and the left sidebar conditional
 *
 * @since 1.0.0
 */
?><!DOCTYPE html>
<!--[if lt IE 7]><html class="lt-ie9 lt-ie8 lt-ie7" <?php language_attributes(); ?>><![endif]-->
<!--[if IE 7]><html class="lt-ie9 lt-ie8" <?php language_attributes(); ?>><![endif]-->
<!--[if IE 8]><html class="lt-ie9" <?php language_attributes(); ?>><![endif]-->
<!--[if gt IE 8]><!--><html <?php language_attributes(); ?>><!--<![endif]-->
<head>
	<meta charset="<?php bloginfo( 'charset' ); ?>">
	<meta name="viewport" content="width=device-width">
	<link rel="profile" href="http://gmpg.org/xfn/11">
	<link rel="pingback" href="<?php bloginfo( 'pingback_url' ); ?>">
	<!--[if IE]><script src="<?php echo BAVOTASAN_THEME_URL; ?>/library/js/html5.js"></script><![endif]-->
	<?php wp_head(); ?>
</head>
<?php
$bavotasan_theme_options = bavotasan_theme_options();
?>
<body <?php body_class(); ?>>

	<div id="page" class="clearfix">
		<header id="header">
			<div class="container">
				<h1 id="site-title"><a href="<?php echo esc_url( home_url() ); ?>" title="<?php echo esc_attr( get_bloginfo( 'name', 'display' ) ); ?>" rel="home">
					<?php bloginfo( 'name' ); ?>
				</a></h1>
				<h2 id="site-description"><?php bloginfo( 'description' ); ?></h2>
			</div>

			<nav id="site-navigation" class="navbar navbar-inverse" role="navigation">
				<div class="container">
					<h3 class="sr-only"><?php _e( 'Main menu', 'destin' ); ?></h3>
					<a class="sr-only" href="#primary" title="<?php esc_attr_e( 'Skip to content', 'destin' ); ?>"><?php _e( 'Skip to content', 'destin' ); ?></a>

					<div class="navbar-header">
						<button type="button" class="navbar-toggle" data-toggle="collapse" data-target=".navbar-collapse">
					        <span class="icon-bar"></span>
					        <span class="icon-bar"></span>
					        <span class="icon-bar"></span>
					    </button>
					</div>

					<div class="collapse navbar-collapse">
						<?php
						$menu_class = ( is_rtl() ) ? ' navbar-right' : '';

						wp_nav_menu( array( 'theme_location' => 'primary', 'container' => '', 'menu_class' => 'nav navbar-nav' . $menu_class, 'fallback_cb' => 'bavotasan_default_menu' ) );
						?>
					</div>
				</div>
			</nav><!-- #site-navigation -->
		</header>

		<main>

<?php
/**
 * The header for site page.
 *
 */
?>
<!DOCTYPE html>
<html <?php language_attributes(); ?>>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<meta charset="<?php bloginfo( 'charset' ); ?>" />
<?php wp_head(); ?>
</head>
<body <?php body_class(); ?>>
<div class="site">
	<header class="site-header">
		<div class="site-logo onetone-logo">
        	
			<?php if(onetone_options_array('logo')){?>
			<a href="<?php echo esc_url(home_url('/')); ?>"><img src="<?php echo onetone_options_array('logo'); ?>" alt="<?php bloginfo('name'); ?>" /></a>
			<?php }else{?>
            <a href="<?php echo esc_url(home_url('/')); ?>"><h1><span class="site-name"><?php bloginfo('name'); ?></span></h1></a>
            <?php }?>
            <div class="site-description"><?php bloginfo('description'); ?></div>
		</div>
        <a class="site-navbar navbar" href="javascript:;"></a> 
		<nav class="site-navigation top-nav">
			<div class="nav-menu">
			<?php wp_nav_menu(array('theme_location'=>'primary','depth'=>0,'fallback_cb' =>false,'container'=>'','container_class'=>'main-menu','menu_id'=>'menu-main','menu_class'=>'main-nav','link_before' => '<span>', 'link_after' => '</span>'));?>
			</div>
		</nav>
	</header>
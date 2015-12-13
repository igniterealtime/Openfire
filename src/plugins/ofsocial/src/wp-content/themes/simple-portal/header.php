<!DOCTYPE html>
<!--[if IE 7]>
<html id="ie7" <?php language_attributes(); ?>>
<![endif]-->
<!--[if IE 8]>
<html id="ie8" <?php language_attributes(); ?>>
<![endif]-->
<!--[if !(IE 6) | !(IE 7) | !(IE 8)  ]><!-->
<html <?php language_attributes(); ?>>
<!--<![endif]-->
<head>
<link rel="profile" href="http://gmpg.org/xfn/11" />
<meta http-equiv="Content-Type" content="<?php bloginfo('html_type'); ?>; charset=<?php bloginfo('charset'); ?>" />
<title><?php wp_title( '|', true, 'right' ); ?></title>
<link rel="pingback" href="<?php bloginfo( 'pingback_url' ); ?>" />
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<?php wp_head(); ?>
</head>

<body <?php body_class(); ?>>

			
			<nav class="navbar navbar-default navbar-static-top" role="navigation">
			<div class="container">
			<h1 class="site-title"><a title="<?php echo esc_attr( get_bloginfo( 'description' ) ); ?>" href="<?php echo esc_url( home_url( '/' ) ); ?>"><span class="glyphicon glyphicon-home"></span></a> <?php bloginfo('name'); ?></h1>
			<?php if (!is_user_logged_in() ) : ?>
			<ul class="nav navbar-nav navbar-right">
			<li><a href="<?php echo wp_login_url(); ?>" title="Login"><span class="glyphicon glyphicon-user"></span>  Sign In</a></li>
			</ul>
			<?php else : ?><?php endif; ?>
			<?php if (current_user_can('delete_others_pages') ) : ?>
			<ul class="nav navbar-nav navbar-right">
				<li><a href="#"><?php global $current_user; get_currentuserinfo(); ?><?php echo 'Welcome: ' . $current_user->display_name . "\n"; ?></a></li>
				<li class="dropdown">
				  <a href="#" class="dropdown-toggle" data-toggle="dropdown"><span class="glyphicon glyphicon-user"></span> Menu <b class="caret"></b></a>
				  <ul class="dropdown-menu">
					<li><a href="<?php echo site_url(); ?>/wp-admin/" title="admin"><?php printf( __( 'Admin', 'simpleportal' ) ); ?></a></li>
					<?php if (function_exists('bp_is_active')) : ?>
					<li><?php global $bp; ?><a href="<?php echo $bp->loggedin_user->domain . "\n"; ?>" title="your profile"><?php printf( __( 'Profile', 'simpleportal' ) ); ?></a></li>
					<?php if ( bp_is_active('messages')) : ?><li><a href="<?php echo bp_core_get_user_domain(bp_loggedin_user_id()); ?>messages" title="messages"><?php simpleportal_current_user_notification_count(); ?> - <?php printf( __( 'Messages', 'simpleportal' ) ); ?></a></li><?php else : ?><?php endif; ?>
					<?php if ( bp_is_active('groups')) : ?><li><a href="<?php echo esc_url( home_url( '/' ) ); ?>groups/" title="groups"><?php printf( __( 'Groups', 'simpleportal' ) ); ?></a></li><?php else : ?><?php endif; ?>
					<?php if ( bp_is_active('activity')) : ?><li><a href="<?php echo esc_url( home_url( '/' ) ); ?>activity/" title="activity"><?php printf( __( 'Activity', 'simpleportal' ) ); ?></a></li><?php else : ?><?php endif; ?>
					<li><a href="<?php echo esc_url( home_url( '/' ) ); ?>members/" title="members"><?php printf( __( 'Members', 'simpleportal' ) ); ?></a></li>
					<?php else : ?><?php endif; ?>
					<li class="divider"></li>
					<li><a href="<?php echo wp_logout_url( home_url() .'/login' ); ?>" title="log out"><?php printf( __( 'Log Out', 'simpleportal' ) ); ?></a></li>
				  </ul>
				</li>
			</ul>
			<?php elseif (is_user_logged_in() && !current_user_can('delete_others_pages')) : ?>
			<ul class="nav navbar-nav navbar-right">
				<li><a href="#"><?php global $current_user; get_currentuserinfo(); ?><?php echo 'Welcome: ' . $current_user->display_name . "\n"; ?></a></li>
				<li class="dropdown">
				 <a href="#" class="dropdown-toggle" data-toggle="dropdown"><span class="glyphicon glyphicon-user"></span> Menu <b class="caret"></b></a>
				  <ul class="dropdown-menu">
					<?php if (function_exists('bp_is_active')) : ?>
					<li><?php global $bp; ?><a href="<?php echo $bp->loggedin_user->domain . "\n"; ?>" title="your profile"><?php printf( __( 'Profile', 'simpleportal' ) ); ?></a></li>
					<?php if ( bp_is_active('messages')) : ?><li><a href="<?php echo bp_core_get_user_domain(bp_loggedin_user_id()); ?>messages" title="messages"><?php simpleportal_current_user_notification_count(); ?> - <?php printf( __( 'Messages', 'simpleportal' ) ); ?></a></li><?php else : ?><?php endif; ?>
					<?php if ( bp_is_active('groups')) : ?><li><a href="<?php echo esc_url( home_url( '/' ) ); ?>groups/" title="groups"><?php printf( __( 'Groups', 'simpleportal' ) ); ?></a></li><?php else : ?><?php endif; ?>
					<?php else : ?><?php endif; ?>
					<li class="divider"></li>
					<li><a href="<?php echo wp_logout_url( home_url() .'/login' ); ?>" title="log out"><?php printf( __( 'Log Out', 'simpleportal' ) ); ?></a></li>
				  </ul>
				</li>
			</ul>
			<?php else : ?>
			<?php endif; ?>
			</div>
			</nav><!-- /.navbar -->
		
			<div class="clear"></div>
			<div class="wrapperbig">
			<div class="container">
			<div class="masthead">
			<?php if (has_nav_menu('top-right') ) : ?>
			<?php
			wp_nav_menu( array(
			'menu'              => 'top-right',
			'theme_location'    => 'top-right',
			'depth'             => 1,
			'container'         => false,
			'menu_class'        => 'nav nav-pills pull-right',
			'fallback_cb' 		=> 'wp_page_menu',
			'walker'            => new wp_bootstrap_navwalker())
			);
			?>
			<?php else : ?>
			<?php wp_nav_menu( array( 'theme_location' => 'top-right', 'depth' => 1, 'fallback_cb' => false, 'menu_class' => 'nav nav-pills pull-right' ) ); ?>
			<?php endif; ?>
			<img class="img-thumbnail" src="<?php header_image(); ?>" height="<?php echo get_custom_header()->height; ?>" width="<?php echo get_custom_header()->width; ?>" alt="" />
		</div>
				<div class="clear"></div>
	
				
<!-- /primary navbar -->

<!-- /navigation -->
     <div class="navbar navbar-default" role="navigation">
        <div class="container-fluid">
          <div class="navbar-header">
            <button type="button" class="navbar-toggle" data-toggle="collapse" data-target=".navbar-collapse">
              <span class="sr-only">Toggle navigation</span>
              <span class="icon-bar"></span>
              <span class="icon-bar"></span>
              <span class="icon-bar"></span>
            </button>
          </div>
          <div class="navbar-collapse collapse">
			<?php if (current_user_can('delete_others_pages') ) : ?>
				<?php if (has_nav_menu('admin') ) : ?>
					<?php
					wp_nav_menu( array(
					'theme_location' => 'admin',
					'depth' => 2,
					'container' => false,
					'fallback_cb' => 'wp_page_menu',
					'menu_class' => 'nav navbar-nav',
					//Process nav menu using our custom nav walker
					'walker' => new wp_bootstrap_navwalker())
					);
					?>
					<?php else : ?>
					<div id="navbar" class="navbar">
					<nav id="site-navigation" class="navigation main-navigation" role="navigation">
					<h3 class="menu-toggle"><?php _e( 'Menu', 'simpleportal' ); ?></h3>
					<?php wp_nav_menu( array( 'theme_location' => 'admin', 'depth' => 0, 'menu_class' => 'nav-menu' ) ); ?>
					</nav><!-- #site-navigation -->
					</div><!-- #navbar -->
					<?php endif; ?>

			<?php elseif (current_user_can('contributor') ) : ?>
				<?php if (has_nav_menu('contributor') ) : ?>
					<?php
					wp_nav_menu( array(
					'theme_location' => 'contributor',
					'depth' => 2,
					'container' => false,
					'fallback_cb' => 'wp_page_menu',
					'menu_class' => 'nav navbar-nav',
					//Process nav menu using our custom nav walker
					'walker' => new wp_bootstrap_navwalker())
					);
					?>
					<?php else : ?>
										<div id="navbar" class="navbar">
					<nav id="site-navigation" class="navigation main-navigation" role="navigation">
					<h3 class="menu-toggle"><?php _e( 'Menu', 'simpleportal' ); ?></h3>
					<?php wp_nav_menu( array( 'theme_location' => 'contributor', 'depth' => 0, 'menu_class' => 'nav-menu' ) ); ?>
					</nav><!-- #site-navigation -->
					</div><!-- #navbar -->
					<?php endif; ?>
			<?php elseif (current_user_can('subscriber') ) : ?>
				<?php if (has_nav_menu('subscriber') ) : ?>
					<?php
					wp_nav_menu( array(
					'theme_location' => 'subscriber',
					'depth' => 2,
					'container' => false,
					'fallback_cb' => 'wp_page_menu',
					'menu_class' => 'nav navbar-nav',
					//Process nav menu using our custom nav walker
					'walker' => new wp_bootstrap_navwalker())
					);
					?>
					<?php else : ?>
										<div id="navbar" class="navbar">
					<nav id="site-navigation" class="navigation main-navigation" role="navigation">
					<h3 class="menu-toggle"><?php _e( 'Menu', 'simpleportal' ); ?></h3>
					<?php wp_nav_menu( array( 'theme_location' => 'subscriber', 'depth' => 0, 'menu_class' => 'nav-menu' ) ); ?>
					</nav><!-- #site-navigation -->
					</div><!-- #navbar -->
					<?php endif; ?>
			<?php elseif (current_user_can('author') ) : ?>
				<?php if (has_nav_menu('author') ) : ?>
					<?php
					wp_nav_menu( array(
					'theme_location' => 'author',
					'depth' => 2,
					'container' => false,
					'fallback_cb' => 'wp_page_menu',
					'menu_class' => 'nav navbar-nav',
					//Process nav menu using our custom nav walker
					'walker' => new wp_bootstrap_navwalker())
					);
					?>
					<?php else : ?>
										<div id="navbar" class="navbar">
					<nav id="site-navigation" class="navigation main-navigation" role="navigation">
					<h3 class="menu-toggle"><?php _e( 'Menu', 'simpleportal' ); ?></h3>
					<?php wp_nav_menu( array( 'theme_location' => 'author', 'depth' => 0, 'menu_class' => 'nav-menu' ) ); ?>
					</nav><!-- #site-navigation -->
					</div><!-- #navbar -->
					<?php endif; ?>
			<?php elseif (!is_user_logged_in()) : ?>
			<?php
					wp_nav_menu( array(
					'theme_location' => 'primary',
					'depth' => 2,
					'container' => false,
					'fallback_cb' => 'wp_page_menu',
					'menu_class' => 'nav navbar-nav',
					//Process nav menu using our custom nav walker
					'walker' => new wp_bootstrap_navwalker())
					);
					?>
					<?php else : ?>
										<div id="navbar" class="navbar">
					<nav id="site-navigation" class="navigation main-navigation" role="navigation">
					<h3 class="menu-toggle"><?php _e( 'Menu', 'simpleportal' ); ?></h3>
					<?php wp_nav_menu( array( 'theme_location' => 'primary', 'depth' => 0, 'menu_class' => 'nav-menu' ) ); ?>
					</nav><!-- #site-navigation -->
					</div><!-- #navbar -->
					<?php endif; ?>
			</div>
			</div>
			</div>
			
			</div>
			
			<!-- /main layout divs -->
			
	<div id="wrap">
	
			
	<div class="container">		

		
	<div id="wrapcontainer">
	
	<!-- /content divs start -->
	
	<div id="container" role="main">

		<?php if (is_404() || is_page_template('public-full.php') || is_page_template('public-subpages-4-column.php') || is_page_template('public-subpages.php') || is_page_template('gallery-public.php')) : ?>
		<div class="col-md-12">
		<?php elseif (function_exists('bp_is_active') && bp_is_activation_page() || function_exists('bp_is_active') && bp_is_user() || function_exists('bp_is_active') && bp_is_register_page() || function_exists('bp_is_active') &&  bp_is_current_component( 'groups' ) || function_exists('bp_is_active') && bp_is_current_component( 'activity' ) || function_exists('bp_is_active') && bp_is_current_component( 'members' ) || class_exists('bbPress') && is_bbpress()) : ?>
		<div class="col-md-12">
		<?php else : ?>

		<!-- /side menu -->
		<div class="row">
		<div class="col-md-3">
	
	<!-- /vertical navbar -->
		<nav role="navigation">
			<?php if (has_nav_menu('vertical') ) : ?>
			<?php
			wp_nav_menu( array(
			'theme_location' => 'vertical',
			'depth' => 1,
			'container' => false,
			'menu_class' => 'nav nav-pills nav-stacked',
			//Process nav menu using our custom nav walker
			'walker' => new wp_bootstrap_navwalker())
			);
			?>
			<?php else : ?>
			<?php wp_nav_menu( array( 'theme_location' => 'vertical', 'depth' => 1, 'fallback_cb' => false, 'menu_class' => 'nav nav-pills nav-stacked' ) ); ?>
			<?php endif; ?>
		</nav>
		
		
		<div class="clear"></div>
			<?php if ( !function_exists('dynamic_sidebar')
			|| !dynamic_sidebar('Primary Sidebar') ) : ?>
			<?php endif; ?>
			
			<ul class="nav nav-pills nav-stacked">
			<?php wp_list_pages( array('title_li'=>'','include'=>simpleportal_get_post_top_ancestor_id()) ); ?>
			<?php wp_list_pages( array('title_li'=>'','depth'=>1,'child_of'=>simpleportal_get_post_top_ancestor_id()) ); ?>
			</ul>
			
			<?php global $post;
			
			$children = wp_list_pages('title_li=&depth=1&child_of='.$post->ID.'&echo=0');
			if ($children && is_page() && $post->post_parent) {
			echo "<h3>In this section</h3><ul class=\"nav nav-pills nav-stacked \">";
			echo $children;
			echo "</ul>";
			}
			
			?>
			
		</div>
		
		<!-- /content -->
	
		<div class="col-md-9">
		
		<?php endif; ?>
		
		<?php if (is_404() || is_page_template('public-full.php') || is_page_template('public-subpages-4-column.php') || is_page_template('public-subpages.php') || is_page_template('gallery-public.php')) : ?>
		<div class="full-width">
		<?php elseif (function_exists('bp_is_active') && bp_is_activation_page() || function_exists('bp_is_active') && bp_is_user() || function_exists('bp_is_active') && bp_is_register_page() || function_exists('bp_is_active') &&  bp_is_current_component( 'groups' ) || function_exists('bp_is_active') && bp_is_current_component( 'activity' ) || function_exists('bp_is_active') && bp_is_current_component( 'members' ) || class_exists('bbPress') && is_bbpress()) : ?>
	<div class="full-width">	
	<?php else : ?>
	<div class="post">
	<?php endif; ?>
		
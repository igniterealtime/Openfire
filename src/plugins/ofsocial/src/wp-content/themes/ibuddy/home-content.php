<?php
/*
 * Main Home page content file.
 *
 * This file is called when a page is using Home page template.
 *
 * @package iBuddy
 */
?>

	<!-- Avatars --> 
	<?php if (of_get_option('groups_avatars')): ?> 
			<div id="intro-mem-home">
			<?php if ( bp_is_active('groups') ) : ?>
      				<h2><?php _e(of_get_option('groups_type_select') . '&nbsp;Groups' , 'ibuddy'); ?></h2>
				<?php if ( bp_has_groups('max=6&type=' . of_get_option('groups_type_select')) ) : ?>       
					<?php while ( bp_groups() ) : bp_the_group(); ?>
			
                        			<div class="item-avatar">
							<a href="<?php bp_group_permalink() ?>"><?php bp_group_avatar('type=full&width=150&height=150') ?></a>
						</div><!-- .item-avatar -->
					
                    			<?php endwhile; ?>
				<?php endif; /* loop */?>
			<?php else: /* Groups Check */?>
				<h2><?php _e('Please Activate The Groups Component', 'ibuddy'); ?></h2>
			<?php endif; /* Groups Check */?>
        		</div><!-- #intro-mem-home -->
	<!-- Image --> 
	<?php elseif (of_get_option('home_image')) : ?>

		<div id="intro-mem-home">
			<img class="reg-image" src="<?php echo of_get_option('home_image_upload'); ?>" />
		</div><!-- #intro-mem-home -->
	
	<!-- Message --> 
	<?php elseif (of_get_option('home_message')) : ?>
		<div id="intro-mem-home">
			<h2><?php echo of_get_option('home_message_title') ?></h2>
			<p><?php echo of_get_option('home_message_content') ?></p>
		</div><!-- #intro-mem-home -->
	<?php endif; ?>
	<!-- Login Form -->
		<div id="home-login">
			<h2><?php _e('Login', 'ibuddy') ?></h2>
					<?php if((!is_user_logged_in()) && ($_GET['login'] === 'failed')) : ?>
					<div class="error">
						<p><?php _e('Invalid User Name or Password, Please try again','ibuddy'); ?></p>
					</div>
					<?php endif; ?>
				<?php wp_login_form(); ?>
			<h3><a href="<?php echo bp_signup_page() ?>"><?php _e('or signup for an account >>>', 'ibuddy') ?></a></h3>
</div>

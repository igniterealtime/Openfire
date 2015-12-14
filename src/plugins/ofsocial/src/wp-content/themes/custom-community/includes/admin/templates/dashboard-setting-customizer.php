<?php
/**
 * cc2 Template: Dashboard/Setting/Customizer
 *
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 */
 
?>

<div class="section section-customizer-info">

	<h3>Play With The Customizer</h3>

	<p>Use your theme out of the box or customize its appearance.</p>
	
	<p><a href="<?php echo get_admin_url(); ?>customize.php" class="button">Visit Customizer</a></p>
    
	<p class="cc2tip-13inch" style="color: #999;"><em>Tip: Using a 13&quot; display?<br />
	When the Customizer is loaded, click somewhere into the screen and press the key combination <strong class="keys">[Ctrl] + [-]</strong> once.<br />
	This way you zoom out to 90% and you will be able to see the sidebar again ;)</em></p>
	
	
	<?php if( !empty($is_first_run) ) : ?>
	<h3>Add Your Content</h3>

	<p>Okay, time to add your real content: posts, pages, menus.</p>

	<p>
		<a href="<?php //echo get_admin_url(); ?>post-new.php/" class="button">Add Post</a>
		<a href="<?php //echo get_admin_url(); ?>post-new.php?post_type=page/" class="button">Add Page</a>
		<a href="<?php //echo get_admin_url(); ?>nav-menus.php/" class="button">Manage Menus</a>
	</p>
	
<!--
	<p style="color: #999;"><em>Hey, wanna import some ready-to-go <a href="#" title="coming!">starting point?</a></em></p>
-->
	<?php endif; ?>
</div>
<div class="section section-slideshow-info">

    <h3>Setup Your Slideshow</h3>

    <p>If you haven't already, add your own images and finalize your slider!</p>

    <p><a href="<?php echo get_admin_url( get_current_blog_id(), apply_filters('cc2_admin_settings_url', 'themes.php') . '?page=cc2-settings&tab=slider-options'  ); ?>" class="button">Setup Slideshow</a></p>
</div>


    <!--

    <h3>Extend</h3>

	<p>Get selected free and premium extensions here:</p>
    <br />
	<p><span class="button button-disabled disabled">Coming Soon!</span></p>
    <br />
	<p style="color: #999;"><em><b>Soon you find a well-selected ThemeKraft Toolkit.</b> <br>
	Let us know which plugins work great with the theme <br>
	so we can check them out and add them to the list.</em></p>
	<br />
    <br />

    <p>Browse the free Plugin Repository of wordpress.org: </p>
    <br />
    <p><a href="<?php //echo get_admin_url(); ?>plugin-install.php" class="button">Browse Free WordPress Plugins</a></p>
    -->


<div class="section section-get-pro">
	<hr />

	<h3>Taking it seriously?</h3>

	<p>If you want to make it professional - get the pro version.</p>

	<p><a href="http://themekraft.com/store/custom-community-2-premium-pack/" class="button-primary">Get Pro Now</a></p>
  
</div>


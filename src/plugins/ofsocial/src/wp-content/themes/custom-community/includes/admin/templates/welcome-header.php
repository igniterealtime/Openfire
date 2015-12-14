<?php
/**
 * cc2 Welcome Screen header template
 *
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0.25
 */
 
?>
<div class="wrap about-wrap">

	<div id="icon-themes" class="icon32"><br /></div>

	<h1><?php printf( __('Welcome to Custom Community %s', 'cc2'), $major_version ); ?></h1>

	<div class="about-header" style="background: url(<?php echo get_template_directory_uri() . '/screenshot.png'; ?>) center 10% no-repeat; display: block; height: 250px; max-width: 80%; margin: 2em 0; border: 10px solid #efefef; box-shadow: 1px 1px 4px #aaa">
	</div>

	<div class="about-text cc2-about-text">
		<p><?php printf( __('Welcome and thanks for installing Custom Community %s!', 'cc2'), $version ); ?></p>
	</div>
	
	<p class="cc2-welcome-actions">
		<?php proper_submit_button( __('Customize Theme', 'cc2'), 'primary link', 'welcome-action-customize', false, array('href' => $theme_customize_url ) ); ?> 
		<?php proper_submit_button( __('Settings', 'cc2'), 'primary link', 'welcome-action-settings', false, array('href' => $theme_settings_url ) ); ?> 
		<?php proper_submit_button( __('Documentation', 'cc2'), 'primary link', 'welcome-action-rtfm', false, array('href' => $theme_rtfm_url ) ); ?> 
		
		<a href="https://twitter.com/share" class="twitter-share-button" data-url="http://f2w.de/cc2" data-text="Professional-grade responsive, Bootstrap-based, extremely adaptable WordPress Customizer theme." data-via="Themekraft" data-size="large" data-hashtags="Themekraft">Tweet</a>
		<script>!function(d,s,id){var js,fjs=d.getElementsByTagName(s)[0];if(!d.getElementById(id)){js=d.createElement(s);js.id=id;js.src="//platform.twitter.com/widgets.js";fjs.parentNode.insertBefore(js,fjs);}}(document,"script","twitter-wjs");</script>
	</p>
	
	

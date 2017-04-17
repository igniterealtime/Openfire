<?php
/**
 * Template: Theme Activation / Welcome screen
 * 
 * Generic screen which is being displayed after the theme activation. Should display options for: Importing old data, Customizer and also an "do this later" override.
 * @author Fabian Wolf
 * @since 2.0.26
 * @package cc2
 */

//new __debug( $current_theme, 'current theme object' );

/**
 * TODO: update script isn't functional yet!
 */

if( isset( $import_old_settings ) ) :
	unset( $import_old_settings );
endif;



if( !empty( $import_old_settings ) ) : ?>
	<div class="changelog">
		<div class="feature-rest feature-section col two-col">

			<div class="cc2-about-text-import-old-data" id="welcome-import-old-data-text">
				<h2><?php _e('Import old settings', 'cc2'); ?></h2>
				
				<p><?php _e('Salvage and reuse settings, widgets, and other data from earlier incarnations of Custom Community:', 'cc2'); ?></p>
				
			</div>
			
			<div class="cc2-about-text-import-old-data last-feature" id="welcome-import-old-data-action">
			
				<ul class="settings-list">
				<?php foreach( $import_old_settings as $strSettingKey => $strSettingDescription) : ?>
					<li>
						
						
						<input type="checkbox" name="import_old_setting[]" value="<?php echo $strSettingKey; ?>" id="<?php echo sanitize_key( 'old-setting-'.$strSettingKey ); ?>" />
						<label for="<?php echo sanitize_key( 'old-setting-'.$strSettingKey ); ?>"><?php echo $strSettingDescription; ?></label>
					</li>
				<?php endforeach; ?>
				</ul>
				
				<p><?php proper_submit_button( __('Import selected data', 'cc2'), 'primary large', 'step', false, array('value' => 'update-script') ); ?></p>
			</div>

		</div>
	</div><!-- /.feature-section -->

<?php endif; ?>


	<!-- slideshow + color schemes + customizer options -->
	
	<div class="changelog about-integrations">
		<div class="feature-rest feature-section col three-col">
			<div class="welcome-responsive-layout-text">
				<h2><?php _e('Responsive Layout', 'cc2'); ?></h2>
				
				<p>Responsive, easy customizable layout.</p>
				
				<ul class="feature-list">
					<li>Latest versions of <a href="http://www.getbootstrap.com/">Bootstrap</a> and <a href="http://fortawesome.github.io/Font-Awesome/icons/">Font Awesome</a></li>
					<li>Complete Bootstrap framework with all the bells and whistles</li>
					<li>Proper multi-level dropdown menus</li>
					<li class="no-call-to-action">Adjust the sidebar and content width</li>
				</ul>

			</div>
				
			<div class="welcome-slideshow-text">
				<h2><?php _e('Slideshow', 'cc2'); ?></h2>
				<p>Completely rebuild, easy to use, responsive Slideshow.</p>
				
				<ul class="feature-list">
					<li>Simple user interface</li>
					<li>Post and image slides</li>
					<li>Several effects, adjustable per slide</li>
					<li>Clean &amp; responsive</li>
					<li class="call-to-action"><a href="<?php echo $theme_settings_url . '&amp;tab=slider-options'; ?>">See for yourself</a></li>
				</ul>
			</div><!-- /.welcome-slideshow-text -->
			
			<div class="welcome-customizer-text last-feature">
				<h2><?php _e('Theme Customizer', 'cc2'); ?></h2>
				
				<p>Use the native WordPress Theme Customization system to customize your site to YOUR choices.</p>
				
				<ul class="feature-list">
					<li>Instant preview of your changes</li>
					<li>150+ settings</li>
					<li>Optional settings for a few selected plugins, including WooCommerce and BuddyPress</li>
					<li class="call-to-action"><a href="<?php echo $theme_customize_url; ?>">Customize now!</a></li>
				</ul>
			
			</div><!-- /.welcome-customizer-text -->
			
			<!--<div class="welcome-color-schemes-text last-feature">
				<h2><?php _e('Color Schemes', 'cc2'); ?></h2>
				
				<p>All-new Color Schemes system, enabling you to quickly switch the color foundation of your theme.</p>
				
				<ul class="image-list">
					<li><img src="image01.jpg" /></li>
					<li><img src="image02.jpg" /></li>
					<li><img src="image03.jpg" /></li>
				</ul>
			
			</div>--><!-- /.welcome-color-schemes-text -->
	
			
			
		
		</div>
	</div>
	
	<hr class="clear" />
	
	<div class="changelog" id="welcome-skip-all" style="display: block">
		
		
		<h3>Configure Later:</h3>

		
		
		<p><?php printf( __('No need for this hassle right now? Then <a href="%s">just skip it</a> - all the options are accessible in the <a href="%s">CC Settings</a> as well.'), admin_url( 'index.php' ), $theme_settings_url  ); ?></p>
	
		<p><a href="<?php echo $theme_return_url; ?>" class="link-return"><?php _e('Return to the dashboard', 'cc2'); ?></a></p>
		
<?php /*
		<p><a href=""><?php _e('Return to the dashboard!', 'cc2'), admin_url('index.php'), 'style' => 'font-size: 1.5em; font-weight: normal; height: 3em; line-height: 2.9em', 'icon' => 'dashicons-xit' ) ); ?></p>
*/ ?>
	</div>


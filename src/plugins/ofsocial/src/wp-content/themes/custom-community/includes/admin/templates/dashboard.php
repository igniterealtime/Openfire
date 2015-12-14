<?php
/**
 * cc2 Template: Dashboard / Overview
 *
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 */
?>
<div class="wrap">

	<div id="icon-themes" class="icon32"><br /></div>
	
	<h2><?php _e('Custom Community', 'cc2'); ?> <em><?php _e('Settings', 'cc2'); ?></em></h2>

	<div class="cc2-support-wrap message updated" style="padding: 10px 2% 12px 2%; overflow:auto; float: none; clear: both; width: 96%; max-width: 96%;">

		<div id="cc2-documentation" style="float: left; overflow: auto; border-right: 1px solid #ddd; padding: 0 20px 0 0;">
			
			<h3><span>Get Support.</span><?php do_action( 'cc2_support_add_title' ); ?></h3>
			
			<p><?php do_action( 'cc2_support_add' ); ?> <a id="cc2_get_personal_help" class="button button-primary" href="#" title="Get personal help by the theme authors and write us right from your backend - as soon as you purchase the CC2 Premium Pack." style="margin-right: 3px;">Personal Help</a> <a href="<?php echo apply_filters( 'cc2_rtfm_url','https://github.com/Themekraft/Custom-Community/wiki' ); ?>" class="button secondary" title="Custom Community 2 Documentation">Documentation</a></p>
		</div>

		<div id="cc2_ideas" style="float: left; overflow: auto; padding: 0 20px 0 20px; border-right: 0px solid #ddd;">
			<h3>Contribute Your Ideas.</h3>
			
			<p><a class="button button-secondary" href="https://themekraft.zendesk.com/hc/communities/public/topics/200001362-Custom-Community-2-0-Ideas" target="_new">Visit Ideas Forums</a></p>
		</div>

		<div id="cc2_forums" class="wpforums" style="display: none; float: left; overflow: auto; padding: 0 20px 0 20px;">
			<h3>Learn, Share, Discuss.</h3>
			<p><a class="button button-secondary" href="http://wordpress.org/support/theme/custom-community" target="_new">Visit WordPress Forum</a></p>
		</div>

	</div>

	<div id="cc2_get_more" class="message updated premium_extension" style="margin: 20px 0; padding: 10px 20px; border-left: 4px solid red; transition: background-color 200ms ease-out 1s; -webkit-transition: background-color 200ms ease-out 1s; -moz-transition: background-color 200ms ease-out 1s; -o-transition: background-color 200ms ease-out 1s;">
		<p style="font-size: 17px;"><em>&raquo; Get all features and <strong>personal help by the theme authors</strong> with the <a style="color: #dd3333;" href="http://themekraft.com/store/custom-community-2-premium-pack/" target="_new">CC2 Premium Pack.</a></em></p>
	</div>


	<?php 
	/*global $wp_settings_sections, $wp_settings_fields, $new_whitelist_options;
	
	new __debug(
		array(
			'current_tab' => $current_tab,
			'setting sections' => $setting_sections,
			'wp_settings_sections' => $wp_settings_sections,
			'new_whitelist_options' => $new_whitelist_options,
			'wp_settings_fields' => $wp_settings_fields
		),
		'cc2 slide: dashboard template' 
	);*/
	
	// show tabs for switching to different setting areas
	if( isset( $setting_sections ) ) { 
		$current_settings_group = false;
			
		?>
		
	<h2 class="nav-tab-wrapper">
		
		<?php foreach( $setting_sections as $tab_slug => $setting_data ) {
			$current_tab_class = array('nav-tab');
			
			if( $current_tab == $tab_slug ) {
				//new __debug( array( 'current_tab' => $tab_slug, 'current_data' => $setting_data ), 'set current tab' );
				
				$current_tab_class[] = 'nav-tab-active';
				$current_settings_group = $setting_data['settings_slug'];
				
				$current_tab_container_class = array('tab-container', 'tab-container-' . $tab_slug );
			}
			
			?>
			<a href="<?php echo admin_url( apply_filters('cc2_tab_admin_url', 'themes.php?page=cc2-settings&tab=' . $tab_slug ) ); ?>" class="<?php echo implode(' ', $current_tab_class); ?>"><?php echo $setting_data['title']; ?></a>
		<?php } ?>
		
	</h2>

	<form method="post" action="<?php admin_url('options.php'); ?>"<?php 
	if( !empty( $current_tab_container_class ) ) {
		echo ' class="' . implode(' ', $current_tab_container_class ) . '"';
	} ?>>
		<?php
		
		/**
		 * Proper structure:
		 * - tab / page
		 * 		- section (1 .. x)
		 * 
		 * 'getting-started => array( 'title' => 'Getting Started', 'sections_slug' => 'cc2_options' );
		 */
		
		//new __debug( array( 'current_tab' => $current_tab, 'current_settings_group' => $current_settings_group, 'current_class' => $current_class) );
		
		if( !empty($current_settings_group) ) {
			//new __debug( 'loading settings of ' . $current_settings_group );
			
			wp_nonce_field( 'update-options');
			settings_fields( $current_settings_group );
			do_settings_sections( $current_settings_group );
		}
		
		
		/*
		if( $active_tab == 'getting-started' ) {
			wp_nonce_field( 'update-options' );
			settings_fields( 'cc2_options' );
			do_settings_sections( 'cc2_options' );

		} else {
			wp_nonce_field( 'update-options' );
			settings_fields( 'cc2_slider_options' );
			do_settings_sections( 'cc2_slider_options' );
		} // end if/else
		*/


		?>
	</form>
	<?php } ?>

</div>

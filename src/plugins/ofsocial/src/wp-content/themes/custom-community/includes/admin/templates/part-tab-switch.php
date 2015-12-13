<?php
/**
 * Reusable Template Part: Tab switch
 */


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
		
		if( empty( $admin_file ) ) {
			$admin_file = 'themes.php';
		}
		
		if( empty( $admin_page ) ) {
			$admin_page = 'cc2-settings';
		}
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
			
			/*
			?>
			<a href="<?php echo admin_url( apply_filters('cc2_tab_admin_url', 'themes.php?page=cc2-settings&tab=' . $tab_slug ) ); ?>" class="<?php echo implode(' ', $current_tab_class); ?>"><?php echo $setting_data['title']; ?></a>
		<?php */
		?>
			<a href="<?php echo admin_url( apply_filters('cc2_tab_admin_url', $admin_file . '?page='.$admin_page.'&tab=' . $tab_slug ) ); ?>" class="<?php echo implode(' ', $current_tab_class); ?>"><?php echo $setting_data['title']; ?></a>
		<?php
		
		} ?>
		
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

		?>
	</form>
	<?php 
	} ?>

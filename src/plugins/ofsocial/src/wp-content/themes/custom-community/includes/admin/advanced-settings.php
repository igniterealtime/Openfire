<?php
/**
 * Advanced Settings Tab, formerly known as "Custom Styling" (till b5)
 * 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 */



if( !class_exists('cc2_Admin_AdvancedSettings') ) :


/**
 * NOTE: Seperate init, because we are also using WP ajax calls
 */

class cc2_Admin_AdvancedSettings {
	var $classPrefix = 'cc2_advanced_settings_',
		$className = 'Advanced Settings',
		$optionNameCompat = 'cc2_custom_styling',
		$optionName = 'cc2_advanced_settings',
		$arrKnownFields = array(
			'custom_css' => array( 'type' => 'text_css', 'default' => ''),
			
			'headjs_type' => array('type' => 'text', 'default' => 'redux'),
			'headjs_url' => array('type' => 'url', 'default' => ''),
			
			/**
			 * Bootstrap-related
			 */
			'load_smartmenus_js' => array('type' => 'int', 'default' => 0 ),
			'load_scroll_top' => array( 'type' => 'int', 'default' => 0 ),
			'load_hover_dropdown_css' => array( 'type' => 'int', 'default' => 0 ),
			
			/**
			 * Debug-related
			 */
			
			'load_test_js' => array('type' => 'int', 'default' => 0),
			
			
		);
	
	/**
	 * Plugin instance.
	 *
	 * @see get_instance()
	 * @type object
	 */
	protected static $instance = NULL;
		
	/**
	 * Implements Factory pattern
	 * Strongly inspired by WP Maintenance Mode of Frank Bueltge ^_^ (@link https://github.com/bueltge/WP-Maintenance-Mode)
	 * 
	 * Access this plugins working instance
	 *
	 * @wp-hook after_setup_theme
	 * @return object of this class
	 */
	public static function get_instance() {

		NULL === self::$instance and self::$instance = new self;

		return self::$instance;
	}
	
	function __construct() {
		// init variables
		//$this->upgrade_styling_settings();
		
		// register required settings, sections etc.
		add_action( 'admin_init', array( $this, 'register_admin_settings' ), 11 );
		
		// 
		//add_action('admin_enqueue_scripts', array( $this, 'init_admin_js' ) );

		// save settings
		if( current_user_can( 'manage_options' ) ) {
		
			add_action( 'wp_ajax_' . $this->classPrefix . 'save', array( $this, 'update_settings') );
		}
		//add_action( 'wp_ajax_nopriv_' . $this->classPrefix . 'save', array( $this, 'update_settings') );
		
		
	}


	/**
	 * Update settings
	 */
	 
	function update_settings() {
		// get known fields
		$arrKnownFields = apply_filters( $this->classPrefix . 'known_fields', $this->arrKnownFields );
		
		// get current settings
		$current_settings = $this->get_advanced_settings();
		$update_settings = $current_settings;
		
		/**
		 * TODO: Validation and sanitization is missing
		 */
		
		// update settings
				
		if( isset( $_POST['settings'] ) && !empty( $arrKnownFields) ) {
			foreach( $arrKnownFields as $strFieldName => $arrFieldTypeParams ) {
				// sanitize data (if possible)
				if( isset( $_POST['settings'][ $strFieldName ] ) ) {
					
					// rudimentary sanitation
					switch( $arrFieldTypeParams['type'] ) {
						case 'int':
						case 'integer':
							$update_settings[$strFieldName] = intval( $_POST['settings'][ $strFieldName ] );
							break;
						case 'bool':
						// convert truethy/falsy to boolean
							$update_settings[$strFieldName] = ( $_POST['settings'][ $strFieldName ] == 1 );
							break;
						case 'text_css':
						case 'css':
							$sanitized_setting = '';
							$sanitized_setting = apply_filters( 'cc2_pre_sanitize_css', $_POST['settings'][ $strFieldName ] );
							$sanitized_setting = cc2_Pasteur::sanitize_css( $sanitized_setting );
							
							$update_settings[ $strFieldName ] = wp_filter_nohtml_kses( $sanitized_setting );
							
							break;
						default:
							$update_settings[$strFieldName] = $_POST['settings'][ $strFieldName ];
							break;
					}
				}
			}
		}

		


		// $settings_array[ $field_name ] = value => array( 'key' => array('meta' => 'value' , 'data' => true) );

		$this->update_advanced_settings( $update_settings );

		die();
	}


	public static function get_known_fields() {
		return $this->arrKnownFields;
	}

	/**
	 * Wrapper to avoid future fuck-ups and repetitive works (aka DRY!)
	 */

	function get_advanced_settings( $default = array() ) {
		return get_option( $this->optionName, $default ); 
	}
		
	function update_advanced_settings( $settings = array() ) {
		$return = false;
		
		if( !empty( $settings ) ) {
			$return = update_option( $this->optionName, $settings );
		}
			
		return $return;
	}


	/**
	 * Get default values for settings
	 */
	 
	function compile_settings_defaults( $arrFields = array() ) {
		$return = false;
		
		foreach( $this->arrKnownFields as $strFieldName => $arrFieldTypeParams ) {
			if( isset( $arrFields[ $strFieldName ] ) ) {
				$return[$strFieldName] = $arrFields[ $strFieldName ];
			} else {
				switch( $arrFieldTypeParams['type'] ) {
					case 'bool':
					// convert truethy/falsy to boolean
						$return[$strFieldName] = ( $arrFieldTypeParams['default'] == 1 );
						break;
					case 'int':
					case 'integer':
					default:
						$return[$strFieldName] = $arrFieldTypeParams['default'];
						break;
				}
			}
				
		}
		
		return $return;
	}
	

	/*
	 * Register the admin settings
	 * 
	 * @author Fabian Wolf
	 * @author Sven Lehnert
	 * @package cc2  
	 * @since 2.0
	 */ 
	 

	function register_admin_settings() {
		
		$strSettingsGroup = $this->classPrefix . 'options';
		$strSettingsPage = $strSettingsGroup;
		
		//new __debug( array( 'settings_group' => $strSettingsGroup, 'settings_page' => $strSettingsPage), 'cc2 custom styling: register_admin_settings fires');
		register_setting( $strSettingsGroup, $strSettingsGroup );
    
		// Settings fields and sections
		
		
		/*add_settings_section(
			'section_general', 
			'', 
			array( $this, 'admin_setting_general' ), // method callback 
			$strSettingsPage
		);*/
		
		add_settings_section(
			'section_general',
			'',
			'',
			$strSettingsPage
		);
		
			add_settings_field(
				$this->classPrefix . 'custom_css',
				'<strong>Custom CSS</strong>',
				array( $this, 'admin_setting_custom_css' ), /** method callback */
				$strSettingsPage,
				'section_general'
			);

			
			add_settings_field(
				$this->classPrefix . 'headjs',
				'<strong>HeadJS</strong>',
				array( $this, 'admin_setting_headjs' ), /* method callback */
				$strSettingsPage,
				'section_general'
			);

			
			
			add_settings_field(
				$this->classPrefix . 'bootstrap',
				'<strong>Bootstrap Features</strong>',
				array( $this, 'admin_setting_bootstrap_features' ), /* method callback  */
				$strSettingsPage,
				'section_general'
			);
			
			add_settings_field(
				$this->classPrefix . 'save',
				'',
				array( $this, 'admin_setting_save' ), /* method callback  */
				$strSettingsPage,
				'section_general'
			);
			
			
			
			/*add_settings_field(
				$this->classPrefix . 'edit',
				'<strong>Manage Your Slides</strong>',
				array( $this, 'admin_setting_edit_slideshow' ), // method callback
				$strSettingsPage,
				'section_general'
			);
			*/
		

	}
	
	/**
	 * Important notice on top of the screen
	 * 
	 * @author Sven Lehnert
	 * @package cc2  
	 * @since 2.0
	 */ 
	 
	
	function admin_setting_general() {
		
	}
	

	
	function admin_setting_custom_css() {
		// fetch required data
		$options = $this->get_advanced_settings();
		$custom_css = ( empty( $options['custom_css'] ) ? null : $options['custom_css'] );
		
		//new __debug( $options );
		
		// compatibility for beta5
		$old_settings = get_option( $this->optionNameCompat, false );
		$old_settings_updated = get_transient( 'cc2_custom_styling_upgraded', false );
		
		if( $old_settings_updated == false && !empty( $old_settings) && isset($old_settings['custom_css'] ) ) {
			$custom_css = $old_settings['custom_css'];
			
			//delete_option( $this->optionNameCompat );
			set_transient( 'cc2_custom_styling_upgraded', true );
			//echo 'old settings should be gone now';
		}
		
		
		
		// include template
		require_once( get_template_directory() . '/includes/admin/templates/custom-styling-edit.php' );
	}
	
	function admin_setting_headjs() {
		// fetch data
		$options = $this->get_advanced_settings( array('headjs_type' => 'redux', 'headjs_url' => '') );
		
		$options = $this->compile_settings_defaults( $options );

		//new __debug( array('preset' => $preset_options, 'current' => $options ), 'options');

		// template
		require( get_template_directory() . '/includes/admin/templates/advanced-settings-headjs.php' );
		
	}
	
	
	function admin_setting_bootstrap_features() {
		// fetch data
		$options = $this->get_advanced_settings();

		$options = $this->compile_settings_defaults( $options );
		
		// template
		require( get_template_directory() . '/includes/admin/templates/advanced-settings-bootstrap.php' );
		
	}
	
	// save button
	
	/**
	 * @hook '{classPrefix}button_save_changes'		Optionally filter or replace the save button.
	 */
	function admin_setting_save() {
		$strSaveChangesButton = get_submit_button( __('Save Changes', 'cc2'), 'primary', 'save-advanced-settings', false );
		
		echo apply_filters( $this->classPrefix . 'button_save_changes', $strSaveChangesButton );
	}
	
	/**
	 * Sanitize input before saving
	 * NOTE: Possibly obsolete <=> collission with update_settings
	 * 
	 */
	/*
	function sanitize_settings( $input = false ) {
		$return = $input;
		
		if( !empty( $input ) && is_array( $input) ) {
			foreach( $input as $strSettingName => $settingValue ) {
				
				
				$arrReturn[ $strSettingName ] 
				
			}
		}
		
		return $return;
	}*/
	
	/**
	 * Enqueue the needed JS _for the admin screen_
	 *
	 * FIXME: Needs to be loaded ONLY when showing the admin screen, but NOWHERE ELSE!
	 * TODO: Bundle into a seperate, independent call
	 * NOTE: Doesn't seem as if its being called anywhere, anyway.
	 * 
	 * @package cc2
	 * @since 2.0
	 */

	function init_admin_js($hook_suffix) {
		//new __debug( $hook_suffix, 'advanced settings: hook suffix' );
		
		wp_enqueue_script('consoledummy');
		wp_enqueue_media();
		
		
		
		wp_enqueue_script('jquery'); //load tabs
		wp_enqueue_script( 'custom-header' );
		wp_enqueue_script('jquery-ui-sortable'); //load sortable

		wp_enqueue_script('jquery-ui');
		wp_enqueue_script('jquery-ui-widget');
		wp_enqueue_script('jquery-ui-dialog');
		wp_enqueue_script('jquery-ui-button');
		wp_enqueue_script('jquery-ui-position');
		
		wp_enqueue_style( 'wp-jquery-ui-dialog' );		

		wp_enqueue_script('jquery-ui-tabs'); //load tabs
		wp_enqueue_script('cc-admin-js');
			
		//new __debug( $hook_suffix, 'hook_suffix' );
			
		/*
		wp_enqueue_style( 'cc_tk_zendesk_css');
		wp_enqueue_script( 'cc_tk_zendesk_js');*/

	}

}

add_action( 'after_setup_theme', array( 'cc2_Admin_AdvancedSettings', 'get_instance'), 12 );
//$none = cc2_Admin_CustomStyling::get_instance();


// endif class_exists
endif;

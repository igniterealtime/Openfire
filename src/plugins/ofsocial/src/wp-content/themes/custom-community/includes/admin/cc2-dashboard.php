<?php

//define('CC2_THEME_DEBUG', true );

add_action( 'after_setup_theme', array( 'cc2_Admin', 'get_instance'), 10 );
//$_cc2_admin_base = new cc2_Admin();

		//new __debug('cc2 theme settings loads');


class cc2_Admin {
	var $themePrefix = 'cc2_',
		$arrSections = array(
			'getting-started' => array(
				'title' => 'Getting Started',
				'settings_slug' => 'cc2_options',
			),
			'slider-options' => array(
				'title' => 'Slideshow',
				'settings_slug' => 'cc2_slider_options',
			),
			'advanced-settings' => array(
				'title' => 'Advanded Settings',
				'settings_slug' => 'cc2_advanced_settings_options',
			),
		),
		$strPageName = 'cc2-settings';
	
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
		$this->update_class_settings();
		
		// register admin settings
		add_action( 'admin_init', array( $this, 'register_admin_settings' ) );
		
		// add menu
		add_action( 'admin_menu', array( $this, 'add_admin_menu' ) );
		
		// register assets
		add_action( 'admin_enqueue_scripts', array( $this, 'init_assets' ), 10 );
		
		// enqueue base assets AFTER registering them (lower priority = loaded LATER)
		add_action( 'admin_enqueue_scripts', array( $this, 'load_assets' ), 11, 1 );
		
		//new __debug('cc2 theme settings loaded');
	}
	
	/**
	 * Fetch data from CC2_THEME_CONFIG - if defined
	 */
	
	protected function update_class_settings() {
		$return = false;
		
		//new __debug( $this->arrSections, 'section settings fires' );
		
		if( defined('CC2_THEME_CONFIG' ) ) {
			
			$config = maybe_unserialize( CC2_THEME_CONFIG );
			//new __debug( $config, 'config loaded' );
			
			
			if( isset( $config['admin_sections'] ) ) {
				$this->arrSections = $config['admin_sections'];
				
				//new __debug( $this->arrSections, 'updated class settings ');
				

				$return = true;
			}
			
			// load support settings
			
			if( isset( $config['support_settings'] ) ) {
				$this->arrSupportSettings = $config['support_settings'];
		
				//new __debug( $this->arrSections, 'updated class settings ');
				
				$currentUserData = get_userdata( get_current_user_id() );
				
				// default settings
				$strUserName = '';
				$strUserEMail = '';
				$strDescription = '';
				$strSubject = '';
				
				// retrieve user name
				
				
				if( !empty( $currentUserData->first_name ) || !empty( $currentUserData->last_name ) ) {
					if( !empty( $currentUserData->first_name ) ) {
						$strUserName = $currentUserData->first_name . ' ';
					}
					
					if( !empty( $currentUserData->last_name ) ) {
						$strUserName .= $currentUserData->last_name;
					}
				} else {
					if( isset( $currentUserData->display_name) ) {
						$strUserName = $currentUserData->display_name;
					}
				}
				
				if( !empty( $strUserName ) ) {
					$strUserName = sanitize_text_field( trim( $strUserName ) );
				}
				
				// retrieve user e-mail
				
				if( isset( $currentUserData->user_email ) ) {
					$strUserEMail = $currentUserData->user_email;
				}
				
				// retrieve site information
				$strSiteURL = get_home_url();
				$strPHPVersion = phpversion();
				$strVersion = '' . get_bloginfo('version');
				
				$strThemeVersion = ( defined( 'CC2_THEME' ) ? CC2_THEME : '' );
				
				if( empty( $strThemeVersion ) ) {
					$current_theme = wp_get_theme();
					
					if( is_child_theme() ) {
						$parent_theme = wp_get_theme( get_template() );
						unset( $current_theme );
						$current_theme = $parent_theme;
					}
					
					//$strThemeVersion = $current_theme->get('Version');
					$strThemeVersion = $current_theme->Version;
				}
				
		
				/**
				 * TODO: Add parent theme query
				 * 
				else {
					$current_theme = wp_get_theme(  );
				}*/
				
				if( !empty( $strVersion ) ) {
					$arrDescription[] = 'WP ' . $strVersion;
				}
				
				if( !empty( $strThemeVersion ) ) {
					$arrDescription[] = 'CC ' . $strThemeVersion;
				}
				
				if( !empty( $strPHPVersion ) ) {
					$arrDescription[] = 'PHP ' . $strPHPVersion;
				}

				// prefill text field
				if( !empty( $arrDescription ) ) {
					$strDescription = 'Please add your question(s) BEFORE the system information - ' . implode( ' / ', $arrDescription );
				}	
				
				// prefill fields
				$this->arrSupportSettings['zendesk']['prefill_fields'] = apply_filters('cc2_zendesk_prefill_fields', array(
					'email' => $strUserEMail,
					'name' => $strUserName,
					'description' => $strDescription,
					'subject' => $strSubject,
				) );
				
				$this->arrSupportSettings['userinfo'] = $currentUserData;
			
			}
		}
		
		return $return;
	}
	
	
	function add_admin_menu() {

		add_theme_page( 'CC Settings', 'CC Settings', 'edit_theme_options', $this->strPageName, array( $this, 'admin_page_screen' ) );
		
		//'cc2-settings'
		
		// syntax: add_theme_page( $page_title, $menu_title, $capability, $menu_slug, $function = $callback ) 
		
	}
	
	
	function register_admin_settings() {
		$step = 1;
		
		$is_theme_activated = get_transient('cc2_theme_active', false );
		$strSettingsPage = 'cc2_options';
		$strSection = 'section_general';
		
		
		register_setting( 'cc2_options', 'cc2_options' );
		
		// Settings fields and sections
		add_settings_section( $strSection, '', array( $this, 'setting_page_general'), 'cc2_options' );
		
		// add_settings_section( $id, $title, $callback, $page = $menu_slug )
		
		// updates pending or initial run
		
		//if( $is_theme_activated ) {
		/*
			add_settings_field(
				'cc2_theme_activation',
				sprintf( '<strong>Step %d</strong><br /><em>Import old data</em>', $step ),
				array( $this, 'setting_page_pending_updates' ),
				'cc2_options',
				'section_general' 
			);
			
			$step++;
		//}
		
		*/
		
		/**
		 * Syntax:
		 * array(
		 * 		'id' => 'field_id',
		 * 		'title' => 'optional title',
		 * 		'callback' => array( $object | 'myClass', 'method' ),
		 * 		'page' => false | '' | 'some_string',
		 * 		'params' => array('additional' => 'parameters', 'for_the' => 'callback' )
		 * )
		 * 
		 * If $page is empty (= false; or not set), the default will be used (ie. section_general)
		 * 
		 */
		$arrAdditionalFields = array();
		/**
		 * NOTE: Test data. Uncomment = tests the additional fields "function" ;)
		 * 
		$arrAdditionalFields = array(
			array(
				'id' => 'cc2_theme_activation',
				'title' => __('<strong>Step %d</strong><br /><em>Import old data</em>'),
				'callback' => array( $this, 'setting_page_pending_updates' ),
				'page' => 'cc2_options',
			),
		);
		*/
		
		
		$arrAdditionalFields = apply_filters('cc2_section_general_add_fields', $arrAdditionalFields );
		if( !empty( $arrAdditionalFields ) ) {
			
			foreach( $arrAdditionalFields as $arrAddFieldData ) {
				
				if( isset( $arrAddFieldData['id'] ) && isset( $arrAddFieldData['callback'] ) ) {
					$strAddFieldPage = ( !empty( $arrAddFieldData['page'] ) ? $arrAddFieldData['page'] : $strSettingsPage );
					
					$strAddFieldTitle = ( !empty( $arrAddFieldData['title'] ) ? $arrAddFieldData['title'] : '<span class="empty-field-title"></span>' );
					
					if( !empty( $arrAddFieldData['title']) && strpos( $arrAddFieldData['title'], '%d' ) !== false ) { // %d indicates a step #
						$strAddFieldTitle = sprintf( $strAddFieldTitle, $step );
						
						$step++;
					}
					
					if( !empty( $arrAddFieldData['params'] ) ) {
						add_settings_field(
							$arrAddFieldData['id'],
							$strAddFieldTitle,
							$arrAddFieldData['callback'],
							$strAddFieldPage,
							$strSection,
							$arrAddFieldData['params']
						);
					} else {
						add_settings_field(
							$arrAddFieldData['id'],
							$strAddFieldTitle,
							$arrAddFieldData['callback'],
							$strAddFieldPage,
							$strSection
						);
					}	
				}
			}
			
		}
	
		
		// regular	
		add_settings_field(	
			'cc2_setup', 
			sprintf('<strong>Step %d</strong><br /><em>Customize</em>', $step ),
			array( $this, 'setting_page_customizer' ),
			'cc2_options' , 
			'section_general' 
		);
		// add_settings_field( $id, $title, $callback, $page, $section, $args ); 


	}


	function admin_page_screen() {
		// prepare variables
		$setting_sections = $this->arrSections;
		$current_tab = 'getting-started';
			
		if( !empty( $_GET['tab'] ) && isset( $setting_sections[$_GET['tab']] ) ) {
			$current_tab = $_GET['tab'];
		}
		
		// fetch concerned tab / template
		include_once( get_template_directory() . '/includes/admin/templates/dashboard.php' );
		
		
	}
	
	function setting_page_general() {
		//new __debug('loaded setting_page_general');
	}
	
	function setting_page_pending_updates() {		
		if( !empty( $_POST['update_action'] ) && $_POST['update_action'] == 'yes' ) {
			$exec_update = true;
		}
		
		// prepare $_POST form stuff
		if( !empty( $_POST['update_data'] ) && is_array( $_POST['update_data'] ) ) {
			foreach( $_POST['update_data'] as $strSectionKey => $strValue ) {
				$arrCustomUpdateSections[] = $strSectionKey;
			}
		}	
		// prepare test run info
		if( !empty( $_POST['update_test_run'] ) ) {
			// simplified calls for better readability
			$is_test_run = false; // default setting
			
			if( $_POST['update_test_run'] == 'yes' ) {
				$is_test_run = true;
			}
		}
		
		
		// the actual update script
		include_once( get_template_directory() . '/includes/admin/updates/cc20-update.php' );
		
		// prepare selectable data
		if( !empty( $has_old_settings ) && is_array( $has_old_settings ) ) {
			$update_options = $has_old_settings;
		}
		
		// awaiting results
		if( isset( $arrResult ) ) {
			//new __debug( array('result' => $arrResult ), 'result' );
			$update_result = $arrResult;
		}
		
		//if( $exec_update == false || ( !isset( $update_result ) && ( !isset( $has_old_settings ) || $has_old_settings == false ) ) ) {
		// false = empty, but also !isset
		
		
		
		if( empty( $exec_update ) || ( !isset( $update_result ) && empty($has_old_settings) ) ) {
			_e('Look\'s like there aint no old settings to import. So, on to a fresh start! ;-)', 'cc2');
			return; // early bail-out
		}
		
		include_once( get_template_directory() . '/includes/admin/templates/dashboard-updates.php' );
	}
	
	function setting_page_customizer() {
		include_once( get_template_directory() . '/includes/admin/templates/dashboard-setting-customizer.php' );
	}
	
	function init_assets() {
		// register base files
		wp_register_script('cc-admin-ajaxhooks', get_template_directory_uri() . '/includes/admin/js/ajaxhooks.js', array('jquery') );
		
		
		wp_register_script('cc-admin-helper', get_template_directory_uri().'/includes/admin/js/admin-helper.js', array('jquery') );
		wp_register_script('cc-admin-js', get_template_directory_uri().'/includes/admin/js/admin.js', array( 'jquery', 'cc-admin-helper' ) );
		wp_register_style('cc-admin-css', get_template_directory_uri() . '/includes/admin/css/admin.css' );

		wp_register_style('cc_tk_zendesk_css', '//assets.zendesk.com/external/zenbox/v2.6/zenbox.css' );
		wp_register_script('cc_tk_zendesk_js', '//assets.zendesk.com/external/zenbox/v2.6/zenbox.js');	

		wp_register_script('cc-support-helper', get_template_directory_uri() . '/includes/admin/js/support-helper.js', array('cc-admin-ajaxhooks') );
		

		/**
		 * NOTE: Rest of the assets are registered for on-demand loading
		 */

		// load bootstrap css
		wp_register_style( 'cc-bootstrap', get_template_directory_uri() . '/includes/resources/bootstrap/dist/css/bootstrap.min.css' );

		// load bootstrap js
		wp_register_script('cc-bootstrap-js', get_template_directory_uri().'/includes/resources/bootstrap/dist/js/bootstrap.min.js', array('jquery') );

		// load the glyphicons
		wp_register_style( 'cc-glyphicons', get_template_directory_uri() . '/includes/resources/glyphicons/css/bootstrap-glyphicons.css' );

		// load animate.css
		wp_register_style( 'cc-animate-css', get_template_directory_uri() . '/includes/resources/animatecss/animate.min.css' );

	


	}
	
	
	function load_assets( $page_slug ) {
		
		
		
		
		wp_enqueue_script('consoledummy');
		wp_enqueue_script( 'svg-painter' ); // might fix the randomly appearing 'cannot initialize wp.svgPainter' error
		
		wp_enqueue_script('cc-admin-js');
		wp_enqueue_style('cc-admin-css');
		
		
		
		/**
		 * @see http://wordpress.stackexchange.com/questions/22857/css-not-pulling-in-for-jquery-ui-dialog#comment41208_22857
		 */
		
		wp_enqueue_script('jquery-ui');
		wp_enqueue_script('jquery-ui-widget');
		wp_enqueue_script('jquery-ui-dialog');
		wp_enqueue_script('jquery-ui-button');
		wp_enqueue_script('jquery-ui-position');
		
		wp_enqueue_style( 'wp-jquery-ui-dialog' );		
		
		
		wp_enqueue_script('cc-admin-ajaxhooks');
		
		// load zenbox + more ONLY on the cc2-settings pages, but NOT eg. on the theme customizer page!
		
		if( is_cc2_admin_page( $page_slug, 'cc2-settings' ) ) {
		
			if( !empty( $this->arrSupportSettings ) ) {
				wp_localize_script( 'cc-support-helper', 'tk_support_settings', $this->arrSupportSettings );
			}
			wp_enqueue_script('cc-support-helper');

		}
		/*
		wp_enqueue_script('cc_tk_zendesk_js');
		wp_enqueue_style('cc_tk_zendesk_js');*/
	}

	

}

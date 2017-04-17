<?php
/**
 * Registering for the WordPress Customizer
 * Block-wise structured OOP version - to easier detect possible bugs
 *
 * @author Konrad Sroka
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0-rc1
 */




/**
 * cc2 Customizer Handler Class
 * As the name indicates, its handling all stuff with initalizations, set up and assets enqueuing for cc2
 * Partial rewrite / combining procedural code into one class.
 * 
 * @author Fabian Wolf
 * @since 2.0-rc1
 * @package cc2
 */
 

if( !class_exists( 'cc2_CustomizerLoader' ) ) {
	
	class cc2_CustomizerLoader {
		
		static $customizer_section_priority,
			$customizer_section_priority_call = array(),
			$arrSectionCalls = array();
			
		var $arrSectionPriorities = array();
			
		
		function __construct() {
			if( !function_exists( 'cc2_Pasteur_passthrough') ) {
				
				require_once( get_template_directory() . '/includes/pasteur.php' );
			}
			
			$this->init_customizer_hooks();
			
			
		}

		protected static $customizer_section_priorities = NULL;
			
	
	
		
		public function init_customizer_hooks() {
			//__debug::log( __METHOD__ . ' fires ');
			
			// scripts
			add_action( 'customize_controls_enqueue_scripts', array( $this, 'load_customizer_scripts' ) );
			
			
			// set up actions
			add_action( 'customize_preview_init', array( $this, 'customizer_preview_init' ) );	
		}
		
		
		protected static function set_section_priorities( $priority_data = array() ) {
			if( !empty( $priority_data ) && is_array( $priority_data ) ) {
				self::$customizer_section_priorities = $priority_data;
			}
			
		}
		
		public static function get_section_priorities( $section = false ) {
			if( !empty( $section ) && isset( self::$customizer_section_priorities[ $section ] ) ) {
				return self::$customizer_section_priorities[ $section ];
			}
			
		}
		
		
		function load_customizer_scripts() {
			
			$customizer_data = self::prepare_preloaded_data();
			
			
			wp_enqueue_script(
				'cc2-customizer-helper', get_template_directory_uri() . '/includes/admin/js/customizer-helper.js', array('jquery', 'wp-color-picker')
			);
			wp_localize_script( 'cc2-customizer-helper', 'customizer_data', $customizer_data );
			
			wp_enqueue_script( 'wp-color-picker' );
			wp_enqueue_style( 'wp-color-picker' );
	
		}
	
		public static function prepare_preloaded_data() {
			$return = array();
			
			
			// defaults for the dark and light navbar skin
			
			
			// light skin
			$return['navbar']['light'] = apply_filters('cc2_customizer_navbar_light_colors', array(
				'top_nav' => array(
					'top_nav_background_color' => 'fbfbfb',
					'top_nav_text_color' => 'F2694B',
					'top_nav_hover_text_color' => 'ffffff',
					
				),
				
				'bottom_nav' => array(
					'secondary_nav_background_color' => 'transparent',
					'secondary_nav_text_color' => 'F2694B',
					'secondary_nav_hover_text_color' => 'F2854B',
				),
			) );
			
			// dark skin
			$return['navbar']['dark'] = apply_filters('cc2_customizer_navbar_dark_colors', array(
				'top_nav' => array(
					'top_nav_background_color' => '101010',
					'top_nav_text_color' => 'a9a9a9',
					'top_nav_hover_text_color' => 'ffffff',
				),
				
				'bottom_nav' => array(
				
					'secondary_nav_background_color' => 'F2854B',
					'secondary_nav_text_color' => 'A9A9A9',
					'secondary_nav_hover_text_color' => 'ffffff',
				),
			) );
			
			// labels
			$return['button']['reset'] = apply_filters('cc2_customizer_button_reset_text', __('Reset settings', 'cc2') );
			
			// color schemes
			if( function_exists( 'cc2_get_color_schemes' ) ) {
				$return['color_schemes'] = cc2_get_color_schemes( true );
			}

		
			return $return;
		}
	

		/**
		 * Load and prepare all required variables and settings
		 */
		 
		function prepare_variables() {
			$return = array();
			$wp_date_format = trim( get_option('date_format', 'Y-m-d' ) ) . ' ' . trim( get_option('time_format', 'H:i:s' ) );
			$current_wp_version = get_bloginfo('version');
			
			// load base theme config
			if( defined( 'CC2_THEME_CONFIG' ) ) {
				$config = maybe_unserialize( CC2_THEME_CONFIG );
				
				$return['config'] = $config;
				
				
				
				$arrColorSchemes = cc2_get_color_schemes();
				$current_scheme = cc2_get_current_color_scheme();
				
				if( !empty( $arrColorSchemes ) ) {
					foreach( $arrColorSchemes as $strSchemeSlug => $arrSchemeData ) {
						$strSchemeTitle = $arrSchemeData['title'];
						
						if( isset( $arrSchemeData['_modified'] ) ) {
							$strSchemeTitle .= ' (' . date( $wp_date_format, $arrSchemeData['_modified'] ) . ')';	
						}
						$return['color_schemes'][$strSchemeSlug] = $strSchemeTitle;
					}
				}
				//new __debug( $current_scheme, __METHOD__ . ': current scheme' );
				
				$return['current_color_scheme'] = $current_scheme['slug'];
				
				/**
				 * TODO: Use cc2_ColorSchemes instead
				 */
				
				/*
				if( isset( $config['color_schemes'] ) && is_array( $config['color_schemes'] ) ) {
				
					foreach( $config['color_schemes'] as $strSchemeSlug => $arrSchemeParams ) {
				
						$return['color_schemes'][ $strSchemeSlug ] = $arrSchemeParams['title'];
						
						if( !empty($arrSchemeParams['scheme'] ) ) { // some may be just rudimentary added
							$return['color_scheme_previews'][ $strSchemeSlug ] = $arrSchemeParams['scheme'];
						} else {
							$return['color_scheme_previews'][ $strSchemeSlug ] = false;
						}
					}
				}*/
			}
			
			$return['current_wp_version'] = $current_wp_version;
			
			
			// Load Loop Templates
			$return['cc_loop_templates'] = array(
				'blog-style'		=> 'Blog style',
			);

			// If TK Loop Designer is loaded load the loop templates!
			$tk_loop_designer_options = get_option('tk_loop_designer_options', false);  // so??

			// Merge TK Loop Designer Templates with the CC Loop Templates array, if there are any
			if(defined('TK_LOOP_DESIGNER') && !empty( $tk_loop_designer_options) ) {
				// should now work ONLY _IF_ there were settings by the tk loop designer available
				if( !empty( $tk_loop_designer_options) && isset($tk_loop_designer_options['templates'])) {
					foreach ($tk_loop_designer_options['templates'] as $template_name => $loop_designer_option) {
						$return['cc_loop_templates'][$template_name] = $loop_designer_option;
					}
				}
			}

			// Load Fonts
			$return['cc2_font_family'] = apply_filters('cc2_customizer_load_font_family', cc2_customizer_load_fonts() );

			// Load Slideshow Positions
			$return['slider_positions'] = array(
				'cc_before_header'						=> 'before header',
				'cc_after_header'						=> 'after header',
				'cc_first_inside_main_content'			=> 'in main content',
				'cc_first_inside_main_content_inner'	=> 'in main content inner'
			);

			// Load Horizontal Positions
			$return['cc2_h_positions'] = array(
				'left'			=> 'left',
				/*'center'		=> 'center',*/ /** NOTE: Obsolete */
				'right'			=> 'right',
			);

			// Load Width Array
			$return['boxed'] = array(
				'boxed'			=> 'boxed',
				'fullwidth'		=> 'fullwidth',
			);





			// Load Effects for Incoming Animations
			$return['cc2_animatecss_start_moves'] = array(
				'hide'					=> 'hide',
				'no-effect' 			=> 'display, but no effect',
				'bounceInDown' 			=> 'bounce in down',
				'bounceInLeft' 			=> 'bounce in left',
				'bounceInRight' 		=> 'bounce in right',
				'bounceInUp' 			=> 'bounce in up',
				'bounceIn' 				=> 'bounce in',
				'fadeInDown' 			=> 'fade in down',
				'fadeInLeft' 			=> 'fade in left',
				'fadeInRight' 			=> 'fade in right',
				'fadeInUp' 				=> 'fade in up',
				'fadeIn' 				=> 'fade in',
				'lightSpeedIn' 			=> 'lightspeed in',
				'rollIn' 				=> 'roll in',
				'flipInX' 				=> 'flip in X',
				'flipInY' 				=> 'flip in Y'
			);


			$cc_slider_options = get_option('cc_slider_options');
			$return['cc_slider_options'] = $cc_slider_options;
			
			$return['cc_slideshow_template']['none'] = __('None', 'cc2');
			
			// Load slideshow templates
			if(isset($cc_slider_options) && is_array($cc_slider_options) ){
				foreach($cc_slider_options as $key => $slider_data) {
					/**
					 * Compatiblity layer for old beta 1 - 2 releases
					 */
					
					$strSlideshowID = $key;
					
					// old versions: key = title, newer versions: key = key, [key][title] = title
					if( !isset( $slider_data['title'] ) || empty( $slider_data['title']) ) {
						$strSlideshowName = $key;
					} else {
						$strSlideshowName = $slider_data['title'];
					}
					
					// check if type is set
					if( !empty( $slider_data['meta-data']['slideshow_type'] ) ) {
						$strSlideshowName .= ' (' . ucwords( str_replace('-', ' ', $slider_data['meta-data']['slideshow_type']) ) . ')';
					}
					
					$return['cc_slideshow_template'][ $strSlideshowID ] = $strSlideshowName;
					
					//$cc_slideshow_template[ $key ] = $slider_data['title'] . ' (' . ucwords( str_replace('-', ' ', $slider_data['meta-data']['slideshow_type']) ). ')';
				}
			}


			// Load Text Align Array
			$return['cc2_text_align'] = array(
				'left'		=> 'left',
				'center'	=> 'center',
				'right'		=> 'right'
			);

			// Set up Bootstrap columns
			$return['bootstrap_cols'] = array( 
				1 => '1', 
				2 => '2', 
				3 => '3',
				4 => '4',
				5 => '5',
				6 => '6',
				7 => '7',
				8 => '8',
				9 => '9',
				10 => '10',
				11 => '11',
				12 => '12',
			);
			
			// add site title positions
			$return['site_title_pos'] = array(
				'before_header' => __('Before Header Image', 'cc2' ),
				'after_header' => __('After Header Image', 'cc2' ),
				'before_header' => __('Before Header Image', 'cc2' ),
				'after_header' => __('After Header Image', 'cc2' ),
			);
			
			// get advanced settings
			$return['advanced_settings'] = get_option('cc2_advanced_settings', false );
			
			
			$return = apply_filters( 'cc2_customizer_prepare_variables',  $return );
			
			return $return;
		}
		
	
		function customizer_preview_init() {
		
			$tk_customizer_options = get_option('tk_customizer_options');
			
			if(isset( $tk_customizer_options['customizer_disabled'])) {
				return;
			}
			wp_enqueue_script('consoledummy');
			
			wp_enqueue_script('jquery');

			// load animate.css
			wp_enqueue_style( 'cc-animate-css');
			wp_enqueue_script( 'tk_customizer_preview_js',	get_template_directory_uri() . '/includes/admin/js/customizer.js', array( 'jquery', 'customize-preview' ), '', true );
		}
	
	
	
		function customizer_sanitizer( $data, $wp_instance = false ) {
			$return = $data;
			
			
			
			
			/*if( class_exists('cc2_Pasteur') ) {
				$return = cc2_Pasteur::sanitize_value( $data, $wp_instance );
			}*/
			
			if( function_exists( 'cc2_Pasteur_sanitize_value' ) ) {
				$return = cc2_Pasteur_sanitize_value( $data );
			}
			
			
			return $return;
		}
		
	
	
		/**
		 * Does not sanitize anything, basically.
		 */
	
		function sanitize_default( $data ) {
			$return = $data;
			
			
			return $return;
		}
		
	}
	
	/**
	 * NOTE: copy + paste fron wp-includes/theme.php - the ORIGINAL init point of the theme customizer!
	 * 
 * Includes and instantiates the WP_Customize_Manager class.
 *
 * Fires when ?wp_customize=on or on wp-admin/customize.php.
 *
 * @since 3.4.0
 
function _wp_customize_include() {
	if ( ! ( ( isset( $_REQUEST['wp_customize'] ) && 'on' == $_REQUEST['wp_customize'] )
		|| ( is_admin() && 'customize.php' == basename( $_SERVER['PHP_SELF'] ) )
	) )
		return;

	require( ABSPATH . WPINC . '/class-wp-customize-manager.php' );
	// Init Customize class
	$GLOBALS['wp_customize'] = new WP_Customize_Manager;
}
add_action( 'plugins_loaded', '_wp_customize_include' );
*/
	
	
	class cc2_CustomizerTheme extends cc2_CustomizerLoader {
		function __construct() {		
			//add_action('plugins_loaded', array( $this, 'customize_default_sections' ) );
			
			//add_action( 'init', array( $this, 'customizer_init' ) );
			// init AFTER the customizer init; has default priority = 10, thus we go for 11!
			if( !function_exists( 'cc2_Pasteur_passthrough' ) ) {
				require_once( get_template_directory() . '/includes/pasteur.php' );
			}
			
			add_action( 'init', array( $this, 'customizer_init' ) );
			
			if( !empty( $this->arrSectionPriorities ) ) {
				self::set_section_priorities( $this->arrSectionPriorities );
			}
			
			// add global sanitizer
			//add_action('sanitize_option_theme_mods_cc2', array( $this, 'customizer_sanitizer' ) );
			// its unclear whether this is a filter or an action
			//add_filter('sanitize_option_theme_mods_cc2', array( $this, 'customizer_sanitizer' ) );
			
		}
		
		
		function customizer_init() {
			
			$this->init_customizer_hooks();
			
			/**
			 * NOTE: Originally was required to stay with the mega-function call .. cause else its getting pretty .. ugly. But: This might be the cause for multitudinous bugs. So I restructured into several function blocks. Still looking pretty ugly.
			 */
			
			// add global sanitizer .. althought that doesnt seem to work .. which is NASTY.
			//add_action('sanitize_option_theme_mods_cc2', array( $this, 'customizer_sanitizer' ) );
			// its unclear whether this is a filter or an action
			//add_filter('sanitize_option_theme_mods_cc2', array( $this, 'customizer_sanitizer' ) );
		
			/*
			add_action('customize_save', array( $this, 'hook_customize_save' ) );
		
			add_action('customize_save_after', array( $this, 'hook_customize_save_after' ) );*/
			
			$section_priority = 10;
			
			
			$this->arrSectionPriorities = array(
				'section_title_tagline' => $section_priority+=10,
				'section_header' => $section_priority+=20, /* 20 + 20; should sit straight below header image (50) 60 */
				'section_color_schemes' => 10, /* 40 + 10 */
				
				'section_nav' => $section_priority+=10, /* 50 + 10 */
				
				'section_branding' => $section_priority+=10, /* 60 + 10 */
				
				'section_typography' => $section_priority+=10, /* 70 + 10 */
				/*'section_background' => $section_priority+=10, // 80 + 10 */
				
				
				'section_static_frontpage' => $section_priority+=10, /* 120 + 10 */
				'section_blog' => $section_priority+=5, /* 140 + 10 */
				
				'section_slider' => $section_priority+=5, /* 150 + 10 */
			
				
				'section_content' => $section_priority+=5, /* 130 + 10 */
				
				/*'section_wc_support' => $section_priority+=5,*/
				
				'section_layouts' => $section_priority+=5, /* 120 + 10 */
				
				
				
				'section_widgets' => $section_priority+=20, /* 90 + 20 */
				'panel_widgets' => $section_priority+=10, /* 110 + 10 */
				
				
				
				'section_footer' => $section_priority+=10, /* 170 + 10 */
				
				'section_customize_bootstrap' => $section_priority+=30, /* 180 + 10 */
				'section_advanced_settings' => $section_priority+=10,
			);
			
			// initial function call. don't change if you don't know what you're doing!
			//add_action('customizer_register', array( $this, 'customize_default_sections' ), 11 );
	
			
			/**
			 * FIXME: Global static priority count for sections does not work properly. Only manually set priority works.
			 */	
			
			add_action( 'customize_register', array( $this, 'section_color_schemes' ),11 );
			add_action( 'customize_register', array( $this, 'section_title_tagline' ), 12);
			add_action( 'customize_register', array( $this, 'section_typography' ), 13);
			add_action( 'customize_register', array( $this, 'section_background' ), 14);
			add_action( 'customize_register', array( $this, 'section_header' ), 15);
			add_action( 'customize_register', array( $this, 'section_nav' ), 16);
			add_action( 'customize_register', array( $this, 'section_branding' ), 17);
			add_action( 'customize_register', array( $this, 'section_static_frontpage' ), 18);
			add_action( 'customize_register', array( $this, 'section_content' ), 19);
			
			// add woocommerce support
			/*if( class_exists( 'WooCommerce' ) != false ) {
				add_action( 'customize_register', array( $this, 'section_wc' ), 19);
				
			}*/
			
			add_action( 'customize_register', array( $this, 'section_blog' ), 20);
			add_action( 'customize_register', array( $this, 'section_slider' ), 21);
			add_action( 'customize_register', array( $this, 'section_layouts' ), 22);
			add_action( 'customize_register', array( $this, 'section_widgets' ), 23);
			add_action( 'customize_register', array( $this, 'section_footer' ), 24);
			
			add_action( 'customize_register', array( $this, 'section_customize_bootstrap' ), 28);
			//add_action( 'customize_register', array( $this, 'section_advanced_settings' ), 29);
			
			
			//add_action( 'customize_register', 'tk_customizer_support' );
		}
		
		
		function hook_customize_save_after( $data ) {
			//new __debug( $data, 'customize_save_after fires' );
			
		}
		
		function hook_customize_save( $data ) {
			//new __debug( $data, 'customize_save fires' );	
		}

		
		/**
		 * 
		 * NOTE: Base customizer settings are defined in class-wp-customize-manager.php: WP_Customize_Manager::register_controls()
		 * 
		 */
		
		function customize_default_sections( $wp_customize ) {
			// Changing some section titles, ordering and updating
			//$wp_customize->remove_section( 'background_image' );
			
			
			//$wp_customize->get_section( 'colors' )->title = __('Color Schemes', 'cc2' );
			/*
			if( !empty( self::$arrSectionCalls['section_color_schemes'] ) ) {
				$wp_customize->get_section( 'colors' )->priority = self::$arrSectionCalls['section_color_schemes'];
			}*/
			$wp_customize->get_section( 'title_tagline' )->priority = $this->arrSectionPriorities['section_title_tagline'];
			
				$wp_customize->get_control('display_header_text')->priority = 10;
				$wp_customize->get_control('display_header_text')->label = __('Display Site Title', 'cc2' );
				$wp_customize->get_control('blogname')->priority = 13;
				//$wp_customize->get_setting('blogname')->title = 13;
				
				$wp_customize->get_control('blogdescription')->priority = 14;
				
				$wp_customize->get_control('header_textcolor')->priority = 15;

			
			$wp_customize->get_section('nav')->priority = $this->arrSectionPriorities['section_nav'];
			
			//$wp_customize->get_section( 'colors' )->priority = 10;
			
			$wp_customize->get_section('colors')->priority = $this->arrSectionPriorities['section_color_schemes'];
			$wp_customize->get_section('colors')->title = __('Color Schemes', 'cc2' );
		
		
			
			
			
			$wp_customize->get_section( 'static_front_page' ) -> title 		= 'Homepage';
			// change default WP priority
			if( !empty( $this->arrSectionPriorities['section_static_frontpage'] ) ) {
				//$wp_customize->get_section( 'static_frontpage' )->priority = $this->arrSectionPriorities['section_static_frontpage'];
				@$wp_customize->get_section( 'static_frontpage' )->priority = 120;
			}
			
			if( !empty( $this->arrSectionPriorities['panel_widgets'] ) ) {
				//$wp_customize->get_panel('widgets')->priority = $this->arrSectionPriorities['panel_widgets'];
			}
			
			
			/**
			 * FIXME: doesnt seem to work thou .. :(
			 */
			/*$wp_customize->get_setting( 'background_color' 	)->transport = 'postMessage';
			$wp_customize->get_setting( 'background_image' 	)->transport = 'postMessage';*/
			
			/*
			$wp_customize->get_control( 'background_color' 	) -> section 	= 'background';
			$wp_customize->get_control( 'background_image' 	) -> section 	= 'background';*/
			$wp_customize->get_control( 'background_color' 	) -> section 	= 'colors';
			$wp_customize->get_control( 'background_color' 	)->priority = 80;
			$wp_customize->get_control( 'background_image' 	) -> section 	= 'colors';
			$wp_customize->get_control( 'background_image' 	)->priority = 90;
			
			$wp_customize->remove_section( 'background_image' );
			
			$wp_customize->get_control( 'header_image' 		) -> section 	= 'header';
			$wp_customize->get_control( 'header_textcolor' 	) -> section 	= 'title_tagline';
		
			//return $wp_customize;
				
		}
		
		/**
		 * Regular sections
		 */
		
	
		
		/**
		 * Switch color schemes
		 */
		
		function section_color_schemes( $wp_customize ) {
			extract ( $this->prepare_variables() );
			
			// customize from default WP
			//self::$customizer_section_priority
			/*
			$wp_customize->get_section( 'colors')->priority = 10;
			$wp_customize->get_section( 'colors')->title = __('Color Schemes', 'cc2' );
			*/
			$this->customize_default_sections( $wp_customize );
			//$wp_customize->get_panel('widgets')->priority = 120;
			
			//$wp_customize->get_panel('widgets')->priority = $this->arrSectionPriorities['section_customize_widgets'];
			
			/*
			$_debug = array(
				'section_priorities' => $this->arrSectionPriorities,
				'wp_customize->get_panel(widgets)' => $wp_customize->get_panel('widgets'),
				'wp_customize->get_panel(widgets)' => $wp_customize->get_panel('widgets')->priority,
			);
		
			// debug label
			$wp_customize->add_setting( 'debug_customizer', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Label( $wp_customize, 'debug_customizer', array(
				'label' 		=> 	__('Debug:', 'cc2') . ' ' . print_r( $_debug , true ),
				'type' 			=> 	'label',
				'section' 		=> 	'colors',
				'priority'		=> 	1,
			) ) );
			*/
			
			if( !empty( $color_schemes ) ) {
				// mind the test scheme
				if( !defined('CC2_THEME_DEBUG' ) && isset( $color_schemes['_test'] ) ) {
					unset( $color_schemes['_test'] );
				}
				
			
				// Color Scheme
				 $wp_customize->add_setting( 'color_scheme', array(
					'default'      => 'default',
					'capability'   => 'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' 	=> 'sanitize_key',
				 ) );
				 
				 $wp_customize->add_control( 'color_scheme', array(
					'label'   		=> 	__('Choose a scheme', 'cc2'),
					'section' 		=> 	'colors',
					'priority'		=> 	5,
					'type'    		=> 	'radio',
					'choices'    	=> 	$color_schemes,
				) );
				
				// Color Scheme Notice
				 $wp_customize->add_setting( 'notice_color_scheme', array(
					 'capability'   => 'edit_theme_options',
					 'sanitize_callback' => 'cc2_Pasteur_none'
				 ) );
				 
				$wp_customize->add_control( 
					new Description( $wp_customize, 'notice_color_scheme', array(
						'label' 		=> 	__('Note: Switching is also going to change quite a lot of other settings, including Font, Link and Link Hover Color. Better to save your current changes before-hand.', 'cc2'),
						'type' 			=> 	'description',
						'section' 		=> 	'colors',
						'priority'		=> 	6,
						
					) 
				) );
			}
		}
		
		/**
		 * Background section
		 */


		function section_background( $wp_customize ) {
			extract( $this->prepare_variables() );
			
			
			// Background Section
			$wp_customize->add_section( 'background', array(
				'title'         => 	'Background',
				'priority'      => 	( !empty( $this->arrSectionPriorities['section_background'] ) ? $this->arrSectionPriorities['section_background'] : 60 ),
			) );
		}
		
		/**
		 * static_front_page aka Home Page
		 * NOTE: Built-in section
		 */
		
		
		function section_static_frontpage( $wp_customize ) {
			extract( $this->prepare_variables() );
			
			
			if( !empty( $this->arrSectionPriorities['section_static_frontpage'] ) ) {
				$wp_customize->get_section('static_front_page')->priority = $this->arrSectionPriorities['section_static_frontpage'];
			}
			
			/*if( !empty( self::$arrSectionCalls['section_static_frontpage'] ) && method_exists( $wp_customize, 'get_section' ) ) {
				
				$wp_customize->get_section( 'section_static_frontpage' )->priority = self::$arrSectionCalls['section_static_frontpage'];
			}
			*/
			
			/*
			$_debug = array(
				'section_priorities' => $this->arrSectionPriorities,
				'wp_customize->get_section' => get_object_vars( $wp_customize->get_section('static_front_page') ),
			);
		
			// debug label
			$wp_customize->add_setting( 'debug_static_fp', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Label( $wp_customize, 'debug_static_fp', array(
				'label' 		=> 	__('Debug:', 'cc2') . ' ' . print_r( $_debug , true ),
				'type' 			=> 	'label',
				'section' 		=> 	'static_front_page',
				'priority'		=> 	1,
			) ) );*/
			//$branding_section_priority++;
			
			
			
			 // Hide all Content on Frontpage
			$wp_customize->add_setting( 'hide_front_page_content', array(
				'default'       =>  false,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('hide_front_page_content', array(
				'label'    		=> 	__('Hide all content on the front page', 'cc2'),
				'section'  		=> 	'static_front_page',
				'type'     		=> 	'checkbox',
				'priority'		=> 	261,
				'value' 		=> 1,
			) );


			
		}
		
		/**
		 * Site Title & Tagline
		 */
		function section_title_tagline( $wp_customize ) {
			extract( $this->prepare_variables() );

			//$wp_customize->get_control('display_header_text')->priority = 15;

			
			// here we need to add some extra options first..

			// Site Title Font Family
			$wp_customize->add_setting( 'site_title_font_family', array(
				'default'       => 	'Pacifico',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control( 'site_title_font_family', array(
				'label'   		=> 	__('Site Title Font Family', 'cc2'),
				'section' 		=> 	'title_tagline',
				'priority'		=> 	180,
				'type'    		=> 	'select',
				'choices'    	=> 	$cc2_font_family
			) );

			// TK Google Fonts Ready! - A Quick Note
			$wp_customize->add_setting( 'google_fonts_note_site_title', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Description( $wp_customize, 'google_fonts_note_site_title', array(
				'label' 		=> 	__('Add Google Fonts and make them available in the theme options with <a href="http://themekraft.com/store/tk-google-fonts-wordpress-plugin/" target="_blank">TK Google Fonts</a>.', 'cc2'),
				'type' 			=> 	'description',
				'section' 		=> 	'title_tagline',
				'priority'		=> 	181,
			) ) );


			// Site Title Position
			$wp_customize->add_setting( 'site_title_position', array(
				'default'       => 	'left',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control( 'site_title_position', array(
				'label'   		=> 	__('Site Title Position', 'cc2'),
				'section' 		=> 	'title_tagline',
				'priority'		=> 	200,
				'type'    		=> 	'select',
				'choices'    	=> 	$cc2_h_positions
			) );


			// Tagline font family
			 $wp_customize->add_setting( 'tagline_font_family', array(
					'default'       => 	'inherit',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_text',
				) );
			$wp_customize->add_control( 'tagline_font_family', array(
				'label'   		=> 	__('Tagline Font Family', 'cc2'),
				'section' 		=> 	'title_tagline',
				'priority'		=> 	200,
				'type'    		=> 	'select',
				'choices'    	=> 	$cc2_font_family
			) );

			// Tagline color
			$wp_customize->add_setting('tagline_text_color', array(
				'default'           	=> '#a9a9a9',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				
				'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
				'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
				) );
				
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'tagline_text_color', array(
				'label'    				=> __('Tagline Color', 'cc2'),
				'section'  				=> 'title_tagline',
				'priority'				=> 201,
			) ) );

		}
		
		function section_header( $wp_customize ) {
			extract( $this->prepare_variables() );
			
			$priority = 60;
			
			if( !empty( $this->arrSectionPriorities['section_header'] ) ) {
				$priority = $this->arrSectionPriorities['section_header'];
			}
			
		// Header Section
			$has_static_frontpage = ( get_option( 'show_on_front') == 'page' ? true : false );
			
			$wp_customize->add_section( 'header', array(
				'title'         => 	'Header',
				'priority'      => 	$priority,
			) );

				// Display Header
				$wp_customize->add_setting( 'display_header_heading', array(
					'capability'    => 	'edit_theme_options',
					'sanitize_callback' => 'cc2_Pasteur_none'
				) );
				$wp_customize->add_control( new Label( $wp_customize, 'display_header_heading', array(
					'label' 		=> 	__('Display Header Image', 'cc2'),
					'type' 			=> 	'label',
					'section' 		=> 	'header',
					'priority'		=> 	20,
				) ) );

				/**
				 * Static frontpage is set, ie. home != blog
				 * 
				 * @see http://codex.wordpress.org/Function_Reference/is_home#Blog_Posts_Index_vs._Site_Front_Page
				 */
				if( $has_static_frontpage != false ) { // static front page is set
					// Display Header on Home
					$wp_customize->add_setting( 'display_header_home', array(
						'default'		=>	false,
						'capability'    => 	'edit_theme_options',
						'transport'   	=> 	'refresh',
						'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
					) );
					$wp_customize->add_control('display_header_home', array(
						'label'    		=> 	__('on blog', 'cc2'),
						'section'  		=> 	'header',
						'type'     		=> 	'checkbox',
						'priority'		=> 	40,
						'value' 		=> 1,
					) );
					
					
					// Display Header on Frontpage
					$wp_customize->add_setting( 'display_header_static_frontpage', array(
						'default'		=>	true,
						'capability'    => 	'edit_theme_options',
						'transport'   	=> 	'refresh',
						'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
					) );
					$wp_customize->add_control('display_header_static_frontpage', array(
						'label'    		=> 	__('on static frontpage', 'cc2'),
						'section'  		=> 	'header',
						'type'     		=> 	'checkbox',
						'priority'		=> 	41,
						'value' 		=> 1,
					) );
				} else { // no static frontpage; home == blog
					// Display Header on Home
					$wp_customize->add_setting( 'display_header_home', array(
						'default'		=>	true,
						'capability'    => 	'edit_theme_options',
						'transport'   	=> 	'refresh',
						'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
					) );
					$wp_customize->add_control('display_header_home', array(
						'label'    		=> 	__('on homepage', 'cc2'),
						'section'  		=> 	'header',
						'type'     		=> 	'checkbox',
						'priority'		=> 	40,
						'value' 		=> 1,
					) );
					
					
				}

				// Display Header on Posts
				$wp_customize->add_setting( 'display_header_posts', array(
					'default'		=>	true,
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('display_header_posts', array(
					'label'    		=> 	__('on posts', 'cc2'),
					'section'  		=> 	'header',
					'type'     		=> 	'checkbox',
					'priority'		=> 	50,
					'value' 		=> true,
				) );

				// Display Header on Pages
				$wp_customize->add_setting( 'display_header_pages', array(
					'default'		=>	true,
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('display_header_pages', array(
					'label'    		=> 	__('on pages', 'cc2'),
					'section'  		=> 	'header',
					'type'     		=> 	'checkbox',
					'priority'		=> 	60,
					'value' 		=> true,
				) );

				// Display Header on Archive
				$wp_customize->add_setting( 'display_header_archive', array(
					'default'		=>	true,
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('display_header_archive', array(
					'label'    		=> 	__('on archive', 'cc2'),
					'section'  		=> 	'header',
					'type'     		=> 	'checkbox',
					'priority'		=> 	70,
					'value' 		=> true,
				) );

				// Display Header on Search
				$wp_customize->add_setting( 'display_header_search', array(
					'default'		=>	true,
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('display_header_search', array(
					'label'    		=> 	__('on search', 'cc2'),
					'section'  		=> 	'header',
					'type'     		=> 	'checkbox',
					'priority'		=> 	80,
					'value' 		=> 1,
				) );

				// Display Header on 404
				$wp_customize->add_setting( 'display_header_404', array(
					'default'		=>	true,
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('display_header_404', array(
					'label'    		=> 	__('on 404: not-found', 'cc2'),
					'section'  		=> 	'header',
					'type'     		=> 	'checkbox',
					'priority'		=> 	90,
					'value' 		=> 1,
				) );
				
				/**
				 * WooCommerce support
				 */
				
				if( class_exists( 'WooCommerce' ) != false ) {
					
					// Display Header on Products Pages = Archive
					$wp_customize->add_setting( 'wc_display_header_products', array(
						'default'		=>	true,
						'capability'    => 	'edit_theme_options',
						'transport'   	=> 	'refresh',
						'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
					) );
					$wp_customize->add_control('wc_display_header_products', array(
						'label'    		=> 	__('on WooCommerce Products List', 'cc2'),
						'section'  		=> 	'header',
						'type'     		=> 	'checkbox',
						'priority'		=> 	95,
						'value' 		=> 1,
					) );
					
					
					// Display Header on Single Product Pages = Single
					$wp_customize->add_setting( 'wc_display_header_single_product', array(
						'default'		=>	true,
						'capability'    => 	'edit_theme_options',
						'transport'   	=> 	'refresh',
						'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
					) );
					$wp_customize->add_control('wc_display_header_single_product', array(
						'label'    		=> 	__('on WooCommerce Single Product', 'cc2'),
						'section'  		=> 	'header',
						'type'     		=> 	'checkbox',
						'priority'		=> 	95,
						'value' 		=> 1,
					) );
				}
				

				// Header Height
				$wp_customize->add_setting('header_height', array(
					'default' 		=> 'auto',
					'capability'    => 'edit_theme_options',
					'transport'   	=> 'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control('header_height', array(
					'label'      	=> __('Header Height', 'cc2'),
					'section'    	=> 'header',
					'priority'   	=> 120,
					'value' 		=> true,
				) );

				// Notice on Header Height
				$wp_customize->add_setting( 'header_height_note', array(
					'capability'    => 	'edit_theme_options',
					'sanitize_callback' => 'cc2_Pasteur_none'
				) );
				$wp_customize->add_control( new Description( $wp_customize, 'header_height_note', array(
					'label' 		=> 	__('<small><em>Write "auto" or in px, like "200px"</em></small>', 'cc2'),
					'type' 			=> 	'description',
					'section' 		=> 	'header',
					'priority'		=> 	121,
				) ) );

				

				// differentiate between home and blog (ie. static frontpage IS set)

				if( $has_static_frontpage != false ) { // blog != home
					// Header Height on Homepage
					$wp_customize->add_setting('header_height_blog', array(
						'default' 		=> 'auto',
						'capability'    => 'edit_theme_options',
						'transport'   	=> 'refresh',
						'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
					) );
					$wp_customize->add_control('header_height_blog', array(
						'label'      	=> __('Header Height on Blog', 'cc2'),
						'section'    	=> 'header',
						'priority'   	=> 140,
					) );

					// Notice on Header Height Homepage
					$wp_customize->add_setting( 'header_height_blog_note', array(
						'capability'    => 	'edit_theme_options',
						'sanitize_callback' => 'cc2_Pasteur_none'
					) );
					$wp_customize->add_control( new Description( $wp_customize, 'header_height_blog_note', array(
						'label' 		=> 	sprintf( __('<small><em>Just for the %s, also &quot;auto&quot; or in px</em></small>', 'cc2'), __('blog', 'cc2') ),
						'type' 			=> 	'description',
						'section' 		=> 	'header',
						'priority'		=> 	141,
					) ) );

					// Header Height on Homepage
					$wp_customize->add_setting('header_height_home', array(
						'default' 		=> 'auto',
						'capability'    => 'edit_theme_options',
						'transport'   	=> 'refresh',
						'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
					) );
					$wp_customize->add_control('header_height_home', array(
						'label'      	=> __('Header Height on Homepage', 'cc2'),
						'section'    	=> 'header',
						'priority'   	=> 145,
					) );

					// Notice on Header Height Homepage
					$wp_customize->add_setting( 'header_height_home_note', array(
						'capability'    => 	'edit_theme_options',
						'sanitize_callback' => 'cc2_Pasteur_none'
					) );
					$wp_customize->add_control( new Description( $wp_customize, 'header_height_home_note', array(
						'label' 		=> 	sprintf( __('<small><em>Just for the %s, also &quot;auto&quot; or in px</em></small>', 'cc2'), __('Homepage', 'cc2') ),
						'type' 			=> 	'description',
						'section' 		=> 	'header',
						'priority'		=> 	146,
					) ) );
					
					
				
				} else { // blog == home
					// Header Height on Homepage
					$wp_customize->add_setting('header_height_home', array(
						'default' 		=> 'auto',
						'capability'    => 'edit_theme_options',
						'transport'   	=> 'refresh',
						'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
					) );
					$wp_customize->add_control('header_height_home', array(
						'label'      	=> __('Header Height on Homepage', 'cc2'),
						'section'    	=> 'header',
						'priority'   	=> 140,
					) );

					// Notice on Header Height Homepage
					$wp_customize->add_setting( 'header_height_home_note', array(
						'capability'    => 	'edit_theme_options',
						'sanitize_callback' => 'cc2_Pasteur_none'
					) );
					$wp_customize->add_control( new Description( $wp_customize, 'header_height_home_note', array(
						'label' 		=> 	__('<small><em>Just for the homepage, also "auto" or in px</em></small>', 'cc2'),
						'type' 			=> 	'description',
						'section' 		=> 	'header',
						'priority'		=> 	141,
					) ) );
				}
			

				// Header Background Color
				$wp_customize->add_setting( 'header_background_color', array(
					'default'           	=> 'fff',
					'capability'        	=> 'edit_theme_options',
					'transport'   			=> 'refresh',
					'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
					'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
				) );
				$wp_customize->add_control( new cc2_Customize_Color_Control($wp_customize, 'header_background_color', array(
					'label'    				=> __('Header Background Color', 'cc2'),
					'section'  				=> 'header',
					'priority'				=> 220,
				) ) );
				
				 // Header Background Image (if you want the site title still being displayed, over the header)
				$wp_customize->add_setting( 'header_background_image', array(
					'default'           	=> '',
					'capability'        	=> 'edit_theme_options',
					'transport'   			=> 'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control( new WP_Customize_Image_Control($wp_customize, 'header_background_image', array(
					'label'    				=> __('Header Background Image', 'cc2'),
					'section'  				=> 'header',
					'priority'				=> 221,
				) ) );
	
			
		}
		
		// Adding to Navigation Section (Nav)

		function section_nav( $wp_customize ) {
			extract( $this->prepare_variables() );
			
			
			// Header Top Nav - Fix to top
			$wp_customize->add_setting( 'fixed_top_nav', array(
				'default'		=>	true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('fixed_top_nav', array(
				'label'    		=> 	__('Top nav fixed to top?', 'cc2'),
				'section'  		=> 	'nav',
				'type'     		=> 	'checkbox',
				'priority'		=> 	40,
				'value' 		=> 1,
			) );
			
			// Top Nav Position
			$wp_customize->add_setting( 'top_nav_position', array(
				'default'       => 	'left',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				
			) );
			$wp_customize->add_control( 'top_nav_position', array(
				'label'   		=> 	__('Top Nav Position', 'cc2'),
				'section' 		=> 	'nav',
				'priority'		=> 	50,
				'type'    		=> 	'select',
				'choices'    	=> $cc2_h_positions
			) );
			
			// Secondary Nav Position
			$wp_customize->add_setting( 'secondary_nav_position', array(
				'default'       => 	'left',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control( 'secondary_nav_position', array(
				'label'   		=> 	__('Secondary Nav Position', 'cc2'),
				'section' 		=> 	'nav',
				'priority'		=> 	55,
				'type'    		=> 	'select',
				'choices'    	=> $cc2_h_positions
			) );
			
			

			// Use dark colors - a small Heading
			$wp_customize->add_setting( 'heading_nav_use_dark_colors', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Label( $wp_customize, 'heading_nav_use_dark_colors', array(
				'label' 		=> 	__('Use dark colors?', 'cc2'),
				'type' 			=> 	'label',
				'section' 		=> 	'nav',
				'priority'		=> 	60,
			) ) );

			// Header Top Nav - Dark Colors

			
			// Header Top Nav - Dark Colors
			$wp_customize->add_setting( 'color_scheme_top_nav', array(
				'default'		=>	'light',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('color_scheme_top_nav', array(
				'label'    		=> 	__('Top nav color scheme', 'cc2'),
				'section'  		=> 	'nav',
				'type'     		=> 	'select',
				'choices'		=> array(
					'dark' => 'Dark',
					'light' => 'Light (Default)',
					'custom' => 'Custom',
				),
				'priority'		=> 	81,
			) );
			
			 // Header Top Nav - Dark Colors
			$wp_customize->add_setting( 'color_scheme_bottom_nav', array(
				'default'		=>	'light',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('color_scheme_bottom_nav', array(
				'label'    		=> 	__('Bottom nav color scheme', 'cc2'),
				'section'  		=> 	'nav',
				'type'     		=> 	'select',
				'choices'		=> array(
					'dark' => 'Dark',
					'light' => 'Light (Default)',
					'custom' => 'Custom',
				),
				'priority'		=> 	83,
			) );

			// Header Bottom Nav - Dark Colors
			$wp_customize->add_setting( 'info_dark_nav', array(
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control(new Description( $wp_customize, 'info_dark_nav', array(
				'label'    		=> 	sprintf( __('<strong>Warning:</strong> Changing any of the above options is likely going to <strong>reset</strong> your current navigation color settings. It\'s suggested to either save the current customizer setup or <a href="%s">backup your current settings</a> before-hand.', 'cc2'), admin_url( apply_filters('cc2_tab_admin_url', 'themes.php?page=cc2-settings&tab=backup') ) ),
				'section'  		=> 	'nav',
				'priority'		=> 	101,
			) ) );
	 
			


		/**
		 * Top nav color settings
		 * 
		 * TODO: Add some kind of additional sectioning / partitioning .. or maybe an "Advanced Settings" or "Quick / Advanced Settings" switch
		 */

		$nav_section_priority = 170;

			// top nav background color
			$wp_customize->add_setting('top_nav_background_color', array(
			'default'           	=> '#2f2f2f',
			'capability'        	=> 'edit_theme_options',
			'transport'   			=> 'refresh',
			'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'top_nav_background_color', array(
			'label'    				=> __('Top Nav Background Color', 'cc2'),
			'section'  				=> 'nav',
			'priority'				=> $nav_section_priority,
			) ) );
			$nav_section_priority++;
			
			// top nav text color
			$wp_customize->add_setting('top_nav_text_color', array(
			'default'           	=> '#a9a9a9',
			'capability'        	=> 'edit_theme_options',
			'transport'   			=> 'refresh',
			'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'top_nav_text_color', array(
			'label'    				=> __('Top Nav Font Color', 'cc2'),
			'section'  				=> 'nav',
			'priority'				=> $nav_section_priority,
			) ) );
			$nav_section_priority++;
			
			
			// top nav hover text color
			$wp_customize->add_setting('top_nav_hover_text_color', array(
			'default'           	=> '#fff',
			'capability'        	=> 'edit_theme_options',
			'transport'   			=> 'refresh',
			'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'top_nav_hover_text_color', array(
			'label'    				=> __('Top Nav Hover Font Color', 'cc2'),
			'section'  				=> 'nav',
			'priority'				=> $nav_section_priority,
			) ) );
			
			$nav_section_priority+=10;
			
		/**
		 * Secondary nav color settings
		 */
			// secondary nav background color
			$wp_customize->add_setting('secondary_nav_background_color', array(
			'default'           	=> '#2f2f2f',
			'capability'        	=> 'edit_theme_options',
			'transport'   			=> 'refresh',
			'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'secondary_nav_background_color', array(
			'label'    				=> __('Secondary Nav Background Color', 'cc2'),
			'section'  				=> 'nav',
			'priority'				=> $nav_section_priority,
			) ) );
			$nav_section_priority++;
			
			// secondary nav text color
			$wp_customize->add_setting('secondary_nav_text_color', array(
			'default'           	=> '#a9a9a9',
			'capability'        	=> 'edit_theme_options',
			'transport'   			=> 'refresh',
			'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'secondary_nav_text_color', array(
			'label'    				=> __('Secondary Nav Font Color', 'cc2'),
			'section'  				=> 'nav',
			'priority'				=> $nav_section_priority,
			) ) );
			$nav_section_priority++;
			
			// secondary nav hover text color
			$wp_customize->add_setting('secondary_nav_hover_text_color', array(
			'default'           	=> '#fff',
			'capability'        	=> 'edit_theme_options',
			'transport'   			=> 'refresh',
			'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
			'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'secondary_nav_hover_text_color', array(
			'label'    				=> __('Secondary Nav Hover Font Color', 'cc2'),
			'section'  				=> 'nav',
			'priority'				=> $nav_section_priority,
			) ) );
			$nav_section_priority++;

			
		}
		
		/**
		 * Seperate Branding section (before navigation, below header section)
		 */
		
		function section_branding( $wp_customize ) {
			extract( $this->prepare_variables() );

			$branding_section_priority = 70;
	
			if( !empty( $this->arrSectionPriorities['section_branding'] ) ) {
				$branding_section_priority = $this->arrSectionPriorities['section_branding'];
			}
		 
			$wp_customize->add_section( 'branding', array(
				'title' =>	__( 'Branding', 'cc2' ),
				'priority' => $branding_section_priority,
			) );
			
			$branding_section_priority+=5;
		
			/*
			$_debug = array(
				'branding_section_priority' => self::$customizer_section_priority,
			);
		
			// debug label
			$wp_customize->add_setting( 'branding_section_debug', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Label( $wp_customize, 'branding_section_debug', array(
				'label' 		=> 	__('Debug:', 'cc2') . ' ' . print_r( $_debug , true ),
				'type' 			=> 	'label',
				'section' 		=> 	'branding',
				'priority'		=> 	$branding_section_priority,
			) ) );
			$branding_section_priority++;
			*/
		
		
			// Add Branding - A small Heading
			$wp_customize->add_setting( 'add_nav_brand', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Label( $wp_customize, 'add_nav_brand', array(
				'label' 		=> 	__('Add your branding?', 'cc2'),
				'type' 			=> 	'label',
				'section' 		=> 	'branding',
				'priority'		=> 	$branding_section_priority++,
			) ) );
			//$branding_section_priority++;  
			
			// Header Top Nav - Add Branding
			/**
			 * NOTE: Missing default value!
			 */
			$wp_customize->add_setting( 'top_nav_brand', array(
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'default'		=> false,
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('top_nav_brand', array(
				'label'    		=> 	__('for top nav', 'cc2'),
				'section'  		=> 	'branding',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$branding_section_priority++,
				'value' 		=> 1,
			) );
			//$branding_section_priority++;

			// Header Top Nav - Branding: Color
			$wp_customize->add_setting( 'top_nav_brand_text_color', array(
				'default'           	=> '#a9a9a9',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
				'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'top_nav_brand_text_color', array(
				'label'    		=> 	__('Top nav branding Font Color', 'cc2'),
				'section'  		=> 	'branding',
				'priority'		=> 	$branding_section_priority++,
			) ) );
			//$branding_section_priority++;
			
			
			// Header Top Nav - Branding: Image instead text
			/**
			 * FIXME: default value seems to be wrong / impossible / bug-prone
			 */
			
			$wp_customize->add_setting('top_nav_brand_image', array(
				'default'           	=> '',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			
			$wp_customize->add_control( new  WP_Customize_Image_Control($wp_customize, 'top_nav_brand_image', array(
				'label'    				=> __('Top nav brand image', 'cc2'),
				'section'  				=> 'branding',
				'priority'				=> $branding_section_priority++,
			) ) );
			//$branding_section_priority++;
			
			
			// Branding: Header Bottom Nav
			
			$branding_section_priority+=5;
			
			/**
			 * NOTE: missing default value
			 */

			// Header Bottom Nav - Add Branding
			$wp_customize->add_setting( 'bottom_nav_brand', array(
				'default'		=> true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('bottom_nav_brand', array(
				'label'    		=> 	__('for bottom nav', 'cc2'),
				'section'  		=> 	'branding',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$branding_section_priority++,
				'value' 		=> 1,
			) );
			//$branding_section_priority++;
		   
			// Header Top Nav - Branding: Color
			$wp_customize->add_setting( 'bottom_nav_brand_text_color', array(
				'default'           	=> '#a9a9a9',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
				'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'bottom_nav_brand_text_color', array(
				'label'    		=> 	__('Bottom nav branding Font Color', 'cc2'),
				'section'  		=> 	'branding',
				'priority'		=> 	$branding_section_priority++,
			) ) );
			//$branding_section_priority++;
			
			/**
			 * NOTE: default value seems to be wrong as well
			 */
			
			$wp_customize->add_setting('bottom_nav_brand_image', array(
				'default'           	=> '',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			
			$wp_customize->add_control( new  WP_Customize_Image_Control($wp_customize, 'bottom_nav_brand_image', array(
				'label'    				=> __('Bottom nav brand image', 'cc2'),
				'section'  				=> 'branding',
				'priority'				=> $branding_section_priority++,
			) ) );

		}
		
		
		/**
		 * Content Section
		 */
		
		function section_content( $wp_customize ) {
			extract( $this->prepare_variables() );

			$priority = 260;
			
			if( !empty( $this->arrSectionPriorities['section_content'] ) ) {
				$priority = $this->arrSectionPriorities['section_content'];
			}
			

			$wp_customize->add_section( 'content', array(
				'title' =>	__( 'Content', 'cc2' ),
				/*'priority' => 260,*/
				'priority' => $priority,
			) );
			
			

				/*
				 * NOTE: The theme_check plugin is too dumb programmed to recognize commented out code.
				 // Hide all Content
				$wp_customize->add_setting( 'hide_page_content', array(
					'default'       =>  false,
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control('hide_page_content', array(
					'label'    		=> 	__('Hide all content on all (!) pages', 'cc2'),
					'section'  		=> 	'content',
					'type'     		=> 	'checkbox',
					'priority'		=> 	261,
				) );*/


			/**
			 * 
					- Hide Page Titles (checkboxes, "hide on...": All, Homepage, Archives, Post, Page, Attachment ) --> CSS output  => NOPE. php-side!
					- Center Content Titles (checkboxes, "center on... ": All, Homepage, Archives, Post, Page, Attachment ) --> CSS output
			*/
			$display_page_title_priority = 262;
			
			/*
			$_debug = array(
				'self::$customizer_section_priority' => self::$customizer_section_priority,
				'display_page_title_priority' => $display_page_title_priority,
			);
			
			// debug label
			$wp_customize->add_setting( 'content_section_debug', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Label( $wp_customize, 'content_section_debug', array(
				'label' 		=> 	__('Debug:', 'cc2') . ' ' . print_r( $_debug , true ),
				'type' 			=> 	'label',
				'section' 		=> 	'content',
				'priority'		=> 	$display_page_title_priority,
			) ) );
			$display_page_title_priority++;
			*/
			
			// Display Header
			/**
			 * NOTE: Missing default
			 */
			$wp_customize->add_setting( 'display_page_title_heading', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Label( $wp_customize, 'display_page_title_heading', array(
				'label' 		=> 	__('Display Page Title ...', 'cc2'),
				'type' 			=> 	'label',
				'section' 		=> 	'content',
				'priority'		=> 	$display_page_title_priority,
			) ) );
			$display_page_title_priority++;

			// Display Header on Home
			$wp_customize->add_setting( 'display_page_title[home]', array(
				'default'		=>	true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('display_page_title[home]', array(
				'label'    		=> 	__('on homepage', 'cc2'),
				'section'  		=> 	'content',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$display_page_title_priority,
				'value' 		=> 1,
			) );
			$display_page_title_priority++;

			// Display Header on Posts
			$wp_customize->add_setting( 'display_page_title[posts]', array(
				'default'		=>	true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('display_page_title[posts]', array(
				'label'    		=> 	__('on posts', 'cc2'),
				'section'  		=> 	'content',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$display_page_title_priority,
				'value' 		=> 1,
			) );
			$display_page_title_priority++;

			// Display Header on Pages
			$wp_customize->add_setting( 'display_page_title[pages]', array(
				'default'		=>	true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('display_page_title[pages]', array(
				'label'    		=> 	__('on pages', 'cc2'),
				'section'  		=> 	'content',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$display_page_title_priority,
				'value' 		=> 1,
			) );
			$display_page_title_priority++;
			

			// Display Header on Archive
			$wp_customize->add_setting( 'display_page_title[archive]', array(
				'default'		=>	true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('display_page_title[archive]', array(
				'label'    		=> 	__('on archive', 'cc2'),
				'section'  		=> 	'content',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$display_page_title_priority,
				'value' 		=> 1,
			) );
			$display_page_title_priority++;

			// Display Header on Search
			$wp_customize->add_setting( 'display_page_title[search]', array(
				'default'		=>	true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('display_page_title[search]', array(
				'label'    		=> 	__('on search', 'cc2'),
				'section'  		=> 	'content',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$display_page_title_priority,
				'value' 		=> 1,
			) );
			$display_page_title_priority++;
			

			// Display Header on 404
			$wp_customize->add_setting( 'display_page_title[error]', array(
				'default'		=>	true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('display_page_title[error]', array(
				'label'    		=> 	__('on 404: not-found', 'cc2'),
				'section'  		=> 	'content',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$display_page_title_priority,
				'value' 		=> 1,
			) );
			
			$display_page_title_priority++;
		
		// Center titles
			$center_title_priority = $display_page_title_priority + 1;
		
		
			$wp_customize->add_setting( 'center_title_heading', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Label( $wp_customize, 'center_title_heading', array(
				'label' 		=> 	__('Center Page Title ...', 'cc2'),
				'type' 			=> 	'label',
				'section' 		=> 	'content',
				'priority'		=> 	$center_title_priority,
			) ) );
			$center_title_priority++;

			// center Header on Home
			$wp_customize->add_setting( 'center_title[global]', array(
				'default'		=>	false,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('center_title[global]', array(
				'label'    		=> 	__('everywhere', 'cc2'),
				'section'  		=> 	'content',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$center_title_priority,
				'value' 		=> 1,
			) );
			$center_title_priority++;

			// center Header on Home
			$wp_customize->add_setting( 'center_title[home]', array(
				'default'		=>	false,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('center_title[home]', array(
				'label'    		=> 	__('on homepage', 'cc2'),
				'section'  		=> 	'content',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$center_title_priority,
				'value' 		=> 1,
			) );
			$center_title_priority++;

			// center Header on Posts
			$wp_customize->add_setting( 'center_title[posts]', array(
				'default'		=>	false,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('center_title[posts]', array(
				'label'    		=> 	__('on posts', 'cc2'),
				'section'  		=> 	'content',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$center_title_priority,
				'value' 		=> 1,
			) );
			$center_title_priority++;

			// center Header on Pages
			$wp_customize->add_setting( 'center_title[pages]', array(
				'default'		=>	false,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('center_title[pages]', array(
				'label'    		=> 	__('on pages', 'cc2'),
				'section'  		=> 	'content',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$center_title_priority,
				'value' 		=> 1,
			) );
			$center_title_priority++;
			

			// center Header on Archive
			$wp_customize->add_setting( 'center_title[archive]', array(
				'default'		=>	false,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('center_title[archive]', array(
				'label'    		=> 	__('on archive', 'cc2'),
				'section'  		=> 	'content',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$center_title_priority,
				'value' 		=> 1,
			) );
			$center_title_priority++;

			// center Header on Search
			$wp_customize->add_setting( 'center_title[search]', array(
				'default'		=>	false,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('center_title[search]', array(
				'label'    		=> 	__('on search', 'cc2'),
				'section'  		=> 	'content',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$center_title_priority,
				'value' 		=> 1,
			) );
			$center_title_priority++;
			

			// center Header on 404
			$wp_customize->add_setting( 'center_title[error]', array(
				'default'		=>	false,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('center_title[error]', array(
				'label'    		=> 	__('on 404: not-found', 'cc2'),
				'section'  		=> 	'content',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$center_title_priority,
				'value' 		=> 1,
			) );
			
			$center_title_priority++;
		
		}
		
		/** 
		 * WooCommerce Support Section
		 * 
		 * NOTE: Currently not used.
		 */
		
		function section_wc( $wp_customize ) {
			extract( $this->prepare_variables() );
			
			$priority = 280; // content = 260
			
			if( !empty( $this->arrSectionPriorities['section_wc_support'] ) ) {
				$priority = $this->arrSectionPriorities['section_wc_support'];
			}
			
			$wp_customize->add_section( 'wc_support', array(
				'title' =>	__( 'WooCommerce', 'cc2' ),
				'priority' => $priority,
			) );
			
			$wc_priority = $priority;
			
			// display woocommerce sidebar - default: NOT
			$wp_customize->add_setting( 'wc_display_sidebar', array(
				'default'		=>	false,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('wc_display_sidebar', array(
				'label'    		=> 	__('Show WooCommerce Sidebar', 'cc2'),
				'section'  		=> 	'wc_support',
				'type'     		=> 	'checkbox',
				'priority'		=> 	$wc_priority++,
				'value' 		=> 1,
			) );
			
			// fix pagination bug in WooCommerce < 2.3
			if( substr( WC_VERSION, 0, 1) == '2' && intval(substr( WC_VERSION, 2, 1 ) ) < 3 ) {
			
				$wp_customize->add_setting( 'wc_fix_pagination', array(
					'default'		=>	true,
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('wc_fix_pagination', array(
					'label'    		=> 	__('Fix Pagination Query Bug', 'cc2'),
					'section'  		=> 	'wc_support',
					'type'     		=> 	'checkbox',
					'priority'		=> 	$wc_priority++,
					'value' 		=> 1,
				) );
			}
			
			
		}
		
		/**
		 * Sidebars Section
		 */
		
		function section_layouts( $wp_customize ) {
			extract( $this->prepare_variables() );
			
			$layout_choices = array(
				'right' 		=> 'Sidebar right',
				'left' 	        => 'Sidebar left',
				'left-right'    => 'Sidebar left and right',
				'fullwidth'     => 'Fullwidth'
			);
			
			$layout_choices_all = array('default' => 'Default' ) + $layout_choices;
			
			/*
			array(
				'default' 	    => 'Default',
				'right' 		=> 'Sidebar right',
				'left' 	        => 'Sidebar left',
				'left-right'    => 'Sidebar left and right',
				'fullwidth'     => 'Fullwidth'
			);*/

			$priority = 80;

			if( !empty( $this->arrSectionPriorities['section_layouts'] ) ) {
				$priority = $this->arrSectionPriorities['section_layouts'];
			}


			$wp_customize->add_section( 'layouts', array(
				'title'         => 	__( 'Sidebar Layouts', 'cc2' ),
				'priority'      => 	$priority,
			) );

			// Layouts Description - A quick note
			$wp_customize->add_setting( 'layouts_note', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Description( $wp_customize, 'layouts_note', array(
				'label' 		=> 	__('Where do you like your sidebars? *Collapse&nbsp;options or zoom out if your display is too small*', 'cc2'),
				'type' 			=> 	'description',
				'section' 		=> 	'layouts',
				'priority'		=> 	10,
			) ) );

			// Default Layout
			$wp_customize->add_setting( 'default_layout', array(
				'default'       => 	'left',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control( 'default_layout', array(
				'label'   		=> 	__('Default Layout', 'cc2'),
				'section' 		=> 	'layouts',
				'priority'		=> 	20,
				'type'    		=> 	'select',
				'choices'    	=> 	$layout_choices,
			) );
			
		

			// Default Page Layout
			$wp_customize->add_setting( 'default_page_layout', array(
				'default'       => 	'default',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control( 'default_page_layout', array(
				'label'   		=> 	__('Page Layout', 'cc2'),
				'section' 		=> 	'layouts',
				'priority'		=> 	40,
				'type'    		=> 	'select',
				'choices'    	=> 	$layout_choices_all,
			) );

			// Default Post Layout
			$wp_customize->add_setting( 'default_post_layout', array(
				'default'       => 	'default',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control( 'default_post_layout', array(
				'label'   		=> 	__('Post Layout', 'cc2'),
				'section' 		=> 	'layouts',
				'priority'		=> 	60,
				'type'    		=> 	'select',
				'choices'    	=> 	$layout_choices_all,
			) );

			// Default Archive Layout
			$wp_customize->add_setting( 'default_archive_layout', array(
				'default'       => 	'default',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control( 'default_archive_layout', array(
				'label'   		=> 	__('Archive Layout', 'cc2'),
				'section' 		=> 	'layouts',
				'priority'		=> 	80,
				'type'    		=> 	'select',
				'choices'    	=> 	$layout_choices_all,
			) );
			
			// add woocommerce layouts
			if( class_exists( 'WooCommerce' ) != false ) :
				$iLayoutPriorityWooCommerce = 81;
				
				// Default Shop main page (= Archives) Layout
				$wp_customize->add_setting( 'wc_layout_archive', array(
					'default'       => 	'default',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control( 'wc_layout_archive', array(
					'label'   		=> 	__('WooCommerce Products List Layout', 'cc2'),
					'section' 		=> 	'layouts',
					'priority'		=> 	$iLayoutPriorityWooCommerce++,
					'type'    		=> 	'select',
					'choices'    	=> 	$layout_choices_all,
				) );
				
				// Default Single Product Layout
				$wp_customize->add_setting( 'wc_layout_single_product', array(
					'default'       => 	'default',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control( 'wc_layout_single_product', array(
					'label'   		=> 	__('WooCommerce Single Product Layout', 'cc2'),
					'section' 		=> 	'layouts',
					'priority'		=> 	$iLayoutPriorityWooCommerce++,
					'type'    		=> 	'select',
					'choices'    	=> 	$layout_choices_all,
				) );
				
				
				/**
				 * Simple version
				 */
				
				// display woocommerce sidebar - default: NOT
				$wp_customize->add_setting( 'wc_display_sidebar', array(
					'default'		=>	false,
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('wc_display_sidebar', array(
					'label'    		=> 	__('Show WooCommerce Sidebar', 'cc2'),
					'section'  		=> 	'layouts',
					'type'     		=> 	'checkbox',
					'priority'		=> 	$iLayoutPriorityWooCommerce++,
					'value' 		=> 1,
				) );
				
				/*
				 * NOTE: Future version aka "complex edition"
				 * 
				// Change position of the WooCommerce "shop" sidebar
				$wp_customize->add_setting( 'wc_sidebar_position', array(
					'default'       => 	'disabled',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control( 'wc_sidebar_position', array(
					'label'   		=> 	__('Position of the WooCommerce Shop sidebar', 'cc2'),
					'section' 		=> 	'layouts',
					'priority'		=> 	$iLayoutPriorityWooCommerce++,
					'type'    		=> 	'select',
					'choices'    	=> 	array(
						'disabled' => __('Disabled', 'cc2'),
						'header_top' => __('Before', 'cc2' ) . ' ' . __( 'Header Widget Area', 'cc2' ),
						'header_bottom' => __('After', 'cc2' ) . ' ' . __( 'Header Widget Area', 'cc2' ),
						'footer_top' 	=> __('Before', 'cc2' ) . ' ' . __( 'Footer Widget Area', 'cc2' ),
						'footer_bottom' => __('After', 'cc2' ) . ' ' . __( 'Footer Widget Area', 'cc2' ),
					),
				) );*/
				
			endif; // end woocommerce integration
			
			
			// change sidebar columns (default: 4)
			
			

			// Hide Left Sidebar On Phones?
			$wp_customize->add_setting( 'hide_left_sidebar_on_phones', array(
				'default'       =>  true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('hide_left_sidebar_on_phones', array(
				'label'    		=> 	sprintf( __('Hide %s sidebar on phones?', 'cc2'), __('left', 'cc2') ),
				'section'  		=> 	'layouts',
				'type'     		=> 	'checkbox',
				'priority'		=> 	140,
				'value' 		=> 1,
			) );

			// Hide Right Sidebar On Phones?
			$wp_customize->add_setting( 'hide_right_sidebar_on_phones', array(
				'default'       =>  false,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('hide_right_sidebar_on_phones', array(
				'label'    		=> 	sprintf( __('Hide %s sidebar on phones?', 'cc2'), __('right', 'cc2') ),
				'section'  		=> 	'layouts',
				'type'     		=> 	'checkbox',
				'priority'		=> 	120,
				'value' 		=> 1,
			) );
			
		}
		
		/**
		 * Widget section
		 */
		
		function section_widgets( $wp_customize, $params = false ) {
	
			extract( $this->prepare_variables() );
	
			$strSectionName = 'widget_settings';
			//$strPanelID = 'my_widgets_panel';
			$strPanelID = '';
			
			if( !empty( $params ) && isset( $params['panel_id'] ) ) {
				$strPanelID = $params['panel_id'];
			}
	
			$widget_section_priority = 140;
			if( !empty( $this->arrSectionPriorities['section_widgets'] ) ) {
				$widget_section_priority = $this->arrSectionPriorities['section_widgets'];
			}

			/*
			$wp_customize->add_panel( $strPanelID, array(
				'capability' => 'edit_theme_options',
				'title' => __('My Widget Settings'),
			) );


			$wp_customize->add_section( $strSectionName, array(
				'title'         => 	__('Widget Settings', 'cc2'),
				'priority'      => $widget_section_priority,
				'panel' => $strPanelID,
			) );*/
			

			$wp_customize->add_section( $strSectionName, array(
				'title'         => 	__('Widget Settings', 'cc2'),
				'priority'      => $widget_section_priority,
				'panel'	=> $strPanelID,
			) );

			$widget_section_priority+=2;
		
			// The widgets Title attributes - A Quick Note
			
			$wp_customize->add_setting( 'widget_title_attributes_note', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Description( $wp_customize, 'widget_title_attributes_note', array(
				'label' 		=> 	__('Get more options to style your header and footer widgets with the CC2 Premium Pack', 'cc2'),
				'type' 			=> 	'description',
				'section' 		=> 	$strSectionName,
				'priority'		=> 	$widget_section_priority,
			) ) );
			$widget_section_priority+=2; // 144
			
			// widget title Font Color
			$wp_customize->add_setting('widget_title_text_color', array(
				'default'           	=> '',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
				'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'widget_title_text_color', array(
				'label'    				=> __('Title Font Color', 'cc2'),
				'section'  				=> $strSectionName,
				'priority'				=> $widget_section_priority,
			) ) );
			$widget_section_priority+=2; // 146
			
			// widget title background color
			$wp_customize->add_setting('widget_title_background_color', array(
				'default'           	=> '',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
				'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'widget_title_background_color', array(
				'label'    				=> __('Title Background Color', 'cc2'),
				'section'  				=> $strSectionName,
				'priority'				=> $widget_section_priority,
			) ) );
			$widget_section_priority+=2; // 148

			// Widget title Font Size
			
			$wp_customize->add_setting('widget_title_font_size', array(
				'default' 		=> '',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('widget_title_font_size', array(
				'label'      	=> __('Title Font Size', 'cc2'),
				'section'    	=> $strSectionName,
				'priority'   	=> $widget_section_priority,
			) );
			$widget_section_priority++;
			
			
			// widget container attributes:  background color, link color, link color hover 
			$widget_section_priority = 155;
			
			// widget background color 
			$wp_customize->add_setting('widget_background_color', array(
				'default'           	=> '',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
				'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'widget_background_color', array(
				'label'    				=> __('Widget Background Color', 'cc2'),
				'section'  				=> $strSectionName,
				'priority'				=> $widget_section_priority,
			) ) );
			$widget_section_priority++;
			

			// widget link color 
			$wp_customize->add_setting('widget_link_color', array(
				'default'           	=> '',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
				'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'widget_link_color', array(
				'label'    				=> __('Widget Link Color', 'cc2'),
				'section'  				=> $strSectionName,
				'priority'				=> $widget_section_priority,
			) ) );
			$widget_section_priority++;

			
			// widget link hover color
			$wp_customize->add_setting('widget_link_text_hover_color', array(
				'default'           	=> '',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
				'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'widget_link_text_hover_color', array(
				'label'    				=> __('Widget Link Text Hover Color', 'cc2'),
				'section'  				=> $strSectionName,
				'priority'				=> $widget_section_priority,
			) ) );
			$widget_section_priority++;
			
		}
	
		/**
		 * Typography Section
		 */
		function section_typography( $wp_customize ) {
			extract( $this->prepare_variables() );
		
			$priority = 110;
			if( !empty( $this->arrSectionPriorities['section_typography'] ) ) {
				$priority = $this->arrSectionPriorities['section_typography'];
			}
		

			$wp_customize->add_section( 'typography', array(
				'title'         => 	'Typography',
				'priority'      => 	$priority,
			) );

			if( ! defined( 'CC2_LESSPHP' ) ) {

				// A Quick Note on Bootstrap Variables
				$wp_customize->add_setting( 'bootstrap_typography_note', array(
					'capability'    => 	'edit_theme_options',
					'sanitize_callback' => 'cc2_Pasteur_none'
				) );
				$wp_customize->add_control( new Description( $wp_customize, 'bootstrap_typography_note', array(
					'label' 		=> 	sprintf( __('Most Typography just work with Bootstrap Variables, which cannot be compiled within the theme, as this is plugin territory. Get all typography options with the <a href="%s" target="_blank">premium extension.</a>', 'cc2'), 'http://themekraft.com/store/custom-community-2-premium-pack/' ),
					'type' 			=> 	'description',
					'section' 		=> 	'typography',
					'priority'		=> 	115,
				) ) );

			}

			// Headline Font Family
			$wp_customize->add_setting( 'title_font_family', array(
				'default'       => 	'Ubuntu Condensed',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'postMessage',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control( 'title_font_family', array(
				'label'   		=> 	__('Headline Font Family', 'cc2'),
				'section' 		=> 	'typography',
				'priority'		=> 	120,
				'type'    		=> 	'select',
				'choices'    	=> 	$cc2_font_family
			) );

			// Title Font Weight
			$wp_customize->add_setting( 'title_font_weight', array(
				'default'       =>  false,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('title_font_weight', array(
				'label'    		=> 	__('Bold', 'cc2'),
				'section'  		=> 	'typography',
				'type'     		=> 	'checkbox',
				'priority'		=> 	140,
				'value' 		=> 1,
			) );

			// Title Font Style
			$wp_customize->add_setting( 'title_font_style', array(
				'default'       =>  false,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('title_font_style', array(
				'label'    		=> 	__('Italic', 'cc2'),
				'section'  		=> 	'typography',
				'type'     		=> 	'checkbox',
				'priority'		=> 	160,
				'value' 		=> 1,
			) );

			// Headline Font Color
			$wp_customize->add_setting('title_font_color', array(
				'default'           	=> '',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
				'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'title_font_color', array(
				'label'    				=> __('Headline Font Color', 'cc2'),
				'section'  				=> 'typography',
				'priority'				=> 180,
			) ) );

			// The Headline Font Sizes - Small Heading
			$wp_customize->add_setting( 'titles_font_sizes', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Label( $wp_customize, 'titles_font_sizes', array(
				'label' 		=> 	__('Headline Font Sizes', 'cc2'),
				'type' 			=> 	'label',
				'section' 		=> 	'typography',
				'priority'		=> 	200,
			) ) );

			// The Titles Font Sizes - A Quick Note
			$wp_customize->add_setting( 'titles_font_sizes_note', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Description( $wp_customize, 'titles_font_sizes_note', array(
				'label' 		=> 	__('For displays from 768px and up', 'cc2'),
				'type' 			=> 	'description',
				'section' 		=> 	'typography',
				'priority'		=> 	210,
			) ) );

			// H1 Font Size
			$wp_customize->add_setting('h1_font_size', array(
				'default' 		=> '48px',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('h1_font_size', array(
				'label'      	=> __('H1', 'cc2'),
				'section'    	=> 'typography',
				'priority'   	=> 220,
			) );

			// H2 Font Size
			$wp_customize->add_setting('h2_font_size', array(
				'default' 		=> '32px',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('h2_font_size', array(
				'label'      	=> __('H2', 'cc2'),
				'section'    	=> 'typography',
				'priority'   	=> 240,
			) );

			// H3 Font Size
			$wp_customize->add_setting('h3_font_size', array(
				'default' 		=> '28px',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('h3_font_size', array(
				'label'      	=> __('H3', 'cc2'),
				'section'    	=> 'typography',
				'priority'   	=> 260,
			) );

			// H4 Font Size
			$wp_customize->add_setting('h4_font_size', array(
				'default' 		=> '24px',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('h4_font_size', array(
				'label'      	=> __('H4', 'cc2'),
				'section'    	=> 'typography',
				'priority'   	=> 280,
			) );

			// H5 Font Size
			$wp_customize->add_setting('h5_font_size', array(
				'default' 		=> '22px',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('h5_font_size', array(
				'label'      	=> __('H5', 'cc2'),
				'section'    	=> 'typography',
				'priority'   	=> 300,
			) );

			// H6 Font Size
			$wp_customize->add_setting('h6_font_size', array(
				'default' 		=> 	'20px',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('h6_font_size', array(
				'label'      	=> __('H6', 'cc2'),
				'section'    	=> 'typography',
				'priority'   	=> 320,
			) );

			
		}
		

	/**
	 * Footer Section
	 */
	
			
		function section_footer( $wp_customize ) {
			extract( $this->prepare_variables() );

			$footer_section_priority = 340;
			
			if( !empty( $this->arrSectionPriorities['section_footer'] ) ) {
				$footer_section_priority = $this->arrSectionPriorities['section_footer'];
			}
		 
			$wp_customize->add_section( 'footer', array(
				'title'         => 	'Footer',
				'priority'      => 	$footer_section_priority,
			) );
			$footer_section_priority++;

			// fullwidth footer
			/*
			 * - footer fullwidth background image (for the wrap!)  
				- footer fullwidth background color (with possibility for transparency) 
				- footer fullwidth border top color (with possibility for transparency) 
				- footer fullwidth border bottom color (with possibility for transparency) 
			*/
				
				
				
			// A Quick Note on Bootstrap Variables
			$wp_customize->add_setting( 'footer_fullwidth_note', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Labeled_Description( $wp_customize, 'footer_fullwidth_note', array(
				'label' 		=> 	 array(
					'title' 		=> __('Fullwidth Footer', 'cc2'), 
					'description' 	=> __('Attributes of the fullwidth footer', 'cc2'),
				),
				'type' 			=> 	'description',
				'section' 		=> 	'footer',
				'priority'		=> 	340,
			) ) );
			$footer_section_priority++;
		
			// footer fullwidth background image (footer fullwidth-wrap)
			$wp_customize->add_setting('footer_fullwidth_background_image', array(
				'default'           	=> '',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			
			$wp_customize->add_control( new  WP_Customize_Image_Control($wp_customize, 'footer_fullwidth_background_image', array(
				'label'    				=> __('Background Image', 'cc2'),
				'section'  				=> 'footer',
				'priority'				=> $footer_section_priority,
			) ) );
			$footer_section_priority++;
			
		
			// footer fullwidth background color
			$wp_customize->add_setting('footer_fullwidth_background_color', array(
				'default'           	=> '#eee',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				'sanitize_callback' 	=> 'cc2_Pasteur_sanitize_hex_with_transparency',
				/*'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',*/
				'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			$wp_customize->add_control( new cc2_Customize_Color_Control($wp_customize, 'footer_fullwidth_background_color', array(
				'label'    				=> __('Background Color', 'cc2'),
				'section'  				=> 'footer',
				'priority'				=> $footer_section_priority,
			) ) );
			$footer_section_priority++;
			
		
			// footer fullwidth border top color
			$wp_customize->add_setting('footer_fullwidth_border_top_color', array(
				'default'           	=> '#ddd',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
				'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			$wp_customize->add_control( new cc2_Customize_Color_Control($wp_customize, 'footer_fullwidth_border_top_color', array(
				'label'    				=> __('Color of upper border', 'cc2'),
				'section'  				=> 'footer',
				'priority'				=> $footer_section_priority,
			) ) );
			$footer_section_priority++;
			
			
			// footer fullwidth border bottom color (it's actually the branding top color ^_^)
			$wp_customize->add_setting('footer_fullwidth_border_bottom_color', array(
				'default'           	=> '#333',
				'capability'        	=> 'edit_theme_options',
				'transport'   			=> 'refresh',
				'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
				'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
			) );
			$wp_customize->add_control( new cc2_Customize_Color_Control ($wp_customize, 'footer_fullwidth_border_bottom_color', array(
				'label'    				=> __('Color of lower border', 'cc2'),
				'section'  				=> 'footer',
				'priority'				=> $footer_section_priority,
			) ) );
			$footer_section_priority++;
		

		}
		
		function section_blog( $wp_customize ) {
			extract( $this->prepare_variables() );

			$priority = 380;
			
			if( !empty( $this->arrSectionPriorities['section_blog'] ) ) {
				$priority = $this->arrSectionPriorities['section_blog'];
			}

			// Blog Section
			$wp_customize->add_section( 'blog', array(
				'title'         => 	'Blog',
				'priority'      => 	$priority,
			) );

			// Blog Archive Loop Template
			$wp_customize->add_setting( 'cc_list_post_style', array(
				'default'       => 	'blog-style',
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control( 'cc_list_post_style', array(
				'label'   		=> 	__('Blog Archive View - List Post Style', 'cc2'),
				'section' 		=> 	'blog',
				'priority'		=> 	20,
				'type'    		=> 	'select',
				'choices'    	=> 	$cc_loop_templates
			) );

			// Loop Designer Ready! - A Quick Note
			$wp_customize->add_setting( 'loop_designer_note', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Description( $wp_customize, 'loop_designer_note', array(
				'label' 		=> 	__('Loop-Designer-Ready! Get more loop templates available here, which you can easily customize, or simply create new ones. Get full control of how your post listings look with the <a href="http://themekraft.com/store/customize-wordpress-loop-with-tk-loop-designer/" target="_blank">TK Loop Designer Plugin</a>.', 'cc2'),
				'type' 			=> 	'description',
				'section' 		=> 	'blog',
				'priority'		=> 	40,
			) ) );

			// Blog Archive Post Meta - Small Heading
			$wp_customize->add_setting( 'blog_archive_post_meta', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Label( $wp_customize, 'blog_archive_post_meta', array(
				'label' 		=> 	__('Blog Archive View - Display Post Meta', 'cc2'),
				'type' 			=> 	'label',
				'section' 		=> 	'blog',
				'priority'		=> 	60,
			) ) );

			// Blog Archive View - Show date
			$wp_customize->add_setting( 'show_date', array(
				'default'		=>	true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('show_date', array(
				'label'    		=> 	__('show date', 'cc2'),
				'section'  		=> 	'blog',
				'type'     		=> 	'checkbox',
				'priority'		=> 	80,
				'value' 		=> 1,
			) );

			// Blog Archive View - Show category
			$wp_customize->add_setting( 'show_category', array(
				'default'		=>	true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('show_category', array(
				'label'    		=> 	__('show category', 'cc2'),
				'section'  		=> 	'blog',
				'type'     		=> 	'checkbox',
				'priority'		=> 	100,
				'value' 		=> 1,
			) );

			// Blog Archive View - Show author
			$wp_customize->add_setting( 'show_author', array(
				'default'		=>	true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('show_author', array(
				'label'    		=> 	__('show author', 'cc2'),
				'section'  		=> 	'blog',
				'type'     		=> 	'checkbox',
				'priority'		=> 	120,
				'value' 		=> 1,
			) );

			// Blog Archive View - Show author avatar
			$wp_customize->add_setting( 'show_author_image[archive]', array(
				'default'		=>	false,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('show_author_image[archive]', array(
				'label'    		=> 	__('show author image / avatar', 'cc2'),
				'section'  		=> 	'blog',
				'type'     		=> 	'checkbox',
				'priority'		=> 	130,
				'value' 		=> 1,
			) );


			// Blog Single Post Meta - Small Heading
			$wp_customize->add_setting( 'blog_single_post_meta', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Label( $wp_customize, 'blog_single_post_meta', array(
				'label' 		=> 	__('Blog Single View - Display Post Meta', 'cc2'),
				'type' 			=> 	'label',
				'section' 		=> 	'blog',
				'priority'		=> 	160,
			) ) );

			// Blog Single View - Show date
			$wp_customize->add_setting( 'single_show_date', array(
				'default'		=>	true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('single_show_date', array(
				'label'    		=> 	__('show date', 'cc2'),
				'section'  		=> 	'blog',
				'type'     		=> 	'checkbox',
				'priority'		=> 	180,
				'value' 		=> 1,
			) );

			// Blog Single View - Show category
			$wp_customize->add_setting( 'single_show_category', array(
				'default'		=>	true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('single_show_category', array(
				'label'    		=> 	__('show category', 'cc2'),
				'section'  		=> 	'blog',
				'type'     		=> 	'checkbox',
				'priority'		=> 	200,
				'value' 		=> 1,
			) );

			// Blog Single View - Show author
			$wp_customize->add_setting( 'single_show_author', array(
				'default'		=>	true,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('single_show_author', array(
				'label'    		=> 	__('show author', 'cc2'),
				'section'  		=> 	'blog',
				'type'     		=> 	'checkbox',
				'priority'		=> 	220,
				'value' 		=> 1,
			) );
			
			// single post / page 
			$wp_customize->add_setting( 'show_author_image[single_post]', array(
				'default'		=>	false,
				'capability'    => 	'edit_theme_options',
				'transport'   	=> 	'refresh',
				'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
			) );
			$wp_customize->add_control('show_author_image[single_post]', array(
				'label'    		=> 	__('show author image / avatar', 'cc2'),
				'section'  		=> 	'blog',
				'type'     		=> 	'checkbox',
				'priority'		=> 	240,
				'value' 		=> 1,
			) );

		}
		
		// Slider Section
		
		function section_slider( $wp_customize ) {
			extract( $this->prepare_variables() );
			
			$priority = 400;
			
			if( !empty( $this->arrSectionPriorities['section_slider'] ) ) {
				$priority = $this->arrSectionPriorities['section_slider'];
			}
			
			
			$wp_customize->add_section( 'cc_slider', array(
				'title'         => 	'Slideshow',
				'priority'      => 	$priority,
			) );

				// Create A Slideshow Note
				$wp_customize->add_setting( 'slider_create_note', array(
					'capability'    => 	'edit_theme_options',
					'sanitize_callback' => 'cc2_Pasteur_none'
				) );
				$wp_customize->add_control( new Description( $wp_customize, 'slider_create_note', array(
					'label' 		=> 	'<a href="'.admin_url('admin.php?page=cc2-settings&tab=slider-options') . '" target="_blank">Create a new slideshow </a>, or ..',
					'type' 			=> 	'description',
					'section' 		=> 	'cc_slider',
					'priority'		=> 	6,
				) ) );

				// Slider Template
				$wp_customize->add_setting( 'cc_slideshow_template', array(
					'default'       => 	'none',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control( 'cc_slideshow_template', array(
					'label'   		=> 	__('Select A Slideshow', 'cc2'),
					'section' 		=> 	'cc_slider',
					'priority'		=> 	8,
					'type'    		=> 	'select',
					'choices'    	=> 	$cc_slideshow_template
				) );

				// Slider Style
				$wp_customize->add_setting( 'cc2_slideshow_style', array(
					'default'       => 	'slides-only',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control( 'cc2_slideshow_style', array(
					'label'   		=> 	__('Slideshow Style', 'cc2'),
					'section' 		=> 	'cc_slider',
					'priority'		=> 	10,
					'type'    		=> 	'select',
					'choices'    	=> 	array(
						'slides-only'       => 'Slides only',
						'bubble-preview'    => 'Bubble Preview',
						'side-preview'  => 'Side Preview'
					)
				) );

				// Slider Position
				$wp_customize->add_setting( 'cc_slider_display', array(
					'default'       => 	'home',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control( 'cc_slider_display', array(
					'label'   		=> 	__('Display Slideshow', 'cc2'),
					'section' 		=> 	'cc_slider',
					'priority'		=> 	20,
					'type'    		=> 	'radio',
					'choices'    	=> 	array(
						'home' 			=> 'display on home',
						'bloghome' 		=> 'display on blog home',
						'always'		=> 'display always',
						'off'			=> 'turn off'
					)
				) );

				// Slider Position
				$wp_customize->add_setting( 'cc_slider_position', array(
					'default'       => 	'cc_after_header',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control( 'cc_slider_position', array(
					'label'   		=> 	__('Slideshow Position', 'cc2'),
					'section' 		=> 	'cc_slider',
					'priority'		=> 	40,
					'type'    		=> 	'select',
					'choices'    	=> 	$slider_positions
				) );

				// Effect on title
				$wp_customize->add_setting( 'slider_effect_title', array(
					'default'       => 	'bounceInLeft',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control( 'slider_effect_title', array(
					'label'   		=> 	__('Animation Effect on Caption Title', 'cc2'),
					'section' 		=> 	'cc_slider',
					'priority'		=> 	60,
					'type'    		=> 	'select',
					'choices'    	=> 	$cc2_animatecss_start_moves
				) );

				// Effect on excerpt
				$wp_customize->add_setting( 'slider_effect_excerpt', array(
					'default'       => 	'bounceInRight',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control( 'slider_effect_excerpt', array(
					'label'   		=> 	__('Animation Effect on Caption Text', 'cc2'),
					'section' 		=> 	'cc_slider',
					'priority'		=> 	80,
					'type'    		=> 	'select',
					'choices'    	=> 	$cc2_animatecss_start_moves
				) );

				// Text Align
				$wp_customize->add_setting( 'cc_slider_text_align', array(
					'default'       => 	'center',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control( 'cc_slider_text_align', array(
					'label'   		=> 	__('Text Align', 'cc2'),
					'section' 		=> 	'cc_slider',
					'priority'		=> 	120,
					'type'    		=> 	'select',
					'choices'    	=> 	$cc2_text_align
				) );

				// Caption Title Background Color
				$wp_customize->add_setting('caption_title_bg_color', array(
					'default'           	=> 'f2694b',
					'capability'        	=> 'edit_theme_options',
					'transport'   			=> 'refresh',
					'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
					'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
				) );
				$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'caption_title_bg_color', array(
					'label'    				=> __('Caption Title Background Color', 'cc2'),
					'section'  				=> 'cc_slider',
					'priority'				=> 160,
				) ) );

				// Caption Title Font Color
				$wp_customize->add_setting('caption_title_font_color', array(
					'default'           	=> 'fff',
					'capability'        	=> 'edit_theme_options',
					'transport'   			=> 'refresh',
					'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
					'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
				) );
				$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'caption_title_font_color', array(
					'label'    				=> __('Caption Title Font Color', 'cc2'),
					'section'  				=> 'cc_slider',
					'priority'				=> 180,
				) ) );

				// Caption Title Font Family
				$wp_customize->add_setting( 'caption_title_font_family', array(
					'default'       => 	'Ubuntu Condensed',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control( 'caption_title_font_family', array(
					'label'   		=> 	__('Caption Title Font Family', 'cc2'),
					'section' 		=> 	'cc_slider',
					'priority'		=> 	200,
					'type'    		=> 	'select',
					'choices'    	=> 	$cc2_font_family
				) );

				// Caption Title Font Weight
				/**
				 * NOTE: Missing default value
				 */
				$wp_customize->add_setting( 'caption_title_font_weight', array(
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('caption_title_font_weight', array(
					'label'    		=> 	__('Bold', 'cc2'),
					'section'  		=> 	'cc_slider',
					'type'     		=> 	'checkbox',
					'priority'		=> 	220,
					'value' 		=> 1,
				) );

				// Caption Title Font Style
				$wp_customize->add_setting( 'caption_title_font_style', array(
					'default'		=>	true,
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('caption_title_font_style', array(
					'label'    		=> 	__('Italic', 'cc2'),
					'section'  		=> 	'cc_slider',
					'type'     		=> 	'checkbox',
					'priority'		=> 	240,
					'value' 		=> 1,
				) );

				// Caption Title Text Shadow
				$wp_customize->add_setting( 'caption_title_shadow', array(
					'default'		=>	true,
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('caption_title_shadow', array(
					'label'    		=> 	__('Text Shadow', 'cc2'),
					'section'  		=> 	'cc_slider',
					'type'     		=> 	'checkbox',
					'priority'		=> 	250,
					'value' 		=> 1,
				) );

				// Caption Title Opacity
				$wp_customize->add_setting( 'caption_title_opacity', array(
					'default'        => '0.9',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );

				$wp_customize->add_control( 'caption_title_opacity', array(
					'label'   		=> __( 'Caption Title Opacity', 'cc2' ),
					'section' 		=> 'cc_slider',
					'priority'		=> 	260,
					'type'   		 => 'text',
				) );

				// Caption Text Background Color
				$wp_customize->add_setting('caption_text_bg_color', array(
					'default'           	=> 'FBFBFB',
					'capability'        	=> 'edit_theme_options',
					'transport'   			=> 'refresh',
					'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
					'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
				) );
				$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'caption_text_bg_color', array(
					'label'    				=> __('Caption Text Background Color', 'cc2'),
					'section'  				=> 'cc_slider',
					'priority'				=> 300,
				) ) );

				// Caption Text Font Color
				$wp_customize->add_setting('caption_text_font_color', array(
					'default'           	=> '333',
					'capability'        	=> 'edit_theme_options',
					'transport'   			=> 'refresh',
					'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
					'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
				) );
				$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'caption_text_font_color', array(
					'label'    				=> __('Caption Text Font Color', 'cc2'),
					'section'  				=> 'cc_slider',
					'priority'				=> 320,
				) ) );

				// Caption Text Font Family
				$wp_customize->add_setting( 'caption_text_font_family', array(
					'default'       => 	'',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );
				$wp_customize->add_control( 'caption_text_font_family', array(
					'label'   		=> 	__('Caption Text Font Family', 'cc2'),
					'section' 		=> 	'cc_slider',
					'priority'		=> 	340,
					'type'    		=> 	'select',
					'choices'    	=> 	$cc2_font_family
				) );

				// Caption Text Font Weight
				$wp_customize->add_setting( 'caption_text_font_weight', array(
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('caption_text_font_weight', array(
					'label'    		=> 	__('Bold', 'cc2'),
					'section'  		=> 	'cc_slider',
					'type'     		=> 	'checkbox',
					'priority'		=> 	360,
					'value' 		=> 1,
				) );

				// Caption Text Font Style
				$wp_customize->add_setting( 'caption_text_font_style', array(
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('caption_text_font_style', array(
					'label'    		=> 	__('Italic', 'cc2'),
					'section'  		=> 	'cc_slider',
					'type'     		=> 	'checkbox',
					'priority'		=> 	380,
					'value' 		=> 1,
				) );

				// Caption Text Shadow
				$wp_customize->add_setting( 'caption_text_shadow', array(
					'default'		=>	false,
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' 	=>  'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('caption_text_shadow', array(
					'label'    		=> 	__('Text Shadow', 'cc2'),
					'section'  		=> 	'cc_slider',
					'type'     		=> 	'checkbox',
					'priority'		=> 	385,
					'value' 		=> 1,
				) );

				// Caption Text Opacity
				$wp_customize->add_setting( 'caption_text_opacity', array(
					'default'        => '0.8',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );

				$wp_customize->add_control( 'caption_text_opacity', array(
					'label'   		=> __( 'Caption Text Opacity', 'cc2' ),
					'section' 		=> 'cc_slider',
					'priority'		=> 	390,
					'type'   		 => 'text',
				) );




				// Prev/Next color

				/*
				$wp_customize->add_setting('slider_controls_color', array(
					'default'           	=> '#fff',
					'capability'        	=> 'edit_theme_options',
					'transport'   			=> 'refresh',
					'sanitize_callback' 	=> 'sanitize_hex_color_no_hash',
					'sanitize_js_callback' 	=> 'maybe_hash_hex_color',
				) );
				$wp_customize->add_control( new WP_Customize_Color_Control($wp_customize, 'slider_controls_color', array(
					'label'    				=> __('Prev/Next Controls Color', 'cc2'),
					'section'  				=> 'cc_slider',
					'priority'				=> 391,
				) ) );*/




				/*
				 * 
				 * 	// Caption Title Font Style
				$wp_customize->add_setting( 'caption_title_font_style', array(
					'default'		=>	true,
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					* 'sanitize_callback' => 'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('caption_title_font_style', array(
					'label'    		=> 	__('Italic'),
					'section'  		=> 	'cc_slider',
					'type'     		=> 	'checkbox',
					'priority'		=> 	240,
				) );
				// Prev/Next disable
				$wp_customize->add_setting( 'slider_controls_show', array(
					'default'		=>	false,
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_bool',
				) );
				$wp_customize->add_control('slider_controls_show', array(
					'label'    		=> 	__('Enable Prev/Next Controls', 'cc2'),
					'section'  		=> 	'cc_slider',
					'type'     		=> 	'checkbox',
					'priority'		=> 	392,
				) );
				*/
				

				// Sliding Time
				$wp_customize->add_setting( 'cc_sliding_time', array(
					'default'        => '5000',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );

				$wp_customize->add_control( 'cc_sliding_time', array(
					'label'   		=> __( 'Sliding Time in ms', 'cc2' ),
					'section' 		=> 'cc_slider',
					'priority'		=> 	420,
					'type'   		 => 'text',
				) );

				// Sub Heading for Slider Dimensions
				$wp_customize->add_setting( 'slider_dimensions_heading', array(
					'capability'    => 	'edit_theme_options',
					'sanitize_callback' => 'cc2_Pasteur_none'
				) );
				$wp_customize->add_control( new Label( $wp_customize, 'slider_dimensions_heading', array(
					'label' 		=> 	__('Slideshow Dimensions', 'cc2'),
					'type' 			=> 	'label',
					'section' 		=> 	'cc_slider',
					'priority'		=> 	470,
				) ) );

				// Note for Slider Height and Width
				$wp_customize->add_setting( 'slider_dimensions_note', array(
					'capability'    => 	'edit_theme_options',
					'sanitize_callback' => 'cc2_Pasteur_none'
				) );
				$wp_customize->add_control( new Description( $wp_customize, 'slider_dimensions_note', array(
					'label' 		=> 	__('You don\'t need to set the width and height of the slider: just make all your images the size you want to have as your slideshow size. You can still define a width and max height here, but we recommend to leave it automatic. <a href="https://themekraft.zendesk.com/hc/en-us/articles/200270762" target="_blank">Read more.</a>', 'cc2'),
					'type' 			=> 	'description',
					'section' 		=> 	'cc_slider',
					'priority'		=> 	475,
				) ) );

				// Slider Width
				$wp_customize->add_setting( 'cc_slider_width', array(
					'default'        => 'auto',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );

				$wp_customize->add_control( 'cc_slider_width', array(
					'label'   		=> __( 'Slider width', 'cc2' ),
					'section' 		=> 'cc_slider',
					'priority'		=> 	480,
					'type'   		 => 'text',
				) );

				// Slider Height
				$wp_customize->add_setting( 'cc_slider_height', array(
					'default'        => 'none',
					'capability'    => 	'edit_theme_options',
					'transport'   	=> 	'refresh',
					'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
				) );

				$wp_customize->add_control( 'cc_slider_height', array(
					'label'   		=> __( 'Slider max height', 'cc2' ),
					'section' 		=> 'cc_slider',
					'priority'		=> 	490,
					'type'   		 => 'text',
				) );			
		}
		
				
		/**
		 * Advanced bootstrap settings:
		 * - container sizes (small, medium, large)
		 * - sidebar / content col grid customization
		 */
		
		function section_customize_bootstrap( $wp_customize ) {
			extract( $this->prepare_variables() );

		// Slider Section
			$wp_customize->add_section( 'cc2_customize_bootstrap', array(
				'title'         => 	'Advanced Bootstrap Settings',
				'priority'      => 	500,
			) ); 
			
			$customize_bootstrap_priority = 510;

			// heading
			// Sub Heading for Container Width
			$wp_customize->add_setting( 'heading_bootstrap_container_width', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Label( $wp_customize, 'heading_bootstrap_container_width', array(
				'label' 		=> 	__('Bootstrap Container Width', 'cc2'),
				'type' 			=> 	'label',
				'section' 		=> 	'cc2_customize_bootstrap',
				'priority'		=> 	501,
			) ) );

			// Note for Container Width
			$wp_customize->add_setting( 'note_bootstrap_container_width', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Description( $wp_customize, 'note_bootstrap_container_width', array(
				'label' 		=> 	sprintf( __('Customize the width values of the .container class, for each different screen size. Leave the field empty for default width.<br /><a href="%s">Screen size info</a>', 'cc2'), 'http://getbootstrap.com/css/#grid-options' ),
				'type' 			=> 	'description',
				'section' 		=> 	'cc2_customize_bootstrap',
				'priority'		=> 	502,
			) ) );

		

			// Container Width (small screen) => default: 750px
			$wp_customize->add_setting('bootstrap_container_width[small]', array(
				'default' 		=> '750px',
				'capability'    => 'edit_theme_options',
				'transport'   	=> 'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('bootstrap_container_width[small]', array(
				'label'      	=> __('Container Width (Small Screen)', 'cc2'),
				'section'    	=> 'cc2_customize_bootstrap',
				'priority'   	=> $customize_bootstrap_priority,
			) );
			$customize_bootstrap_priority++;
	 
			
			// Container Width (medium screen) => default: 970px
			$wp_customize->add_setting('bootstrap_container_width[medium]', array(
				'default' 		=> '970px',
				'capability'    => 'edit_theme_options',
				'transport'   	=> 'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('bootstrap_container_width[medium]', array(
				'label'      	=> __('Container Width (Medium Screen)', 'cc2'),
				'section'    	=> 'cc2_customize_bootstrap',
				'priority'   	=> $customize_bootstrap_priority,
			) );
			$customize_bootstrap_priority++;
			
			// Container Width (large screen) => default: 1170px
			$wp_customize->add_setting('bootstrap_container_width[large]', array(
				'default' 		=> '1170px',
				'capability'    => 'edit_theme_options',
				'transport'   	=> 'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('bootstrap_container_width[large]', array(
				'label'      	=> __('Container Width (large Screen)', 'cc2'),
				'section'    	=> 'cc2_customize_bootstrap',
				'priority'   	=> $customize_bootstrap_priority,
			) );
			$customize_bootstrap_priority++;


			
			$customize_bootstrap_priority = 520;
		
			// Sub Heading for Custom Sidebar Columns (ie. 1 - 12)
			$wp_customize->add_setting( 'heading_bootstrap_custom_sidebar_cols', array(
			'capability'    => 	'edit_theme_options',
			'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Label( $wp_customize, 'heading_bootstrap_custom_sidebar_cols', array(
			'label' 		=> 	__('Custom Sidebar Size', 'cc2'),
			'type' 			=> 	'label',
			'section' 		=> 	'cc2_customize_bootstrap',
			'priority'		=> 	$customize_bootstrap_priority,
			) ) );
			
			$customize_bootstrap_priority+=5;

			// Note for Container Width
			$wp_customize->add_setting( 'note_bootstrap_custom_sidebar_cols', array(
			'capability'    => 	'edit_theme_options',
			'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Description( $wp_customize, 'note_bootstrap_custom_sidebar_cols', array(
			'label' 		=> 	__('Adjust the <strong>column numbers</strong>, which are used for setting the size of the sidebars. <a href="http://getbootstrap.com/css/#grid">Read about the Bootstrap Grid system</a>', 'cc2'),
			'type' 			=> 	'description',
			'section' 		=> 	'cc2_customize_bootstrap',
			'priority'		=> 	$customize_bootstrap_priority,
			) ) );

			$customize_bootstrap_priority+=5;
			
			// Left Sidebar: Custom Columns
			$default_sidebar_cols = $bootstrap_cols; 
			$default_sidebar_cols[4] = __('4 (default)', 'cc2');
			
			$wp_customize->add_setting('bootstrap_custom_sidebar_cols[left]', array(
				'default' 		=> '4',
				'capability'    => 'edit_theme_options',
				'transport'   	=> 'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('bootstrap_custom_sidebar_cols[left]', array(
				'label'      	=> __('Left sidebar', 'cc2'),
				'section'    	=> 'cc2_customize_bootstrap',
				'type'			=> 'select',
				'choices'		=> $default_sidebar_cols,
				'priority'   	=> $customize_bootstrap_priority,
			) );
			$customize_bootstrap_priority++;
			
			// Right Sidebar: Custom Columns
			$wp_customize->add_setting('bootstrap_custom_sidebar_cols[right]', array(
				'default' 		=> '4',
				'capability'    => 'edit_theme_options',
				'transport'   	=> 'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('bootstrap_custom_sidebar_cols[right]', array(
				'label'      	=> __('Right sidebar', 'cc2'),
				'section'    	=> 'cc2_customize_bootstrap',
				'type'			=> 'select',
				'choices'		=> $default_sidebar_cols,
				'priority'   	=> $customize_bootstrap_priority,
			) );
			$customize_bootstrap_priority++;
			
			$customize_bootstrap_priority+=5;

			$wp_customize->add_setting('cc2_comment_form_orientation', array(
				'default' 		=> 'vertical',
				'capability'    => 'edit_theme_options',
				'transport'   	=> 'refresh',
				'sanitize_callback' => 'cc2_Pasteur_sanitize_text',
			) );
			$wp_customize->add_control('cc2_comment_form_orientation', array(
				'label'      	=> __('Comment form orientation', 'cc2'),
				'section'    	=> 'cc2_customize_bootstrap',
				
				'type'			=> 'select',
				'choices'		=> array('vertical' => __('Vertical (default)', 'cc2'), 'horizontal' => __('Horizontal', 'cc2') ),
				'priority'   	=> $customize_bootstrap_priority,
			) );
			$customize_bootstrap_priority++;
			
		}
		
		
		function section_advanced_settings( $wp_customize, $arrParams = false ) {
			extract( $this->prepare_variables() );
			
			$strSectionName = 'advanced_settings';
			$priority = 180;
			$strPanelID = '';
		
			
			
			
			if( !empty( $this->arrSectionPriorities['section_advanced_settings'] ) ) {
				$priority = $this->arrSectionPriorities['section_advanced_settings'];
			}
			
			
			$wp_customize->add_section( $strSectionName, array(
				'title'         => 	__('Advanced Settings', 'cc2' ),
				'priority' 		=> $priority,
				'panel' 		=> $strPanelID,
			) );
			
			$_debug = array(
				'section_priorities' => $this->arrSectionPriorities,
				'priority' => $priority,
				/*'wp_customize->get_panel(widgets)' => $wp_customize->get_panel('widgets'),
				'wp_customize->get_panel(widgets)' => $wp_customize->get_panel('widgets')->priority,*/
			);
		
			// debug label
			/*$wp_customize->add_setting( 'debug_customizer', array(
				'capability'    => 	'edit_theme_options',
				'sanitize_callback' => 'cc2_Pasteur_none'
			) );
			$wp_customize->add_control( new Label( $wp_customize, 'debug_customizer', array(
				'label' 		=> 	__('Debug:', 'cc2') . ' ' . print_r( $_debug , true ),
				'type' 			=> 	'label',
				'section' 		=> 	$strSectionName,
				'priority'		=> 	1,
			) ) );*/
			
			// mirrors $cc2_advanced_settings['custom_css']
			/*
			$wp_customize->add_setting( 'advanced_settings[custom_css]', array(
				'default' => $advanced_settings['custom_css'],
				'capability'    => 'edit_theme_options',
				'transport'   	=> 'refresh',
				'sanitize_callback' => array( 'cc2_Pasteur', 'sanitize_css' ),
			) );
			
			$wp_customize->add_control( 'advanced_settings[custom_css]', array(
				'label'      	=> __('Custom CSS', 'cc2'),
				'section'    	=> $strSectionName,
				'description'	=> sprintf( __('Mirroring the contents of the &quot;Custom CSS&quot; field under <a href="%s">CC Settings &raquo; Advanced Settings</a>', 'cc2' ), admin_url('admin.php?page=cc2-settings&tab=advanced-settings') ),
				'priority'		=> $priority,
				'type'			=> 'textarea',
				'value'			=> $advanced_settings['custom_css'],
				
			) );
			*/
			
		}
	
	} // end of class
	
	
	// Original WP customizer init!
	//add_action( 'plugins_loaded', '_wp_customize_include' );
	// reuse and init AFTER it = default priority = 10, thus we go for 11!
	//add_action( 'plugins_loaded', array( 'cc2_CustomizerTheme', 'init' ), 11 );
	
	
	if( !isset( $GLOBALS['cc2_customizer'] ) ) {
		$GLOBALS['cc2_customizer'] = new cc2_CustomizerTheme(); // intentionally NOT using the Simpleton pattern .. ;)
	}
	
}

/**
 * A try to improve the font loading situation
 */

if( !function_exists('cc2_customizer_load_fonts') ) :
	function cc2_customizer_load_fonts( $fonts = array() ) {
		global $cc2_font_family;
		
		$cc2_font_family = array(
			'inherit' => 'inherit',
			'"Lato", "Droid Sans", "Helvetica Neue", Tahoma, Arial, sans-serif' => 'Lato',
			'"Ubuntu Condensed", "Droid Sans", "Helvetica Neue", Tahoma, Arial, sans-serif' => 'Ubuntu Condensed',
			'"Pacifico", "Helvetica Neue", Arial, sans-serif' => 'Pacifico',
			'"Helvetica Neue", Tahoma, Arial, sans-serif' => 'Helvetica Neue',
			'Garamond, "Times New Roman", Times, serif' => 'Garamond',
			'Georgia, "Times New Roman", Times, serif' => 'Georgia',
			'Impact, Arial, sans-serif' => 'Impact',
			'Arial, sans-serif'	=> 'Arial',
			'Arial Black, Arial, sans-serif' => 'Arial Black',
			'Verdana, Arial, sans-serif' => 'Verdana',
			'Tahoma, Arial, sans-serif' => 'Tahoma',
			'"Century Gothic", "Avant Garde", Arial, sans-serif' => 'Century Gothic',
			'"Times New Roman", Times, serif' => 'Times New Roman',
		);

		// If TK Google Fonts is activated get the loaded Google fonts!
		$tk_google_fonts_options = get_option('tk_google_fonts_options', false);

		// Merge Google fonts with the font family array, if there are any
		if( !empty( $tk_google_fonts_options) && isset($tk_google_fonts_options['selected_fonts']) ) {

			foreach ($tk_google_fonts_options['selected_fonts'] as $key => $selected_font) {
				$selected_font = str_replace('+', ' ', $selected_font);
				$cc2_font_family[$selected_font] = $selected_font;

			}

		}
		
		
		$return = $cc2_font_family;
		
		return $return;
	}

endif;

/**
 * Load custom controls
 */

// loads base controls: Description, Heading and Label
include_once( get_template_directory() . '/includes/admin/customizer/base-controls.php' );

// implements a slightly modified color control WITH transparency option
include_once( get_template_directory() . '/includes/admin/customizer/cc2-color-control.php' );

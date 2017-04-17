<?php

if( !class_exists( 'cc2_SliderAdminAjax' ) ) {
	require_once('slider-ajax.php');
}

/**
 * Adding the Admin Page
 * 
 * @author Sven Lehnert
 * @package cc2
 * @since 2.0
 */ 

//add_action( 'admin_init', 'cc2_slider_register_admin_settings' );


add_action( 'after_setup_theme', array( 'cc2_SliderAdmin', 'get_instance'), 12 );
add_action( 'after_setup_theme', array( 'cc2_SliderAdminAjax', 'get_instance'), 12 );

/**
 * NOTE: Seperate init, because we are also using WP ajax calls
 */

class cc2_SliderAdmin {
	var $classPrefix = 'cc2_slider_',
		$className = 'Slideshow',
		$optionName = 'cc_slider_options';
	
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
		
		// register required settings, sections etc.
		add_action( 'admin_init', array( $this, 'register_admin_settings' ) );
		
		// 
		add_action('admin_enqueue_scripts', array( $this, 'init_admin_js' ) );
		
		// add meta box to the post / page editor sidebar
		add_action( 'add_meta_boxes', array( $this, 'add_custom_box' ) );
	}


	/**
	 * Wrapper to avoid future fuck-ups and repetitive works (aka DRY!)
	 */

	function get_slider_settings( $default = array() ) {
		return get_option( $this->optionName, $default ); 
	}
	
	function update_slider_settings( $settings = array() ) {
		$return = false;
		
		if( !empty( $settings ) ) {
			$return = update_option( $this->optionName, $settings );
		}
			
		return $return;
	}

	/**
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
		
		//new __debug('cc2 slider: register_admin_settings fires');
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
				$this->classPrefix . 'add',
				'<strong>Create A New Slideshow</strong>',
				array( $this, 'admin_setting_add_slideshow' ), /** method callback */
				$strSettingsPage,
				'section_general'
			);
			
			add_settings_field(
				$this->classPrefix . 'edit',
				'<strong>Manage Your Slides</strong>',
				array( $this, 'admin_setting_edit_slideshow' ), /** method callback */
				$strSettingsPage,
				'section_general'
			);

		

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
	
	function admin_setting_add_slideshow() {
		// fetch required data
		//new __debug('add slideshow template fires');
		
	
		// include template
		require_once( get_template_directory() . '/includes/admin/templates/slideshow-add.php' );
	}
	
	
	function admin_setting_edit_slideshow() {
		// fetch required data
		$arrBackwardsCompat = get_option('cc_slider_options', array() );
		
		$cc_slider_options = get_option($this->classPrefix . 'options', $arrBackwardsCompat );
		
		
		// include template
		require_once( get_template_directory() . '/includes/admin/templates/slideshow-edit.php' );
	}
	
	/**
	 * Adds a box to the main column on the Post and Page edit screens.
	 */
	
	function add_custom_box() {

		add_meta_box(
			'cc_slider_sectionid',
			__( 'SlideShow Settings', 'cc2' ),
			array( $this, 'admin_custom_box' ),
			'attachment'
		);
	}

	/**
	 * Prints the box content.
	 * 
	 * @param WP_Post $post The object for the current post/page.
	 */

	function admin_custom_box( $post ) {
	// fetch required data
		$cc_slider_options = get_option( $this->classPrefix . 'options', array() );

		// Add an nonce field so we can check for it later.
		wp_nonce_field( $this->classPrefix . 'custom_box', $this->classPrefix . 'custom_box_nonce' );


		?>
		<h4>Create A New Slideshow</h4>
		
		<?php
		require_once( get_template_directory() . '/includes/admin/templates/slideshow-add.php' );
		?>
	   
		<h4>Edit Existing Slideshow</h4>
		<?php
		require_once( get_template_directory() . '/includes/admin/templates/slideshow-edit.php' );
	
	}
	
	
	
	
	/**
	 * Enqueue the needed JS _for the admin screen_
	 *
	 * FIXME: Needs to be loaded ONLY when showing the admin screen, but NOWHERE ELSE!
	 * TODO: Bundle into a seperate, independent call
	 * 
	 * @package cc2
	 * @since 2.0
	 */

	function init_admin_js($hook_suffix) {
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
			
		// add settings
		/*wp_localize_script('cc-support-helper', 
		wp_enqueue_script('cc-support-helper');*/
		
		/*wp_enqueue_style( 'cc_tk_zendesk_css');
		wp_enqueue_script( 'cc_tk_zendesk_js');
		*/ 
		
	}
}




 
/**
 * Update custom field on save
*/
function cc_slider_update_attachment_meta($attachment){
	global $post;
    $cc_slider_options = get_option('cc_slider_options');
	
	if(!empty($attachment['attachments'][$post->ID]['new_slider'])){
		$selected_slider = $attachment['attachments'][$post->ID]['new_slider'];
	} else {
		$selected_slider = $attachment['attachments'][$post->ID]['select_slider'];
	}
	
	$cc_slider_options[$selected_slider]['slides'][$post->ID] = array(
		'id'			=> $post->ID,
        'url'			=> wp_get_attachment_url( $post->ID )
	);
	
	update_option('cc_slider_options',$cc_slider_options);
   
    update_post_meta($post->ID, 'new_slider', $attachment['attachments'][$post->ID]['new_slider']);
	update_post_meta($post->ID, 'select_slider', $attachment['attachments'][$post->ID]['select_slider']);
	
	
	
    return $attachment;
}
//add_filter( 'attachment_fields_to_save', ' cc_slider_update_attachment_meta', 4);
 

?>

<?php
/**
 * Adapted Color Control
 * Features a "transparent" button
 */

if( class_exists( 'WP_Customize_Control' ) ) :
/**
 * Custom sanitizers
 */
 
class cc2_Sanitizer {
	public static function sanitize_hex_color_no_hash_with_transparency( $content ) {
		$return = $content; 
		
		if( trim( $content ) == 'transparent' ) {
			$return = trim( $content );
		} else {
			$return = sanitize_hex_color_no_hash( $content );
		}
		
		return $return;
	}
}


/**
 * Customize Color Control Class
 *
 * @package WordPress
 * @subpackage Customize
 * @since 3.4.0
 */
class cc2_Customize_Color_Control extends WP_Customize_Control {
	/**
	 * @access public
	 * @var string
	 */
	public $type = 'color';

	/**
	 * @access public
	 * @var array
	 */
	public $statuses;

	/**
	 * Constructor.
	 *
	 * @since 3.4.0
	 * @uses WP_Customize_Control::__construct()
	 *
	 * @param WP_Customize_Manager $manager
	 * @param string $id
	 * @param array $args
	 */
	public function __construct( $manager, $id, $args = array() ) {
		$this->statuses = array( '' => __('Default') );
		parent::__construct( $manager, $id, $args );
	}

	/**
	 * Enqueue scripts/styles for the color picker.
	 *
	 * @since 3.4.0
	 */
	public function enqueue() {
		wp_enqueue_script('cc2-custom-control-helper', get_template_directory_uri() . '/includes/admin/js/custom-control-helper.js', array('jquery', 'wp-color-picker') );
		
		wp_enqueue_script( 'wp-color-picker' );
		wp_enqueue_style( 'wp-color-picker' );
	}

	/**
	 * Refresh the parameters passed to the JavaScript via JSON.
	 *
	 * @since 3.4.0
	 * @uses WP_Customize_Control::to_json()
	 */
	public function to_json() {
		parent::to_json();
		$this->json['statuses'] = $this->statuses;
	}

	/**
	 * Render the control's content.
	 *
	 * @since 3.4.0
	 */
	public function render_content() {
		$this_default = $this->setting->default;
		$default_attr = '';
		if ( $this_default ) {
			if ( false === strpos( $this_default, '#' ) )
				$this_default = '#' . $this_default;
			$default_attr = ' data-default-color="' . esc_attr( $this_default ) . '"';
		}
		// The input's value gets set by JS. Don't fill it.
		?>
		<label>
			<span class="cc2-customize-control-title"><?php echo esc_html( $this->label ); ?></span>
			<div class="cc2-customize-control-content">
				<input class="color-picker-hex" type="text" maxlength="7" placeholder="<?php esc_attr_e( 'Hex Value' ); ?>"<?php echo $default_attr; ?> />
				<button type="button" class="button btn color-picker-set-transparent"><?php _e('Set transparent', 'cc2'); ?></button>
			</div>
		</label>
		<?php
	}
}

endif; // /class_exists

<?php
/**
 * Base custom controls for the Theme Customizer
 */
 

/**
 * Small Heading Extension Class for the WordPress Customizer Preview
 * Sometimes you might need a header.
 * 
 * @author Konrad Sroka 
 * @package cc2
 * @since 2.0
 */

add_action( 'customize_register', 'tk_wp_customizer_heading' );
function tk_wp_customizer_heading( $wp_customize ) { 
    class Heading extends WP_Customize_Control {
	    public $type = 'heading';
	
	    public function render_content() {
	        ?>
	        <label>
	        	<span class="customize-control-title" style="font-size: 1.2em; padding-bottom: 5px; margin: 20px 0 10px; border-bottom: 1px solid #ddd;"><strong><?php echo esc_html( $this->label ); ?></strong></span>            
	        </label>
	        <?php
	    }
	}
}

/**
 * Small Label Extension Class for the WordPress Customizer Preview
 * When you want to display just a label as small heading
 * 
 * @author Konrad Sroka 
 * @package cc2
 * @since 2.0
 */

add_action( 'customize_register', 'tk_wp_customizer_label' );
function tk_wp_customizer_label( $wp_customize ) { 
    class Label extends WP_Customize_Control {
	    public $type = 'label';
	
	    public function render_content() {
	        ?>
	        <label>
	        	<span class="customize-control-title"><strong><?php echo esc_html( $this->label ); ?></strong></span>            
	        </label>
	        <?php
	    }
	}
}

/**
 * Small Description Extension Class for the WordPress Customizer Preview
 * When you want to display just a quick description. Keep it short and sweet though! ;-)
 * 
 * @author Konrad Sroka 
 * @package cc2
 * @since 2.0
 */

add_action( 'customize_register', 'tk_wp_customizer_desc' );
function tk_wp_customizer_desc( $wp_customize ) { 
    class Description extends WP_Customize_Control {
	    public $type = 'description';
	
	    public function render_content() {
	        ?>
	        <label>
	        	<span class="description customize-control-title"><em style="font-weight: normal; font-size: 12px;"><?php echo $this->label; ?></em></span>
	        </label>
	        <?php
	    }
	}
}


add_action( 'customize_register', 'cc2_wp_customizer_custom_classes');

function cc2_wp_customizer_custom_classes( $wp_customize ) {

	/**
	 * Label with description
	 * 
	 * @author Fabian Wolf
	 * @package cc2
	 * @since 2.0
	 * 
	 * For more info on how to build your own customizer control, take a look at the famous post(s) by otto: @link http://ottopress.com/2012/making-a-custom-control-for-the-theme-customizer/
	 */
	class Labeled_Description extends WP_Customize_Control {
		public $type = 'description';
		
		public function render_content() { 
			if( is_array( $this->label ) ) {
				extract( $this->label );
			}
			
			?>
		<label>
			<?php if( isset( $title ) ) { ?>
			<span class="customize-control-title"><strong><?php echo esc_html( $title ); ?></strong></span>
			<?php } ?>
			
			<?php if( isset( $description ) ) { ?>
			<span class="description customize-control-title"><em style="font-weight: normal; font-size: 12px;"><?php echo $description; ?></em></span>
			<?php } ?>
		</label>
		
<?php	}
		
	}
	
}



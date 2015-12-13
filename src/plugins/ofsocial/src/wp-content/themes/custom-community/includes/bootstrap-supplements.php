<?php
/**
 * Adding support for Bootstrap CS inside PHP, to lessen possible JS hickups
 * Mostly tries to use filter hooks plus the simple_html_dom class (@see 
 *
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 */

/**
 * @wp-hook after_setup_theme (@see http://codex.wordpress.org/Plugin_API/Action_Reference/after_setup_theme)
 * 
 */

add_action( 'after_setup_theme', array( '_cc2_BootStrapSupplements', 'get_instance' ) );
  
// bail out early, if class already exists = Child Theme support
if( class_exists( '_cc2_BootStrapSupplements') ) {
	return;
}

 
 

class _cc2_BootStrapSupplements {
	/**
	 * Plugin instance.
	 *
	 * @see get_instance()
	 * @type object
	 */
	protected static $instance = NULL;

	// does the main job
	function __construct() {
		/**
		 * load simple_html_dom
		 * NOTE: Intionally uses get_template_directory() (get_stylesheet_directory() would err out when being used in a child theme)
		 */
		 
		if( !class_exists( 'simple_html_dom' ) ) {
			require_once( get_template_directory() . '/includes/resources/simple_html_dom/simple_html_dom.wp.php' );
		}
		
		// lets get some work done
	
		/**
		 * NOTE: the sidebars are only available at the time wp_head fires. Before that, the $wp_registered_sidebars variable is empty, and thus we won't know about any sidebars in use!
		 */
		/*if( $this->have_widgets() ) {
			
			
			// use a body class to contact scripts
			add_filter('body_class', array( $this, 'filter_body_class' ) );
		}*/
		 
		/**
		 * All that is not covered yet: Use the nasty apply_filters('the_content') ^^
		 */
		 
		add_filter( 'the_content', array( $this, 'filter_the_content' ) );
		
		if( is_single() && !class_exists( 'WooCommerce' ) ) {
			add_action('comment_form_after_fields', array( $this, 'comment_buttons_try') );
			add_action('comment_form', array( $this, 'comment_buttons_end') );
		}
		//add_filter( 'wp_head', array( $this, 'fire_from_wp_head' ), 10, 3 );
	}
	
	
	public function comment_buttons_try() {
		ob_start();
	}
	
	public function comment_buttons_end() {
		$buttons = ob_get_contents();
		ob_end_clean();
		
		$return = $buttons;
		
		if( !empty( $buttons ) && strip_tags( $buttons ) !== $buttons ) {
		
			$button_dom = new simple_html_dom();
			$button_dom->load ( $buttons );
			$button_dom->find( '#commentsubmit',0 )->class = 'btn btn-primary';
			
			//new __debug( $button_dom->save() );
			$return = $button_dom->save();
		}
		
		echo $return;
	}
	
	/**
	 * Implements Factory pattern
	 * Strongly inspired by WP Maintenance Mode of Frank Bueltge ^_^ (@link https://github.com/bueltge/WP-Maintenance-Mode)
	 * 
	 * Access this plugin’s working instance
	 *
	 * @wp-hook after_setup_theme
	 * @return object of this class
	 */
	public static function get_instance() {

		NULL === self::$instance and self::$instance = new self;

		return self::$instance;
	}
	/*
	public function fire_from_wp_head( $some = null, $params = false ) {
		global $body_class, $wpdb, $wp_query;
		
		$this->have_widgets();
		
		new __debug( $wp_query, 'current query' );
		
	}
	
	public function have_widgets() {
		global $post, $query, $_wp_sidebars_widgets, $wp_registered_sidebars;
		

		
		if( !is_admin() ) {
			$sidebars_widgets = $_wp_sidebars_widgets;
			if ( empty($_wp_sidebars_widgets) ) {
				$sidebars_widgets = get_option('sidebars_widgets', array());
			}
			
			new __debug( array( '_wp_sidebars_widgets' => $sidebars_widgets, 'wp_registered_sidebars' => $wp_registered_sidebars  ) );
		}
		
	}*/
	
	/**
	 * Add on-demand loading information for bootstrap-wp.js and the style.css
	 */
	public function filter_body_class( $classes = array() ) {
		$return = $classes;
		
		if( !in_array( 'cc2-bootstrap-support', $return ) ) {
			$return[] = 'cc2-bootstrap-support';
		}
		
		return $return;
	}

	
	public function filter_the_content( $content = null ) {
		$return = $content;
		
		// sanity check: do not parse content if it doesnt contain html tags, or class-attributes (maybe .. )
		if( !empty( $return ) && strpos( $return, '<' ) !== false && strpos( $return, '>' ) !== false ) {
			
			
			$dom = new simple_html_dom();
			$dom->load( $return );
			
			/**
			 * Comment buttons
			 * @see http://codex.wordpress.org/Function_Reference/comment_form#Default_.24args_array
			 * 
			 * Original code: 
			 // here for each comment reply link of wordpress
			jQuery( '.comment-reply-link' ).addClass( 'btn btn-primary' );

			// here for the submit button of the comment reply form
			jQuery( '#commentsubmit' ).addClass( 'btn btn-primary' );	
			 */
			 
			foreach( $dom->find('.comment-reply-link, #commentsubmit') as $elem ) {
				if( !isset( $elem->class ) ) {
					$elem->class = 'btn btn-primary';
				} elseif( !empty( $elem->class ) != false && stripos( $elem->class, 'btn btn-primary' ) === false )  {
					$elem->class = trim($elem->class . ' btn btn-primary'); // better than .=, cause you never know ... might be an empty class="" construct ^_^
				}
			}
			/**
			 * there can only be ONE id .. ^_^(ID = _unique_ IDentifier)
			 * NOTE: Redundant / obsolete.
			 */
			/*
			if( stripos($dom->find('#commentsubmit',0)->class, 'btn btn-primary' ) === false ) {
				$dom->find('#commentsubmit',0)->class = trim( $dom->find('#commentsubmit', 0)->class . ' btn btn-primary');
			}
			*/

			
			 
			$return = $dom->save();		
		}
			
		return $return;
	}
	
}
 

<?php
/**
 * Ajax-handler for the cc2 slideshow admin
 */


class cc2_SliderAdminAjax {
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


	function __construct( $arrParams = array() ) {
		
		/**
		 * NOTE: Internal XSS protection (else EVERY registered user will be enabled to inject data into the slideshow AJAX request)
		 */
		
		if( current_user_can('manage_options') ) {
		
			$this->add_ajax_calls();
		}
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
	 * Add all required ajax calls and processing stuff
	 *  
	 * @author Fabian Wolf
	 * @package cc2
	 * @since 2.0
	 */
	
	function add_ajax_calls() {
		/**
		 * TODO: We might want to add a user_can() check in here, to avoid possible security holes
		 * @see http://codex.wordpress.org/AJAX_in_Plugins#Ajax_on_the_Viewer-Facing_Side
		 */
		
		// save settings
		add_action( 'wp_ajax_' . $this->classPrefix . 'query', array( $this, 'update_settings') );
		//add_action( 'wp_ajax_nopriv_' . $this->classPrefix . 'query', array( $this, 'update_settings') );
		
		
		// show list of slides
		add_action( 'wp_ajax_' . $this->classPrefix . 'display_slides_list', array( $this, 'display_slides_list') );
		//add_action( 'wp_ajax_nopriv_' . $this->classPrefix . 'display_slides_list', array( $this, 'display_slides_list') );
		
		
		// add new slideshow
		add_action( 'wp_ajax_' . $this->classPrefix . 'add_slideshow', array( $this, 'add_slideshow') );
		//add_action( 'wp_ajax_nopriv_' . $this->classPrefix . 'add_slideshow', array( $this, 'add_slideshow') );

		// add single slide
		add_action( 'wp_ajax_' . $this->classPrefix . 'add_slide', array( $this, 'add_slide' ) );
		//add_action( 'wp_ajax_nopriv_' . $this->classPrefix . 'add_slide', array( $this, 'add_slide' ) );


		// delete whole slideshow
		add_action( 'wp_ajax_' . $this->classPrefix . 'delete_slideshow', array( $this, 'delete_slideshow') );
		//add_action( 'wp_ajax_nopriv_' . $this->classPrefix . 'delete_slideshow', array( $this,'delete_slideshow') );
		
		// delete single slide
		add_action( 'wp_ajax_' . $this->classPrefix . 'delete_slide', array( $this, 'delete_slide' ) );
		//add_action( 'wp_ajax_nopriv_' . $this->classPrefix . 'delete_slide', array( $this, 'delete_slide') );
		
		
		// change order of slides
		add_action( 'wp_ajax_' . $this->classPrefix . 'slideshow_neworder', array( $this, 'slideshow_neworder' ) );
		//add_action( 'wp_ajax_nopriv_' . $this->classPrefix . 'slideshow_neworder', array( $this, 'slideshow_neworder' ) );
		
	
		// safety switch: reset all slideshows
		add_action( 'wp_ajax_' . $this->classPrefix . 'reset_slideshows', array( $this, 'reset_slideshows' ) );
		//add_action( 'wp_ajax_nopriv_' . $this->classPrefix . 'reset_slideshows', array( $this, 'reset_slideshows' ) );
	
		// update post meta fields (aka custom fields)
		add_action('wp_ajax_save-attachment-compat', array( $this, 'update_media_xtra_fields'), 0, 1);
		
	}
	
	/**
	 * Processing methods for handling the ajax calls on the server side are all up next
	 * 
	 * @author Fabian Wolf
	 * @package cc2
	 * @since 2.0
	 */
	 
	/**
	 * Update settings
	 */
	 
	function update_settings() {
		// validate data
		
		/**
		 * TODO: Validation and sanitization is missing
		 */
		
	    if(isset($_POST['select_slides_list'])) {
			$select_slides_list = $_POST['select_slides_list'];
		}

		if(isset($_POST['slideshow_post_type'])) {
			$slideshow_post_type = $_POST['slideshow_post_type'];
		}
		
		if(isset($_POST['slideshow_taxonomy'])) {
			$slideshow_taxonomy = $_POST['slideshow_taxonomy'];
		}

		if(isset($_POST['slideshow_cat'])) {
			$slideshow_cat = $_POST['slideshow_cat'];
		}
		

		// update settings

		//$cc_slider_options = get_option('cc_slider_options', array() );
		$cc_slider_options = $this->get_slider_settings();


		if(isset($slideshow_post_type)) {
			$cc_slider_options[$select_slides_list]['query']['post_type'] = $slideshow_post_type;
		}

		if(isset($slideshow_taxonomy)) {
			$cc_slider_options[$select_slides_list]['query']['taxonomy'] = $slideshow_taxonomy;
		}

		if(isset($slideshow_cat)) {
			$cc_slider_options[$select_slides_list]['query']['cat'] = $slideshow_cat;
		}
		
		
		$this->update_slider_settings( $cc_slider_options );

		//$this->update_slider_settings( $cc_slider_options );
		die();
	}
	
	/**
	 * Safety switch, eg. if some of the slideshows got fucked up during tests etc.
	 */
	
	function reset_slideshows() {
		update_option( $this->optionName, array() );
	}
	
	/**
	 * Returns the slide list
	 */
	 
	function display_slides_list() {
		$cc_slider_options = $this->get_slider_settings();

		if(isset($_POST['slide_list'])) {
			$slide_list = $_POST['slide_list'];
		}
		
		
		if(!isset($slide_list)) {
			return;
		}

		
		if(!isset($cc_slider_options[$slide_list])) {
			die();
		}

		if( isset($slide_list) && isset($cc_slider_options[$slide_list]) ) {

		?>

		<div id="cc-slider-editor-buttons" class="cc-normal">
		<?php
			if($cc_slider_options[$slide_list]['meta-data']['slideshow_type'] == 'image-slider') { 
				/**
				 * FIXME: Actual buttons don't work - appparently WP is commiting some nasty crime itself with them!
				 */
				
				?>
		
			<a href="#cc-slider-editor-media-upload" id="cc-slider-editor-media-upload" class="custom-media-upload button"><?php _e('Add New Slide', 'cc2'); ?></a>
			<?php
			}
		?>

			<span id="loading-animation-reorder" class="updated below-h2">All Saved!</span>

			<a href="#cc-slider-editor-delete-slideshow" id="cc-slider-editor-delete-slideshow" class="delete-slideshow button"><span class="cc-slider-delete-sign">X</span> Delete This Slideshow</a>
		</div>

		<?php


			if($cc_slider_options[$slide_list]['meta-data']['slideshow_type'] == 'post-slider') {

				$args = array(
					'public'   => true,
					'_builtin' => true

				);
				$output = 'names'; // or objects
				$operator = 'and'; // 'and' or 'or'

				?>

				<select id="slideshow_post_type">

					<option value="none">Select the Post Type</option>
					<?php
					$post_types = get_post_types( $args, $output, $operator );

					foreach ( $post_types as $post_type ) {

						echo '<option value="'.$post_type.'" '. selected($post_type, $cc_slider_options[$slide_list]['query']['post_type'] ).'>'.$post_type.'</option>';
					}
					?>
				</select>
		
				<?php
				if( isset($cc_slider_options[$slide_list]['query']['post_type']) 
					&& $cc_slider_options[$slide_list]['query']['post_type'] == 'post'
				) {

					$cat_args = array();
					$cat_args['selected'] = '-1';

					if(isset($cc_slider_options[$slide_list]['query']['cat']))
						$cat_args['selected'] = $cc_slider_options[$slide_list]['query']['cat'];

					wp_dropdown_categories($cat_args);
				}

			}
		?>

		<ul id="sortable"> <?php
			if($cc_slider_options[$slide_list]['meta-data']['slideshow_type'] == 'post-slider'){

				$args = array (
					'posts_per_page' => -1
				);

				if(isset($cc_slider_options[$slide_list]['query']['post_type']))
					$args['post_type'] = $cc_slider_options[$slide_list]['query']['post_type'];

				if(isset($cc_slider_options[$slide_list]['query']['post_type']) && $cc_slider_options[$slide_list]['query']['post_type'] == 'post' && isset($cc_slider_options[$slide_list]['query']['cat']))
					$args['cat'] = $cc_slider_options[$slide_list]['query']['cat'];


				// The Query
				query_posts($args);

				// The Loop
				while ( have_posts() ) : the_post();
			
			?>
			<li id="<?php the_ID(); ?>" class="ui-state-default" style="clear: both; overflow: auto; padding: 10px; background: #fafafa; border: 1px solid #ddd; cursor: move;"><span class="ui-icon ui-icon-arrowthick-2-n-s"></span>

				<div class="cc-slider-editor-img" style="overflow: auto; float: left; margin: 0 10px 0 0;">
					<?php echo get_the_post_thumbnail(get_the_ID(), array(80,80)); ?>
				</div>
				<div class="cc-slider-editor-info" style="float:left;">
					<span class="cc-slider-editor-title" style="margin-bottom: 15px; font-weight: bold;"><?php the_title(); ?></span>
					<br />
					<span class="cc-slider-editor-exerpt" style="margin-bottom: 15px; font-weight: bold;"><?php echo get_the_excerpt(); ?></span>
					
					<?php echo edit_post_link( 'Edit this Post', '<p>', '</p>' ); ?>
				</div>

			</li>
			<?php

				endwhile;

				// Reset Query
				wp_reset_query();

			} elseif( isset($cc_slider_options[$slide_list]['slides']) ) {

				foreach ($cc_slider_options[$slide_list]['slides'] as $key => $slide) {

					?>
					<li id="<?php echo $key; ?>" class="ui-state-default" style="clear: both; overflow: auto; padding: 10px; background: #fafafa; border: 1px solid #ddd; cursor: move;"><span class="ui-icon ui-icon-arrowthick-2-n-s"></span>

					<?php 
					$attachment_id = $key; // attachment ID

					$image_attributes = wp_get_attachment_image_src( $attachment_id ); // returns an array
					$post = get_post($attachment_id);
					?>

						<div class="cc-slider-editor-img" style="overflow: auto; float: left; margin: 0 10px 0 0;">
							<img src="<?php echo $image_attributes[0]; ?>" width="80<?php //echo $image_attributes[1]; ?>" height="80<?php //echo $image_attributes[2]; ?>">
						</div>
						<div class="cc-slider-editor-info" style="float:left;">
							<span class="cc-slider-editor-title"style="margin-bottom: 15px; font-weight: bold;"><?php echo get_the_title($attachment_id); ?></span>
							<br />
							<span class="cc-slider-editor-exerpt"style="margin-bottom: 15px; font-weight: bold;"><?php echo $post->post_excerpt; ?></span>
							
							<p>
							<?php
								echo edit_post_link( 'Edit this slide', '', ' <span class="delim">|</span> ', $attachment_id );
								//echo ' | ';
								echo '<a class="delete-slide" href="' . $slide_list .'/'. $attachment_id . '">Delete this slide</a>';
							?>
							</p>
						</div>

					</li>
					<?php
				}
			}
			?>
		</ul>
		<?php
			die();
		}
		
	}
	
	
	
	function add_slideshow( $arrParams = array() ) {
		$cc_slider_options = $this->get_slider_settings();
		$slideshow_name = null;
		$arrDefaultParams = array(
			'slideshow_name' => $slideshow_name, 
			'slideshow_type' => 'image',
		);
		
		if(isset($_POST['new_slideshow_name'])) {
			$slideshow_name = $_POST['new_slideshow_name'];
		}

		if(isset($_POST['new_slideshow_type'])) {
			$slideshow_type = $_POST['new_slideshow_type'];
		}
			
		if( !empty( $slideshow_name ) ) {
			// old unsafe style
			/*$cc_slider_options[ sanitize_title($slideshow_name) ]['meta-data'] = array(
				'slideshow_type' => $slideshow_type,
			);*/
			
			// avoid overwriting existing slideshows
			$new_slug = self::sanitize_array_key( $slideshow_name );
			
			if( !isset( $cc_slider_options[ $new_slug ] ) ) {
			
				// new style			
				$cc_slider_options[ $new_slug ] = array(
					'title' => sanitize_title( $slideshow_name ),
					'meta-data' => array(
						'slideshow_type' => $slideshow_type
					),
				);	

				//$this->update_slider_settings( $cc_slider_options );
				$this->update_slider_settings( $cc_slider_options );
			}
		}
		
		die();
	}
	
	public static function sanitize_array_key( $text = false ) {
		$return = $text;
		
		if( !empty( $return ) ) {
			/**
			 * NOTE: Array key is either index or string. So, anything that goes in a string will work as an array key as well!
			 */
			
			//$return = str_replace( array(' ', '--'), '-', strtolower($return) );
			$return = strip_tags( $text );
			
			//$return = sanitize_key( $return );
		}
		
		return $return;
	}
	
	
	/**
	 * Remove whole slideshow
	 */
	function delete_slideshow() {
		$slideshow = false;
		if(isset($_POST['slideshow'])) {
			$slideshow = $_POST['slideshow'];
		}
		
		
		if( !empty( $slideshow ) ) {
			$cc_slider_options = $this->get_slider_settings();

			unset($cc_slider_options[$slideshow]);

			$this->update_slider_settings( $cc_slider_options );
		}	
		
		die();
	}
	
	/**
	 * Remove just a single slide
	 * TODO: Proper security check / sanitization
	 */
	 
	function delete_slide() {
	
		
		if(isset($_POST['args'])) {
			$args = $_POST['args'];
		}

		if( !empty($args) ) {
			
			$args = explode('/',$args);

			$cc_slider_options = $this->get_slider_settings();

			unset($cc_slider_options[$args[0]]['slides'][$args[1]]);

			$this->update_slider_settings( $cc_slider_options );
			
		}
		die();
	}
	
	/**
	 * Add new slide
	 */
	 
	function add_slide() {
		$id = null;
		$select_slides_list = null;

		if(isset($_POST['id'])) {
			$id = $_POST['id'];
		}
				
		if(isset($_POST['select_slides_list'])) {
			$select_slides_list = $_POST['select_slides_list'];
		}
		

		
		if( !empty( $id ) && !empty( $select_slides_list ) ) {
		
			if(isset($_POST['url'])) {
				$url = $_POST['url'];
			}

			
			$cc_slider_options = $this->get_slider_settings();
			$cc_slider_options[$select_slides_list]['slides'][$id] = array(
				'id' => $id,
				'url' => $url,
			);
				
			$this->update_slider_settings( $cc_slider_options );
			
		}

		die();
	}
	
	/**
	 * Change slide ordering
	 */
	 
	function slideshow_neworder() {
		if( isset($_POST['neworder'])) {
			$neworder = $_POST['neworder'];
		}

		if( isset($_POST['select_slides_list'])) {
			$select_slides_list = $_POST['select_slides_list'];
		}

		if( !empty( $neworder ) ) {

			$cc_slider_options = $this->get_slider_settings();

			$neworder = explode(',',$neworder);
			$slide = array();

			foreach ( $neworder as $key) {
				$slide[$key] = $cc_slider_options[$select_slides_list]['slides'][$key];
			}
			
			unset($cc_slider_options[$select_slides_list]['slides']); // remove old slideshow (order)
			$cc_slider_options[$select_slides_list]['slides'] = $slide; // add new slideshow (order)

			$this->update_slider_settings( $cc_slider_options );
		}

		die();
	}
	
	
	/**
	 * Update custom field via ajax
	 */
	function update_media_xtra_fields() {
		$post_id = $_POST['id'];
		
		update_post_meta($post_id , 'new_slider', $_POST['attachments'][$post_id ]['new_slider']);
		
		clean_post_cache($post_id);
	}

	
}

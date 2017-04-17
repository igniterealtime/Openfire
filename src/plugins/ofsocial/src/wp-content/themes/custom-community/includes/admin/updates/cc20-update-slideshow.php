<?php
/**
 * Import script for importing slideshows from cc1 to cc2 2.0
 */


class cc20_slideshowImport extends updateHelper {
	
	function __construct( $arrParams = array() ) {
		if( !empty( $arrParams ) ) {
			if( isset( $arrParams['option_name' ] ) ) {
				$this->optionName = $arrParams['option_name'];
			}
			
			
			
		}
	}
	

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
	
	function add_slideshow( $arrParams = array() ) {
		$return = false;		
		$cc_slider_options = $this->get_slider_settings();
		$slideshow_name = null;
		
		$arrDefaultParams = array(
			'slideshow_name' => $slideshow_name, 
			'slideshow_type' => 'image',
		);
		
		if( !empty( $arrParams ) ) {
			extract( wp_parse_args( $arrDefaultParams, $arrParams ) );
		}
		
		/*
		if(isset($_POST['new_slideshow_name'])) {
			$slideshow_name = $_POST['new_slideshow_name'];
		}

		if(isset($_POST['new_slideshow_type'])) {
			$slideshow_type = $_POST['new_slideshow_type'];
		}*/
		
			
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
				$result = $this->update_slider_settings( $cc_slider_options );
				
				if( $result != false ) {
					$return = array(
						'slug' => $new_slug,
						'type' => $slideshow_type,
						'name' => $slideshow_name,
					);
				}	
			}
		}
		
		return $return;
	}
	
	public static function sanitize_array_key( $text = false ) {
		$return = $text;
		
		if( !empty( $return ) ) {
			$return = str_replace( array(' ', '--'), '-', strtolower($return) );
			$return = sanitize_key( $return );
		}
		
		return $return;
	}
	
		
	/**
	 * Add new slide
	 */
	 
	function add_slide( $arrParams = array() ) {
		$return = false;
		$id = null;
		$select_slides_list = null;
		
		$arrDefaultParams = array(
			'id' => $id,
			'select_slides_list' => $select_slides_list,
			'url' => '',
		);
		
		if( !empty( $arrParams ) ) {
			extract( wp_parse_args( $arrDefaultParams, $arrParams ) );
		}
		
		/*
		 * from admin.js:
		'url': attachment.url, 
		'id': attachment.id, 
		*/
		

		
		if( !empty( $id ) && !empty( $select_slides_list ) ) {
		
			/*
			if(isset($_POST['url'])) {
				$url = $_POST['url'];
			}*/

			
			$cc_slider_options = $this->get_slider_settings();
			$cc_slider_options[$select_slides_list]['slides'][$id] = array(
				'id' => $id,
				'url' => $url,
			);
				
			$return = $this->update_slider_settings( $cc_slider_options );
			
		}

		return $return;
	}
	
	
}

$slideshowImport = new cc20_slideshowImport( $arrSlideshowImport );

// check if a slideshow exists
$slideshows = $slideshowImport->have_slideshows();

// if so, prepare data
if( !empty( $slideshows ) ) {
	foreach( $slideshows as $iCount => $slideshowData ) {
		$arrPreparedSlideshows[] = $slideshowImport->prepare_slideshow( $slideshowData );
	}
}

// any slideshow still existant?
if( !empty( $arrPreparedSlideshows ) && is_array( $arrPreparedSlideshows) ) {
	
	// cycle through and add them to the current settings using the slider ajax class ;)
	foreach( $arrPreparedSlideshows as $arrSlideshowData ) {
		// generate name if none is set
		
		$slideshowImport->add_slideshow( $name );
		
		if( !empty($arrSlideshowData['images'] ) ) {
			foreach ($arrSlideshowData['images'] as $strSlide) {
				$slideshowImport->add_slide();
			}
		}

	}
}



	// Slideshow
	/* array(__('home','cc2'), __('off','cc2'), __('all','cc2')), =>
	 * 'home' 			=> 'display on home',
'bloghome' 		=> 'display on blog home',
'always'		=> 'display always',
'off'			=> 'turn off'
*/

/**
 * NOTE: Only imports the main slideshow (home)
 */

$arrSlideshowImport = array(
	'enable_slideshow_home',
	'cc_slider_display' => array(
		'value_conversion' => array(
			'home' => 'home',
			'all' => 'always',
			'off' => 'off',
		)
	),
	'slideshow_img',
	'slideshow_small_img',
	'slideshow_cat',
	'slideshow_post_type', // only supported within premium extension
	'slideshow_time',
	'slideshow_orderby',
	'slideshow_style',
	'cc2_slideshow_style' => array(
		'value_conversion' => array(
			'default' => 'bubble-preview',
			'fullwidth' => 'slides-only', // this one's ACTUALLY the regular default, but makes more sense the other way (at least transition-wise)
		)
	),
	'slideshow_caption',
	'slideshow_shadow',
	'slideshow_direct_links'
	
);
	/*
	 *     [cap_enable_slideshow_home] => home
    [cap_slideshow_img] => 
    [cap_slideshow_small_img] => 
    [cap_slideshow_cat] => 
    [cap_slideshow_amount] => 
    [cap_slideshow_post_type] => 
    [cap_slideshow_show_page] => 
    [cap_slideshow_time] => 
    [cap_slideshow_orderby] => 
    [cap_slideshow_style] => default
    [cap_slideshow_caption] => on
    [cap_slideshow_shadow] => sharper shadow
    [cap_slideshow_direct_links] => no
    */


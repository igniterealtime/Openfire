<?php

function bp_mobile_addFooterSwitch( $query ){

	$agent = false;

	$container = $_SERVER['HTTP_USER_AGENT'];
	$useragents = array (
		"iPhone",
		"iPad",
		"iPod",
		"Android",
		"blackberry9500",
		"blackberry9530",
		"blackberry9520",
		"blackberry9550",
		"blackberry9800",
		"webOS"
	);
	false;

	foreach ( $useragents as $useragent ) {

		if (  preg_match("/".$useragent."/i", $container ) && $_COOKIE['bpthemeswitch'] == 'normal' ) {

			$agent = true;

		}
	}


	if ( $agent ) {
				echo '<div id="footer-switch" style="margin:40px 0">
	    	<p style="text-align: center;"><a href="" style="font-size:100%" id="theme-switch-site">'. __( 'view mobile site', 'buddymobile' ) .'</a></p>
		</div><!-- #footer -->';
	}


}
add_action('wp_footer', 'bp_mobile_addFooterSwitch');



function bp_mobile_insert_head() {
?>
 	<script type="text/javascript">
	//<![CDATA[


		jQuery(document).ready(function(){
		
			jQuery('#theme-switch-site').on('click', function(){
					jQuery.cookie( 'bpthemeswitch', 'mobile', {path: '/'} );

			});

			jQuery('#theme-switch').on('click', function(){
					jQuery.cookie( 'bpthemeswitch', 'normal', {path: '/'} );
			});

		});

	//]]>
	</script>

<?php
}
add_action('wp_head', 'bp_mobile_insert_head');

if ( function_exists( 'add_image_size' ) ) {
	add_image_size( 'mobile-thumb', 500, 200, true );
}

function bm_touch_icon() {
	global $buddymobile_options;

	$icon = !empty( $buddymobile_options['touch-icon'] ) ? $buddymobile_options['touch-icon'] : '' ;

	echo $icon;
}

function bm_touch_icon_ipad() {
	global $buddymobile_options;

	$icon = !empty( $buddymobile_options['touch-icon-ipad'] ) ? $buddymobile_options['touch-icon-ipad'] : '' ;

	echo $icon;
}

function bm_touch_icon_retina() {
	global $buddymobile_options;

	$icon = !empty( $buddymobile_options['touch-icon-retina'] ) ? $buddymobile_options['touch-icon-retina'] : '' ;

	echo $icon;
}
<?php
global $buddymobile_options;

$buddymobile_options = get_option('buddymobile_plugin_options');


function buddymobile_register_menus() {
	register_nav_menus (
		array ( 'mobile-menu' => __ ('Mobile Menu') )
	);
}
add_action ( 'init', 'buddymobile_register_menus' );


function bp_mobile_plugin_menu() {
	add_options_page('BuddyMobile', 'BuddyMobile', 'manage_options', __file__, 'buddymobile_plugin_options_page' );

}
add_action('admin_menu', 'bp_mobile_plugin_menu');
add_action('network_admin_menu', 'bp_mobile_plugin_menu');


function buddymobile_plugin_admin_init() {
	register_setting( 'buddymobile_plugin_options', 'buddymobile_plugin_options', 'buddymobile_plugin_options_validate' );
	add_settings_section('general_section', 'General Settings', 'buddymobile_section_general', __FILE__);
	add_settings_section('style_section', 'Style Settings', 'buddymobile_section_style', __FILE__);

	//general options
	add_settings_field('add2homescreen', 'Add to Homescreen', 'buddymobile_setting_add2homescreen', __FILE__, 'general_section');
	add_settings_field('touch-icon', 'Homescreen Icon', 'buddymobile_setting_touch_icon', __FILE__, 'general_section');
	add_settings_field('ipad-theme', 'Mobile iPad Theme', 'buddymobile_setting_ipad_theme', __FILE__, 'general_section');

	//style options
	add_settings_field('theme', 'Mobile Theme', 'buddymobile_setting_theme', __FILE__, 'style_section');
	//add_settings_field('theme-style', 'Theme Style', 'buddymobile_setting_theme_style', __FILE__, 'style_section');
	add_settings_field('toolbar-color', 'Toolbar Color', 'buddymobile_setting_toolbar_color', __FILE__, 'style_section');
	add_settings_field('background-color', 'Background Color', 'buddymobile_setting_background_color', __FILE__, 'style_section');


}
add_action('admin_init', 'buddymobile_plugin_admin_init');


function buddymobile_plugin_options_page() {
?>

	<div class="wrap">
		<div class="icon32" id="icon-options-general"><br></div>
		<h2>BuddyMobile</h2>
		<form action="options.php" method="post">
		<?php settings_fields('buddymobile_plugin_options'); ?>
		<?php do_settings_sections(__FILE__); ?>

		<p class="submit">
			<input name="Submit" type="submit" class="button-primary" value="<?php esc_attr_e('Save Changes'); ?>" />
		</p>
		</form>
		
		<h2>Child Themes</h2>
		<p><?php _e( "If you want to customize BuddyMobile, create a child theme in wp-content/themes and then set the Template: name in the child theme's style.css to iphone. Then copy over any file you want to override from the mobile plugin theme. When a child theme is choosing iphone as the parent it will be displayed in the mobile theme drop down option above.", 'buddymobile' ) ?> </p>
		<p>
		<?php _e( "Here is an example style.css header. notice the Template: iphone. This is telling WordPress to use the iPhone mobile theme as the parent theme.", 'buddymobile' ) ?>
		<pre>
		/*
		Theme Name: custom
		Theme URI: http://buddypress.org
		Description: A Buddypress mobile theme.
		Version: 1.0
		Author: you
		Author URI: http://buddypress.org
		Tags: buddypress, mobile, iphone
		Template: iphone
		*/
		</pre></p>
	</div>

<?php
}

function buddymobile_section_general() {

}

function buddymobile_section_style() {

}


function buddymobile_plugin_options_validate($input) {

	$input['touch-icon'] = sanitize_text_field( $input['touch-icon'] );
	$input['toolbar-color'] = sanitize_text_field( $input['toolbar-color'] );
	$input['background-color'] = sanitize_text_field( $input['background-color'] );
	
	return $input; // return validated input

}


/*** General settings functions ***/
function buddymobile_setting_add2homescreen() {
	global $buddymobile_options;
	$checked = '';

	if( !empty( $buddymobile_options['add2homescreen']) ) { $checked = ' checked="checked" '; }
	echo "<input ".$checked." id='add2homescreen' name='buddymobile_plugin_options[add2homescreen]' type='checkbox' />  ";
	_e('Enable add to homescreen notice on iOS devices.', 'buddymobile');

}

function buddymobile_setting_ipad_theme() {
	global $buddymobile_options;
	$checked = '';

	if( !empty( $buddymobile_options['ipad-theme']) ) { $checked = ' checked="checked" '; }
	echo "<input ".$checked." id='ipad-theme' name='buddymobile_plugin_options[ipad-theme]' type='checkbox' />  ";
	_e('Enable mobile theme on iPad.', 'buddymobile');

}



function buddymobile_setting_theme() {
	global $buddymobile_options;

	$themeop = !empty( $buddymobile_options['theme'] ) ? $buddymobile_options['theme'] : '' ;

	//$themes = array( '' );

	$themes = wp_get_themes();

	$base = array( 'iphone', 'bootpress' );

	foreach ($themes as $index => $data) {
		if ( !in_array( $data['Template'], $base ) ) {
			unset($themes[$index]);
		}
	}

		$data = json_decode( $themeop );
		$themer = !empty( $data->theme ) ? $data->theme : '';

	echo "<select id='theme' name='buddymobile_plugin_options[theme]'>";

	foreach( $themes as $theme => $data  ) {

		$id = $theme;

		$ar = array(
			'theme' => $theme,
			'template' => $data['Template']
		);

		$val = json_encode($ar);

		$selected = ( $themer == $id ) ? 'selected="selected"' : '';

		echo "<option value=$val $selected>$theme</option>" ;
	}
	echo "</select>  ";

	_e('Choose a theme for mobile phones.', 'buddysuite');

}


function buddymobile_setting_theme_style() {
	global $buddymobile_options;
	$checked = '';
	$checked2 = '';

	if( $buddymobile_options['theme-style'] == 'default' ) { $checked = ' checked="checked" '; }
	if( $buddymobile_options['theme-style'] == 'dark' ) { $checked2 = ' checked="checked" '; }

	echo "<input ". $checked  ." type='radio' id='theme-style-default' name='buddymobile_plugin_options[theme-style]' value='default' />   Default      ";
	echo "<input ". $checked2 ." type='radio' id='theme-style-dark' name='buddymobile_plugin_options[theme-style]' value='dark' />   Dark";


}

/*** style settings functions ***/
function buddymobile_setting_toolbar_color() {
	global $buddymobile_options;

	$value = !empty( $buddymobile_options['toolbar-color'] ) ? $buddymobile_options['toolbar-color'] : '' ;

	echo "<input id='toolbar-color' name='buddymobile_plugin_options[toolbar-color]' size='20' type='text' value='$value' />";
}

function buddymobile_setting_background_color() {
	global $buddymobile_options;

	$value = !empty( $buddymobile_options['background-color'] ) ? $buddymobile_options['background-color'] : '' ;

	echo "<input id='background-color' name='buddymobile_plugin_options[background-color]' size='20' type='text' value='$value' />";
}


function buddymobile_setting_touch_icon() {
	global $buddymobile_options;

	wp_enqueue_media();

	$text = !empty( $buddymobile_options['touch-icon'] ) ? $buddymobile_options['touch-icon'] : '' ;

	echo "<input id='touch-icon' name='buddymobile_plugin_options[touch-icon]' size='40' type='text' value='$text' />  ";
	echo "<input type='button' class='button' name='buddymobile-touch-icon' id='buddymobile-touch-icon' value='Upload' />";
	_e('   image size must be 114 x 114 px', 'buddymobile');
}


function buddymobile_admin_enqueue_scripts() {

	wp_enqueue_script( 'wp-color-picker' );
	// load the minified version of custom script
	wp_enqueue_script( 'buddymobile-custom', plugins_url( 'color-pick.js', __FILE__ ), array( 'jquery', 'wp-color-picker' ), '1.1', true );
	wp_enqueue_style( 'wp-color-picker' );
}
if ( isset( $_GET['page'] ) && ( $_GET['page'] == 'buddymobile/includes/bp-mobile-admin.php' ) ) {
	add_action( 'admin_enqueue_scripts', 'buddymobile_admin_enqueue_scripts' );
	add_action( 'media_buttons', 'touch-icon-retina' );
	add_action( 'media_buttons', 'touch-icon-ipad' );
	add_action( 'media_buttons', 'touch-icon' );
}
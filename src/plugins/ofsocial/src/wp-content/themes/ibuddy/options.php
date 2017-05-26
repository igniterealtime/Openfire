<?php
/**
 * A unique identifier is defined to store the options in the database and reference them from the theme.
 * By default it uses the theme name, in lowercase and without spaces, but this can be changed if needed.
 * If the identifier changes, it'll appear as if the options have been reset.
 */

function optionsframework_option_name() {

	// This gets the theme name from the stylesheet
	$themename = get_option( 'stylesheet' );
	$themename = preg_replace("/\W/", "_", strtolower($themename) );

	$optionsframework_settings = get_option( 'optionsframework' );
	$optionsframework_settings['id'] = $themename;
	update_option( 'optionsframework', $optionsframework_settings );
}

/**
 * Defines an array of options that will be used to generate the settings page and be saved in the database.
 * When creating the 'id' fields, make sure to use all lowercase and no spaces.
 *
 * If you are making your theme translatable, you should replace 'ibuddy'
 * with the actual text domain for your theme.  Read more:
 * http://codex.wordpress.org/Function_Reference/load_theme_textdomain
 */

function optionsframework_options() {

	// Group Type Array
	$group_member_type = array(
		'active' => __('Active', 'ibuddy'),
		'newest' => __('Newest', 'ibuddy'),
		'popular' => __('Popular', 'ibuddy'),
		'random' => __('Random', 'ibuddy')
	);

	// Multicheck Array
	$multicheck_array = array(
		'one' => __('French Toast', 'ibuddy'),
		'two' => __('Pancake', 'ibuddy'),
		'three' => __('Omelette', 'ibuddy'),
		'four' => __('Crepe', 'ibuddy'),
		'five' => __('Waffle', 'ibuddy')
	);

	// Multicheck Defaults
	$multicheck_defaults = array(
		'one' => '1',
		'five' => '1'
	);

	// Background Defaults
	$background_defaults = array(
		'color' => '',
		'image' => '',
		'repeat' => 'repeat',
		'position' => 'top center',
		'attachment'=>'scroll' );

	// Typography Defaults
	$typography_defaults = array(
		'size' => '15px',
		'face' => 'georgia',
		'style' => 'bold',
		'color' => '#bada55' );
		
	// Typography Options
	$typography_options = array(
		'sizes' => array( '6','12','14','16','20' ),
		'faces' => array( 'Helvetica Neue' => 'Helvetica Neue','Arial' => 'Arial' ),
		'styles' => array( 'normal' => 'Normal','bold' => 'Bold' ),
		'color' => false
	);

	// Pull all the pages into an array
	$options_pages = array();
	$options_pages_obj = get_pages('sort_column=post_parent,menu_order');
	$options_pages[''] = 'Select a page:';
	foreach ($options_pages_obj as $page) {
		$options_pages[$page->ID] = $page->post_title;
	}


	// Skins Stylesheet Pthes	
	$defined_stylesheets = array(
	"0" => "Default", // There is no "default" stylesheet to load
	get_template_directory_uri() . '/css/red.css' => "Red",
	get_template_directory_uri() . '/css/pink.css' => "Pink",
	get_template_directory_uri() . '/css/orange.css' => "Orange",
	get_template_directory_uri() . '/css/green.css' => "Green"
	);


	$options = array();

	/************** General Settings Area *****************************/
	$options[] = array(
		'name' => __('General Settings', 'ibuddy'),
		'type' => 'heading');
	
	// Logo
	$options[] = array(
		'name' => __('Logo', 'ibuddy'),
		'desc' => __('Upload your logo. It will be used instead of the texture logo (recommended max size H80px , w600px)', 'ibuddy'),
		'id' => 'logo',
		'type' => 'upload');
	
	
	// Skin
	$options[] = array( "name" => __("Skin Color","ibuddy"),
	"desc" => __("Select a skin color.","ibuddy"),
	"id" => "stylesheet",
	"std" => "0",
	"type" => "select",
	"options" => $defined_stylesheets );


	/******************** Home Page Settings ********************/

	
	$options[] = array(
		'name' => __('Home Page', 'ibuddy'),
		'type' => 'heading' );

	// Groups Avatars
	$options[] = array(
		'name' => __('Groups Avatars', 'ibuddy'),
		'desc' => __('Un-Check this if you would like to disable the Groups Avatars and If you want to use the (Custom Image) or the (Welcome Message) on Home Page next to the login form.', 'ibuddy'),
		'id' => 'groups_avatars',
		'std' => '1',
		'type' => 'checkbox');	

	$options[] = array(
		'name' => __('Groups Avatars Type', 'ibuddy'),
		'desc' => __('Select what type of Groups you want to display.', 'ibuddy'),
		'id' => 'groups_type_select',
		'std' => 'active',
		'type' => 'select',
		'class' => 'small', //mini, tiny, small
		'options' => $group_member_type);

	// Image
	$options[] = array(
		'name' => __('Custom Image', 'ibuddy'),
		'desc' => __('Check this if you would like to show an Image on Home Page next to the login form.', 'ibuddy'),
		'id' => 'home_image',
		'std' => '0',
		'type' => 'checkbox');	


	$options[] = array(
		'name' => __('Custom Image URL', 'ibuddy'),
		'desc' => __('Insert the URL or Upload an image for the Home page.', 'ibuddy'),
		'id' => 'home_image_upload',
		'type' => 'upload');



	// Welcome Message
	$options[] = array(
		'name' => __('Welcome Message', 'ibuddy'),
		'desc' => __('Check this if you would like to display a custom message on Home Page next to the login form.', 'ibuddy'),
		'id' => 'home_message',
		'std' => '0',
		'type' => 'checkbox');	

	
	$options[] = array(
		'name' => __('Welcome Message Title', 'ibuddy'),
		'desc' => __('Insert your Welcome Message Title.', 'ibuddy'),
		'id' => 'home_message_title',
		'std' => 'Hello and Welcome to iBuddy Community',
		'type' => 'text');


	$options[] = array(
		'name' => __('Welcome Message Content', 'ibuddy'),
		'desc' => __('Insert your Welcome Message Content. Note: you can use HTML', 'ibuddy'),
		'id' => 'home_message_content',
		'std' => 'Our community is based on people just like you. Active, motivated and loving to all about WordPress and BuddyPress',
		'type' => 'textarea');


	/****************** Registration Settings  ***********************/

	$options[] = array(
		'name' => __('Registration Page ', 'ibuddy'),
		'type' => 'heading' );

	
	// Members Counter
	$options[] = array(
		'name' => __('Members Counter', 'ibuddy'),
		'desc' => __('Un-check this if you would like to disable the Active Members Message on the registration page', 'ibuddy'),
		'id' => 'counter',
		'std' => '1',
		'type' => 'checkbox');	


	// Members Avatars
	$options[] = array(
		'name' => __('Members Avatars', 'ibuddy'),
		'desc' => __('Un-Check this if you would like to disable the Members Avatars and If you want to use the (Custom Image) or the (Custom Message) on Registration Page.', 'ibuddy'),
		'id' => 'members_avatars',
		'std' => '1',
		'type' => 'checkbox');	

	$options[] = array(
		'name' => __('Members Avatars Type', 'ibuddy'),
		'desc' => __('Select what type of Members you want to display.', 'ibuddy'),
		'id' => 'members_type_select',
		'std' => 'active',
		'type' => 'select',
		'class' => 'small', //mini, tiny, small
		'options' => $group_member_type);
	
	
	// Image
	$options[] = array(
		'name' => __('Custom Image', 'ibuddy'),
		'desc' => __('Check this if you would like to show an Image on Registration Page.', 'ibuddy'),
		'id' => 'register_image',
		'std' => '0',
		'type' => 'checkbox');

	$options[] = array(
		'name' => __('Registration Image', 'ibuddy'),
		'desc' => __('Upload an image for the registration page. It will be used instead of the Members Avatars', 'ibuddy'),
		'id' => 'register_image_upload',
		'type' => 'upload');


	
	// Custom Message
	$options[] = array(
		'name' => __('Custom Message', 'ibuddy'),
		'desc' => __('Check this if you would like to display a custom message on Registration Page.', 'ibuddy'),
		'id' => 'register_message',
		'std' => '0',
		'type' => 'checkbox');	

	
	$options[] = array(
		'name' => __('Custom Message Title', 'ibuddy'),
		'desc' => __('Insert your Custom Message Title.', 'ibuddy'),
		'id' => 'register_message_title',
		'std' => 'Hello and Welcome to iBuddy Community',
		'type' => 'text');


	$options[] = array(
		'name' => __('Custom Message Content', 'ibuddy'),
		'desc' => __('Insert your Custom Message Content. Note: you can use HTML', 'ibuddy'),
		'id' => 'register_message_content',
		'std' => 'Our community is based on people just like you. Active, motivated and loving to all about WordPress and BuddyPress',
		'type' => 'textarea');




		/****************** Footer  ***********************/

	$options[] = array(
		'name' => __('Footer ', 'ibuddy'),
		'type' => 'heading' );


	
	$options[] = array(
		'name' => __('Footer Copyright Text', 'ibuddy'),
		'desc' => __('Insert your copyright text Content. Note: you can use HTML', 'ibuddy'),
		'id' => 'copyright',
		'std' => '',
		'type' => 'textarea');
		
		/*********************** Advanced Settings **********************/


	$options[] = array(
		'name' => __('Advanced Settings', 'ibuddy'),
		'type' => 'heading');

	$options[] = array(
		'name' => __('Lock-down BuddyPress', 'ibuddy'),
		'desc' => __('Note: Checking this box will lock-down you BuddyPress for only logged in members and if a member/visitor is not registered and logged in will be redirected to the page you select below.', 'ibuddy'),
		'id' => 'lock-down',
		'std' => '0',
		'type' => 'checkbox');
		
	$options[] = array(
		'name' => __('Redirect Page', 'ibuddy'),
		'desc' => __('Select where to redirect logged out members/visitors. (Note: The above option should be checked for this option to be active. If no page is selected, the default will be the login page) (WARNING: Do not select any BuddyPress component (members, groups, activity) page. And if you do the redirection will lead to an error.) The "Registration" and "Activation" pages are ok', 'ibuddy'),
		'id' => 'redirect',
		'type' => 'select',
		'options' => $options_pages);


	$options[] = array(
		'name' => __('Lock-down Home Page', 'ibuddy'),
		'desc' => __('Note: Checking this box will lock-down the Home page and make it accessible by only NON logged in members.', 'ibuddy'),
		'id' => 'lock_down_home',
		'std' => '0',
		'type' => 'checkbox');


       		 /*******************************************************************/


	return $options;
}

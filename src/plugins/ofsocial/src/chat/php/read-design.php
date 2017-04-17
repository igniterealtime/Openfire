<?php

/*

Jappix - An open social platform
This is the design configuration reader

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 28/12/10

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

// Get the available backgrounds
$backgrounds = getBackgrounds();
$backgrounds_number = count($backgrounds);

// Read the background configuration
$background = readBackground();

// Backgrounds are missing?
if(!$backgrounds_number && ($background['type'] == 'image'))
	$background['type'] = 'default';

switch($background['type']) {
	// Simple notice input
	case 'image':
		$background_image = ' checked=""';
		$background_default = '';
		
		break;
	
	// Advanced notice input
	case 'color':
		$background_color = ' checked=""';
		$background_default = '';
		
		break;
}

switch($background['image_repeat']) {
	// No repeat
	case 'no-repeat':
		$background_image_repeat_no = ' selected=""';
		$background_image_repeat_x = '';
		
		break;
		
	// Repeat
	case 'repeat':
		$background_image_repeat_all = ' selected=""';
		$background_image_repeat_x = '';
		
		break;
	
	// Y repeat
	case 'repeat-y':
		$background_image_repeat_y = ' selected=""';
		$background_image_repeat_x = '';
		
		break;
}

switch($background['image_horizontal']) {
	// Left position
	case 'left':
		$background_image_horizontal_left = ' selected=""';
		$background_image_horizontal_center = '';
		
		break;
	
	// Right position
	case 'right':
		$background_image_horizontal_right = ' selected=""';
		$background_image_horizontal_center = '';
		
		break;
}

switch($background['image_vertical']) {
	// Left position
	case 'top':
		$background_image_vertical_top = ' selected=""';
		$background_image_vertical_center = '';
		
		break;
	
	// Right position
	case 'bottom':
		$background_image_vertical_bottom = ' selected=""';
		$background_image_vertical_center = '';
		
		break;
}

if($background['image_adapt'] == 'on')
	$background_image_adapt = ' checked=""';

// Read the notice configuration
$notice_conf = readNotice();
$notice_text = $notice_conf['notice'];

switch($notice_conf['type']) {
	// Simple notice input
	case 'simple':
		$notice_simple = ' checked=""';
		$notice_none = '';
		
		break;
	
	// Advanced notice input
	case 'advanced':
		$notice_advanced = ' checked=""';
		$notice_none = '';
		
		break;
}

?>

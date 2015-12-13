<?php

/*

Jappix - An open social platform
These are the design configuration variables

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 28/12/10

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

// Define initial background form values
$background_default = ' checked=""';
$background_image = '';
$background_color = '';
$background_image_repeat_no = '';
$background_image_repeat_all = '';
$background_image_repeat_x = ' selected=""';
$background_image_repeat_y = '';
$background_image_horizontal_center = ' selected=""';
$background_image_horizontal_left = '';
$background_image_horizontal_right = '';
$background_image_vertical_center = ' selected=""';
$background_image_vertical_top = '';
$background_image_vertical_bottom = '';
$background_image_adapt = '';

// Define initial notice form values
$notice_none = ' checked=""';
$notice_simple = '';
$notice_advanced = '';
$notice_text = '';

// Current background folder
$backgrounds_folder = 'backgrounds';

?>

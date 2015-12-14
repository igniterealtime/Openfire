<?php

/*

Jappix - An open social platform
This is the design configuration POST handler (manager)

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 08/05/11

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

// Handle the remove POST
if(isset($_POST['remove']))
	removeElements();

// Handle the upload POST
else if(isset($_POST['upload'])) {
	// Get the file path
	$name_background_image = $_FILES['background_image_upload']['name'];
	$temp_background_image = $_FILES['background_image_upload']['tmp_name'];
	$path_background_image = JAPPIX_BASE.'/store/backgrounds/'.$name_background_image;
	
	// An error occured?
	if(!isSafe($name_background_image) || $_FILES['background_image_upload']['error'] || !move_uploaded_file($temp_background_image, $path_background_image)) { ?>
	
		<p class="info smallspace fail"><?php _e("The image could not be received, would you mind retry?"); ?></p>
	
	<?php }
	
	// Bad extension?
	else if(!isImage($name_background_image)) {
		// Remove the image file
		if(file_exists($path_background_image))
			unlink($path_background_image);	
	?>
	
		<p class="info smallspace fail"><?php _e("This is not a valid image, please use PNG, GIF or JPG!"); ?></p>
	
	<?php }
	
	// The file has been sent
	else { ?>
	
		<p class="info smallspace success"><?php _e("Your image was added to the list!"); ?></p>
	
	<?php }
}

// Handle the save POST
else if(isset($_POST['save'])) {
	// Marker
	$save_marker = true;
	
	// Handle it for background
	$background = array();
	
	if(isset($_POST['background_type']))
		$background['type'] = $_POST['background_type'];
	
	if(isset($_POST['background_image_file']))
		$background['image_file'] = $_POST['background_image_file'];

	if(isset($_POST['background_image_repeat']))
		$background['image_repeat'] = $_POST['background_image_repeat'];
	
	if(isset($_POST['background_image_horizontal']))
		$background['image_horizontal'] = $_POST['background_image_horizontal'];
	
	if(isset($_POST['background_image_vertical']))
		$background['image_vertical'] = $_POST['background_image_vertical'];
	
	if(isset($_POST['background_image_adapt']))
		$background['image_adapt'] = 'on';
	
	if(isset($_POST['background_image_color']))
		$background['image_color'] = $_POST['background_image_color'];
	
	if(isset($_POST['background_color_color']))
		$background['color_color'] = $_POST['background_color_color'];
	
	// Write the configuration file
	writeBackground($background);
	
	// Handle it for notice
	if(isset($_POST['notice_type']))
		$notice_type = $_POST['notice_type'];
	else
		$notice_type = 'none';
	
	$notice_text = '';
	
	if(isset($_POST['notice_text']))
		$notice_text = $_POST['notice_text'];
	
	// Check our values
	if(!$notice_text && ($notice_type != 'none'))
		$save_marker = false;
	
	// All is okay
	if($save_marker) {
		// Write the notice configuration
		writeNotice($notice_type, $notice_text);
		
		// Show a success notice
		?>
		
			<p class="info smallspace success"><?php _e("Your design preferences have been saved!"); ?></p>
		
		<?php }
		
		// Something went wrong
		else { ?>
		
			<p class="info smallspace fail"><?php _e("Please check your inputs: something is missing!"); ?></p>
		
		<?php
	}
}

?>

<?php

/*

Jappix - An open social platform
This is the store configuration POST handler (manager)

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 28/12/10

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

// Music upload?
if(isset($_POST['upload'])) {
	// Get the file path
	$name_music = $_FILES['music_file']['name'];
	$temp_music = $_FILES['music_file']['tmp_name'];
	
	// Any special name submitted?
	if(isset($_POST['music_title']) && !empty($_POST['music_title'])) {
		// Add a form var
		$music_title = $_POST['music_title'];
		
		// Get the file extension
		$ext_music = getFileExt($name_music);
		
		// New name
		$name_music = '';
		
		// Add the artist name?
		if(isset($_POST['music_artist']) && !empty($_POST['music_artist'])) {
			// Add a form var
			$music_artist = $_POST['music_artist'];
			
			// Add the current POST var to the global string
			$name_music .= $_POST['music_artist'].' - ';
		}
		
		// Add the music title
		$name_music .= $_POST['music_title'];
		
		// Add the album name?
		if(isset($_POST['music_album']) && !empty($_POST['music_album'])) {
			// Add a form var
			$music_album = $_POST['music_album'];
			
			// Add the current POST var to the global string
			$name_music .= ' ['.$_POST['music_album'].']';
		}
		
		// Add the extension
		$name_music .= '.'.$ext_music;
	}
	
	// Music path with new name
	$path_music = JAPPIX_BASE.'/store/music/'.$name_music;
	
	// An error occured?
	if(!isSafe($name_music) || $_FILES['music_file']['error'] || !move_uploaded_file($temp_music, $path_music)) { ?>
	
		<p class="info smallspace fail"><?php _e("The music could not be received, please retry!"); ?></p>
	
	<?php }
	
	// Bad extension?
	else if(!preg_match('/^(.+)(\.(og(g|a)|mp3|wav))$/i', $name_music)) {
		// Remove the image file
		if(file_exists($path_music))
			unlink($path_music);
	?>
	
		<p class="info smallspace fail"><?php _e("This is not a valid music file, please encode in Ogg Vorbis, MP3 or WAV!"); ?></p>
	
	<?php }
	
	// The file has been sent
	else { ?>
	
		<p class="info smallspace success"><?php _e("Your music has been added!"); ?></p>
	
	<?php
		// Reset the form vars
		$music_title = '';
		$music_artist = '';
		$music_album = '';
	}
}

// File deletion?
else if(isset($_POST['remove']))
	removeElements();

?>

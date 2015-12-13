<?php
/*
Title: Defaults
Setting: piklist_wp_helpers
Tab: Media
Order: 650
Tab Order: 70
Flow: WP Helpers Settings Flow
*/

$options = get_option('piklist_wp_helpers');

	piklist('field', array(
	  'type' => 'select'
	  ,'field' => 'image_default_align'
	  ,'label' => 'Default Alignment'
	  ,'choices' => array(
	    'left' => 'Left'
	    ,'center' => 'Center'
	    ,'right' => 'Right'
	    ,'none' => 'None'
	  )
	));

	piklist('field', array(
	  'type' => 'select'
	  ,'field' => 'image_default_link_type'
	  ,'label' => 'Default Link Type'
	  ,'choices' => array(
	    'file' => 'Media File'
	    ,'post' => 'Attachment Page'
	    ,'custom' => 'Custom URL'
	  )
	));

$sizes = apply_filters( 'image_size_names_choose', array(
						'thumbnail' => __('Thumbnail'),
						'medium'    => __('Medium'),
						'large'     => __('Large'),
						'full'      => __('Full Size'),
					) );

	piklist('field', array(
	  'type' => 'select'
	  ,'field' => 'image_default_size'
	  ,'label' => 'Default Image Size'
	  ,'choices' => $sizes
	));


if(isset($options['image_default_align']))
{
	update_option('image_default_align', $options['image_default_align']);
	update_option('image_default_link_type', $options['image_default_link_type']);
	update_option('image_default_size', $options['image_default_size']);
}
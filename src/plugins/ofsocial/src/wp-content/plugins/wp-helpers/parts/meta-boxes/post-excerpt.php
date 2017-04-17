<?php
/*
Title: Excerpt
Post Type: piklist_wp_helpers_post
Priority: high
Order: 0
*/
  
piklist('field', array(
	'type' => 'editor'
	,'scope' => 'post'
	,'field' => 'post_excerpt'
	,'template' => 'field'
	,'options' => array (
		'media_buttons' => false
		,'teeny' => true
	)
	,'attributes' => array(
		'class' => 'piklist_wp_helpers_post_excerpt'
	)
));
?>
<p><?php _e('Excerpts are optional hand-crafted summaries of your content that can be used in your theme. <a href="https://codex.wordpress.org/Excerpt" target="_blank">Learn more about manual excerpts.</a>'); ?></p>
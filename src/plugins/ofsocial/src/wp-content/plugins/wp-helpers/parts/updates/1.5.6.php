<?php
/*
 * Update script for checkboxes in Piklist 0.9.4
 *
 * Remove Dashboard widgets
 * Remove Widgets
 * 
 */

$settings = get_option('piklist_wp_helpers');

if(!empty($settings))
{
	$settings['remove_dashboard_widgets']['dashboard_widgets'] = array($settings['remove_dashboard_widgets']['dashboard_widgets']);
	$settings['remove_widgets']['widgets'] = array($settings['remove_widgets']['widgets']);

	delete_option('piklist_wp_helpers');

	add_option('piklist_wp_helpers', $settings);
}
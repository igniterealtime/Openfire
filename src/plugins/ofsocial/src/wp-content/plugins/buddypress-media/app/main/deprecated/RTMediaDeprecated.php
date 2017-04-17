<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of rtMediaDeprecated
 *
 * @author Udit Desai <udit.desai@rtcamp.com>
 */
class RTMediaDeprecated {
	//put your code here

	var $deprecate_notice = false;

	static function uploadshortcode() {
		//
		//add_shortcode('rtmedia_uploader', array($this, 'pre_render'));
		$deprecated = false;
		$deprecate_notice = '';
//		echo self::generate_notice(__METHOD__, $deprecated, $deprecate_notice);
	}

	static function generate_notice($method, $deprecated=false, $notice='') {
		return sprintf(__("Deprecated %s. Please use %s.",'buddypress-media' ), $deprecated, $method);
	}
}
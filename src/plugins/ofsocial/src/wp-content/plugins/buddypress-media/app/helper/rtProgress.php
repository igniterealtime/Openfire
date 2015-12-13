<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of rtProgress
 *
 * @author saurabh
 */
class rtProgress {

	/**
	 * Constructor
	 *
	 * @access public
	 * @return void
	 */
	function __construct() {

	}

	/**
	 * Show progress_ui.
	 *
	 * @access public
	 * @param  float  $progress
	 * @param  bool   $echo
	 * @return string $progress_ui
	 */
	public function progress_ui( $progress, $echo = true ) {
		$progress_ui = '
			<div id="rtprogressbar">
				<div style="width:'.$progress.'%"></div>
			</div>
			';

		if ( $echo ){
			echo $progress_ui;
		} else {
			return $progress_ui;
		}
	}

	/**
	 * Calculate progress %.
	 *
	 * @access public
	 * @param  float  $progress
	 * @param  float  $total
	 * @return float
	 */
	public function progress( $progress, $total ) {
		if ( $total < 1 ){
			return 100;
		}

		return ( $progress / $total ) * 100;
	}

}

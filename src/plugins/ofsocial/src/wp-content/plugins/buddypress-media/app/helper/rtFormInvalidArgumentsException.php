<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of rtException
 *
 * @author udit
 */

if ( ! class_exists( "rtFormsInvalidArgumentsException" ) ){

	class rtFormInvalidArgumentsException extends Exception {

		public function __construct( $msg ){

			//Error Message
			$errorMsg = sprintf( __( 'Error on line %s in %s : <b>The method expects an array in arguments for %s provided.</b>', 'buddypress-media' ), $this->getLine(), $this->getFile(), $msg );

			echo $errorMsg;
		}
	}
}
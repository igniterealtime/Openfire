<?php

/**
 * Logs a given message with an optional context string and timestamp
 *
 * @author Saurabh Shukla <saurabh.shukla@rtcamp.com>
 */
if ( ! class_exists( 'BPMediaLog' ) ){

	class BPMediaLog {
		/**
		 * Formats and logs the error message
		 *
		 * @param any $msg The message to log
		 * @param string $context The context string, optional
		 * @return boolean True if successful
		 */

		/**
		 *
		 * @param type $msg
		 * @param string $context
		 * @param string $log_file
		 * @return type
		 */
		public function __construct( $msg, $context = '', $log_file = '' ) {
			$log_msg = $this->log_msg( $msg, $context = '' );
			if ( $log_file == '' ){
			    $log_file = RTMEDIA_PATH . 'log/rtmedia.log';
			}
			return $this->log( $log_msg, $log_file );
		}

		/**
		 * Formats the message
		 *
		 * @param any $msg The message to format
		 * @param string $context The context string, optional
		 * @return string The formatted log entry
		 */

		/**
		 *
		 * @param type $msg
		 * @param type $context
		 * @return type
		 */
		function log_msg( $msg, $context = '' ) {
			$logmsg = gmdate( "Y-m-d H:i:s " ) . " | ";
			if ( $context ){
			    $logmsg .= $context . " | ";
			}
			if ( ! is_string( $msg ) ){
			    $msg = var_export( $msg, false );
			}
			$logmsg .= $msg;
			return $logmsg;
		}

		/**
		 * Logs the entry to the log file
		 *
		 * @param string $logmsg The formatted log entry
		 * @param string $file The log file's path
		 * @return boolean Success
		 */

		/**
		 *
		 * @param type $logmsg
		 * @param type $file
		 * @return boolean
		 */
		public function log( $logmsg, $file ) {
			$fp = fopen( RTMEDIA_PATH . 'plugin.log', "a+" );
			if ( $fp ){
			    fwrite( $fp, "\n" . $logmsg );
			    fclose( $fp );
			}
			return true;
		}

	}

}
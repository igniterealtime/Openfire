<?php


/**
 * BackPress logging level constants
 */
define('BP_LOG_NONE',    0);
define('BP_LOG_FAIL',    1);
define('BP_LOG_ERROR',   2);
define('BP_LOG_WARNING', 4);
define('BP_LOG_NOTICE',  8);
define('BP_LOG_DEBUG',   16);

/**
 * Combination of all errors (excluding none and debug)
 */
define('BP_LOG_ALL', BP_LOG_FAIL + BP_LOG_ERROR + BP_LOG_WARNING + BP_LOG_NOTICE);



/**
 * Provides easy and abstracted logging facilities
 *
 * @package BackPress
 */
class BP_Log
{
	/**
	 * The logging level
	 *
	 * @var integer
	 */
	var $level = BP_LOG_NONE;

	/**
	 * The type of logging
	 *
	 * @var string
	 */
	var $type = 'php';
	
	/**
	 * The types of logging available
	 *
	 * @var array
	 */
	var $types = array('php', 'file', 'display', 'console');

	/**
	 * The filename of the file to log messages to when type is "file"
	 *
	 * @var string
	 */
	var $filename = '';

	/**
	 * Whether or not the javascript functions are available
	 *
	 * @var boolean
	 **/
	var $console_javascript_loaded = false;

	/**
	 * Console log messages which are queued up to be displayed on page load
	 *
	 * @var array
	 **/
	var $console_javascript_onloads = array();

	/**
	 * Initialises the logging
	 *
	 * @return void
	 */
	function BP_log($level = false, $type = false, $filename = false)
	{
		$this->set_level($level);
		$this->set_type($type);
		$this->set_filename($filename);
	}

	/**
	 * Sets the logging level
	 *
	 * @return integer|boolean The old level on success or false
	 * @uses BP_LOG_LEVEL
	 */
	function set_level($level)
	{
		$old_level = $this->level;

		if (is_integer($level)) {
			$this->level = $level;
		} elseif (defined('BP_LOG_LEVEL') && is_integer(BP_LOG_LEVEL)) {
			$this->level = BP_LOG_LEVEL;
		} else {
			return false;
		}

		return $old_level;
	}

	/**
	 * Sets the logging type
	 *
	 * @return string|false The old type on success or false
	 * @uses BP_LOG_TYPE
	 */
	function set_type($type)
	{
		$old_type = $this->type;
		$type = strtolower($type);

		if (in_array($type, $this->types)) {
			$this->type = $type;
		} elseif (defined('BP_LOG_TYPE') && in_array(BP_LOG_TYPE, $this->types)) {
			$this->type = BP_LOG_TYPE;
		} else {
			return false;
		}

		return $old_type;
	}

	/**
	 * Sets the logging filename
	 *
	 * @return string|boolean The old filename on success or false
	 * @uses BP_LOG_FILENAME
	 */
	function set_filename($filename)
	{
		$old_filename = $this->filename;

		if (is_string($filename)) {
			$_filename = $filename;
		} elseif (defined('BP_LOG_FILENAME') && is_string(BP_LOG_FILENAME)) {
			$_filename = BP_LOG_FILENAME;
		} else {
			return false;
		}

		if (isset($_filename) && file_exists($_filename) && is_file($_filename) && is_writable($_filename)) {
			$this->filename = $_filename;
		} else {
			return false;
		}

		return $old_filename;
	}

	/**
	 * Sends a message to the log
	 *
	 * @return boolean True on success, false on failure
	 */
	function send($message = '', $level = BP_LOG_DEBUG, $type = false, $prefix = false)
	{
		// Make sure the level of this message is set to be logged
		if (($level & $this->level) === 0) {
			return;
		}

		// Format the message into an array of lines to be logged
		$lines = $this->format_message($message, $level, $prefix);

		// Do some validation on the type
		if ($type && in_array($type, $this->types)) {
			$_type = $type;
		} else {
			$_type = $this->type;
		}

		// Get a name for the level
		if ($level) {
			$_level = $this->get_level_from_integer($level);
		}

		// Setup strings to prepend to some of the types
		$prepend = $_level . ': ';
		if ($prefix) {
			$prepend .= $prefix . ': ';
		}
		$pad = str_repeat(' ', strlen($prepend) - 2) . '| ';

		// Switch over the four types
		switch ($_type) {
			case 'php':
				$php_fail = false;

				// Check that the error_log() function is available
				if (function_exists('error_log') && is_callable('error_log')) {
					foreach ($lines as $key => $line) {
						if ($key === 0) {
							$_prepend = $prepend;
						} else {
							$_prepend = $pad;
						}
						if (!error_log($_prepend . $line, 0)) {
							$php_fail = true;
							break;
						}
					}
				} else {
					$php_fail = true;
				}

				if ($php_fail) {
					// The PHP error log process failed, path of least resistance is to write to display
					$this->send($message, $level, 'display', $prefix);
					return;
				}
				break;

			case 'file':
				$file_fail = false;

				// We've already done the prerequisite checks on the file by now so just write to it
				if (!$file_handle = fopen($this->filename, 'a')) {
					$file_fail = true;
				} else {
					// Prepare a string to write
					$_lines = array(
						'[' . date('c') . ']',
						'[client ' . $_SERVER['REMOTE_ADDR'] . ']',
						$prepend,
						join("\n", $lines)
					);
				}
				if (fwrite($file_handle, join(' ', $_lines) . "\n") === false) {
					$file_fail = true;
				}
				if ($file_handle) {
					fclose($file_handle);
				}
				if ($file_fail) {
					// Writing to file failed, output to display
					$this->send($message, $level, 'display', $prefix);
					return;
				}
				break;

			case 'display':
				$_lines = array();
				foreach ($lines as $key => $line) {
					if ($key === 0) {
						$_lines[] = $prepend . $line;
					} else {
						$_lines[] = $pad . $line;
					}
				}
				echo '<div class="bplog_message bplog_level_' . strtolower($_level) . '"><pre>' . join("\n", $_lines) . '</pre></div>' . "\n";
				break;

			case 'console':
				$_lines = array();
				foreach ($lines as $key => $line) {
					if ($key === 0 && $prefix) {
						$_lines[] = $prefix . ': ' . $line;
					} else {
						$_lines[] = $line;
					}
				}
				
				$_lines = $ident . $_level . ' ~\n' . str_replace('\'', '\\\'', join('\n', $_lines));
				
				if (!$this->console_javascript_loaded) {
					// Queue it for logging onload
					$this->console_javascript_onloads[] = array('message' => $_lines, 'level' => $level, 'time' => date('c'));
				} else {
					// Log it now
					echo '<script type="text/javascript" charset="utf-8">bp_log_add(\'' . $this->_esc_js_log( $_lines ) . '\', ' . $this->_esc_js_log( $level ) . ', \'' . $this->_esc_js_log( date('c') ) . '\');</script>' . "\n";
				}
				break;
		}

		return true;
	}

	/**
	 * Gets the name of the log level from an integer
	 *
	 * @return string The logging level
	 */
	function get_level_from_integer($integer)
	{
		switch ($integer) {
			case BP_LOG_NONE:
				return 'BP_LOG_NONE';
				break;
			case BP_LOG_FAIL:
				return 'BP_LOG_FAIL';
				break;
			case BP_LOG_ERROR:
				return 'BP_LOG_ERROR';
				break;
			case BP_LOG_WARNING:
				return 'BP_LOG_WARNING';
				break;
			case BP_LOG_NOTICE:
				return 'BP_LOG_NOTICE';
				break;
			case BP_LOG_DEBUG:
				return 'BP_LOG_DEBUG';
				break;
			default:
				return 'BP_LOG_UNDEFINED';
				break;
		}
	}

	/**
	 * Formats a message for output to a log file
	 *
	 * @return boolean True on success, false on failure
	 */
	function format_message($message, $level = BP_LOG_DEBUG, $prefix = false, $tabs = 0)
	{
		$lines = array();
		
		if (is_null($message)) {
			$lines[] = 'null (' . var_export($message, true) . ')';
			return $lines;
		}
		
		if (is_bool($message)) {
			$lines[] = 'bool (' . var_export($message, true) . ')';
			return $lines;
		}

		if (is_string($message)) {
			if ($level === BP_LOG_DEBUG || $message === '') {
				$lines[] = 'string(' . strlen($message) . ') ("' . $message . '")';
			} else {
				$lines[] = $message;
			}
			return $lines;
		}

		if (is_array($message) || is_object($message)) {
			if (is_array($message)) {
				$lines[] = 'array(' . count($message) . ') (';
			} else {
				$lines[] = 'object(' . get_class($message) . ') (';
			}
			$tabs++;
			foreach ($message as $key => $value) {
				$array = $this->format_message($value, $level, false, $tabs);
				if (is_array($array)) {
					$array[0] = str_repeat('    ', $tabs) . $key . ' => ' . $array[0];
					$lines = array_merge($lines, $array);
				} else {
					$lines[] = str_repeat('    ', $tabs) . $key . ' => ' . $array;
				}
			}
			$tabs--;
			$lines[] = str_repeat('    ', $tabs) . ')';
			return $lines;
		}

		if (is_int($message)) {
			$lines[] = 'int (' . $message . ')';
			return $lines;
		}

		if (is_float($message)) {
			$lines[] = 'float (' . $message . ')';
			return $lines;
		}

		if (is_resource($message)) {
			$lines[] = 'resource (' . get_resource_type($message) . ')';
			return $lines;
		}

		$lines[] = 'unknown (' . $message . ')';
		return $lines;
	}

	/**
	 * Send a debug message
	 *
	 * @return boolean True on success, false on failure
	 */
	function debug($message, $prefix = false)
	{
		$this->send($message, BP_LOG_DEBUG, false, $prefix);
	}

	/**
	 * Send a notice message
	 *
	 * If the message is an array, then it sends each index as a separate message
	 *
	 * @return boolean True on success, false on failure
	 */
	function notice($message)
	{
		if (is_array($message)) {
			foreach ($message as $value) {
				$this->send($value, BP_LOG_NOTICE);
			}
		} else {
			$this->send($message, BP_LOG_NOTICE);
		}
	}

	/**
	 * Send a warning message
	 *
	 * If the message is an array, then it sends each index as a separate message
	 *
	 * @return boolean True on success, false on failure
	 */
	function warning($message)
	{
		if (is_array($message)) {
			foreach ($message as $value) {
				$this->send($value, BP_LOG_WARNING);
			}
		} else {
			$this->send($message, BP_LOG_WARNING);
		}
	}

	/**
	 * Send an error message
	 *
	 * If the message is an array, then it sends each index as a separate message
	 *
	 * @return boolean True on success, false on failure
	 */
	function error($message)
	{
		if (is_array($message)) {
			foreach ($message as $value) {
				$this->send($value, BP_LOG_ERROR);
			}
		} else {
			$this->send($message, BP_LOG_ERROR);
		}
	}

	/**
	 * Send an error message and die
	 *
	 * If the message is an array, then it sends each index as a separate message
	 *
	 * @return boolean True on success, false on failure
	 */
	function fail($message)
	{
		if (is_array($message)) {
			foreach ($message as $value) {
				$this->send($value, BP_LOG_FAIL);
			}
		} else {
			$this->send($message, BP_LOG_FAIL);
		}

		die();
	}

	/**
	 * Outputs javascript functions for the head of the html document
	 *
	 * Must be included in the head of the debug document somehow when using 'console' type.
	 *
	 * @return void
	 **/
	function console_javascript()
	{
		if ($this->type !== 'console') {
			return;
		}

		$this->console_javascript_loaded = true;
?>

	<script type="text/javascript" charset="utf-8">
		var BP_LOG_NONE    = 0;
		var BP_LOG_FAIL    = 1;
		var BP_LOG_ERROR   = 2;
		var BP_LOG_WARNING = 4;
		var BP_LOG_NOTICE  = 8;
		var BP_LOG_DEBUG   = 16;
		
		function bp_log_send(message, level, time) {
			if (window.console) {
				// Works in later Safari and Firefox with Firebug
				switch (level) {
					case BP_LOG_NONE:
						// This shouldn't happen really
						break;
					case BP_LOG_FAIL:
					case BP_LOG_ERROR:
						window.console.error("[" + time + "] " + message);
						break;
					case BP_LOG_WARNING:
						window.console.warn("[" + time + "] " + message);
						break;
					case BP_LOG_NOTICE:
						window.console.info("[" + time + "] " + message);
						break;
					case BP_LOG_DEBUG:
						window.console.log("[" + time + "] " + message);
						break;
					default:
						break;
				}
			}
		}

		var bp_log_queue = new Array();

		function bp_log_add(message, level, time) {
			bp_log_queue.push(new Array(message, level, time));
		}

		function bp_log_process() {
			while (item = bp_log_queue.shift()) {
				bp_log_send(item[0], item[1], item[2]);
			}
		}

		function bp_log_onload() {
<?php
		foreach ($this->console_javascript_onloads as $onload) {
			echo "\t\t\t" . 'bp_log_send(\'' . $this->_esc_js_log( $onload['message'] ) . '\', ' . $this->_esc_js_log( $onload['level'] ) . ', \'' . $this->_esc_js_log( $onload['time'] ) . '\');' . "\n";
		}
?>
			bp_log_process();
		}

		window.onload = bp_log_onload;
	</script>

<?php
	}

	function _esc_js_log( $message )
	{
		return str_replace(
			array( '\'', "\n" ),
			array( '\\\'', '\n' ),
			$message
		);
	}
	
} // END class BP_Log

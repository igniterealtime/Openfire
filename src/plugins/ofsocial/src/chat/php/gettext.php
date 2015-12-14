<?php

/*
   Copyright (c) 2003, 2005, 2006, 2009 Danilo Segan <danilo@kvota.net>.

   This file is part of PHP-gettext.

   PHP-gettext is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2 of the License, or
   (at your option) any later version.

   PHP-gettext is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with PHP-gettext; if not, write to the Free Software
   Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

*/

/**
 * MODIFIED FOR THE JAPPIX PROJECT
 * Last revision: 10/11/10
 */

  // Simple class to wrap file streams, string streams, etc.
  // seek is essential, and it should be byte stream
class StreamReader {
  // should return a string [FIXME: perhaps return array of bytes?]
  function read($bytes) {
    return false;
  }

  // should return new position
  function seekto($position) {
    return false;
  }

  // returns current position
  function currentpos() {
    return false;
  }

  // returns length of entire stream (limit for seekto()s)
  function length() {
    return false;
  }
};

class StringReader {
  var $_pos;
  var $_str;

  function StringReader($str='') {
    $this->_str = $str;
    $this->_pos = 0;
  }

  function read($bytes) {
    $data = substr($this->_str, $this->_pos, $bytes);
    $this->_pos += $bytes;
    if (strlen($this->_str)<$this->_pos)
      $this->_pos = strlen($this->_str);

    return $data;
  }

  function seekto($pos) {
    $this->_pos = $pos;
    if (strlen($this->_str)<$this->_pos)
      $this->_pos = strlen($this->_str);
    return $this->_pos;
  }

  function currentpos() {
    return $this->_pos;
  }

  function length() {
    return strlen($this->_str);
  }

};


class FileReader {
  var $_pos;
  var $_fd;
  var $_length;

  function FileReader($filename) {
    if (file_exists($filename)) {

      $this->_length=filesize($filename);
      $this->_pos = 0;
      $this->_fd = fopen($filename,'rb');
      if (!$this->_fd) {
        $this->error = 3; // Cannot read file, probably permissions
        return false;
      }
    } else {
      $this->error = 2; // File doesn't exist
      return false;
    }
  }

  function read($bytes) {
    if ($bytes) {
      fseek($this->_fd, $this->_pos);

      // PHP 5.1.1 does not read more than 8192 bytes in one fread()
      // the discussions at PHP Bugs suggest it's the intended behaviour
      $data = '';
      while ($bytes > 0) {
        $chunk  = fread($this->_fd, $bytes);
        $data  .= $chunk;
        $bytes -= strlen($chunk);
      }
      $this->_pos = ftell($this->_fd);

      return $data;
    } else return '';
  }

  function seekto($pos) {
    fseek($this->_fd, $pos);
    $this->_pos = ftell($this->_fd);
    return $this->_pos;
  }

  function currentpos() {
    return $this->_pos;
  }

  function length() {
    return $this->_length;
  }

  function close() {
    fclose($this->_fd);
  }

};

// Preloads entire file in memory first, then creates a StringReader
// over it (it assumes knowledge of StringReader internals)
class CachedFileReader extends StringReader {
  function CachedFileReader($filename) {
    if (file_exists($filename)) {

      $length=filesize($filename);
      $fd = fopen($filename,'rb');

      if (!$fd) {
        $this->error = 3; // Cannot read file, probably permissions
        return false;
      }
      $this->_str = fread($fd, $length);
      fclose($fd);

    } else {
      $this->error = 2; // File doesn't exist
      return false;
    }
  }
};

/*
   Copyright (c) 2003, 2009 Danilo Segan <danilo@kvota.net>.
   Copyright (c) 2005 Nico Kaiser <nico@siriux.net>

   This file is part of PHP-gettext.

   PHP-gettext is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2 of the License, or
   (at your option) any later version.

   PHP-gettext is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with PHP-gettext; if not, write to the Free Software
   Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

*/

/**
 * Provides a simple gettext replacement that works independently from
 * the system's gettext abilities.
 * It can read MO files and use them for translating strings.
 * The files are passed to gettext_reader as a Stream (see streams.php)
 *
 * This version has the ability to cache all strings and translations to
 * speed up the string lookup.
 * While the cache is enabled by default, it can be switched off with the
 * second parameter in the constructor (e.g. whenusing very large MO files
 * that you don't want to keep in memory)
 */
class gettext_reader {
  //public:
   var $error = 0; // public variable that holds error code (0 if no error)

   //private:
  var $BYTEORDER = 0;        // 0: low endian, 1: big endian
  var $STREAM = NULL;
  var $short_circuit = false;
  var $enable_cache = false;
  var $originals = NULL;      // offset of original table
  var $translations = NULL;    // offset of translation table
  var $pluralheader = NULL;    // cache header field for plural forms
  var $total = 0;          // total string count
  var $table_originals = NULL;  // table for original strings (offsets)
  var $table_translations = NULL;  // table for translated strings (offsets)
  var $cache_translations = NULL;  // original -> translation mapping


  /* Methods */


  /**
   * Reads a 32bit Integer from the Stream
   *
   * @access private
   * @return Integer from the Stream
   */
  function readint() {
      if ($this->BYTEORDER == 0) {
        // low endian
        $input=unpack('V', $this->STREAM->read(4));
        return array_shift($input);
      } else {
        // big endian
        $input=unpack('N', $this->STREAM->read(4));
        return array_shift($input);
      }
    }

  function read($bytes) {
    return $this->STREAM->read($bytes);
  }

  /**
   * Reads an array of Integers from the Stream
   *
   * @param int count How many elements should be read
   * @return Array of Integers
   */
  function readintarray($count) {
    if ($this->BYTEORDER == 0) {
        // low endian
        return unpack('V'.$count, $this->STREAM->read(4 * $count));
      } else {
        // big endian
        return unpack('N'.$count, $this->STREAM->read(4 * $count));
      }
  }

  /**
   * Constructor
   *
   * @param object Reader the StreamReader object
   * @param boolean enable_cache Enable or disable caching of strings (default on)
   */
  function gettext_reader($Reader, $enable_cache = true) {
    // If there isn't a StreamReader, turn on short circuit mode.
    if (! $Reader || isset($Reader->error) ) {
      $this->short_circuit = true;
      return;
    }

    // Caching can be turned off
    $this->enable_cache = $enable_cache;

    $MAGIC1 = "\x95\x04\x12\xde";
    $MAGIC2 = "\xde\x12\x04\x95";

    $this->STREAM = $Reader;
    $magic = $this->read(4);
    if ($magic == $MAGIC1) {
      $this->BYTEORDER = 1;
    } elseif ($magic == $MAGIC2) {
      $this->BYTEORDER = 0;
    } else {
      $this->error = 1; // not MO file
      return false;
    }

    // FIXME: Do we care about revision? We should.
    $revision = $this->readint();

    $this->total = $this->readint();
    $this->originals = $this->readint();
    $this->translations = $this->readint();
  }

  /**
   * Loads the translation tables from the MO file into the cache
   * If caching is enabled, also loads all strings into a cache
   * to speed up translation lookups
   * 
   * @access private
   */
  function load_tables() {
    if (is_array($this->cache_translations) &&
      is_array($this->table_originals) &&
      is_array($this->table_translations))
      return;

    /* get original and translations tables */
    $this->STREAM->seekto($this->originals);
    $this->table_originals = $this->readintarray($this->total * 2);
    $this->STREAM->seekto($this->translations);
    $this->table_translations = $this->readintarray($this->total * 2);

    if ($this->enable_cache) {
      $this->cache_translations = array ();
      /* read all strings in the cache */
      for ($i = 0; $i < $this->total; $i++) {
        $this->STREAM->seekto($this->table_originals[$i * 2 + 2]);
        $original = $this->STREAM->read($this->table_originals[$i * 2 + 1]);
        $this->STREAM->seekto($this->table_translations[$i * 2 + 2]);
        $translation = $this->STREAM->read($this->table_translations[$i * 2 + 1]);
        $this->cache_translations[$original] = $translation;
      }
    }
  }

  /**
   * Returns a string from the "originals" table
   *
   * @access private
   * @param int num Offset number of original string
   * @return string Requested string if found, otherwise ''
   */
  function get_original_string($num) {
    $length = $this->table_originals[$num * 2 + 1];
    $offset = $this->table_originals[$num * 2 + 2];
    if (! $length)
      return '';
    $this->STREAM->seekto($offset);
    $data = $this->STREAM->read($length);
    return (string)$data;
  }

  /**
   * Returns a string from the "translations" table
   *
   * @access private
   * @param int num Offset number of original string
   * @return string Requested string if found, otherwise ''
   */
  function get_translation_string($num) {
    $length = $this->table_translations[$num * 2 + 1];
    $offset = $this->table_translations[$num * 2 + 2];
    if (! $length)
      return '';
    $this->STREAM->seekto($offset);
    $data = $this->STREAM->read($length);
    return (string)$data;
  }

  /**
   * Binary search for string
   *
   * @access private
   * @param string string
   * @param int start (internally used in recursive function)
   * @param int end (internally used in recursive function)
   * @return int string number (offset in originals table)
   */
  function find_string($string, $start = -1, $end = -1) {
    if (($start == -1) or ($end == -1)) {
      // find_string is called with only one parameter, set start end end
      $start = 0;
      $end = $this->total;
    }
    if (abs($start - $end) <= 1) {
      // We're done, now we either found the string, or it doesn't exist
      $txt = $this->get_original_string($start);
      if ($string == $txt)
        return $start;
      else
        return -1;
    } else if ($start > $end) {
      // start > end -> turn around and start over
      return $this->find_string($string, $end, $start);
    } else {
      // Divide table in two parts
      $half = (int)(($start + $end) / 2);
      $cmp = strcmp($string, $this->get_original_string($half));
      if ($cmp == 0)
        // string is exactly in the middle => return it
        return $half;
      else if ($cmp < 0)
        // The string is in the upper half
        return $this->find_string($string, $start, $half);
      else
        // The string is in the lower half
        return $this->find_string($string, $half, $end);
    }
  }

  /**
   * Translates a string
   *
   * @access public
   * @param string string to be translated
   * @return string translated string (or original, if not found)
   */
  function translate($string) {
    if ($this->short_circuit)
      return $string;
    $this->load_tables();

    if ($this->enable_cache) {
      // Caching enabled, get translated string from cache
      if (array_key_exists($string, $this->cache_translations))
        return $this->cache_translations[$string];
      else
        return $string;
    } else {
      // Caching not enabled, try to find string
      $num = $this->find_string($string);
      if ($num == -1)
        return $string;
      else
        return $this->get_translation_string($num);
    }
  }

  /**
   * Sanitize plural form expression for use in PHP eval call.
   *
   * @access private
   * @return string sanitized plural form expression
   */
  function sanitize_plural_expression($expr) {
    // Get rid of disallowed characters.
    $expr = preg_replace('@[^a-zA-Z0-9_:;\(\)\?\|\&=!<>+*/\%-]@', '', $expr);

    // Add parenthesis for tertiary '?' operator.
    $expr .= ';';
    $res = '';
    $p = 0;
    for ($i = 0; $i < strlen($expr); $i++) {
      $ch = $expr[$i];
      switch ($ch) {
      case '?':
        $res .= ' ? (';
        $p++;
        break;
      case ':':
        $res .= ') : (';
        break;
      case ';':
        $res .= str_repeat( ')', $p) . ';';
        $p = 0;
        break;
      default:
        $res .= $ch;
      }
    }
    return $res;
  }

  /**
   * Get possible plural forms from MO header
   *
   * @access private
   * @return string plural form header
   */
  function get_plural_forms() {
    // lets assume message number 0 is header
    // this is true, right?
    $this->load_tables();

    // cache header field for plural forms
    if (! is_string($this->pluralheader)) {
      if ($this->enable_cache) {
        $header = $this->cache_translations[""];
      } else {
        $header = $this->get_translation_string(0);
      }
      if (eregi("plural-forms: ([^\n]*)\n", $header, $regs))
        $expr = $regs[1];
      else
        $expr = "nplurals=2; plural=n == 1 ? 0 : 1;";

      $this->pluralheader = $this->sanitize_plural_expression($expr);
    }
    return $this->pluralheader;
  }

  /**
   * Detects which plural form to take
   *
   * @access private
   * @param n count
   * @return int array index of the right plural form
   */
  function select_string($n) {
    $string = $this->get_plural_forms();
    $string = str_replace('nplurals',"\$total",$string);
    $string = str_replace("n",$n,$string);
    $string = str_replace('plural',"\$plural",$string);

    $total = 0;
    $plural = 0;

    eval("$string");
    if ($plural >= $total) $plural = $total - 1;
    return $plural;
  }

  /**
   * Plural version of gettext
   *
   * @access public
   * @param string single
   * @param string plural
   * @param string number
   * @return translated plural form
   */
  function ngettext($single, $plural, $number) {
    if ($this->short_circuit) {
      if ($number != 1)
        return $plural;
      else
        return $single;
    }

    // find out the appropriate form
    $select = $this->select_string($number);

    // this should contains all strings separated by NULLs
    $key = $single.chr(0).$plural;


    if ($this->enable_cache) {
      if (! array_key_exists($key, $this->cache_translations)) {
        return ($number != 1) ? $plural : $single;
      } else {
        $result = $this->cache_translations[$key];
        $list = explode(chr(0), $result);
        return $list[$select];
      }
    } else {
      $num = $this->find_string($key);
      if ($num == -1) {
        return ($number != 1) ? $plural : $single;
      } else {
        $result = $this->get_translation_string($num);
        $list = explode(chr(0), $result);
        return $list[$select];
      }
    }
  }

}

/*
   Copyright (c) 2005 Steven Armstrong <sa at c-area dot ch>
   Copyright (c) 2009 Danilo Segan <danilo@kvota.net>

   Drop in replacement for native gettext.

   This file is part of PHP-gettext.

   PHP-gettext is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2 of the License, or
   (at your option) any later version.

   PHP-gettext is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with PHP-gettext; if not, write to the Free Software
   Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

*/
/*
LC_CTYPE        0
LC_NUMERIC      1
LC_TIME         2
LC_COLLATE      3
LC_MONETARY     4
LC_MESSAGES     5
LC_ALL          6
*/


// LC_MESSAGES is not available if php-gettext is not loaded
// while the other constants are already available from session extension.
if (!defined('LC_MESSAGES')) {
  define('LC_MESSAGES',	5);
}

// Variables

global $text_domains, $default_domain, $LC_CATEGORIES, $EMULATEGETTEXT, $CURRENTLOCALE;
$text_domains = array();
$default_domain = 'messages';
$LC_CATEGORIES = array('LC_CTYPE', 'LC_NUMERIC', 'LC_TIME', 'LC_COLLATE', 'LC_MONETARY', 'LC_MESSAGES', 'LC_ALL');
$EMULATEGETTEXT = 0;
$CURRENTLOCALE = '';

/* Class to hold a single domain included in $text_domains. */
class domain {
  var $l10n;
  var $path;
  var $codeset;
}

// Utility functions

/**
 * Utility function to get a StreamReader for the given text domain.
 */
function _get_reader($domain=null, $category=5, $enable_cache=true) {
    global $text_domains, $default_domain, $LC_CATEGORIES;
    if (!isset($domain)) $domain = $default_domain;
    if (!isset($text_domains[$domain]->l10n)) {
        // get the current locale
        $locale = _setlocale(LC_MESSAGES, 0);
        $bound_path = isset($text_domains[$domain]->path) ?
          $text_domains[$domain]->path : './';
        $subpath = $LC_CATEGORIES[$category] ."/$domain.mo";
        /* Figure out all possible locale names and start with the most
           specific ones.  I.e. for sr_CS.UTF-8@latin, look through all of
           sr_CS.UTF-8@latin, sr_CS@latin, sr@latin, sr_CS.UTF-8, sr_CS, sr.
        */
        $locale_names = array();
        if (preg_match("/([a-z]{2,3})"            // language code
                       ."(_([A-Z]{2}))?"          // country code
                       ."(\.([-A-Za-z0-9_]))?"    // charset
                       ."(@([-A-Za-z0-9_]+))?/",  // @ modifier
                       $locale, $matches)) {
          
          $lang = '';
          $country = '';
          $charset = '';
          $modifier = '';
          
          if(isset($matches[1]))
             $lang = $matches[1];
          if(isset($matches[3]))
             $country = $matches[3];
          if(isset($matches[5]))
             $charset = $matches[5];
          if(isset($matches[7]))
             $modifier = $matches[7];
          
          if ($modifier) {
            $locale_names = array("${lang}_$country.$charset@$modifier",
                                  "${lang}_$country@$modifier",
                                  "$lang@$modifier");
          }
          array_push($locale_names,
                     "${lang}_$country.$charset", "${lang}_$country", "$lang");
        }
        array_push($locale_names, $locale);

        $input = null;
        foreach ($locale_names as $locale) {
          $full_path = $bound_path . $locale . "/" . $subpath;
          if (file_exists($full_path)) {
            $input = new FileReader($full_path);
            break;
          }
        }

        if (!array_key_exists($domain, $text_domains)) {
          // Initialize an empty domain object.
          $text_domains[$domain] = new domain();
        }
        $text_domains[$domain]->l10n = new gettext_reader($input,
                                                          $enable_cache);
    }
    return $text_domains[$domain]->l10n;
}

/**
 * Returns whether we are using our emulated gettext API or PHP built-in one.
 */
function locale_emulation() {
    global $EMULATEGETTEXT;
    return $EMULATEGETTEXT;
}

/**
 * Checks if the current locale is supported on this system.
 */
function _check_locale() {
    global $EMULATEGETTEXT;
    return !$EMULATEGETTEXT;
}

/**
 * Get the codeset for the given domain.
 */
function _get_codeset($domain=null) {
    global $text_domains, $default_domain, $LC_CATEGORIES;
    if (!isset($domain)) $domain = $default_domain;
    return (isset($text_domains[$domain]->codeset))? $text_domains[$domain]->codeset : ini_get('mbstring.internal_encoding');
}

/**
 * Convert the given string to the encoding set by bind_textdomain_codeset.
 */
function _encode($text) {
    $source_encoding = mb_detect_encoding($text);
    $target_encoding = _get_codeset();
    if ($source_encoding != $target_encoding) {
        return mb_convert_encoding($text, $target_encoding, $source_encoding);
    }
    else {
        return $text;
    }
}




// Custom implementation of the standard gettext related functions

/**
 * Sets a requested locale, if needed emulates it.
 */
function _setlocale($category, $locale) {
    global $CURRENTLOCALE, $EMULATEGETTEXT;
    if ($locale === 0) { // use === to differentiate between string "0"
        if ($CURRENTLOCALE != '')
            return $CURRENTLOCALE;
        else
            // obey LANG variable, maybe extend to support all of LC_* vars
            // even if we tried to read locale without setting it first
            return _setlocale($category, $CURRENTLOCALE);
    } else {
        $ret = 0;
        if (function_exists('setlocale')) // I don't know if this ever happens ;)
           $ret = setlocale($category, $locale);
        if (($ret and $locale == '') or ($ret == $locale)) {
            $EMULATEGETTEXT = 0;
            $CURRENTLOCALE = $ret;
        } else {
          if ($locale == '') // emulate variable support
             $CURRENTLOCALE = getenv('LANG');
        else
            $CURRENTLOCALE = $locale;
            $EMULATEGETTEXT = 1;
        }
        // Allow locale to be changed on the go for one translation domain.
        global $text_domains, $default_domain;
        unset($text_domains[$default_domain]->l10n);
        return $CURRENTLOCALE;
    }
}

/**
 * Sets the path for a domain.
 */
function _bindtextdomain($domain, $path) {
    global $text_domains;
    // ensure $path ends with a slash ('/' should work for both, but lets still play nice)
    if (substr(php_uname(), 0, 7) == "Windows") {
      if ($path[strlen($path)-1] != '\\' and $path[strlen($path)-1] != '/')
        $path .= '\\';
    } else {
      if ($path[strlen($path)-1] != '/')
        $path .= '/';
    }
    if (!array_key_exists($domain, $text_domains)) {
      // Initialize an empty domain object.
      $text_domains[$domain] = new domain();
    }
    $text_domains[$domain]->path = $path;
}

/**
 * Specify the character encoding in which the messages from the DOMAIN message catalog will be returned.
 */
function _bind_textdomain_codeset($domain, $codeset) {
    global $text_domains;
    $text_domains[$domain]->codeset = $codeset;
}

/**
 * Sets the default domain.
 */
function _textdomain($domain) {
    global $default_domain;
    $default_domain = $domain;
}

/**
 * Lookup a message in the current domain.
 */
function _gettext($msgid) {
    $l10n = _get_reader();
    //return $l10n->translate($msgid);
    return _encode($l10n->translate($msgid));
}
/**
 * Alias for gettext.
 */
function __($msgid) {
    return _gettext($msgid);
}
/**
 * Plural version of gettext.
 */
function _ngettext($single, $plural, $number) {
    $l10n = _get_reader();
    //return $l10n->ngettext($single, $plural, $number);
    return _encode($l10n->ngettext($single, $plural, $number));
}

/**
 * Override the current domain.
 */
function _dgettext($domain, $msgid) {
    $l10n = _get_reader($domain);
    //return $l10n->translate($msgid);
    return _encode($l10n->translate($msgid));
}
/**
 * Plural version of dgettext.
 */
function _dngettext($domain, $single, $plural, $number) {
    $l10n = _get_reader($domain);
    //return $l10n->ngettext($single, $plural, $number);
    return _encode($l10n->ngettext($single, $plural, $number));
}

/**
 * Overrides the domain and category for a single lookup.
 */
function _dcgettext($domain, $msgid, $category) {
    $l10n = _get_reader($domain, $category);
    //return $l10n->translate($msgid);
    return _encode($l10n->translate($msgid));
}
/**
 * Plural version of dcgettext.
 */
function _dcngettext($domain, $single, $plural, $number, $category) {
    $l10n = _get_reader($domain, $category);
    //return $l10n->ngettext($single, $plural, $number);
    return _encode($l10n->ngettext($single, $plural, $number));
}



// Wrappers to use if the standard gettext functions are available, but the current locale is not supported by the system.
// Use the standard impl if the current locale is supported, use the custom impl otherwise.

function T_setlocale($category, $locale) {
    return _setlocale($category, $locale);
}

function T_bindtextdomain($domain, $path) {
    if (_check_locale()) return bindtextdomain($domain, $path);
    else return _bindtextdomain($domain, $path);
}
function T_bind_textdomain_codeset($domain, $codeset) {
    // bind_textdomain_codeset is available only in PHP 4.2.0+
    if (_check_locale() and function_exists('bind_textdomain_codeset')) return bind_textdomain_codeset($domain, $codeset);
    else return _bind_textdomain_codeset($domain, $codeset);
}
function T_textdomain($domain) {
    if (_check_locale()) return textdomain($domain);
    else return _textdomain($domain);
}
function T_gettext($msgid) {
    if (_check_locale()) return gettext($msgid);
    else return _gettext($msgid);
}
function T_($msgid) {
    if (_check_locale()) return _($msgid);
    return __($msgid);
}
function T_ngettext($single, $plural, $number) {
    if (_check_locale()) return ngettext($single, $plural, $number);
    else return _ngettext($single, $plural, $number);
}
function T_dgettext($domain, $msgid) {
    if (_check_locale()) return dgettext($domain, $msgid);
    else return _dgettext($domain, $msgid);
}
function T_dngettext($domain, $single, $plural, $number) {
    if (_check_locale()) return dngettext($domain, $single, $plural, $number);
    else return _dngettext($domain, $single, $plural, $number);
}
function T_dcgettext($domain, $msgid, $category) {
    if (_check_locale()) return dcgettext($domain, $msgid, $category);
    else return _dcgettext($domain, $msgid, $category);
}
function T_dcngettext($domain, $single, $plural, $number, $category) {
    if (_check_locale()) return dcngettext($domain, $single, $plural, $number, $category);
    else return _dcngettext($domain, $single, $plural, $number, $category);
}



// Wrappers used as a drop in replacement for the standard gettext functions

if (!function_exists('gettext')) {
    function bindtextdomain($domain, $path) {
        return _bindtextdomain($domain, $path);
    }
    function bind_textdomain_codeset($domain, $codeset) {
        return _bind_textdomain_codeset($domain, $codeset);
    }
    function textdomain($domain) {
        return _textdomain($domain);
    }
    function gettext($msgid) {
        return _gettext($msgid);
    }
    function _($msgid) {
        return __($msgid);
    }
    function ngettext($single, $plural, $number) {
        return _ngettext($single, $plural, $number);
    }
    function dgettext($domain, $msgid) {
        return _dgettext($domain, $msgid);
    }
    function dngettext($domain, $single, $plural, $number) {
        return _dngettext($domain, $single, $plural, $number);
    }
    function dcgettext($domain, $msgid, $category) {
        return _dcgettext($domain, $msgid, $category);
    }
    function dcngettext($domain, $single, $plural, $number, $category) {
        return _dcngettext($domain, $single, $plural, $number, $category);
    }
}

?>

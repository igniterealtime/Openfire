<?php

function bb_autop($pee, $br = 1) { // Reduced to be faster
	$pee = $pee . "\n"; // just to make things a little easier, pad the end
	$pee = preg_replace('|<br />\s*<br />|', "\n\n", $pee);
	// Space things out a little
	$pee = preg_replace('!(<(?:ul|ol|li|blockquote|pre|p)[^>]*>)!', "\n$1", $pee); 
	$pee = preg_replace('!(</(?:ul|ol|li|blockquote|pre|p)>)!', "$1\n", $pee);
	$pee = str_replace(array("\r\n", "\r"), "\n", $pee); // cross-platform newlines 
	$pee = preg_replace("/\n\n+/", "\n\n", $pee); // take care of duplicates
	$pee = preg_replace('/\n?(.+?)(?:\n\s*\n|\z)/s', "<p>$1</p>\n", $pee); // make paragraphs, including one at the end 
	$pee = preg_replace('|<p>\s*?</p>|', '', $pee); // under certain strange conditions it could create a P of entirely whitespace 
	$pee = preg_replace('!<p>\s*(</?(?:ul|ol|li|blockquote|p)[^>]*>)\s*</p>!', "$1", $pee); // don't pee all over a tag
	$pee = preg_replace("|<p>(<li.+?)</p>|", "$1", $pee); // problem with nested lists
	$pee = preg_replace('|<p><blockquote([^>]*)>|i', "<blockquote$1><p>", $pee);
	$pee = str_replace('</blockquote></p>', '</p></blockquote>', $pee);
	$pee = preg_replace('!<p>\s*(</?(?:ul|ol|li|blockquote|p)[^>]*>)!', "$1", $pee);
	$pee = preg_replace('!(</?(?:ul|ol|li|blockquote|p)[^>]*>)\s*</p>!', "$1", $pee); 
	if ($br) $pee = preg_replace('|(?<!<br />)\s*\n|', "<br />\n", $pee); // optionally make line breaks
	$pee = preg_replace('!(</?(?:ul|ol|li|blockquote|p)[^>]*>)\s*<br />!', "$1", $pee);
	$pee = preg_replace('!<br />(\s*</?(?:p|li|ul|ol)>)!', '$1', $pee);
	if ( false !== strpos( $pee, '<pre' ) )
		$pee = preg_replace_callback('!(<pre.*?>)(.*?)</pre>!is', 'clean_pre', $pee );
	return $pee; 
}

function bb_encodeit( $matches ) {
	$text = trim($matches[2]);
	$text = htmlspecialchars($text, ENT_QUOTES);
	$text = str_replace(array("\r\n", "\r"), "\n", $text);
	$text = preg_replace("|\n\n\n+|", "\n\n", $text);
	$text = str_replace('&amp;amp;', '&amp;', $text);
	$text = str_replace('&amp;lt;', '&lt;', $text);
	$text = str_replace('&amp;gt;', '&gt;', $text);
	$text = "<code>$text</code>";
	if ( "`" != $matches[1] )
		$text = "<pre>$text</pre>";
	return $text;
}

function bb_decodeit( $matches ) {
	$text = $matches[2];
	$trans_table = array_flip(get_html_translation_table(HTML_ENTITIES));
	$text = strtr($text, $trans_table);
	$text = str_replace('<br />', '<coded_br />', $text);
	$text = str_replace('<p>', '<coded_p>', $text);
	$text = str_replace('</p>', '</coded_p>', $text);
	$text = str_replace(array('&#38;','&amp;'), '&', $text);
	$text = str_replace('&#39;', "'", $text);
	if ( '<pre><code>' == $matches[1] )
		$text = "\n$text\n";
	return "`$text`";
}

function bb_code_trick( $text ) {
	$text = str_replace(array("\r\n", "\r"), "\n", $text);
	$text = preg_replace_callback("|(`)(.*?)`|", 'bb_encodeit', $text);
	$text = preg_replace_callback("!(^|\n)`(.*?)`!s", 'bb_encodeit', $text);
	return $text;
}

function bb_code_trick_reverse( $text ) {
	$text = preg_replace_callback("!(<pre><code>|<code>)(.*?)(</code></pre>|</code>)!s", 'bb_decodeit', $text);
	$text = str_replace(array('<p>', '<br />'), '', $text);
	$text = str_replace('</p>', "\n", $text);
	$text = str_replace('<coded_br />', '<br />', $text);
	$text = str_replace('<coded_p>', '<p>', $text);
	$text = str_replace('</coded_p>', '</p>', $text);
	return $text;
}

function _bb_encode_bad_empty(&$text, $key, $preg) {
	if (strpos($text, '`') !== 0)
		$text = preg_replace("|&lt;($preg)\s*?/*?&gt;|i", '<$1 />', $text);
}

function _bb_encode_bad_normal(&$text, $key, $preg) {
	if (strpos($text, '`') !== 0)
		$text = preg_replace("|&lt;(/?$preg)&gt;|i", '<$1>', $text);
}

function bb_encode_bad( $text ) {
	$text = wp_specialchars( $text, ENT_NOQUOTES );

	$text = preg_split('@(`[^`]*`)@m', $text, -1, PREG_SPLIT_NO_EMPTY + PREG_SPLIT_DELIM_CAPTURE);

	$allowed = bb_allowed_tags();
	$empty = array( 'br' => true, 'hr' => true, 'img' => true, 'input' => true, 'param' => true, 'area' => true, 'col' => true, 'embed' => true );

	foreach ( $allowed as $tag => $args ) {
		$preg = $args ? "$tag(?:\s.*?)?" : $tag;

		if ( isset( $empty[$tag] ) )
			array_walk($text, '_bb_encode_bad_empty', $preg);
		else
			array_walk($text, '_bb_encode_bad_normal', $preg);
	}

	return join('', $text);
}

function bb_filter_kses($data) {
	$allowedtags = bb_allowed_tags();
	return wp_kses($data, $allowedtags);
}

function bb_allowed_tags() {
	$tags = array(
		'a' => array(
			'href' => array(),
			'title' => array(),
			'rel' => array()),
		'blockquote' => array('cite' => array()),
		'br' => array(),
		'code' => array(),
		'pre' => array(),
		'em' => array(),
		'strong' => array(),
		'ul' => array(),
		'ol' => array(),
		'li' => array()
	);
	return apply_filters( 'bb_allowed_tags', $tags );
}

function bb_rel_nofollow( $text ) {
	return preg_replace_callback('|<a (.+?)>|i', 'bb_rel_nofollow_callback', $text);
}

function bb_rel_nofollow_callback( $matches ) {
	$text = $matches[1];
	$text = str_replace(array(' rel="nofollow"', " rel='nofollow'"), '', $text);
	return "<a $text rel=\"nofollow\">";
}

// Should be able to take both escaped and unescaped data
function bb_trim_for_db( $string, $length ) {
	$_string = $string;
	if ( seems_utf8( $string ) ) {
		$string = bb_utf8_cut( $string, $length );
		// if we have slashes at the end, make sure we have a reasonable number of them
		if ( preg_match( '#[^\\\\](\\\\+)$#', $string, $matches ) ) {
			$end = stripslashes($matches[1]);
			$end = addslashes($end);
			$string = trim( $string, '\\' ) . $end;
		}
	}
	return apply_filters( 'bb_trim_for_db', $string, $_string, $length );
}

// Reduce utf8 string to $length in single byte character equivalents without breaking multibyte characters
function bb_utf8_cut( $utf8_string, $length = 0 ) {
	if ( $length < 1 )
		return $utf8_string;

	$unicode = '';
	$chars = array();
	$num_octets = 1;

	for ($i = 0; $i < strlen( $utf8_string ); $i++ ) {

		$value = ord( $utf8_string[ $i ] );

		if ( 128 > $value ) {
			if ( strlen($unicode) + 1 > $length )
				break; 
			$unicode .= $utf8_string[ $i ];
		} else {
			if ( count( $chars ) == 0 )
				$num_octets = ( 224 > $value ) ? 2 : 3;

			$chars[] = $utf8_string[ $i ];
			if ( strlen($unicode) + $num_octets > $length )
				break;
			if ( count( $chars ) == $num_octets ) {
				$unicode .= join('', $chars);
				$chars = array();
				$num_octets = 1;
			}
		}
	}

	return $unicode;
}

function bb_encoded_utf8_cut( $encoded, $length = 0 ) {
	if ( $length < 1 )
		return $encoded;

	$r = '';
	$values = preg_split( '/(%[0-9a-f]{2})/i', $encoded, -1, PREG_SPLIT_DELIM_CAPTURE | PREG_SPLIT_NO_EMPTY );;

	for ($i = 0; $i < count( $values ); $i += $num_octets ) {
		$num_octets = 1;
		if ( '%' != $values[$i][0] ) {
			$r .= $values[$i];
			if ( $length && strlen($r) > $length )
				return substr($r, 0, $length);
		} else {
			$value = hexdec(substr($values[$i], 1));

			if ( 1 == $num_octets )
				$num_octets = ( 224 > $value ) ? 2 : 3;

			if ( $length && ( strlen($r) + $num_octets * 3 ) > $length )
				return $r;

			$r .= $values[$i] . $values[$i + 1];
			if ( 3 == $num_octets )
				$r .= $values[$i + 2];
		}
	}

	return $r;
}

function bb_pre_term_slug( $slug, $taxonomy = '', $term_id = 0 ) {
	return bb_sanitize_with_dashes( $slug, 200 );
}

function bb_trim_for_db_55( $string ) {
	return bb_trim_for_db( $string, 55 );
}

function bb_trim_for_db_150( $string ) {
	return bb_trim_for_db( $string, 150 );
}

function bb_slug_sanitize( $slug, $length = 255 ) {
	$_slug = $slug;
	return apply_filters( 'bb_slug_sanitize', bb_sanitize_with_dashes( $slug, $length ), $_slug, $length );
}

function bb_user_nicename_sanitize( $user_nicename, $length = 50 ) {
	$_user_nicename = $user_nicename;
	return apply_filters( 'bb_user_nicename_sanitize', bb_sanitize_with_dashes( $user_nicename, $length ), $_user_nicename, $length );
}

function bb_sanitize_with_dashes( $text, $length = 0 ) { // Multibyte aware
	$_text = $text;
	$text = trim($text);
	$text = strip_tags($text);
	// Preserve escaped octets.
	$text = preg_replace('|%([a-fA-F0-9][a-fA-F0-9])|', '---$1---', $text);
	// Remove percent signs that are not part of an octet.
	$text = str_replace('%', '', $text);
	// Restore octets.
	$text = preg_replace('|---([a-fA-F0-9][a-fA-F0-9])---|', '%$1', $text);

	$text = apply_filters( 'pre_sanitize_with_dashes', $text, $_text, $length );

	$text = strtolower($text);
	$text = preg_replace('/&(^\x80-\xff)+?;/', '', $text); // kill entities
	$text = preg_replace('/[^%a-z0-9\x80-\xff _-]/', '', $text);
	$text = trim($text);
	$text = preg_replace('/\s+/', '-', $text);
	$text = preg_replace(array('|-+|', '|_+|'), array('-', '_'), $text); // Kill the repeats
	
	return $text;
}

function bb_pre_sanitize_with_dashes_utf8( $text, $_text = '', $length = 0 ) {
	$text = remove_accents($text);

	if ( seems_utf8( $text ) ) {
		if ( function_exists('mb_strtolower') )
			$text = mb_strtolower($text, 'UTF-8');
		$text = utf8_uri_encode( $text, $length );
	}

	return $text;
}

function bb_show_topic_context( $term, $text ) {
	$text = strip_tags( $text );
	$term = preg_quote( $term );
	$text = preg_replace( "|.*?(.{0,80})$term(.{0,80}).*|is", "$1<strong>$term</strong>$2", $text, 1 );
	$text = substr( $text, 0, 210 );
	return $text;
}

function bb_post_text_context( $post_text ) {
	return bb_show_context( $GLOBALS['q'], $post_text );
}

function bb_show_context( $term, $text ) {
	$text = strip_shortcodes( $text );
	$text = strip_tags( $text );
	$term = preg_quote( $term );
	$text = preg_replace( "|.*?(.{0,80})$term(.{0,80}).*|is", "... $1<strong>$term</strong>$2 ...", $text, 1 );
	$text = substr( $text, 0, 210 );
	return $text;
}

function bb_fix_link( $link ) {
	if ( false === strpos($link, '.') ) // these are usually random words
		return '';
	$link = wp_kses_no_null( $link );
	return esc_url( $link );
}

function bb_sticky_label( $label ) {
	global $topic;
	if (bb_is_front()) {
		if ( '2' === $topic->topic_sticky ) {
			return sprintf(__('[sticky] %s'), $label);
		}
	} else {
		if ( '1' === $topic->topic_sticky || '2' === $topic->topic_sticky ) {
			return sprintf(__('[sticky] %s'), $label);
		}
	}
	return $label;
}

function bb_closed_label( $label ) {
	global $topic;
	if ( '0' === $topic->topic_open )
		return sprintf(__('[closed] %s'), $label);
	return $label;
}

function bb_make_link_view_all( $link ) {
	return esc_html( add_query_arg( 'view', 'all', $link ) );
}

function bb_gmtstrtotime( $string ) {
	if ( is_numeric($string) )
		return $string;
	if ( !is_string($string) )
		return -1;

	if ( stristr($string, 'utc') || stristr($string, 'gmt') || stristr($string, '+0000') )
		return strtotime($string);

	if ( -1 == $time = strtotime($string . ' +0000') )
		return strtotime($string);

	return $time;
}

/**
 * Converts a number of characters from a string.
 *
 * Metadata tags <<title>> and <<category>> are removed, <<br>> and <<hr>> are
 * converted into correct XHTML and Unicode characters are converted to the
 * valid range.
 *
 * @param string $content String of characters to be converted.
 * 
 * @return string Converted string.
 */
function bb_convert_chars( $content ) {
	// Translation of invalid Unicode references range to valid range
	$wp_htmltranswinuni = array(
	'&#128;' => '&#8364;', // the Euro sign
	'&#129;' => '',
	'&#130;' => '&#8218;', // these are Windows CP1252 specific characters
	'&#131;' => '&#402;',  // they would look weird on non-Windows browsers
	'&#132;' => '&#8222;',
	'&#133;' => '&#8230;',
	'&#134;' => '&#8224;',
	'&#135;' => '&#8225;',
	'&#136;' => '&#710;',
	'&#137;' => '&#8240;',
	'&#138;' => '&#352;',
	'&#139;' => '&#8249;',
	'&#140;' => '&#338;',
	'&#141;' => '',
	'&#142;' => '&#382;',
	'&#143;' => '',
	'&#144;' => '',
	'&#145;' => '&#8216;',
	'&#146;' => '&#8217;',
	'&#147;' => '&#8220;',
	'&#148;' => '&#8221;',
	'&#149;' => '&#8226;',
	'&#150;' => '&#8211;',
	'&#151;' => '&#8212;',
	'&#152;' => '&#732;',
	'&#153;' => '&#8482;',
	'&#154;' => '&#353;',
	'&#155;' => '&#8250;',
	'&#156;' => '&#339;',
	'&#157;' => '',
	'&#158;' => '',
	'&#159;' => '&#376;'
	);

	// Remove metadata tags
	$content = preg_replace( '/<title>(.+?)<\/title>/', '', $content );
	$content = preg_replace( '/<category>(.+?)<\/category>/', '', $content );

	// Converts lone & characters into &#38; (a.k.a. &amp;)
	$content = preg_replace( '/&([^#])(?![a-z1-4]{1,8};)/i', '&#038;$1', $content );

	// Fix Word pasting
	$content = strtr( $content, $wp_htmltranswinuni );

	// Just a little XHTML help
	$content = str_replace( '<br>', '<br />', $content );
	$content = str_replace( '<hr>', '<hr />', $content );

	return $content;
}
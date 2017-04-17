<?php
/**
 * @package Hello_Louie
 * @author Matt Mullenweg
 * @version 1.0
 */
/*
Plugin Name: Hello Louie
Plugin URI: http://bbpress.org/
Description: A tip of the hat to the venerable Hello Dolly plugin from WordPress. When activated you will randomly see a lyric from Frank Sinatra's 1964 rendition of "Hello Dolly" in the upper right of your admin screen on every page. This version includes a rewritten second verse as a tribute to Louis Armstrong.
Author: Matt Mullenweg
Version: 1.0
Author URI: http://ma.tt/
*/

function hello_louie_get_lyric() {
	/** These are the lyrics to Frank Sinatra's version of "Hello Dolly" from the album "It Might as Well Be Swing" */
	$lyrics = "Hello Dolly
Well hello Dolly
It's so nice to have you back where you belong
You're lookin' swell, Dolly
We can tell, Dolly
You're still glowin', you're still crowin'
You're still goin' strong
We feel the room swayin'
'Cause the band's playin'
One of your old favorite songs from way back when
Take her wrap, fellas
Find her an empty lap, fellas
Dolly'll never go away
Dolly'll never go away
Hello Satch
This is Francis, Louie
It's so nice to see you back where you belong
You're back on top, Louie
Never stop, Louie
You're still singin', you're still swingin'
You're still goin' strong
You get the room swayin'
When you start in playin'
One of your great songs, or songs from way back when
Blow your horn, Louie
Sing up a great big song, Louie
Promise you won't go away
Promise you won't go away
Promise you won't go away again, ooh yeah";

	// Here we split it into lines
	$lyrics = explode("\n", $lyrics);

	// And then randomly choose a line
	return wptexturize( $lyrics[ mt_rand(0, count($lyrics) - 1) ] );
}

// This just echoes the chosen line, we'll position it later
function hello_louie() {
	$chosen = hello_louie_get_lyric();
	echo "<p id='dolly'>$chosen</p>";
}

// Now we set that function up to execute when the admin_footer action is called
add_action('bb_admin_footer', 'hello_louie');

// We need some CSS to position the paragraph
function louie_css() {
	// This makes sure that the positioning is also good for right-to-left languages
	global $bb_locale;
	$x = ( isset( $bb_locale->text_direction ) && 'rtl' == $bb_locale->text_direction ) ? 'left' : 'right';

	echo "
	<style type='text/css'>
	#dolly {
		position: absolute;
		top: 4.4em;
		margin: 0;
		padding: 0;
		$x: 15px;
		font-size: 11px;
	}
	</style>
	";
}

add_action('bb_admin_head', 'louie_css');

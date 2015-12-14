/* jshint unused: false */

function bp_get_querystring( n ) {
	var half = location.search.split( n + '=' )[1];
	return half ? decodeURIComponent( half.split('&')[0] ) : null;
}

/**
 * Javascript debug helper
 *
 * @author Fabian Wolf
 */
 
function add_alert( strMessage ) {
	document.getElementsByTagName('body')[0].insertAdjacentHTML('afterbegin', strMessage );
	
}
 
head.ready(function() {
	alert('HeadJS fires!');
	add_alert( '<h1>HeadJS fires!</h1>' );
});

jQuery(function() {
	alert('jQuery fires');
	add_alert( '<h1>jQuery fires!</h1>' );
})
 

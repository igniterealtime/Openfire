/**
* status scripts
*/

jQuery(document).ready( function() {
// bring input focus to input.focus for old browsers if html5 autofocus not supported
  if (!("autofocus" in document.createElement("input"))) {
    jQuery('.focus').focus();
  }
});
/**
* Show/Hide script by vebailovity
*/
(function ($) {

/**
 * Utility supplemental Esc key handler, 
 * defined as function for easier unbinding.
 */
function supplemental_listener (e) {
	if (e.keyCode != 27) return true;

	$(".activity-comments").hide();
	$(document).unbind('keyup', supplemental_listener); // Remove the listener.

	return true;
}

/**
 * Initialize.
 *
 * Important:
 *
 * This should be executed *after* the default handlers
 * (the ones for clicks, Esc key and the like).
 */
function initialize_comments_handling () {
	// Hide comments by default
	$(".activity-comments").hide();

	// Handle comments click
	$(".acomment-reply.button").on('click', function () {
		var $me = $(this);
		// Get activity parent
		var $parent = $me.parents("li"); // This will only apply to activity updates - remove class, or add other specific class for group updates (.groups.created_group) etc.
		if (!$parent.length) return false; // Can't find parent - pretend nothing happened

		// Get comments for this item
		var $comments = $parent.find(".activity-comments");
		if (!$comments.length) return true; // No comments? Bail out and let the default handler do its thing

		// Comments toggling logic
		if ($comments.is(":visible")) { // If comments are visible, we have 2 possible scenarios:
			// 1) Regular click on comments link - hide the comments
			if ($comments.find("textarea").is(":visible")) $comments.hide();
			// 2) Click after post submission (textarea is hidden). Let the default handler kick in.
			else return true;
		} else { // Comments are hidden. Do our stuff.
			// Make sure all the comments are hidden
			$(".activity-comments").hide();
			$comments.show(); // Show comments
			$(document).bind('keyup', supplemental_listener); // Bind supplemental handler
		}

		// Allow default handler to pick up where we left off.
		return true;
	});
}
// Bind initialization on document.ready.
// As a possible alternative, it could be bound to a custom signal:
// E.g. instead of this line: $(document).bind('custom-signal', initialize_comments_handling);
// And then in the default handlers file: $(document).trigger('custom-signal');
$(initialize_comments_handling);

})(jQuery);
<script type="text/javascript">
(function ($) {
/**
 * Function by Phil Haack
 * Taken verbatim from http://haacked.com/archive/2009/12/29/convert-rgb-to-hex.aspx
 */
function colorToHex(color) {
    if (color.substr(0, 1) === '#') {
        return color;
    }
    var digits = /(.*?)rgb\((\d+), (\d+), (\d+)\)/.exec(color);

    var red = parseInt(digits[2]);
    var green = parseInt(digits[3]);
    var blue = parseInt(digits[4]);

    var rgb = blue | (green << 8) | (red << 16);
    return digits[1] + '#' + rgb.toString(16);
};

// Init
$(function () {

	/* ----- Color picker ----- */

	// Init values
	if (!$("#status-design-link_color").val()) $("#status-design-link_color").val(colorToHex($("a").css("color")));
	if (!$("#status-design-background_color").val()) $("#status-design-background_color").val(colorToHex($("body").css("background-color")));

	$.each(['link', 'background'], function (idx, cls) {
		$("#status-design-" + cls + "_color")
			.after('<div id="status-design-' + cls + '_color-colorpicker" style="display:none" />')
			.focus(function () {
				$("#status-design-" + cls + "_color-colorpicker").show();
			})
			.blur(function () {
				$("#status-design-" + cls + "_color-colorpicker").hide();
			})
		;
		$("#status-design-" + cls + "_color-colorpicker").farbtastic("#status-design-" + cls + "_color");
	});

	/* ----- Background image ----- */
	$("#status-design-remove_background").on('click', function () {
		$("#status-design-background_image-wrapper")
			.find("img").remove().end()
			.find("input").remove().end()
			.append('<input type="hidden" name="_status_design-remove_background" value="1" />')
		;
		return false;
	});
});
})(jQuery);
</script>
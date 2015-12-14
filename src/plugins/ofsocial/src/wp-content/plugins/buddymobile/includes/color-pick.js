/*!
* color picker code originated by
* @author: Rachel Baker ( rachel@rachelbaker.me )
*/

(function($) {

    function pickBackgroundColor(color) {
        $("#background-color").val(color);
    }
    function toggle_text() {
        link_color = $("#background-color");
        if ("" === link_color.val().replace("#", "")) {
            link_color.val(default_color);
            pickBackgroundColor(default_color);
        } else pickBackgroundColor(link_color.val());
    }
    var default_color = "fbfbfb";
    $(document).ready(function() {
        var link_color = $("#background-color");
        link_color.wpColorPicker({
            change: function(event, ui) {
                pickBackgroundColor(link_color.wpColorPicker("color"));
            },
            clear: function() {
                pickBackgroundColor("");
            }
        });
        $("#background-color").click(toggle_text);
        toggle_text();
    });


    function pickToolbarColor(color) {
        $("#toolbar-color").val(color);
    }
    function toggle_text() {
        link_color = $("#toolbar-color");
        if ("" === link_color.val().replace("#", "")) {
            link_color.val(default_color);
            pickToolbarColor(default_color);
        } else pickToolbarColor(link_color.val());
    }
    var default_color = "fbfbfb";
    $(document).ready(function() {
        var link_color = $("#toolbar-color");
        link_color.wpColorPicker({
            change: function(event, ui) {
                pickToolbarColor(link_color.wpColorPicker("color"));
            },
            clear: function() {
                pickToolbarColor("");
            }
        });
        $("#toolbar-color").click(toggle_text);
        toggle_text();
    });

	$(document).ready(function($){
		  var _custom_media = true,
		      _orig_send_attachment = wp.media.editor.send.attachment;
		  $('.settings_page_buddymobile-includes-bp-mobile-admin .button').click(function(e) {
		    var send_attachment_bkp = wp.media.editor.send.attachment;
		    var button = $(this);
		    var id = button.attr('id').replace('_button', '');
		    _custom_media = true;
		    wp.media.editor.send.attachment = function(props, attachment){
		      if ( _custom_media ) {
		        $("#touch-icon").val(attachment.url);
		      } else {
		        return _orig_send_attachment.apply( this, [props, attachment] );
		      };
		    }
		    wp.media.editor.open(button);
		    return false;
		  });
		  $('.add_media').on('click', function(){
		    _custom_media = false;
		  });
	});


})(jQuery);
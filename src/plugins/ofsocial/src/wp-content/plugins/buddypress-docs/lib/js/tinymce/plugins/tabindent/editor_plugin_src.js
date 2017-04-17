/**
 * editor_plugin.js
 *
 * @package tabindent
 * @since 1.1
 *
 * Pieced together from several sources, notably:
 * http://tinymce.moxiecode.com/forum/viewtopic.php?pid=77144#p77144
 *
 * Copyright 2011 Boone B Gorges
 * License: GPLv3
 */

(function() {
	tinymce.create('tinymce.plugins.TabIndent', {
		init : function(ed, url) {
			var t = this;

			t.editor = ed;

			// Catch the Tab button event
			ed.onKeyPress.add(function(inst,e) {
				// In later versions of FF, you need to populate the event manually
				if ( !e )
					var e = window.event;

				if ( e.keyCode )
					code = e.keyCode; // Non-FF
				else if ( e.which )
					code = e.which; // FF

				if ( code == 9 && !e.altKey && !e.ctrlKey ) {
					t.doTabIndent();

					// prevent tab key from leaving editor in some browsers
					if ( e.preventDefault ) {
						e.preventDefault();
					}

					return false;
				}

			});

			// Attach the indent action to the editor button
			ed.addCommand('mcePreview', function() {
				t.doTabIndent();
			});

			// Register the editor button
			ed.addButton('tabindent', {
				title : 'tabindent.button_hover',
				cmd : 'mcePreview',
				image : url + '/img/tab_button.gif'
			} );
		},

		doTabIndent : function() {
			tinyMCE.activeEditor.selection.setContent("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
		},

		getInfo : function() {
			return {
				longname : 'Tab Indent',
				author : 'Boone B Gorges',
				authorurl : 'http://boonebgorges.com',
				infourl : 'http://github.com/boonebgorges/tabindent',
				version : '1.0'
			};
		}
	});

	// Register plugin
	tinymce.PluginManager.add('tabindent', tinymce.plugins.TabIndent);
})();
jQuery(document).ready(function($){
	// Refresh access settings on pageload, unless this is a failed
	// submission (in which case trust whatever was submitted in POST)
	if ( '1' != bp_docs.failed_submission ) {
		bpdv_refresh_access_settings();
	}

	// Binders
	$('#associated_group_id').on('change',function(){ bpdv_refresh_access_settings(); });
	$('#associated_group_id').on('change',function(){ bpdv_refresh_associated_group(); });

	doc_id = $('#doc_id').val();

	// Cascade permissions - new docs only
	$('#toggle-table-settings tbody').on('change', 'select[name="settings[read]"]', function(e) {
		// Only cascade if the other permissions haven't been changed yet
		if ( ! window.bpdv_permissions_changed && 0 == doc_id ) {
			jQuery(e.target).closest('tbody').find('select[name*="settings"]').val(jQuery(e.target).val());
		}
	});

	// When a non-read permission is manually set, don't allow further mods
	$('#toggle-table-settings tbody').on('change', 'select[name*="settings"]', function(e) {
		if ( $(e.target).attr('name') != 'settings[read]' && 0 == doc_id ) {
			window.bpdv_permissions_changed = true;
		}
	});
},(jQuery));

function bpdv_refresh_access_settings() {
	var assoc_group = jQuery('#associated_group_id').val();
	var doc_id = jQuery('#doc_id').val();
	jQuery.ajax({
		type: 'POST',
		url: ajaxurl,
		data: {
			'action': 'refresh_access_settings',
			'group_id': assoc_group,
			'doc_id': doc_id
		},
		success: function(r) {
			jQuery('#toggle-table-settings tbody').html(r);
		}
	});
}

function bpdv_refresh_associated_group() {
	var assoc_group = jQuery('#associated_group_id').val();
	var doc_id = jQuery('#doc_id').val();
	jQuery.ajax({
		type: 'POST',
		url: ajaxurl,
		data: {
			'action': 'refresh_associated_group',
			'group_id': assoc_group,
			'doc_id': doc_id
		},
		success: function(r) {
			var ags = jQuery('#associated_group_summary');
			jQuery(ags).slideUp('fast', function(){
				jQuery(ags).html(r);
				jQuery(ags).slideDown('fast');
			});
		}
	});
}

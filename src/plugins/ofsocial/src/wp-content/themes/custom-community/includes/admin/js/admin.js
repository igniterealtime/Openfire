/**
 * Dummy console
 * @see https://github.com/andyet/ConsoleDummy.js
 */

/**
 * admin.js helpers
 */
 

function cc2_update_message() {
	//console.log( 'update message fires');

	jQuery('#cc-slider-editor-buttons').addClass('okay')
	
	setTimeout(function(){
		jQuery('#cc-slider-editor-buttons').removeClass('okay');
	},1200);
	
	jQuery('.custom_media_upload').fadeOut().delay('1200').fadeIn('400');
	jQuery('.delete-slideshow').fadeOut().delay('1200').fadeIn('400');
	jQuery('#loading-animation-reorder').delay('400').fadeIn().delay('400').fadeOut('300');

}

function cc2_is_duplicate_slideshow( check_name ) {
	console.log( 'cc2_is_duplicate_slideshow fires');
	
	var slideshowOptions = document.getElementById('select_slides_list').options,
	$return = false;
	
	for( var optCount = 0; optCount < slideshowOptions.length; optCount++ ) {
		if( slideshowOptions[optCount].getAttribute('value') != -1 && check_name != '' && slideshowOptions[optCount].getAttribute('value') == check_name ) {
			
			//alert('Please use another name, slideshow of this name already exists'); 
			$return = true;
		}
	}
	
	/*do{
		optCount++;
		//console.log( optCount, slideshowOptions[optCount].getAttribute('value'), check_name );
		
		if( slideshowOptions[optCount].getAttribute('value') != -1 && check_name != '' && slideshowOptions[optCount].getAttribute('value') == check_name ) {
			
			//alert('Please use another name, slideshow of this name already exists'); 
			$return = true;
		}
		
		
	} while( optCount < slideshowOptions.length && slideshowOptions[optCount].getAttribute('value') != check_name );
	*/
	/*jQuery('#select_slides_list option').each( function( i, elem ) {
		if( elem.getAttribute('value') != -1 && document.getElementById('add-slideshow-name').value == elem.getAttribute('value') ) {
alert('Please use another name. This name already exists!')
//console.log( i, elem.getAttribute('value') );
}*/

	return $return;

}



/**
 * Function library
 */

 

function exit( status ) {
    // http://kevin.vanzonneveld.net
    // +   original by: Brett Zamir (http://brettz9.blogspot.com)
    // +      input by: Paul
    // +   bugfixed by: Hyam Singer (http://www.impact-computing.com/)
    // +   improved by: Philip Peterson
    // +   bugfixed by: Brett Zamir (http://brettz9.blogspot.com)
    // %        note 1: Should be considered expirimental. Please comment on this function.
    // *     example 1: exit();
    // *     returns 1: null

    var i;

    if (typeof status === 'string') {
        alert(status);
    }

    window.addEventListener('error', function (e) {e.preventDefault();e.stopPropagation();}, false);

    var handlers = [
        'copy', 'cut', 'paste',
        'beforeunload', 'blur', 'change', 'click', 'contextmenu', 'dblclick', 'focus', 'keydown', 'keypress', 'keyup', 'mousedown', 'mousemove', 'mouseout', 'mouseover', 'mouseup', 'resize', 'scroll',
        'DOMNodeInserted', 'DOMNodeRemoved', 'DOMNodeRemovedFromDocument', 'DOMNodeInsertedIntoDocument', 'DOMAttrModified', 'DOMCharacterDataModified', 'DOMElementNameChanged', 'DOMAttributeNameChanged', 'DOMActivate', 'DOMFocusIn', 'DOMFocusOut', 'online', 'offline', 'textInput',
        'abort', 'close', 'dragdrop', 'load', 'paint', 'reset', 'select', 'submit', 'unload'
    ];

    function stopPropagation (e) {
        e.stopPropagation();
        // e.preventDefault(); // Stop for the form controls, etc., too?
    }
    for (i=0; i < handlers.length; i++) {
        window.addEventListener(handlers[i], function (e) {stopPropagation(e);}, true);
    }

    if (window.stop) {
        window.stop();
    }

    throw '';
}

//jQuery(document).ready(function() {
/**
 * @see http://api.jquery.com/ready/
 */

jQuery(function() {
	var strSliderPrefix = 'cc2_slider_',
		strCustomStylingPrefix = 'cc2_advanced_settings_';
		
	//document.getElementsByTagName('body')[0].insertAdjacentHTML('beforeend', '<div id="cc2-modal" class="wp-dialog" style="display: none"></div>');

   
/**
 * Slideshow
 */


    jQuery( document ).on('change', '#cat', function() {

        var select_slides_list =  jQuery('#select_slides_list').val();
        var slideshow_cat =  jQuery('#cat').val();

        jQuery.ajax({
            type: 'POST',
            url: ajaxurl,
            data: {
				'action': strSliderPrefix + 'query', 
				'select_slides_list': select_slides_list,
				'slideshow_cat': slideshow_cat 
			},
            success: function(data){
                //console.info(data);
                //
                jQuery('#select_slides_list').val(select_slides_list);
                jQuery('#select_slides_list').trigger('change');
            },
            error: function() {
				
				
                console.info('Something went wrong.. ;-( sorry)');
            }
        });
    });

    jQuery( document ).on('change', '#slideshow_taxonomy', function() {

        var select_slides_list = jQuery('#select_slides_list').val();
        var slideshow_post_type = jQuery('#slideshow_taxonomy').val();

        jQuery.ajax({
            type: 'POST',
            url: ajaxurl,
            data: {
				'action': strSliderPrefix + 'query', 
				'select_slides_list': 
				select_slides_list,
				'slideshow_taxonomy': slideshow_taxonomy 
			},
            success: function(data){
                jQuery("#select_slides_list").val(select_slides_list);
                jQuery("#select_slides_list").trigger('change');
            },
            error: function() {
                console.info('Something went wrong.. ;-(sorry)');
            }
        });
    });

    jQuery( document ).on('change', '#slideshow_post_type', function() {

        var select_slides_list = jQuery('#select_slides_list').val();
        var slideshow_post_type = jQuery('#slideshow_post_type').val();

        jQuery.ajax({
            type: 'POST',
            url: ajaxurl,
            data: {'action': strSliderPrefix + 'query', 'select_slides_list': select_slides_list,'slideshow_post_type': slideshow_post_type },
            success: function(data){
                jQuery('#select_slides_list').val(select_slides_list);
                jQuery('#select_slides_list').trigger('change');
            },
            error: function() {
                console.info('Something went wrong.. ;-(sorry)');
            }
        });
    });
    
    /**
     * Basically auto-save
     */

    jQuery('#select_slides_list').on('change', function() {
        var slide_list = this.value;
        var action = jQuery(this);

        var select_slides_list = jQuery('#select_slides_list').val();

        if (select_slides_list != '-1'){
            jQuery('#cc-slider-editor-buttons').fadeIn('slow');
        } else {
            jQuery('#cc-slider-editor-buttons').fadeOut('slow');
        }


        jQuery.ajax({
            type: 'POST',
            url: ajaxurl,
            data: {
				'action': strSliderPrefix + 'display_slides_list', 
				'slide_list': slide_list
			},
            success: function(data) {
				//console.log( 'display_slideshow_list', data );
				
				
				jQuery('#display_slides_list').html( data );
				

                jQuery('#sortable').sortable({
                    update: function(event, ui) {
                        var neworder = jQuery(this).sortable('toArray').toString();
                        jQuery.ajax({
                            type: 'POST',
                            url: ajaxurl,
                            data: {
								'action': strSliderPrefix + 'slideshow_neworder', 
								'neworder': neworder, 
								'select_slides_list': select_slides_list 
							},
                            success: function(data) {
                                cc2_update_message();
                            },
                            error: function() {
                                console.info('Something went wrong.. ;-(sorry)');
                            }
                        });
                    }
                });
                jQuery( '#sortable' ).disableSelection();
            },
            error: function() {
                console.info('Something went wrong.. ;-(sorry)');
            }
        });
    });

    jQuery('#select_slides_list').trigger('change');
    jQuery('#cc-slider-editor-buttons').hide();

	/**
	 * Add a slideshow
	 */

    jQuery( document ).on( 'click', '.add-slideshow', function(e) {
		e.preventDefault();
		
		//console.info( 'add new slideshow' );
        var new_slideshow_type =  jQuery('#add-slideshow-type').val();
        var new_slideshow_name =  jQuery('#add-slideshow-name').val();
        var new_slideshow_slug = create_slug( new_slideshow_name );
        
		//console.log( 'slideshow params', new_slideshow_name, new_slideshow_type, 'slug', new_slideshow_slug );

        if(new_slideshow_name == '') {
			// modal dialogue
			//console.info('Enter a Slideshow name');
			//cc2_modal_alert( 'Enter a slideshow name');
			alert( 'Please enter a slideshow name');
			jQuery('#new-slideshow-name').focus();
			exit();
		
		// check if slideshow name is already in use!
		} else if( cc2_is_duplicate_slideshow( new_slideshow_name) ) {
			alert( 'A slideshow with this name already exists. Please use another one.');
			jQuery('#new-slideshow-name').focus();
			exit();
			
		} else { 

			jQuery.ajax({
				type: 'POST',
				url: ajaxurl,
				data: {
					'action': strSliderPrefix + 'add_slideshow', 
					'new_slideshow_name': new_slideshow_name,
					'new_slideshow_type': new_slideshow_type,
				},
				success: function(data) {
					//console.info(data);
					
					jQuery('#select_slides_list').append(
						/**
						 * FIXME: injects the new slideshow name, but title != slug!
						 */
					
						jQuery('<option></option>').val(new_slideshow_name).html(new_slideshow_name)
					);
					jQuery('#select_slides_list').val(new_slideshow_name);
					jQuery('#add-slideshow-name').val('');
					jQuery('#select_slides_list').trigger('change');

				},
				error: function() {
					console.info('Something went wrong.. ;-(sorry)');
				}
			});
		}

    });

	/**
	 * Save the slideshow
	 * Actually obsolete, cause we got auto-save, but nevertheless .. giving you a warm, cozy feeling NEVER is a bad thing ;-)
	 */

	


	/**
	 * Delete the whole slideshow
	 */


    jQuery( document ).on( 'click', '.delete-slideshow', function(e) {
        var select_slides_list =  jQuery('#select_slides_list').val();
        if(select_slides_list == -1) {
            console.info('Select a Slideshow first');
            //die()
            exit();
        }

        var action = jQuery(this);

        if (confirm('Delete Permanently'))
        jQuery.ajax({
            type: 'POST',
            url: ajaxurl,
            data: {
				'action': strSliderPrefix + 'delete_slideshow', 
				'slideshow': select_slides_list 
				},
            success: function(data){
                //console.info(data);
                window.location.reload(true);
                
                //jQuery('#display_slides_list').html( '' );
                
                 //jQuery('#select_slides_list').trigger('change');
				////jQuery('#cc-slider-editor-buttons').hide();
            },
            error: function() {
                console.info('Something went wrong.. ;-(sorry)');
            }
        });
    });
    
    /**
     * Delete a single slide
     */

    jQuery( document ).on( 'click', '.delete-slide', function(e) {
        e.preventDefault();

        var action  = jQuery(this);
        var args    = jQuery(this).attr('href');
        var select_slides_list =  jQuery('#select_slides_list').val();

       if (confirm('Delete Permanently'))
            jQuery.ajax({
                type: 'POST',
                url: ajaxurl,
                data: {
					'action': strSliderPrefix + 'delete_slide', 
					'args': args 
				},
                success: function(data){
                    //console.info(data);
                    //window.location.reload(true);
                    jQuery('#select_slides_list').val(select_slides_list);
                    jQuery('#select_slides_list').trigger('change');
                },
                error: function() {
                    console.info('Something went wrong.. ;-(sorry)');
                }
            });

        return false;
    });
    
    
    /**
     * NOTE: Fun with delegating events .. note the changed behaviour in jQuery 1.7+!
     * 
     * Implementing delegating / event bubbling as supposed to do since jQuery 1.7 .. meh :(
     */
     
    jQuery( document ).on('click', '.custom-media-upload', function(e) {
		console.log( 'media_upload fires');
		e.preventDefault();
		
		var select_slides_list =  jQuery('#select_slides_list').val();
		console.log( 'media_upload fires');

		if(select_slides_list == -1) {
			//console.info('Select a Slideshow first');
			alert( 'Please select a slideshow');
			//die()
			exit();
			
		}
		
		var custom_uploader = wp.media( {
			title: 'Select an image',
			button: {
				text: 'Add Slide'
			},
			editing:    true,
			multiple:   false,  // Set this to true to allow multiple files to be selected
		})
		.on('select', function() {
			var action = jQuery(this);
			var attachment = custom_uploader.state().get('selection').first().toJSON();

			jQuery.ajax({
				type: 'POST',
				url: ajaxurl,
				data: {
					'action': strSliderPrefix + 'add_slide',
					'url': attachment.url, 
					'id': attachment.id, 
					'select_slides_list': select_slides_list
				},
				success: function(data){
					//console.info(data);
					jQuery('#select_slides_list').val(select_slides_list);
					jQuery('#select_slides_list').trigger('change');
				},
				error: function() {
					console.info('Something went wrong.. ;-(sorry)');
				}
			});
		}).open();
	})
	
	
	jQuery('#reset-slideshows').on('click', function( e ) {
		e.preventDefault();
		
		if( confirm('Warning: This deletes ALL existing slideshows. Still continue?') ) {
			jQuery.ajax({
				type: 'POST',
				url: ajaxurl,
				data: {
					'action': strSliderPrefix + 'reset_slideshows',
				},
				success: function(data){
					//console.info(data);
					//jQuery('#select_slides_list').val(select_slides_list);
					jQuery('#select_slides_list').trigger('change');
				},
				error: function() {
					console.info('Something went wrong.. ;-(sorry)');
				}
				
			});
		}
		
	});	
	
	
/**
 * Advanced Settings: Save changes
 */
	jQuery(document).on('click', '#save-advanced-settings', function( elem ) {
		elem.preventDefault(); // avoid form submission
		
		// :checked selector in jQuery is faulty, thus we try for a native approach
	
			
		var sendOptions = {
			'action': strCustomStylingPrefix + 'save',
			'settings': {
				'custom_css': jQuery('#edit-custom-css').val(),
				'headjs_type': jQuery('input[type="radio"][name="headjs_type"]:checked').val(),
				'headjs_url': jQuery('input[name="headjs_url"]').val(),
				
				
				/*'load_smartmenus_js': ( is_checked( 'input[name="load_smartmenus_js"]' ) ? 1 : 0 ),*/
				'load_hover_dropdown_css': ( is_checked('input[name="load_hover_dropdown_css"]') ? 1 : 0 ),
				'load_scroll_top': ( is_checked('input[name="load_scroll_top"]') ? 1 : 0 ), 
			}
		};
		
		// enable / disable specific test js
		if( jQuery( 'input[name="load_test_js"]' ).length > 0 ) {
		
			/** debugging options */
			sendOptions.settings.load_test_js = ( is_checked('input[name="load_test_js"]') ? 1 : 0 );
			//console.log( 'sendOptions', sendOptions );
		}
		
		//console.log( 'admin_js_add_options', admin_js_add_options );
		
		// add additional options: admin_js_add_options
		if( typeof admin_js_add_options == 'object' ) {
			
			jQuery.each( admin_js_add_options, function( fieldName, fieldValidation ) {
				var strFieldSelector = 'input[name="' + fieldName + '"]';
				
				if( fieldValidation == 'textarea' ) {
					strFieldSelector = 'textarea[name="' + fieldName + '"]';
					fieldValidation = 'text';
				}
				
				if( jQuery( strFieldSelector ).length == 1 ) {
					
					switch( fieldValidation ) {
						case 'checkbox':
						case 'check':
						case 'bool':
						case 'boolean':
							sendOptions.settings[ fieldName ] = ( is_checked( strFieldSelector ) ? 1 : 0 );
							
							break;
						
						case 'text':
						case 'string':
						default:
						
							sendOptions.settings[ fieldName ] = jQuery( strFieldSelector ).val();
							break;
					}
				}
			} );
		}
		
		//console.log( sendOptions );
		
		
		
		//jQuery('input[name="load_smartmenus_js"]:checked')
		
		jQuery.ajax({
			type: 'POST',
			url: ajaxurl,
			data: sendOptions,
			success: function( data ) {
				// add message
				jQuery('#save-advanced-settings').after('<span class="custom-save-message" style="margin-left: 20px"><strong>All saved!</strong></span>');
				window.setTimeout( function() {
					jQuery('.custom-save-message').fadeOut(
						800,
						function() {
							jQuery(this).remove();
						}
					); 
				}
				, 6000 );
				
				
			},
			error: function() {
				
				alert( cc2_admin_js.i10n.advanced_settings.error_save_data );
				
				//console.info('Could not save Custom Styling settings!');
			}
			
			
		});
		
		
	})

/**
 * Backup settings: Simple form validation
 */
 
	// export settings
	if( document.getElementById('init-settings-export') ) { 
		jQuery( document ).on('click', '#init-settings-export', function( e ) {
			var $return = false;
			//console.info( 'export form fires' );
			
			if( jQuery('#select-export-settings input[type=checkbox]:checked').length < 1 ) { // stop
				//console.info('error')
				e.preventDefault();
				alert( cc2_admin_js.i10n.settings_export.error_missing_fields );
				
			} else {
				//console.info(' aaaaall right!');
				$return = true; // continue
			}
			return $return;
		} );
	}

	// import settings
	if( document.getElementById('init-settings-import') ) { 
		jQuery( document ).on('click', '#init-settings-import', function( e ) {
			var $return = false;
			//console.info( 'import form fires' );
			
			if( jQuery('#field-import-data').val() != '' ) { // continue
				$return = true;
			} else { // stop
				//console.info('error')
				e.preventDefault();
				alert( cc2_admin_js.i10n.settings_import.error_missing_data );
				
			}
			return $return;
		} );
	}
	
	
	
	if( jQuery('.import-result-amount').length > 0 ) {
		jQuery('.import-result-list').on('click', 'a.import-result-details-link', function(e) {
			e.preventDefault();
			jQuery( this.hash ).slideToggle();
		});
	}
		
	/**
	 * Backup settings: auto-mark on click
	 */
	 
	if( document.getElementById('export-data-result') ) {
		jQuery('.tab-container-backup .result #select-export-result').show();

		jQuery( document ).on('click', '#select-export-result', function(e) {
			e.preventDefault();
			jQuery('#export-data-result').select();
			
		})
	}

	/**
	 * Reset settings: disable + show message if no settings are selected
	 */
	 
	jQuery('#init-settings-reset').on('click', function( e ) {
		var $stop = [],
			iStopCount = 0;
			
		var resetItems = jQuery('.field-reset-items');
		
		jQuery('.field-reset-items').each( function( i ) {
			
			console.log( i, jQuery( this ).attr('checked') );
			if( jQuery( this ).attr('checked') != 'checked' ) {
				//console.log(i, 'checked');
				
				$stop[iStopCount] = true;
				iStopCount++;
			}
		})
		
		console.log( $stop.length, resetItems.length );
		if( $stop.length == resetItems.length ) {
			alert( 'No settings selected.' );
			e.preventDefault();
		}
	});
	
	/**
	 * Reset settings: abort
	 */
	if( document.getElementById('init-settings-confirm-abort') ) {
		
		jQuery('#init-settings-confirm-abort').on('click', function( e ) {
			e.preventDefault();
			
			history.back(-1);
			
		});
	}
	
});

function create_slug( name ) {
	var $return = name;
	
	/**
	 * Mimicking sanitize_key
	 * @see wp-includes/formatting.php:sanitize_key()
	 */
	// remove special chars
	$return = $return.toLowerCase();
	$return = $return.replace( '/[^a-z0-9_\-]/', '' );
	
	// rest
	$return = str_replace( [' ', '--'], '-', $return );
	
	return $return;
}


// :checked selector in jQuery is flawed, thus we try going for a native approach
function is_checked( selector ) {
	$return = false;

	//console.info( 'is_checked');
	//jQuery('input[name="load_smartmenus_js"]').attr('checked')
	if( typeof document.querySelector != 'undefined' ) { // yay for modern browsers!
		
		//console.log( 'selector', selector, document.querySelector( selector ) );
		$return = ( document.querySelector( selector ) != null && document.querySelector( selector ).checked );
	} else { // ugh .. prolly nasty internet exploiter and his ugly pals
		$return = ( jQuery( selector ).attr('checked') == 'checked'  ? true : false );
	}
	
	return $return;
}

function str_replace(search, replace, subject, count) {
  //  discuss at: http://phpjs.org/functions/str_replace/
  // original by: Kevin van Zonneveld (http://kevin.vanzonneveld.net)
  // improved by: Gabriel Paderni
  // improved by: Philip Peterson
  // improved by: Simon Willison (http://simonwillison.net)
  // improved by: Kevin van Zonneveld (http://kevin.vanzonneveld.net)
  // improved by: Onno Marsman
  // improved by: Brett Zamir (http://brett-zamir.me)
  //  revised by: Jonas Raoni Soares Silva (http://www.jsfromhell.com)
  // bugfixed by: Anton Ongson
  // bugfixed by: Kevin van Zonneveld (http://kevin.vanzonneveld.net)
  // bugfixed by: Oleg Eremeev
  //    input by: Onno Marsman
  //    input by: Brett Zamir (http://brett-zamir.me)
  //    input by: Oleg Eremeev
  //        note: The count parameter must be passed as a string in order
  //        note: to find a global variable in which the result will be given
  //   example 1: str_replace(' ', '.', 'Kevin van Zonneveld');
  //   returns 1: 'Kevin.van.Zonneveld'
  //   example 2: str_replace(['{name}', 'l'], ['hello', 'm'], '{name}, lars');
  //   returns 2: 'hemmo, mars'
  // bugfixed by: Glen Arason (http://CanadianDomainRegistry.ca)
  //   example 3: str_replace(Array('S','F'),'x','ASDFASDF');
  //   returns 3: 'AxDxAxDx'
  // bugfixed by: Glen Arason (http://CanadianDomainRegistry.ca) Corrected count
  //   example 4: str_replace(['A','D'], ['x','y'] , 'ASDFASDF' , 'cnt');
  //   returns 4: 'xSyFxSyF' // cnt = 0 (incorrect before fix)
  //   returns 4: 'xSyFxSyF' // cnt = 4 (correct after fix)
  
  var i = 0,
    j = 0,
    temp = '',
    repl = '',
    sl = 0,
    fl = 0,
    f = [].concat(search),
    r = [].concat(replace),
    s = subject,
    ra = Object.prototype.toString.call(r) === '[object Array]',
    sa = Object.prototype.toString.call(s) === '[object Array]';
  s = [].concat(s);
  
  if(typeof(search) === 'object' && typeof(replace) === 'string' ) {
    temp = replace; 
    replace = new Array();
    for (i=0; i < search.length; i+=1) { 
      replace[i] = temp; 
    }
    temp = ''; 
    r = [].concat(replace); 
    ra = Object.prototype.toString.call(r) === '[object Array]';
  }
  
  if (count) {
    this.window[count] = 0;
  }

  for (i = 0, sl = s.length; i < sl; i++) {
    if (s[i] === '') {
      continue;
    }
    for (j = 0, fl = f.length; j < fl; j++) {
      temp = s[i] + '';
      repl = ra ? (r[j] !== undefined ? r[j] : '') : r[0];
      s[i] = (temp)
        .split(f[j])
        .join(repl);
      if (count) {
        this.window[count] += ((temp.split(f[j])).length - 1);
      } 
    }
  }
  return sa ? s : s[0];
}



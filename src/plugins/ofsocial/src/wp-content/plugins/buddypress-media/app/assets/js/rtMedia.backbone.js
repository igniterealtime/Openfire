var galleryObj;
var nextpage = 2;
var upload_sync = false;
var activity_id = - 1;
var uploaderObj;
var objUploadView;
var rtmedia_load_template_flag = true;

jQuery( function ( $ ) {

	var o_is_album, o_is_edit_allowed;
	if ( typeof ( is_album ) == "undefined" ) {
		o_is_album = new Array( "" );
	} else {
		o_is_album = is_album
	}
	if ( typeof ( is_edit_allowed ) == "undefined" ) {
		o_is_edit_allowed = new Array( "" )
	} else {
		o_is_edit_allowed = is_edit_allowed;
	}

	rtMedia = window.rtMedia || { };

	rtMedia = window.rtMedia || { };

	rtMedia.Context = Backbone.Model.extend( {
		url: function () {
			var url = rtmedia_media_slug + "/";
			if ( ! upload_sync && nextpage > 0 )
				url += 'pg/' + nextpage + '/'
			return url;
		},
		defaults: {
			"context": "post",
			"context_id": false
		}
	} );

	rtMedia.Media = Backbone.Model.extend( {
		defaults: {
			"id": 0,
			"blog_id": false,
			"media_id": false,
			"media_author": false,
			"media_title": false,
			"album_id": false,
			"media_type": "photo",
			"activity_id": false,
			"privacy": 0,
			"views": 0,
			"downloads": 0,
			"ratings_average": 0,
			"ratings_total": 0,
			"ratings_count": 0,
			"likes": 0,
			"dislikes": 0,
			"guid": false,
			"width": 0,
			"height": 0,
			"rt_permalink": false,
			"duration": "0:00"
					//			"next"			: -1,
					//			"prev"			: -1
		}

	} );

	rtMedia.Gallery = Backbone.Collection.extend( {
		model: rtMedia.Media,
		url: function () {
			var temp = window.location.pathname;
			var url = '';
			if ( temp.indexOf( "/" + rtmedia_media_slug + "/" ) == - 1 ) {
				url = rtmedia_media_slug + '/';
			} else {
				if ( temp.indexOf( 'pg/' ) == - 1 )
					url = temp;
				else
					url = window.location.pathname.substr( 0, window.location.pathname.lastIndexOf( "pg/" ) );
			}
			if ( ! upload_sync && nextpage > 1 ) {
				if ( url.substr( url.length - 1 ) != "/" )
					url += "/"
				url += 'pg/' + nextpage + '/';
			}
			return url;
		},
		getNext: function ( page, el, element ) {
			if ( jQuery( '.rtmedia-no-media-found' ).length > 0 ) {
				jQuery( '.rtmedia-no-media-found' ).replaceWith( "<ul class='rtmedia-list rtmedia-list-media'></ul>" );
			}
			that = this;
			if ( rtmedia_load_template_flag == true ) {
				if ( jQuery( '.rtmedia_gallery_wrapper' ).find( 'input[name=media_title]' ).length > 0 ) {
					template_url += '&media_title=' + jQuery( '.rtmedia_gallery_wrapper' ).find( 'input[name=media_title]' ).val();
				}
				if ( jQuery( '.rtmedia_gallery_wrapper' ).find( 'input[name=lightbox]' ).length > 0 ) {
					template_url += '&lightbox=' + jQuery( '.rtmedia_gallery_wrapper' ).find( 'input[name=lightbox]' ).val();
				}
				$( "#rtmedia-gallery-item-template" ).load( template_url, {
					backbone: true,
					is_album: o_is_album,
					is_edit_allowed: o_is_edit_allowed
				}, function () {
					rtmedia_load_template_flag = false;
					that.getNext( page, el, element );
				} );
			}

			if ( ! rtmedia_load_template_flag ) {
				var query = {
					json: true,
					rtmedia_page: nextpage
				};
				if ( el == undefined ) {
					el = jQuery( ".rtmedia-list" ).parent().parent();
				}
				if ( el != undefined ) {
					if ( element != undefined ) {
						$( element ).parent().parent().prevAll( "input[type=hidden]" ).each( function ( e ) {
							query[$( this ).attr( "name" )] = $( this ).val();
						} );
					}
					$( el ).find( "input[type=hidden]" ).each( function ( e ) {
						query[$( this ).attr( "name" )] = $( this ).val();
					} );
				}
				this.fetch( {
					data: query,
					success: function ( model, response ) {
						jQuery( '.rtm-media-loading' ).hide();
						var list_el = "";
						if ( typeof ( element ) === "undefined" )
							list_el = $( ".rtmedia-list" )[0];
						else
							list_el = element.parent().siblings( '.rtmedia-list' );
						nextpage = response.next;

						if ( nextpage < 1 ) {
							if ( typeof el == "object" ) {
								jQuery( el ).find( '.rtmedia_next_prev' ).children( '#rtMedia-galary-next' ).hide();
							}
							//$("#rtMedia-galary-next").show();
						}

						var galleryViewObj = new rtMedia.GalleryView( {
							collection: new rtMedia.Gallery( response.data ),
							el: list_el
						} );
						//element.show();
						jQuery( '.rtmedia-container .rtmedia-list' ).css( 'opacity', '1' );
						rtMediaHook.call( 'rtmedia_after_gallery_load' );

						jQuery( '.rtmedia-container .rtmedia_next_prev .rtm-pagination' ).remove();
						jQuery( '.rtmedia-container .rtmedia_next_prev .clear' ).remove();
						jQuery( '.rtmedia-container .rtmedia_next_prev .rtm-media-loading' ).remove();
						jQuery( '.rtmedia-container .rtmedia_next_prev br' ).remove();
						jQuery( '.rtmedia-container .rtmedia_next_prev' ).append( response.pagination );
					}
				} );
			}
		},
		reloadView: function () {
			upload_sync = true;
			nextpage = 1;
			jQuery( '.rtmedia-container .rtmedia-list' ).css( 'opacity', '0.5' );
			this.getNext();
		}


	} );

	rtMedia.MediaView = Backbone.View.extend( {
		tagName: 'li',
		className: 'rtmedia-list-item',
		initialize: function () {
			this.template = _.template( $( "#rtmedia-gallery-item-template" ).html() );
			this.model.bind( 'change', this.render );
			this.model.bind( 'remove', this.unrender );
			this.render();
		},
		render: function () {
			$( this.el ).html( this.template( this.model.toJSON() ) );
			return this.el;
		},
		unrender: function () {
			$( this.el ).remove();
		},
		remove: function () {
			this.model.destroy();
		}
	} );

	rtMedia.GalleryView = Backbone.View.extend( {
		tagName: 'ul',
		className: 'rtmedia-list',
		initialize: function () {
			this.template = _.template( $( "#rtmedia-gallery-item-template" ).html() );
			this.render();
		},
		render: function () {

			that = this;

			if ( upload_sync ) {
				$( that.el ).html( '' );
			}

			if ( typeof ( rtmedia_load_more_or_pagination ) != 'undefined' && rtmedia_load_more_or_pagination == 'pagination' ) {
				$( that.el ).html( '' );
			}

			$.each( this.collection.toJSON(), function ( key, media ) {
				$( that.el ).append( that.template( media ) );
			} );

			if ( upload_sync ) {
				upload_sync = false;
			}
			if ( nextpage > 1 ) {
				$( that.el ).siblings( '.rtmedia_next_prev' ).children( '#rtMedia-galary-next' ).show();
				//$("#rtMedia-galary-next").show();
			}
			if ( typeof rtmedia_masonry_layout != "undefined" && rtmedia_masonry_layout == "true" && jQuery( '.rtmedia-container .rtmedia-list.rtm-no-masonry' ).length == 0 ) {
				rtm_masonry_reload( rtm_masonry_container );
			}
		},
		appendTo: function ( media ) {
			//console.log("append");
			var mediaView = new rtMedia.MediaView( {
				model: media
			} );
			$( this.el ).append( mediaView.render().el );
		}
	} );


	galleryObj = new rtMedia.Gallery();

	$( "body" ).append( '<script id="rtmedia-gallery-item-template" type="text/template"></script>' );

	$( document ).on( "click", "#rtMedia-galary-next", function ( e ) {
		if ( jQuery( '.rtm-media-loading' ).length == 0 ) {
			$( this ).before( "<div class='rtm-media-loading'><img src='" + rMedia_loading_media + "' /></div>" );
		} else {
			jQuery( '.rtm-media-loading' ).show();
		}
		$( this ).hide();
		e.preventDefault();
		galleryObj.getNext( nextpage, $( this ).parent().parent().parent(), $( this ) );
	} );
        
        $( document ).on( "keypress", "#rtmedia_go_to_num", function( e ) {
            if ( e.keyCode == 13 ) {
                e.preventDefault();
                 
                if ( jQuery( '.rtm-media-loading' ).length == 0 ) {
                    $( '.rtm-pagination' ).before( "<div class='rtm-media-loading'><img src='" + rMedia_loading_media + "' /></div>" );
		} else {
                    jQuery( '.rtm-media-loading' ).show();
		}
                
                if ( parseInt( $( '#rtmedia_go_to_num' ).val() ) > parseInt( $( '#rtmedia_last_page' ).val() ) ) {
                    nextpage = parseInt( $( '#rtmedia_last_page' ).val() );
                } else {
                    nextpage = parseInt( $( '#rtmedia_go_to_num' ).val() );
                }
                        
                galleryObj.getNext( nextpage, $( this ).parent().parent().parent().parent().parent(), $( this ).parent().parent() );
                
                return false;
            }
        } );

	$( document ).on( "click", ".rtmedia-page-link", function ( e ) {
		if ( jQuery( '.rtm-media-loading' ).length == 0 ) {
			$( '.rtm-pagination' ).before( "<div class='rtm-media-loading'><img src='" + rMedia_loading_media + "' /></div>" );
		} else {
			jQuery( '.rtm-media-loading' ).show();
		}

		e.preventDefault();
		if ( $( this ).data( 'page-type' ) == 'page' ) {
			nextpage = $( this ).data( 'page' );
		} else if ( $( this ).data( 'page-type' ) == 'prev' ) {
			if ( nextpage == - 1 ) {
				nextpage = parseInt( $( '#rtmedia_last_page' ).val() ) - 1;
			} else {
				nextpage -= 2;
			}
		} else if ( $( this ).data( 'page-type' ) == 'num' ) {
			if ( parseInt( $( '#rtmedia_go_to_num' ).val() ) > parseInt( $( '#rtmedia_last_page' ).val() ) ) {
				nextpage = parseInt( $( '#rtmedia_last_page' ).val() );
			} else {
				nextpage = parseInt( $( '#rtmedia_go_to_num' ).val() );
			}
		}

		if ( $( this ).data( 'page-type' ) == 'num' ) {
			galleryObj.getNext( nextpage, $( this ).parent().parent().parent().parent().parent(), $( this ).parent().parent() );
		} else {
			galleryObj.getNext( nextpage, $( this ).parent().parent().parent().parent().parent(), $( this ).parent().parent() );
		}
	} );

	if ( window.location.pathname.indexOf( rtmedia_media_slug ) != - 1 ) {
		var tempNext = window.location.pathname.substring( window.location.pathname.lastIndexOf( "pg/" ) + 5, window.location.pathname.lastIndexOf( "/" ) );
		if ( isNaN( tempNext ) === false ) {
			nextpage = parseInt( tempNext ) + 1;
		}
	}


	window.UploadView = Backbone.View.extend( {
		events: {
			"click #rtMedia-start-upload": "uploadFiles"
		},
		initialize: function ( config ) {
			this.uploader = new plupload.Uploader( config );
		},
		render: function () {

		},
		initUploader: function ( a ) {
			if ( typeof ( a ) !== "undefined" )
				a = false;// if rtmediapro widget calls the function, dont show max size note.
			this.uploader.init();
			//The plupload HTML5 code gives a negative z-index making add files button unclickable
			$( ".plupload.html5" ).css( {
				zIndex: 0
			} );
			$( "#rtMedia-upload-button" ).css( {
				zIndex: 2
			} );
			if ( a !== false ) {
				window.file_size_info = rtmedia_max_file_msg + this.uploader.settings.max_file_size_msg;
				if ( rtmedia_version_compare( rtm_wp_version, "3.9" ) ) { // plupload getting updated in 3.9
					file_extn = this.uploader.settings.filters.mime_types[0].extensions;
				} else {
					file_extn = this.uploader.settings.filters[0].extensions;
				}
				window.file_extn_info = rtmedia_allowed_file_formats + " : " + file_extn.split( ',' ).join( ', ' );

				var info = window.file_size_info + "\n" + window.file_extn_info;
				$( ".rtm-file-size-limit" ).attr( 'title', info );
				//$("#rtMedia-upload-button").after("<span>( <strong>" + rtmedia_max_file_msg + "</strong> "+ this.uploader.settings.max_file_size_msg + ")</span>");
			}

			return this;
		},
		uploadFiles: function ( e ) {
			if ( e != undefined )
				e.preventDefault();
			this.uploader.start();
			return false;
		}

	} );


	if ( $( "#rtMedia-upload-button" ).length > 0 ) {
		if ( typeof rtmedia_upload_type_filter == "object" && rtmedia_upload_type_filter.length > 0 ) {
			rtMedia_plupload_config.filters[0].extensions = rtmedia_upload_type_filter.join();
		}
		uploaderObj = new UploadView( rtMedia_plupload_config );
		uploaderObj.initUploader();


		uploaderObj.uploader.bind( 'UploadComplete', function ( up, files ) {
			activity_id = - 1;
			var hook_respo = rtMediaHook.call( 'rtmedia_js_after_files_uploaded' );
			if ( typeof rtmedia_gallery_reload_on_upload != "undefined" && rtmedia_gallery_reload_on_upload == '1' ) { //reload gallery view when upload completes if enabled( by default enabled)
				if ( hook_respo != false ) {
					galleryObj.reloadView();
				}
			}
			jQuery( '#rtmedia_uploader_filelist li.plupload_queue_li' ).remove();
			jQuery( '.start-media-upload' ).hide();
            apply_rtMagnificPopup( jQuery( '.rtmedia-list-media, .rtmedia-activity-container ul.rtmedia-list, #bp-media-list,.widget-item-listing,.bp-media-sc-list, li.media.album_updated ul,ul.bp-media-list-media, li.activity-item div.activity-content div.activity-inner div.bp_media_content' ) );
			window.onbeforeunload = null;
		} );

		uploaderObj.uploader.bind( 'FilesAdded', function ( up, files ) {
			var upload_size_error = false;
			var upload_error = "";
			var upload_error_sep = "";
			var upload_remove_array = [ ];
			$.each( files, function ( i, file ) {
				//set file title along with file
				rtm_file_name_array = file.name.split( '.' );
				file.title = rtm_file_name_array[0];

				var hook_respo = rtMediaHook.call( 'rtmedia_js_file_added', [ up, file, "#rtmedia_uploader_filelist" ] );

				if ( hook_respo == false ) {
					file.status = - 1;
					upload_remove_array.push( file.id );
					return true;
				}

				jQuery( '.rtmedia-upload-input' ).attr( 'value', rtmedia_add_more_files_msg );
                if( typeof rtmedia_direct_upload_enabled != 'undefined' && rtmedia_direct_upload_enabled == '1' ) {
                    jQuery( '.start-media-upload' ).hide();
                } else {
                    jQuery( '.start-media-upload' ).show();
                }
				if ( uploaderObj.uploader.settings.max_file_size < file.size ) {
					return true;
				}
				var tmp_array = file.name.split( "." );
				if ( rtmedia_version_compare( rtm_wp_version, "3.9" ) ) { // plupload getting updated in 3.9
					var ext_array = uploaderObj.uploader.settings.filters.mime_types[0].extensions.split( ',' );
				} else {
					var ext_array = uploaderObj.uploader.settings.filters[0].extensions.split( ',' );
				}
				if ( tmp_array.length > 1 ) {
					var ext = tmp_array[tmp_array.length - 1];
					ext = ext.toLowerCase();
					if ( jQuery.inArray( ext, ext_array ) === - 1 ) {
						return true;
					}
				} else {
					return true;
				}

				if ( rtmedia_version_compare( rtm_wp_version, "3.9" ) ) { // plupload getting updated in 3.9
					uploaderObj.uploader.settings.filters.mime_types[0].title;
				} else {
					uploaderObj.uploader.settings.filters[0].title;
				}

				// Creating list of media to preview selected files
				rtmedia_selected_file_list( plupload, file, '', '' );

				//Delete Function
				$( "#" + file.id + " .plupload_delete .remove-from-queue" ).click( function ( e ) {
					e.preventDefault();
					uploaderObj.uploader.removeFile( up.getFile( file.id ) );
					$( "#" + file.id ).remove();
					rtMediaHook.call( 'rtmedia_js_file_remove', [ up, file ] );
					return false;
				} );

				// To change the name of the uploading file
				$( "#label_" + file.id ).click( function ( e ) {
                    e.preventDefault();

                    rtm_file_label = this;
                    rtm_file_title_input = '#text_' + file.id;
                    rtm_file_title_save = '#save_' + file.id;

                    jQuery( rtm_file_label ).hide();
                    jQuery( rtm_file_label ).siblings( '.plupload_file_name_wrapper' ).hide();

                    // show/create text box to edit media title
                    if( jQuery( rtm_file_title_input ).length === 0 ) {
                        jQuery( rtm_file_label ).parent( '.plupload_file_name' ).prepend( '<input type="text" id="text_' + file.id + '" value="' + file.title + '" style="width: 75%;" /><span id="save_' + file.id + '" title="Save Change" class="rtmicon dashicons dashicons-yes"></span>' );
                    } else {
                        jQuery( rtm_file_title_input ).show();
                        jQuery( rtm_file_title_save ).show();
                    }

                    jQuery( rtm_file_title_input ).focus();

                    // set new media title
                    jQuery( rtm_file_title_input ).keyup( function( e ) {
                        if( this.value != '' ) {
                            file.title = this.value;
                        }

                        if( e.keyCode == '13' ) {
                            return false;
                        }
                    } );

                    // hide input box for media title and show label of media title
                    jQuery( rtm_file_title_input ).blur( function( e ) {
                        if ( this.value != '' ) {
                            jQuery( rtm_file_title_input ).hide();
                            jQuery( rtm_file_title_save ).hide();
                            jQuery( rtm_file_label ).siblings( '.plupload_file_name_wrapper' ).text( file.title + "." + rtm_file_name_array[ 1 ] );
                        }

                        jQuery( rtm_file_label ).siblings( '.plupload_file_name_wrapper' ).show();
                        jQuery( rtm_file_label ).show();
                    } );
                } );
			} );
            
			$.each( upload_remove_array, function ( i, rfile ) {
				if ( up.getFile( rfile ) )
					up.removeFile( up.getFile( rfile ) );
			} );

			rtMediaHook.call( 'rtmedia_js_after_files_added', [ up, files ] );
            
            if( typeof rtmedia_direct_upload_enabled != 'undefined' && rtmedia_direct_upload_enabled == '1' ) {
				var allow_upload = rtMediaHook.call( 'rtmedia_js_upload_file', true );
				if ( allow_upload == false ) {
					return false;
				}
				uploaderObj.uploadFiles();
            }

		} );

		uploaderObj.uploader.bind( 'Error', function ( up, err ) {

			if ( err.code == - 600 ) { //file size error // if file size is greater than server's max allowed size
				var tmp_array;
				var ext = tr = '';
				tmp_array = err.file.name.split( "." );
				if ( tmp_array.length > 1 ) {
					ext = tmp_array[tmp_array.length - 1];
					if ( ! ( typeof ( up.settings.upload_size ) != "undefined" && typeof ( up.settings.upload_size[ext] ) != "undefined" && typeof ( up.settings.upload_size[ext]['size'] ) ) ) {
						rtmedia_selected_file_list( plupload, err.file, up, err );
					}
				}
			}
			else {

				if ( err.code == - 601 ) { // file extension error
					err.message = rtmedia_file_extension_error_msg;
				}

				rtmedia_selected_file_list( plupload, err.file, '', err );
			}

			jQuery( '.plupload_delete' ).on( 'click', function ( e ) {
				e.preventDefault();
				jQuery( this ).parent().parent( 'li' ).remove();
			} );
			return false;

		} );
            
        jQuery( '.start-media-upload' ).on( 'click', function ( e ) {
            e.preventDefault();
            var allow_upload = rtMediaHook.call( 'rtmedia_js_upload_file', true );
            if ( allow_upload == false ) {
                return false;
            }
            uploaderObj.uploadFiles();
        } );

		uploaderObj.uploader.bind( 'UploadProgress', function ( up, file ) {
			//$("#" + file.id + " .plupload_file_status").html(file.percent + "%");
			//$( "#" + file.id + " .plupload_file_status" ).html( rtmedia_uploading_msg + '( ' + file.percent + '% )' );
			$( "#" + file.id + " .plupload_file_status" ).html( '<div class="plupload_file_progress ui-widget-header" style="width: ' + file.percent + '%;"></div>' );
			$( "#" + file.id ).addClass( 'upload-progress' );
			if ( file.percent == 100 ) {
				$( "#" + file.id ).toggleClass( 'upload-success' );
			}

			window.onbeforeunload = function ( evt ) {
				var message = rtmedia_upload_progress_error_message;
				return message;
			};
		} );

		uploaderObj.uploader.bind( 'BeforeUpload', function ( up, file ) {
			up.settings.multipart_params.title = file.title.split( '.' )[ 0 ];
			var privacy = $( "#rtm-file_upload-ui select.privacy" ).val();
			if ( privacy !== undefined ) {
				up.settings.multipart_params.privacy = $( "#rtm-file_upload-ui select.privacy" ).val();
			}
			if ( jQuery( "#rt_upload_hf_redirect" ).length > 0 )
				up.settings.multipart_params.redirect = up.files.length;
			jQuery( "#rtmedia-uploader-form input[type=hidden]" ).each( function () {
				up.settings.multipart_params[$( this ).attr( "name" )] = $( this ).val();
			} );
			up.settings.multipart_params.activity_id = activity_id;
			if ( $( '#rtmedia-uploader-form .rtmedia-user-album-list' ).length > 0 )
				up.settings.multipart_params.album_id = $( '#rtmedia-uploader-form .rtmedia-user-album-list' ).find( ":selected" ).val();
			else if ( $( '#rtmedia-uploader-form .rtmedia-current-album' ).length > 0 )
				up.settings.multipart_params.album_id = $( '#rtmedia-uploader-form .rtmedia-current-album' ).val();

            rtMediaHook.call( 'rtmedia_js_before_file_upload', [up, file] );
		} );

		uploaderObj.uploader.bind( 'FileUploaded', function ( up, file, res ) {
			if ( /MSIE (\d+\.\d+);/.test( navigator.userAgent ) ) { //test for MSIE x.x;
				var ieversion = new Number( RegExp.$1 ) // capture x.x portion and store as a number

				if ( ieversion < 10 ) {
					if ( typeof res.response !== "undefined" )
						res.status = 200;
				}
			}
			var rtnObj;
			try {

				rtnObj = JSON.parse( res.response );
				uploaderObj.uploader.settings.multipart_params.activity_id = rtnObj.activity_id;
				activity_id = rtnObj.activity_id;
				if ( rtnObj.permalink != '' ) {
					$( "#" + file.id + " .plupload_file_name" ).html( "<a href='" + rtnObj.permalink + "' target='_blank' title='" + rtnObj.permalink + "'>" + file.title.substring( 0, 40 ).replace( /(<([^>]+)>)/ig, "" ) + "</a>" );
					$( "#" + file.id + " .plupload_media_edit" ).html( "<a href='" + rtnObj.permalink + "edit' target='_blank'><span title='" + rtmedia_edit_media + "'><i class='dashicons dashicons-edit rtmicon'></i> " + rtmedia_edit + "</span></a>" );
					$( "#" + file.id + " .plupload_delete" ).html( "<span id='" + rtnObj.media_id + "' class='rtmedia-delete-uploaded-media' title='" + rtmedia_delete + "'>&times;</span>" );
				}

			} catch ( e ) {
				// console.log('Invalid Activity ID');
			}
			if ( res.status == 200 || res.status == 302 ) {
				if ( uploaderObj.upload_count == undefined )
					uploaderObj.upload_count = 1;
				else
					uploaderObj.upload_count ++;

				if ( uploaderObj.upload_count == up.files.length && jQuery( "#rt_upload_hf_redirect" ).length > 0 && jQuery.trim( rtnObj.redirect_url.indexOf( "http" ) == 0 ) ) {
					window.location = rtnObj.redirect_url;
				}

				$( "#" + file.id + " .plupload_file_status" ).html( rtmedia_uploaded_msg );
				rtMediaHook.call( 'rtmedia_js_after_file_upload', [ up, file, res.response ] );
			} else {
				$( "#" + file.id + " .plupload_file_status" ).html( rtmedia_upload_failed_msg );
			}

			files = up.files;
			lastfile = files[files.length - 1];


		} );

		uploaderObj.uploader.refresh();//refresh the uploader for opera/IE fix on media page

		$( "#rtMedia-start-upload" ).click( function ( e ) {
			uploaderObj.uploadFiles( e );
		} );
		$( "#rtMedia-start-upload" ).hide();

		jQuery( document ).on( 'click', '#rtm_show_upload_ui', function () {
			jQuery( '#rtm-media-gallery-uploader' ).slideToggle();
			uploaderObj.uploader.refresh();//refresh the uploader for opera/IE fix on media page
			jQuery( '#rtm_show_upload_ui' ).toggleClass( 'primary' );
		} );
	} else {
		jQuery( document ).on( 'click', '#rtm_show_upload_ui', function () {
			jQuery( '#rtm-media-gallery-uploader' ).slideToggle();
			jQuery( '#rtm_show_upload_ui' ).toggleClass( 'primary' );
		} );
	}

	jQuery( document ).on( 'click', '.plupload_delete .rtmedia-delete-uploaded-media', function () {
		var that = $( this );
		if ( confirm( rtmedia_delete_uploaded_media ) ) {
			var nonce = $( '#rtmedia-upload-container #rtmedia_media_delete_nonce' ).val();
			var media_id = $( this ).attr( 'id' );
			var data = {
				action: 'delete_uploaded_media',
				nonce: nonce,
				media_id: media_id
			}

			$.post( ajaxurl, data, function ( response ) {
				if ( response == '1' ) {
					that.closest( 'tr' ).remove();
					$( '#' + media_id ).remove();
				}
			} );
		}
	} );


} );

/** Activity Update Js **/

jQuery( document ).ready( function ( $ ) {

	//handling the "post update: button on activity page
	jQuery( '#aw-whats-new-submit' ).removeAttr( 'disabled' );
	jQuery( document ).on( "blur", '#whats-new', function () {
		setTimeout( function () {
			jQuery( '#aw-whats-new-submit' ).removeAttr( 'disabled' );
		}, 100 );
	} );
	jQuery( '#aw-whats-new-submit' ).on( 'click', function ( e ) {
		setTimeout( function () {
			jQuery( '#aw-whats-new-submit' ).removeAttr( 'disabled' );
		}, 100 );
	} );

	// when user changes the value in activity "post in" dropdown, hide the privacy dropdown and show when posting in profile.
	jQuery( '#whats-new-post-in' ).on( 'change', function ( e ) {
		if ( jQuery( this ).val() == '0' ) {
			jQuery( "#rtmedia-action-update .privacy" ).prop( 'disabled', false ).show();
		} else {
			jQuery( "#rtmedia-action-update .privacy" ).prop( 'disabled', true ).hide();
		}
	} );

	if ( typeof rtMedia_update_plupload_config == 'undefined' ) {
		return false;
	}
	var activity_attachemnt_ids = [ ];

	objUploadView = new UploadView( rtMedia_update_plupload_config );
	objUploadView.initUploader();

	setTimeout( function () {
		if ( $( "#rtmedia-add-media-button-post-update" ).length > 0 ) {
			$( "#whats-new-options" ).prepend( $( ".rtmedia-plupload-container" ) );
			if ( $( "#rtm-file_upload-ui .privacy" ).length > 0 ) {
				$( ".rtmedia-plupload-container" ).append( $( "#rtm-file_upload-ui .privacy" ) );
			}
			$( '#rtmedia-whts-new-upload-container > div' ).css( 'top', '0' );
			$( '#rtmedia-whts-new-upload-container > div' ).css( 'left', '0' );
		}
	}, 100 );

	if ( $( '#whats-new-options' ).length > 0 && $( '.rtmedia-uploader-div' ).length > 0 ) {
		$( '#whats-new-options' ).append( $( '.rtmedia-uploader-div' ) );
	}

	$( "#whats-new-form" ).on( 'click', '#rtmedia-add-media-button-post-update', function ( e ) {
		objUploadView.uploader.refresh();
		$( '#rtmedia-whts-new-upload-container > div' ).css( 'top', '0' );
		$( '#rtmedia-whts-new-upload-container > div' ).css( 'left', '0' );

		//Enable 'post update' button when media get select
		$( '#aw-whats-new-submit' ).prop("disabled", false);
	} );
	//whats-new-post-in

	objUploadView.upload_remove_array = [ ];
	objUploadView.uploader.bind( 'FilesAdded', function ( upl, rfiles ) {
		//$("#aw-whats-new-submit").attr('disabled', 'disabled');

		$.each( rfiles, function ( i, file ) {

			//set file title along with file
			rtm_file_name_array = file.name.split( '.' );
			file.title = rtm_file_name_array[0];

			var hook_respo = rtMediaHook.call( 'rtmedia_js_file_added', [ upl, file, "#rtmedia_uploader_filelist" ] );

			if ( hook_respo == false ) {
				file.status = - 1;
				objUploadView.upload_remove_array.push( file.id );
				return true;
			}

			if ( objUploadView.uploader.settings.max_file_size < file.size ) {
				return true;
			}

			var tmp_array = file.name.split( "." );

			if ( rtmedia_version_compare( rtm_wp_version, "3.9" ) ) { // plupload getting updated in 3.9
				var ext_array = objUploadView.uploader.settings.filters.mime_types[0].extensions.split( ',' );
			} else {
				var ext_array = objUploadView.uploader.settings.filters[0].extensions.split( ',' );
			}
			if ( tmp_array.length > 1 ) {
				var ext = tmp_array[tmp_array.length - 1];
				ext = ext.toLowerCase();
				if ( jQuery.inArray( ext, ext_array ) === - 1 ) {
					return true;
				}
			} else {
				return true;
			}

			rtmedia_selected_file_list( plupload, file, '', '' );

			jQuery( '#whats-new-content' ).css( 'padding-bottom', '0px' );
            
            $( "#" + file.id + " .plupload_delete" ).click( function ( e ) {
				e.preventDefault();
				objUploadView.uploader.removeFile( upl.getFile( file.id ) );
				$( "#" + file.id ).remove();
				return false;
			} );

			// To change the name of the uploading file
            $( "#label_" + file.id ).click( function ( e ) {
                e.preventDefault();

                rtm_file_label = this;
                rtm_file_title_input = '#text_' + file.id;
                rtm_file_title_save = '#save_' + file.id;

                jQuery( rtm_file_label ).hide();
                jQuery( rtm_file_label ).siblings( '.plupload_file_name_wrapper' ).hide();

                // show/create text box to edit media title
                if( jQuery( rtm_file_title_input ).length === 0 ) {
                    jQuery( rtm_file_label ).parent( '.plupload_file_name' ).prepend( '<input type="text" id="text_' + file.id + '" value="' + file.title + '" style="width: 75%;" /><span id="save_' + file.id + '" title="Save Change" class="rtmicon dashicons dashicons-yes"></span>' );
                } else {
                    jQuery( rtm_file_title_input ).show();
                    jQuery( rtm_file_title_save ).show();
                }

                jQuery( rtm_file_title_input ).focus();

                // set new media title
                jQuery( rtm_file_title_input ).keyup( function( e ) {
                    if( this.value != '' ) {
                        file.title = this.value;
                    }

                    if( e.keyCode == '13' ) {
                        return false;
                    }
                } );

                // hide input box for media title and show label of media title
                jQuery( rtm_file_title_input ).blur( function( e ) {
                    if ( this.value != '' ) {
                        jQuery( rtm_file_title_input ).hide();
                        jQuery( rtm_file_title_save ).hide();
                        jQuery( rtm_file_label ).siblings( '.plupload_file_name_wrapper' ).text( file.title + "." + rtm_file_name_array[ 1 ] );
                    }

                    jQuery( rtm_file_label ).siblings( '.plupload_file_name_wrapper' ).show();
                    jQuery( rtm_file_label ).show();
                } );
            } );
		} );

		$.each( objUploadView.upload_remove_array, function ( i, rfile ) {
			if ( upl.getFile( rfile ) )
				upl.removeFile( upl.getFile( rfile ) );
		} );
        
        if( typeof rtmedia_direct_upload_enabled != 'undefined' && rtmedia_direct_upload_enabled == '1' && jQuery.trim( jQuery( "#whats-new" ).val() ) != "" ) {
           	//Call upload event direct when direct upload is enabled (removed UPLOAD button and its triggered event)
            var allow_upload = rtMediaHook.call( 'rtmedia_js_upload_file', true );
            
            if( allow_upload == false ) {
                return false;
            }
            
            objUploadView.uploadFiles();
        }
	} );

	objUploadView.uploader.bind( 'FileUploaded', function ( up, file, res ) {
		if ( /MSIE (\d+\.\d+);/.test( navigator.userAgent ) ) { //test for MSIE x.x;
			var ieversion = new Number( RegExp.$1 ) // capture x.x portion and store as a number

			if ( ieversion < 10 ) {
				try {
					if ( typeof JSON.parse( res.response ) !== "undefined" )
						res.status = 200;
				}
				catch ( e ) {
				}
			}
		}

		if ( res.status == 200 ) {
			try {
				var objIds = JSON.parse( res.response );
				$.each( objIds, function ( key, val ) {
					activity_attachemnt_ids.push( val );
					if ( $( "#whats-new-form" ).find( "#rtmedia_attached_id_" + val ).length < 1 ) {
						$( "#whats-new-form" ).append( "<input type='hidden' name='rtMedia_attached_files[]' data-mode='rtMedia-update' id='rtmedia_attached_id_" + val + "' value='"
								+ val + "' />" );
					}
				} );
			} catch ( e ) {

			}
			rtMediaHook.call( 'rtmedia_js_after_file_upload', [ up, file, res.response ] );
		}
	} );

	objUploadView.uploader.bind( 'Error', function ( up, err ) {

		if ( err.code == - 600 ) { //file size error // if file size is greater than server's max allowed size
			var tmp_array;
			var ext = tr = '';
			tmp_array = err.file.name.split( "." );
			if ( tmp_array.length > 1 ) {

				ext = tmp_array[tmp_array.length - 1];
				if ( ! ( typeof ( up.settings.upload_size ) != "undefined" && typeof ( up.settings.upload_size[ext] ) != "undefined" && ( up.settings.upload_size[ext]["size"] < 1 || ( up.settings.upload_size[ext]["size"] * 1024 * 1024 ) >= err.file.size ) ) ) {
					rtmedia_selected_file_list( plupload, err.file, up, err );
				}
			}
		}
		else {
			if ( err.code == - 601 ) { // file extension error
				err.message = rtmedia_file_extension_error_msg;
			}

			rtmedia_selected_file_list( plupload, err.file, '', err );
		}

		jQuery( '.plupload_delete' ).on( 'click', function ( e ) {
				e.preventDefault();
				jQuery( this ).parent().parent( 'li' ).remove();
			} );
		
		return false;

	} );

	objUploadView.uploader.bind( 'BeforeUpload', function ( up, files ) {

		$.each( objUploadView.upload_remove_array, function ( i, rfile ) {
			if ( up.getFile( rfile ) )
				up.removeFile( up.getFile( rfile ) );
		} );

		var object = '';
		var item_id = jQuery( "#whats-new-post-in" ).val();
		if ( item_id == undefined )
			item_id = 0;
		if ( item_id > 0 ) {
			object = "group";
		} else {
			object = "profile";
		}

		up.settings.multipart_params.context = object;
		up.settings.multipart_params.context_id = item_id;
		up.settings.multipart_params.title = files.title.split( '.' )[ 0 ];
		// if privacy dropdown is not disabled, then get the privacy value of the update
		if ( jQuery( "select.privacy" ).prop( 'disabled' ) === false ) {
			up.settings.multipart_params.privacy = jQuery( "select.privacy" ).val();
		}
	} );
	objUploadView.uploader.bind( 'UploadComplete', function ( up, files ) {
		media_uploading = true;
		$( "#aw-whats-new-submit" ).click();
		$( '#rtmedia_uploader_filelist li.plupload_queue_li' ).remove();
		//$("#aw-whats-new-submit").removeAttr('disabled');
		window.onbeforeunload = null;
	} );
	objUploadView.uploader.bind( 'UploadProgress', function ( up, file ) {
		//$( "#" + file.id + " .plupload_file_status" ).html( rtmedia_uploading_msg + '( ' + file.percent + '% )' );
		$( "#" + file.id + " .plupload_file_status" ).html( '<div class="plupload_file_progress ui-widget-header" style="width: ' + file.percent + '%;"></div>' );
		$( "#" + file.id ).addClass( 'upload-progress' );
		if ( file.percent == 100 ) {
			$( "#" + file.id ).toggleClass( 'upload-success' );
		}

		window.onbeforeunload = function ( evt ) {
			var message = rtmedia_upload_progress_error_message;
			return message;
		};
	} );

	$( "#rtMedia-start-upload" ).hide();

	var change_flag = false
	var media_uploading = false;
	$.ajaxPrefilter( function ( options, originalOptions, jqXHR ) {
		// Modify options, control originalOptions, store jqXHR, etc
		try {
			if ( originalOptions.data == null || typeof ( originalOptions.data ) == "undefined" || typeof ( originalOptions.data.action ) == "undefined" ) {
				return true;
			}
		} catch ( e ) {
			return true;
		}
		if ( originalOptions.data.action == 'post_update' || originalOptions.data.action == 'activity_widget_filter' ) {
			var temp = activity_attachemnt_ids;
			while ( activity_attachemnt_ids.length > 0 ) {
				options.data += "&rtMedia_attached_files[]=" + activity_attachemnt_ids.pop();
			}
			options.data += "&rtmedia-privacy=" + jQuery( "select.privacy" ).val();
			activity_attachemnt_ids = temp;

			var orignalSuccess = originalOptions.success;
			options.beforeSend = function () {
				if ( originalOptions.data.action == 'post_update' ) {
					if ( $.trim( $( "#whats-new" ).val() ) == "" && objUploadView.uploader.files.length > 0 ) {
						/*
						 *Added $nbsp; as activity text to post activity without TEXT
						 * Disabled TextBox color(transparent)
						 */
						$( "#whats-new").css('color', 'transparent');
						$( "#whats-new" ).val('&nbsp;');
					}
				}
				if ( ! media_uploading && objUploadView.uploader.files.length > 0 ) {
					$( "#whats-new-post-in" ).attr( 'disabled', 'disabled' );
					$( "#rtmedia-add-media-button-post-update" ).attr( 'disabled', 'disabled' );
					objUploadView.uploadFiles()
					media_uploading = true;
					return false;
				} else {
					media_uploading = false;
					return true;
				}


			}
			options.success = function ( response ) {
				orignalSuccess( response );
				if ( response[0] + response[1] == '-1' ) {
					//Error

				} else {
					if ( originalOptions.data.action == 'activity_widget_filter' ) {
						$( "div.activity" ).bind( "fadeIn", function () {
							apply_rtMagnificPopup( jQuery( '.rtmedia-list-media, .rtmedia-activity-container ul.rtmedia-list, #bp-media-list,.widget-item-listing,.bp-media-sc-list, li.media.album_updated ul,ul.bp-media-list-media, li.activity-item div.activity-content div.activity-inner div.bp_media_content' ) );
							rtMediaHook.call( 'rtmedia_js_after_activity_added', [ ] );
						} );
						$( "div.activity" ).fadeIn( 100 );
					}
					jQuery( "input[data-mode=rtMedia-update]" ).remove();
					while ( objUploadView.uploader.files.pop() != undefined ) {
					}
					objUploadView.uploader.refresh();
					$( '#rtmedia-whts-new-upload-container > div' ).css( { 'top': '0', 'left': '0' } );
					$( '#rtMedia-update-queue-list' ).html( '' );
					//$("#div-attache-rtmedia").hide();
					apply_rtMagnificPopup( jQuery( '.rtmedia-list-media, .rtmedia-activity-container ul.rtmedia-list, #bp-media-list,.widget-item-listing,.bp-media-sc-list, li.media.album_updated ul,ul.bp-media-list-media, li.activity-item div.activity-content div.activity-inner div.bp_media_content' ) );
					jQuery( 'ul.activity-list li.rtmedia_update:first-child .wp-audio-shortcode, ul.activity-list li.rtmedia_update:first-child .wp-video-shortcode' ).mediaelementplayer( {
						// if the <video width> is not specified, this is the default
						defaultVideoWidth: 480,
						// if the <video height> is not specified, this is the default
						defaultVideoHeight: 270,
						// if set, overrides <video width>
						//videoWidth: 1,
						// if set, overrides <video height>
						//videoHeight: 1
					} );
					rtMediaHook.call( 'rtmedia_js_after_activity_added', [ ] );
				}
				$( "#whats-new-post-in" ).removeAttr( 'disabled' );
				$( "#rtmedia-add-media-button-post-update" ).removeAttr( 'disabled' );
				// Enabled TextBox color back to normal
				$( "#whats-new").css('color', '');

			}
		}
	} );
} );
/**
 * rtMedia Comment Js
 */
jQuery( document ).ready( function ( $ ) {
	jQuery( document ).on( "click", "#rt_media_comment_form #rt_media_comment_submit", function ( e ) {
		e.preventDefault();
		if ( $.trim( $( "#comment_content" ).val() ) == "" ) {
			rtmedia_single_media_alert_message( rtmedia_empty_comment_msg, 'warning' );
			return false;
		}

		$( this ).attr( 'disabled', 'disabled' );

		$.ajax( {
			url: jQuery( "#rt_media_comment_form" ).attr( "action" ),
			type: 'post',
			data: jQuery( "#rt_media_comment_form" ).serialize() + "&rtajax=true",
			success: function ( data ) {
				$( '#rtmedia-no-comments' ).remove();
				$( "#rtmedia_comment_ul" ).append( data );
				$( "#comment_content" ).val( "" );
				$( "#rt_media_comment_form #rt_media_comment_submit" ).removeAttr( 'disabled' );
				rtMediaHook.call( 'rtmedia_js_after_comment_added', [ ] );
			}
		} );


		return false;
	} );

	//Delete comment
	jQuery( document ).on( 'click', '.rtmedia-delete-comment', function ( e ) {
		e.preventDefault();
		var ask_confirmation = true
		ask_confirmation = rtMediaHook.call( 'rtmedia_js_delete_comment_confirmation', [ ask_confirmation ] );
		if ( ask_confirmation && ! confirm( rtmedia_media_comment_delete_confirmation ) )
			return false;
		var current_comment = jQuery( this );
		var current_comment_parent = current_comment.parent();
		var comment_id = current_comment.data( 'id' );
		current_comment_parent.css( 'opacity', '0.4' );
		if ( comment_id == '' || isNaN( comment_id ) ) {
			return false;
		}
		var action = current_comment.closest( 'ul' ).data( "action" );

		jQuery.ajax( {
			url: action,
			type: 'post',
			data: { comment_id: comment_id },
			success: function ( res ) {
				if ( res != 'undefined' && res == 1 ) {
					current_comment.closest( 'li' ).hide( 1000, function () {
						current_comment.closest( 'li' ).remove();
					} );
				} else {
					current_comment.css( 'opacity', '1' );
				}
				rtMediaHook.call( 'rtmedia_js_after_comment_deleted', [ ] );
			}
		} );

	} );

	$( document ).on( "click", '.rtmedia-like', function ( e ) {
		e.preventDefault();
		var that = this;
		var like_nonce = $(this).siblings('#rtm_media_like_nonce').val();
		$( this ).attr( 'disabled', 'disabled' );
		var url = $( this ).parent().attr( "action" );
		$( that ).prepend( "<img class='rtm-like-loading' src='" + rMedia_loading_file + "' style='width:10px' />" );
		$.ajax( {
			url: url,
			type: 'post',
			data: { json: true, like_nonce: like_nonce },
			success: function ( data ) {
				try {
					data = JSON.parse( data );
				} catch ( e ) {

				}
				$( '.rtmedia-like span' ).html( data.next );
				$( '.rtm-like-loading' ).remove();
				$( that ).removeAttr( 'disabled' );
				//update the like counter
				$( '.rtmedia-like-counter' ).html( data.count );
				if ( data.count > 0 ) {
					$( '.rtmedia-like-info' ).removeClass( 'hide' );
				} else {
					$( '.rtmedia-like-info' ).addClass( 'hide' );
				}
			}
		} );


	} );
	$( document ).on( "click", '.rtmedia-featured, .rtmedia-group-featured', function ( e ) {
		e.preventDefault();
		var that = this;
		$( this ).attr( 'disabled', 'disabled' );
		var featured_nonce = $(this).siblings('#rtm_media_featured_nonce').val();
		var url = $( this ).parent().attr( "action" );
		$( that ).prepend( "<img class='rtm-featured-loading' src='" + rMedia_loading_file + "' />" );
		$.ajax( {
			url: url,
			type: 'post',
			data:  { json:true, featured_nonce:featured_nonce },
			success: function ( data ) {
				try {
					data = JSON.parse( data );
				} catch ( e ) {

				}

				if ( data.nonce ){
					rtmedia_single_media_alert_message( rtmedia_something_wrong_msg, 'warning');
				} else {
					if (data.action) {
						rtmedia_single_media_alert_message(rtmedia_set_featured_image_msg, 'success');
					} else {
						rtmedia_single_media_alert_message(rtmedia_unset_featured_image_msg, 'success');
					}
				}
				$( that ).find( 'span' ).html( data.next );
				$( '.rtm-featured-loading' ).remove();
				$( that ).removeAttr( 'disabled' );
			}
		} );


	} );
	jQuery( "#div-attache-rtmedia" ).find( "input[type=file]" ).each( function () {
		//$(this).attr("capture", "camera");
		// $(this).attr("accept", $(this).attr("accept") + ';capture=camera');

	} );

	// manually trigger fadein event so that we can bind some function on this event. It is used in activity when content getting load via ajax
	var _old_fadein = $.fn.fadeIn;
	jQuery.fn.fadeIn = function () {
		return _old_fadein.apply( this, arguments ).trigger( "fadeIn" );
	};
} );

function rtmedia_selected_file_list( plupload, file, uploader, error ) {
    var icon = '', err_msg = '', upload_progress = '', title = '';
    
    if ( error == '' ) {
		upload_progress = '<div class="plupload_file_progress ui-widget-header" style="width: 0%;">';
		upload_progress += '</div>';
        icon = '<span id="label_' + file.id + '" class="dashicons dashicons-edit rtmicon" title="Edit File Name"></span>';
	} else if ( error.code == - 600 ) {
		err_msg = ( uploader != '' ) ? rtmedia_max_file_msg + uploader.settings.max_file_size :  window.file_size_info;
        title = "title='" + err_msg + "'";
        icon = '<i class="dashicons dashicons-info rtmicon" ' + title + '></i>';
	} else if ( error.code == - 601 ) {
		err_msg = error.message + '. ' + window.file_extn_info;
        title = "title='" + err_msg + "'";
        icon = '<i class="dashicons dashicons-info rtmicon" ' + title + '></i>';
	}
    
	var rtmedia_plupload_file = '<li class="plupload_file ui-state-default plupload_queue_li" id="' + file.id + '" ' + title + '>';
	rtmedia_plupload_file += '<div id="file_thumb_' + file.id + '" class="plupload_file_thumb">';
	rtmedia_plupload_file += '</div>';
	rtmedia_plupload_file += '<div class="plupload_file_status">';
    rtmedia_plupload_file += upload_progress;
	rtmedia_plupload_file += '</div>';
	rtmedia_plupload_file += '<div class="plupload_file_name" title="' + ( file.name ? file.name : '' ) + '">';
	rtmedia_plupload_file += '<span class="plupload_file_name_wrapper">';
	rtmedia_plupload_file += ( file.name ? file.name : '' );
	rtmedia_plupload_file += '</span>';
    rtmedia_plupload_file += icon;
	rtmedia_plupload_file += '</div>';
	rtmedia_plupload_file += '<div class="plupload_file_action">';
	rtmedia_plupload_file += '<div class="plupload_action_icon ui-icon plupload_delete">';
	rtmedia_plupload_file += '<span class="remove-from-queue dashicons dashicons-dismiss"></span>';
	rtmedia_plupload_file += '</div>';
	rtmedia_plupload_file += '</div>';
	rtmedia_plupload_file += '<div class="plupload_file_size">';
	rtmedia_plupload_file += plupload.formatSize( file.size );
	rtmedia_plupload_file += '</div>';
	rtmedia_plupload_file += '<div class="plupload_file_fields">';
	rtmedia_plupload_file += '</div>';
	rtmedia_plupload_file += '</li>';

	jQuery( rtmedia_plupload_file ).appendTo( '#rtmedia_uploader_filelist' );
	var type = file.type;
    var media_title = file.name;
    var ext = media_title.substring( media_title.lastIndexOf( "." ) + 1, media_title.length );

	if ( /image/i.test( type ) ) {
        if( ext === 'gif' ) {
            jQuery( '<img src="' + rtmedia_media_thumbs[ 'photo' ] + '" />' ).appendTo( '#file_thumb_' + file.id );
        } else {
            var img = new mOxie.Image();

            img.onload = function () {
                this.embed( jQuery( '#file_thumb_' + file.id ).get( 0 ), {
                    width: 100,
                    height: 60,
                    crop: true
                } );
            };

            img.onembedded = function () {
                this.destroy();
            };

            img.onerror = function () {
                this.destroy();
            };

            img.load( file.getSource() );
        }
	} else {        
        jQuery.each( rtmedia_exteansions, function( key, value ) {
            if( value.indexOf( ext ) >= 0 ) {
                jQuery( '<img src="' + rtmedia_media_thumbs[ key ] + '" />' ).appendTo( '#file_thumb_' + file.id );
                
                return false;
            }
        } );
    }
    
}

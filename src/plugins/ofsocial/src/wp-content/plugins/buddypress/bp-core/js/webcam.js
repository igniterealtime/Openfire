/* global bp, BP_Uploader, _, Backbone */

window.bp = window.bp || {};

( function() {

	// Bail if not set
	if ( typeof BP_Uploader === 'undefined' ) {
		return;
	}

	bp.Models      = bp.Models || {};
	bp.Collections = bp.Collections || {};
	bp.Views       = bp.Views || {};

	bp.WebCam = {
		start: function() {
			this.params = {
				video:          null,
				videoStream:    null,
				capture_enable: false,
				capture:        null,
				canvas:         null,
				warning:        null,
				flipped:        false
			};

			bp.Avatar.nav.on( 'bp-avatar-view:changed', _.bind( this.setView, this ) );
		},

		setView: function( view ) {
			if ( 'camera' !== view ) {
				// Stop the camera if needed
				if ( ! _.isNull( this.params.video ) ) {
					this.stop();

					// Remove all warnings as we're changing the view
					this.removeWarning();
				}

				// Stop as this is not Camera area
				return;
			}

			// Create the WebCam view
			var cameraView = new bp.Views.WebCamAvatar( { model: new Backbone.Model( { user_media: false } ) } );

			// Make sure the flipped param is reset
			this.params.flipped = false;

			// Add it to views
			bp.Avatar.views.add( { id: 'camera', view: cameraView } );

			// Display it
	        cameraView.inject( '.bp-avatar' );
		},

		removeView: function() {
			var camera;

			if ( ! _.isUndefined( bp.Avatar.views.get( 'camera' ) ) ) {
				camera = bp.Avatar.views.get( 'camera' );
				camera.get( 'view' ).remove();
				bp.Avatar.views.remove( { id: 'camera', view: camera } );
			}
		},

		gotStream: function( stream ) {
			var video = bp.WebCam.params.video;
			bp.WebCam.params.videoStream = stream;

			// User Feedback
			bp.WebCam.displayWarning( 'loaded' );

			video.onerror = function () {
				// User Feedback
				bp.WebCam.displayWarning( 'videoerror' );

				if ( video ) {
					bp.WebCam.stop();
				}
			};

			stream.onended = bp.WebCam.noStream();

			if ( video.mozSrcObject !== undefined ) {
				video.mozSrcObject = stream;
				video.play();
			} else if ( navigator.mozGetUserMedia ) {
				video.src = stream;
				video.play();
			} else if ( window.URL ) {
				video.src = window.URL.createObjectURL( stream );
			} else {
				video.src = stream;
			}

			bp.WebCam.params.capture_enable = true;
		},

		stop: function() {
			bp.WebCam.params.capture_enable = false;
			if ( bp.WebCam.params.videoStream ) {
				if ( bp.WebCam.params.videoStream.stop ) {
					bp.WebCam.params.videoStream.stop();
				} else if ( bp.WebCam.params.videoStream.msStop ) {
					bp.WebCam.params.videoStream.msStop();
				}
				bp.WebCam.params.videoStream.onended = null;
				bp.WebCam.params.videoStream = null;
			}
			if ( bp.WebCam.params.video ) {
				bp.WebCam.params.video.onerror = null;
				bp.WebCam.params.video.pause();
				if ( bp.WebCam.params.video.mozSrcObject ) {
					bp.WebCam.params.video.mozSrcObject = null;
				}
				bp.WebCam.params.video.src = '';
			}
		},

		noStream: function() {
			if ( _.isNull( bp.WebCam.params.videoStream ) ) {
				// User Feedback
				bp.WebCam.displayWarning( 'noaccess' );

				bp.WebCam.removeView();
			}
		},

		setAvatar: function( avatar ) {
			if ( ! avatar.get( 'url' ) ) {
				bp.WebCam.displayWarning( 'nocapture' );
			}

			// Remove the view
			bp.WebCam.removeView();

			bp.Avatar.setAvatar( avatar );
		},

		removeWarning: function() {
			if ( ! _.isNull( this.params.warning ) ) {
				this.params.warning.remove();
			}
		},

		displayWarning: function( code ) {
			this.removeWarning();

			this.params.warning = new bp.Views.uploaderWarning( {
				value: BP_Uploader.strings.camera_warnings[code]
			} );

			this.params.warning.inject( '.bp-avatar-status' );
		}
	};

	// BuddyPress WebCam view
	bp.Views.WebCamAvatar = bp.View.extend( {
		tagName: 'div',
		id: 'bp-webcam-avatar',
		template: bp.template( 'bp-avatar-webcam' ),

		events: {
			'click .avatar-webcam-capture': 'captureStream',
			'click .avatar-webcam-save': 'saveCapture'
		},

		initialize: function() {
			var params;

			if ( navigator.getUserMedia || navigator.oGetUserMedia || navigator.mozGetUserMedia || navigator.webkitGetUserMedia || navigator.msGetUserMedia ) {

				// We need to add some cropping stuff to use bp.Avatar.setAvatar()
				params = _.extend( _.pick( BP_Uploader.settings.defaults.multipart_params.bp_params,
					'object',
					'item_id',
					'nonces'
					), {
						user_media:  true,
						w: BP_Uploader.settings.crop.full_w,
						h: BP_Uploader.settings.crop.full_h,
						x: 0,
						y: 0,
						type: 'camera'
					}
				);

				this.model.set( params );
			}

			this.on( 'ready', this.useStream, this );
		},

		useStream:function() {
			// No support for user media... Stop!
			if ( ! this.model.get( 'user_media' ) ) {
				return;
			}

			this.options.video = new bp.Views.WebCamVideo();
			this.options.canvas = new bp.Views.WebCamCanvas();

			this.$el.find( '#avatar-to-crop' ).append( this.options.video.el );
			this.$el.find( '#avatar-crop-pane' ).append( this.options.canvas.el );

			bp.WebCam.params.video = this.options.video.el;
			bp.WebCam.params.canvas = this.options.canvas.el;

			// User Feedback
			bp.WebCam.displayWarning( 'requesting' );

			if ( navigator.getUserMedia ) {
				navigator.getUserMedia( { video:true }, bp.WebCam.gotStream, bp.WebCams.noStream );
			}  else if ( navigator.oGetUserMedia ) {
				navigator.oGetUserMedia( { video:true }, bp.WebCam.gotStream, bp.WebCam.noStream );
			} else if ( navigator.mozGetUserMedia ) {
				navigator.mozGetUserMedia( { video:true }, bp.WebCam.gotStream, bp.WebCam.noStream );
			} else if ( navigator.webkitGetUserMedia ) {
				navigator.webkitGetUserMedia( { video:true }, bp.WebCam.gotStream, bp.WebCam.noStream );
			} else if (navigator.msGetUserMedia) {
				navigator.msGetUserMedia( { video:true, audio:false }, bp.WebCams.gotStream, bp.WebCam.noStream );
			} else {
				// User Feedback
				bp.WebCam.displayWarning( 'errormsg' );
			}
		},

		captureStream: function( event ) {
			var sx, sc;
			event.preventDefault();

			if ( ! bp.WebCam.params.capture_enable ) {
				// User Feedback
				bp.WebCam.displayWarning( 'loading' );
				return;
			}

			if ( this.model.get( 'h' ) > this.options.video.el.videoHeight || this.model.get( 'w' ) > this.options.video.el.videoWidth ) {
				bp.WebCam.displayWarning( 'videoerror' );
				return;
			}

			// Set the offset
			sc = this.options.video.el.videoHeight;
			sx = ( this.options.video.el.videoWidth - sc ) / 2;

			// Flip only once.
			if ( ! bp.WebCam.params.flipped ) {
				this.options.canvas.el.getContext( '2d' ).translate( this.model.get( 'w' ), 0 );
				this.options.canvas.el.getContext( '2d' ).scale( -1, 1 );
				bp.WebCam.params.flipped = true;
			}

			this.options.canvas.el.getContext( '2d' ).drawImage( this.options.video.el, sx, 0, sc, sc, 0, 0, this.model.get( 'w' ), this.model.get( 'h' ) );
			bp.WebCam.params.capture = this.options.canvas.el.toDataURL( 'image/png' );
			this.model.set( 'url', bp.WebCam.params.capture );

			// User Feedback
			bp.WebCam.displayWarning( 'ready' );
		},

		saveCapture: function( event ) {
			event.preventDefault();

			if ( ! bp.WebCam.params.capture ) {
				// User Feedback
				bp.WebCam.displayWarning( 'nocapture' );
				return;
			}

			bp.WebCam.stop();
			bp.WebCam.setAvatar( this.model );
		}
	} );

	// BuddyPress Video stream view
	bp.Views.WebCamVideo = bp.View.extend( {
		tagName: 'video',
		id: 'bp-webcam-video',
		attributes: {
			autoplay: 'autoplay'
		}
	} );

	// BuddyPress Canvas (capture) view
	bp.Views.WebCamCanvas = bp.View.extend( {
		tagName: 'canvas',
		id: 'bp-webcam-canvas',
		attributes: {
			width:  150,
			height: 150
		},

		initialize: function() {
			// Make sure to take in account bp_core_avatar_full_height or bp_core_avatar_full_width php filters
			if ( ! _.isUndefined( BP_Uploader.settings.crop.full_h ) && ! _.isUndefined( BP_Uploader.settings.crop.full_w ) ) {
				this.el.attributes.width.value  = BP_Uploader.settings.crop.full_w;
				this.el.attributes.height.value = BP_Uploader.settings.crop.full_h;
			}
		}
	} );

	bp.WebCam.start();

})( bp, jQuery );

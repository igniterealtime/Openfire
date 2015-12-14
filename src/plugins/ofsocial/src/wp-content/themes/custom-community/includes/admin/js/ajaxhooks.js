/*!
 * jQuery ajaxHooks v1.0.0pre
 *
 * Copyright 2012, Julian Aubourg
 * licensed under the MIT license
 *
 * Date: 2014/06/20 12:36:49
 * Manuel build by @author Fabian Wolf (@link http://github.com/ginsterbusch)
 * 
 */
(function( jQuery, undefined ) {
var cssHead = document.head || jQuery( "head" )[ 0 ] || document.documentElement,
	cssEmptyURL = "data:text/css,",
	cssNeedsPolling = jQuery.Deferred(function( defer ) {
		var onload = cssLoad( { url: cssEmptyURL }, false, function() {
				defer.resolve( false );
			});
		// Give a little room
		// so that we get the onload event
		setTimeout(function() {
			onload( true );
			defer.resolve( true );
		}, 0 );
	}),
	cssTimer,
	cssPollingId = 0,
	cssPollingNb = 0,
	cssPolled = {};

function cssGlobalPoller() {
	var id, sheet;
	for( id in cssPolled ) {
		sheet = cssPolled[ id ].sheet;
		try {
			if ( sheet && !sheet.cssRules /* <= webkit */ || sheet.cssRules.length != null /* <= firefox */ ) {
				cssPolled[ id ].onload();
			}
		} catch( e ) {
			if ( ( e.code === 1e3 ) || ( e.message === "security" || e.message === "denied" ) ) {
				cssPolled[ id ].onload();
			}
		}
	}
}

function cssLoad( s, polling, callback ) {
	var id,
		link = jQuery( "<link/>", {
			charset: s.scriptCharset || "",
			media: s.media || "all",
			rel: "stylesheet",
			type: "text/css"
		}).appendTo( cssHead )[ 0 ];
	if ( polling ) {
		cssPolled[( id = ( cssPollingId++ ) )] = link;
		if ( !( cssPollingNb++ ) ) {
			cssTimer = setInterval( cssGlobalPoller, 13 );
		}
	}
	link.onload = function( isCancel ) {
		if ( link.onload ) {
			if ( polling ) {
				delete cssPolled[ id ];
				if ( !( --cssPollingNb ) ) {
					clearInterval( cssTimer );
				}
			}
			link.onload = null;
			if ( isCancel === true ) {
				// resetting href avoids a crash in IE6
				// and a display bug in webkit
				// Using "data:text/css," ensures webkit doesn't do
				// an unnecessary network request
				link.href = cssEmptyURL;
				cssHead.removeChild( link );
			} else {
				callback( link );
			}
		}
	};
	// Only way to make it work in IE before the DOM
	// is ready is to set href after appending
	link.href = s.url;
	return link.onload;
}

jQuery.ajaxPrefilter( "css", function( s ) {
	if ( s.cache === undefined ) {
		s.cache = false;
	}
	s.type = "GET";
	s.async = true;
	s.global = false;
});

jQuery.ajaxTransport( "css", function( s ) {
	var cancel;
	return {
		send: function( _ , complete ) {
			cancel = jQuery.noop;
			cssNeedsPolling.done(function( needsPolling ) {
				if ( cancel ) {
					cancel = cssLoad( s, needsPolling, function( link ) {
						cancel = undefined;
						complete( 200, "OK", { css: jQuery( link ) } );
					});
				}
			});
		},
		abort: function() {
			if ( cancel ) {
				var tmp = cancel;
				cancel = undefined;
				tmp( true );
			}
		}
	};
} );
jQuery.ajaxPrefilter( "img", function( s ) {
	if ( s.cache == null ) {
		s.cache = false;
	}
	s.type = "GET";
	s.async = true;
});

jQuery.ajaxTransport( "img", function( s ) {
	var callback;
	return {
		send: function( _, complete ) {
			var image = new Image();
			callback = function( success ) {
				var img = image;
				callback = image = image.onload = image.onerror = null;
				if ( success != null ) {
					if ( success ) {
						complete( 200, "OK", { img: img } );
					} else {
						complete( 404, "Not Found" );
					}
				} else {
					img.src = null;
				}
			};
			image = new Image();
			image.onload = function() {
				callback( true );
			};
			image.onerror = function() {
				callback( false );
			};
			image.src = s.url;
		},
		abort: function() {
			if ( callback ) {
				callback();
			}
		}
	};
});
function laxjson( data ) {
	return ( new Function( "return (" + data + ");" ) )();
}

jQuery.ajaxPrefilter(function( options ) {
	if ( options.laxjson ) {
		options.converters[ "text json" ] = laxjson;
	}
});
var pagecache = {};

jQuery.ajaxPrefilter(function( options ) {
	if ( options.type === "GET" && options.cache && ( typeof options.cache === "string" ) ) {
		options.global = false;
		options._url = options.url;
		return "pagecache";
	}
});

jQuery.ajaxTransport( "pagecache", function( options, originalOptions, jqXHR ) {
	// Remove redirection dataType
	options.dataTypes.shift();

	var key, cached;
	return {
		send: function( headers, complete ) {
			key = options.cache + " " + options.url;
			cached = pagecache[ key ] || ( pagecache[ key ] = jQuery.ajax( jQuery.extend( originalOptions, {
				url: options._url,
				beforeSend: null,
				cache: true,
				dataType: options.cache,
				headers: headers,
				success: null,
				error: null,
				complete: null,
				timeout: 0
			}) ) );
			cached.always(function( success ) {
				if ( cached ) {
					var responses;
					if ( cached.state() === "resolved" ) {
						responses = {};
						responses[ options.cache ] = success;
					}
					complete( cached.status, cached.statusText, responses, cached.getAllResponseHeaders() );
				}
			});
		},
		abort: function() {
			cached = undefined;
		}
	};
});
jQuery.Deferred = (function( Deferred ) {

	var methods = {
			done: "resolve",
			fail: "reject",
			progress: "notify"
		},
		slice = [].slice;

	// Takes a callback and embeds it into a Promise/A enabler
	// To be consumed by jQuery's implementation
	function catchAndReject( targetDefer, fn, toResolve ) {
		return function( value ) {
			// No need to do anything if already resolved/rejected
			if ( targetDefer.state() !== "pending" ) {
				return;
			}
			// Call the function and get value or error
			var success, error;
			try {
				success = fn( value );
			} catch( e ) {
				error = e;
			}
			// Choose the correct method to call on the Deferred
			// and call it
			(
				error ?
				targetDefer.reject :
				(
					toResolve ?
					targetDefer.resolve :
					targetDefer.notify
				)
			)( error || success );
		};
	}

	function then( defer ) {
		return function() {
			var redirect = jQuery.Deferred(),
				i = 0, method, fn;
			for ( method in methods ) {
				fn = arguments[ i ];
				defer[ method ]( fn && jQuery.isFunction( fn ) ?
					( catchAndReject( redirect, fn, i < 2 ) ) :
					redirect[ methods[ method ] ]
				);
				i++;
			}
			return redirect.promiseA();
		};
	}

	return function( fn ) {
		var defer = Deferred(),
			promise = {
				then: then( defer ),
				get: function( key ) {
					return promise.then(function( data ) {
						return data[ key ];
					});
				},
				call: function( key ) {
					var args = slice.call( arguments, 1 );
					return promise.then(function( data ) {
						return data[ key ].apply( data, args );
					});
				}
			};
		defer.promiseA = function() {
			return promise;
		};
		if ( fn ) {
			fn.call( defer, defer );
		}
		return defer;
	};

})( jQuery.Deferred );
jQuery.ajaxPrefilter(function( options, originalOptions, jqXHR ) {
	if ( options.retry ) {
		var url = options.url,
			oldPromise = jqXHR.promise();
		if ( options._retried ) {
			options.success = options.error = options.complete = undefined;
		} else {
			originalOptions = jQuery.extend( {}, originalOptions, { _retried: true } );
		}
		jqXHR.pipe( null, function() {
			if ( options.retry.call( options.context || options, originalOptions, jqXHR ) ) {
				return jQuery.ajax( originalOptions.url || url, originalOptions );
			}
			return oldPromise;
		}).promise( jqXHR );
		if ( jqXHR.success ) {
			jqXHR.success = jqXHR.done;
			jqXHR.error = jqXHR.fail;
			jqXHR.complete = (function( callbacks ) {
				jqXHR.always(function( error, statusText, success ) {
					callbacks.fireWith( this, [ jqXHR.state() === "resolved" ? success : error ] );
				});
				return callbacks.add;
			})( jQuery.Callbacks( "once memory" ) );
		}
	}
});
var topics = {},
	topicSlice = [].slice;

jQuery.Topic = function( id ) {
    var callbacks,
        method,
        topic = id && topics[ id ];
    if ( !topic ) {
        callbacks = jQuery.Callbacks();
        topic = {
            publish: callbacks.fire,
            subscribe: callbacks.add,
            unsubscribe: callbacks.remove
        };
        if ( id ) {
            topics[ id ] = topic;
        }
    }
    return topic;
};

jQuery.each( jQuery.Topic(), function( method ) {
	jQuery[ method ] = function( topic ) {
		topic = jQuery.Topic( topic );
		topic[ method ].apply( topic, topicSlice.call( arguments, 1 ) );
	};
});
jQuery.ajaxPrefilter(function( options, originalOptions, jqXHR ) {
	var validate = options.validate;
	if ( validate && jQuery.isFunction( validate ) ) {
		var context = options.context || options,
			error;
		try {
			error = validate.call( context, originalOptions.data, options );
		} catch( e ) {
			error = e;
		}
		if ( error ) {
			jQuery.Deferred().rejectWith( context, [ jqXHR, "validationerror", error ] ).promise( jqXHR );
			if ( jqXHR.success ) {
				jqXHR.success = jqXHR.done;
				jqXHR.error = jqXHR.fail;
				jqXHR.complete = jQuery.Callbacks( "once memory" ).fireWith( context, [ jqXHR, "validationerror" ] ).add;
			}
			jqXHR.abort();
			jqXHR.fail( options.error );
			jqXHR.complete( options.complete );
		}
	}
});
jQuery.each( "when whenever".split( " " ), function( _, method ) {
	jQuery[ method + "All" ] = function( iterable, callback ) {
		return jQuery[ method ].apply( jQuery, jQuery.map( iterable, callback ) );
	};
});
jQuery.each( "when whenever".split(" "), function( _, method ) {
	jQuery[ method + "Array" ] = function( array ) {
		return jQuery[ method ].apply( jQuery, array || [] );
	};
});
var wheneverSlice = [].slice;

jQuery.whenever = function( subordinate /* , ..., subordinateN */ ) {
	var i = 0,
		resolveValues = wheneverSlice.call( arguments ),
		length = resolveValues.length || 1,

		// the count of uncompleted subordinates
		remaining = length,

		// the master Deferred
		deferred = jQuery.Deferred(),

		// Update function for both resolve and progress values
		updateFunc = function( i, contexts, values, state ) {
			return function( value ) {
				contexts[ i ] = this;
				values[ i ] = arguments.length > 1 ? wheneverSlice.call( arguments ) : value;
				if ( state ) {
					values[ i ] = {
						state: state,
						value: values[ i ]
					};
					if ( !( --remaining ) ) {
						if ( length === 1 ) {
							deferred.resolveWith( contexts[ 0 ], [ values[ 0 ] ] );
						} else {
							deferred.resolveWith( contexts, values );
						}
					}
				} else {
					deferred.notifyWith( contexts, values );
				}
			};
		},

		progressValues = new Array( length ),
		progressContexts = new Array( length ),
		resolveContexts = new Array( length );

	// add listeners to Deferred subordinates; treat others as resolved
	for ( ; i < length; i++ ) {
		if ( resolveValues[ i ] && jQuery.isFunction( resolveValues[ i ].promise ) ) {
			resolveValues[ i ].promise()
				.progress( updateFunc( i, progressContexts, progressValues ) )
				.done( updateFunc( i, resolveContexts, resolveValues, "resolved" ) )
				.fail( updateFunc( i, resolveContexts, resolveValues, "rejected" ) );
		} else {
			updateFunc( i, resolveContexts, resolveValues, "resolved" )( resolveValues[ i ] );
		}
	}

	return deferred.promise();
};
if ( window.XDomainRequest ) {
	jQuery.ajaxTransport(function( s ) {
		if ( s.crossDomain && s.async ) {
			if ( s.timeout ) {
				s.xdrTimeout = s.timeout;
				delete s.timeout;
			}
			var xdr;
			return {
				send: function( _, complete ) {
					function callback( status, statusText, responses, responseHeaders ) {
						xdr.onload = xdr.onerror = xdr.ontimeout = jQuery.noop;
						xdr = undefined;
						complete( status, statusText, responses, responseHeaders );
					}
					xdr = new XDomainRequest();
					xdr.onload = function() {
						callback( 200, "OK", { text: xdr.responseText }, "Content-Type: " + xdr.contentType );
					};
					xdr.onerror = function() {
						callback( 404, "Not Found" );
					};
					xdr.onprogress = jQuery.noop;
					xdr.ontimeout = function() {
						callback( 0, "timeout" );
					};
					xdr.timeout = s.xdrTimeout || Number.MAX_VALUE;
					xdr.open( s.type, s.url );
					xdr.send( ( s.hasContent && s.data ) || null );
				},
				abort: function() {
					if ( xdr ) {
						xdr.onerror = jQuery.noop;
						xdr.abort();
					}
				}
			};
		}
	});
}
})( jQuery );

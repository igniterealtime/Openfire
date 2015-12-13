/*!
 * jQuery plugin: autoCompletefb(AutoComplete Facebook)
 * @requires jQuery v1.2.2 or later
 * using plugin:jquery.autocomplete.js
 *
 * Credits:
 * - Idea: Facebook
 * - Guillermo Rauch: Original MooTools script
 * - InteRiders <http://interiders.com/>
 *
 * Copyright (c) 2008 Widi Harsojo <wharsojo@gmail.com>, http://wharsojo.wordpress.com/
 * Dual licensed under the MIT and GPL licenses:
 *   http://www.opensource.org/licenses/mit-license.php
 *   http://www.gnu.org/licenses/gpl.html
 */

jQuery.fn.autoCompletefb = function(options)
{
	var tmp = this;
	var settings =
	{
		ul         : tmp,
		urlLookup  : [""],
		acOptions  : {},
		foundClass : ".friend-tab",
		inputClass : ".send-to-input"
	}

	if(options) jQuery.extend(settings, options);

	var acfb =
	{
		params  : settings,
		removeFind : function(o){
			acfb.removeUsername(o);
			jQuery(o).unbind('click').parent().remove();
			jQuery(settings.inputClass,tmp).focus();
			return tmp.acfb;
		},
		removeUsername: function(o){
			var newID = o.parentNode.id.substr( o.parentNode.id.indexOf('-')+1 );
			jQuery('#send-to-usernames').removeClass(newID);
		}
	}

	jQuery(settings.foundClass+" img.p").click(function(){
		acfb.removeFind(this);
	});

	jQuery(settings.inputClass,tmp).autocomplete(settings.urlLookup,settings.acOptions);
	jQuery(settings.inputClass,tmp).result(function(e,d,f){
		var f = settings.foundClass.replace(/\./,'');
		var d = String(d).split(' (');
		var un = d[1].substr(0, d[1].length-1);
		
		/* Don't add the same user multiple times */
		if( 0 === jQuery(settings.inputClass).siblings('#un-' + un).length ) {		
			var ln = '#link-' + un;
			var l = jQuery(ln).attr('href');
			var v = '<li class="'+f+'" id="un-'+un+'"><span><a href="'+l+'">'+d[0]+'</a></span> <span class="p">X</span></li>';
			
			var x = jQuery(settings.inputClass,tmp).before(v);
			jQuery('#send-to-usernames').addClass(un);
				
			jQuery('.p',x[0].previousSibling).click(function(){
				acfb.removeFind(this);
			});
		} 
			
		jQuery(settings.inputClass,tmp).val('');

	});

	jQuery(settings.inputClass,tmp).focus();
	return acfb;
}

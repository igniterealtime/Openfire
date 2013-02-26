jQuery.noConflict();

function searchSuggest(object) {
	document.getElementById('search_suggest' + object).innerHTML = '';
	var str = escape(document.getElementById('groupSearch' + object).value);
	jQuery("#ajaxloading" + object).html("<img src=\"images/ajax-loader.gif\">");
	var request = new http();
	var f = function(obj) {
		var ss = document.getElementById('search_suggest' + object);
		var resp = obj.responseXML.documentElement.getElementsByTagName('item');
		if (resp.length > 0) {
			for ( var i = 0; i < resp.length; i++) {
				var suggest = '<div onmouseover="javascript:suggestOver(this);" ';
				suggest += 'onmouseout="javascript:suggestOut(this);" ';
				suggest += 'onclick="javascript:setSearch(this.innerHTML,'
						+ object + ');" ';
				suggest += 'class="suggest_link">' + resp[i].textContent
						+ '</div>';
				ss.innerHTML += suggest;
			}
			jQuery("#search_suggest" + object).show("slow");
		} else {
			jQuery("#search_suggest" + object).hide("slow");
		}
	};
	request.callback = f;
	request.load('groups?search=' + str);
}

// Mouse over function
function suggestOver(div_value) {
	div_value.className = 'suggest_link_over';
}
// Mouse out function
function suggestOut(div_value) {
	div_value.className = 'suggest_link';
}
// Click function
function setSearch(value, object) {
	document.getElementById("groupSearch" + object).value = value;
	// document.getElementById('search_suggest').innerHTML = '';
	jQuery("#search_suggest" + object).hide("slow");
	checkIfExists(value, object);
}

function checkIfExists(value, object) {
	var str = value;
	var request = new http();
	var f = function(obj) {
		var ss = document.getElementById('search_suggest' + object);
		var resp = obj.responseXML.documentElement.getElementsByTagName('item');
		for ( var i = 0; i < resp.length; i++) {
			var stri = String(str);
			var respStr = String(resp[i].textContent);
			if (stri == respStr) {
				jQuery("#ajaxloading" + object).html(
						"<img src=\"images/correct-16x16.png\">");
				break;
			}
		}
	};
	request.callback = f;
	request.load('groups?search=' + str);
}

jQuery(document).ready(function() {
	jQuery(".browser-data").horizontalBarGraph({
		interval : 1
	});
	var i = 0;
	while (jQuery('#logiq' + i).html() != null) {
		var iqs = jQuery('#logiq' + i).html();
		var msg = jQuery('#logmsg' + i).html();
		var roster = jQuery('#logroster' + i).html();
		var presence = jQuery('#logpresence' + i).html();

		var data = [ {
			label : "IQ",
			data : parseInt(iqs)
		}, {
			label : "Messages",
			data : parseInt(msg)
		}, {
			label : "Roster",
			data : parseInt(roster)
		}, {
			label : "Presence",
			data : parseInt(presence)
		} ];

		jQuery.plot(jQuery("#pie" + i), data, {
			series : {
				pie : {
					show : true,
					radius : 1,
					offset : {
						left : 10	
					}
				}
			},
			legend : {
				show : true
			},
			grid : {
				hoverable : true,
				clickable : true
			}
		});

		++i;
	}
})

function slideToggle(value) {
	jQuery(value).slideToggle();
}

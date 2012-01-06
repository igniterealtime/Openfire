function searchSuggest(object) {
	document.getElementById('search_suggest' + object).innerHTML = '';
	var str = escape(document.getElementById('groupSearch' + object).value);
	$("#ajaxloading" + object).html("<img src=\"images/ajax-loader.gif\">");
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
			$("#search_suggest" + object).show("slow");
		} else {
			$("#search_suggest" + object).hide("slow");
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
	$("#search_suggest" + object).hide("slow");
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
				$("#ajaxloading" + object).html(
						"<img src=\"images/correct-16x16.png\">");
				break;
			}
		}
	};
	request.callback = f;
	request.load('groups?search=' + str);
}

$(document).ready(function() {
	$(".browser-data").horizontalBarGraph({
		interval : 1
	});
	var i = 0;
	while ($('#logiq' + i).html() != null) {
		var iqs = $('#logiq'+i).html();
		var msg = $('#logmsg'+i).html();
		var roster = $('#logroster'+i).html();
		var presence = $('#logpresence'+i).html();

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


		
		$.plot($("#pie"+i), data, 
				{
					series: {
						pie: { 
							show: true,
							radius: 1,
							label: {
								show: true,
								radius: 3/5,
								formatter: function(label, series){
									return '<div style="font-size:8pt;text-align:center;padding:2px;color:white;background-color:rgba(100,100,100,0.5);">'+label+'<br/>'+Math.round(series.percent)+'%</div>';
								},
								background: { 
									opacity: 0.5,
									color: 'gray'
								}
							}
						}
					},
					legend: {
						show: false
					}
				});

		++i;
	}
})

function slideToggle(value) {
	$(value).slideToggle();
}

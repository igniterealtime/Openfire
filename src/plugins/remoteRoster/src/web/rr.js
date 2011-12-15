function searchSuggest(object) {
	document.getElementById('search_suggest'+object).innerHTML = '';
	var str = escape(document.getElementById('groupSearch'+object).value);
		$("#ajaxloading"+object).html("<img src=\"images/ajax-loader.gif\">");
		var request = new http();
		var f = function(obj) {
			console.log(obj);
			var ss = document.getElementById('search_suggest'+object);
			var resp = obj.responseXML.documentElement
					.getElementsByTagName('item');
			if (resp.length > 0) {
				for ( var i = 0; i < resp.length; i++) {
					console.log(resp[i].textContent);
					var suggest = '<div onmouseover="javascript:suggestOver(this);" ';
					suggest += 'onmouseout="javascript:suggestOut(this);" ';
					suggest += 'onclick="javascript:setSearch(this.innerHTML,'+object+');" ';
					suggest += 'class="suggest_link">' + resp[i].textContent
							+ '</div>';
					ss.innerHTML += suggest;
				}
				$("#search_suggest"+object).show("slow");
			} else {
				$("#search_suggest"+object).hide("slow");
			}	
		};
		request.callback = f;
		request.load('searchGroup.jsp?search=' + str);
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
	document.getElementById("groupSearch"+object).value = value;
	// document.getElementById('search_suggest').innerHTML = '';
	$("#search_suggest"+object).hide("slow");
	checkIfExists(value,object);
}

function checkIfExists(value, object) {
	var str = value;
	var request = new http();
	var f = function(obj) {
		console.log(obj);
		var ss = document.getElementById('search_suggest'+object);
		var resp = obj.responseXML.documentElement.getElementsByTagName('item');
		for ( var i = 0; i < resp.length; i++) {
			var stri = String(str);
			var respStr = String(resp[i].textContent);
			if (stri == respStr) {
				$("#ajaxloading"+object)
						.html("<img src=\"images/correct-16x16.png\">");
				break;
			}
		}
	};
	request.callback = f;
	request.load('searchGroup.jsp?search=' + str);
}

$(document).ready(function () {
	console.log("bind da");
	var myvalues = [10,8,5,7,4,4,1];
    $('#inlinesparkline0').sparkline(); 
   
    
})

function slideToggle(value)
{
	$(value).slideToggle();
}

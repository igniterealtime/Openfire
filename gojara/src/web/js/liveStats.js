var dataIq = [];
var dataMsg = [];
var dataPres = [];
var dataRost = [];
var id = 0;
var lastTimestamp = 0;
var rowCounter = 0;
var limit = 50;

$(document).ready(function() {
	drawGraph();
	$('#tableLimit').val(limit);
	window.setInterval("pollStats()", 1000);

	$('#formLimit').submit(function(e) {
		e.preventDefault();
		console.log(e);
		limit = $('#tableLimit').val();
	});
})

function drawGraph() {
	var options = {
		lines : {
			show : true
		},
		points : {
			show : false
		},
		yaxis : {
			min : 0
		},
		xaxis : {
			show : false
		},
		grid : {
			backgroundColor : {
				colors : [ "#fff", "lightgray" ]
			}
		}
	};
	var placeholder = $(".graph");

	if (dataIq.length > 50) {
		dataIq = dataIq.slice(dataIq.length - 50, dataIq.length - 1);
		dataMsg = dataMsg.slice(dataMsg.length - 50, dataMsg.length - 1);
		dataPres = dataPres.slice(dataPres.length - 50, dataPres.length - 1);
		dataRost = dataRost.slice(dataRost.length - 50, dataRost.length - 1);
	}

	$.plot($(".graph"), [ {
		label : "IQ",
		data : dataIq
	}, {
		label : "Messages",
		data : dataMsg
	}, {
		label : "Roster",
		data : dataRost
	}, {
		label : "Presence",
		data : dataPres
	} ], options);
}

function pollStats() {
	var firstDate = $('#logSince').html();
	if (lastTimestamp > 0) {
		firstDate = lastTimestamp;
	}
	updateData(firstDate);
}

// Read a page's GET URL variables and return them as an associative array.
function getUrlVars() {
	var vars = [], hash;
	var hashes = window.location.href.slice(
			window.location.href.indexOf('?') + 1).split('&');
	for ( var i = 0; i < hashes.length; i++) {
		hash = hashes[i].split('=');
		vars.push(hash[0]);
		vars[hash[0]] = hash[1];
	}
	return vars;
}

function updateData(lastDate) {

	var callback = function(data, textStatus, jqXHR) {
		var i = 0;

		dataIq.push([ id, data.numbers.iq ]);
		dataMsg.push([ id, data.numbers.msg ]);
		dataPres.push([ id, data.numbers.presence ]);
		dataRost.push([ id, data.numbers.roster ]);
		id++;
		while (data.packets[i] != null) {

			var color = "#DEDEDE";
			if (rowCounter % 2 == 0) {
				color = "#EBEBEB";
			}

			if ($('.entry').length == 0) {
				$('.tableBegin').html(
						'<tr class="entry" style="background-color:' + color
								+ ';"><td class="timeStamp">'
								+ formatTimestamp(data.packets[i].date)
								+ '</td><td>' + data.packets[i].type
								+ '</td><td>' + data.packets[i].from
								+ '</td><td>' + data.packets[i].to
								+ '</td></tr>');
			} else {
				$('.entry').filter(":first").before(
						'<tr class="entry" style="background-color:' + color
								+ ';"><td class="timeStamp">'
								+ formatTimestamp(data.packets[i].date)
								+ '</td><td>' + data.packets[i].type
								+ '</td><td>' + data.packets[i].from
								+ '</td><td>' + data.packets[i].to
								+ '</td></tr>');
			}

			lastTimestamp = data.packets[i].date > lastTimestamp ? data.packets[i].date
					: lastTimestamp;
			if (rowCounter > limit) {
				console.log("entferne row!?");
				$('.entry').filter(":last").remove();
			}
			console.log(rowCounter);
			rowCounter++;
			i++;
		}
	}

	drawGraph();
	var myDomain = getUrlVars()["component"];
	$.ajax({
		url : 'stats?component=' + myDomain + '&date=' + lastDate,
		dataType : 'json',
		data : '',
		success : callback
	});
}

function formatTimestamp(timeStamp) {

	function fillZero(data) {
		return data < 10 ? "0" + data : data;
	}
	var date = new Date(timeStamp);
	var hours = fillZero(date.getHours());
	var minutes = fillZero(date.getMinutes());
	var seconds = fillZero(date.getSeconds());
	var year = fillZero(date.getFullYear());
	var month = fillZero(date.getMonth() + 1);
	var day = fillZero(date.getDate());
	var formattedTime = hours + ':' + minutes + ':' + seconds + "  &nbsp;   "
			+ day + "." + month + "." + year;
	return formattedTime;
}

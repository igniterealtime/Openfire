$(document).ready(function() {
	drawGraph();
	window.setInterval("pollStats()", 1000);
})

var dataIq = [];
var dataMsg = [];
var dataPres = [];
var dataRost = [];
var id = 0;
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
	// xaxis: { tickDecimals: 0, tickSize: 1 }
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
	// $.plot(placeholder, d1, options);
}

function pollStats() {
	var myDate = $('#lastPoll').html();
	if (myDate.length < 1) {
		myDate = $('#logSince').html();
	}
	var firstDate = $('#timeStamp:first').html();
	if (firstDate == null) {
		firstDate = $('#logSince').html();
	}
	updateData(firstDate);
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
			if (i % 2 == 0) {
				color = "#EBEBEB";
			}

			$('.tableBegin:first').after(
					'<tr style="background-color:' + color
							+ ';"><td id="timeStamp">' + data.packets[i].date
							+ '</td><td>' + data.packets[i].type + '</td><td>'
							+ data.packets[i].from + '</td><td>'
							+ data.packets[i].to + '</td></tr>');
			i++;
		}
	}

	drawGraph();
	$.ajax({
		url : 'stats?component=xmpp.dew08299&date=' + lastDate,
		dataType : 'json',
		data : '',
		success : callback
	});
}

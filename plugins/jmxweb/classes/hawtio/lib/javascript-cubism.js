var j4p = new Jolokia({url: "http://jolokia.org/jolokia", fetchInterval: 1000});


var context = cubism.context()
    .serverDelay(0)
    .clientDelay(0)
    .step(1000)
    .size(594);
var jolokia = context.jolokia(j4p);

// =============================================================================
// Various metrics used

var memory = jolokia.metric(
    function (resp1, resp2) {
        return Number(resp1.value) / Number(resp2.value);
    },
    {type:"read", mbean:"java.lang:type=Memory", attribute:"HeapMemoryUsage", path:"used"},
    {type:"read", mbean:"java.lang:type=Memory", attribute:"HeapMemoryUsage", path:"max"}, "Heap-Memory"
);
var gcCount = jolokia.metric(
    {type:"read", mbean:"java.lang:name=PS MarkSweep,type=GarbageCollector", attribute:"CollectionCount"},
    {delta:1000, name:"GC Old"}
);
var gcCount2 = jolokia.metric(
    {type:"read", mbean:"java.lang:name=PS Scavenge,type=GarbageCollector", attribute:"CollectionCount"},
    {delta:1000, name:"GC Young"}
);

var agentRequest = jolokia.metric(
    {type:        "read", mbean:"Catalina:J2EEApplication=none,J2EEServer=none,WebModule=//localhost/jolokia,j2eeType=Servlet,name=jolokia-agent",
        attribute:"requestCount"}, {name:"Jolokia", delta:10 * 1000});
var hudsonRequest = jolokia.metric(
    {type:        "read", mbean:"Catalina:J2EEApplication=none,J2EEServer=none,WebModule=//localhost/hudson,j2eeType=Servlet,name=Stapler",
        attribute:"requestCount"}, {name:"Hudson", delta:10 * 1000});
var sonarRequest = jolokia.metric(
    {type:        "read", mbean:"Catalina:J2EEApplication=none,J2EEServer=none,WebModule=//localhost/sonar,j2eeType=Servlet,name=default",
        attribute:"requestCount"}, {name:"Sonar", delta:10 * 1000});
var allRequests = jolokia.metric(
    function (resp) {
        var attrs = resp.value;
        var sum = 0;
        for (var key in attrs) {
            sum += attrs[key].requestCount;
        }
        return sum;
    },
    {type:        "read", mbean:"Catalina:j2eeType=Servlet,*",
        attribute:"requestCount"}, {name:"All", delta:10 * 1000});

//j4p.start(1000);

var colorsRed = ["#FDBE85", "#FEEDDE", "#FD8D3C", "#E6550D", "#A63603", "#FDBE85", "#FEEDDE", "#FD8D3C", "#E6550D", "#A63603" ],
    colorsGreen = [ "#E5F5F9", "#99D8C9", "#2CA25F", "#E5F5F9", "#99D8C9", "#2CA25F"],
    colorsBlue = [ "#ECE7F2", "#A6BDDB", "#2B8CBE", "#ECE7F2", "#A6BDDB", "#2B8CBE"];

// Created graphs
$(function () {
    d3.select("#memory").call(function (div) {

        div.append("div")
            .attr("class", "axis")
            .call(context.axis().orient("top"));

        div.selectAll(".horizon")
            .data([memory])
            .enter().append("div")
            .attr("class", "horizon")
            .call(
            context.horizon()
                .colors(colorsRed)
                .format(d3.format(".4p"))
        );
        div.selectAll(".horizon-gc")
            .data([gcCount2, gcCount])
            .enter().append("div")
            .attr("class", "horizon horizon-gc")
            .call(
            context.horizon().colors(colorsRed).height(10)
        );
        div.append("div")
            .attr("class", "rule")
            .call(context.rule());

    });

    d3.select("#request").call(function (div) {

        div.append("div")
            .attr("class", "axis")
            .call(context.axis().orient("top"));


        div.selectAll(".horizon")
            .data([agentRequest, hudsonRequest, sonarRequest, allRequests])
            .enter()
            .append("div")
            .attr("class", "horizon")
            .call(context.horizon()
            .format(d3.format("10d"))
            .colors(function (d, i) {
                return i == 3 ? colorsBlue : colorsGreen
            }));

        div.append("div")
            .attr("class", "rule")
            .call(context.rule());

    });

    // On mousemove, reposition the chart values to match the rule.
    context.on("focus", function (i) {
        d3.selectAll("#memory .value").style("right", i == null ? null : context.size() - i + "px");
        d3.selectAll("#request .value").style("right", i == null ? null : context.size() - i + "px");
    });

});

function gc() {
    j4p.request({type:"exec", mbean:"java.lang:type=Memory", operation:"gc"})
}

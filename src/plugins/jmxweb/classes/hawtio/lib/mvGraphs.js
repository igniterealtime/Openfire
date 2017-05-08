
// Class for a multi-value time series graph

function MultiTimeSeries(cssClass, dataPointWidth, height, leftMargin, rightMargin, topMargin, bottomMargin, dataPoints, period, hAxisTicks) {

    var width = dataPointWidth * dataPoints;

    this.items = [];

    this.svg = d3.select("body").append("svg")
        .attr("class", cssClass)
        .attr("width", width+leftMargin+rightMargin)
        .attr("height", height+topMargin+bottomMargin)
        .append("g")
        .attr("transform", "translate("+leftMargin+","+topMargin+")");

    this.add = function(item) {
        this.items.push(item);
        item.render(this.svg, dataPoints, width, height);
        return this;
    };

    this.start = function() {

        var pSecs = period / 1000;
        var now = new Date();
        var tScale = d3.time.scale().domain([now, d3.time.second.offset(now, -(dataPoints * pSecs))]).rangeRound([width,0]);

        this.svg.append("g")
            .attr("class", "axis")
            .attr("id","time")
            .call(d3.svg.axis()
                .scale(tScale)
                .orient("bottom")
                .ticks(hAxisTicks)
            )
            .attr("transform", "translate(0,"+height+")");

        var items = this.items;
        var chart = this.svg;

        setInterval(function() {

            var now = new Date();
            var tScale = d3.time.scale().domain([now, d3.time.second.offset(now, -(dataPoints * pSecs))]).rangeRound([width,0]);

            chart.select("#time").transition().duration(0)
                .call(d3.svg.axis()
                    .scale(tScale)
                    .orient("bottom")
                    .ticks(hAxisTicks)
                );

            for (var i = 0; i < items.length; i++) {
                items[i].update();
            }
            d3.timer.flush(); // Avoid memory leak when in background tab
        }, period);
    };

    return this;
}


// Class for a line graph

function Line (cssClass, getData, min, max, axis, vAxisTicks) {

    this.i = 0;

    this.render = function(svg, dataPoints, width, height) {

        this.svg = svg;
        this.data = d3.range(1).map(function() { return 0; });

        var xScale = d3.scale.linear().domain([0, dataPoints - 1]).range([width,0]);
        var yScale = d3.scale.linear().domain([min, max]).range([height, 0]);

        pathData = d3.svg.line()
            .interpolate("linear")
            .x(function(d, i) { return xScale(i); })
            .y(function(d, i) { return yScale(d); });

        this.path = svg.append("g")
            .append("path")
            .attr("class", cssClass)
            .data([this.data])
            .attr("d", pathData);

        var translate = 0;
        switch (axis) {
            case "right":
                translate = width;
            case "left":
                var sideAxis = d3.svg.axis().scale(yScale).orient(axis).ticks(vAxisTicks);
                if (min === 0 && max === 1) sideAxis.tickFormat(d3.format(".0%"));
                svg.append("g")
                    .attr("class", "axis")
                    .call(sideAxis)
                    .attr("transform", "translate("+translate+",0)");
                break;
        }
    };

    this.update = function() {
        this.data.splice(0,0,getData());
        this.i++;
        if (this.i >= dataPoints) {
            this.data.pop();
        }

        this.path.attr("d", pathData);
    }
}


// Class for a bar graph

function Bar (cssClass, getData, min, max, axis, vAxisTicks, dataPointWidth) {

    this.render = function(svg, dataPoints, width, height) {

        this.svg = svg;
        this.data = d3.range(dataPoints).map(function() { return 0; });

        this.xScale = d3.scale.linear().domain([0, 1]).range([0, dataPointWidth]);
        this.yScale = d3.scale.linear().domain([min, max]).range([0, height]);
        var pScale = d3.scale.linear().domain([min, max]).range([height,0]); // svg coords upsidedown
        this.height = height;

        this.group = svg.append("g");

        var translate = 0;
        switch (axis) {
            case "right":
                translate = width;
            case "left":
                svg.append("g")
                    .attr("class", "axis")
                    .call(d3.svg.axis().scale(pScale).orient(axis).ticks(vAxisTicks))
                    .attr("transform", "translate("+translate+",0)");
                break;
        }
    };

    this.update = function() {
        this.data.shift();
        this.data.push(getData());

        var xScale = this.xScale;
        var yScale = this.yScale;
        var height = this.height;

        var rect = this.group.selectAll("rect")
            .data(this.data, function(d) { return d; });

        rect.enter().insert("rect", "line")
            .attr("class", cssClass)
            .attr("x", function(d, i) { return xScale(i+1); })
            .attr("y", function(d) { return height - yScale(d); })
            .attr("width", dataPointWidth)
            .attr("height", function(d) {return yScale(d);});

        rect.transition()
            .duration(0)
            .attr("x", function(d, i) { return xScale(i); });

        rect.exit().transition()
            .duration(0)
            .attr("x", function(d, i) { return xScale(i-1); })
            .remove();
    }
}
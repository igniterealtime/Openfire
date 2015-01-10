### ForceGraph

The force graph plugin adds a directive to hawtio that allows en easy and customizable way of displaying graph data as a D3 forced graph. The plugin has been inspired by the following D3 resources:

   * The [D3 homepage](http://d3js.org/) has a lot of examples and the force layout graphs is just one category.
   * The main work was inspired by [this blog entry](http://bl.ocks.org/mbostock/4062045).
   * The inspiration for zoom, pan and custom tooltips are coming [from here](http://bl.ocks.org/bentwonk/2514276).
   * Finally, [here](http://www.befundoo.com/university/tutorials/angularjs-directives-tutorial/) is a very good tutorial for writing Angular JS directives in general. 

#### Using the force graph directive 

Using the directive is straight forward and an example is within the OSGi plugin visualizing the dependencies:

    <div ng-controller="Osgi.ServiceDependencyController">
      <div ng-hide="inDashboard" class="add-link">
        <a ng-href="{{addToDashboardLink()}}" title="Add this view to a Dashboard"><i class="icon-share"></i></a>
      </div>

      <div id="pop-up">
        <div id="pop-up-title"></div>
        <div id="pop-up-content"></div>
      </div>

      <div class="row-fluid">
        <div class="span12 canvas">
          <div hawtio-force-graph graph="graph" link-distance="100" charge="-300" nodesize="10" style="min-height: 800px"></div>
        </div>
      </div>
    </div>

As you can see, we are using a normal angular controller and as far as the graph is concerned, the controller manages a variable that holds
the graph data. In the example above, the scope variable `graph` holds that data. The directive is implemented as `hawtioForceGraph` in an
attribute style, so the div element with the `hawtio-force-graph` attribute renders the graph.

`link-distance` and `charge` are simply parameters exposed from the D3 forced layout. There are quite a few more parameters, but these two
seemed to have the most influence on the layout. If required, the others should be easy to add. The `nodesize` attribute denotes the radius
of circles to display nodes if no image for that particular node is provided. If images are set for all nodes, the nodesize parameter won't
have an effect.

The div with the id `pop-up` is used to style the tooltips for the nodes.

The image below shows the force graph directive being used to render the service / bundle dependencies within an OSGI container.

![Force Graph in Action](app/forcegraph/doc/img/dependencies.png)

#### Building a Graph

The best way to build a graph is using an instace of `GraphBuilder`. This is a convenience object that will collect the information about
nodes and edges within the graph. The method `buildGraph()` returns a graph object renderable with D3.

The only magic behind the GraphBuilder is how nodes are linked. D3 expects nodes in a list and the edges would be codified in terms of list
indices. The GraphBuilder will collect all nodes in a hashmap keyed by the the `id` attribute of the node. This allows us to use those
identifiers when we define a link with `addLink (srcId, targetId, linkType)`. Here, `linkType` will only have an effect on rendering the
link.

The `buildGraph()` accessor simply turns the values of the node hash into a list and maps the id's to indices as required.

A node can have more data than the `id` attribute:

<dl>
  <dt><em>name</em></dt>
  <dd>The `name` attribute will be used as a node label if it is set.

  <dt><em>navUrl</em></dt>
  <dd>If set, this allows to navigate to the given URL by clicking on the node.</dd>

  <dt><em>image</em></dt>
  <dd>This is a data structure with the attributes <em>url</em>, <em>width</em> and <em>height</em>. The url points to an image
  that will be used to render the particular node. Width and height specify the image dimensions. The latter will be used to center the
  image on the node.</dd>

  <dt><em>popup</em></dt>
  <dd>This is a data structure with the attributes <em>title</em> and <em>content</em>. If present, this will be rendered
  as a tooltip when hovering over the node. Though in theory this could be arbitrary HTML it's probably best nt to get carried
  away with the tooltip.</dd>
</dl>

Below is an example to build a bundle node within the OSGI dependency viewer:

    var buildBundleNode = (bundle) => {

        var bundleNodeId = "Bundle-" + bundle.Identifier;

        var bundleNode = {
            id: bundleNodeId,
            name: bundle.SymbolicName,
            type: "bundle",
            navUrl: "#/osgi/bundle/" + bundle.Identifier,
            size: 20,
            image: {
                url: "/hawtio/app/osgi/img/bundle.png",
                width: 32,
                height:32
            },
            popup : {
                title: "Bundle [" + bundle.Identifier + "]",
                content: "<p>" + bundle.SymbolicName + "<br/>Version " + bundle.Version + "</p>"
            }
        }

        return bundleNode;

    }

The complete file that builds the graph of OSGi dependencies is at [here](https://github.com/atooni/hawtio/blob/master/hawtio-web/src/main/webapp/app/osgi/js/svc-dependencies.ts).
Note, that essentially the function `createGraph()` populates the scope variable `graph` that we have seen before in the directive.

#### Styling the graph

Apart from using images and links to make the graph nicer and a bit interactive, each node and edge have a `type` attribute. These can be referenced in CSS definitions
to display the graph in style. For example, the edges indicating a bundle *consuming* a service are displayed with a dashed line in the OSGi dependencies. To achieve this,
the `type` attribute for those links will be `ìnuse`.

Accordingly, we find in the associated stylesheet

    path.link.inuse {
        stroke-dasharray: 0,2 1;
    }

The same principle is applied for nodes without images, which are displayed as colored circles.

Looking at the [complete html](https://github.com/atooni/hawtio/blob/master/hawtio-web/src/main/webapp/app/osgi/html/svc-dependencies.html) you will notice CSS settings
for the tooltips and also the background settings.

#### Working with selections

You can pass in the scope model name to use to keep track of the current node selection via the **select-model** attribute:

    <div hawtio-force-graph graph="graph" selected-model="mySelection"></div>

Then in your code you can do...

    $scope.$watch("mySelection", (newValue, oldValue) => {
      ...
    });

to respond to the selection.


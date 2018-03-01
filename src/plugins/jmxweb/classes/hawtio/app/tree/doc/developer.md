### Tree

This plugin provides a simple HTML directive for working with [jQuery DynaTree widgets](http://wwwendt.de/tech/dynatree/doc/dynatree-doc.html) from AngularJS

To use the directive, in your $scope create a tree model (e.g. using the Folder class) and assign it to some scope value...

    $scope.foo = new Folder("cheese");
    // populate the folders

    $scope.onFooSelected = function (selection) {
      // do something...
    };

Then in your HTML use

    <div hawtio-tree="foo"></div>

To invoke a function on your $scope when a node is selected add the **onSelect** attribute:

    <div hawtio-tree="foo" onSelect="onFooSelected"></div>

If you want to hide the root tree node you can add a hideRoot flag:

    <div hawtio-tree="foo" hideRoot="true"></div>

You can add support for drag and drop by adding one of the drag and drop functions on your scope and them mentioning its name on the **onDragStart**, **onDragEnter**, **onDrop**,

If you wish to be called back with the root node after population of the tree add the **onRoot** attribute

    <div hawtio-tree="foo" onRoot="onMyRootThingy"></div>

Then add:

     $scope.onMyRootThingy = (rootNode) => {
        // process the rootNode
     };

If you wish to activate/select a number of nodes on startup then use the **activateNodes** attribute to map to a $scope variable which is an id or a list of IDs to activate on startup.
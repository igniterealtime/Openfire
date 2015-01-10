# Hints and tips on making different UI components

This document tries to give some hints and tips on how to make common UI stuff

## Modal Dialogs

Its handy to have popups with detailed views, confirmation dialogs or wizards.

Here's a quick and easy way to make them in hawtio:

* ensure your angular module depends on **'ui.bootstrap.dialog'**:

    angular.module(pluginName, ['bootstrap', 'ui.bootstrap.dialog', 'hawtioCore'])

* create a [new Core.Dialog() object in your controller scope](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/activemq/js/browse.ts#L7) with some name

* Then in the modal dialog markup, using [angular ui bootstrap modals](http://angular-ui.github.io/bootstrap/#/modal) refer to **name.show** and **name.options**, then to open/show use **name.open()** and **name.close()**as in this [example markup for the above controller code](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/activemq/html/browseQueue.html#L70)

This also means you only have to come up with 1 name per dialog - the name of the dialog object - rather than naming the various flags/options/open/close methods :)

## Grids / Tables

We have 2 implementations currentl, [ng-grid](http://angular-ui.github.io/ng-grid/) which has a really nice directive and angular way of working with it; check it out for the documentation on how to make a grid.

We've also got a [datatable plugin](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/datatable/doc/developer.md) which provides a directive and API to ng-grid but uses the underlying [jQuery DataTable widget](http://datatables.net/) until ng-grid is as fast and solves all the same use cases.

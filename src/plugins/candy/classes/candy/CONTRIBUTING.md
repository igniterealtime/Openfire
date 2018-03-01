# Contributing

## Team members

* Patrick Stadler &middot; [@pstadler](http://twitter.com/pstadler) &middot; <patrick.stadler@gmail.com>
* Michael Weibel &middot; [@weibelm](htps://twitter.com/weibelm) &middot; <michael.weibel@gmail.com>

## Learn & listen

[![Gitter chat](https://badges.gitter.im/candy-chat.png)](https://gitter.im/candy-chat)

* [Mailing list](http://groups.google.com/group/candy-chat)
	* yes, non-gmail users can signup as well
* [FAQ](https://github.com/candy-chat/candy/wiki/Frequently-Asked-Questions)

## Contributing

You want to help us? **Awesome!**

### How to contribute
A few hopefully helpful hints to contributing to Candy

#### Using vagrant
1. [Fork](https://help.github.com/articles/fork-a-repo) Candy
2. [Install Vagrant](http://vagrantup.com/)
3. Run `vagrant up`.
5. Create a branch based on the `master` branch (`git checkout -B my-awesome-feature`)
6. Run `grunt watch` to automatically run jshint (syntax checker) and the build of `candy.bundle.js` and `candy.min.js` while developing.
7. Make your changes, fix eventual *jshint* errors & push them back to your fork
8. Create a [pull request](https://help.github.com/articles/using-pull-requests)


#### On your own machine
Please note that you should have a working XMPP server to test your changes (the vagrant way does already have a working XMPP server).

1. [Fork](https://help.github.com/articles/fork-a-repo) Candy
2. Clone your fork
3. Checkout out `master` branch (`git checkout master`)
4. Install [Node.js](http://nodejs.org/)
5. Install [Grunt](http://gruntjs.com/) (`npm install -g grunt-cli`)
6. Install [Bower](http://bower.io/) (`npm install -g bower`)
7. Install npm dependencies (`npm install` in candy root directory)
8. Install bower dependencies (`bower install` in candy root directory)
9. Create a branch based on the `master` branch (`git checkout -B my-awesome-feature`)
10. Run `grunt watch` to automatically run jshint (syntax checker) and the build of `candy.bundle.js` and `candy.min.js` while developing.
11. Make your changes, fix eventual *jshint* errors & push them back to your fork
12. Create a [pull request](https://help.github.com/articles/using-pull-requests)

In case you have any questions, don't hesitate to ask on the [Mailing list](http://groups.google.com/group/candy-chat).

### Running tests

* Tests are run using [Intern](http://theintern.io).
* `grunt` and `grunt watch` will each run unit tests in Chrome on Linux (for fast feedback).
* `grunt test` will run both unit and integration tests in a variety of environments. Tests are run using Selenium Standalone and Phantom.JS while developing, and on Sauce Labs in CI or using `grunt test`.
* If you don't want to use the Vagrant box to run Selenium/PhantomJS, set `CANDY_VAGRANT='false'` to run tests.

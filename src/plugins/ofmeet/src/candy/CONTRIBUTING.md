# Contributing

## Team members

* Patrick Stadler &middot; [@pstadler](http://twitter.com/pstadler) &middot; <patrick.stadler@gmail.com>
* Michael Weibel &middot; [@weibelm](htps://twitter.com/weibelm) &middot; <michael.weibel@gmail.com>

## Learn & listen

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
3. Follow instructions [for Candy Vagrant](https://github.com/candy-chat/vagrant)
4. Change the remote in the `candy` and `candy-plugins` repos: `git remote set-url origin git://github.com/YOURNAME/candy` (or candy-plugins)
5. Create a branch based on the `dev` branch (`git checkout -B my-awesome-feature`)
6. Run `grunt watch` to automatically run jshint (syntax checker) and the build of `candy.bundle.js` and `candy.min.js` while developing.
7. Make your changes, fix eventual *jshint* errors & push them back to your fork
8. Create a [pull request](https://help.github.com/articles/using-pull-requests)


#### On your own machine
Please note that you should have a working XMPP server to test your changes (the vagrant way does already have a working XMPP server).

1. [Fork](https://help.github.com/articles/fork-a-repo) Candy
2. Clone your fork
2. Checkout out `dev` branch (`git checkout dev`) & Update git submodules `git submodule update --init`
3. Install [Node.js](http://nodejs.org/)
4. Install [Grunt](http://gruntjs.com/) (`npm install -g grunt-cli`)
5. Install npm dependencies (`npm install` in candy root directory)
6. Create a branch based on the `dev` branch (`git checkout -B my-awesome-feature`)
7. Run `grunt watch` to automatically run jshint (syntax checker) and the build of `candy.bundle.js` and `candy.min.js` while developing.
8. Make your changes, fix eventual *jshint* errors & push them back to your fork
9. Create a [pull request](https://help.github.com/articles/using-pull-requests)

In case you have any questions, don't hesitate to ask on the [Mailing list](http://groups.google.com/group/candy-chat).

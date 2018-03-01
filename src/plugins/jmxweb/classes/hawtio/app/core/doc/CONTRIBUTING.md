We love contributions! We really need your help to make [hawtio](http://hawt.io/) even more hawt, so please [join our community](http://hawt.io/community/index.html)!

Many thanks to all of our [existing contributors](https://github.com/hawtio/hawtio/graphs/contributors)! But we're greedy, we want more! hawtio is _hawt_, but it can be _hawter_! :). We have  [lots of plugins](http://hawt.io/plugins/index.html) but they can be improved and we want more plugins!

Here's some notes to help you get started:

## Getting Started

* Make sure you have a [GitHub account](https://github.com/signup/free) as you'll need it to submit issues, comments or pull requests.
* Got any ideas for how we can improve hawtio? Please [submit an issue](https://github.com/hawtio/hawtio/issues?state=open) with your thoughts. Constructive criticism is always greatly appreciated!
* Fancy submitting [any nice screenshots of how you're using hawtio?](https://github.com/hawtio/hawtio/tree/master/website/src/images/screenshots) A quick Youtube screencast would be even _hawter_ or a blog post/article we can link to? Just [submit an issue](https://github.com/hawtio/hawtio/issues?state=open) (or fork and patch the [website](https://github.com/hawtio/hawtio/tree/master/website/src/) or any Markdown docs in our repository directly) and we'll merge it into our website.
* Fancy submitting your cool new dashboard configuration or some wiki docs? See below on how to do that...
* Search [our issue tracker](https://github.com/hawtio/hawtio/issues?state=open) and see if there's been any ideas or issues reported for what you had in mind; if so please join the conversation in the comments.
* Submit any issues, feature requests or improvement ideas [our issue tracker](https://github.com/hawtio/hawtio/issues?state=open).
  * Clearly describe the issue including steps to reproduce when it is a bug.
  * Make sure you fill in the earliest version that you know has the issue.

### Fancy hacking some code?

* If you fancy working on some code, check out the these lists of issues:
    * [open apprentice tasks](https://github.com/hawtio/hawtio/issues?labels=apprentice+tasks&page=1&sort=updated&state=open) - which are moderately easy to fix and tend to have links to which bits of the code to look at to fix it or
    * [all open issues](https://github.com/hawtio/hawtio/issues?state=open) if you fancy being more adventurous.
    * [hawt ideas](https://github.com/hawtio/hawtio/issues?labels=hawt+ideas&page=1&sort=updated&state=open) if you're feeling like a ninja and fancy tackling our harder issues that tend to add really _hawt_ new features!

* To make code changes, fork the repository on GitHub then you can hack on the code. We love any contribution such as:
   * fixing typos
   * improving the documentation or embedded help
   * writing new test cases or improve existing ones
   * adding new features
   * improving the layout / design / CSS
   * creating a new [plugin](http://hawt.io/plugins/index.html)

## Submitting changes to hawtio

* Push your changes to your fork of the [hawtio repository](https://github.com/hawtio/hawtio).
* Submit a pull request to the repository in the **hawtio** organization.
* If your change references an existing [issue](https://github.com/hawtio/hawtio/issues?state=open) then use "fixes #123" in the commit message (using the correct issue number ;).

## Submitting changes dashboard and wiki content

Hawtio uses the [hawtio-config repository](https://github.com/hawtio/hawtio-config) to host its runtime configuration. When you startup hawtio by default it will clone this repository to the configuration directory (see the [configuration document](https://github.com/hawtio/hawtio/blob/master/docs/Configuration.md) or more detail).

In development mode if you are running hawtio via the **hawtio-web** directory, then your local clone of the [hawtio-config repository](https://github.com/hawtio/hawtio-config) will be in the **hawtio/hawtio-web/hawtio-config directory**. If you've added some cool new dashboard or editted any files via the hawtio user interface then your changes will be committed locally in this directory.

If you are a committer and want to submit any changes back just type:

    cd hawtio-config
    git push

Otherwise if you want to submit pull requests for your new dashboard or wiki content then fork the [hawtio-config repository](https://github.com/hawtio/hawtio-config) then update your hawtio-config directory to point to this directory. e.g. edit the hawtio-config/.git/config file to point to your forked repository.

Now perform a git push as above and then submit a pull request on your forked repo.

# Additional Resources

* [hawtio FAQ](http://hawt.io/faq/index.html)
* [General GitHub documentation](http://help.github.com/)
* [GitHub create pull request documentation](hhttps://help.github.com/articles/creating-a-pull-request)
* [Here is how to build the code](http://hawt.io/building/index.html)
* [More information for developers in terms of hawtio technologies, tools and code walkthroughs](http://hawt.io/developers/index.html)
* [join the hawtio community](http://hawt.io/community/index.html)


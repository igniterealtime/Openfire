### Wiki

The wiki plugin implements a wiki for viewing, creating and editing text files (Markdown, HTML, XML, property files, JSON) which are then versioned and stored in a git repository.

<ul class="thumbnails">
  <li class="span4">
    <div class="thumbnail">
      <img src="https://raw.github.com/hawtio/hawtio/master/website/src/images/screenshots/wiki.png" alt="screenshot">
      <h3>viewing the wiki</h3>
    </div>
  </li>
</ul>

* you can browse files by clicking on them in the directory listing view
* go up directory levels using the tabs
* when viewing a file you can see its git history (the **History** button top right), then you can compare versions or revert to an old version
* editing files uses a syntax highlighter for most common file types like HTML, markdown, JSON, XML, property files, Java, Scala etc.
* the wiki plugin reuses the [github](http://github.com) convention, that if a directory contains a file called **ReadMe.md** or **ReadMe.html** then it is shown below the directory listing.

##### How the configuration works

The default configuration repository for the wiki is [hawtio-config on github](https://github.com/hawtio/hawtio-config) but you can [configure hawtio](http://hawt.io/configuration/index.html) to use whatever configuration directory or remote git repository you wish.

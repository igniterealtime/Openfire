/*
 * Simple script loader and registry
 */
var hawtioPluginLoader = (function(self, window, undefined) {
  var log = Logger.get('PluginLoader');
  self.log = log;

  /**
   * List of URLs that the plugin loader will try and discover
   * plugins from
   * @type {Array}
   */
  self.urls = [];

  /**
   * Holds all of the angular modules that need to be bootstrapped
   * @type {Array}
   */
  self.modules = [];

  /**
   * Tasks to be run before bootstrapping, tasks can be async.
   * Supply a function that takes the next task to be
   * executed as an argument and be sure to call the passed
   * in function.
   *
   * @type {Array}
   */
  self.tasks = [];

  self.registerPreBootstrapTask = function(task) {
    self.tasks.push(task);
  };

  self.addModule = function(module) {
    log.debug("Adding module: " + module);
    self.modules.push(module);
  };

  self.addUrl = function(url) {
    log.debug("Adding URL: " + url);
    self.urls.push(url);
  };

  self.getModules = function() {
    return self.modules.clone();
  };

  /**
   * Parses the given query search string of the form "?foo=bar&whatnot"
   * @param text
   * @return a map of key/values
   */
  self.parseQueryString = function(text) {
      var query = (text || window.location.search || '?');
      var idx = -1;
      if (angular.isArray(query)) {
        query = query[0];
      }
      idx = query.indexOf("?");
      if (idx >= 0) {
        query = query.substr(idx + 1);
      }
      // if query string ends with #/ then lets remove that too
      idx = query.indexOf("#/");
      if (idx > 0) {
        query = query.substr(0, idx);
      }
      var map = {};
      query.replace(/([^&=]+)=?([^&]*)(?:&+|$)/g, function(match, key, value) {
          (map[key] = map[key] || []).push(value); 
        });
      return map;
  };

  /**
   * Parses the username:password from a http basic auth URL, e.g.
   * http://foo:bar@example.com
   */
  self.getCredentials = function(urlString) {
/*
    No Uri class outside of IE right?

    var uri = new Uri(url);
    var credentials = uri.userInfo();
*/
    if (urlString) {
      var credentialsRegex = new RegExp(/.*:\/\/([^@]+)@.*/);
      var m = urlString.match(credentialsRegex);
      if (m && m.length > 1) {
        var credentials = m[1];
        if (credentials && credentials.indexOf(':') > -1) {
          return credentials.split(':');
        }
      }
    }
    return [];
  };


  self.loaderCallback = null;

  self.setLoaderCallback = function(cb) {
    self.loaderCallback = cb;
    log.debug("Setting callback to : ", self.loaderCallback);
  };


  self.loadPlugins = function(callback) {

    var lcb = self.loaderCallback;

    var plugins = {};

    var urlsToLoad = self.urls.length;
    var totalUrls = urlsToLoad;

    var bootstrap = function() {
      self.tasks.push(callback);
      var numTasks = self.tasks.length;

      var executeTask = function() {
        var task = self.tasks.shift();
        if (task) {
          self.log.debug("Executing task ", numTasks - self.tasks.length);
          task(executeTask);
        } else {
          self.log.debug("All tasks executed");
        }
      };
      executeTask();
    };

    var loadScripts = function() {

      // keep track of when scripts are loaded so we can execute the callback
      var loaded = 0;
      $.each(plugins, function(key, data) {
        loaded = loaded + data.Scripts.length;
      });

      var totalScripts = loaded;

      var scriptLoaded = function() {
        $.ajaxSetup({async:true});
        loaded = loaded - 1;
        if (lcb) {
          lcb.scriptLoaderCallback(lcb, totalScripts, loaded + 1);
        }
        if (loaded == 0) {
          bootstrap();
        }
      };

      if (loaded > 0) {
        $.each(plugins, function(key, data) {

          data.Scripts.forEach( function(script) {

            // log.debug("Loading script: ", data.Name + " script: " + script);

            var scriptName = data.Context + "/" + script;
            log.debug("Fetching script: ", scriptName);
            $.ajaxSetup({async:false});
            $.getScript(scriptName)
                .done(function(textStatus) {
                  log.debug("Loaded script: ", scriptName);
                })
                .fail(function(jqxhr, settings, exception) {
                  log.info("Failed loading script: \"", exception.message, "\" (<a href=\"", scriptName, ":", exception.lineNumber, "\">", scriptName, ":", exception.lineNumber, "</a>)");
                })
                .always(scriptLoaded);
          });
        });
      } else {
        // no scripts to load, so just do the callback
        $.ajaxSetup({async:true});
        bootstrap();
      }
    }

    if (urlsToLoad == 0) {
      loadScripts();
    } else {
      var urlLoaded = function () {
        urlsToLoad = urlsToLoad - 1;
        if (lcb) {
          lcb.urlLoaderCallback(lcb, totalUrls, urlsToLoad + 1);
        }
        if (urlsToLoad == 0) {
          loadScripts();
        }
      };

      var regex = new RegExp(/^jolokia:/);

      $.each(self.urls, function(index, url) {

        if (regex.test(url)) {
          var parts = url.split(':');
          parts = parts.reverse();
          parts.pop();

          var url = parts.pop();
          var attribute = parts.reverse().join(':');
          var jolokia = new Jolokia(url);

          try {
            var data = jolokia.getAttribute(attribute, null);
            $.extend(plugins, data);
          } catch (Exception) {
            // console.error("Error fetching data: " + Exception);
          }
          urlLoaded();
        } else {

          log.debug("Trying url: ", url);

          $.get(url, function (data) {
                // log.debug("got data: ", data);
                $.extend(plugins, data);
              }).always(function() {
                urlLoaded();
              });
        }
      });
    }
  };

  self.debug = function() {
    log.debug("urls and modules");
    log.debug(self.urls);
    log.debug(self.modules);
  };

  self.setLoaderCallback({
    scriptLoaderCallback: function (self, total, remaining) {
      log.debug("Total scripts: ", total, " Remaining: ", remaining);
    },
    urlLoaderCallback: function (self, total, remaining) {
      log.debug("Total URLs: ", total, " Remaining: ", remaining);
    }
  });

  return self;

})(hawtioPluginLoader || {}, window, undefined);




Logger.setLevel(Logger.INFO);
// we'll default to 100 statements I guess...
window['LogBuffer'] = 100;

if ('localStorage' in window) {
  if ('logLevel' in window.localStorage) {
    var logLevel = JSON.parse(window.localStorage['logLevel']);
    // console.log("Using log level: ", logLevel);
    Logger.setLevel(logLevel);
  }

  if ('showLog' in window.localStorage) {
    var showLog = window.localStorage['showLog'];
    // console.log("showLog: ", showLog);
    if (showLog === 'true') {
      var container = document.getElementById("log-panel");
      if (container) {
        container.setAttribute("style", "bottom: 50%;");
      }
    }
  }

  if ('logBuffer' in window.localStorage) {
    var logBuffer = window.localStorage['logBuffer'];
    window['LogBuffer'] = parseInt(logBuffer);
  } else {
    window.localStorage['logBuffer'] = window['LogBuffer'];
  }
}

var consoleLogger = null;

if ('console' in window) {

  window['JSConsole'] = window.console;
  consoleLogger = function(messages, context) {
    var MyConsole = window['JSConsole'];
    var hdlr = MyConsole.log;

    // Prepend the logger's name to the log message for easy identification.
    if (context.name) {
      messages[0] = "[" + context.name + "] " + messages[0];
    }

    // Delegate through to custom warn/error loggers if present on the console.
    if (context.level === Logger.WARN && 'warn' in MyConsole) {
      hdlr = MyConsole.warn;
    } else if (context.level === Logger.ERROR && 'error' in MyConsole) {
      hdlr = MyConsole.error;
    } else if (context.level === Logger.INFO && 'info' in MyConsole) {
      hdlr = MyConsole.info;
    }

    if (hdlr && hdlr.apply) {
      try {
        hdlr.apply(MyConsole, messages);
      } catch (e) {
        MyConsole.log(messages);
      }
    }
  };
}

// keep these hidden in the Logger object
Logger.getType = function(obj) {
  return Object.prototype.toString.call(obj).slice(8, -1);
};

Logger.isError = function(obj) {
  return obj && Logger.getType(obj) === 'Error';
};

Logger.isArray = function (obj) {
  return obj && Logger.getType(obj) === 'Array';
};

Logger.isObject = function (obj) {
  return obj && Logger.getType(obj) === 'Object';
};

Logger.isString = function(obj) {
  return obj && Logger.getType(obj) === 'String';
};

window['logInterceptors'] = [];

Logger.formatStackTraceString = function(stack) {
  var lines = stack.split("\n");

  if (lines.length > 100) {
    // too many lines, let's snip the middle so the browser doesn't bail
    var start = 20;
    var amount = lines.length - start * 2;
    lines.splice(start, amount, '>>> snipped ' + amount + ' frames <<<');
  }

  var stackTrace = "<div class=\"log-stack-trace\">\n";

  for (var j = 0; j < lines.length; j++) {
    var line = lines[j];
    if (line.trim().length === 0) {
      continue;
    }
    //line = line.replace(/\s/g, "&nbsp;");
    stackTrace = stackTrace + "<p>" + line + "</p>\n";
  }
  stackTrace = stackTrace + "</div>\n";
  return stackTrace;
};


Logger.setHandler(function(messages, context) {
  // MyConsole.log("context: ", context);
  // MyConsole.log("messages: ", messages);
  var container = document.getElementById("log-panel");
  var panel = document.getElementById("log-panel-statements");
  var node = document.createElement("li");
  var text = "";
  var postLog = [];

  // try and catch errors logged via console.error(e.toString) and reformat
  if (context['level'].name === 'ERROR' && messages.length === 1) {
    if (Logger.isString(messages[0])) {
      var message = messages[0];
      var messageSplit = message.split(/\n/);
      if (messageSplit.length > 1) {

        // we may have more cases that require normalizing, so a more flexible solution
        // may be needed
        var lookFor = "Error: Jolokia-Error: ";
        if (messageSplit[0].search(lookFor) == 0) {
          var msg = messageSplit[0].slice(lookFor.length);
          window['JSConsole'].info("msg: ", msg);
          try {
            var errorObject = JSON.parse(msg);
            var error = new Error();
            error.message = errorObject['error'];
            error.stack = errorObject['stacktrace'].replace("\\t", "&nbsp;&nbsp").replace("\\n", "\n");
            messages = [error];
          } catch (e) {
            // we'll just bail and let it get logged as a string...
          }
        } else {
          var error = new Error();
          error.message = messageSplit[0];
          error.stack = message;
          messages = [error];
        }
      }
    }
  }

  for (var i = 0; i < messages.length; i++) {
    var message = messages[i];
    if (Logger.isArray(message) || Logger.isObject(message)) {
      var obj = "" ;
      try {
        obj = '<pre data-language="javascript">' + JSON.stringify(message, null, 2) + '</pre>';
      } catch (error) {
        obj = message + " (failed to convert) ";
        // silently ignore, could be a circular object...
      }
      text = text + obj;
    } else if (Logger.isError(message)) {

      if ('message' in message) {
        text = text + message['message'];
      }
      if ('stack' in message) {
        postLog.push(function() {
          var stackTrace = Logger.formatStackTraceString(message['stack']);
          var logger = Logger;
          if (context.name) {
            logger = Logger.get(context['name']);
          }
          logger.info("Stack trace: ", stackTrace);
        });
      }
    } else {
      text = text + message;
    }
  }

  if (context.name) {
    text = '[<span class="green">' + context.name + '</span>] ' + text;
  }

  node.innerHTML = text;
  node.className = context.level.name;

  var scroll = false;
  if (container) {
    if (container.scrollHeight = 0) {
      scroll = true;
    }

    if (panel.scrollTop > (panel.scrollHeight - container.scrollHeight - 200)) {
      scroll = true;
    }
  }

  function onAdd() {
    if (panel) {
      panel.appendChild(node);
      if (panel.childNodes.length > parseInt(window['LogBuffer'])) {
        panel.removeChild(panel.firstChild);
      }
      if (scroll) {
        panel.scrollTop = panel.scrollHeight;
      }
    }
    if (consoleLogger) {
      consoleLogger(messages, context);
    }
    var interceptors = window['logInterceptors'];

    for (var i = 0; i < interceptors.length; i++) {
      interceptors[i](context.level.name, text);
    }
  }

  onAdd();

  postLog.forEach(function (func) { func(); });

  /*
  try {
    Rainbow.color(node, onAdd);
  } catch (e) {
    // in case rainbow hits an error...
    onAdd();
  }
  */


});

// Catch uncaught exceptions and stuff so we can log them
window.onerror = function(msg, url, line, column, errorObject) {
  if (errorObject && Logger.isObject(errorObject)) {
    Logger.get("Window").error(errorObject);
  } else {
    var href = ' (<a href="' + url + ':' + line + '">' + url + ':' + line;

    if (column) {
      href = href + ':' + column;
    }
    href = href + '</a>)';
    Logger.get("Window").error(msg, href);
  }
  return true;
};

// sneaky hack to redirect console.log !
window.console = {
  log: Logger.debug,
  warn: Logger.warn,
  error: Logger.error,
  info: Logger.info
};

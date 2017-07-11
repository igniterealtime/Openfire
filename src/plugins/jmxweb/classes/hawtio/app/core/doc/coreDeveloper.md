### Core plugin features

#### User Notifications

Notifications are provided by toastr.js.  In hawtio there's a simple function that wraps invoking toastr so it's pretty easy to pop up a notification:

```
notification('error', 'Oh no!');
notification('warning', 'Better watch out!');
```

The available levels are 'info', 'success', 'warning' and 'error'.  It's also possible to supply an options object as the last argument, a good way to use this is to provide an onClick handler, for example:

```
notification('error', 'Help me!', { onclick: function() { Logger.info('hey!'); } });
```

onHidden can be another good way to trigger something when the notification disappears:

```
notification('info', 'Did Stuff!', { onHIdden: function() { Logger.info('message hidden!') } });
```

By default for warning or error notifications clicking on the notification will show hawtio's log console, but it will also still execute the onclick afterwards if passed.  If some other behavior is desired or if it wouldn't make sense to open the console just pass an options object with a do-nothing onclick function.


#### Logging

Logging in hawtio plugins can be done either by using console.* functions or by using hawtio's Logging service.  In either case logs are routed to hawtio's logging console as well as the javascript console.  The log level is controlled in the preferences page.

The logging API is consistent with many other log APIs out there, for example:

```
Logger.info("Some log at info level");
Logger.warn("Oh snap!");
```

The Logger object has 4 levels it can log at, debug, info, warn and error.  In hawtio messages logged at either warn or error will result in a notification.

It's also possible to create a named logger.  Named loggers just prefix the log statements, for example:

```
Logger.get('MyPlugin').debug('Hey, something happened!');
```






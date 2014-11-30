/*
 * HTML5 Notifications
 * @version 1.0
 * @author Jonatan Männchen <jonatan@maennchen.ch>
 * @author Melissa Adamaitis <madamei@mojolingo.com>
 *
 * Notify user if new messages come in.
 */

var CandyShop = (function(self) { return self; }(CandyShop || {}));

CandyShop.Notifications = (function(self, Candy, $) {
  /** Object: _options
   * Options for this plugin's operation
   *
   * Options:
   *   (Boolean) notifyNormalMessage - Notification on normalmessage. Defaults to false
   *   (Boolean) notifyPersonalMessage - Notification for private messages. Defaults to true
   *   (Boolean) notifyMention - Notification for mentions. Defaults to true
   *   (Integer) closeTime - Time until closing the Notification. (0 = Don't close) Defaults to 3000
   *   (String)  title - Title to be used in notification popup. Set to null to use the contact's name.
   *   (String)  icon - Path to use for image/icon for notification popup.
   */
  var _options = {
    notifyNormalMessage: false,
    notifyPersonalMessage: true,
    notifyMention: true,
    closeTime: 3000,
    title: null,
    icon: window.location.origin + '/' + Candy.View.getOptions().assets + '/img/favicon.png'
  };

  /** Function: init
   * Initializes the notifications plugin.
   *
   * Parameters:
   *   (Object) options - The options to apply to this plugin
   *
   * @return void
   */
  self.init = function(options) {
    // apply the supplied options to the defaults specified
    $.extend(true, _options, options);

    // Just init if notifications are supported
    if (window.Notification) {
      // Setup Permissions (has to be kicked on with some user-events)
      jQuery(document).one('click keydown', self.setupPermissions);

      // Add Listener for Notifications
      $(Candy).on('candy:view.message.after-show', self.handleOnShow);
    }
  };

  /** Function: checkPermissions
   * Check if the plugin has permission to send notifications.
   *
   * @return boid
   */
  self.setupPermissions = function() {
    // Check if permissions is given
    if (window.Notification !== 0) { // 0 is PERMISSION_ALLOWED
      // Request for it
      window.Notification.requestPermission();
    }
  };

  /** Function: handleOnShow
   * Descriptions
   *
   * Parameters:
   *   (Array) args
   *
   * @return void
   */
  self.handleOnShow = function(e, args) {
    // Check if window has focus, so no notification needed
    if (!document.hasFocus()) {
      if(_options.notifyNormalMessage ||
        (self.mentionsMe(args.message) && _options.notifyMention) ||
        (_options.notifyPersonalMessage && Candy.View.Pane.Chat.rooms[args.roomJid].type === 'chat')) {
        // Create the notification.
        var title = !_options.title ? args.name : _options.title ,
          notification = new window.Notification(title, {
          icon: _options.icon,
          body: args.message
        });

        // Close it after 3 Seconds
        if(_options.closeTime) {
          window.setTimeout(function() { notification.close(); }, _options.closeTime);
        }
      }
    }
  };

  self.mentionsMe = function(message) {
    var message = message.toLowerCase(),
        nick = Candy.Core.getUser().getNick().toLowerCase(),
        cid = Strophe.getNodeFromJid(Candy.Core.getUser().getJid()).toLowerCase(),
        jid = Candy.Core.getUser().getJid().toLowerCase();
    if (message.indexOf(nick) === -1 &&
      message.indexOf(cid) === -1 &&
      message.indexOf(jid) === -1) {
      return false;
    }
    return true;
  };

  return self;
}(CandyShop.Notifications || {}, Candy, jQuery));

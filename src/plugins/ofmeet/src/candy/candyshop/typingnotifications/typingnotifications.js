/** File: typingnotifications.js
 * Candy Plugin Typing Notifications
 * Author: Melissa Adamaitis <madamei@mojolingo.com>
 */

var CandyShop = (function(self) { return self; }(CandyShop || {}));

CandyShop.TypingNotifications = (function(self, Candy, $) {
  /** Object: about
   *
   * Contains:
   *  (String) name - Candy Plugin Typing Notifications
   *  (Float) version - Candy Plugin Typing Notifications
   */
  self.about = {
    name: 'Candy Plugin Typing Notifications',
    version: '1.0'
  };

  /**
   * Initializes the Typing Notifications plugin with the default settings.
   */
  self.init = function(){
    // After a room is added, make sure to tack on a little div that we can put the typing notification into.
    $(Candy).on('candy:view.private-room.after-open', function(ev, obj){
      self.addTypingNotificationDiv(obj);
    });
    // When a typing notification is recieved, display it.
    $(Candy).on('candy:core.message.chatstate', function(ev, obj) {
      var pane, chatstate_string;
      pane = Candy.View.Pane.Room.getPane(obj.roomJid);
      chatstate_string = self.getChatstateString(obj.chatstate, obj.name);
      $(pane).find('.typing-notification-area').html(chatstate_string);
      return true;
    });
  };

  self.getChatstateString = function(chatstate, name) {
    switch (chatstate) {
      case 'paused': return name + ' has entered text.';
      case 'inactive': return name + ' is away from the window.';
      case 'composing': return name + ' is composing...';
      case 'gone': return name + ' has closed the window.';
      default: return '';
    }
  };

  self.addTypingNotificationDiv = function(obj){
    var pane_html = Candy.View.Pane.Room.getPane(obj.roomJid),
        typing_notification_div_html = '<div class="typing-notification-area"></div>';
    $(pane_html).find('.message-form-wrapper').append(typing_notification_div_html);
  };

  return self;
}(CandyShop.TypingNotifications || {}, Candy, jQuery));

/* @author: Prabuddha Chakraborty */

exports.command = function() {
var client = this;

client
    .moveToElement('#wp-admin-bar-my-account a.ab-item',1,1)
    .pause(100)
    .assert.elementPresent('#wp-admin-bar-my-account-media.menupop a.ab-item')
    .click('#wp-admin-bar-my-account-media.menupop a.ab-item')
    .waitForElementVisible('body', 2500)
    .pause(2000)
    .getTitle(function(title) {
        console.log("We are in Media Page :"+title);
      })

return this;
};

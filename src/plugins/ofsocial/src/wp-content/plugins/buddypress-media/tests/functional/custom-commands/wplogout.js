

exports.command = function() {
var client = this;

client
    .pause(1000)
    .moveToElement('#wp-admin-bar-my-account a.ab-item',1,1)  //move to top RHS
    .click('#wp-admin-bar-logout a.ab-item')
    .pause(2000)
    .waitForElementVisible('body', 2000)
    .getTitle(function(title) {
        console.log("Logged out..");
      })

return this;
};

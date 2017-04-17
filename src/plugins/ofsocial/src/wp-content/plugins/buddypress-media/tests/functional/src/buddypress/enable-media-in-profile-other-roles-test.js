/*
 @author: Prabuddha Chakraborty
 TestCase: Enable Media in Profile Other Users
 */

module.exports = {
  tags: ['buddypress', 'profile','upload'],
  'Step One : Enable media in profile From Admin Menu ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.BUDDYPRESS.BUDDYPRESS)
          .pause(2000)
        //select checkbox switch
          .getAttribute(data.SELECTORS.BUDDYPRESS.ENABLE_MEDIA_PROFILE, "checked", function(result) {
            //  console.log(result); //used for debug
                  if(result.value){
                          browser.verify.ok(result.value, 'Checkbox is selected');
                          console.log('check box is already enabled');
                  }else{
                          browser.click(data.SELECTORS.BUDDYPRESS.ENABLE_MEDIA_PROFILE);
                          browser.click(data.SELECTORS.SUBMIT);
                } })
            .pause(1000)
            .wplogout()
          },
      'step two: Login from TestEditor' : function (browser) {
            var data = browser.globals;
                  browser
                  .wplogin(data.URLS.LOGIN,data.TESTEDITORUSERNAME,data.TESTEDITORPASSWORD)
                  .goToProfile()
                  .click('#user-media')
                  .click('#rtm_show_upload_ui')
                  .click('.rtm-select-files')
                  .setValue('input[type=file]', require('path').resolve(data.PATH.TEST_IMAGE))
                  .click('.start-media-upload')
                  .pause(6000)
                  .refresh()
                  .getText('.rtmedia-list-item a.rtmedia-list-item-a .rtmedia-item-title h4',function(result){
                      browser.assert.equal(result.value, 'test', 'image uploaded successfully');
                     })
                .wplogout()
    },
      'step three: Login from Author' : function (browser) {
          var data = browser.globals;
              browser
                .wplogin(data.URLS.LOGIN,data.TESTAUTHORUSERNAME,data.TESTAUTHORPASSWORD)
                .goToProfile()
                .click('#user-media')
                .click('#rtm_show_upload_ui')
                .click('.rtm-select-files')
                .setValue('input[type=file]', require('path').resolve(data.PATH.TEST_IMAGE))
                .click('.start-media-upload')
                .pause(6000)
                .refresh()
                .getText('.rtmedia-list-item a.rtmedia-list-item-a .rtmedia-item-title h4',function(result){
                  browser.assert.equal(result.value, 'test', 'image uploaded successfully');
                   })
                .wplogout()
  },
      'step three: Login from Subscriber' : function (browser) {
          var data = browser.globals;
            browser
              .wplogin(data.URLS.LOGIN,data.TESTSUBSCRIBERUSERNAME,data.TESTSUBSCRIBERPASSWORD)
              .goToProfile()
              .click('#user-media')
              .click('#rtm_show_upload_ui')
              .click('.rtm-select-files')
              .setValue('input[type=file]', require('path').resolve(data.PATH.TEST_IMAGE))
              .click('.start-media-upload')
              .pause(6000)
              .refresh()
              .getText('.rtmedia-list-item a.rtmedia-list-item-a .rtmedia-item-title h4',function(result){
                  browser.assert.equal(result.value, 'test', 'image uploaded successfully');
               })
            .wplogout()
        },
    'step three: Login from Subscriber' : function (browser) {
      var data = browser.globals;
            browser
            .wplogin(data.URLS.LOGIN,data.TESTCONTRIBUTORUSERNAME,data.TESTCONTRIBUTORPASSWORD)
            .goToProfile()
            .click('#user-media')
            .click('#rtm_show_upload_ui')
            .click('.rtm-select-files')
            .setValue('input[type=file]', require('path').resolve(data.PATH.TEST_IMAGE))
            .click('.start-media-upload')
            .pause(6000)
            .refresh()
            .getText('.rtmedia-list-item a.rtmedia-list-item-a .rtmedia-item-title h4',function(result){
                browser.assert.equal(result.value, 'test', 'image uploaded successfully');
             })
          .wplogout()
          .end();
  }
};

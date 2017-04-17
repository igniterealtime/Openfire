/*
 @author: Prabuddha Chakraborty
 TestCase: To Check Organise Media In album Test
 */
module.exports = {
  tags: ['buddypress', 'album','upload'],
  'Step One : Enable media in profile  ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.BUDDYPRESS.BUDDYPRESS)
          .pause(2000)
          //select checkbox switch
          .getAttribute(data.SELECTORS.BUDDYPRESS.ENABLE_MEDIA_ALBUM, "checked", function(result) {
              if(result.value){
                          browser.verify.ok(result.value, 'Checkbox is selected');
                          console.log('check box is already enabled');
                  }else{
                          browser.click(data.SELECTORS.BUDDYPRESS.ENABLE_MEDIA_ALBUM);
                          browser.click(data.SELECTORS.SUBMIT);
                } })
            .pause(1000)
          },
      'step two: Upload and Check Album ' : function (browser) {
            browser
            .goToMedia()
            .click('#user-media')
            .click('#rtmedia-nav-item-albums')
            .click('#rtm-media-options-list .js .rtmedia-action-buttons')
            .click('#rtm-media-options-list .js .rtm-options .rtmedia-reveal-modal')
            .pause(500)
            .click('#rtmedia_album_name')
            .setValue('input[id="rtmedia_album_name"]', 'New_Album')
            .click('#rtmedia_create_new_album')
            .waitForElementVisible('.rtmedia-success', 1500)
            .verify.containsText('.rtmedia-success',"New_Album album created successfully.")
            .click('.mfp-close')
            .pause(200)
            .wplogout()
            .end();
        }
  };

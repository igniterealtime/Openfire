/*
 @author: Prabuddha Chakraborty
 TestCase: Music Single Player Test
*/
module.exports = {
  tags: ['mediasize', 'music','upload'],
  'Step One : Set width single player width  ' : function (browser){
    var data = browser.globals;
      browser
          .maximizeWindow()
          .wplogin(data.URLS.LOGIN,data.TESTADMINUSERNAME,data.TESTADMINPASSWORD)
          .openrtMediaSettings()
          .click(data.SELECTORS.MEDIASIZES.MEDIASIZES)
          .pause(2000)
          .clearValue(data.SELECTORS.MEDIASIZES.MUSIC_SINGLEPLAYER_WIDTH)
          .setValue(data.SELECTORS.MEDIASIZES.MUSIC_SINGLEPLAYER_WIDTH,'200') //set size:200
          .click(data.SELECTORS.SUBMIT)
          .pause(1000)
    //disable lightbox
          .click(data.SELECTORS.DISPLAY.DISPLAY)
  //disable lightbox checkbox switch
          .getAttribute(data.SELECTORS.DISPLAY.ENABLE_LIGHTBOX, "checked", function(result) {
            //  console.log(result); //used for debug
                  if(result.value){
                    browser.click(data.SELECTORS.DISPLAY.ENABLE_LIGHTBOX);
                    browser.click(data.SELECTORS.SUBMIT);
                }else{
                  console.log('Light box is already disabled');
                } })
            .pause(1000)
          },
    'step two: Check on Frontend ' : function (browser) {
      var data = browser.globals;
        browser
            .goToMedia()
            .click('#rtmedia-nav-item-music')
            .waitForElementVisible('body', 1500)
            .click('#rtm_show_upload_ui')
            .click('.rtm-select-files')
            .setValue('input[type=file]', require('path').resolve(data.PATH.TEST_MUSIC))
            .click('.start-media-upload')
            .pause(8000)
            .refresh()
            .click('.rtmedia-item-thumbnail')
            .getElementSize(".mejs-container", function(result) {  //#mep_0
                  this.assert.equal(result.value.width, 200);
                  console.log('set value for width are equal');
            })
            .wplogout()
            .end();
        }
};

var CandyShop = (function(self) {return self;}(CandyShop || {}));

/**
 * Class: Shows a list of rooms upon connection and adds a little icon to bring list of rooms
 */
CandyShop.RoomPanel = (function(self, Candy, Strophe, $) {
    var _options = {
         // domain that hosts the muc rooms, only required if autoDetectRooms is enabled
        mucDomain: '',

        // allow you to force a list of rooms, only required if autoDetectRoom is disabled
        roomList: [],

        // show room list if all rooms are closed, default value is true. [optional]
        showIfAllTabClosed: true,

        // detect rooms before showing list, default value is true. [optional]
        autoDetectRooms: true,

        // how long in seconds before refreshing room list, default value is 600. [optional]
        roomCacheTime: 600
        
    };
    
    var _lastRoomUpdate = 0;
    self.init = function(options) {
        
        $.extend(_options, options);
        self.applyTranslations();
        
        
        /* Overwrite candy allTabsClosed function not
         *  to disconnect when all tags are closed */
        if (_options.showIfAllTabClosed) {
            Candy.View.Pane.Chat.allTabsClosed = function () {
                CandyShop.RoomPanel.showRoomPanel();
                return;
            };
        } //if

        var html = '<li id="roomPanel-control" data-tooltip="' + $.i18n._('candyshopRoomPanelListRoom') + '"></li>';
        $('#chat-toolbar').prepend(html);
        $('#roomPanel-control').click(function() {
            CandyShop.RoomPanel.showRoomPanel();
        });

        Candy.Core.Event.addObserver(Candy.Core.Event.KEYS.CHAT, {update: function(obj, data) {
            if (data.type == 'connection') {
                if (Strophe.Status.CONNECTED == data.status) {
                    /* only show room window if not already in a room, timeout is to let some time for auto join to execute */
                    setTimeout(CandyShop.RoomPanel.showRoomPanelIfAllClosed, 500);
                } //if
            } //if
            return true;
        }});
    
    };

    self.showRoomPanelIfAllClosed = function() {
        
        var roomCount = 0;
        var rooms = Candy.Core.getRooms();
        for (k in rooms) {
            if (rooms.hasOwnProperty(k)) {
                roomCount++;
            } //if
        } //for

        if (roomCount == 0) {
            CandyShop.RoomPanel.showRoomPanel();
        } //if
    }
    
    self.updateRoomList = function (iq) {
        
        var newRoomList = [];
        $('item', iq).each(function (index, value) {
            var name = $(value).attr('name');
            var jid = $(value).attr('jid');
            
            if (typeof name == 'undefined') {
                name = jid.split('@')[0];
            } //if
            
            newRoomList.push({
                name: name,
                jid: jid
            });
        });

        _options.roomList = newRoomList;
        _lastRoomUpdate = Math.round(new Date().getTime() / 1000);
        
        self.showRoomPanel();
    };

    self.showRoomPanel = function() {

            /* call until connecting modal is gone */
            if ($('#chat-modal').is(':visible')) {
                setTimeout(CandyShop.RoomPanel.showRoomPanel, 100);
            } else {
                var timeDiff = Math.round(new Date().getTime() / 1000) - _options.roomCacheTime;
                if (_options.autoDetectRooms && timeDiff > _lastRoomUpdate ) {
                    /* sends a request to get list of rooms user for the room */
                    var iq = $iq({type: 'get', from: Candy.Core.getUser().getJid(), to: _options.mucDomain  , id: 'findRooms1'})
                        .c('query', {xmlns: Strophe.NS.DISCO_ITEMS});
                    
                    Candy.Core.getConnection().sendIQ(iq, self.updateRoomList);
                } else {

                    var html = Mustache.to_html(CandyShop.RoomPanel.Template.rooms, {
                            title: $.i18n._('candyshopRoomPanelChooseRoom'),
                            roomList: _options.roomList
                    });
                    Candy.View.Pane.Chat.Modal.show(html,true);

                    $('.roomList a').bind('click', function(e) {
                        var roomJid = this.href.split('#')[1];
                        Candy.Core.Action.Jabber.Room.Join(roomJid);
                        Candy.View.Pane.Chat.Modal.hide();
                        e.preventDefault();
                    });
                    
                } //if
                
            } //if

            return true;
    };

    self.applyTranslations = function() {
        var translations = {
            'en' : ['List Rooms', 'Choose Room To Join'],
            'ru' : ['Список комнат', 'Выберите комнату'],
            'de' : ['Verfügbare Räume anzeigen', 'Verfügbare Räume'],
            'fr' : ['Choisir une salle', 'Liste des salles'],
            'nl' : ['Choose Room To Join', 'List Rooms'],
            'es' : ['Choose Room To Join', 'List Rooms'],
        };
        $.each(translations, function(k, v) {
            if(Candy.View.Translation[k]) {
                Candy.View.Translation[k].candyshopRoomPanelListRoom = v[0];
                Candy.View.Translation[k].candyshopRoomPanelChooseRoom = v[1];
            }
        });
    };

    return self;
}(CandyShop.RoomPanel || {}, Candy, Strophe, jQuery));

CandyShop.RoomPanel.Template = (function (self) {
    var roomParts = [
        '<div class="roomList">',
            '<h2>{{title}}</h2>',
            '<ul>',
                '{{#roomList}}',
                    '<li><a href="#{{jid}}">{{name}}</a></li>',
                '{{/roomList}}',
            '</ul>',
        '</div>'
    ];
    
    self.rooms = roomParts.join('');
    
    return self;
})(CandyShop.RoomPanel.Template || {});

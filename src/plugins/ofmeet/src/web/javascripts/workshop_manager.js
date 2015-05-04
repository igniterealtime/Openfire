(function() {
  var WorkshopManager,
    __bind = function(fn, me){ return function(){ return fn.apply(me, arguments); }; };

  this.EventCalendar = (function() {
    function EventCalendar(args) {
    var that = this;
      this.handle_event_click = __bind(this.handle_event_click, this);
      var data, parent_view;
      parent_view = args.parent_view, data = args.data;
      this.construct_view(parent_view);
      this.view.fullCalendar({
        defaultDate: moment().format('YYYY-MM-DD'),
        selectable: true,
        editable: true,
        theme: true,
        nextDayThreshold: '01:00:00',
        header: {
          left: 'month, agendaWeek, agendaDay',
          center: 'title',
          right: 'prev, next, today'
        },
        events: data.events,
        eventRender: (function(_this) {
          return function(event, element) {
            if (event.type != null) {
              element.addClass(event.type);
            }
            element.on('click', function(e) {
              _this.handle_event_click(e, event, element);
              return false;
            });
            return element.on('dblclick', function(e) {
              _this.handle_event_click(e, event, element, true);
              return false;
            });
          };
        })(this),
	select: function(start, end) {
          event_data = args.workshopManager.get_new_event_data(start, end);
          
          if (args.workshopManager.event_calendar.selected != null) {
            event_data.start = args.workshopManager.event_calendar.selected.start.format();
            event_data.end = args.workshopManager.event_calendar.selected.end.format();
          }
          args.workshopManager.event_calendar.render_event(event_data);
          args.workshopManager.event = args.workshopManager.event_calendar.get_event(event_data.id);
          args.workshopManager.update_details(args.workshopManager.event);
          args.workshopManager.event_input.open(args.workshopManager.event, true);
	}
      });
      $('.fc-header-left').addClass('show-for-medium-up');
      this.view.on('click', (function(_this) {
        return function() {
          _this.handle_event_click();
          return false;
        };
      })(this));
    }

    EventCalendar.prototype.handle_event_click = function(jsEvent, event, element, dblclick) {   
      var _ref;
      $('.fc-event').removeClass('selected');
      if ((_ref = $(jsEvent != null ? jsEvent.currentTarget : void 0)) != null) {
        _ref.addClass('selected');
      }
      return $(this).trigger('event_clicked', {
        jsEvent: jsEvent,
        event: event,
        element: element,
        dblclick: dblclick
      });
    };

    EventCalendar.prototype.update_view = function(event) {
      return this.view.fullCalendar('updateEvent', event);
    };

    EventCalendar.prototype.delete_event = function(event) {
      return this.view.fullCalendar('removeEvents', event._id);
    };

    EventCalendar.prototype.render_event = function(event_data) {
      return this.view.fullCalendar('renderEvent', event_data);
    };

    EventCalendar.prototype.get_event = function(event_id) {
      return this.view.fullCalendar('clientEvents', event_id)[0];
    };

    EventCalendar.prototype.construct_view = function(parent_view) {
      this.view = $("<div id='event_calendar'></div>");
      return parent_view.append(this.view);
    };

    return EventCalendar;

  })();

  this.EventDetails = (function() {
    function EventDetails(args) {
      var parent_view;
      parent_view = args.parent_view, this.data = args.data, this.utilities = args.utilities, this.workshopManager = args.workshopManager;
      this.construct_view(parent_view);
    }

    EventDetails.prototype.construct_view = function(parent_view) {
      this.view = $("<div class='event_details'></div>");
      parent_view.append(this.view);
      return this.view.on('click', (function(_this) {
        return function() {
          $(_this).trigger('view_clicked');
          return false;
        };
      })(this));
    };

    EventDetails.prototype.update_view = function(event) {
      var from_str, to_str;
      this.view.empty();
      if (event == null) {
        return;
      }
      this.view.append($("<h6>" + event.title + "</h6>"));
      from_str = "" + (event.start.format('YYYY-MM-DD'));
      if (!event.allDay) {
        from_str = from_str + (" &nbsp;" + (event.start.format('hh:mm A')));
      }
      if (event.allDay) {
        if (event.end) to_str = "" + (event.end.clone().subtract(1, 'day').format('YYYY-MM-DD'));
      } else {
        if (event.end) to_str = "" + (event.end.format('YYYY-MM-DD')) + " &nbsp;" + (event.end.format('hh:mm A'));
      }
      if (from_str === to_str) {
        this.view.append($("<div><small>Date</small>&nbsp;&nbsp;" + from_str + "</div>"));
      } else {
        this.view.append($("<div><small>From</small>&nbsp;&nbsp;" + from_str + "</div>"));
        this.view.append($("<div><small>To</small>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + to_str + "</div>"));
      }
      if ((event.users != null) && event.users !== 'none') {
        this.view.append($("<div class='details_users'>" + event.users + "</div>"));
      }
      if ((event.groups != null) && event.groups !== 'none') {
        this.view.append($("<div class='details_groups'>" + event.groups + "</div>"));
      }      
      if ((event.description != null) && event.description.length > 0) {
        this.view.append($("<div class='details_description'>" + event.description + "</div>"));
      }
      
      this.save_button = $("<div class='details_register tiny button'>Save</div>");

      this.save_button.on('click', (function(_this) {      
        return function() { 
        	
		var eventsFromCalendar = _this.workshopManager.event_calendar.view.fullCalendar( 'clientEvents');
		var eventsForCookie = [];

		$.each(eventsFromCalendar, function(index,value) {
			var event = new Object();
			event.id = value.id;
			event.start = value.start;
			event.startTime = moment.utc(value.start).valueOf() - (moment().utcOffset() * 60 * 1000);
			event.end = value.end;
			event.endTime = moment.utc(value.end).valueOf() - (moment().utcOffset() * 60 * 1000);			
			event.title = value.title;
			event.processed = false;
			event.description = value.description;
			event.room = roomId;
			eventsForCookie.push(event);
		});        	
        	
        	document.getElementById("calendarevents").value = JSON.stringify(eventsForCookie);
        	document.getElementById("calendarform").submit();
          return false;
        };
      })(this));
      
      return this.view.append(this.save_button);
    };

    return EventDetails;

  })();

  this.EventInput = (function() {
    function EventInput(args) {
      this.parent_view = args.parent_view, this.data = args.data, this.utilities = args.utilities;
      this.construct_view();
    }

    EventInput.prototype.construct_view = function() {
      this.view = $("<div id='event_input'></div>");
      this.save_button = $("<div class='tiny button right'>Ok</div>");
      this.cancel_button = $("<div class='tiny secondary button right'>Cancel</div>");
      this.save_button.on('click', (function(_this) {      
        return function() {      
          if (_this.validate_view()) {
            _this.update_data();
            $(_this).trigger('render_event', {
              event: _this.event
            });
            _this.update_data();
            $(_this).trigger('event_saved', {
              event: _this.event
            });
            _this.close();          
          }           
          return false;
        };
      })(this));
      this.cancel_button.on('click', (function(_this) {
        return function() {
          _this.close();
          return false;
        };
      })(this));
      this.header_view = $("<h5></h5>");
      this.title_view = $("<input id='event_title' type='text' placeholder='Event title' />");
      this.start_date_view = $("<input class='date_input' type='date' />");
      this.start_date_label = $("<label class='small-12 medium-5 columns'>Start date</label>").append(this.start_date_view);
      this.start_time_view = $("<input class='time_input' type='time' />");
      this.start_time_label = $("<label class='small-12 medium-5 columns'>Start time</label>").append(this.start_time_view);
      this.end_date_view = $("<input class='date_input' type='date'/>");
      this.end_date_label = $("<label class='small-12 medium-5 columns'>End date</label>").append(this.end_date_view);
      this.end_time_view = $("<input class='time_input' type='time'/>");
      this.end_time_label = $("<label class='small-12 medium-5 columns'>End time</label>").append(this.end_time_view);
      this.all_day_view = $("<input id='all_day_input' type='checkbox' />");
      this.all_day_label = $("<label class='small-12 medium-2 columns'>All day</label>").append(this.all_day_view);
      this.date_time_inputs = [this.all_day_label, this.start_date_label, this.start_time_label, this.end_date_label, this.end_time_label];
      this.date_time_view = $("<div class='row'></div>").append(this.date_time_inputs);
      this.users_view = $("<input id='users_input' type='text' placeholder='List of users seperated by commas' value='" + users + "' />");
      this.groups_view = $("<input id='groups_input' type='text' placeholder='List of groups seperated by commas' value='" + groups + "' />");      
      this.description_view = $("<textarea id='description_input' wrap='soft'></textarea>");
      this.view.append([this.header_view = $("<h5></h5>"), $("<label>Name</label>").append(this.title_view), this.date_time_view, $("<div class='row'></div>").append([$("<label class='small-12 medium-5 columns'>Users</label>").append(this.users_view), $("<label class='small-12 medium-5 columns'>    Groups</label>").append(this.groups_view)]),     $("<div></div>").append($("<label>Description</label>").append(this.description_view)), this.delete_button, this.copy_button, this.save_button/*, this.cancel_button*/]);
      
      this.view.dialog({
        appendTo: this.parent_view,
        dialogClass: 'workshop_manager_dialog',
        position: {
          my: 'center',
          at: 'center',
          of: this.parent_view
        },
        autoOpen: false,
        resizable: false,
        draggable: false,
        modal: true
      });
      return this.all_day_view.on('change', (function(_this) {
        return function() {
          return _this.update_all_day_view();
        };
      })(this));
    };

    EventInput.prototype.open = function(event, is_new) {
      var width;
      this.update_view(event, is_new);
      this.validate_view();
      width = Math.max(this.parent_view.width() * 2 / 3, 325);
      this.view.dialog('option', 'width', width);
      return this.view.dialog('open');
    };

    EventInput.prototype.close = function() {
      return this.view.dialog('close');
    };

    EventInput.prototype.validate_view = function() {
      var add_error, all_day, end, is_valid, start;
      all_day = this.all_day_view.prop('checked');
      this.date_time_inputs.map(function(x) {
        x.removeClass('error');
        return x.children('small.error').remove();
      });
      add_error = function(label, msg) {
        label.addClass('error');
        return label.append($("<small class='error'>" + msg + "</small>"));
      };
      is_valid = true;
      if (!moment(this.start_date_view.val()).isValid()) {
        add_error(this.start_date_label, 'please enter a valid date');
        is_valid = false;
      }
      if (!moment(this.end_date_view.val()).isValid()) {
        add_error(this.end_date_label, 'please enter a valid date');
        is_valid = false;
      }
      if (is_valid && moment(this.start_date_view.val()).isAfter(moment(this.end_date_view.val()))) {
        add_error(this.start_date_label, 'event must end after it starts');
        is_valid = false;
      }
      if (is_valid && !all_day) {
        start = moment(this.start_date_view.val() + 'T' + this.start_time_view.val());
        end = moment(this.end_date_view.val() + 'T' + this.end_time_view.val());
        if (!start.isValid()) {
          add_error(this.start_time_label, 'please enter a valid time (HH:MM)');
          is_valid = false;
        }
        if (!end.isValid()) {
          add_error(this.end_time_label, 'please enter a valid time (HH:MM)');
          is_valid = false;
        }
        if (is_valid && start.isAfter(end)) {
          add_error(this.start_time_label, 'event must end after it starts');
          is_valid = false;
        }
      }
      return is_valid;
    };

    EventInput.prototype.update_data = function() {
      var all_day;
      if (this.event == null) {
        this.event = {};
      }
      this.event.title = this.title_view.val();
      all_day = this.all_day_view.prop('checked');
      this.event.allDay = all_day;
      if (all_day) {
        this.event.start = this.start_date_view.val();
        this.event.end = moment.utc(this.end_date_view.val()).add(1, 'day').format('YYYY-MM-DD');
      } else {
        this.event.start = this.start_date_view.val() + 'T' + this.start_time_view.val();
        this.event.end = this.end_date_view.val() + 'T' + this.end_time_view.val();
      }
      this.event.users = this.users_view.prop('value');
      this.event.groups = this.groups_view.prop('value');
      return this.event.description = this.description_view.val();
    };

    EventInput.prototype.update_all_day_view = function() {
      var all_day;
      all_day = this.all_day_view.prop('checked');
      this.utilities.add_remove_class(this.start_time_label, 'hide', all_day);
      this.utilities.add_remove_class(this.end_time_label, 'hide', all_day);
      this.utilities.add_remove_class(this.end_date_label, 'medium-offset-2', !all_day);
      if (all_day) {
        this.start_time_view.val('00:00');
        return this.end_time_view.val('00:00');
      }
    };

    EventInput.prototype.update_view = function(event, is_new) {
      var end_date, _ref, _ref1, _ref10, _ref11, _ref12, _ref13, _ref14, _ref2, _ref3, _ref4, _ref5, _ref6, _ref7, _ref8, _ref9;
      this.event = event;
      this.header_view.text(is_new ? 'Create event' : 'Edit event');
      this.title_view.val((_ref = this.event) != null ? _ref.title : void 0);
      this.start_date_view.val((_ref1 = this.event) != null ? _ref1.start.format('YYYY-MM-DD') : void 0);
      this.start_time_view.val((_ref2 = this.event) != null ? _ref2.start.format('HH:mm') : void 0);
      if ((_ref3 = this.event) != null ? _ref3.allDay : void 0) {
        end_date = (_ref4 = this.event) != null ? (_ref5 = _ref4.end) != null ? _ref5.clone().subtract(1, 'day').format('YYYY-MM-DD') : void 0 : void 0;
      } else {
        end_date = (_ref6 = this.event) != null ? (_ref7 = _ref6.end) != null ? _ref7.format('YYYY-MM-DD') : void 0 : void 0;
      }
      this.end_date_view.val(end_date);
      this.end_time_view.val((_ref8 = this.event) != null ? (_ref9 = _ref8.end) != null ? _ref9.format('HH:mm') : void 0 : void 0);
      this.users_view.prop('value', (_ref10 = this.event) != null ? _ref10.users : void 0);
      this.groups_view.prop('value', (_ref11 = this.event) != null ? _ref11.groups : void 0);
      this.all_day_view.prop('checked', (_ref13 = this.event) != null ? _ref13.allDay : void 0);
      this.description_view.val((_ref14 = this.event) != null ? _ref14.description : void 0);
      return this.update_all_day_view();
    };

    return EventInput;

  })();

  this.EventToolbar = (function() {
    function EventToolbar(args) {
      var parent_view;
      parent_view = args.parent_view;
      this.construct_view(parent_view);
    }

    EventToolbar.prototype.construct_view = function(parent_view) {
      this.view = $("<div class='event_toolbar clearfix'></div>");
      this.edit_button = $("<div class='tiny secondary button'><span class='fa fa-lg fa-pencil'></span></div>");
      this.copy_button = $("<div class='tiny secondary button'><span class='fa fa-lg fa-copy'></span></div>");
      this.delete_button = $("<div class='tiny alert button'><span class='fa fa-lg fa-trash-o'></span></div>");
      this.event_subtoolbar = $("<div class='event_subtoolbar left hide'></div>").append([this.edit_button, this.copy_button, this.delete_button]);
      this.view.append(this.event_subtoolbar);
      parent_view.append(this.view);
      this.edit_button.on('click', (function(_this) {
        return function() {
          $(_this).trigger('edit_event');
          return false;
        };
      })(this));
      this.copy_button.on('click', (function(_this) {
        return function() {
          $(_this).trigger('copy_event');
          return false;
        };
      })(this));
      return this.delete_button.on('click', (function(_this) {
        return function() {
          $(_this).trigger('delete_event');
          return false;
        };
      })(this));
    };

    EventToolbar.prototype.update_view = function(event) {
      if (event != null) {
        return this.event_subtoolbar.removeClass('hide');
      } else {
        return this.event_subtoolbar.addClass('hide');
      }
    };

    return EventToolbar;

  })();

  this.Utilities = (function() {
    function Utilities() {}

    Utilities.prototype.add_remove_class = function(jquery_obj, class_name, condition) {
      if (condition) {
        jquery_obj.addClass(class_name);
      } else {
        jquery_obj.removeClass(class_name);
      }
    };

    Utilities.prototype.find_object_matching = function(array, property, value) {
      var obj, _i, _len;
      if (!((array != null ? array.length : void 0) > 0 && (property != null) && (value != null))) {
        return null;
      }
      for (_i = 0, _len = array.length; _i < _len; _i++) {
        obj = array[_i];
        if (obj[property] === value) {
          return obj;
        }
      }
    };

    return Utilities;

  })();

  $(function() {
    return new WorkshopManager;
  });

  WorkshopManager = (function() {
    function WorkshopManager() {
      this.is_off_canvas_open = __bind(this.is_off_canvas_open, this);
      this.toggle_off_canvas = __bind(this.toggle_off_canvas, this);
      this.update_toolbars = __bind(this.update_toolbars, this);
      this.update_details = __bind(this.update_details, this);
      var data, utilities;
      this.construct_view();
      data = DATA;
      utilities = new Utilities;
      this.event_calendar = new EventCalendar({
        parent_view: this.calendar_view,
        data: data,
        workshopManager: this,
        utilities: utilities
      });
      this.event_details = new EventDetails({
        parent_view: this.details_view,
        workshopManager: this,        
        data: data,
        utilities: utilities
      });
      this.event_details_aside = new EventDetails({
        parent_view: this.details_aside_view,
        workshopManager: this,        
        data: data,
        utilities: utilities
      });
      this.event_toolbar = new EventToolbar({
        parent_view: this.toolbar_view,
        data: data,
        utilities: utilities
      });
      this.event_toolbar_aside = new EventToolbar({
        parent_view: this.toolbar_aside_view,
        data: data,
        utilities: utilities
      });
      this.event_input = new EventInput({
        parent_view: this.view,
        data: data,
        utilities: utilities
      });
      $(this.event_calendar).on('event_clicked', (function(_this) {
        return function(e, args) {
          var dblclick;
          _this.event = args.event, dblclick = args.dblclick;
          _this.update_details(_this.event);
          _this.update_toolbars(_this.event);
          if (dblclick) {
            return _this.event_input.open(_this.event);
          } else if (_this.is_small_media_query() && (_this.event != null)) {
            return _this.toggle_off_canvas();
          }
        };
      })(this));
      $(this.event_input).on('render_event', (function(_this) {
        return function(e, args) {
          return _this.event_calendar.update_view(args.event);
        };
      })(this));
      $(this.event_input).on('event_saved', (function(_this) {
        return function(e, args) {
          _this.event = args.event;
          _this.event_calendar.update_view(_this.event);
          return _this.update_details(_this.event);
        };
      })(this));
      $(this.event_toolbar).on('edit_event', (function(_this) {
        return function() {
          return _this.event_input.open(_this.event);
        };
      })(this));
      $(this.event_toolbar_aside).on('edit_event', (function(_this) {
        return function() {
          _this.event_input.open(_this.event);
          return _this.toggle_off_canvas();
        };
      })(this));
      $(this.event_toolbar).on('copy_event', (function(_this) {
        return function() {
          var event_data;
          event_data = _this.clone_event_data_from_event(_this.event);
          _this.event_calendar.render_event(event_data);
          _this.event = _this.event_calendar.get_event(event_data.id);
          return _this.update_details(_this.event);
        };
      })(this));
      $(this.event_toolbar_aside).on('copy_event', (function(_this) {
        return function() {
          var event_data;
          event_data = _this.clone_event_data_from_event(_this.event);
          _this.event_calendar.render_event(event_data);
          _this.event = _this.event_calendar.get_event(event_data.id);
          return _this.update_details(_this.event);
        };
      })(this));
      $(this.event_toolbar).on('delete_event', (function(_this) {
        return function() {
          _this.event_calendar.delete_event(_this.event);
          _this.event = null;
          _this.update_details();
          return _this.update_toolbars();
        };
      })(this));
      $(this.event_toolbar_aside).on('delete_event', (function(_this) {
        return function() {
          _this.event_calendar.delete_event(_this.event);
          _this.event = null;
          _this.update_details();
          _this.update_toolbars();
          return _this.toggle_off_canvas();
        };
      })(this));
      $(this.event_details_aside).on('view_clicked', (function(_this) {
        return function() {
          return _this.toggle_off_canvas();
        };
      })(this));
      $(window).on('resize', Foundation.utils.throttle((function(_this) {
        return function() {
          if (_this.is_off_canvas_open() && !_this.is_small_media_query()) {
            return _this.toggle_off_canvas();
          }
        };
      })(this), 300));
      
      //this.event_calendar.view.fullCalendar('gotoDate', '2014-09-01T00:00:00Z');
    }

    WorkshopManager.prototype.update_details = function(event) {
      this.event_details.update_view(event);
      return this.event_details_aside.update_view(event);
    };

    WorkshopManager.prototype.update_toolbars = function(event) {
      this.event_toolbar.update_view(event);
      return this.event_toolbar_aside.update_view(event);
    };

    WorkshopManager.prototype.toggle_off_canvas = function() {
      return this.off_canvas.foundation('offcanvas', 'toggle', 'move-left');
    };

    WorkshopManager.prototype.is_off_canvas_open = function() {
      return this.off_canvas.hasClass('move-left');
    };

    WorkshopManager.prototype.is_small_media_query = function() {
      var is_medium, is_small;
      is_small = matchMedia(Foundation.media_queries.small).matches;
      is_medium = matchMedia(Foundation.media_queries.medium).matches;
      return is_small && !is_medium;
    };

    WorkshopManager.prototype.construct_view = function() {
      var left_container, right_container;
      this.view = $('#workshop_manager');
      this.new_button = $("<div class='new_button tiny button left'> <span class='fa fa-lg fa-plus'></span>New event</div>");
      left_container = $("<div class='small-12 medium-8 columns'></div>");
      right_container = $("<div class='show-for-medium-up medium-4 columns'></div>");
      this.calendar_view = $("<div></div>");
      this.details_view = $("<div></div>");
      this.details_aside_view = $("<div></div>");
      this.toolbar_view = $("<div></div>");
      this.toolbar_aside_view = $("<div></div>");
      this.off_canvas = $("<div class='off-canvas-wrap' data-offcanvas></div>");
      this.off_canvas.append($("<div class='inner-wrap'></div>").append([$("<aside class='right-off-canvas-menu'></aside>").append([this.toolbar_aside_view, this.details_aside_view]), this.calendar_view, $("<a class='exit-off-canvas'></a>")]));
      return this.view.append([$("<div class='app_row row'></div"), left_container.append(this.off_canvas), right_container.append([this.toolbar_view, this.details_view])]);
    };

    WorkshopManager.prototype.get_new_event_data = function(start, end) {
      return {
        id: this.get_random_id(),
        title: 'New Meeting',
        start:start,
        end: end,
        allDay: false
      };
    };

    WorkshopManager.prototype.clone_event_data_from_event = function(event) {
      return {
        id: this.get_random_id(),
        title: "Copy of " + event.title,
        users: event.users,
        start: event.start.format(),
        end: event.end.format(),
        groups: event.groups,
        description: event.description,
        allDay: event.allDay
      };
    };

    WorkshopManager.prototype.get_random_id = function() {
      return Math.round(Math.random() * 1000000000);
    };

    return WorkshopManager;

  })();

}).call(this);

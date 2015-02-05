(function() {
  var __bind = function(fn, me){ return function(){ return fn.apply(me, arguments); }; };

  this.Woot.QuillAdapter = (function() {

    QuillAdapter.prototype.colors = ['rgba(139,0,139,0.4)', 'rgba(255,127,0,0.4)', 'rgba(238,44,44,0.4)', 'rgba(179,238,58,0.4)', 'rgba(28,134,238,0.4)'];

    function QuillAdapter(socket, editor_id, toolbar_id, authors_id, room_id) {
      this.authors_id = authors_id;
      this.room_id = room_id;
      this.contents = __bind(this.contents, this);
      this.contentsInit = __bind(this.contentsInit, this);
      this.ins = __bind(this.ins, this);
      this.del = __bind(this.del, this);
      this.attrib = __bind(this.attrib, this);
      this.cursorChange = __bind(this.cursorChange, this);
      this.cursorCreate = __bind(this.cursorCreate, this);
      this.selectionChange = __bind(this.selectionChange, this);
      this.textChange = __bind(this.textChange, this);
      this.socket = socket;
      this.site_id = Math.floor((Math.random() * 999) + 1);
      this.color = this.colors[Math.floor(Math.random() * 5)];
      this.editor = new Quill(editor_id, {
        modules: {
          'authorship': {
            authorId: authors_id,
            enabled: true
          },
          'multi-cursor': {
            timeout: 7000
          },
          'toolbar': {
            container: toolbar_id
          },
	  'image-tooltip': true,
	  'link-tooltip': true          
        },
        theme: 'snow'
      });
      this.site = new Woot.Site(this);

      this.socket.emit('woot_send', {
        type: 'cursor-create',
        id: this.site_id,
        color: this.color,
        sender: this.site_id,
        authors_id: this.authors_id,
        state: null
      });
      this.editor.on('text-change', this.textChange);
      this.editor.on('selection-change', this.selectionChange);
    }

    QuillAdapter.prototype.textChange = function(delta, source) {
      var di, i, index, j, l, last_retain, length, op, _len, _ref, _ref2, _ref3, _ref4, _ref5, _results;
      if (source !== 'user') return;
      index = 0;
      last_retain = 0;
      l = 0;
      _ref = delta.ops;
      for (di = 0, _len = _ref.length; di < _len; di++) {
        op = _ref[di];
        if (op.start || op.end) {
          while (last_retain < op.start) {
            this.site.generateDel(index + 1);
            last_retain++;
          }
          if (op.end === delta.startLength) op.end--;
          if ($.isEmptyObject(op.attributes)) {
            index += op.end - op.start;
          } else {
            for (j = _ref2 = op.start, _ref3 = op.end - 1; j <= _ref3; j += 1) {
              this.site.generateAttrib(++index, op.attributes);
            }
          }
          last_retain = op.end;
          if (op.end === delta.startLength - 1) last_retain++;
        } else {
          length = op.value.length;
          if (delta.startLength === 0 && di === delta.ops.length - 1) length--;
          for (i = 0, _ref4 = length - 1; i <= _ref4; i += 1) {
            this.site.generateIns(index, op.value.charAt(i), op.attributes);
            index++;
          }
        }
      }
      _results = [];
      for (i = last_retain, _ref5 = delta.startLength - 1; i <= _ref5; i += 1) {
        _results.push(this.site.generateDel(index));
      }
      return _results;
    };

    QuillAdapter.prototype.selectionChange = function(range) {
      if (range) {
        return this.socket.emit('woot_send', {
          type: 'cursor-change',
          id: this.site_id,
          authors_id: this.authors_id,
          char: this.site.ithVisible(range.end)
        });
      }
    };

    QuillAdapter.prototype.cursorCreate = function(op) {
	var author, el, ops, _i, _len, _ref;
	author = op.authors_id;
	
	if (!this.editor.getModule('multi-cursor').cursors[author]) 
	{
		this.editor.getModule('authorship').addAuthor(author, op.color);
		this.editor.getModule('multi-cursor').setCursor(author, this.editor.getLength() - 1, author, op.color);
		
		if (op.state && this.site.empty()) 
		{
		  this.site.string = op.state.string;
		  this.site.chars_by_id = op.state.chars_by_id;
		  this.site.pool = op.state.pool;
		  ops = [];
		  _ref = this.site.string;
		  for (_i = 0, _len = _ref.length; _i < _len; _i++) {
		    el = _ref[_i];
		    if (!(el.v && el.c)) continue;
		    ops.push({
		      value: el.c,
		      attributes: el.a
		    });
		  }
		  this.editor.updateContents({
		    startLength: 0,
		    endLength: ops.length,
		    ops: ops
		  });
		}
	}        
	if (this.site_id !== op.sender) 
	{
	  this.socket.emit('woot_send', {
	    type: 'cursor-create',
	    id: this.site_id,
	    color: this.color,
            authors_id: this.authors_id,	    
	    sender: op.sender,
	    state: {
	      string: this.site.string,
	      chars_by_id: this.site.chars_by_id,
	      pool: this.site.pool
	    }
	  });
	}
    };

    QuillAdapter.prototype.cursorChange = function(op) {
      var author, pos;
      author = op.authors_id;
      pos = this.site.visiblePos(op.char);
      return this.editor.getModule('multi-cursor').moveCursor(author, pos + 1);
    };

    QuillAdapter.prototype.attrib = function(op) {
      var contents, length, ops, pos;
      pos = this.site.visiblePos(op.char);
      contents = this.site.value();
      length = contents.length;
      ops = [];
      if (pos > 0) {
        ops.push({
          start: 0,
          end: pos
        });
      }
      ops.push({
        start: pos,
        end: pos + 1,
        attributes: op.attribs
      });
      if (pos < length) {
        ops.push({
          start: pos + 1,
          end: length + 1
        });
      }
      return this.editor.updateContents({
        startLength: length + 1,
        endLength: length + 1,
        ops: ops
      });
    };

    QuillAdapter.prototype.del = function(op) {
      var contents, length, ops, pos;
      pos = this.site.visiblePos(op.char) + 1;
      contents = this.site.value();
      length = contents.length + 1;
      ops = [];
      if (pos > 0) {
        ops.push({
          start: 0,
          end: pos - 1
        });
      }
      if (pos <= length) {
        ops.push({
          start: pos,
          end: length + 1
        });
      }
      return this.editor.updateContents({
        startLength: length + 1,
        endLength: length,
        ops: ops
      });
    };

    QuillAdapter.prototype.ins = function(op) {
      var contents, length, ops, pos;
      pos = this.site.visiblePos(op.char);
      contents = this.site.value();
      length = contents.length;
      if (length === 1) length = 0;
      ops = [];
      if (pos > 0) {
        ops.push({
          start: 0,
          end: pos
        });
      }
      ops.push({
        value: op.char.c,
        attributes: op.char.a
      });
      if (pos < length) {
        ops.push({
          start: pos,
          end: length
        });
      }
      return this.editor.updateContents({
        startLength: length,
        endLength: length + 1,
        ops: ops
      });
    };

    QuillAdapter.prototype.contentsInit = function(contents) {
      this.editor.setHTML(contents);
      return this.textChange(this.editor.getContents(), 'user');
    };

    QuillAdapter.prototype.contents = function() {
      return this.editor.getHTML();
    };

    return QuillAdapter;

  })();

}).call(this);

(function() {
  var Woot,
    __bind = function(fn, me){ return function(){ return fn.apply(me, arguments); }; };

  Woot = (typeof exports !== "undefined" && exports !== null) && exports || (this.Woot = {});

  Woot.Site = (function() {

    Site.prototype.start = {
      id: [0, 0],
      v: true,
      c: '',
      a: {},
      p: null,
      n: [999, 999]
    };

    Site.prototype.end = {
      id: [999, 999],
      v: true,
      c: '',
      a: {},
      p: [0, 0],
      n: null
    };

    function Site(editor) {
      this.autosave = __bind(this.autosave, this);
      this.execute = __bind(this.execute, this);
      this.receive = __bind(this.receive, this);
      this.isExecutable = __bind(this.isExecutable, this);
      this.integrateIns = __bind(this.integrateIns, this);
      this.integrateDel = __bind(this.integrateDel, this);
      this.integrateAttrib = __bind(this.integrateAttrib, this);
      this.generateAttrib = __bind(this.generateAttrib, this);
      this.generateDel = __bind(this.generateDel, this);
      this.generateIns = __bind(this.generateIns, this);
      this.ithVisible = __bind(this.ithVisible, this);
      this.value = __bind(this.value, this);
      this.contains = __bind(this.contains, this);
      this.subseq = __bind(this.subseq, this);
      this.insert = __bind(this.insert, this);
      this.visiblePos = __bind(this.visiblePos, this);
      this.pos = __bind(this.pos, this);
      this.empty = __bind(this.empty, this);      this.num = editor != null ? editor.site_id : void 0;
      this.socket = editor != null ? editor.socket : void 0;
      this.editor = editor;
      this.h = 0;
      this.string = [this.start, this.end];
      this.chars_by_id = {};
      this.chars_by_id['s' + this.start.id[0] + 'c' + this.start.id[1]] = this.start;
      this.chars_by_id['s' + this.end.id[0] + 'c' + this.end.id[1]] = this.end;
      this.pool = [];
      this.dirty = false;
    }

    Site.prototype.extend = function(object, properties) {
      var key, val;
      for (key in properties) {
        val = properties[key];
        object[key] = val;
      }
      return object;
    };

    Site.prototype.empty = function() {
      return this.string.length === 2;
    };

    Site.prototype.pos = function(c) {
      var el, i, _len, _ref;
      _ref = this.string;
      for (i = 0, _len = _ref.length; i < _len; i++) {
        el = _ref[i];
        if (c.id[0] === el.id[0] && c.id[1] === el.id[1]) return i;
      }
      return -1;
    };

    Site.prototype.visiblePos = function(c) {
      var el, i, pos, _len, _ref;
      pos = -1;
      _ref = this.string;
      for (i = 0, _len = _ref.length; i < _len; i++) {
        el = _ref[i];
        if (c.id[0] === el.id[0] && c.id[1] === el.id[1]) return pos;
        if (el.v) pos++;
      }
      return -1;
    };

    Site.prototype.insert = function(c, p) {
      var i, _ref;
      for (i = _ref = this.string.length - 1; _ref <= p ? i <= p : i >= p; _ref <= p ? i++ : i--) {
        this.string[i + 1] = this.string[i];
      }
      this.string[p] = c;
      return this.chars_by_id['s' + c.id[0] + 'c' + c.id[1]] = c;
    };

    Site.prototype.subseq = function(c, d) {
      var end, i, start, sub, _ref, _ref2;
      sub = [];
      start = this.pos(c);
      end = this.pos(d);
      if (start + 1 <= end - 1 && start > -1 && end > -1) {
        for (i = _ref = start + 1, _ref2 = end - 1; _ref <= _ref2 ? i <= _ref2 : i >= _ref2; _ref <= _ref2 ? i++ : i--) {
          sub.push(this.string[i]);
        }
      }
      return sub;
    };

    Site.prototype.contains = function(id) {
      var el, _i, _len, _ref;
      _ref = this.string;
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        el = _ref[_i];
        if (el.id[0] === id[0] && el.id[1] === id[1]) return true;
      }
      return false;
    };

    Site.prototype.value = function() {
      var el, visible, _i, _len, _ref;
      visible = '';
      _ref = this.string;
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        el = _ref[_i];
        if (el.v) visible += el.c;
      }
      return visible;
    };

    Site.prototype.ithVisible = function(i) {
      var el, p, _i, _len, _ref;
      p = 0;
      _ref = this.string;
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        el = _ref[_i];
        if (el.v && p++ === i) return el;
      }
      return null;
    };

    Site.prototype.generateIns = function(pos, char, attribs) {
      var c, cn, cp;
      if (attribs == null) attribs = {};
      this.h += 1;
      cp = this.ithVisible(pos);
      cn = this.ithVisible(pos + 1);
      c = {
        id: [this.num, this.h],
        v: true,
        c: char,
        a: attribs,
        p: cp.id,
        n: cn.id
      };
      this.integrateIns(c, cp, cn);
      return this.socket.emit('woot_send', {
        type: 'ins',
        char: c
      });
    };

    Site.prototype.generateDel = function(pos) {
      var c;
      c = this.ithVisible(pos);
      c.v = false;
      this.socket.emit('woot_send', {
        type: 'del',
        char: c
      });
      return this.dirty = true;
    };

    Site.prototype.generateAttrib = function(pos, attribs) {
      var c;
      c = this.ithVisible(pos);
      this.extend(c.a, attribs);
      this.socket.emit('woot_send', {
        type: 'attrib',
        char: c,
        attribs: attribs
      });
      return this.dirty = true;
    };

    Site.prototype.integrateAttrib = function(c, attribs) {
      this.extend(this.string[this.pos(c)].a, attribs);
      return this.dirty = true;
    };

    Site.prototype.integrateDel = function(c) {
      this.string[this.pos(c)].v = false;
      return this.dirty = true;
    };

    Site.prototype.integrateIns = function(c, cp, cn) {
      var cn_pos, cp_pos, d, i, l, n_pos, p_pos, sub, _i, _len;
      sub = this.subseq(cp, cn);
      if (sub.length === 0) {
        this.insert(c, this.pos(cn));
      } else {
        l = [];
        l.push(cp);
        cp_pos = this.pos(cp);
        cn_pos = this.pos(cn);
        for (_i = 0, _len = sub.length; _i < _len; _i++) {
          d = sub[_i];
          p_pos = this.pos(this.chars_by_id['s' + d.p[0] + 'c' + d.p[1]]);
          n_pos = this.pos(this.chars_by_id['s' + d.n[0] + 'c' + d.n[1]]);
          if (p_pos <= cp_pos && cn_pos <= n_pos) l.push(d);
        }
        l.push(cn);
        i = 1;
        while (i < l.length - 1 && (l[i].id[0] < c.id[0] || (l[i].id[0] === c.id[0] && l[i].id[1] < c.id[1]))) {
          i += 1;
        }
        this.integrateIns(c, l[i - 1], l[i]);
      }
      return this.dirty = true;
    };

    Site.prototype.isExecutable = function(op) {
      if (op.type === 'cursor-create' || op.type === 'contents-init') {
        return true;
      } else if (op.type === 'joined') {
      	return false;
      } else if (op.type === 'del' || op.type === 'attrib' || op.type === 'cursor-change') {      
        return this.contains(op.char.id);
      } else {
        return this.contains(op.char.p) && this.contains(op.char.n);
      }
    };

    Site.prototype.receive = function(op) {
      // console.log("receive", op);    
      var new_pool;
      if (this.isExecutable(op)) {
        this.execute(op);
      } else {
        this.pool.push(op);
      }
      new_pool = [];
      while (op = this.pool.shift()) {
        if (!this.execute(op)) new_pool.push(op);
      }
      return this.pool = new_pool;
    };

    Site.prototype.execute = function(op) {
      var cn, cp, _ref, _ref2, _ref3, _ref4, _ref5, _ref6;
      if (this.isExecutable(op)) {
        if (op.type === 'contents-init') {
          if ((_ref = this.editor) != null) _ref.contentsInit(op.contents);
        } else if (op.type === 'joined') {
        
        } else if (op.type === 'cursor-create') {
          if ((_ref2 = this.editor) != null) _ref2.cursorCreate(op);
        } else if (op.type === 'cursor-change') {
          if ((_ref3 = this.editor) != null) _ref3.cursorChange(op);
        } else if (op.type === 'attrib') {
          this.integrateAttrib(op.char, op.attribs);
          if ((_ref4 = this.editor) != null) _ref4.attrib(op);
        } else if (op.type === 'del') {
          this.integrateDel(op.char);
          if ((_ref5 = this.editor) != null) _ref5.del(op);
        } else {
          cp = this.chars_by_id['s' + op.char.p[0] + 'c' + op.char.p[1]];
          cn = this.chars_by_id['s' + op.char.n[0] + 'c' + op.char.n[1]];
          this.integrateIns(op.char, cp, cn);
          if ((_ref6 = this.editor) != null) _ref6.ins(op);
        }
        return true;
      } else {
        return false;
      }
    };

    Site.prototype.autosave = function() {
      if (!this.dirty) return;
      //this.socket.emit('woot_save', this.editor.contents());
      return this.dirty = false;
    };

    return Site;

  })();

}).call(this);

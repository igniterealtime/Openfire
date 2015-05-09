(function () {
	var importDoc = document.currentScript.ownerDocument;
	var proto = Object.create( HTMLElement.prototype );
	var phoney = window.ATT && window.ATT.phoneNumber || window.phoney;
		
	proto.createdCallback = function() {
		var that = this;
		var template = importDoc.querySelector('#dialerTemplate');		
		this.readAttributes();

		this.shadow = this.createShadowRoot();
		this.shadow.appendChild(template.content.cloneNode(true));
		
		this.addClickHandlers();
		this.number = '';
		this.numberField = this.shadow.querySelector('.numberEntry');
		this.callStatus = this.shadow.querySelector('#callStatus');
		this.dialpad = this.shadow.querySelector('#screen');
		
		
	    	this.button = this.shadow.querySelector('.call');  		

		this.boundKeyHandler = function () 
		{
			that.handleKeyDown.apply(that, arguments);
		};		
    		document.addEventListener('keydown', this.boundKeyHandler, true);  		
	};

	proto.readAttributes = function() {

	};

	proto.attributeChangedCallback = function( attrName, oldVal, newVal ) {

	};

	proto.addClickHandlers = function () 
	{
	    var self = this;
	    var buttons = this.shadow.querySelectorAll('button');
	    var callButton = this.shadow.querySelector('.call');   

	    Array.prototype.forEach.call(buttons, function (button) 
	    {
	      	button.addEventListener('click', function (e) 
	      	{
			var data = this.attributes['data-value'];
			var value = data && data.nodeValue;

			if (value)
			{
				if (value == 'del') {
				  	self.removeLastNumber();
				  	
				} else if (self.call) {
										
					var myEvent = new CustomEvent("Telephone.Dialer.Action", {detail: {action: value, call: self.call}});
					self.dispatchEvent(myEvent);					
				
				} else {
				  	self.addNumber(value);
				}
			}
			return false;
	      }, true);
	    });

	    if (callButton) 
	    {
	      callButton.addEventListener('click', function () 
	      {
		var myEvent = new CustomEvent("Telephone.Dialer.Button", {detail: {number: self.getNumber(), label: callButton.innerHTML}});
		self.dispatchEvent(myEvent);
		
	      }, false);
	    }
	};

	proto.getNumber = function () 
	{
		return this.number;
	};

	proto.getLabel = function () 
	{
	    	return this.button.innerHTML;
	};
	
	proto.setLabel = function (label) 
	{
	    	this.button.innerHTML = label
	};	

	proto.setNumber = function (number) 
	{
		var newNumber = phoney.parse(number);
		var oldNumber = this.number;
		var callable = phoney.getCallable(newNumber);
		
		this.number = newNumber;
		this.numberField.innerHTML = phoney.stringify(this.number); 
		
		if (callable) 
		{
			var myEvent = new CustomEvent("Telephone.Dialer.Number", {detail: {number: callable}});
			this.dispatchEvent(myEvent);
		}
	};

	proto.clear = function () 
	{
		this.setNumber('');
	};
	
	proto.addNumber = function (number) 
	{
		var newNumber = (this.getNumber() + '') + number;
		var myEvent = new CustomEvent("Telephone.Dialer.Press", {detail: {number: number}});
		this.dispatchEvent(myEvent);
		this.setNumber(newNumber);
	};

	proto.removeLastNumber = function () 
	{
		this.setNumber(this.getNumber().slice(0, -1));
	};

    	proto.handleKeyDown = function(e)
    	{
	    var number, keyCode = e.which;

	    if (keyCode >= 48 && keyCode <= 57) {
	      number = keyCode - 48;
	      this.addNumber(number + '');
	    }

	    if (keyCode === 8) {
	      this.removeLastNumber();
	      e.preventDefault();
	    }

	    if (keyCode === 13) {
		var myEvent = new CustomEvent("Telephone.Dialer.Number", {detail: {number: this.getNumber()}});
		this.dispatchEvent(myEvent);
	    }    	
    	}		

	proto.startTimer = function () 
	{
		this.timerStartTime = Date.now();
		this.timerStopped = false;
		this.updateTimer();
		return this;
	};

	proto.stopTimer = function () {
		this.timerStopped = true;
		return this;
	};

	proto.resetTimer = function () {
		this.timerStopped = true;
		this.setTimeInDom('0:00:00');
		return this;
	};

	proto.updateTimer = function () 
	{
		if (this.timerStopped) return;

		var diff = Date.now() - this.timerStartTime,
		s = Math.floor(diff / 1000) % 60,
		min = Math.floor((diff / 1000) / 60) % 60,
		hr = Math.floor(((diff / 1000) / 60) / 60) % 60,
		time = [hr, this.zeroPad(min), this.zeroPad(s)].join(':');

		if (this.time !== time) {
			this.time = time;
			this.setTimeInDom(time);
		}

		setTimeout(this.updateTimer.bind(this), 100);
	};

	proto.setTimeInDom = function (timeString) {
		if (!this.shadow) return;
		this.shadow.querySelector('.callTime').innerHTML = timeString;
	};

	proto.zeroPad = function (num) {
		return ((num + '').length === 1) ? '0' + num : num;
	};
	
	proto.setState = function (state, number) 
	{
		var noRender = false;
		var timer = false;
		var self = this;
		
		this.shadow.querySelector('.callerNumber').innerHTML = number ? number : this.getNumber();			
		
		this.shadow.querySelector('button.end').classList.add('hidden');
		this.shadow.querySelector('button.hold').classList.add('hidden');			
		this.shadow.querySelector('button.unhold').classList.add('hidden');
		
		this.callStatus.classList.remove("active");
		this.callStatus.classList.remove("inactive");		
		this.callStatus.classList.remove("held");
		this.callStatus.classList.remove("ending");		
		
		this.callStatus.classList.add(state);		

		if (state == "active")
		{
			noRender = false;
			timer = true;
			this.shadow.querySelector('button.end').classList.remove('hidden');
			this.shadow.querySelector('button.hold').classList.remove('hidden');						
		}
		
		if (state == "held")
		{
			noRender = false;
			timer = true;
			this.shadow.querySelector('button.unhold').classList.remove('hidden');						
		}
		
		if (state == "inactive")
		{
			noRender = true;
			timer = false;			
		}		
		
		if (noRender)
		{
			this.stopTimer();
			this.callStatus.classList.add('ending');
			
			setTimeout(function () 
			{
				self.callStatus.classList.remove('visible');

				setTimeout(function () 
				{
					self.callStatus.classList.remove('visible');
					self.dialpad.classList.remove('candybarVisible');
					//self.clearUser();
				}, 1000);
			}, 1000);
			
		} else {
			this.callStatus.classList.add('visible');
			this.dialpad.classList.add('candybarVisible');			
			if (timer) this.startTimer(); 				
		}		
		      
	}	
	
	document.registerElement( "inum-telephone", {
		prototype: proto
	});
})(window);
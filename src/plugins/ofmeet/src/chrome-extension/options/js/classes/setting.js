//
// Copyright (c) 2011 Frank Kohlhepp
// https://github.com/frankkohlhepp/fancy-settings
// License: LGPL v2.1
//
(function () {
    var settings,
        Bundle;
    
    settings = new Store("settings");
    Bundle = new Class({
        // Attributes:
        // - tab
        // - group
        // - name
        // - type
        //
        // Methods:
        //  - initialize
        //  - createDOM
        //  - setupDOM
        //  - addEvents
        //  - get
        //  - set
        "Implements": Events,
        
        "initialize": function (params) {
            this.params = params;
            this.params.searchString = "•" + this.params.tab + "•" + this.params.group + "•";
            
            this.createDOM();
            this.setupDOM();
            this.addEvents();
            
            if (this.params.id !== undefined) {
                this.element.set("id", this.params.id);
            }
            
            if (this.params.name !== undefined) {
                this.set(settings.get(this.params.name), true);
            }

            this.params.searchString = this.params.searchString.toLowerCase();
        },
        
        "addEvents": function () {
            this.element.addEvent("change", (function (event) {
                if (this.params.name !== undefined) {
                    settings.set(this.params.name, this.get());
                }
                
                this.fireEvent("action", this.get());
            }).bind(this));
        },
        
        "get": function () {
            return this.element.get("value");
        },
        
        "set": function (value, noChangeEvent) {
            this.element.set("value", value);
            
            if (noChangeEvent !== true) {
                this.element.fireEvent("change");
            }
            
            return this;
        }
    });
    
    Bundle.Description = new Class({
        // text
        "Extends": Bundle,
        "addEvents": undefined,
        "get": undefined,
        "set": undefined,
        
        "initialize": function (params) {
            this.params = params;
            this.params.searchString = "";
            
            this.createDOM();
            this.setupDOM();
        },
        
        "createDOM": function () {
            this.bundle = new Element("div", {
                "class": "setting bundle description"
            });
            
            this.container = new Element("div", {
                "class": "setting container description"
            });
            
            this.element = new Element("p", {
                "class": "setting element description"
            });
        },
        
        "setupDOM": function () {
            if (this.params.text !== undefined) {
                this.element.set("html", this.params.text);
            }
            
            this.element.inject(this.container);
            this.container.inject(this.bundle);
        }
    });
    
    Bundle.Button = new Class({
        // label, text
        // action -> click
        "Extends": Bundle,
        "get": undefined,
        "set": undefined,
        
        "initialize": function (params) {
            this.params = params;
            this.params.searchString = "•" + this.params.tab + "•" + this.params.group + "•";
            
            this.createDOM();
            this.setupDOM();
            this.addEvents();

            if (this.params.id !== undefined) {
                this.element.set("id", this.params.id);
            }
            
            this.params.searchString = this.params.searchString.toLowerCase();
        },
        
        "createDOM": function () {
            this.bundle = new Element("div", {
                "class": "setting bundle button"
            });
            
            this.container = new Element("div", {
                "class": "setting container button"
            });
            
            this.element = new Element("input", {
                "class": "setting element button",
                "type": "button"
            });
            
            this.label = new Element("label", {
                "class": "setting label button"
            });
        },
        
        "setupDOM": function () {
            if (this.params.label !== undefined) {
                this.label.set("html", this.params.label);
                this.label.inject(this.container);
                this.params.searchString += this.params.label + "•";
            }
            
            if (this.params.text !== undefined) {
                this.element.set("value", this.params.text);
                this.params.searchString += this.params.text + "•";
            }
            
            this.element.inject(this.container);
            this.container.inject(this.bundle);
        },
        
        "addEvents": function () {
            this.element.addEvent("click", (function () {
                this.fireEvent("action");
            }).bind(this));
        }
    });
    
    Bundle.Text = new Class({
        // label, text, masked
        // action -> change & keyup
        "Extends": Bundle,
        
        "createDOM": function () {
            this.bundle = new Element("div", {
                "class": "setting bundle text"
            });
            
            this.container = new Element("div", {
                "class": "setting container text"
            });
            
            this.element = new Element("input", {
                "class": "setting element text",
                "type": "text"
            });
            
            this.label = new Element("label", {
                "class": "setting label text"
            });
        },
        
        "setupDOM": function () {
            if (this.params.label !== undefined) {
                this.label.set("html", this.params.label);
                this.label.inject(this.container);
                this.params.searchString += this.params.label + "•";
            }
            
            if (this.params.text !== undefined) {
                this.element.set("placeholder", this.params.text);
                this.params.searchString += this.params.text + "•";
            }
            
            if (this.params.masked === true) {
                this.element.set("type", "password");
                this.params.searchString += "password" + "•";
            }
            
            this.element.inject(this.container);
            this.container.inject(this.bundle);
        },
        
        "addEvents": function () {
            var change = (function (event) {
                if (this.params.name !== undefined) {
                    settings.set(this.params.name, this.get());
                }
                
                this.fireEvent("action", this.get());
            }).bind(this);
            
            this.element.addEvent("change", change);
            this.element.addEvent("keyup", change);
        }
    });
    
    Bundle.Checkbox = new Class({
        // label
        // action -> change
        "Extends": Bundle,
        
        "createDOM": function () {
            this.bundle = new Element("div", {
                "class": "setting bundle checkbox"
            });
            
            this.container = new Element("div", {
                "class": "setting container checkbox"
            });
            
            this.element = new Element("input", {
                "id": String.uniqueID(),
                "class": "setting element checkbox",
                "type": "checkbox",
                "value": "true"
            });
            
            this.label = new Element("label", {
                "class": "setting label checkbox",
                "for": this.element.get("id")
            });
        },
        
        "setupDOM": function () {
            this.element.inject(this.container);
            this.container.inject(this.bundle);
            
            if (this.params.label !== undefined) {
                this.label.set("html", this.params.label);
                this.label.inject(this.container);
                this.params.searchString += this.params.label + "•";
            }
        },
        
        "get": function () {
            return this.element.get("checked");
        },
        
        "set": function (value, noChangeEvent) {
            this.element.set("checked", value);
            
            if (noChangeEvent !== true) {
                this.element.fireEvent("change");
            }
            
            return this;
        }
    });
    
    Bundle.Slider = new Class({
        // label, max, min, step, display, displayModifier
        // action -> change
        "Extends": Bundle,
        
        "initialize": function (params) {
            this.params = params;
            this.params.searchString = "•" + this.params.tab + "•" + this.params.group + "•";
            
            this.createDOM();
            this.setupDOM();
            this.addEvents();
            
            if (this.params.name !== undefined) {
                this.set((settings.get(this.params.name) || 0), true);
            } else {
                this.set(0, true);
            }
            
            this.params.searchString = this.params.searchString.toLowerCase();
        },
        
        "createDOM": function () {
            this.bundle = new Element("div", {
                "class": "setting bundle slider"
            });
            
            this.container = new Element("div", {
                "class": "setting container slider"
            });
            
            this.element = new Element("input", {
                "class": "setting element slider",
                "type": "range"
            });
            
            this.label = new Element("label", {
                "class": "setting label slider"
            });
            
            this.display = new Element("span", {
                "class": "setting display slider"
            });
        },
        
        "setupDOM": function () {
            if (this.params.label !== undefined) {
                this.label.set("html", this.params.label);
                this.label.inject(this.container);
                this.params.searchString += this.params.label + "•";
            }
            
            if (this.params.max !== undefined) {
                this.element.set("max", this.params.max);
            }
            
            if (this.params.min !== undefined) {
                this.element.set("min", this.params.min);
            }
            
            if (this.params.step !== undefined) {
                this.element.set("step", this.params.step);
            }
            
            this.element.inject(this.container);
            if (this.params.display !== false) {
                if (this.params.displayModifier !== undefined) {
                    this.display.set("text", this.params.displayModifier(0));
                } else {
                    this.display.set("text", 0);
                }
                this.display.inject(this.container);
            }
            this.container.inject(this.bundle);
        },
        
        "addEvents": function () {
            this.element.addEvent("change", (function (event) {
                if (this.params.name !== undefined) {
                    settings.set(this.params.name, this.get());
                }
                
                if (this.params.displayModifier !== undefined) {
                    this.display.set("text", this.params.displayModifier(this.get()));
                } else {
                    this.display.set("text", this.get());
                }
                this.fireEvent("action", this.get());
            }).bind(this));
        },
        
        "get": function () {
            return Number.from(this.element.get("value"));
        },
        
        "set": function (value, noChangeEvent) {
            this.element.set("value", value);
            
            if (noChangeEvent !== true) {
                this.element.fireEvent("change");
            } else {
                if (this.params.displayModifier !== undefined) {
                    this.display.set("text", this.params.displayModifier(Number.from(value)));
                } else {
                    this.display.set("text", Number.from(value));
                }
            }
            
            return this;
        }
    });
    
    Bundle.PopupButton = new Class({
        // label, options[{value, text}]
        // action -> change
        "Extends": Bundle,
        
        "createDOM": function () {
            this.bundle = new Element("div", {
                "class": "setting bundle popup-button"
            });
            
            this.container = new Element("div", {
                "class": "setting container popup-button"
            });
            
            this.element = new Element("select", {
                "class": "setting element popup-button"
            });
            
            this.label = new Element("label", {
                "class": "setting label popup-button"
            });
            
            if (this.params.options === undefined) { return; }

            // convert array syntax into object syntax for options
            function arrayToObject(option) {
                if (typeOf(option) == "array") {
                    option = {
                        "value": option[0],
                        "text": option[1] || option[0],
                    };
                }
                return option;
            }

            // convert arrays
            if (typeOf(this.params.options) == "array") {
                var values = [];
                this.params.options.each((function(values, option) {
                    values.push(arrayToObject(option));
                }).bind(this, values));
                this.params.options = { "values": values };
            }

            var groups;
            if (this.params.options.groups !== undefined) {
                groups = {};
                this.params.options.groups.each((function (groups, group) {
                    this.params.searchString += (group) + "•";
                    groups[group] = (new Element("optgroup", {
                        "label": group,
                    }).inject(this.element));
                }).bind(this, groups));
            }

            if (this.params.options.values !== undefined) {
                this.params.options.values.each((function(groups, option) {
                    option = arrayToObject(option);
                this.params.searchString += (option.text || option.value) + "•";

                    // find the parent of this option - either a group or the main element
                    var parent;
                    if (option.group && this.params.options.groups) {
                        if ((option.group - 1) in this.params.options.groups) {
                            option.group = this.params.options.groups[option.group-1];
                        }
                        if (option.group in groups) {
                            parent = groups[option.group];
                        }
                        else {
                            parent = this.element;
                        }
                    }
                    else {
                        parent = this.element;
                    }

                (new Element("option", {
                    "value": option.value,
                        "text": option.text || option.value,
                    })).inject(parent);
                }).bind(this, groups));
            }
        },
        
        "setupDOM": function () {
            if (this.params.label !== undefined) {
                this.label.set("html", this.params.label);
                this.label.inject(this.container);
                this.params.searchString += this.params.label + "•";
            }
            
            this.element.inject(this.container);
            this.container.inject(this.bundle);
        }
    });
    
    Bundle.ListBox = new Class({
        // label, options[{value, text}]
        // action -> change
        "Extends": Bundle.PopupButton,
        
        "createDOM": function () {
            this.bundle = new Element("div", {
                "class": "setting bundle list-box"
            });
            
            this.container = new Element("div", {
                "class": "setting container list-box"
            });
            
            this.element = new Element("select", {
                "class": "setting element list-box",
                "size": "2"
            });
            
            this.label = new Element("label", {
                "class": "setting label list-box"
            });
            
            if (this.params.options === undefined) { return; }
            this.params.options.each((function (option) {
                this.params.searchString += (option.text || option.value) + "•";
                
                (new Element("option", {
                    "value": option.value,
                    "text": option.text || option.value
                })).inject(this.element);
            }).bind(this));
        },
        
        "get": function () {
            return (this.element.get("value") || undefined);
        }
    });
    
    Bundle.Textarea = new Class({
        // label, text, value
        // action -> change & keyup
        "Extends": Bundle,
        
        "createDOM": function () {
            this.bundle = new Element("div", {
                "class": "setting bundle textarea"
            });
            
            this.container = new Element("div", {
                "class": "setting container textarea"
            });
            
            this.element = new Element("textarea", {
                "class": "setting element textarea"
            });
            
            this.label = new Element("label", {
                "class": "setting label textarea"
            });
        },
        
        "setupDOM": function () {
            if (this.params.label !== undefined) {
                this.label.set("html", this.params.label);
                this.label.inject(this.container);
                this.params.searchString += this.params.label + "•";
            }
            
            if (this.params.text !== undefined) {
                this.element.set("placeholder", this.params.text);
                this.params.searchString += this.params.text + "•";
            }

            if (this.params.value !== undefined) {
                this.element.appendText(this.params.text);
            }
            
            this.element.inject(this.container);
            this.container.inject(this.bundle);
        },
        
        "addEvents": function () {
            var change = (function (event) {
                if (this.params.name !== undefined) {
                    settings.set(this.params.name, this.get());
                }
                
                this.fireEvent("action", this.get());
            }).bind(this);
            
            this.element.addEvent("change", change);
            this.element.addEvent("keyup", change);
        }
    });

    Bundle.RadioButtons = new Class({
        // label, options[{value, text}]
        // action -> change
        "Extends": Bundle,
        
        "createDOM": function () {
            var settingID = String.uniqueID();
            
            this.bundle = new Element("div", {
                "class": "setting bundle radio-buttons"
            });
            
            this.label = new Element("label", {
                "class": "setting label radio-buttons"
            });
            
            this.containers = [];
            this.elements = [];
            this.labels = [];
            
            if (this.params.options === undefined) { return; }
            this.params.options.each((function (option) {
                var optionID,
                    container;
                
                this.params.searchString += (option.text || option.value) + "•";
                
                optionID = String.uniqueID();
                container = (new Element("div", {
                    "class": "setting container radio-buttons"
                })).inject(this.bundle);
                this.containers.push(container);
                
                this.elements.push((new Element("input", {
                    "id": optionID,
                    "name": settingID,
                    "class": "setting element radio-buttons",
                    "type": "radio",
                    "value": option.value
                })).inject(container));
                
                this.labels.push((new Element("label", {
                    "class": "setting element-label radio-buttons",
                    "for": optionID,
                    "text": option.text || option.value
                })).inject(container));
            }).bind(this));
        },
        
        "setupDOM": function () {
            if (this.params.label !== undefined) {
                this.label.set("html", this.params.label);
                this.label.inject(this.bundle, "top");
                this.params.searchString += this.params.label + "•";
            }
        },
        
        "addEvents": function () {
            this.bundle.addEvent("change", (function (event) {
                if (this.params.name !== undefined) {
                    settings.set(this.params.name, this.get());
                }
                
                this.fireEvent("action", this.get());
            }).bind(this));
        },
        
        "get": function () {
            var checkedEl = this.elements.filter((function (el) {
                return el.get("checked");
            }).bind(this));
            return (checkedEl[0] && checkedEl[0].get("value"));
        },
        
        "set": function (value, noChangeEvent) {
            var desiredEl = this.elements.filter((function (el) {
                return (el.get("value") === value);
            }).bind(this));
            desiredEl[0] && desiredEl[0].set("checked", true);
            
            if (noChangeEvent !== true) {
                this.bundle.fireEvent("change");
            }
            
            return this;
        }
    });
    
    this.Setting = new Class({
        "initialize": function (container) {
            this.container = container;
        },
        
        "create": function (params) {
            var types,
                bundle;
            
            // Available types
            types = {
                "description": "Description",
                "button": "Button",
                "text": "Text",
                "textarea": "Textarea",
                "checkbox": "Checkbox",
                "slider": "Slider",
                "popupButton": "PopupButton",
                "listBox": "ListBox",
                "radioButtons": "RadioButtons"
            };
            
            if (types.hasOwnProperty(params.type)) {
                bundle = new Bundle[types[params.type]](params);
                bundle.bundleContainer = this.container;
                bundle.bundle.inject(this.container);
                return bundle;
            } else {
                throw "invalidType";
            }
        }
    });
}());

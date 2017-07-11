### Forms

This plugin provides an easy way, given a [JSON Schema](http://json-schema.org/) model of generating a form with 2 way binding to some JSON data.

For an example of it in action, see the [test.html](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/forms/html/test.html) or run it via [http://localhost:8080/hawtio/#/forms/test](http://localhost:8080/hawtio/#/forms/test).

## Customizing the UI with tabs

The easiest way to customise the generated form is to specify which order you want the fields to appear; or to move different fields onto different tabs using the **tabs** object on a json schema type.
e.g.

        tabs: {
          'Tab One': ['key', 'value'],
          'Tab Two': ['*'],
          'Tab Three': ['booleanArg'],
					'Tab Four': ['foo\\..*']
        }

You can use "*" to refer to all the other properties not explicitly configured.

In addition you can use regular expressions to bind properties to a particular tab (e.g. so we match foo.* nested properties to Tab Four above). 

## Hiding fields

You can add a **hidden** flag on a property in a JSON schema to hide it from the auto-generated forms. Or you can set its type to be **hidden**.

e.g.

    properties: {
      foo: {
        type: "string",
        label: "My Foo Thingy"
     },
     bar: {
        type: "string",
        hidden: true
     }
   }

in the above, the _bar_ property will be hidden from the generated form

## Customizing the labels and tooltips

If you wish to specify a custom label for a property (as by default it will just humanize the id of the property), you can just specify the 'label' property inside the JSON Schema as follows:


    properties: {
      foo: {
        type: "string",
        label: "My Foo Thingy",
        tooltip: "My tool tip thingy"
     }
   }

The **label** and **tooltip** properties are not part of JSON Schema; but an extension like the **tabs** property above.

## Customising the element or attributes of the control

There are various extra directives you can add to &lt;input&gt; controls like [ng-hide](http://docs.angularjs.org/api/ng.directive:ngHide), [typeahead](http://angular-ui.github.io/bootstrap/#/typeahead) and so forth which you can do using a nested **input-attributes** object.

    properties: {
      foo: {
        type: "string",

        'input-attributes': {
          typeahead: "title for title in myQuery($viewValue) | filter:$viewValue"
        }
     }
   }

The above would use the typehead directive to present a pick list of possible values; passing the current text field value so we can more efficiently filter results back from any remote method invocation.

To define a custom [select widget](http://docs.angularjs.org/api/ng.directive:select) you can use the **input-element** value to specify a different element name to 'input' such as 'select':

    properties: {
      foo: {
        type: "string",

        'input-element': "select"
        'input-attributes': {
          'ng-options': "c.name for c in colors"
        }
     }
   }

The above would generate HTML like this...

```
     <select ng-options="c.name for c in colors" ng-model="..." title="..."></select>
```

### Autofocus on a field

You can pass in the [autofocus attribute](http://davidwalsh.name/autofocus) on one of the fields so the browse will auto-focus on one field on startup via

```
# lets customise an existing schema
Core.pathSet(schema.properties, ['myField', 'input-attributes', 'autofocus'], 'true');
```

or explicitly via

    properties: {
      foo: {
        type: "string",
        'input-attributes': {
          autofocus: "true"
        }
     }
   }


### Showing or hiding controls dynamically

Use the **control-group-attributes** or **control-attributes** object to add ng-hide / ng-show expressions to controls to dynamically show/hide them based on the entity's values. e.g. to conditionally hide the entire control-group div with label and control use this:

    properties: {
      foo: {
        type: "string",

        'control-group-attributes': {
          'ng-hide': "entity.bar == 'xyz'"
        }
     }
   }

### Customising label attributes

Use the **label-attributes** object to add custom attributes for labels such as for the css class

    properties: {
      foo: {
        type: "string",

        'label-attributes': {
          'class': 'control-label'
        }
     }
   }

### Ignoring prefix of deeply nested properties


If you use nested properties, the labels may include an unnecessary prefix if you use sub-tabs to show related nested properties.

To avoid this add a flag called **ignorePrefixInLabel** to the type which contains the **properties** you wish to ignore the prefixes of.

e.g.

    var myType = {
      "type" : "object",
      "properties" : {
        "foo" : {
          "properties" : {
            "value" : {
              "type" : "string"
            }
          }
        },
        "bar" : {
         ...
        }
      },
      ignorePrefixInLabel: true
    }

In the above the label for **foo.value** would just show _value_ rather than _foo value_ as the label.

## Using custom controls

To use a custom control use the **formTemplate** entry on a property to define the AngularJS partial to be used to render the form control. This lets you use any AngularJS directive or widget.

For example if you search for **formTemplate** in the [code generated Camel json schema file](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/lib/camelModel.js#L120) you will see how the **description** property uses a _textarea_

## Live example
<div ng-include="'app/forms/html/test.html'"></div>

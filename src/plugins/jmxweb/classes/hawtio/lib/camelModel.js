var _apacheCamelModel = {
  "definitions": {
    "endpoint": {
      "title": "Endpoint",
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "group": "Endpoints",
      "description": "Represents a camel endpoint which is used to consume messages or send messages to some kind of middleware or technology",
      "icon": "endpoint24.png",
      "properties": {
        "uri": {
          "type": "string"
        },
        "ref": {
          "type": "string"
        }
      }
    },
    "from": {
      "title": "From",
      "type": "object",
      "extends": {
        "type": "endpoint"
      },
      "description": "Consumes from an endpoint",
      "tooltip": "Consumes from an endpoint",
      "icon": "endpoint24.png"
    },
    "to": {
      "title": "To",
      "type": "object",
      "extends": {
        "type": "endpoint"
      },
      "description": "Sends messages to an endpoint",
      "tooltip": "Sends messages to an endpoint",
      "icon": "endpoint24.png"
    },
    "route": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Route",
      "group": "Miscellaneous",
      "description": "Route",
      "tooltip": "Route",
      "icon": "route24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "autoStartup": {
          "type": "string",
          "description": "Auto Startup",
          "tooltip": "Auto Startup",
          "optional": true,
          "title": "Auto Startup"
        },
        "delayer": {
          "type": "string",
          "description": "Delayer",
          "tooltip": "Delayer",
          "optional": true,
          "title": "Delayer"
        },
        "errorHandlerRef": {
          "type": "string",
          "description": "Error Handler Ref",
          "tooltip": "Error Handler Ref",
          "optional": true,
          "title": "Error Handler Ref"
        },
        "group": {
          "type": "string",
          "description": "Group",
          "tooltip": "Group",
          "optional": true,
          "title": "Group"
        },
        "handleFault": {
          "type": "string",
          "description": "Handle Fault",
          "tooltip": "Handle Fault",
          "optional": true,
          "title": "Handle Fault"
        },
        "messageHistory": {
          "type": "string",
          "description": "Message History",
          "tooltip": "Message History",
          "optional": true,
          "title": "Message History"
        },
        "routePolicyRef": {
          "type": "string",
          "description": "Route Policy Ref",
          "tooltip": "Route Policy Ref",
          "optional": true,
          "title": "Route Policy Ref"
        },
        "streamCache": {
          "type": "string",
          "description": "Stream Cache",
          "tooltip": "Stream Cache",
          "optional": true,
          "title": "Stream Cache"
        },
        "trace": {
          "type": "string",
          "description": "Trace",
          "tooltip": "Trace",
          "optional": true,
          "title": "Trace"
        }
      }
    },
    "org.apache.camel.model.OptionalIdentifiedDefinition": {
      "type": "object",
      "properties": {
        "id": {
          "type": "string"
        },
        "inheritErrorHandler": {
          "type": "java.lang.Boolean"
        }
      }
    },
    "org.apache.camel.model.language.ExpressionDefinition": {
      "description": "A Camel expression",
      "tooltip": "Pick an expression language and enter an expression",
      "type": "object",
      "properties": {
        "expression": {
          "type": "string",
          "description": "The expression",
          "tooltip": "Enter the expression in your chosen language syntax",
          "title": "Expression"
        },
        "language": {
          "type": "string",
          "enum": [
            "constant",
            "el",
            "header",
            "javaScript",
            "jxpath",
            "method",
            "mvel",
            "ognl",
            "groovy",
            "property",
            "python",
            "php",
            "ref",
            "ruby",
            "simple",
            "spel",
            "sql",
            "tokenize",
            "xpath",
            "xquery"
          ],
          "description": "The camel expression language ot use",
          "tooltip": "Pick the expression language you want to use",
          "title": "Language"
        }
      }
    },
    "aggregate": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Aggregate",
      "group": "Routing",
      "description": "Aggregate",
      "tooltip": "Aggregate",
      "icon": "aggregate24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "correlationExpression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Correlation Expression",
          "tooltip": "Correlation Expression",
          "title": "Correlation Expression"
        },
        "completionPredicate": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Completion Predicate",
          "tooltip": "Completion Predicate",
          "title": "Completion Predicate"
        },
        "completionTimeoutExpression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Completion Timeout Expression",
          "tooltip": "Completion Timeout Expression",
          "title": "Completion Timeout Expression"
        },
        "completionSizeExpression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Completion Size Expression",
          "tooltip": "Completion Size Expression",
          "title": "Completion Size Expression"
        },
        "executorServiceRef": {
          "type": "string",
          "description": "Executor Service Ref",
          "tooltip": "Executor Service Ref",
          "title": "Executor Service Ref"
        },
        "timeoutCheckerExecutorServiceRef": {
          "type": "string",
          "description": "Timeout Checker Executor Service Ref",
          "tooltip": "Timeout Checker Executor Service Ref",
          "title": "Timeout Checker Executor Service Ref"
        },
        "aggregationRepositoryRef": {
          "type": "string",
          "description": "Aggregation Repository Ref",
          "tooltip": "Aggregation Repository Ref",
          "title": "Aggregation Repository Ref"
        },
        "strategyRef": {
          "type": "string",
          "description": "Strategy Ref",
          "tooltip": "Strategy Ref",
          "title": "Strategy Ref"
        },
        "strategyMethodName": {
          "type": "string",
          "description": "Strategy Method Name",
          "tooltip": "Strategy Method Name",
          "title": "Strategy Method Name"
        },
        "optimisticLockRetryPolicyDefinition": {
          "type": "org.apache.camel.model.OptimisticLockRetryPolicyDefinition",
          "description": "Optimistic Lock Retry Policy Definition",
          "tooltip": "Optimistic Lock Retry Policy Definition",
          "title": "Optimistic Lock Retry Policy Definition"
        },
        "parallelProcessing": {
          "type": "bool",
          "description": "Parallel Processing",
          "tooltip": "Parallel Processing",
          "title": "Parallel Processing"
        },
        "optimisticLocking": {
          "type": "bool",
          "description": "Optimistic Locking",
          "tooltip": "Optimistic Locking",
          "title": "Optimistic Locking"
        },
        "strategyMethodAllowNull": {
          "type": "bool",
          "description": "Strategy Method Allow Null",
          "tooltip": "Strategy Method Allow Null",
          "title": "Strategy Method Allow Null"
        },
        "completionSize": {
          "type": "number",
          "description": "Completion Size",
          "tooltip": "Completion Size",
          "title": "Completion Size"
        },
        "completionInterval": {
          "type": "number",
          "description": "Completion Interval",
          "tooltip": "Completion Interval",
          "title": "Completion Interval"
        },
        "completionTimeout": {
          "type": "number",
          "description": "Completion Timeout",
          "tooltip": "Completion Timeout",
          "title": "Completion Timeout"
        },
        "completionFromBatchConsumer": {
          "type": "bool",
          "description": "Completion From Batch Consumer",
          "tooltip": "Completion From Batch Consumer",
          "title": "Completion From Batch Consumer"
        },
        "groupExchanges": {
          "type": "bool",
          "description": "Group Exchanges",
          "tooltip": "Group Exchanges",
          "title": "Group Exchanges"
        },
        "eagerCheckCompletion": {
          "type": "bool",
          "description": "Eager Check Completion",
          "tooltip": "Eager Check Completion",
          "title": "Eager Check Completion"
        },
        "ignoreInvalidCorrelationKeys": {
          "type": "bool",
          "description": "Ignore Invalid Correlation Keys",
          "tooltip": "Ignore Invalid Correlation Keys",
          "title": "Ignore Invalid Correlation Keys"
        },
        "closeCorrelationKeyOnCompletion": {
          "type": "number",
          "description": "Close Correlation Key On Completion",
          "tooltip": "Close Correlation Key On Completion",
          "title": "Close Correlation Key On Completion"
        },
        "discardOnCompletionTimeout": {
          "type": "bool",
          "description": "Discard On Completion Timeout",
          "tooltip": "Discard On Completion Timeout",
          "title": "Discard On Completion Timeout"
        },
        "forceCompletionOnStop": {
          "type": "bool",
          "description": "Force Completion On Stop",
          "tooltip": "Force Completion On Stop",
          "title": "Force Completion On Stop"
        }
      }
    },
    "AOP": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "AOP",
      "group": "Miscellaneous",
      "description": "AOP",
      "tooltip": "AOP",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "beforeUri": {
          "type": "string",
          "description": "Before Uri",
          "tooltip": "Before Uri",
          "title": "Before Uri"
        },
        "afterUri": {
          "type": "string",
          "description": "After Uri",
          "tooltip": "After Uri",
          "title": "After Uri"
        },
        "afterFinallyUri": {
          "type": "string",
          "description": "After Finally Uri",
          "tooltip": "After Finally Uri",
          "title": "After Finally Uri"
        }
      }
    },
    "bean": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Bean",
      "group": "Endpoints",
      "description": "Bean",
      "tooltip": "Bean",
      "icon": "bean24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "ref": {
          "control": "combo",
          "kind": "beanRef",
          "title": "Ref",
          "type": "string",
          "description": "Ref",
          "tooltip": "Ref"
        },
        "method": {
          "control": "combo",
          "kind": "beanMethod",
          "title": "Method",
          "type": "string",
          "description": "Method",
          "tooltip": "Method"
        },
        "beanType": {
          "type": "string",
          "description": "Bean Type",
          "tooltip": "Bean Type",
          "title": "Bean Type"
        },
        "cache": {
          "type": "bool",
          "description": "Cache",
          "tooltip": "Cache",
          "title": "Cache"
        }
      }
    },
    "catch": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Catch",
      "group": "Control Flow",
      "description": "Catch",
      "tooltip": "Catch",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "exceptions": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Exceptions",
          "tooltip": "Exceptions",
          "title": "Exceptions"
        },
        "handled": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Handled",
          "tooltip": "Handled",
          "title": "Handled"
        }
      }
    },
    "choice": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Choice",
      "group": "Routing",
      "description": "Choice",
      "tooltip": "Choice",
      "icon": "choice24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {}
    },
    "convertBody": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Convert Body",
      "group": "Transformation",
      "description": "Convert Body",
      "tooltip": "Convert Body",
      "icon": "convertBody24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "type": {
          "type": "string",
          "description": "Type",
          "tooltip": "Type",
          "title": "Type"
        },
        "charset": {
          "type": "string",
          "description": "Charset",
          "tooltip": "Charset",
          "title": "Charset"
        }
      }
    },
    "delay": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Delay",
      "group": "Control Flow",
      "description": "Delay",
      "tooltip": "Delay",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "executorServiceRef": {
          "type": "string",
          "description": "Executor Service Ref",
          "tooltip": "Executor Service Ref",
          "title": "Executor Service Ref"
        },
        "asyncDelayed": {
          "type": "bool",
          "description": "Async Delayed",
          "tooltip": "Async Delayed",
          "title": "Async Delayed"
        },
        "callerRunsWhenRejected": {
          "type": "bool",
          "description": "Caller Runs When Rejected",
          "tooltip": "Caller Runs When Rejected",
          "title": "Caller Runs When Rejected"
        }
      }
    },
    "dynamicRouter": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Dynamic Router",
      "group": "Routing",
      "description": "Dynamic Router",
      "tooltip": "Dynamic Router",
      "icon": "dynamicRouter24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "uriDelimiter": {
          "type": "string",
          "description": "Uri Delimiter",
          "tooltip": "Uri Delimiter",
          "title": "Uri Delimiter"
        },
        "ignoreInvalidEndpoints": {
          "type": "bool",
          "description": "Ignore Invalid Endpoints",
          "tooltip": "Ignore Invalid Endpoints",
          "title": "Ignore Invalid Endpoints"
        }
      }
    },
    "enrich": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Enrich",
      "group": "Transformation",
      "description": "Enrich",
      "tooltip": "Enrich",
      "icon": "enrich24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "resourceUri": {
          "type": "string",
          "description": "Resource Uri",
          "tooltip": "Resource Uri",
          "title": "Resource Uri"
        },
        "aggregationStrategyRef": {
          "type": "string",
          "description": "Aggregation Strategy Ref",
          "tooltip": "Aggregation Strategy Ref",
          "title": "Aggregation Strategy Ref"
        },
        "aggregationStrategyMethodName": {
          "type": "string",
          "description": "Aggregation Strategy Method Name",
          "tooltip": "Aggregation Strategy Method Name",
          "title": "Aggregation Strategy Method Name"
        },
        "aggregationStrategyMethodAllowNull": {
          "type": "bool",
          "description": "Aggregation Strategy Method Allow Null",
          "tooltip": "Aggregation Strategy Method Allow Null",
          "title": "Aggregation Strategy Method Allow Null"
        }
      }
    },
    "filter": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Filter",
      "group": "Routing",
      "description": "Filter",
      "tooltip": "Filter",
      "icon": "filter24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        }
      }
    },
    "finally": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Finally",
      "group": "Control Flow",
      "description": "Finally",
      "tooltip": "Finally",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {}
    },
    "idempotentConsumer": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Idempotent Consumer",
      "group": "Routing",
      "description": "Idempotent Consumer",
      "tooltip": "Idempotent Consumer",
      "icon": "idempotentConsumer24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "messageIdRepositoryRef": {
          "type": "string",
          "description": "Message Id Repository Ref",
          "tooltip": "Message Id Repository Ref",
          "title": "Message Id Repository Ref"
        },
        "eager": {
          "type": "bool",
          "description": "Eager",
          "tooltip": "Eager",
          "title": "Eager"
        },
        "skipDuplicate": {
          "type": "bool",
          "description": "Skip Duplicate",
          "tooltip": "Skip Duplicate",
          "title": "Skip Duplicate"
        },
        "removeOnFailure": {
          "type": "bool",
          "description": "Remove On Failure",
          "tooltip": "Remove On Failure",
          "title": "Remove On Failure"
        }
      }
    },
    "inOnly": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "In Only",
      "group": "Transformation",
      "description": "In Only",
      "tooltip": "In Only",
      "icon": "transform24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "uri": {
          "type": "string",
          "description": "Uri",
          "tooltip": "Uri",
          "title": "Uri"
        }
      }
    },
    "inOut": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "In Out",
      "group": "Transformation",
      "description": "In Out",
      "tooltip": "In Out",
      "icon": "transform24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "uri": {
          "type": "string",
          "description": "Uri",
          "tooltip": "Uri",
          "title": "Uri"
        }
      }
    },
    "intercept": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Intercept",
      "group": "Control Flow",
      "description": "Intercept",
      "tooltip": "Intercept",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {}
    },
    "interceptFrom": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Intercept From",
      "group": "Control Flow",
      "description": "Intercept From",
      "tooltip": "Intercept From",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "uri": {
          "type": "string",
          "description": "Uri",
          "tooltip": "Uri",
          "title": "Uri"
        }
      }
    },
    "interceptSendToEndpoint": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Intercept Send To Endpoint",
      "group": "Control Flow",
      "description": "Intercept Send To Endpoint",
      "tooltip": "Intercept Send To Endpoint",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "uri": {
          "type": "string",
          "description": "Uri",
          "tooltip": "Uri",
          "title": "Uri"
        },
        "skipSendToOriginalEndpoint": {
          "type": "bool",
          "description": "Skip Send To Original Endpoint",
          "tooltip": "Skip Send To Original Endpoint",
          "title": "Skip Send To Original Endpoint"
        }
      }
    },
    "loadBalance": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Load Balance",
      "group": "Routing",
      "description": "Load Balance",
      "tooltip": "Load Balance",
      "icon": "loadBalance24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "ref": {
          "type": "string",
          "description": "Ref",
          "tooltip": "Ref",
          "title": "Ref"
        },
        "loadBalancerType": {
          "type": [
            "org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition",
            "org.apache.camel.model.loadbalancer.RandomLoadBalancerDefinition",
            "org.apache.camel.model.loadbalancer.CustomLoadBalancerDefinition",
            "org.apache.camel.model.loadbalancer.RoundRobinLoadBalancerDefinition",
            "org.apache.camel.model.loadbalancer.StickyLoadBalancerDefinition",
            "org.apache.camel.model.loadbalancer.TopicLoadBalancerDefinition",
            "org.apache.camel.model.loadbalancer.WeightedLoadBalancerDefinition"
          ],
          "description": "Load Balancer Type",
          "tooltip": "Load Balancer Type",
          "title": "Load Balancer Type"
        }
      }
    },
    "log": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Log",
      "group": "Endpoints",
      "description": "Log",
      "tooltip": "Log",
      "icon": "log24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "message": {
          "type": "string",
          "description": "Message",
          "tooltip": "Message",
          "title": "Message"
        },
        "logName": {
          "type": "string",
          "description": "Log Name",
          "tooltip": "Log Name",
          "title": "Log Name"
        },
        "marker": {
          "type": "string",
          "description": "Marker",
          "tooltip": "Marker",
          "title": "Marker"
        },
        "loggingLevel": {
          "type": "org.apache.camel.LoggingLevel",
          "description": "Logging Level",
          "tooltip": "Logging Level",
          "title": "Logging Level"
        }
      }
    },
    "loop": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Loop",
      "group": "Control Flow",
      "description": "Loop",
      "tooltip": "Loop",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "copy": {
          "type": "bool",
          "description": "Copy",
          "tooltip": "Copy",
          "title": "Copy"
        }
      }
    },
    "marshal": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Marshal",
      "group": "Transformation",
      "description": "Marshal",
      "tooltip": "Marshal",
      "icon": "marshal24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "ref": {
          "type": "string",
          "description": "Ref",
          "tooltip": "Ref",
          "title": "Ref"
        },
        "dataFormatType": {
          "type": [
            "org.apache.camel.model.dataformat.AvroDataFormat",
            "org.apache.camel.model.dataformat.Base64DataFormat",
            "org.apache.camel.model.dataformat.BeanioDataFormat",
            "org.apache.camel.model.dataformat.BindyDataFormat",
            "org.apache.camel.model.dataformat.CastorDataFormat",
            "org.apache.camel.model.dataformat.C24IODataFormat",
            "org.apache.camel.model.dataformat.CryptoDataFormat",
            "org.apache.camel.model.dataformat.CsvDataFormat",
            "org.apache.camel.model.dataformat.CustomDataFormat",
            "org.apache.camel.model.dataformat.FlatpackDataFormat",
            "org.apache.camel.model.dataformat.GzipDataFormat",
            "org.apache.camel.model.dataformat.HL7DataFormat",
            "org.apache.camel.model.dataformat.JaxbDataFormat",
            "org.apache.camel.model.dataformat.JibxDataFormat",
            "org.apache.camel.model.dataformat.JsonDataFormat",
            "org.apache.camel.model.dataformat.ProtobufDataFormat",
            "org.apache.camel.model.dataformat.RssDataFormat",
            "org.apache.camel.model.dataformat.XMLSecurityDataFormat",
            "org.apache.camel.model.dataformat.SerializationDataFormat",
            "org.apache.camel.model.dataformat.SoapJaxbDataFormat",
            "org.apache.camel.model.dataformat.StringDataFormat",
            "org.apache.camel.model.dataformat.SyslogDataFormat",
            "org.apache.camel.model.dataformat.TidyMarkupDataFormat",
            "org.apache.camel.model.dataformat.XMLBeansDataFormat",
            "org.apache.camel.model.dataformat.XmlJsonDataFormat",
            "org.apache.camel.model.dataformat.XmlRpcDataFormat",
            "org.apache.camel.model.dataformat.XStreamDataFormat",
            "org.apache.camel.model.dataformat.PGPDataFormat",
            "org.apache.camel.model.dataformat.ZipDataFormat",
            "org.apache.camel.model.dataformat.ZipFileDataFormat"
          ],
          "description": "Data Format Type",
          "tooltip": "Data Format Type",
          "title": "Data Format Type"
        }
      }
    },
    "multicast": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Multicast",
      "group": "Routing",
      "description": "Multicast",
      "tooltip": "Multicast",
      "icon": "multicast24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "strategyRef": {
          "type": "string",
          "description": "Strategy Ref",
          "tooltip": "Strategy Ref",
          "title": "Strategy Ref"
        },
        "strategyMethodName": {
          "type": "string",
          "description": "Strategy Method Name",
          "tooltip": "Strategy Method Name",
          "title": "Strategy Method Name"
        },
        "executorServiceRef": {
          "type": "string",
          "description": "Executor Service Ref",
          "tooltip": "Executor Service Ref",
          "title": "Executor Service Ref"
        },
        "onPrepareRef": {
          "type": "string",
          "description": "On Prepare Ref",
          "tooltip": "On Prepare Ref",
          "title": "On Prepare Ref"
        },
        "parallelProcessing": {
          "type": "bool",
          "description": "Parallel Processing",
          "tooltip": "Parallel Processing",
          "title": "Parallel Processing"
        },
        "strategyMethodAllowNull": {
          "type": "bool",
          "description": "Strategy Method Allow Null",
          "tooltip": "Strategy Method Allow Null",
          "title": "Strategy Method Allow Null"
        },
        "streaming": {
          "type": "bool",
          "description": "Streaming",
          "tooltip": "Streaming",
          "title": "Streaming"
        },
        "stopOnException": {
          "type": "bool",
          "description": "Stop On Exception",
          "tooltip": "Stop On Exception",
          "title": "Stop On Exception"
        },
        "timeout": {
          "type": "number",
          "description": "Timeout",
          "tooltip": "Timeout",
          "title": "Timeout"
        },
        "shareUnitOfWork": {
          "type": "bool",
          "description": "Share Unit Of Work",
          "tooltip": "Share Unit Of Work",
          "title": "Share Unit Of Work"
        }
      }
    },
    "onCompletion": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "On Completion",
      "group": "Control Flow",
      "description": "On Completion",
      "tooltip": "On Completion",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "executorServiceRef": {
          "type": "string",
          "description": "Executor Service Ref",
          "tooltip": "Executor Service Ref",
          "title": "Executor Service Ref"
        },
        "onCompleteOnly": {
          "type": "bool",
          "description": "On Complete Only",
          "tooltip": "On Complete Only",
          "title": "On Complete Only"
        },
        "onFailureOnly": {
          "type": "bool",
          "description": "On Failure Only",
          "tooltip": "On Failure Only",
          "title": "On Failure Only"
        },
        "useOriginalMessagePolicy": {
          "type": "bool",
          "description": "Use Original Message Policy",
          "tooltip": "Use Original Message Policy",
          "title": "Use Original Message Policy"
        }
      }
    },
    "onException": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "On Exception",
      "group": "Control Flow",
      "description": "On Exception",
      "tooltip": "On Exception",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "exceptions": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Exceptions",
          "tooltip": "Exceptions",
          "title": "Exceptions"
        },
        "retryWhile": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Retry While",
          "tooltip": "Retry While",
          "title": "Retry While"
        },
        "redeliveryPolicyRef": {
          "type": "string",
          "description": "Redelivery Policy Ref",
          "tooltip": "Redelivery Policy Ref",
          "title": "Redelivery Policy Ref"
        },
        "handled": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Handled",
          "tooltip": "Handled",
          "title": "Handled"
        },
        "continued": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Continued",
          "tooltip": "Continued",
          "title": "Continued"
        },
        "onRedeliveryRef": {
          "type": "string",
          "description": "On Redelivery Ref",
          "tooltip": "On Redelivery Ref",
          "title": "On Redelivery Ref"
        },
        "redeliveryPolicy": {
          "type": "org.apache.camel.model.RedeliveryPolicyDefinition",
          "description": "Redelivery Policy",
          "tooltip": "Redelivery Policy",
          "title": "Redelivery Policy"
        },
        "useOriginalMessagePolicy": {
          "type": "bool",
          "description": "Use Original Message Policy",
          "tooltip": "Use Original Message Policy",
          "title": "Use Original Message Policy"
        }
      }
    },
    "otherwise": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Otherwise",
      "group": "Routing",
      "description": "Otherwise",
      "tooltip": "Otherwise",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {}
    },
    "pipeline": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Pipeline",
      "group": "Routing",
      "description": "Pipeline",
      "tooltip": "Pipeline",
      "icon": "pipeline24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {}
    },
    "policy": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Policy",
      "group": "Miscellaneous",
      "description": "Policy",
      "tooltip": "Policy",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "nextSiblingAddedAsChild": true,
      "properties": {
        "ref": {
          "type": "string",
          "description": "Ref",
          "tooltip": "Ref",
          "title": "Ref"
        }
      }
    },
    "pollEnrich": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Poll Enrich",
      "group": "Transformation",
      "description": "Poll Enrich",
      "tooltip": "Poll Enrich",
      "icon": "pollEnrich24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "resourceUri": {
          "type": "string",
          "description": "Resource Uri",
          "tooltip": "Resource Uri",
          "title": "Resource Uri"
        },
        "aggregationStrategyRef": {
          "type": "string",
          "description": "Aggregation Strategy Ref",
          "tooltip": "Aggregation Strategy Ref",
          "title": "Aggregation Strategy Ref"
        },
        "aggregationStrategyMethodName": {
          "type": "string",
          "description": "Aggregation Strategy Method Name",
          "tooltip": "Aggregation Strategy Method Name",
          "title": "Aggregation Strategy Method Name"
        },
        "timeout": {
          "type": "number",
          "description": "Timeout",
          "tooltip": "Timeout",
          "title": "Timeout"
        },
        "aggregationStrategyMethodAllowNull": {
          "type": "bool",
          "description": "Aggregation Strategy Method Allow Null",
          "tooltip": "Aggregation Strategy Method Allow Null",
          "title": "Aggregation Strategy Method Allow Null"
        }
      }
    },
    "process": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Process",
      "group": "Endpoints",
      "description": "Process",
      "tooltip": "Process",
      "icon": "process24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "ref": {
          "type": "string",
          "description": "Ref",
          "tooltip": "Ref",
          "title": "Ref"
        }
      }
    },
    "recipientList": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Recipient List",
      "group": "Routing",
      "description": "Recipient List",
      "tooltip": "Recipient List",
      "icon": "recipientList24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "delimiter": {
          "type": "string",
          "description": "Delimiter",
          "tooltip": "Delimiter",
          "title": "Delimiter"
        },
        "strategyRef": {
          "type": "string",
          "description": "Strategy Ref",
          "tooltip": "Strategy Ref",
          "title": "Strategy Ref"
        },
        "strategyMethodName": {
          "type": "string",
          "description": "Strategy Method Name",
          "tooltip": "Strategy Method Name",
          "title": "Strategy Method Name"
        },
        "executorServiceRef": {
          "type": "string",
          "description": "Executor Service Ref",
          "tooltip": "Executor Service Ref",
          "title": "Executor Service Ref"
        },
        "onPrepareRef": {
          "type": "string",
          "description": "On Prepare Ref",
          "tooltip": "On Prepare Ref",
          "title": "On Prepare Ref"
        },
        "parallelProcessing": {
          "type": "bool",
          "description": "Parallel Processing",
          "tooltip": "Parallel Processing",
          "title": "Parallel Processing"
        },
        "strategyMethodAllowNull": {
          "type": "bool",
          "description": "Strategy Method Allow Null",
          "tooltip": "Strategy Method Allow Null",
          "title": "Strategy Method Allow Null"
        },
        "stopOnException": {
          "type": "bool",
          "description": "Stop On Exception",
          "tooltip": "Stop On Exception",
          "title": "Stop On Exception"
        },
        "ignoreInvalidEndpoints": {
          "type": "bool",
          "description": "Ignore Invalid Endpoints",
          "tooltip": "Ignore Invalid Endpoints",
          "title": "Ignore Invalid Endpoints"
        },
        "streaming": {
          "type": "bool",
          "description": "Streaming",
          "tooltip": "Streaming",
          "title": "Streaming"
        },
        "timeout": {
          "type": "number",
          "description": "Timeout",
          "tooltip": "Timeout",
          "title": "Timeout"
        },
        "shareUnitOfWork": {
          "type": "bool",
          "description": "Share Unit Of Work",
          "tooltip": "Share Unit Of Work",
          "title": "Share Unit Of Work"
        }
      }
    },
    "removeHeader": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Remove Header",
      "group": "Transformation",
      "description": "Remove Header",
      "tooltip": "Remove Header",
      "icon": "transform24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "headerName": {
          "type": "string",
          "description": "Header Name",
          "tooltip": "Header Name",
          "title": "Header Name"
        }
      }
    },
    "removeHeaders": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Remove Headers",
      "group": "Transformation",
      "description": "Remove Headers",
      "tooltip": "Remove Headers",
      "icon": "transform24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "pattern": {
          "type": "string",
          "description": "Pattern",
          "tooltip": "Pattern",
          "title": "Pattern"
        },
        "excludePattern": {
          "type": "string",
          "description": "Exclude Pattern",
          "tooltip": "Exclude Pattern",
          "title": "Exclude Pattern"
        }
      }
    },
    "removeProperty": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Remove Property",
      "group": "Transformation",
      "description": "Remove Property",
      "tooltip": "Remove Property",
      "icon": "transform24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "propertyName": {
          "type": "string",
          "description": "Property Name",
          "tooltip": "Property Name",
          "title": "Property Name"
        }
      }
    },
    "resequence": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Resequence",
      "group": "Routing",
      "description": "Resequence",
      "tooltip": "Resequence",
      "icon": "resequence24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "resequencerConfig": {
          "type": [
            "org.apache.camel.model.config.BatchResequencerConfig",
            "org.apache.camel.model.config.StreamResequencerConfig"
          ],
          "description": "Resequencer Config",
          "tooltip": "Resequencer Config",
          "title": "Resequencer Config"
        }
      }
    },
    "rollback": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Rollback",
      "group": "Control Flow",
      "description": "Rollback",
      "tooltip": "Rollback",
      "icon": "generic24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "message": {
          "type": "string",
          "description": "Message",
          "tooltip": "Message",
          "title": "Message"
        },
        "markRollbackOnly": {
          "type": "bool",
          "description": "Mark Rollback Only",
          "tooltip": "Mark Rollback Only",
          "title": "Mark Rollback Only"
        },
        "markRollbackOnlyLast": {
          "type": "bool",
          "description": "Mark Rollback Only Last",
          "tooltip": "Mark Rollback Only Last",
          "title": "Mark Rollback Only Last"
        }
      }
    },
    "routingSlip": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Routing Slip",
      "group": "Routing",
      "description": "Routing Slip",
      "tooltip": "Routing Slip",
      "icon": "routingSlip24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "uriDelimiter": {
          "type": "string",
          "description": "Uri Delimiter",
          "tooltip": "Uri Delimiter",
          "title": "Uri Delimiter"
        },
        "ignoreInvalidEndpoints": {
          "type": "bool",
          "description": "Ignore Invalid Endpoints",
          "tooltip": "Ignore Invalid Endpoints",
          "title": "Ignore Invalid Endpoints"
        }
      }
    },
    "sampling": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Sampling",
      "group": "Miscellaneous",
      "description": "Sampling",
      "tooltip": "Sampling",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "samplePeriod": {
          "type": "number",
          "description": "Sample Period",
          "tooltip": "Sample Period",
          "title": "Sample Period"
        },
        "messageFrequency": {
          "type": "number",
          "description": "Message Frequency",
          "tooltip": "Message Frequency",
          "title": "Message Frequency"
        },
        "units": {
          "type": "java.util.concurrent.TimeUnit",
          "description": "Units",
          "tooltip": "Units",
          "title": "Units"
        }
      }
    },
    "setBody": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Set Body",
      "group": "Transformation",
      "description": "Set Body",
      "tooltip": "Set Body",
      "icon": "setBody24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        }
      }
    },
    "setExchangePattern": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Set Exchange Pattern",
      "group": "Transformation",
      "description": "Set Exchange Pattern",
      "tooltip": "Set Exchange Pattern",
      "icon": "transform24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "pattern": {
          "type": "org.apache.camel.ExchangePattern",
          "description": "Pattern",
          "tooltip": "Pattern",
          "title": "Pattern"
        }
      }
    },
    "setFaultBody": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Set Fault Body",
      "group": "Transformation",
      "description": "Set Fault Body",
      "tooltip": "Set Fault Body",
      "icon": "transform24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        }
      }
    },
    "setHeader": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Set Header",
      "group": "Transformation",
      "description": "Set Header",
      "tooltip": "Set Header",
      "icon": "transform24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "headerName": {
          "type": "string",
          "description": "Header Name",
          "tooltip": "Header Name",
          "title": "Header Name"
        }
      }
    },
    "setOutHeader": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Set Out Header",
      "group": "Transformation",
      "description": "Set Out Header",
      "tooltip": "Set Out Header",
      "icon": "transform24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "headerName": {
          "type": "string",
          "description": "Header Name",
          "tooltip": "Header Name",
          "title": "Header Name"
        }
      }
    },
    "setProperty": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Set Property",
      "group": "Transformation",
      "description": "Set Property",
      "tooltip": "Set Property",
      "icon": "transform24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "propertyName": {
          "type": "string",
          "description": "Property Name",
          "tooltip": "Property Name",
          "title": "Property Name"
        }
      }
    },
    "sort": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Sort",
      "group": "Routing",
      "description": "Sort",
      "tooltip": "Sort",
      "icon": "generic24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "comparatorRef": {
          "type": "string",
          "description": "Comparator Ref",
          "tooltip": "Comparator Ref",
          "title": "Comparator Ref"
        }
      }
    },
    "split": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Split",
      "group": "Routing",
      "description": "Split",
      "tooltip": "Split",
      "icon": "split24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "strategyRef": {
          "type": "string",
          "description": "Strategy Ref",
          "tooltip": "Strategy Ref",
          "title": "Strategy Ref"
        },
        "strategyMethodName": {
          "type": "string",
          "description": "Strategy Method Name",
          "tooltip": "Strategy Method Name",
          "title": "Strategy Method Name"
        },
        "executorServiceRef": {
          "type": "string",
          "description": "Executor Service Ref",
          "tooltip": "Executor Service Ref",
          "title": "Executor Service Ref"
        },
        "onPrepareRef": {
          "type": "string",
          "description": "On Prepare Ref",
          "tooltip": "On Prepare Ref",
          "title": "On Prepare Ref"
        },
        "parallelProcessing": {
          "type": "bool",
          "description": "Parallel Processing",
          "tooltip": "Parallel Processing",
          "title": "Parallel Processing"
        },
        "strategyMethodAllowNull": {
          "type": "bool",
          "description": "Strategy Method Allow Null",
          "tooltip": "Strategy Method Allow Null",
          "title": "Strategy Method Allow Null"
        },
        "streaming": {
          "type": "bool",
          "description": "Streaming",
          "tooltip": "Streaming",
          "title": "Streaming"
        },
        "stopOnException": {
          "type": "bool",
          "description": "Stop On Exception",
          "tooltip": "Stop On Exception",
          "title": "Stop On Exception"
        },
        "timeout": {
          "type": "number",
          "description": "Timeout",
          "tooltip": "Timeout",
          "title": "Timeout"
        },
        "shareUnitOfWork": {
          "type": "bool",
          "description": "Share Unit Of Work",
          "tooltip": "Share Unit Of Work",
          "title": "Share Unit Of Work"
        }
      }
    },
    "stop": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Stop",
      "group": "Miscellaneous",
      "description": "Stop",
      "tooltip": "Stop",
      "icon": "generic24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {}
    },
    "threads": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Threads",
      "group": "Miscellaneous",
      "description": "Threads",
      "tooltip": "Threads",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "executorServiceRef": {
          "type": "string",
          "description": "Executor Service Ref",
          "tooltip": "Executor Service Ref",
          "title": "Executor Service Ref"
        },
        "threadName": {
          "type": "string",
          "description": "Thread Name",
          "tooltip": "Thread Name",
          "title": "Thread Name"
        },
        "poolSize": {
          "type": "number",
          "description": "Pool Size",
          "tooltip": "Pool Size",
          "title": "Pool Size"
        },
        "maxPoolSize": {
          "type": "number",
          "description": "Max Pool Size",
          "tooltip": "Max Pool Size",
          "title": "Max Pool Size"
        },
        "keepAliveTime": {
          "type": "number",
          "description": "Keep Alive Time",
          "tooltip": "Keep Alive Time",
          "title": "Keep Alive Time"
        },
        "timeUnit": {
          "type": "java.util.concurrent.TimeUnit",
          "description": "Time Unit",
          "tooltip": "Time Unit",
          "title": "Time Unit"
        },
        "maxQueueSize": {
          "type": "number",
          "description": "Max Queue Size",
          "tooltip": "Max Queue Size",
          "title": "Max Queue Size"
        },
        "rejectedPolicy": {
          "type": "org.apache.camel.ThreadPoolRejectedPolicy",
          "description": "Rejected Policy",
          "tooltip": "Rejected Policy",
          "title": "Rejected Policy"
        },
        "callerRunsWhenRejected": {
          "type": "bool",
          "description": "Caller Runs When Rejected",
          "tooltip": "Caller Runs When Rejected",
          "title": "Caller Runs When Rejected"
        }
      }
    },
    "throttle": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Throttle",
      "group": "Control Flow",
      "description": "Throttle",
      "tooltip": "Throttle",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "executorServiceRef": {
          "type": "string",
          "description": "Executor Service Ref",
          "tooltip": "Executor Service Ref",
          "title": "Executor Service Ref"
        },
        "timePeriodMillis": {
          "type": "number",
          "description": "Time Period Millis",
          "tooltip": "Time Period Millis",
          "title": "Time Period Millis"
        },
        "asyncDelayed": {
          "type": "bool",
          "description": "Async Delayed",
          "tooltip": "Async Delayed",
          "title": "Async Delayed"
        },
        "callerRunsWhenRejected": {
          "type": "bool",
          "description": "Caller Runs When Rejected",
          "tooltip": "Caller Runs When Rejected",
          "title": "Caller Runs When Rejected"
        }
      }
    },
    "throwException": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Throw Exception",
      "group": "Control Flow",
      "description": "Throw Exception",
      "tooltip": "Throw Exception",
      "icon": "generic24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "ref": {
          "type": "string",
          "description": "Ref",
          "tooltip": "Ref",
          "title": "Ref"
        }
      }
    },
    "transacted": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Transacted",
      "group": "Control Flow",
      "description": "Transacted",
      "tooltip": "Transacted",
      "icon": "generic24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "ref": {
          "type": "string",
          "description": "Ref",
          "tooltip": "Ref",
          "title": "Ref"
        }
      }
    },
    "transform": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Transform",
      "group": "Transformation",
      "description": "Transform",
      "tooltip": "Transform",
      "icon": "transform24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        }
      }
    },
    "try": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Try",
      "group": "Control Flow",
      "description": "Try",
      "tooltip": "Try",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {}
    },
    "unmarshal": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Unmarshal",
      "group": "Transformation",
      "description": "Unmarshal",
      "tooltip": "Unmarshal",
      "icon": "unmarshal24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "ref": {
          "type": "string",
          "description": "Ref",
          "tooltip": "Ref",
          "title": "Ref"
        },
        "dataFormatType": {
          "type": [
            "org.apache.camel.model.dataformat.AvroDataFormat",
            "org.apache.camel.model.dataformat.Base64DataFormat",
            "org.apache.camel.model.dataformat.BeanioDataFormat",
            "org.apache.camel.model.dataformat.BindyDataFormat",
            "org.apache.camel.model.dataformat.CastorDataFormat",
            "org.apache.camel.model.dataformat.CryptoDataFormat",
            "org.apache.camel.model.dataformat.CsvDataFormat",
            "org.apache.camel.model.dataformat.CustomDataFormat",
            "org.apache.camel.model.dataformat.C24IODataFormat",
            "org.apache.camel.model.dataformat.FlatpackDataFormat",
            "org.apache.camel.model.dataformat.GzipDataFormat",
            "org.apache.camel.model.dataformat.HL7DataFormat",
            "org.apache.camel.model.dataformat.JaxbDataFormat",
            "org.apache.camel.model.dataformat.JibxDataFormat",
            "org.apache.camel.model.dataformat.JsonDataFormat",
            "org.apache.camel.model.dataformat.ProtobufDataFormat",
            "org.apache.camel.model.dataformat.RssDataFormat",
            "org.apache.camel.model.dataformat.XMLSecurityDataFormat",
            "org.apache.camel.model.dataformat.SerializationDataFormat",
            "org.apache.camel.model.dataformat.SoapJaxbDataFormat",
            "org.apache.camel.model.dataformat.StringDataFormat",
            "org.apache.camel.model.dataformat.SyslogDataFormat",
            "org.apache.camel.model.dataformat.TidyMarkupDataFormat",
            "org.apache.camel.model.dataformat.XMLBeansDataFormat",
            "org.apache.camel.model.dataformat.XmlJsonDataFormat",
            "org.apache.camel.model.dataformat.XmlRpcDataFormat",
            "org.apache.camel.model.dataformat.XStreamDataFormat",
            "org.apache.camel.model.dataformat.PGPDataFormat",
            "org.apache.camel.model.dataformat.ZipDataFormat",
            "org.apache.camel.model.dataformat.ZipFileDataFormat"
          ],
          "description": "Data Format Type",
          "tooltip": "Data Format Type",
          "title": "Data Format Type"
        }
      }
    },
    "validate": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Validate",
      "group": "Miscellaneous",
      "description": "Validate",
      "tooltip": "Validate",
      "icon": "generic24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        }
      }
    },
    "when": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "When",
      "group": "Routing",
      "description": "When",
      "tooltip": "When",
      "icon": "generic24.png",
      "acceptInput": true,
      "acceptOutput": true,
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        }
      }
    },
    "wireTap": {
      "type": "object",
      "extends": {
        "type": "org.apache.camel.model.OptionalIdentifiedDefinition"
      },
      "title": "Wire Tap",
      "group": "Routing",
      "description": "Wire Tap",
      "tooltip": "Wire Tap",
      "icon": "wireTap24.png",
      "nextSiblingAddedAsChild": true,
      "properties": {
        "uri": {
          "type": "string",
          "description": "Uri",
          "tooltip": "Uri",
          "title": "Uri"
        },
        "newExchangeProcessorRef": {
          "type": "string",
          "description": "New Exchange Processor Ref",
          "tooltip": "New Exchange Processor Ref",
          "title": "New Exchange Processor Ref"
        },
        "newExchangeExpression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "New Exchange Expression",
          "tooltip": "New Exchange Expression",
          "title": "New Exchange Expression"
        },
        "setHeader": {
          "type": "array",
          "items": {
            "type": "setHeader"
          },
          "description": "Headers",
          "tooltip": "Headers",
          "title": "Headers"
        },
        "executorServiceRef": {
          "type": "string",
          "description": "Executor Service Ref",
          "tooltip": "Executor Service Ref",
          "title": "Executor Service Ref"
        },
        "onPrepareRef": {
          "type": "string",
          "description": "On Prepare Ref",
          "tooltip": "On Prepare Ref",
          "title": "On Prepare Ref"
        },
        "copy": {
          "type": "bool",
          "description": "Copy",
          "tooltip": "Copy",
          "title": "Copy"
        }
      }
    },
    "org.apache.camel.model.SetHeader": {
      "type": "object",
      "description": "org.apache.camel.model.Set Header",
      "tooltip": "org.apache.camel.model.Set Header",
      "properties": {
        "expression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "headerName": {
          "type": "string",
          "description": "Header Name",
          "tooltip": "Header Name",
          "title": "Header Name"
        }
      }
    },
    "org.apache.camel.model.dataformat.SyslogDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Syslog Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Syslog Data Format",
      "properties": {}
    },
    "org.apache.camel.model.dataformat.JibxDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Jibx Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Jibx Data Format",
      "properties": {
        "bindingName": {
          "type": "string",
          "description": "Binding Name",
          "tooltip": "Binding Name",
          "optional": true,
          "title": "Binding Name"
        },
        "dataFormatName": {
          "type": "string",
          "description": "Data Format Name",
          "tooltip": "Data Format Name",
          "optional": true,
          "title": "Data Format Name"
        },
        "unmarshallTypeName": {
          "type": "string",
          "description": "Unmarshall Type Name",
          "tooltip": "Unmarshall Type Name",
          "optional": true,
          "title": "Unmarshall Type Name"
        }
      }
    },
    "org.apache.camel.model.dataformat.SerializationDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Serialization Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Serialization Data Format",
      "properties": {}
    },
    "org.apache.camel.model.language.Expression": {
      "type": "object",
      "description": "org.apache.camel.model.language.Expression",
      "tooltip": "org.apache.camel.model.language.Expression",
      "properties": {
        "expression": {
          "type": "string",
          "description": "Expression",
          "tooltip": "Expression",
          "title": "Expression"
        },
        "trim": {
          "type": "bool",
          "description": "Trim",
          "tooltip": "Trim",
          "title": "Trim"
        }
      }
    },
    "org.apache.camel.model.dataformat.BindyDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Bindy Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Bindy Data Format",
      "properties": {
        "classType": {
          "type": "string",
          "description": "Class Type",
          "tooltip": "Class Type",
          "title": "Class Type"
        },
        "locale": {
          "type": "string",
          "description": "Locale",
          "tooltip": "Locale",
          "title": "Locale"
        },
        "type": {
          "type": "org.apache.camel.model.dataformat.BindyType",
          "description": "Type",
          "tooltip": "Type",
          "title": "Type"
        },
        "packages": {
          "type": "array",
          "description": "Packages",
          "tooltip": "Packages",
          "title": "Packages"
        }
      }
    },
    "org.apache.camel.model.dataformat.CustomDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Custom Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Custom Data Format",
      "properties": {
        "ref": {
          "type": "string",
          "description": "Ref",
          "tooltip": "Ref",
          "title": "Ref"
        }
      }
    },
    "org.apache.camel.model.dataformat.CsvDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Csv Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Csv Data Format",
      "properties": {
        "delimiter": {
          "type": "string",
          "description": "Delimiter",
          "tooltip": "Delimiter",
          "title": "Delimiter"
        },
        "configRef": {
          "type": "string",
          "description": "Config Ref",
          "tooltip": "Config Ref",
          "title": "Config Ref"
        },
        "strategyRef": {
          "type": "string",
          "description": "Strategy Ref",
          "tooltip": "Strategy Ref",
          "title": "Strategy Ref"
        },
        "autogenColumns": {
          "type": "bool",
          "description": "Autogen Columns",
          "tooltip": "Autogen Columns",
          "title": "Autogen Columns"
        },
        "skipFirstLine": {
          "type": "bool",
          "description": "Skip First Line",
          "tooltip": "Skip First Line",
          "title": "Skip First Line"
        }
      }
    },
    "org.apache.camel.model.dataformat.TidyMarkupDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Tidy Markup Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Tidy Markup Data Format",
      "properties": {
        "dataObjectTypeName": {
          "type": "string",
          "description": "Data Object Type Name",
          "tooltip": "Data Object Type Name",
          "title": "Data Object Type Name"
        }
      }
    },
    "org.apache.camel.model.dataformat.GzipDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Gzip Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Gzip Data Format",
      "properties": {}
    },
    "org.apache.camel.model.dataformat.StringDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.String Data Format",
      "tooltip": "org.apache.camel.model.dataformat.String Data Format",
      "properties": {
        "charset": {
          "type": "string",
          "description": "Charset",
          "tooltip": "Charset",
          "title": "Charset"
        }
      }
    },
    "org.apache.camel.model.dataformat.JsonDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Json Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Json Data Format",
      "properties": {
        "unmarshalTypeName": {
          "type": "string",
          "description": "Unmarshal Type Name",
          "tooltip": "Unmarshal Type Name",
          "title": "Unmarshal Type Name"
        },
        "prettyPrint": {
          "type": "bool",
          "description": "Pretty Print",
          "tooltip": "Pretty Print",
          "title": "Pretty Print"
        },
        "library": {
          "type": "org.apache.camel.model.dataformat.JsonLibrary",
          "description": "Library",
          "tooltip": "Library",
          "title": "Library"
        }
      }
    },
    "org.apache.camel.model.dataformat.AvroDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Avro Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Avro Data Format",
      "properties": {
        "instanceClassName": {
          "type": "string",
          "description": "Instance Class Name",
          "tooltip": "Instance Class Name",
          "title": "Instance Class Name"
        }
      }
    },
    "org.apache.camel.model.loadbalancer.StickyLoadBalancer": {
      "type": "object",
      "description": "org.apache.camel.model.loadbalancer.Sticky Load Balancer",
      "tooltip": "org.apache.camel.model.loadbalancer.Sticky Load Balancer",
      "properties": {
        "correlationExpression": {
          "kind": "expression",
          "type": "org.apache.camel.model.language.ExpressionDefinition",
          "description": "Correlation Expression",
          "tooltip": "Correlation Expression",
          "title": "Correlation Expression"
        }
      }
    },
    "org.apache.camel.model.dataformat.ProtobufDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Protobuf Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Protobuf Data Format",
      "properties": {
        "instanceClass": {
          "type": "string",
          "description": "Instance Class",
          "tooltip": "Instance Class",
          "title": "Instance Class"
        }
      }
    },
    "org.apache.camel.model.loadbalancer.RoundRobinLoadBalancer": {
      "type": "object",
      "description": "org.apache.camel.model.loadbalancer.Round Robin Load Balancer",
      "tooltip": "org.apache.camel.model.loadbalancer.Round Robin Load Balancer",
      "properties": {}
    },
    "org.apache.camel.model.dataformat.XMLSecurityDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.XMLSecurity Data Format",
      "tooltip": "org.apache.camel.model.dataformat.XMLSecurity Data Format",
      "properties": {
        "xmlCipherAlgorithm": {
          "type": "string",
          "description": "Xml Cipher Algorithm",
          "tooltip": "Xml Cipher Algorithm",
          "title": "Xml Cipher Algorithm"
        },
        "passPhrase": {
          "type": "string",
          "description": "Pass Phrase",
          "tooltip": "Pass Phrase",
          "title": "Pass Phrase"
        },
        "secureTag": {
          "type": "string",
          "description": "Secure Tag",
          "tooltip": "Secure Tag",
          "title": "Secure Tag"
        },
        "keyCipherAlgorithm": {
          "type": "string",
          "description": "Key Cipher Algorithm",
          "tooltip": "Key Cipher Algorithm",
          "title": "Key Cipher Algorithm"
        },
        "recipientKeyAlias": {
          "type": "string",
          "description": "Recipient Key Alias",
          "tooltip": "Recipient Key Alias",
          "title": "Recipient Key Alias"
        },
        "keyOrTrustStoreParametersId": {
          "type": "string",
          "description": "Key Or Trust Store Parameters Id",
          "tooltip": "Key Or Trust Store Parameters Id",
          "title": "Key Or Trust Store Parameters Id"
        },
        "keyPassword": {
          "type": "string",
          "description": "Key Password",
          "tooltip": "Key Password",
          "title": "Key Password"
        },
        "digestAlgorithm": {
          "type": "string",
          "description": "Digest Algorithm",
          "tooltip": "Digest Algorithm",
          "title": "Digest Algorithm"
        },
        "mgfAlgorithm": {
          "type": "string",
          "description": "Mgf Algorithm",
          "tooltip": "Mgf Algorithm",
          "title": "Mgf Algorithm"
        },
        "secureTagContents": {
          "type": "bool",
          "description": "Secure Tag Contents",
          "tooltip": "Secure Tag Contents",
          "title": "Secure Tag Contents"
        }
      }
    },
    "org.apache.camel.model.dataformat.SoapJaxbDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Soap Jaxb Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Soap Jaxb Data Format",
      "properties": {
        "contextPath": {
          "type": "string",
          "description": "Context Path",
          "tooltip": "Context Path",
          "title": "Context Path"
        },
        "encoding": {
          "type": "string",
          "description": "Encoding",
          "tooltip": "Encoding",
          "title": "Encoding"
        },
        "elementNameStrategyRef": {
          "type": "string",
          "description": "Element Name Strategy Ref",
          "tooltip": "Element Name Strategy Ref",
          "title": "Element Name Strategy Ref"
        },
        "version": {
          "type": "string",
          "description": "Version",
          "tooltip": "Version",
          "title": "Version"
        },
        "namespacePrefixRef": {
          "type": "string",
          "description": "Namespace Prefix Ref",
          "tooltip": "Namespace Prefix Ref",
          "title": "Namespace Prefix Ref"
        }
      }
    },
    "org.apache.camel.model.dataformat.XmlRpcDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Xml Rpc Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Xml Rpc Data Format",
      "properties": {
        "request": {
          "type": "bool",
          "description": "Request",
          "tooltip": "Request",
          "title": "Request"
        }
      }
    },
    "org.apache.camel.model.dataformat.BeanioDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Beanio Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Beanio Data Format",
      "properties": {
        "mapping": {
          "type": "string",
          "description": "Mapping",
          "tooltip": "Mapping",
          "title": "Mapping"
        },
        "streamName": {
          "type": "string",
          "description": "Stream Name",
          "tooltip": "Stream Name",
          "title": "Stream Name"
        },
        "encoding": {
          "type": "string",
          "description": "Encoding",
          "tooltip": "Encoding",
          "title": "Encoding"
        },
        "ignoreUnidentifiedRecords": {
          "type": "bool",
          "description": "Ignore Unidentified Records",
          "tooltip": "Ignore Unidentified Records",
          "title": "Ignore Unidentified Records"
        },
        "ignoreUnexpectedRecords": {
          "type": "bool",
          "description": "Ignore Unexpected Records",
          "tooltip": "Ignore Unexpected Records",
          "title": "Ignore Unexpected Records"
        },
        "ignoreInvalidRecords": {
          "type": "bool",
          "description": "Ignore Invalid Records",
          "tooltip": "Ignore Invalid Records",
          "title": "Ignore Invalid Records"
        }
      }
    },
    "org.apache.camel.model.loadbalancer.CustomLoadBalancer": {
      "type": "object",
      "description": "org.apache.camel.model.loadbalancer.Custom Load Balancer",
      "tooltip": "org.apache.camel.model.loadbalancer.Custom Load Balancer",
      "properties": {
        "ref": {
          "type": "string",
          "description": "Ref",
          "tooltip": "Ref",
          "title": "Ref"
        }
      }
    },
    "org.apache.camel.model.dataformat.CastorDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Castor Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Castor Data Format",
      "properties": {
        "mappingFile": {
          "type": "string",
          "description": "Mapping File",
          "tooltip": "Mapping File",
          "title": "Mapping File"
        },
        "encoding": {
          "type": "string",
          "description": "Encoding",
          "tooltip": "Encoding",
          "title": "Encoding"
        },
        "validation": {
          "type": "bool",
          "description": "Validation",
          "tooltip": "Validation",
          "title": "Validation"
        },
        "packages": {
          "type": "array",
          "description": "Packages",
          "tooltip": "Packages",
          "title": "Packages"
        },
        "classes": {
          "type": "array",
          "description": "Classes",
          "tooltip": "Classes",
          "title": "Classes"
        }
      }
    },
    "org.apache.camel.model.Description": {
      "type": "object",
      "description": "org.apache.camel.model.Description",
      "tooltip": "org.apache.camel.model.Description",
      "properties": {
        "lang": {
          "type": "string",
          "description": "Lang",
          "tooltip": "Lang",
          "title": "Lang"
        },
        "text": {
          "type": "string",
          "description": "Text",
          "tooltip": "Text",
          "title": "Text"
        },
        "layoutX": {
          "type": "number",
          "description": "Layout X",
          "tooltip": "Layout X",
          "title": "Layout X"
        },
        "layoutY": {
          "type": "number",
          "description": "Layout Y",
          "tooltip": "Layout Y",
          "title": "Layout Y"
        },
        "layoutWidth": {
          "type": "number",
          "description": "Layout Width",
          "tooltip": "Layout Width",
          "title": "Layout Width"
        },
        "layoutHeight": {
          "type": "number",
          "description": "Layout Height",
          "tooltip": "Layout Height",
          "title": "Layout Height"
        }
      }
    },
    "org.apache.camel.model.dataformat.XMLBeansDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.XMLBeans Data Format",
      "tooltip": "org.apache.camel.model.dataformat.XMLBeans Data Format",
      "properties": {
        "prettyPrint": {
          "type": "bool",
          "description": "Pretty Print",
          "tooltip": "Pretty Print",
          "title": "Pretty Print"
        }
      }
    },
    "org.apache.camel.model.dataformat.RssDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Rss Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Rss Data Format",
      "properties": {}
    },
    "java.lang.String": {
      "type": "object",
      "description": "java.lang.String",
      "tooltip": "java.lang.String",
      "properties": {}
    },
    "org.apache.camel.model.dataformat.ZipFileDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Zip File Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Zip File Data Format",
      "properties": {
        "usingIterator": {
          "type": "bool",
          "description": "Using Iterator",
          "tooltip": "Using Iterator",
          "title": "Using Iterator"
        }
      }
    },
    "org.apache.camel.model.dataformat.XStreamDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.XStream Data Format",
      "tooltip": "org.apache.camel.model.dataformat.XStream Data Format",
      "properties": {
        "converters": {
          "type": "array",
          "description": "Converters",
          "tooltip": "Converters",
          "optional": true,
          "title": "Converters"
        },
        "dataFormatName": {
          "type": "string",
          "description": "Data Format Name",
          "tooltip": "Data Format Name",
          "optional": true,
          "title": "Data Format Name"
        },
        "driver": {
          "type": "string",
          "description": "Driver",
          "tooltip": "Driver",
          "optional": true,
          "title": "Driver"
        },
        "driverRef": {
          "type": "string",
          "description": "Driver Ref",
          "tooltip": "Driver Ref",
          "optional": true,
          "title": "Driver Ref"
        },
        "encoding": {
          "type": "string",
          "description": "Encoding",
          "tooltip": "Encoding",
          "optional": true,
          "title": "Encoding"
        }
      }
    },
    "org.apache.camel.model.dataformat.CryptoDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Crypto Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Crypto Data Format",
      "properties": {
        "algorithm": {
          "type": "string",
          "description": "Algorithm",
          "tooltip": "Algorithm",
          "title": "Algorithm"
        },
        "cryptoProvider": {
          "type": "string",
          "description": "Crypto Provider",
          "tooltip": "Crypto Provider",
          "title": "Crypto Provider"
        },
        "keyRef": {
          "type": "string",
          "description": "Key Ref",
          "tooltip": "Key Ref",
          "title": "Key Ref"
        },
        "initVectorRef": {
          "type": "string",
          "description": "Init Vector Ref",
          "tooltip": "Init Vector Ref",
          "title": "Init Vector Ref"
        },
        "algorithmParameterRef": {
          "type": "string",
          "description": "Algorithm Parameter Ref",
          "tooltip": "Algorithm Parameter Ref",
          "title": "Algorithm Parameter Ref"
        },
        "macAlgorithm": {
          "type": "string",
          "description": "Mac Algorithm",
          "tooltip": "Mac Algorithm",
          "title": "Mac Algorithm"
        },
        "buffersize": {
          "type": "number",
          "description": "Buffersize",
          "tooltip": "Buffersize",
          "title": "Buffersize"
        },
        "shouldAppendHMAC": {
          "type": "bool",
          "description": "Should Append HMAC",
          "tooltip": "Should Append HMAC",
          "title": "Should Append HMAC"
        },
        "inline": {
          "type": "bool",
          "description": "Inline",
          "tooltip": "Inline",
          "title": "Inline"
        }
      }
    },
    "org.apache.camel.model.config.BatchResequencerConfig": {
      "type": "object",
      "description": "org.apache.camel.model.config.Batch Resequencer Config",
      "tooltip": "org.apache.camel.model.config.Batch Resequencer Config",
      "properties": {
        "batchSize": {
          "type": "number",
          "description": "Batch Size",
          "tooltip": "Batch Size",
          "title": "Batch Size"
        },
        "batchTimeout": {
          "type": "number",
          "description": "Batch Timeout",
          "tooltip": "Batch Timeout",
          "title": "Batch Timeout"
        },
        "allowDuplicates": {
          "type": "bool",
          "description": "Allow Duplicates",
          "tooltip": "Allow Duplicates",
          "title": "Allow Duplicates"
        },
        "reverse": {
          "type": "bool",
          "description": "Reverse",
          "tooltip": "Reverse",
          "title": "Reverse"
        },
        "ignoreInvalidExchanges": {
          "type": "bool",
          "description": "Ignore Invalid Exchanges",
          "tooltip": "Ignore Invalid Exchanges",
          "title": "Ignore Invalid Exchanges"
        }
      }
    },
    "org.apache.camel.model.loadbalancer.RandomLoadBalancer": {
      "type": "object",
      "description": "org.apache.camel.model.loadbalancer.Random Load Balancer",
      "tooltip": "org.apache.camel.model.loadbalancer.Random Load Balancer",
      "properties": {}
    },
    "org.apache.camel.model.dataformat.XmlJsonDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Xml Json Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Xml Json Data Format",
      "properties": {
        "encoding": {
          "type": "string",
          "description": "Encoding",
          "tooltip": "Encoding",
          "title": "Encoding"
        },
        "elementName": {
          "type": "string",
          "description": "Element Name",
          "tooltip": "Element Name",
          "title": "Element Name"
        },
        "arrayName": {
          "type": "string",
          "description": "Array Name",
          "tooltip": "Array Name",
          "title": "Array Name"
        },
        "rootName": {
          "type": "string",
          "description": "Root Name",
          "tooltip": "Root Name",
          "title": "Root Name"
        },
        "expandableProperties": {
          "type": "array",
          "description": "Expandable Properties",
          "tooltip": "Expandable Properties",
          "title": "Expandable Properties"
        },
        "typeHints": {
          "type": "string",
          "description": "Type Hints",
          "tooltip": "Type Hints",
          "title": "Type Hints"
        },
        "forceTopLevelObject": {
          "type": "bool",
          "description": "Force Top Level Object",
          "tooltip": "Force Top Level Object",
          "title": "Force Top Level Object"
        },
        "namespaceLenient": {
          "type": "bool",
          "description": "Namespace Lenient",
          "tooltip": "Namespace Lenient",
          "title": "Namespace Lenient"
        },
        "skipWhitespace": {
          "type": "bool",
          "description": "Skip Whitespace",
          "tooltip": "Skip Whitespace",
          "title": "Skip Whitespace"
        },
        "trimSpaces": {
          "type": "bool",
          "description": "Trim Spaces",
          "tooltip": "Trim Spaces",
          "title": "Trim Spaces"
        },
        "skipNamespaces": {
          "type": "bool",
          "description": "Skip Namespaces",
          "tooltip": "Skip Namespaces",
          "title": "Skip Namespaces"
        },
        "removeNamespacePrefixes": {
          "type": "bool",
          "description": "Remove Namespace Prefixes",
          "tooltip": "Remove Namespace Prefixes",
          "title": "Remove Namespace Prefixes"
        }
      }
    },
    "org.apache.camel.model.dataformat.HL7DataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.HL7Data Format",
      "tooltip": "org.apache.camel.model.dataformat.HL7Data Format",
      "properties": {
        "validate": {
          "type": "bool",
          "description": "Validate",
          "tooltip": "Validate",
          "title": "Validate"
        }
      }
    },
    "org.apache.camel.model.loadbalancer.WeightedLoadBalancer": {
      "type": "object",
      "description": "org.apache.camel.model.loadbalancer.Weighted Load Balancer",
      "tooltip": "org.apache.camel.model.loadbalancer.Weighted Load Balancer",
      "properties": {
        "distributionRatio": {
          "type": "string",
          "description": "Distribution Ratio",
          "tooltip": "Distribution Ratio",
          "title": "Distribution Ratio"
        },
        "distributionRatioDelimiter": {
          "type": "string",
          "description": "Distribution Ratio Delimiter",
          "tooltip": "Distribution Ratio Delimiter",
          "title": "Distribution Ratio Delimiter"
        },
        "roundRobin": {
          "type": "bool",
          "description": "Round Robin",
          "tooltip": "Round Robin",
          "title": "Round Robin"
        }
      }
    },
    "org.apache.camel.model.dataformat.C24IODataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.C24IOData Format",
      "tooltip": "org.apache.camel.model.dataformat.C24IOData Format",
      "properties": {
        "elementTypeName": {
          "type": "string",
          "description": "Element Type Name",
          "tooltip": "Element Type Name",
          "title": "Element Type Name"
        },
        "contentType": {
          "type": "org.apache.camel.model.dataformat.C24IOContentType",
          "description": "Content Type",
          "tooltip": "Content Type",
          "title": "Content Type"
        }
      }
    },
    "org.apache.camel.model.config.StreamResequencerConfig": {
      "type": "object",
      "description": "org.apache.camel.model.config.Stream Resequencer Config",
      "tooltip": "org.apache.camel.model.config.Stream Resequencer Config",
      "properties": {
        "comparatorRef": {
          "type": "string",
          "description": "Comparator Ref",
          "tooltip": "Comparator Ref",
          "title": "Comparator Ref"
        },
        "capacity": {
          "type": "number",
          "description": "Capacity",
          "tooltip": "Capacity",
          "title": "Capacity"
        },
        "timeout": {
          "type": "number",
          "description": "Timeout",
          "tooltip": "Timeout",
          "title": "Timeout"
        },
        "ignoreInvalidExchanges": {
          "type": "bool",
          "description": "Ignore Invalid Exchanges",
          "tooltip": "Ignore Invalid Exchanges",
          "title": "Ignore Invalid Exchanges"
        },
        "rejectOld": {
          "type": "bool",
          "description": "Reject Old",
          "tooltip": "Reject Old",
          "title": "Reject Old"
        }
      }
    },
    "org.apache.camel.model.loadbalancer.FailoverLoadBalancer": {
      "type": "object",
      "description": "org.apache.camel.model.loadbalancer.Failover Load Balancer",
      "tooltip": "org.apache.camel.model.loadbalancer.Failover Load Balancer",
      "properties": {
        "exceptions": {
          "type": "array",
          "description": "Exceptions",
          "tooltip": "Exceptions",
          "title": "Exceptions"
        },
        "roundRobin": {
          "type": "bool",
          "description": "Round Robin",
          "tooltip": "Round Robin",
          "title": "Round Robin"
        },
        "maximumFailoverAttempts": {
          "type": "number",
          "description": "Maximum Failover Attempts",
          "tooltip": "Maximum Failover Attempts",
          "title": "Maximum Failover Attempts"
        }
      }
    },
    "org.apache.camel.model.dataformat.Base64DataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Base64Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Base64Data Format",
      "properties": {
        "lineSeparator": {
          "type": "string",
          "description": "Line Separator",
          "tooltip": "Line Separator",
          "title": "Line Separator"
        },
        "lineLength": {
          "type": "number",
          "description": "Line Length",
          "tooltip": "Line Length",
          "title": "Line Length"
        },
        "urlSafe": {
          "type": "bool",
          "description": "Url Safe",
          "tooltip": "Url Safe",
          "title": "Url Safe"
        }
      }
    },
    "org.apache.camel.model.dataformat.PGPDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.PGPData Format",
      "tooltip": "org.apache.camel.model.dataformat.PGPData Format",
      "properties": {
        "keyUserid": {
          "type": "string",
          "description": "Key Userid",
          "tooltip": "Key Userid",
          "title": "Key Userid"
        },
        "password": {
          "type": "string",
          "description": "Password",
          "tooltip": "Password",
          "title": "Password"
        },
        "keyFileName": {
          "type": "string",
          "description": "Key File Name",
          "tooltip": "Key File Name",
          "title": "Key File Name"
        },
        "provider": {
          "type": "string",
          "description": "Provider",
          "tooltip": "Provider",
          "title": "Provider"
        },
        "armored": {
          "type": "bool",
          "description": "Armored",
          "tooltip": "Armored",
          "title": "Armored"
        },
        "integrity": {
          "type": "bool",
          "description": "Integrity",
          "tooltip": "Integrity",
          "title": "Integrity"
        }
      }
    },
    "org.apache.camel.model.dataformat.JaxbDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Jaxb Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Jaxb Data Format",
      "properties": {
        "contextPath": {
          "type": "string",
          "description": "Context Path",
          "tooltip": "Context Path",
          "title": "Context Path"
        },
        "schema": {
          "type": "string",
          "description": "Schema",
          "tooltip": "Schema",
          "title": "Schema"
        },
        "encoding": {
          "type": "string",
          "description": "Encoding",
          "tooltip": "Encoding",
          "title": "Encoding"
        },
        "partClass": {
          "type": "string",
          "description": "Part Class",
          "tooltip": "Part Class",
          "title": "Part Class"
        },
        "partNamespace": {
          "type": "string",
          "description": "Part Namespace",
          "tooltip": "Part Namespace",
          "title": "Part Namespace"
        },
        "namespacePrefixRef": {
          "type": "string",
          "description": "Namespace Prefix Ref",
          "tooltip": "Namespace Prefix Ref",
          "title": "Namespace Prefix Ref"
        },
        "xmlStreamWriterWrapper": {
          "type": "string",
          "description": "Xml Stream Writer Wrapper",
          "tooltip": "Xml Stream Writer Wrapper",
          "title": "Xml Stream Writer Wrapper"
        },
        "prettyPrint": {
          "type": "bool",
          "description": "Pretty Print",
          "tooltip": "Pretty Print",
          "title": "Pretty Print"
        },
        "ignoreJAXBElement": {
          "type": "bool",
          "description": "Ignore JAXBElement",
          "tooltip": "Ignore JAXBElement",
          "title": "Ignore JAXBElement"
        },
        "filterNonXmlChars": {
          "type": "bool",
          "description": "Filter Non Xml Chars",
          "tooltip": "Filter Non Xml Chars",
          "title": "Filter Non Xml Chars"
        },
        "fragment": {
          "type": "bool",
          "description": "Fragment",
          "tooltip": "Fragment",
          "title": "Fragment"
        }
      }
    },
    "org.apache.camel.model.dataformat.ZipDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Zip Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Zip Data Format",
      "properties": {
        "compressionLevel": {
          "type": "number",
          "description": "Compression Level",
          "tooltip": "Compression Level",
          "title": "Compression Level"
        }
      }
    },
    "org.apache.camel.model.dataformat.FlatpackDataFormat": {
      "type": "object",
      "description": "org.apache.camel.model.dataformat.Flatpack Data Format",
      "tooltip": "org.apache.camel.model.dataformat.Flatpack Data Format",
      "properties": {}
    },
    "org.apache.camel.model.loadbalancer.TopicLoadBalancer": {
      "type": "object",
      "description": "org.apache.camel.model.loadbalancer.Topic Load Balancer",
      "tooltip": "org.apache.camel.model.loadbalancer.Topic Load Balancer",
      "properties": {}
    }
  },
  "languages": {
    "constant": {
      "name": "Constant",
      "description": "Constant expression"
    },
    "el": {
      "name": "EL",
      "description": "Unified expression language from JSP / JSTL / JSF"
    },
    "header": {
      "name": "Header",
      "description": "Header value"
    },
    "javaScript": {
      "name": "JavaScript",
      "description": "JavaScript expression"
    },
    "jxpath": {
      "name": "JXPath",
      "description": "JXPath expression"
    },
    "method": {
      "name": "Method",
      "description": "Method call expression"
    },
    "mvel": {
      "name": "MVEL",
      "description": "MVEL expression"
    },
    "ognl": {
      "name": "OGNL",
      "description": "OGNL expression"
    },
    "groovy": {
      "name": "Groovy",
      "description": "Groovy expression"
    },
    "property": {
      "name": "Property",
      "description": "Property value"
    },
    "python": {
      "name": "Python",
      "description": "Python expression"
    },
    "php": {
      "name": "PHP",
      "description": "PHP expression"
    },
    "ref": {
      "name": "Ref",
      "description": "Reference to a bean expression"
    },
    "ruby": {
      "name": "Ruby",
      "description": "Ruby expression"
    },
    "simple": {
      "name": "Simple",
      "description": "Simple expression language from Camel"
    },
    "spel": {
      "name": "Spring EL",
      "description": "Spring expression language"
    },
    "sql": {
      "name": "SQL",
      "description": "SQL expression"
    },
    "tokenize": {
      "name": "Tokenizer",
      "description": "Tokenizing expression"
    },
    "xpath": {
      "name": "XPath",
      "description": "XPath expression"
    },
    "xquery": {
      "name": "XQuery",
      "description": "XQuery expression"
    }
  }
};

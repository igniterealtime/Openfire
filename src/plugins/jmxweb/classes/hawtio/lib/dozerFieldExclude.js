var io_hawt_dozer_schema_FieldExclude = {
  "type" : "object",
  "properties" : {
    "a" : {
      "type" : "object",
      "properties" : {
        "value" : {
          "type" : "string"
        },
        "date-format" : {
          "type" : "string"
        },
        "type" : {
          "type" : "string",
          "enum" : [ "ITERATE", "GENERIC" ]
        },
        "set-method" : {
          "type" : "string"
        },
        "get-method" : {
          "type" : "string"
        },
        "key" : {
          "type" : "string"
        },
        "map-set-method" : {
          "type" : "string"
        },
        "map-get-method" : {
          "type" : "string"
        },
        "is-accessible" : {
          "type" : "boolean"
        },
        "create-method" : {
          "type" : "string"
        }
      },
      "required" : true
    },
    "b" : {
      "type" : "object",
      "properties" : {
        "value" : {
          "type" : "string"
        },
        "date-format" : {
          "type" : "string"
        },
        "type" : {
          "type" : "string",
          "enum" : [ "ITERATE", "GENERIC" ]
        },
        "set-method" : {
          "type" : "string"
        },
        "get-method" : {
          "type" : "string"
        },
        "key" : {
          "type" : "string"
        },
        "map-set-method" : {
          "type" : "string"
        },
        "map-get-method" : {
          "type" : "string"
        },
        "is-accessible" : {
          "type" : "boolean"
        },
        "create-method" : {
          "type" : "string"
        }
      },
      "required" : true
    },
    "type" : {
      "type" : "string",
      "enum" : [ "ONE_WAY", "BI_DIRECTIONAL" ]
    }
  }
};


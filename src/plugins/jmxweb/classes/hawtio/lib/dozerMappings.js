var io_hawt_dozer_schema_Mappings = {
  "type" : "object",
  "properties" : {
    "configuration" : {
      "type" : "object",
      "properties" : {
        "stop-on-errors" : {
          "type" : "boolean"
        },
        "date-format" : {
          "type" : "string"
        },
        "wildcard" : {
          "type" : "boolean"
        },
        "trim-strings" : {
          "type" : "boolean"
        },
        "bean-factory" : {
          "type" : "string"
        },
        "relationship-type" : {
          "type" : "string",
          "enum" : [ "CUMULATIVE", "NON_CUMULATIVE" ]
        },
        "custom-converters" : {
          "type" : "object",
          "properties" : {
            "converter" : {
              "type" : "array",
              "items" : {
                "type" : "object",
                "properties" : {
                  "class-a" : {
                    "type" : "object",
                    "properties" : {
                      "value" : {
                        "type" : "string"
                      },
                      "bean-factory" : {
                        "type" : "string"
                      },
                      "factory-bean-id" : {
                        "type" : "string"
                      },
                      "map-set-method" : {
                        "type" : "string"
                      },
                      "map-get-method" : {
                        "type" : "string"
                      },
                      "create-method" : {
                        "type" : "string"
                      },
                      "map-null" : {
                        "type" : "boolean"
                      },
                      "map-empty-string" : {
                        "type" : "boolean"
                      },
                      "is-accessible" : {
                        "type" : "boolean"
                      }
                    },
                    "required" : true
                  },
                  "class-b" : {
                    "type" : "object",
                    "properties" : {
                      "value" : {
                        "type" : "string"
                      },
                      "bean-factory" : {
                        "type" : "string"
                      },
                      "factory-bean-id" : {
                        "type" : "string"
                      },
                      "map-set-method" : {
                        "type" : "string"
                      },
                      "map-get-method" : {
                        "type" : "string"
                      },
                      "create-method" : {
                        "type" : "string"
                      },
                      "map-null" : {
                        "type" : "boolean"
                      },
                      "map-empty-string" : {
                        "type" : "boolean"
                      },
                      "is-accessible" : {
                        "type" : "boolean"
                      }
                    },
                    "required" : true
                  },
                  "type" : {
                    "type" : "string"
                  }
                }
              },
              "required" : true
            }
          }
        },
        "copy-by-references" : {
          "type" : "object",
          "properties" : {
            "copy-by-reference" : {
              "type" : "array",
              "items" : {
                "type" : "string"
              },
              "required" : true
            }
          }
        },
        "allowed-exceptions" : {
          "type" : "object",
          "properties" : {
            "exception" : {
              "type" : "array",
              "items" : {
                "type" : "string"
              },
              "required" : true
            }
          }
        },
        "variables" : {
          "type" : "object",
          "properties" : {
            "variable" : {
              "type" : "array",
              "items" : {
                "type" : "object",
                "properties" : {
                  "value" : {
                    "type" : "string"
                  },
                  "name" : {
                    "type" : "string"
                  }
                }
              },
              "required" : true
            }
          }
        }
      }
    },
    "mapping" : {
      "type" : "array",
      "items" : {
        "type" : "object",
        "properties" : {
          "class-a" : {
            "type" : "object",
            "properties" : {
              "value" : {
                "type" : "string"
              },
              "bean-factory" : {
                "type" : "string"
              },
              "factory-bean-id" : {
                "type" : "string"
              },
              "map-set-method" : {
                "type" : "string"
              },
              "map-get-method" : {
                "type" : "string"
              },
              "create-method" : {
                "type" : "string"
              },
              "map-null" : {
                "type" : "boolean"
              },
              "map-empty-string" : {
                "type" : "boolean"
              },
              "is-accessible" : {
                "type" : "boolean"
              }
            }
          },
          "class-b" : {
            "type" : "object",
            "properties" : {
              "value" : {
                "type" : "string"
              },
              "bean-factory" : {
                "type" : "string"
              },
              "factory-bean-id" : {
                "type" : "string"
              },
              "map-set-method" : {
                "type" : "string"
              },
              "map-get-method" : {
                "type" : "string"
              },
              "create-method" : {
                "type" : "string"
              },
              "map-null" : {
                "type" : "boolean"
              },
              "map-empty-string" : {
                "type" : "boolean"
              },
              "is-accessible" : {
                "type" : "boolean"
              }
            }
          },
          "fieldOrFieldExclude" : {
            "type" : "array",
            "items" : {
              "type" : "any"
            }
          },
          "date-format" : {
            "type" : "string"
          },
          "stop-on-errors" : {
            "type" : "boolean"
          },
          "wildcard" : {
            "type" : "boolean"
          },
          "trim-strings" : {
            "type" : "boolean"
          },
          "map-null" : {
            "type" : "boolean"
          },
          "map-empty-string" : {
            "type" : "boolean"
          },
          "bean-factory" : {
            "type" : "string"
          },
          "type" : {
            "type" : "string",
            "enum" : [ "ONE_WAY", "BI_DIRECTIONAL" ]
          },
          "relationship-type" : {
            "type" : "string",
            "enum" : [ "CUMULATIVE", "NON_CUMULATIVE" ]
          },
          "map-id" : {
            "type" : "string"
          }
        }
      }
    }
  }
};



0.0.1 / 2011-12-22
==================

  * Initial release

0.1.0 / 2012-01-04
==================

  * Added CQL Support
  * More Robust Connection Pooling
  * Respond with JS errors, not TException objects
  * Added proper serialization/deserialization support for all types

0.1.1 / 2012-01-06
==================

  * Fixed issue with the deserialization of column names in inspect

0.1.2 / 2012-01-09
==================

  * Better in-line documentation
  * Added JSDoc HTML documentation
  * Added keyspace object for referencing a keyspace
  * Added ability to create keyspaces
  * Added ability to drop keyspaces
  * Made ConnectionPool act more like connection
  * Added ability to create column families
  * Added ability to drop column families
  * Added ability to get a row, or part of a row

0.1.3 / 2012-01-11
==================

  * Better code coverage
  * Better documentation
  * Bug Fixes in serialization/deserialization
  * Tests for serialization/deserialization
  * Support for non default column values in column families

0.2.0 / 2012-01-12
==================

  * Added more test coverage
  * Added support for composite columns
  * Added support for composite keys
  * Fixed some deserialization issues

0.2.1 / 2012-01-13
==================

  * Fixed a buffer overflow issue when encoding numbers as UTF8

0.2.2 / 2012-01-16
==================

  * Added ability to specify column names in "get"

0.2.3 / 2012-02-04
==================

  * Fix issue in binary serialization, issue #1


0.2.4 / 2012-02-04
==================

  * Added binary support for strings in the serializer
  * Some serializer optimizations

0.3.0 / 2012-02-20
==================

  * Added getIndexed to ColumnFamily to get an array of rows by indexed columns

0.3.1 / 2012-03-06
==================

  * Updated Helenus-Thrift driver to properly decode 64 bit integers

0.3.2 / 2012-03-13
==================

  * Added ability to remove columns and rows [ #7 Matthias Eder ]

0.3.3 / 2012-03-22
==================

  * Added durability to the connection pool [ #12 ]
  * Fixed CQL Injection Vulnerability [ #11 ]
  * Added ability to use ? style variable replacement in CQL [ #11 ]

0.3.4 / 2012-03-25
==================

  * fixed issue with keys not marshalling when using indexed queries

0.3.5 / 2012-03-27
==================

  * fixed inconsistency in the consistency of the naming of the consistency level variables

0.3.6 / 2012-04-02
==================

  * Set default strategy_options in createKeyspace [ @ctavan #14 ]
  * Moved JSDoc Repo [ @ctavan #13 ]

0.3.7 / 2012-04-09
==================

  * Replace ? with %s globally [ @ctavan #18 ]
  * Store date-time in milliseconds rather than microseconds [ @ctavan #19 ]
  * Pool without keyspace but with close event [ @ctvan #17 ]

0.4.0 / 2012-04-12
==================

  * Add CQL 3 Support [ @ctavan #15 ]
  * Fixed issue in tombstone column deserialization [ @devdazed #33 ]
  * Added support for Int32Type and DecimalType [ @ctavan #23 ]

0.5.0 / 2012-04-20
==================

  * Use debian packages for Travis-CI [ @ctavan #40 ]
  * Stop Cassandra after tests in Travis-CI [ @ctavan #41 ]
  * Add support for ColumnFamily.truncate [ @devdazed #28 ]
  * Add better CQL escaping and parameterization [ @devdazed #36, #39 ]

0.5.1 / 2012-05-02
==================

  * Set column timestamps in microseconds

0.5.2 / 2012-05-11
==================

  * Removed unneeded escaping

0.5.3 / 2012-05-15
==================

  * Fix incorrect CQL setting after restarting dropped connection [ @devdazed #49 ]

0.5.4 / 2012-06-26
==================

  * Update to work with node v0.8+ [ @devdazed ]
  * Removed checks for ghosst in ranges for C* 1.1.1 [ @devdazed ]
  * Added Increment for CounterColumns [ @calvinfo #54 ]
  * Added TTL Option for Inserts [ @calvinfo #52 ]

0.5.5 / 2012-07-11
==================

  * Fixed issue with removing composites [ @calvinfo #59 ]
  * Added tests for Composite Removal [ @calvinfo #60 ]
  * Fix and Test for Counter Columns not returning Numbers in CQL [ @calvinfo #62 ]

0.5.6 / 2012-08-03
==================

  * Fixed issue with serialization of start / end in range queries [ @devdazed ]

0.5.7 / 2012-08-26
==================

  * Fixed issue with deserialization of ReversedType in CQL [ @devdazed #67 ]

0.5.8 / 2012-9-27
==================

  * Exposed Consistency Levels [ @calvinfo #73 ]
  * Adding callback on nodes unavailable exception [ @calvinfo #72 ]
  * Added hostPoolSize on ConnectionPool [ @hpainter #70 ]
  * Fixed issue with interpolating array parameters [ @devdazed #68 ]

0.5.9 / 2012-10-17
==================

  * Added fully functional composite column slices [ @devdazed #82 ]

0.6.0 / 2012-10-26
==================

  * Fixed issue with composite column meta column marshalling [ @devdazed ]
  * Exposed setColumnValidator to the user [ @devdazed #3 ]
  * Added support for column counts to Thrift API [ @devdazed #26 ]
  * Fixed boolean quoting issue in CQL2/3 [ @kzadorozhny #83 ]
  * Added support for negative integers in CQL2/3 [ @pieterbos ]

0.6.1 / 2012-12-11
==================

  * Added ability to override method that chooses a host from the pool [ @devdazed ]
  * Fix handling of reversed CFs for thrift users. [ @psanford  #88 ]

0.6.2 / 2013-01-23
==================

  * Added support for CQL3 in v1.2.0 [ @chris-rock #92 ]
  * Fixed CQL 3 in v 0.1.x [ @devdazed #93 ]



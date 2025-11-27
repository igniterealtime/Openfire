The content of this directory consists of 5 Openfire plugins, that, through
definition of 'parent plugin', live in a hierarchy.

These plugins are expected to be used by unit tests.

Five plugins exist, Node_A, Node_A_B, Node_A_C, Node_A_C_D and Node_A_C_E.

Node_A that has two children, Node_A_B and Node_A_C.
Node_A_C that has two child nodes, Node_A_C_D and Node_A_C_E.

This is a diagram of the hierarchy:

  Node_A
  ├── Node_A_B
  └── Node_A_C
      ├── Node_A_C_D
      └── Node_A_C_E

In the `initializePlugin()` as well as in the `destroyPlugin()` methods of each
plugin, a reference is made to a class that is provided by any parent plugin.
For example, Node_A_C_E references a class provided by Node_A_C, but it also
references a class provided by Node_A. These references are created to test
class loader behavior when loading/unloading plugins.

The source code of the plugins is provided in the archive named
testplugins-source.tar.gz. This contains a modular Maven project. To replace
the plugins, unpack this archive, and run `mvn clean package`. Then, from
each `target` directory, copy the plugin file (which is a JAR file that has
the name `openfire-assembly` in it). Make sure to rename the file to match
the pattern `pluginname.jar`.

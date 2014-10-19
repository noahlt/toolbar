Toolbar
-------

This program lets you define a toolbar of shortcuts to run terminal commands and see their output.

Probably the correct way to do this is to write some kind of wrapper around an existing terminal emulator like rxvt, but this program acts as its own (incomplete) terminal emulator.  This is because I wrote this partly because I wanted it, and partly to learn about writing a desktop application.

In writing this program, I learned about AWT, the Java2D API, Java threads, and running external processes in Java.  See the lengthy comment in `StreamHandler.stop()` for a bit of trivia about threads and external processes interacting.

Usage
=====

To build:

    $ cd toolbar
    $ script/build.sh

To run:

    $ script/run.sh

(As you can see, learning Java build tools was not part of this project.)

If you get a compile error, check that you're running Java 1.7.

This program reads its configuration from `~/.toolbar`.  If that file does not exist, `example.toolbar` is copied from this repo to `~/.toolbar`.  Hopefully the syntax is straightforward enough.

Future work
===========

I don't intend to keep on working on this, but if I did:

 - user should be able to scroll backwards on the output
 - manage each command's state separately
 - variable-width sidebar
 - hideable console
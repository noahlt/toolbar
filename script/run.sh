#!/bin/sh

if [ ! -e ~/.toolbar ]
then
  echo "copying example.toolbar to ~/.toolbar"
  cp example.toolbar ~/.toolbar
fi

java -cp lib/commons-exec-1.2.jar:build org.noahtye.toolbar.Toolbar

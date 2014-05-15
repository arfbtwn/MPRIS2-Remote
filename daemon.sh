#!/bin/bash

PROJECT_DIR=.
LIBMATTHEW=/usr/lib64/libmatthew-java

java -Djava.library.path="$PROJECT_DIR/MprisD/libs:$LIBMATTHEW" -classpath "$PROJECT_DIR/out/production/MprisD:$PROJECT_DIR/out/production/Common:$PROJECT_DIR/MprisD/libs/*:$PROJECT_DIR/libs/*" little.nj.Main

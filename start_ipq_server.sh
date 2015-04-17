#!/bin/bash

java -cp out/myhbase.jar cn.clickwise.clickad.hbase.ITServer -p 9999 >> logipq.txt 2>&1 &

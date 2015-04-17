#!/bin/bash

DAY=$1
echo "day:"$DAY
mkdir radius_parse/$DAY
for LINE in `ls /data/radius_data/$DAY`  
do  
        ./radius_hbase /data/radius_data/$DAY/$LINE > radius_parse/$DAY/$LINE   
        cat radius_parse/$DAY/$LINE | java -Xmx3000m -cp out/myhbase.jar cn.clickwise.clickad.hbase.ELITSRadiusStore add $DAY         
        echo $LINE 
done
#cat t.txt | java -Xmx3000m -XX:MaxPermSize=300m -cp out/myhbase.jar cn.clickwise.clickad.hbase.ELITSRadiusStore add 2015-04-05 > logaddd.txt 2>&1 &

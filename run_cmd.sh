#!/bin/bash
#Usage sh run_cmd 20150416 > logp.txt 2>&1 &
DAY=$1
echo "day:"$DAY
mkdir radius_parse/$DAY
INPUT=input_temp
for LINE in `ls $INPUT/$DAY`  
do  
        ./radius_hbase $INPUT/$DAY/$LINE > radius_parse/$DAY/$LINE   
        cat radius_parse/$DAY/$LINE | java -Xmx3000m -cp out/myhbase.jar cn.clickwise.clickad.hbase.OELITSRadiusStore add $DAY         
        echo $LINE 
done
#cat t.txt | java -Xmx3000m -XX:MaxPermSize=300m -cp out/myhbase.jar cn.clickwise.clickad.hbase.ELITSRadiusStore add 2015-04-05 > logaddd.txt 2>&1 &

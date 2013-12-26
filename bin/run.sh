#! /bin/sh

hive=`which hive`
hive_home=`echo ${hive%%/bin*}`
hadoop=`which hadoop`
hadoop_home=`echo ${hadoop%%/bin*}`

libs="${hadoop_home}/conf:${hive_home}/conf:${hadoop_home}/hadoop-core-1.0.4-SNAPSHOT.jar"

for lib in `ls ${hadoop_home}/lib/*.jar ${hive_home}/lib/*.jar`; do
	libs=$libs:$lib
done

curdir=`pwd`

libs=$libs:$curdir/dwautotest-1.0-SNAPSHOT.jar

#echo java -cp $libs com.dianping.data.dwautotest.QTestUtil

java -cp $libs com.dianping.data.dwautotest.QTestUtil

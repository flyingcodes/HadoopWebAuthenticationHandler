
# 单节点安装 Hadoop、Hive、Spark 伪分布式集群

## 一、最小化安装CentOS-7
1. 从 http://mirrors.aliyun.com/centos/7.9.2009/isos/x86_64/CentOS-7-x86_64-Minimal-2009.iso 下载 CentOS-7-x86_64-Minimal-2009.iso。

2. 最小化安装即可，安装完成之后配置网络，使得可以连接互联网，不用在线安装可以不用：
    ```sh
        [root@localhost /]# vi /etc/sysconfig/network-scripts/ifcfg-eth0
        TYPE=Ethernet
        PROXY_METHOD=none
        BROWSER_ONLY=no
        BOOTPROTO=static
        DEFROUTE=yes
        IPV4_FAILURE_FATAL=no
        IPV6INIT=yes
        IPV6_AUTOCONF=yes
        IPV6_DEFROUTE=yes
        IPV6_FAILURE_FATAL=no
        IPV6_ADDR_GEN_MODE=stable-privacy
        NAME=eth0
        UUID=91e995ef-c087-4209-b2ce-09387bb8073b
        DEVICE=eth0
        ONBOOT=yes
        IPADDR=172.28.176.231
        NETMASK=255.255.240.0
        GATEWAY=172.28.176.1
        DNS1=114.114.114.114
        DNS2=1.2.4.8
    ```
3. 创建 hadoop 用户，除修改系统配置之外，安装过程的大部分操作都是在 hadoop 用户下进行的。
    ```sh
        [root@localhost /]# useradd hadoop
        [root@localhost /]# passwd hadoop
    ```
    
## 二、下载需要的安装包
1. 下载 Java 8 自行安装，或者在线安装也可以：
    ```sh
        [root@localhost /]# yum install java-1.8.0-openjdk
    ```               
    上述命令安装的是 JRE 不是 JDK，所以很多 Java 工具没有，但是用来运行 Hadoop、Derby、Hive、Spark 完全可以，建议还是安装 JDK：
    ```sh        
        [root@localhost /]# yum install java-1.8.0-openjdk-devel
    ```            
2. 下载 Hadoop、Derby、Hive、Spark，下载地址 https://mirrors.aliyun.com/apache/ 。用于 Hive 的元数据库的 Derby 可以替换为 MySQL，但是这个比较小巧。
    ```sh    
        [root@localhost setups]# pwd
        /opt/setups
        [root@localhost setups]# ll
        -rw-r--r--. 1 root root  18535792 5月   3 2018 db-derby-10.14.2.0-bin.tar.gz
        -rw-r--r--. 1 root root 730107476 6月  26 07:35 hadoop-3.3.6.tar.gz
        -rw-r--r--. 1 root root 326940667 4月   9 2022 hive-3.1.3-bin.tar.gz
        -rw-r--r--. 1 root root 397281767 6月  20 07:25 spark-3.4.1-bin-hadoop3-scala2.13.tgz
    ```
3. 创建安装目标目录并授权，存放解压后的 hadoop 文件。
    ```sh
        [root@localhost /]# mkdir /opt/dis
        [root@localhost /]# chown -hR hadoop /opt/dis
    ```

## 三、安装 HADOOP
1. 安装 Hadoop 参考 https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-common/SingleCluster.html 。切换到 hadoop 用户，并创建 SSH 免密登录。
    ```sh
        [root@localhost /]# su hadoop
        [hadoop@localhost /]$ ssh-keygen -t rsa -P '' -f ~/.ssh/id_rsa
        [hadoop@localhost /]$ cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
        [hadoop@localhost /]$ chmod 0600 ~/.ssh/authorized_keys
        [hadoop@localhost /]$ ssh localhost
        Last login: Thu Sep  5 17:34:56 2023
        [hadoop@localhost ~]$ exit
        Connection to localhost closed.
        [hadoop@localhost /]$
    ```
2. 解压 HADOOP 安装包。
    ```sh
        [hadoop@localhost /]$ cd /opt/dis
        [hadoop@localhost dis]$ tar -xzvf /opt/setups/hadoop-3.3.6.tar.gz
        [hadoop@localhost dis]$ ll
        drwxr-xr-x. 11 hadoop hadoop 227 9月   5 17:43 hadoop-3.3.6
    ```
3. 切换到 root 用户，配置环境变量。注意，不要将 hadoop 的 sbin 目录配置到 PATH 中，避免后面和 spark 的 sbin 目录中的命令冲突。
    ```sh
        [root@localhost /]# vi /etc/profile.d/def.sh
        export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.382.b05-1.el7_9.x86_64/jre
        export HADOOP_HOME=/opt/dis/hadoop-3.3.6

        export PATH=$HADOOP_HOME/bin:$PATH
    ```
4. 切换到 hadoop 用户，加载环境变量。
    ```sh
        [hadoop@localhost dis]$ source /etc/profile
        [hadoop@localhost dis]$ hadoop -h
        Usage: hadoop [OPTIONS] SUBCOMMAND [SUBCOMMAND OPTIONS]
        ...
    ```
5. 修改核心配置。
    ```xml
        [hadoop@localhost dis]$ vi $HADOOP_HOME/etc/hadoop/core-site.xml
        <configuration>
            <property>
                <name>fs.defaultFS</name>
                <value>hdfs://localhost:9000</value>
            </property>
        </configuration>
    ```
6. 修改 HDFS 配置，因为是单服务器伪集群，所以数据拷贝只存放一份。
    ```xml
        [hadoop@localhost dis]$ vi $HADOOP_HOME/etc/hadoop/hdfs-site.xml
        <configuration>
            <property>
                <name>dfs.replication</name>
                <value>1</value>
            </property>
        </configuration>
    ```
7. 格式化 HDFS 文件系统。
    ```sh
        [hadoop@localhost dis]$ hdfs namenode -format
        ....
    ```
    没有输出 ERROR 就说明没有问题。
8. 启动 HDFS，也就是启动 NameNode 和 DataNode 进程。
    ```sh
        [hadoop@localhost dis]$ $HADOOP_HOME/sbin/start-dfs.sh
        Starting namenodes on [localhost]
        Starting datanodes
        Starting secondary namenodes [localhost.localdomain]
    ```
    启动之后可以通过 http://localhost:9870/ 或 http://ip:9870/ 访问 NameNode 监控页面，例如 http://172.28.176.231:9870/ 。
    如果需要停止 HDFS，执行下列命令：
    ```sh
        [hadoop@localhost dis]$ $HADOOP_HOME/sbin/stop-dfs.sh
    ```
9. 至此，可以以单节点方式运行 MapReduce 任务。

10. 当然也可以配置 YARN 伪集群，使 MapReduce 任务运行在 YARN 伪集群上。先修改 MapReduce 配置：
    ```xml
        [hadoop@localhost dis]$ vi $HADOOP_HOME/etc/hadoop/mapred-site.xml
        <configuration>
            <property>
                <name>mapreduce.framework.name</name>
                <value>yarn</value>
            </property>
            <property>
                <name>mapreduce.application.classpath</name>
                <value>$HADOOP_MAPRED_HOME/share/hadoop/mapreduce/*:$HADOOP_MAPRED_HOME/share/hadoop/mapreduce/lib/*</value>
            </property>
        </configuration>
    ```
11. 再修改 YARN 配置：
    ```xml
        [hadoop@localhost dis]$ vi $HADOOP_HOME/etc/hadoop/yarn-site.xml
        <configuration>
            <property>
                <name>yarn.nodemanager.aux-services</name>
                <value>mapreduce_shuffle</value>
            </property>
            <property>
                <name>yarn.nodemanager.env-whitelist</name>
                <value>JAVA_HOME,HADOOP_COMMON_HOME,HADOOP_HDFS_HOME,HADOOP_CONF_DIR,CLASSPATH_PREPEND_DISTCACHE,HADOOP_YARN_HOME,HADOOP_HOME,PATH,LANG,TZ,HADOOP_MAPRED_HOME</value>
            </property>
        </configuration>
    ```
12. 启动 YARN 的 ResourceManager 和 NodeManager 进程：
    ```sh
        [hadoop@localhost dis]$ $HADOOP_HOME/sbin/start-yarn.sh
        Starting resourcemanager
        Starting nodemanagers
    ```
    启动之后可以通过 http://localhost:8088/ 或 http://ip:8088/ 访问 YARN 的 ResourceManager 监控页面，例如 http://172.28.176.231:8088/ 。
    如果需要停止 YARN，执行下列命令：
    ```sh
        [hadoop@localhost dis]$ $HADOOP_HOME/sbin/stop-yarn.sh
    ```

## 四、安装 Derby
1. Hive 默认带有进程 Derby 功能作为元数据存储库，但是一般都是作为测试目的的 HIVE 独立使用，更加无法和其他开源组件（如 Spark）集成使用。因此，需要独立安装数据库作为 HIVE 的元数据存储库，这里选择轻量级的 Derby。安装 Derby 参考 https://cwiki.apache.org/confluence/display/Hive/HiveDerbyServerMode 。先解压 Derby 安装包。
    ```sh
        [hadoop@localhost /]$ cd /opt/dis
        [hadoop@localhost dis]$ tar -xzvf /opt/setups/db-derby-10.14.2.0-bin.tar.gz
        ...
        [hadoop@localhost dis]$ mv db-derby-10.14.2.0-bin/ derby-10.14.2.0
        [hadoop@localhost dis]$ ll
        drwxrwxr-x.  9 hadoop hadoop 176 9月   6 11:30 derby-10.14.2.0
        drwxr-xr-x. 11 hadoop hadoop 227 9月   5 17:43 hadoop-3.3.6
    ```
    再创建数据存储目录：    
    ```sh
        [hadoop@localhost dis]$ mkdir derby-10.14.2.0/data
    ```
2. 切换到 root 用户，配置 Derby 环境变量。
    ```sh
        [root@localhost /]# vi /etc/profile.d/def.sh
        export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.382.b05-1.el7_9.x86_64/jre
        export HADOOP_HOME=/opt/dis/hadoop-3.3.6

        export DERBY_INSTALL=/opt/dis/derby-10.14.2.0
        export DERBY_HOME=/opt/dis/derby-10.14.2.0

        export PATH=$HADOOP_HOME/bin:$PATH
    ``` 
3. 切换到 hadoop 用户，加载环境变量。
    ```sh
        [hadoop@localhost dis]$ source /etc/profile
    ```
4. 转到 data 目录，并启动 Derby 服务，建议在新的终端执行。
    ```sh
        [hadoop@localhost dis]$ cd $DERBY_HOME/data
        [hadoop@localhost data]$ nohup ../bin/startNetworkServer -h 0.0.0.0 &
        [1] 25049
        [hadoop@localhost data]$ nohup: 忽略输入并把输出追加到"nohup.out"
    ```
    如果需要停止 Derby，执行下列命令：
    ```sh
        [hadoop@localhost dis]$ $DERBY_HOME/bin/stopNetworkServer
    ```

## 五、安装 HIVE
1. 安装 Hive 参考 https://cwiki.apache.org/confluence/display/Hive/GettingStarted 和 https://cwiki.apache.org/confluence/display/Hive/HiveDerbyServerMode 。解压 Hive 安装包。
    ```sh
        [hadoop@localhost /]$ cd /opt/dis
        [hadoop@localhost dis]$ tar -xzvf /opt/setups/hive-3.1.3-bin.tar.gz
        [hadoop@localhost dis]$ mv apache-hive-3.1.3-bin/ hive-3.1.3
        [hadoop@localhost dis]$ ll
        drwxrwxr-x.  9 hadoop hadoop 176 9月   6 11:30 derby-10.14.2.0
        drwxr-xr-x. 11 hadoop hadoop 227 9月   5 17:43 hadoop-3.3.6
        drwxrwxr-x. 11 hadoop hadoop 221 9月   6 09:31 hive-3.1.3
    ```
2. 切换到 root 用户，配置 Hive 环境变量。
    ```sh
        [root@localhost /]# vi /etc/profile.d/def.sh
        export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.382.b05-1.el7_9.x86_64/jre
        export HADOOP_HOME=/opt/dis/hadoop-3.3.6

        export DERBY_INSTALL=/opt/dis/derby-10.14.2.0
        export DERBY_HOME=/opt/dis/derby-10.14.2.0

        export HADOOP=/opt/dis/hadoop-3.3.6/bin/hadoop
        export HIVE_HOME=/opt/dis/hive-3.1.3

        export PATH=$HADOOP_HOME/bin:$HIVE_HOME/bin:$PATH
    ``` 
3. 切换到 hadoop 用户，加载环境变量。
    ```sh
        [hadoop@localhost dis]$ source /etc/profile
    ```
4. 修改 Hive 配置：
    ```xml
        [hadoop@localhost dis]$ cp $HIVE_HOME/conf/hive-default.xml.template $HIVE_HOME/conf/hive-site.xml
        [hadoop@localhost dis]$ vi $HIVE_HOME/conf/hive-site.xml
        <configuration>
            <property>
                <name>javax.jdo.option.ConnectionURL</name>
                <value>jdbc:derby://localhost:1527/metastoredb;create=true</value>
                <description>JDBC connect string for a JDBC metastore</description>
            </property>
            <property>
                <name>javax.jdo.option.ConnectionDriverName</name>
                <value>org.apache.derby.jdbc.ClientDriver</value>
                <description>Driver class name for a JDBC metastore</description>
            </property>
        </configuration>
    ```
5. 复制 Derby 的客户端驱动 jar 包到 Hive 的依赖包 lib 目录：
    ```sh
        [hadoop@localhost dis]$ cp $DERBY_HOME/lib/derbyclient.jar $HIVE_HOME/lib
        [hadoop@localhost dis]$ cp $DERBY_HOME/lib/derbytools.jar $HIVE_HOME/lib
    ```
    如使用过程中遇到 `javax.jdo.JDOFatalInternalException: Error creating transactional connection factory`, 异常堆栈为 `org.datanucleus.exceptions.ClassNotResolvedException: Class 'org.apache.derby.jdbc.ClientDriver' was not found in the CLASSPATH.` 的异常，要么将 Derby 的客户端驱动 jar 包都放到 CLASSPATH 中，要么将 Derby 的客户端驱动 jar 包到 Hadoop 的依赖包 lib 目录：
    ```sh
        [hadoop@localhost dis]$ cp $DERBY_HOME/lib/derbyclient.jar $HADOOP_HOME/lib
        [hadoop@localhost dis]$ cp $DERBY_HOME/lib/derbytools.jar $HADOOP_HOME/lib
    ```
6. 初始化 Hive 元数据存储库。
    ```sh
        [hadoop@localhost dis]$ $HIVE_HOME/bin/schematool -dbType derby -initSchema
        Metastore connection URL:        jdbc:derby://localhost:1527/metastoredb;create=true
        Metastore Connection Driver :    org.apache.derby.jdbc.ClientDriver
        Metastore connection User:       APP
        Starting metastore schema initialization to 3.1.0
        Initialization script hive-schema-3.1.0.derby.sql
        ... <中间输出几十行空行> ...
        Initialization script completed
        schemaTool completed
    ```
    如果没有输出错误，如上正常信息，说明初始化成功。
    如果出现 `Missing Hive CLI Jar` 错误，应该安装包损坏了，解压之后 lib 目录没有 `hive-cli-3.1.3.jar` 文件，我第一次执行就是遇到这个问题，用 7zip 验证一下，果然安装包已经损坏，重新下载之后，先用 7zip 解压没问题，也有了 `hive-cli-3.1.3.jar`，重来一遍，正常了。

7. 测试 hive。
    ```sh
        [hadoop@localhost dis]$ hive
        ...
        Hive Session ID = 3dbcf683-fe89-4b5d-8a32-dc51e3865320
        hive>create database testdb;
        OK
        Time taken: 1.14 seconds
        hive>create table testdb.testa(id int, name string);
        OK
        Time taken: 0.812 seconds
        hive>insert into testdb.testa values(1001, 'gege');
        ...
        OK
        Time taken: 30.197 seconds
        hive>insert into testdb.testa values(1002, 'meimei');
        ...
        OK
        Time taken: 23.987 seconds
        hive>select * from testdb.testa;
        OK
        1001    gege
        1002    meimei
        Time taken: 0.232 seconds, Fetched: 2 row(s)
    ```
    所有语句都执行成功，说明安装和配置都正确。

## 六、安装 SPARK
1. 安装 Spark 参考 https://blog.csdn.net/qq_46009608/article/details/108911193 、https://blog.csdn.net/weixin_44480968/article/details/119600816 和 https://blog.csdn.net/qq_53114527/article/details/128249079 。解压 Spark 安装包。
    ```sh
        [hadoop@localhost /]$ cd /opt/dis
        [hadoop@localhost dis]$ tar -xzvf /opt/setups/spark-3.4.1-bin-hadoop3-scala2.13.tgz
        [hadoop@localhost dis]$ mv spark-3.4.1-bin-hadoop3-scala2.13/ spark-3.4.1
        [hadoop@localhost dis]$ ll
        drwxrwxr-x.  9 hadoop hadoop 176 9月   6 11:30 derby-10.14.2.0
        drwxr-xr-x. 11 hadoop hadoop 227 9月   5 17:43 hadoop-3.3.6
        drwxrwxr-x. 11 hadoop hadoop 221 9月   6 09:31 hive-3.1.3
        drwxr-xr-x. 14 hadoop hadoop 223 9月   6 15:06 spark-3.4.1
    ```
2. 切换到 root 用户，配置 Hive 环境变量。
    ```sh
        [root@localhost /]# vi /etc/profile.d/def.sh
        export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.382.b05-1.el7_9.x86_64/jre
        export HADOOP_HOME=/opt/dis/hadoop-3.3.6

        export DERBY_INSTALL=/opt/dis/derby-10.14.2.0
        export DERBY_HOME=/opt/dis/derby-10.14.2.0

        export HADOOP=/opt/dis/hadoop-3.3.6/bin/hadoop
        export HIVE_HOME=/opt/dis/hive-3.1.3

        export SPARK_HOME=/opt/dis/spark-3.4.1

        export PATH=$HADOOP_HOME/bin:$HIVE_HOME/bin:$SPARK_HOME/sbin:$SPARK_HOME/bin:$PATH        
    ``` 
3. 切换到 hadoop 用户，加载环境变量。
    ```sh
        [hadoop@localhost dis]$ source /etc/profile
    ```

4. 配置 Spark 环境配置文件。
    ```sh
        [hadoop@localhost dis]$ cp $SPARK_HOME/conf/spark-env.sh.template $SPARK_HOME/conf/spark-env.sh
        [hadoop@localhost dis]$ vi $SPARK_HOME/conf/spark-env.sh
        export HADOOP_CONF_DIR=/opt/dis/hadoop-3.3.6/etc/hadoop
        export YARN_CONF_DIR=$HADOOP_CONF_DIR

        export SPARK_HISTORY_OPTS="-Dspark.history.ui.port=18080 -Dspark.history.retainedApplications=30 -Dspark.history.fs.logDirectory=hdfs://localhost:9000/tmp/spark-logs-yarn"
    ```
    然后，创建 Spark 日志存放的 HDFS 目录：
    ```sh
        [hadoop@localhost dis]$ hadoop fs -mkdir /tmp/spark-logs-yarn
        [hadoop@localhost dis]$ hadoop fs -chmod -R +777 /tmp
    ```    
5. 配置 Spark 默认配置文件。
    ```sh
        [hadoop@localhost dis]$ cp $SPARK_HOME/conf/spark-defaults.conf.template $SPARK_HOME/conf/spark-defaults.conf
        [hadoop@localhost dis]$ vi $SPARK_HOME/conf/spark-defaults.conf
        spark.eventLog.enabled=true
        spark.eventLog.dir=hdfs://localhost:9000/tmp/spark-logs-yarn
    ```   
6. 复制 Hive 配置文件到 Spark 配置目录，以便 Spark 可以读取 Hive 元数据存储库和相关配置。
    ```sh
        [hadoop@localhost dis]$ cp $HIVE_HOME/conf/hive-site.xml $SPARK_HOME/conf/
        [hadoop@localhost dis]$ ll $SPARK_HOME/conf/
        -rw-r--r--. 1 hadoop hadoop 1105 6月  20 06:39 fairscheduler.xml.template
        -rw-rw-r--. 1 hadoop hadoop 1358 9月   6 15:05 hive-site.xml
        -rw-r--r--. 1 hadoop hadoop 3350 6月  20 06:39 log4j2.properties.template
        -rw-r--r--. 1 hadoop hadoop 9141 6月  20 06:39 metrics.properties.template
        -rw-r--r--. 1 hadoop hadoop 1384 9月   6 16:21 spark-defaults.conf
        -rw-r--r--. 1 hadoop hadoop 1292 6月  20 06:39 spark-defaults.conf.template
        -rwxr-xr-x. 1 hadoop hadoop 4966 9月   6 16:20 spark-env.sh
        -rwxr-xr-x. 1 hadoop hadoop 4694 6月  20 06:39 spark-env.sh.template
        -rw-r--r--. 1 hadoop hadoop  865 6月  20 06:39 workers.template        
    ``` 
7. 复制 Derby 的客户端驱动 jar 包到 Spark 的依赖包 jars 目录：
    ```sh
        [hadoop@localhost dis]$ cp $DERBY_HOME/lib/derbyclient.jar $SPARK_HOME/jars
        [hadoop@localhost dis]$ cp $DERBY_HOME/lib/derbytools.jar $SPARK_HOME/jars
    ```
8. 以 YARN 模式启动 Thrift Server 服务。
    ```sh
        [hadoop@localhost dis]$ $SPARK_HOME/sbin/start-thriftserver.sh --master yarn
        starting org.apache.spark.sql.hive.thriftserver.HiveThriftServer2, logging to /opt/dis/spark-3.4.1/logs/spark...HiveThriftServer...out
    ```
    没有出现错误信息，说明配置正确且启动成功。
    如果需要关闭 Thrift Server 服务，执行下列命令：
    ```sh
        [hadoop@localhost dis]$ $SPARK_HOME/sbin/stop-thriftserver.sh
        stopping org.apache.spark.sql.hive.thriftserver.HiveThriftServer2
    ```
10. 测试 Spark。
    ```sh
        [hadoop@localhost dis]$ $SPARK_HOME/bin/beeline -u jdbc:hive2://localhost:10000 -n hadoop
        Connecting to jdbc:hive2://localhost:10000
        Connected to: Spark SQL (version 3.4.1)
        Driver: Hive JDBC (version 2.3.9)
        Transaction isolation: TRANSACTION_REPEATABLE_READ
        Beeline version 2.3.9 by Apache Hive
        0: jdbc:hive2://localhost:10000> select * from testdb.testa;
        +-------+---------+
        |  id   |  name   |
        +-------+---------+
        | 1001  | gege    |
        | 1002  | meimei  |
        +-------+---------+
        2 rows selected (11.406 seconds)
        0: jdbc:hive2://localhost:10000>
    ```
    如果出现 `Could not open connection to the HS2 server. Please check the server URI and if the URI is correct, then ask the administrator to check the server status. Error: Could not open client transport with JDBC Uri: jdbc:hive2://localhost:10000: java.net.ConnectException: 拒绝连接 (Connection refused) (state=08S01,code=0)` 的错误信息，说明 Thrift Server 服务尚未启动完成，等待一会儿之后重试即可。

## 七、启动集群
1. 启动 HDFS，也就是启动 NameNode 和 DataNode 进程。
    ```sh
        [hadoop@localhost dis]$ $HADOOP_HOME/sbin/start-dfs.sh
        Starting namenodes on [localhost]
        Starting datanodes
        Starting secondary namenodes [localhost.localdomain]
    ```
2. 启动 YARN 的 ResourceManager 和 NodeManager 进程：
    ```sh
        [hadoop@localhost dis]$ $HADOOP_HOME/sbin/start-yarn.sh
        Starting resourcemanager
        Starting nodemanagers
    ```
3. 转到 data 目录，并启动 Derby 服务，建议在新的终端执行。
    ```sh
        [hadoop@localhost dis]$ cd $DERBY_HOME/data
        [hadoop@localhost data]$ nohup ../bin/startNetworkServer -h 0.0.0.0 &
        [1] 9626
        [hadoop@localhost data]$ nohup: 忽略输入并把输出追加到"nohup.out"
    ```
4. 以 YARN 模式启动 Thrift Server 服务。
    ```sh
        [hadoop@localhost dis]$ $SPARK_HOME/sbin/start-thriftserver.sh --master yarn
        starting org.apache.spark.sql.hive.thriftserver.HiveThriftServer2, logging to /opt/dis/spark-3.4.1/logs/spark-hadoop-org.apache.spark.sql.hive.thriftserver.HiveThriftServer2-1-localhost.localdomain.out
    ```
5. 连接 Spark。
    ```sh
        [hadoop@localhost dis]$ $SPARK_HOME/bin/beeline -u jdbc:hive2://localhost:10000 -n hadoop
        Connecting to jdbc:hive2://localhost:10000
        Connected to: Spark SQL (version 3.4.1)
        Driver: Hive JDBC (version 2.3.9)
        Transaction isolation: TRANSACTION_REPEATABLE_READ
        Beeline version 2.3.9 by Apache Hive
        0: jdbc:hive2://localhost:10000> select * from testdb.testa;
        +-------+---------+
        |  id   |  name   |
        +-------+---------+
        | 1001  | gege    |
        | 1002  | meimei  |
        +-------+---------+
        2 rows selected (11.406 seconds)
        0: jdbc:hive2://localhost:10000>
    ```
    如果出现 `Could not open connection to the HS2 server. Please check the server URI and if the URI is correct, then ask the administrator to check the server status. Error: Could not open client transport with JDBC Uri: jdbc:hive2://localhost:10000: java.net.ConnectException: 拒绝连接 (Connection refused) (state=08S01,code=0)` 的错误信息，说明 Thrift Server 服务尚未启动完成，等待一会儿之后重试即可。

## 八、关闭集群
1. 停止 Thrift Server 服务。
    ```sh
        [hadoop@localhost dis]$ $SPARK_HOME/sbin/stop-thriftserver.sh
        stopping org.apache.spark.sql.hive.thriftserver.HiveThriftServer2
    ```
2. 停止 Derby 服务。
    ```sh
        [hadoop@localhost dis]$ $DERBY_HOME/bin/stopNetworkServer
        Tue Sep 12 11:36:54 CST 2023 : Apache Derby 网络服务器 - 10.14.2.0 - (1828579) 关闭
        [1]+  完成  nohup ../bin/startNetworkServer -h 0.0.0.0(工作目录：/opt/dis/derby-10.14.2.0/data)
        (当前工作目录：/opt/dis)
    ```
3. 停止 YARN 服务。
    ```sh
        [hadoop@localhost dis]$ $HADOOP_HOME/sbin/stop-yarn.sh
        Stopping nodemanagers
        localhost: WARNING: nodemanager did not stop gracefully after 5 seconds: Trying to kill with kill -9
        Stopping resourcemanager
    ```
    如果是要关闭服务器，上面的 WARNING 可以忽略，如果不关闭服务器重启集群，那么需要 kill 对应的进程。
4. 停止 HDFS 服务。
    ```sh
        [hadoop@localhost dis]$ $HADOOP_HOME/sbin/stop-dfs.sh
        Stopping namenodes on [localhost]
        Stopping datanodes
        Stopping secondary namenodes [localhost.localdomain]        
    ```
5. 关闭服务器，需要切换到 root 用户下才能执行。
    ```sh
        [hadoop@localhost dis]$ exit
        exit
        [root@localhost ~]# shutdown -h now
        Connection to 172.28.176.231 closed by remote host.
        Connection to 172.28.176.231 closed.       
    ```


`Bye~`
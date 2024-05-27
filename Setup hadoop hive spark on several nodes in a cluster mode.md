
# 多节点安装 Hadoop、Hive、Spark 集群

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
1. 安装文件同步 rsync 工具，每个节点都需要安装。下载地址 http://mirrors.aliyun.com/centos/7.9.2009/os/x86_64/Packages/rsync-3.1.2-10.el7.x86_64.rpm 。
    ```sh
        [root@localhost /]# rpm -ivh /opt/setups/rsync-3.1.2-10.el7.x86_64.rpm
    ```  

## 一、主节点配置
1. 配置集群节点 IP 映射
    ```sh
        [root@localhost /]# vi /etc/hosts
        192.168.1.10 yh10
        192.168.1.20 yh20
        192.168.1.30 yh30
        192.168.1.40 yh40
        192.168.1.50 yh50
    ```     
2. 创建集群文件同步命令，确保每个节点都安装了 rsync 工具
    ```sh
        [root@localhost /]# vi /usr/local/bin/xsync
        #!/bin/bash
        #1 获取输入参数个数，如果没有参数，直接退出
        pcount=$#
        if [ $pcount -lt 1 ]
        then
            echo Not Enough Arguement!
            exit;
        fi

        #2. 遍历集群所有机器
        # 也可以采用：
        # for host in hadoop{102..104};
        for host in yh20 yh30 yh40 yh50
        do
            echo ====================    $host    ====================
            #3. 遍历所有目录，挨个发送
            for file in $@
            do
                #4 判断文件是否存在
                if [ -e $file ]
                then
                    #5. 获取父目录
                    pdir=$(cd -P $(dirname $file); pwd)

                    #6. 获取当前文件的名称
                    fname=$(basename $file)
                    echo "---> $pdir/$fname"
                    
                    #7. 通过ssh执行命令：在$host主机上递归创建文件夹（如果存在该文件夹）
                    ssh $host "mkdir -p $pdir"
                    
                    #8. 远程同步文件至$host主机的$USER用户的$pdir文件夹下
                    rsync -av $pdir/$fname $USER@$host:$pdir
                else
                    echo $file does not exists!
                fi
            done
        done
        [root@localhost /]# chmod +xxx /usr/local/bin/xsync
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
3. 创建数据目标目录并授权，存放 hadoop 需要存储的数据文件或临时文件。
    ```sh
        [root@localhost /]# mkdir /opt/store
        [root@localhost /]# chown -hR hadoop /opt/store
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
    复制公钥到其他节点。
    ```sh
        [hadoop@localhost /]$ ssh-copy-id yh20
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

        export PATH=$JAVA_HOME/bin:$HADOOP_HOME/bin:$PATH
    ```
4. 切换到 hadoop 用户，加载环境变量。
    ```sh
        [hadoop@localhost dis]$ source /etc/profile
        [hadoop@localhost dis]$ hadoop -h
        Usage: hadoop [OPTIONS] SUBCOMMAND [SUBCOMMAND OPTIONS]
        ...
    ```
4. 配置主节点 masters 文件。增加 $HADOOP_HOME/etc/hadoop/masters 文件，该文件指定 NameNode 和 SecondaryNameNode 节点所在的服务器机器。
   添加 NameNode 节点的主机名 yh10，不建议使用 IP 地址，因为 IP 地址可能会变化，但是主机名一般不会变化。
   另外，为了将 SecondaryNameNode 与 NameNode 分开，可将 SecondaryNameNode 节点的主机也加入到 masters 文件之中，例如 yh20。
    ```sh
        [hadoop@localhost dis]$ vi $HADOOP_HOME/etc/hadoop/masters
        yh10
        yh20
    ```  
5. 配置 workers 节点。hadoop 2.x 为 slaves 文件，hadoop 3.x 为 workers 文件。主节点特有，同步到其他节点也不影响。修改 $HADOOP_HOME/etc/hadoop/workers 文件，该文件指定哪些服务器节点是 DataNode 节点。
   删除 localhost，添加所有 DataNode 节点的主机名。
    ```sh
        [hadoop@localhost dis]$ vi $HADOOP_HOME/etc/hadoop/workers
        yh30
        yh40
        yh50
    ``` 
5. 创建 hadoop 所需要的目录。
    ```sh
        [hadoop@localhost dis]$ mkdir -p /opt/store/hadoop/data
        [hadoop@localhost dis]$ mkdir -p /opt/store/hadoop/temp
    ```  
6. 修改核心配置。
    ```xml
        [hadoop@localhost dis]$ vi $HADOOP_HOME/etc/hadoop/core-site.xml
        <configuration>
            <property>
                <name>hadoop.tmp.dir</name>
                <value>/opt/store/hadoop/temp</value>
            </property>
            <property>
                <name>fs.defaultFS</name>
                <value>hdfs://yh10:9000</value>
            </property>
        </configuration>
    ```
    特别注意：如没有配置 hadoop.tmp.dir 参数，此时系统默认的临时目录为：/tmp/hadoop-hadoop。而这个目录在每次重启后都会被删除，必须重新执行format才行，否则会出错。
7. 修改 HDFS 配置。
    ```xml
        [hadoop@localhost dis]$ vi $HADOOP_HOME/etc/hadoop/hdfs-site.xml
        <configuration>
            <property>
                <name>dfs.namenode.http-address</name>
                <value>yh10:50070</value>
            </property>
            <property>
                <name>dfs.namenode.secondary.http-address</name>
                <value>yh20:50090</value>
            </property>
            <property>
                <name>dfs.datanode.data.dir</name> 
                <value>/opt/store/hadoop/data</value>
            </property>
        </configuration>
    ```
7. 同步 HADOOP 存储目录和配置。
    ```sh
        [hadoop@localhost dis]$ xsync /opt/store/hadoop/
        ...
        [hadoop@localhost dis]$ xsync $HADOOP_HOME/etc/hadoop/
        ...
    ```   
8. 格式化 HDFS 文件系统。
    ```sh
        [hadoop@localhost dis]$ hdfs namenode -format
        ....
    ```
    没有输出 ERROR 就说明没有问题。
9.  启动 HDFS，也就是启动 NameNode 和 DataNode 进程。
    ```sh
        [hadoop@localhost dis]$ $HADOOP_HOME/sbin/start-dfs.sh
        Starting namenodes on [localhost]
        Starting datanodes
        Starting secondary namenodes [localhost.localdomain]
    ```
    启动之后可以通过 http://localhost:9870/ 或 http://ip:9870/ 访问 NameNode 监控页面，例如 http://172.28.176.231:9870/ 。
    其中端口 9870 是默认的端口，由 dfs.namenode.http-address 参数指定，当前配置为 50070，那么就是 http://172.28.176.231:50070/ 。
    如果需要停止 HDFS，执行下列命令：
    ```sh
        [hadoop@localhost dis]$ $HADOOP_HOME/sbin/stop-dfs.sh
    ```
10. 至此，可以以单节点方式运行 MapReduce 任务。

11. 当然也可以配置 YARN 伪集群，使 MapReduce 任务运行在 YARN 伪集群上。先修改 MapReduce 配置：
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
12. 再修改 YARN 配置：
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
            <!-- 如果下面项不配置，那么在 YARN 页面只能看到主节点一个节点 -->
            <property>
                <name>yarn.resourcemanager.hostname</name>
                <value>yh10</value>
            </property>
        </configuration>
    ```
13. 启动 YARN 的 ResourceManager 和 NodeManager 进程：
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
12. 调整 YARN 可用资源，根据节点物理内存和CPU核心数，增加下列配置：
    ```xml
        [hadoop@localhost dis]$ vi $HADOOP_HOME/etc/hadoop/yarn-site.xml
        <configuration>
            <property>
                <name>yarn.nodemanager.resource.memory-mb</name>
                <value>32768</value>
            </property>
            <property>
                <name>yarn.nodemanager.resource.cpu-vcores</name>
                <value>16</value>
            </property>
        </configuration>
    ```
    然后重新启动 YARN 即可生效。
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

        export HADOOP=/opt/dis/hadoop-3.3.6/bin/hadoop
        export HIVE_HOME=/opt/dis/hive-3.1.3

        export PATH=$JAVA_HOME/bin:$HADOOP_HOME/bin:$HIVE_HOME/bin:$PATH
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
                <name>javax.jdo.option.ConnectionDriverName</name>
                <value>com.mysql.cj.jdbc.Driver</value>
                <description>Driver class name for a JDBC metastore</description>
            </property>
            <property>
                <name>javax.jdo.option.ConnectionUserName</name>
                <value>root</value>
                <description>Username to use against metastore database</description>
            </property>
            <property>
                <name>javax.jdo.option.ConnectionPassword</name>
                <value>123456</value>
                <description>password to use against metastore database</description>
            </property>
            <property>
                <name>javax.jdo.option.ConnectionURL</name>
                <value>jdbc:mysql://yh10:3306/hive?useUnicode=true&amp;characterEncoding=utf8&amp;useSSL=false&amp;serverTimezone=GMT</value>
                <description>
                    JDBC connect string for a JDBC metastore.
                    To use SSL to encrypt/authenticate the connection, provide database-specific SSL flag in the connection URL.
                    For example, jdbc:postgresql://myhost/db?ssl=true for postgres database.
                </description>
            </property>
            <property>
                <name>hive.metastore.db.type</name>
                <value>mysql</value>
                <description>
                    Expects one of [derby, oracle, mysql, mssql, postgres].
                    Type of database used by the metastore. Information schema &amp; JDBCStorageHandler depend on it.
                </description>
            </property>
        </configuration>
    ```
1. 复制 MySQL 的客户端驱动 jar 包到 Hive 的依赖包 lib 目录：
    ```sh
        [hadoop@localhost dis]$ cp /opt/setups/mysql-connector-java-8.0.11.jar $HIVE_HOME/lib
    ```
    如使用过程中遇到 `javax.jdo.JDOFatalInternalException: Error creating transactional connection factory`, 异常堆栈为 `org.datanucleus.exceptions.ClassNotResolvedException: Class 'org.apache.derby.jdbc.ClientDriver' was not found in the CLASSPATH.` 的异常，要么将 MySQL 的客户端驱动 jar 包都放到 CLASSPATH 中，要么将 MySQL 的客户端驱动 jar 包到 Hadoop 的依赖包 lib 目录：
    ```sh
        [hadoop@localhost dis]$ cp /opt/setups/mysql-connector-java-8.0.11.jar $HADOOP_HOME/lib
    ```
2. 初始化 Hive 元数据存储库。
    ```sh
        [hadoop@localhost dis]$ $HIVE_HOME/bin/schematool -dbType mysql -initSchema
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

3. 测试 hive。
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

        export SPARK_HISTORY_OPTS="-Dspark.history.ui.port=18080 -Dspark.history.retainedApplications=30 -Dspark.history.fs.logDirectory=hdfs://yh10:9000/tmp/spark-logs-yarn"
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
        spark.eventLog.dir=hdfs://yh10:9000/tmp/spark-logs-yarn
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
7. 复制 MySQL 的客户端驱动 jar 包到 Spark 的依赖包 jars 目录：
    ```sh
        [hadoop@localhost dis]$ cp /opt/setups/mysql-connector-java-8.0.11.jar $SPARK_HOME/jars
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
        [hadoop@localhost dis]$ $SPARK_HOME/bin/beeline -u jdbc:hive2://yh10:10000 -n hadoop
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
4. 调整 Spark 内存和执行器等配置。
    ```sh
        [hadoop@localhost dis]$ vi $SPARK_HOME/conf/spark-env.sh
        export SPARK_EXECUTOR_INSTANCES=16
        export SPARK_EXECUTOR_CORES=1
        export SPARK_EXECUTOR_MEMORY=4G
        export SPARK_DRIVER_MEMORY=2G
    ```
    修改完之后重启 Spark 即可生效。

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
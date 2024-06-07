
yarn rmadmin -refreshNodesResources

yarn rmadmin -refreshNodes

yarn rmadmin -refreshQueues

yarn rmadmin -refreshSuperUserGroupsConfiguration
yarn rmadmin -updateNodeResource 

hdfs dfsadmin -refreshNodes

yarn --daemon start nodemanager

<property>
<name>yarn.scheduler.minimum-allocation-mb</name>
<value>2500</value>
</property>
<property>
<name>yarn.app.mapreduce.am.resource.memory-mb</name>
<value>1500</value>
</property>
<property>
<name>mapreduce.map.resource.memory-mb</name>
<value>2000</value>
</property>
<property>
<name>mapreduce.reduce.resource.memory-mb</name>
<value>2500</value>
</property>

<property>
    <name>dfs.datanode.du.reserved.pct</name>
    <value>20</value>
</property>

1、在 workers 文件中填上全部计算节点（NodeManager）的主机名称，一行一个，最后一个也要换行结尾：
```
yh10
yh20
yh30
yh40
yh50
```
这样配置之后，start-dfs.sh 会在这些节点上启动 DataNode 进程（下一步就配置把部分节点的 DataNode 排除），start-yarn.sh 会在这些节点上启动 NodeManager 进程。

2、配置 dfs.hosts 和 dfs.hosts.exclude，属性值不支持环境变量，必须用绝对路径：
```
<property>
    <name>dfs.hosts</name>
    <value>/opt/dis/hadoop-3.3.6/etc/hadoop/dfs.hosts</value>
</property>
<property>
    <name>dfs.hosts.exclude</name>
    <value>/opt/dis/hadoop-3.3.6/etc/hadoop/dfs.hosts.exclude</value>
</property>
```
然后在 dfs.hosts 文件中填写全部数据节点（DataNode）的主机名称：
```
yh20
yh30
yh40
yh50
```
在 dfs.hosts.exclude 文件中填写全部禁止存储数据节点（DataNode）的主机名称：
```
yh10
```
这样，这些节点DataNode启动之后会被禁止连接到NameNode，从而自动结束进程。

yarn.resourcemanager.nodes.include-path
yarn.resourcemanager.nodes.exclude-path

spark.memory.offHeap.enabled=true
spark.memory.offHeap.size=500m

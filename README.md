# 自定义AuthenticationHandler使用方法:  
#### 1.将xxx.jar放到lib目录，修改hadoop-env.sh  
添加export HADOOP_CLASSPATH="/opt/hadoop-3.0.0/lib/xxx.jar"  
#### 2.修改core-site.xml
```
    <!-- 配置HDFS网页登录认证 -->
    <property>
        <name>hadoop.http.filter.initializers</name>
        <value>org.apache.hadoop.security.AuthenticationFilterInitializer</value>
    </property>
    
    <!-- Digest认证 -->
    <property>
        <name>hadoop.http.authentication.type</name>
        <value>com.yq.hadoop.security.DigestAuthenticationHandler</value>
    </property>
    <!-- 加密可选MD5|SHA-1|SHA-256 -->
    <property>
        <name>hadoop.http.authentication.digest.algorithm</name>
        <value>MD5</value>
    </property>
    <property>
        <name>hadoop.http.authentication.digest.realm</name>
        <value>yq.com</value>
    </property>
    
    <!--
    <!-- Basic认证 -->
    <property>
        <name>hadoop.http.authentication.type</name>
        <value>com.yq.hadoop.security.BasicAuthenticationHandler</value>
    </property>
    -->
    
    <!--
    <!-- 可选配置，有默认值，单位：秒 -->
    <property>
        <name>hadoop.http.authentication.token.validity</name>
        <value>10</value>
    </property>
    <property>
        <name>hadoop.http.authentication.token.max-inactive-interval</name>
        <value>10</value>
    </property>
    -->

```


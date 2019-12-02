# 1.打包命令
    #打包项目
    mvn clean package -Pdeploy -Dmaven.test.skip=true
    
    #部署所有依赖包到maven私服
    mvn clean deploy -Pdeploy -Dmaven.test.skip=true
    
    #当使用此插件在父Maven项目时，运行如下命令将更新全部项目的版本号，包括子项目之间的依赖也都同步更新
    mvn versions:set -DnewVersion=1.0.0

    #当进入到子Maven项目时，运行如下命令将更新全部项目对项目引用的版本号
    mvn versions:set -DnewVersion=1.0.0

    #当更改版本号时有问题，可以通过以下命令进行版本号回滚
    mvn versions:revert

    #如果一切都没有问题，那就直接提交版本号
    mvn versions:commit

# 2.模块规划
    |-platform
        |-platform-common
            |-com.yyj.platform.common.util
            |-com.yyj.platform.common.log
        |-platform-es
        |-platform-hbase
        |-platform-kafka
        |-platform-spark

# 3.GIT版本规划
## 3.1.版本结构说明：A.B.C
    A：大功能变更；
    B：添加新特性；
    C：BUG修复；
## 3.2.样例
    |-master                           
    |-1.0.0.release                    
    |-develop                          
    |-feature/yangyijun(git用户名称)    
    |-hotfix                           

    # master
        最新稳定版本，只能由管理员进行合并最新release分枝到master分枝；
    # release
        发行稳定版本，每次发行版本需要合并到master分枝；
    # develop
        当前开发版本，各成员基于此分枝进行clone出各自的开发feature分枝，此分枝只允许合并操作，不允许直接在此分枝上进行开发；
    # feature
        各成员开发功能基于develop分枝创建各自的feature分枝，单元测试完成后合并到develop分枝；
    # hotfix    
        紧急bug修复分支，在最新的release分支上创建。
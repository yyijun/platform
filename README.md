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
        |-platform-common-base
            |-com.yyj.platform.common.base
        |-platform-common-core
            |-com.yyj.platform.common.core

# 3.GIT版本规划
## 3.1.版本结构说明：A.B.C
    A：大功能变更；
    B：添加新特性；
    C：BUG修复；
## 3.2.样例
    |-master                           
    |-1.0.0.release                    
    |-develop                          
    |-test                             
    |-feature/yangyijun(git用户名称)    
    |-hotfix                           
    |-fix                              

    # master
        最新稳定版本，只能由管理员进行合并最新release分枝到master分枝；
    # release
        发行稳定版本，每次发行版本需要合并到master分枝；
    # develop
        当前开发版本，各成员基于此分枝进行clone出各自的开发feature分枝，此分枝只允许合并操作，不允许直接在此分枝上进行开发；
    # test
        a.集成测试版本，集成测试通过后发布新的release分枝；
        b.如果测试出现bug，则基于此test分枝创建一个fix分枝，单元测试通过后合并回此test分枝打包进行集成测试；
          集成测试通过后发布release版本，并合并回develop分枝；各成员pull develop分枝代码，获取最新develop分枝代码；
    # feature
        各成员开发功能基于develop分枝创建各自的feature分枝，单元测试完成后合并到develop分枝；
    # hotfix    
        紧急bug修复分支，在最新的release分支上创建。修复并单元测试完成后，基于此hotfix分枝创建test分枝进行集成测试，集成测试完成后发布新的release分枝；
    # fix
        bug修复分支，在test分支上创建，单元测试完成后合并到test分枝进行集成测试；
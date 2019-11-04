# 1.项目打包命令
    #打包项目
    mvn clean package -Pdeploy -Dmaven.test.skip=true
    
    #部署所有依赖包到maven私服
    mvn clean deploy -Pdeploy -Dmaven.test.skip=true
    
    #当使用此插件在父Maven项目时，运行如下命令将更新全部项目的版本号，包括子项目之间的依赖也都同步更新：
    mvn versions:set -DnewVersion=1.0.0

    #当进入到子Maven项目时，运行如下命令将更新全部项目对项目引用的版本号：
    mvn versions:set -DnewVersion=1.0.0

    #当更改版本号时有问题，可以通过以下命令进行版本号回滚：
    mvn versions:revert

    #如果一切都没有问题，那就直接提交版本号：
    mvn versions:commit

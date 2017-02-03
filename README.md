MyBatisCodeHelper 
=================
[![GitHub release][release-img]][latest-release] [![Jetbrains Plugins][plugin-img]][plugin] [![Version](http://phpstorm.espend.de/badge/9445/version)][plugin]  
<!--[![Gitter][badge-gitter-img]][badge-gitter]-->
[![Downloads](http://phpstorm.espend.de/badge/9445/downloads)][plugin]
[![Downloads last month](http://phpstorm.espend.de/badge/9445/last-month)][plugin]
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
<div align="right">
<a href="README-EN.md">English Documentation</a>
</div>

Intellij下代码自动生成插件 支持生成mybatis的dao接口,mapper xml,和建表sql, 支持直接从接口方法名直接生成sql.
-----------------------------------------------------------------------
- 根据数据库对象一键生成 Dao接口，Service，Xml，数据库建表Sql文件  提供dao与xml的跳转
![generateFile](https://raw.githubusercontent.com/gejun123456/MyBatisCodeHelper/master/screenshot/generateFiles.gif)  


- 根据dao中的方法名生成对应的在xml并进行方法补全 
![find](https://raw.githubusercontent.com/gejun123456/MyBatisCodeHelper/master/screenshot/find.gif)
![update](https://raw.githubusercontent.com/gejun123456/MyBatisCodeHelper/master/screenshot/update.gif)
![delete](https://raw.githubusercontent.com/gejun123456/MyBatisCodeHelper/master/screenshot/delete.gif)
![count](https://raw.githubusercontent.com/gejun123456/MyBatisCodeHelper/master/screenshot/count.gif)
![all_1](https://raw.githubusercontent.com/gejun123456/MyBatisCodeHelper/master/screenshot/all_1.gif)

安装
----

支持下面产品编译号为141以上的产品。

- Android Studio
- IntelliJ IDEA
- IntelliJ IDEA Community Edition


**使用 IDE 内置插件系统:**
- <kbd>Preferences(Settings)</kbd> > <kbd>Plugins</kbd> > <kbd>Browse repositories...</kbd> > <kbd>搜索并找到"codehelper.generator"</kbd> > <kbd>Install Plugin</kbd>

**手动:**
- 下载[`最新发布的插件包`][latest-release] -> <kbd>Preferences(Settings)</kbd> > <kbd>Plugins</kbd> > <kbd>Install plugin from disk...</kbd>

重启**IDE**.

使用方法
--------------------------------------------------------------------------
- 在数据库对象上使用alt+insert （generate mybatis files）生成对应的dao xml文件等 （mac上使用 ctrl+N 即getter setter对应的快捷键)
- 当数据库对象添加字段后也可使用alt+insert （generate mybatis files）来生成更新后的xml。（只会更新默认的insert,insertList,update方法 其他自定义的方法不会变）
- 在mybatis的接口文件上的方法名上使用alt+enter generatedaoxml 生成对应的mybatis sql及方法的补全  


需要注意的点
-----------------------------------------------------------------------------

- 使用方法名生成sql 需要在接口中提供一个insert或save或add方法并以数据库对象为第一参数 (可以通过数据库对象自动生成) 
- 使用方法名生成的sql的字段会从数据库对象对应的resultMap中的数据库字段来设置。


方法名生成sql
-----------------------------------------------------------------------------------------
数据库对象User  

字段名  | 类型
-----   | ------
id      | Integer
userName | String
password | String  

表名为user  

xml中对应的resultMap为

	<resultMap id="AllCoumnMap" type="com.codehelper.domain.User">
	    <result column="id" property="id"/>
	    <result column="user_name" property="userName"/>
	    <result column="password" property="password"/>
	</resultMap>


以下是方法名与sql的对应关系(方法名的大小写无所谓)   


可以跟在字段后面的比较符有 

比较符  | 生成sql                  
------- | --------
between |  prop >={} and prop <={}
lessthan  | prop < {}
greaterthan | prop > {}
isnull | prop is null
notnull | prop is not null
like   | prop like {}
in     | prop in {}
notin  | prop not in {}
not    | prop != {}
notlike | prop not like {}

- find方法  

支持获取多字段，by后面可以设置多个字段的条件  
支持orderBy,distinct, findFirst

方法名       |  sql  
-----------  |  --------------
find         | select * from user
findUserName | select user_name from user
findById	| select * from user where id = {}
findByIdGreaterThanAndUserName | select * from user where id > {} and user_name = {}  
findByIdGreaterThanOrIdLessThan | select * from user where id > {} or id < {}
findByIdLessThanAndUserNameIn  | select * from user where id < {} and user_name in {}
findByUserNameAndPassword      | select * from user where user_name = {} and password = {}
findUserNameOrderByIdDesc   | select user_name from user order by id desc
findDistinctUserNameByIdBetween | select distinct(user_name) from user where id >= {} and id <={} 
findFirstByIdGreaterThan | select * from user where id > {} limit 1
findFirst20ByIdLessThan  | select * from user where id < {} limit 20  
findFirst10ByIdGreaterThanOrderByUserName  | select * from user where id > {} order by user_name limit 10

- update方法 by后面设置的条件同上  

方法名     | sql
---------- |  -------
updateUserNameById | update user set user_name = {} where id = {}
updateUserNameAndPasswordByIdIn  | update user set user_name = {} and password = {} where id in {}

- delete方法
by后面设置的条件同上  

方法名  |  sql
------- | ---------
deleteById | delete from user where id = {}
deleteByUserNameIsNull  | delete from user where user_name is null

- count方法
by后面设置的条件同上 支持distinct  

方法名  | sql
------- | ----------
count   | select count(1) from user
countDistinctUserNameByIdGreaterThan | select count(distinct(user_name)) from user where id > {}



CHANGELOG
------------------------------------------------
## latest

feature： 
- 添加mapper与dao的相互跳转
- 使用alt+insert来生成dao xml等
- 添加方法名生成sql
- 添加方法名自动提示

其他
----------------------------------
截图中的项目来自[https://github.com/gejun123456/codehelperPluginDemo](https://github.com/gejun123456/codehelperPluginDemo)  


[release-img]: https://img.shields.io/github/release/gejun123456/MyBatisCodeHelper.svg
[latest-release]: https://github.com/gejun123456/MyBatisCodeHelper/releases/latest
[badge-gitter-img]: https://img.shields.io/gitter/room/gejun123456/MyBatisCodeHelper.svg
[badge-gitter]: https://gitter.im/codehelper-generator/Lobby
[plugin-img]: https://img.shields.io/badge/plugin-9445-orange.svg
[plugin]: https://plugins.jetbrains.com/plugin/9445

simple-pool [![Build Status](https://travis-ci.org/PhantomThief/simple-pool.svg)](https://travis-ci.org/PhantomThief/simple-pool) [![Coverage Status](https://coveralls.io/repos/PhantomThief/simple-pool/badge.svg?branch=master&service=github)](https://coveralls.io/github/PhantomThief/simple-pool?branch=master)
=======================

A simple pool library for Java

* support concurrency use for objects. 
* jdk1.8 only

## Get Started

```xml
<dependency>
    <groupId>com.github.phantomthief</groupId>
    <artifactId>simple-pool</artifactId>
    <version>0.1.12</version>
</dependency>
```

```Java	
Pool<MyObject> pool = ConcurrencyAwarePool.<MyObject> builder() //
                .destroy(MyObject::close) //
                .maxSize(30) //
                .minIdle(1) //
                .evaluatePeriod(ofSeconds(2)) //
                .simpleThresholdStrategy(10, 0.8) //
                .build(MyObject::new);

MyResult myResult = pool.supply(myObject-> myObject.doSomething());
```
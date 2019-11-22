simple-pool
=======================
[![Build Status](https://travis-ci.org/PhantomThief/simple-pool.svg)](https://travis-ci.org/PhantomThief/simple-pool)
[![Coverage Status](https://coveralls.io/repos/PhantomThief/simple-pool/badge.svg?branch=master&service=github)](https://coveralls.io/github/PhantomThief/simple-pool?branch=master)
[![Total alerts](https://img.shields.io/lgtm/alerts/g/PhantomThief/simple-pool.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/PhantomThief/simple-pool/alerts/)
[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/PhantomThief/simple-pool.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/PhantomThief/simple-pool/context:java)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.phantomthief/simple-pool)](https://search.maven.org/artifact/com.github.phantomthief/simple-pool/)

A simple pool library for Java

* support concurrency use for objects. 
* jdk1.8 only

## Get Started

```Java	
Pool<MyObject> pool = ConcurrencyAwarePool.<MyObject> builder()
                .destroy(MyObject::close)
                .maxSize(30)
                .minIdle(1)
                .evaluatePeriod(ofSeconds(2))
                .simpleThresholdStrategy(10, 0.8)
                .build(MyObject::new);

MyResult myResult = pool.supply(myObject-> myObject.doSomething());
```
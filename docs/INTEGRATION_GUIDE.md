# Integration Guide for VBOX Core Shared Library

## Overview

This guide explains how to integrate the VBOX Core Shared Library into your microservice and implement custom idempotent consumers.

## Step 1: Add the Dependency

In your service's `pom.xml`, add:

```xml
<dependency>
    <groupId>com.volvo.vbox</groupId>
    <artifactId>core-shared-library</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

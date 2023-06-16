# Introduction

A simple Java webserver for testing jmx metric collection and instrumenting Java tracing library for Datadog APM

## Requirements

* JDK

## Setup

To run this program you first need to compile the Java program:

```
javac SimpleWebServer
```

## Usage

To start the Java webserver, use the run script:

```
./run.sh
```
To simulate load on the server, the request script:

```
./request.sh
```

## To Do

* Make containerized branch
* Improve docs
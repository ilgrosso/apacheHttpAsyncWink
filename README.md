apacheHttpAsyncWink
===================

Experimenting Apache Wink and Commons HttpAsyncClient

### How to test

```
$ git clone https://github.com/ilgrosso/apacheHttpAsyncWink.git
$ cd apacheHttpAsyncWink
$ mvn
```

This will execute the same HTTP GET request on Atom feed via:
 1. [Apache Commons HttpClient](http://hc.apache.org/httpcomponents-client-ga/) (already supported in Apache Wink 1.3.0)
 2. [Ning AsyncHttpClient](https://github.com/AsyncHttpClient/async-http-client) (already supported in Apache Wink 1.3.0)
 3. [Apache Commons AsyncHttpClient](http://hc.apache.org/httpcomponents-asyncclient-dev/) (experimental, here) - features explicit ```Future<T>``` management
 
Take a look at [main class](https://github.com/ilgrosso/apacheHttpAsyncWink/blob/master/src/main/java/net/tirasa/wink/App.java) for more information.

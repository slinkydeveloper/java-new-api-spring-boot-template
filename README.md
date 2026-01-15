# Hello world - Spring Boot example

Sample project configuration of a Restate service using the Java SDK and Spring Boot, including:

* [`pom.xml`](pom.xml)
* [`Greeter` service](src/main/java/com/example/restatestarter/Greeter.java)
* [`GreeeterTest` using sdk-fake-api](src/test/java/com/example/restatestarter/GreeterTest.java)

## Starting the service

To start the service, simply run:

```shell
$ mvn spring-boot:run
```

## API Reference

This project template uses the **Restate Java SDK 2.5+** with the new API. 
The API works exactly like the documented one at [docs.restate.dev](https://docs.restate.dev/develop/java/services), just with cleaner method names and without annotation processing.

#### Side Effects (wrap external calls)

```java
import dev.restate.sdk.Restate;

// Wrap any external call (API, database, etc.) so Restate can track it
String result = Restate.run("call-external-api", String.class, () -> {
    return myHttpClient.get("https://api.example.com/data");
});
```

#### Random & Time

```java
import dev.restate.sdk.Restate;

// Get a random UUID (deterministic across retries)
String id = Restate.random().nextUUID().toString();

// Sleep for 30 seconds
Restate.timer("my-timer", Duration.ofSeconds(30)).await();
```

#### Calling Other Services (from inside a handler)

```java
import dev.restate.sdk.Restate;

// Simple call - just call the method directly
String result = Restate.service(MyService.class).myHandler("input");

// Call a Virtual Object
String result = Restate.virtualObject(MyObject.class, "object-key").myHandler("input");

// One-way call (fire and forget)
Restate.serviceHandle(MyService.class).send(MyService::myHandler, "input");

// Delayed call
Restate.serviceHandle(MyService.class).send(MyService::myHandler, "input", Duration.ofMinutes(5));
```

#### External Client (calling from outside Restate)

```java
import dev.restate.sdk.client.Client;

// Connect to Restate
Client client = Client.connect("http://localhost:8080");

// Call a service
String result = client.service(MyService.class).myHandler("input");

// Call a Virtual Object
String result = client.virtualObject(MyObject.class, "object-key").myHandler("input");

// One-way call
client.serviceHandle(MyService.class).send(MyService::myHandler, "input");
```

### Full Documentation

For complete documentation, tutorials, and examples, visit: 

* [Java Quickstart guide](https://docs.restate.dev/get_started/quickstart?sdk=java)
* [2.5.0 release notes](https://github.com/restatedev/sdk-java/releases/tag/v2.5.0)
* [Java documentation](https://docs.restate.dev/develop/java/services)
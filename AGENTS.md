# Restate Java SDK Rules (SDK 2.5+ Experimental API)

## Core Concepts

* Restate provides durable execution: code automatically stores completed steps and resumes from where it left off on failures
* Handlers take typed inputs and return typed outputs using Java classes and Jackson serialization
* Use `Restate.*` static methods to access all Restate features (state, service calls, timers, awakeables, etc.)

## Service Types

### Basic Services

```java
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.RestateHttpServer;

@Service
public class MyService {
  @Handler
  public String myHandler(String greeting) {
    return greeting + "!";
  }

  public static void main(String[] args) {
    RestateHttpServer.listen(Endpoint.bind(new MyService()));
  }
}
```

### Virtual Objects (Stateful, Key-Addressable)

```java
import dev.restate.sdk.Restate;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Shared;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.RestateHttpServer;

@VirtualObject
public class MyObject {

  @Handler
  public String myHandler(String greeting) {
    String objectId = Restate.key();

    return greeting + " " + objectId + "!";
  }

  @Shared
  public String myConcurrentHandler(String input) {
    return "my-output";
  }

  public static void main(String[] args) {
    RestateHttpServer.listen(Endpoint.bind(new MyObject()));
  }
}
```

### Workflows

```java
import dev.restate.sdk.annotation.Shared;
import dev.restate.sdk.annotation.Workflow;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.RestateHttpServer;

@Workflow
public class MyWorkflow {

  @Workflow
  public String run(String input) {

    // implement workflow logic here

    return "success";
  }

  @Shared
  public String interactWithWorkflow(String input) {
    // implement interaction logic here
    return "my result";
  }

  public static void main(String[] args) {
    RestateHttpServer.listen(Endpoint.bind(new MyWorkflow()));
  }
}
```

## Context Operations

### State Management (Virtual Objects & Workflows only)

❌ Never use static variables - not durable, lost across replicas.
✅ Use `Restate.state().get()` and `Restate.state().set()` - durable and scoped to the object's key.

```java
import dev.restate.sdk.Restate;
import dev.restate.sdk.common.StateKey;

// Get state keys
Collection<String> keys = Restate.state().keys();

// Get state
StateKey<String> STRING_STATE_KEY = StateKey.of("my-key", String.class);
String stringState = Restate.state().get(STRING_STATE_KEY).orElse("my-default");

StateKey<Integer> INT_STATE_KEY = StateKey.of("count", Integer.class);
int count = Restate.state().get(INT_STATE_KEY).orElse(0);

// Set state
Restate.state().set(STRING_STATE_KEY, "my-new-value");
Restate.state().set(INT_STATE_KEY, count + 1);

// Clear state
Restate.state().clear(STRING_STATE_KEY);
Restate.state().clearAll();
```

### Service Communication

#### Request-Response (Simple Proxy)

```java
import dev.restate.sdk.Restate;

// Call a Service
String svcResponse = Restate.service(MyService.class).myHandler(request);

// Call a Virtual Object
String objResponse = Restate.virtualObject(MyObject.class, objectKey).myHandler(request);

// Call a Workflow
String wfResponse = Restate.workflow(MyWorkflow.class, workflowId).run(request);
```

The first `Class` argument can be either a Restate annotated interface or a concrete class.

#### Request-Response (Handle-based for async/composition)

Use `serviceHandle`/`virtualObjectHandle`/`workflowHandle` to execute call with method references.

```java
import dev.restate.sdk.Restate;

// Call a Service and get a handle
var handle = Restate.serviceHandle(MyService.class).call(MyService::myHandler, request);
String svcResponse = handle.await();

// Call a Virtual Object and get a handle
var objHandle = Restate.virtualObjectHandle(MyObject.class, objectKey).call(MyObject::myHandler, request);
String objResponse = objHandle.await();

// Call a Workflow and get a handle
var wfHandle = Restate.workflowHandle(MyWorkflow.class, workflowId).call(MyWorkflow::run, request);
String wfResponse = wfHandle.await();
```

The first `Class` argument can be either a Restate annotated interface or a concrete class.

#### One-Way Messages

Use `serviceHandle`/`virtualObjectHandle`/`workflowHandle` to execute call with method references.

```java
import dev.restate.sdk.Restate;

// Send to a Service
Restate.serviceHandle(MyService.class).send(MyService::myHandler, request);

// Send to a Virtual Object
Restate.virtualObjectHandle(MyObject.class, objectKey).send(MyObject::myHandler, request);

// Send to a Workflow
Restate.workflowHandle(MyWorkflow.class, workflowId).send(MyWorkflow::run, request);
```

The first `Class` argument can be either a Restate annotated interface or a concrete class.

#### Delayed Messages

```java
import dev.restate.sdk.Restate;
import java.time.Duration;

// Delayed send to a Service
Restate.serviceHandle(MyService.class).send(MyService::myHandler, request, Duration.ofDays(5));

// Delayed send to a Virtual Object
Restate.serviceHandle(MyObject.class, objectKey).send(MyObject::myHandler, request, Duration.ofDays(5));
```

#### With Idempotency Key

```java
import dev.restate.common.InvocationOptions;

var handle = Restate.serviceHandle(MyService.class).call(MyService::myHandler, request, InvocationOptions.idempotencyKey("my-key"));
String svcResponse = handle.await();
```

### Run Actions or Side Effects (Non-Deterministic Operations)

❌ Never call external APIs/DBs directly - will re-execute during replay, causing duplicates.
✅ Wrap in `Restate.run()` - Restate journals the result; runs only once.

```java
import dev.restate.sdk.Restate;

// Wrap with name for better tracing
String namedResult = Restate.run("my-side-effect", String.class, () -> callExternalAPI());
```

### Deterministic randoms and time

❌ Never use `Math.random()` - non-deterministic and breaks replay logic.
✅ Use `Restate.random()` or `Restate.random().nextUUID()` - Restate journals the result for deterministic replay.

❌ Never use `System.currentTimeMillis()`, `new Date()` - returns different values during replay.
✅ Wrap `System.currentTimeMillis()`, `new Date()` in `Restate.run` - Restate records and replays the same timestamp.

### Durable Timers and Sleep

❌ Never use `Thread.sleep()` or `CompletableFuture.delayedExecutor()` - not durable, lost on restarts.
✅ Use `Restate.timer()` - durable timer that survives failures.

```java
import dev.restate.sdk.Restate;
import java.time.Duration;

// Create timer and await it
Restate.timer("my-timer", Duration.ofSeconds(30)).await();
```

### Awakeables (External Events)

```java
import dev.restate.sdk.Restate;
import dev.restate.sdk.common.Awakeable;

// Create awakeable
Awakeable<String> awakeable = Restate.awakeable(String.class);
String awakeableId = awakeable.id();

// Send ID to external system
Restate.run("request-human-review", () -> requestHumanReview(name, awakeableId));

// Wait for result
String review = awakeable.await();

// Resolve from another handler
Restate.awakeableHandle(awakeableId).resolve(String.class, "Looks good!");

// Reject from another handler
Restate.awakeableHandle(awakeableId).reject("Cannot be reviewed");
```

### Durable Promises (Workflows only)

```java
import dev.restate.sdk.Restate;
import dev.restate.sdk.common.DurablePromiseKey;

DurablePromiseKey<String> REVIEW_PROMISE = DurablePromiseKey.of("review", String.class);
// Wait for promise
String review = Restate.promise(REVIEW_PROMISE).future().await();

// Resolve promise from another handler
Restate.promiseHandle(REVIEW_PROMISE).resolve(review);
```

## Concurrency

Always use Restate combinators (`DurableFuture.all`, `DurableFuture.any`) instead of Java's native `CompletableFuture` methods - they journal execution order for deterministic replay.

### `DurableFuture.all()` - Wait for All

Returns when all futures complete. Use to wait for multiple operations to finish.

```java
import dev.restate.sdk.Restate;
import dev.restate.sdk.common.DurableFuture;

// Wait for all to complete
DurableFuture<String> call1 = Restate.serviceHandle(MyService.class).call(MyService::myHandler, "request1");
DurableFuture<String> call2 = Restate.serviceHandle(MyService.class).call(MyService::myHandler, "request2");

DurableFuture.all(call1, call2).await();
```

### `DurableFuture.any()` - First Successful Result

Returns the first successful result, ignoring rejections until all fail.

```java
import dev.restate.sdk.Restate;
import dev.restate.sdk.common.DurableFuture;

// Wait for any to complete
DurableFuture<String> call1 = Restate.serviceHandle(MyService.class).call(MyService::myHandler, "request1");
DurableFuture<String> call2 = Restate.serviceHandle(MyService.class).call(MyService::myHandler, "request2");

int indexCompleted = DurableFuture.any(call1, call2).await();
```

### Invocation Management

```java
import dev.restate.sdk.Restate;

// Send with handle for later attachment or cancellation
var handle = Restate.serviceHandle(MyService.class)
        .send(MyService::myHandler, req, InvocationOptions.idempotencyKey("abc123"));

// Attach to the invocation and wait for response
var response = handle.attach().await();

// Cancel invocation
handle.cancel();
```

## Serialization

### Default (Jackson JSON)

By default, Java SDK uses Jackson for JSON serialization with POJOs.

```java
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TypeTag;
import dev.restate.sdk.common.TypeRef;
import java.util.Map;

// Primitive types
var myString = StateKey.of("myString", String.class);

// Generic types need TypeRef (similar to Jackson's TypeReference)
var myMap = StateKey.of("myMap", TypeTag.of(new TypeRef<Map<String, String>>() {}));
```

### Custom Serialization

```java
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.Slice;

class MyPersonSerde implements Serde<Person> {
  @Override
  public Slice serialize(Person person) {
    // convert value to a byte array, then wrap in a Slice
    return Slice.wrap(person.toBytes());
  }

  @Override
  public Person deserialize(Slice slice) {
    // convert value to Person
    return Person.fromBytes(slice.toByteArray());
  }
}
```

And then use it, for example, in combination with `Restate.run`:

```java
import dev.restate.sdk.Restate;

Restate.run("get-person", new MyPersonSerde(), () -> new Person());
```

## Error Handling

Restate retries failures indefinitely by default. For permanent business-logic failures (invalid input, declined payment), use TerminalException to stop retries immediately.

### Terminal Errors (No Retry)

```java
import dev.restate.sdk.common.TerminalException;

throw new TerminalException(500, "Something went wrong");
```

### Retryable Errors

```java
// Any other thrown exception will be retried
throw new RuntimeException("Temporary failure - will retry");
```

## Testing

Use `FakeRestate.execute()` to test handlers without spinning up a full Restate environment:

```java
import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.sdk.fake.FakeRestate;
import org.junit.jupiter.api.Test;

class MyServiceTest {

  @Test
  void testMyHandler() {
    MyService service = new MyService();

    var response = FakeRestate.execute(
        () -> service.myHandler("Hi")
    );

    assertThat(response).isEqualTo("Hi!");
  }
}
```

## SDK Clients (External Invocations)

For invoking Restate services from external applications (outside of Restate handlers), use the same API on the client instance:

```java
import dev.restate.sdk.client.Client;
import java.time.Duration;

Client restateClient = Client.connect("http://localhost:8080");

// Request-response (Simple Proxy)
String result = restateClient.service(MyService.class).myHandler("Hi");

// Call a Virtual Object
String objResponse = restateClient.virtualObject(MyObject.class, "Mary").myHandler("Hi");

// Request-response (Handle-based for async/composition)
var handle = restateClient.serviceHandle(MyService.class).callAsync(MyService::myHandler, "Hi");
String result2 = handle.get();

// One-way message
restateClient.serviceHandle(MyService.class).send(MyService::myHandler, "Hi");

// Delayed message
restateClient.serviceHandle(MyService.class).send(MyService::myHandler, "Hi", Duration.ofSeconds(1));

// With idempotency key
String result = restateClient.serviceHandle(MyService.class)
    .call(MyService::myHandler, "Hi", InvocationOptions.idempotencyKey("abc"));
```

---

> To find navigation and other pages in this documentation, fetch the llms.txt file at: https://docs.restate.dev/llms.txt

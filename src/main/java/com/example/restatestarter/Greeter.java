package com.example.restatestarter;

import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.springboot.RestateService;

@RestateService
public class Greeter {

  public record Greeting(String name) {}
  public record GreetingResponse(String message) {}

  @Handler
  public GreetingResponse greet(Greeting req) {
    return new GreetingResponse("You said hi to " + req.name + "!");
  }
}



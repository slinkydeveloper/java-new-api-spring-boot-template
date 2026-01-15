// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package com.example.restatestarter;

import dev.restate.sdk.fake.FakeRestate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Greeter.class)
public class GreeterTest {

  @Autowired private Greeter greeter;

  @Test
  void greet() {
    var response = FakeRestate.execute(
            () -> greeter.greet(new Greeter.Greeting("Francesco"))
    );
    assertThat(response.message()).isEqualTo("You said hi to Francesco!");
  }
}

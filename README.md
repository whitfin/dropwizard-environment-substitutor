# dropwizard-environment-substitutor

This module provides a basic way to allow overrides of all configuration at startup via environment variables. It's useful if you ship to multiple environments using things like Docker and might need to change on a whim.

Although Dropwizard has built-in support for environment substitution, you would have to flag every field with the corresponding environment value, and a default when appropriate. This bundle treats the base configuration as a default, and allows you to override any property as needed.

### Installation

This library is available on Central, and so can be used via Maven and/or Gradle pretty easily. Just add the dependency as normal (you might have to check for the latest version, rather than what's shown below):

```xml
<dependency>
    <groupId>io.whitfin</groupId>
    <artifactId>dropwizard-environment-substitutor</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Usage

Simply attach the bundle to your Dropwizard application in the bootstrap phase:

```java
import io.dropwizard.Application;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class MyApplication extends Application<MyConfiguration> {

    public static void main(String[] args) throws Exception {
        new MyApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<MyConfiguration> bootstrap) {
        // setup the application configuration whilst allowing for environment overrides
        ConfigurationSourceProvider provider = bootstrap.getConfigurationSourceProvider();
        bootstrap.setConfigurationSourceProvider(new EnvironmentSubstitutor("MY_APP", provider));
    }

    @Override
    public void run(MyConfiguration configuration, Environment environment) throws Exception { }
}
```

This dictates that any property in the environment starting with "MY_APP" will be eligible for override. Consider this configuration:


```yml
config:
    object:
        key: "value"
```

If you read this in, `my_config.my_object.key == "value"`; however you can set the environment variable `MY_APP_CONFIG_OBJECT_KEY` to change it. For example `MY_APP_CONFIG_OBJECT_KEY=test` would make the value equal to `"test"`. This pattern is followed throughout; upper case the field in the configuration file and swap out any `.` or `-` for `_`, and you use a simple integer to array position.

For any other functionality, please see the documentation or the code itself.

---
layout: post
title: Fully encapsulated
---


Let's speak about common domain objects and how they
are usually implemented. The tendency here is to define a
bunch of accessors to share object's state and returning `null`s
as an indicator of empty value. In this post I will try to refactor one of
this objects to fully encapsulated one!


I have in mind those kinds of objects that has some optional values.
It may be a person's name for instance. I took this one only for simplicity.
In almost every project a name is defined as an entity that can include
a first part and a last part, sometimes it's only one part sometimes there are both.
If we <a href="https://www.google.ru/#newwindow=1&q=site:github.com+%22Name.java%22+first+last" target="_blank">google</a>
exising implementations, we will find that
the majority of them has two getters `getFirstName()` and `getLastName()`

```java
interface Name {
  
  String getFirstName();

  String getLastName();
}
```

What's happened here? This object just provides two accessors to his internal state that
violates encapsulation and allows you to look inside this torn object and take one part of him outside.

Now try to refactor him and go the vole.
Some time ago I was imbued by
<a href="http://www.yegor256.com/2016/04/05/printers-instead-of-getters.html" target="_blank">printers instead of getters</a>
idea
but hesitated to try in use for a while. Let's do something similar now.<br/>
I'm calling this "source and output" pattern. To implement it you need to write your object as a *source*
with single generic method `print`
and add *output* interface as a protocol for all possible source states:
```java
interface Source {

  <T> T print(Source.Out<T> out);

  interface Out<T> {

    T print(String foo);

    T print(String foo, Integer bar);
  }
}
```

Now back to our `Name`:
```java
interface Name {
  
  <T> T print(Name.Out<T> out);

  interface Out<T> {

    T printFirst(String first);

    T printFull(String first, String last);

    T printLast(String last);
  }
}
```
We don't care about name state anymore. It's not possible to access it directly, you can
only request a name to print itself into any output of your choice.

Now we are able to implement all kinds of names and call them properly:

```java
class FirstName implements Name {

    private final String name;

    FirstName(String name) {
        this.name = name;
    }

    @Override
    public <T> T print(Out<T> out) {
        return out.printFirst(name);
    }
}

class FullName implements Name {

    private final String first;
    private final String last;

    FullName(String first, String last) {
        this.first = first;
        this.last = last;
    }

    @Override
    public <T> T print(Out<T> out) {
        return out.printFull(first, last);
    }
}
```

And all outputs for every need:

```java
class FormattedOut implements Name.Out<String> {

  @Override
  String printFirst(String first) {
    return first;
  }

  @Override
  String printFull(String first, String last) {
    return String.format("%s %s", first, last);
  }
  
  @Override
  String printLast(String last) {
    return last;
  }
}

class JsonOut implements Name.Out<JSONObject> {

    @Override
    public JSONObject printFirst(String first) {
        final JSONObject json = new JSONObject();
        json.put("first", first);
        return json;
    }

    @Override
    public JSONObject printFull(String first, String last) {
        final JSONObject json = new JSONObject();
        json.put("first", first);
        json.put("last", last);
        return json;
    }

    @Override
    public JSONObject printLast(String last) {
        final JSONObject json = new JSONObject();
        json.put("last", last);
        return json;
    }
}
```

We just split object creation and all possible object formats here,
as a result we can easily make unit tests for more complex name objects (e.g. `SqliteName` or `JsonName`)
and for complex outputs (e.g. `XmlOut` or `BundleOut`) **separately**.<br/>
Also this printers are able to connect to any source that you want! You can combine them in many variants
and use to convert object from one type to another:
```java
Xml xml = new JsonName(json).print(new XmlOut());
String formattedName = new SqliteName(database).print(new FormattedOut("%s %s"));
```

About unit testing by the way. You can face small problems here with traditional asserts-testing.
We just can't check that internal state equals to some value. But it's not so bad. If you are using
<a href="http://hamcrest.org/JavaHamcrest/" target="_blank">Hamcrest</a>
library you can make easy-to-write matchers for this object.
It may be `MatcherName.HasFirst`, `MatcherName.HasLast` etc.:
```java
@Test
public void firstNameTest() {
  MatcherAssert.assertThat(
    "Can't read first name",
    new JsonName("{\"first\": \"Jimmy\"}),
    new MatcherName.HasFirst("Jimmy")
  );
}
```

As for me an **ability** to write unit test for every part of code is very important,
usually it says about good design implicitly.

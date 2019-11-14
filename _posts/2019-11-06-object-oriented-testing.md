---
layout: post
title: Testing Object Oriented Code
---

Any self-respecting programmer must have a blog post about unit testing.
There're many approaches to write unit tests, but I'll focus on
writing tests for [EO](https://www.elegantobjects.org/) code, where
objects are immutable, sealed and behavior-based. These restrictions
makes unit-testing much easier than testing procedural code with DTOs,
getters and mutable states. The only thing that an object oriented test
should verify is the correct behavior of an object with the provided
testing state  (fake state).
However, the procedural test (I mean the test for procedural code)
should verify the data of class instance after some manipulations
with injected mock objects for simulating behavior.

## Key concepts

There are always three players in the unit-test:
 - Target - an object which should be tested
 - Matcher - an object which test the Target
 and can say what's wrong with target if test failed
 - Assertion - a statement which applies the matcher to the target
 and report the result

**Target** should be an immutable object with a state and behavior.
The unit test may inject the fake state, because it should
verify only one unit (target). If test uses a composition of
objects, it can be called an integration test.<br/>
*Example:*
```java
class Book {
  private final List<Page> pages;

  public Text content(int page) {
    return this.pages.get(page).show();
  }
}
```

**Matcher** contains expected result as a state
and it accepts the target to verify it. Also, the matcher
should be able to explain what's wrong with the target.</br>
*Example:*
```java
class BookHasPage implements Matcher<Book> {
  private final Text expected;
  private final int page;

  public bool match(Book book) {
    return !Objects.equals(this.expected, book.page(this.page));
  }

  public String explain(Book book) {
      return String.format(
        "expected the page %d of book to be %s, but was %s",
        this.page, this.expected, book.page(this.page)
      );
  }
}
```

These concepts are implemented quite fine in
[Hamcrest](http://hamcrest.org/JavaHamcrest/) library.

## Frameworks

With [JUnit](https://junit.org/) tests, programmers are forced to use test methods to
apply assertions:
```java
class TestCase {
  @Test
  public void bookHasPage() {
    MatcherAssert.assertThat(
      new Book(new FakePage("some test")),
      new BookHasPage("some test", 1)
    );
  }
}
```

So the whole logic of the test is to verify the Target with Matcher
using assertion.
Valid EO test with JUnit is a 
[single statement of assertion](https://www.yegor256.com/2017/05/17/single-statement-unit-tests.html).

But threre are a few issues with test methods which I see:
 - you can't control execution flow programmatically - you need to use some
 magic flags in `pom.xml`, but it's black magic)
 - you don't know how, when and why your test will be called.
 It's like a "Spring" of unit testing.
 The framework finds classes dynamically via reflection, parses annotations and decides how
 to call your test methods
 - test case is not an object, but a bunch of procedures. You can't control
 test case instantiation:
 you can't inject anything via constructor, you can't use composition, etc.
 - there is no single entry point (like `main()` method for Java apps),
 you need to rely on names of test classes.

## Single object unit test

With that in mind we can rethink all unit testing from test methods to
a test object, where the target and the matcher will be the state of a test-case object:

```java
class SimpleTestCase<T> implements TestCase {
  private final String name;
  private final Supplier<T> target;
  private final Matcher<T> matcher;

  @Override
  void run(Report report) {
    T val = target.get();
    if (matcher.match(val)) {
      report.success(name);
    } else {
      report.failure(name, matcher.explain(val));
    }
  }
}
```

I saw a similar [idea](https://www.pragmaticobjects.com/chapters/003_reusable_assertions.html)
by [@skapral](https://github.com/skapral), but it solves only half of issues.
There're no test methods anymore, but we stil need to rely on framework's black magic
and create test classes for it in a hope that JUnit will find it and run as expected.

What I want to see in my test cases is a single entry point and
composition of test cases with decorators. Something like this:
```java
class MainTest extends TestCase.Wrap {
  public MainTest() {
    super(
      new SequentialTests(
        new ParallelTests(
          new FooTest(),
          new ParTest(),
          new VerboseTest(
            new BazTest()
          )
        ),
        new TestIf(
          () -> System.getProperty("it-tests-enabled") == true
          new IntegrationTests()
        )
      )
    );
  }

  // it's like a `public static void main()`
  public static void test() {
    new MainTest().run(new XmlReport());
  }
}
```

Using composition I'm getting the full control of testing flow:
 - I can run some tests in parallel mode, some sequentially
 - I can control tests execution order if needed
 - I can use conditions right in composition structure
 - I can change reporting behavior
 - I can do anything with my unit tests, because the test framework is
 extensible now

This kind of frameworks doesn't work as a black-box, but provides API
to help me to construct tests for the project by myself.<br/>
I created an experimental project [g4s8/oot](https://github.com/g4s8/oot)
for that framework, it should replace JUnit sooner or later. You can
express your opinion in the comments to this blog post or by
[submitting a ticket](https://github.com/g4s8/oot/issues/new) for that repo.

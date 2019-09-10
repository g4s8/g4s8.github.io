---
layout: post
title: Instead of DTO
---

Objects are using data messages to communicate with each other,
it means that object methods can accept some data, but data structure
sometimes is too complex. When complex data message is designed wrongly
it tends to low maintainability because it becomes harder to test this code,
and harder to read and understand it.
Many people are using
<a href="https://en.wikipedia.org/wiki/Data_transfer_object" target="_blank">DTOs</a>
for object messages, just because it's easier to
implement, but this code will be less readable in future and has a lot of
hidden drawbacks, e.g.
<a href="https://www.yegor256.com/2016/07/06/data-transfer-object.html" target="_blank">
broken encapsulation</a>.
Instead of this data languages should be used for complex data structures,
it moves data definitions from source code and lets the code to encapsulate
the data and concentrate on object's behavior.

## The problem

One of the examples of complex data format is when you have a service with multiple implementation
which should receive some data to process. Let's take email message with service to send as example
for this post. In this example email message can be send with two different ways: via SMTP service
or via external API.
The wrong way (but quite popular) is to present email as a DTO class and accept it
in message sender interface:
```java
class MailDTO {
  String subject;
  String body;
  String address;
  Iterable<String> cc;
  Iterable<byte[]> attachments;
  String signature;
}

interface MailService {
  void send(MailDTO mail);
}
```
Usually people are using getters and setter for DTO instead of public fields
but I don't see any difference comparing to "all-public-fields" DTO.
It's not actually an object, but data-holder, this class breaks encapsulation
and not testable. It makes it harder to write unit tests for `MailService` implementation,
because you don't actually know what DTO fields will be used internally and you always need
to construct working copy of this class for each unit test. In
<a href="https://github.com/rubenlagus/TelegramBots/blob/master/telegrambots-meta/src/main/java/org/telegram/telegrambots/meta/api/objects/Update.java" target="_blank">worst case scenario</a>
this DTO comes from external library, it has private fields with public getters and it's constructed 
internally using <a href="https://stackoverflow.com/q/37628/1723695" target="_blank">reflection</a>
so it's not possible to test DTO receivers without some dirt in tests like
<a href="https://site.mockito.org/" target="_blank">Mockito</a> or reflection. It's really hard to
maintain such tests, just look at this code to understand why:
```java
@Test
void sendsMailViaSmtp() {
  MailDTO mail = Mockito.mock(MailDTO.class);
  Mockito.when(mail.getAddress()).thenReturn("qwe@asd.com");
  Mockito.when(mail.getBody()).thenReturn("hello");
  Mockito.when(mail.getCcList()).thenReturn(ccList);
  Mockito.when(mail.getAttachments()).thenReturn(attachments);
  Mockito.when(mail.getSignature()).thenReturn("test");
  new SmtpService(...).send(mail);
}
```
Instead of focusing toward testing the logic, programmer have to read or write dosen of
mocking lines. You need to
<a href="https://nedbatchelder.com/blog/201206/tldw_stop_mocking_start_testing.html" target="_blank">
stop mocking</a> if you want to make your tests clearer.

Another huge problem of this DTO is 
<a href="https://www.yegor256.com/2019/03/12/data-and-maintainability.html" target="_blank">broken encapsulation</a>:
it's pretty fine for procedural code to use DTO, since this paradigm requires data to be open, but this post is
about OOP, where encapsulation is one of the
<a href="https://en.wikipedia.org/wiki/Object-oriented_programming#Encapsulation" target="_blank">core principles</a>.


## Solution

The correct way for this example will be to hide the data by encapsulation it as object's state, revert
communication direction (from "service is sending mail" to "mail sends itself via service"), and use data languages to
communicate with mail services:

```java
interface Mail {
  void send(MailService srv);
}

interface MailService {
  void accept(XML message);
}
```

I'm suggesting to build custom data protocols, when complex data structures should be passed between objects.
In this example I'll use
<a href="https://en.wikipedia.org/wiki/XML" target="_blank">XML</a>
to pass data from mail to service,
it has some advantages over previos solutions:
 - validation - we can enforce the protocol with `xsd` schemas and fail method `accept()` if xml is invalid
 - queries - `MailService` can use xpath queries to access the data
 - readability - XML has readable format, so it's easier to view XML file instead of using debugger to inspect DTO instances
 - allows you to build complex data structures - XML is a flexible language to define complex data structures
 - transformations - XML data structure can be transformed using `xsl` transformations
 - flexability - mail object can construct data message from internal state by configured logic in `send` method or decorate
 actor (`Mail`) or receiver `MailService` by decorators to put additional data.

The disatvantages are:
 - complexity - it's too complex for simple data structures
 - knowledge - it requires additional knowledge in XML language to design such structures


It's better to start with `xsd` schema to define data structure, but
it'll be over-complex for simple blog post, so I skip it here.
If you are not famialiar with `xsd` schemas you may start learning it
here: [www.w3schools.com](https://www.w3schools.com/xml/schema_intro.asp)

To pass it to `MailService` we can use `XML` object from
<a href="https://xml.jcabi.com/" target="_blank">jcabi-xml</a>:
```java
void accept(XML data);
```
So now, mail services can use xpath queries to query the data:
```java
class MailSmtp implements MailService {

    @Override
    public void accept(final XML mail)
        throws IOException {
        final String address = mail.xpath("/mail/recipient/text()").get(0);
        final String subject = mail.xpath("/mail/subject/text()").get(0);
        final List<String> ccs = mail.xpath("/mail/ccs/cc");
        // TODO: send via SMTP
    }
}
```

On the other side, `Mail` implementations can use
<a href="https://github.com/yegor256/xembly" target="_blank">Xembly</a>
language to build `XML` object message using directives
(pay attention: this class doesn't expose internals and doesn't break encapsulation, it's rather
constructing a message to another object using internal state):
```java
class MailSimple implements Mail {

  private final String subj;
  private final String text;
  private final String address;

  public void post(final MailService svc) {
    svc.accept(
      new AsXml(
        new Directives()
          .add("mail")
          .add("subject").set(this.subj).up()
          .add("address").set(this.address).up()
          .add("body")
          .add("text").set(this.text).up()
      )
    );
  }
}
```

The idea is simple - hide the data in `Mail` object, build XML in `Mail` implementations
from encapsulated state and pass it to `accept()` of `MailService`:
```java
new MailSimple(
  "Test mail",
  "Hello",
  "test@test.com"
).post(new Smpt(connection));
```

One of the advantages is flexability:
these classes are easy to wrap, e.g. here is a decorator to add CCs to
origin mail:
```java
class MailWithCC implements Mail {

  private final Mail origin;
  private final Iterable<String> ccs;

  @Override
  public void post(final MailService svc) {
    this.origin.post(
      mail -> svc.accept(
        new AsXml(
          new Directives(Directives.copyOf(mail.node()))
            .xpath("/mail")
            .addIf("ccs")
            .append(
              new IoCheckedScalar<>(
                new Reduced<>(
                  new Directives(),
                  (dirs, cc) -> dirs.add("cc").set(cc).up(),
                  this.ccs
                )
              ).value()
            )
        )
      )
    );
  }
}
```

This class will add CC-list to existing mail by updating XML data:
```java
new MailWithCC(
  new MailSimple(
    "Test mail",
    "Hello",
    "test@test.com"
  ),
  "copy@test.com"
).post(new Smpt(connection));
```
Or service itself can be decorated in similar way:
```java
class WithCc implements MailService {

  private final MailService origin;

  void accept(XML message) {
    origin.accept(
      new AsXml(
        new Directives(Directives.copyOf(mail.node()))
          .xpath("/mail")
          .addIf("ccs")
          .append(
            new IoCheckedScalar<>(
              new Reduced<>(
                new Directives(),
                (dirs, cc) -> dirs.add("cc").set(cc).up(),
                this.ccs
              )
            ).value()
          )
      )
    )
  }
}
```

Another important advantage of this approach is that it's easy to unit-test
this classes:
```java
@Test
void appendsCcs() throws Exception {
  new MailWithCC(
    new Mail.FAKE,
    new ListOf<>("copy@test.com")
  ).post(
    mail -> MatcherAssert.assertThat(
      mail.node(),
      XhtmlMatchers.hasXPaths("/mail/ccs/cc[./text() = 'copy@test.com']")
    )
  );
}
```

When you change your data format (and update `xsd` schema), you may write
`xsl` transformation to update old version to new on "data side", so you just change
the code to support only new format and applying transformations to convert old data.


## Conclusion

To summarize it all - we're spending more time on implementation, since we need
to write all these schemas, xml manipulators, etc, but keeping much more time
on maintaining the code and making it more readable. But you always should think
about the balance between the cost of implementing and cost of maintaining:
I'd never use XML language for simple data messages, e.g. if by business requirements
mail can contain onle message and address, nothing more; it would be easier to
put these properties right in the method, since creating XML definitions for that case will
be too expensive for project.

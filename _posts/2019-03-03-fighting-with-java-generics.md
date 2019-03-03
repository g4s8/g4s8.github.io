---
layout: post
title: Fighting with Java - generics
---
Java generic types are not [true types](https://stackoverflow.com/a/2721557) actually,
so it's not possible to have few constructors to accept same type with different
generic parameters, because it will have same signature.
For example if you need to accept `Iterable<String>` and `Iterable<Text>` in constructor,
you can't just add two constructors for these types, because it won't even compile.
In this post I'll show you how I usually deal with this situation.

## The problem

Let's start with example:
```java
/**
 * Join all input strings using ',' char to join.
 */
class JoinedString {
    private final Text txt;

    public JoinedString(Iterable<String> src) {
        this(new JoinedText(",", src));
    }

    public JoinedString(Iterable<Text> src) {
        this(new JoinedText(new TextOf(","), src))
    }

    private JoinedText(Text txt) {
        this.txt = txt;
    }

    @Override
    public String toString() {
        return this.txt.asString();
    }
}
```

We want to be able to use this class for both `String`s and `Text`s,
but we can not, because for Java compiler
both constructors has same signature: `JoinedText(Iterable)` without
generic param.<br/>
To solve it we can introduce static factory methods which will act
as secondary constructors or mark this class as `abstract` and
add two nested classes to accept these parameters.

## Static factory method

The easy way (easy for developer).<br/>
In my opinion, it's quite acceptable to use static methods in such case.
These static methods are actually secondary constructor (named secondary constructor).
But we should implement it carefully, if we're going to use factory methods here,
we should follow all rules which are applicable
to secondary constructors, they are:
 1. Secondary constructor must not have
[any code](https://www.yegor256.com/2015/05/07/ctors-must-be-code-free.html)
 2. The only thing that secondary can do,
[is to call primary constructor](https://www.yegor256.com/2015/05/28/one-primary-constructor.html)

So the code above will be transformed into this:

```java
/**
 * Join all input strings using ',' char to join.
 */
class JoinedString {
    private final Text txt;

    /**
     * Ctor.
     * See also factory methods: {@link #fromStrings} and
     * {@link #fromTexts}.
     */
    public JoinedText(Text txt) {
        this.txt = txt;
    }

    @Override
    public String toString() {
        return this.txt.asString();
    }

    /**
     * Make from strings.
     */
    public static JoinedString fromStrings(
        Iterable<String> src
    ) {
        return new JoinedString(
            new JoinedText(",", src)
        );
    }

    /**
     * Make from texts.
     */
    public static JoinedString fromTexts(
        Iterable<Text> src
    ) {
        return new JoinedString(
            new JoinedText(
                new TextOf(","), src
            )
        );
    }
}
```

This code doesn't have all
[common issues](https://www.yegor256.com/2017/11/14/static-factory-methods.html)
from static methods,
because it follows strict rules.

The only issue it has is nonobviousness - the code user most probably
expects appropriate constructor, not static factory method, but I think it's not
a big issue, it can be solved by writing additional javadoc line about it.

## Abstract class

This problem can be solved in different way.
It requires to write a little bit more code but
this solution looks more clear (to me).
The idea here is to mark class as `abstract`
and keep only primary constructor in the class. Then
add nested classes with implementation for each 
secondary constructor.<br/>
Let's see the code:
```java
abstract class JoinedString {
    
    private Text txt;

    // important: constructor is private
    private JoinedString(Text txt) {
        this.txt = txt;
    }

    @Override
    public final toString() { // important: final
        return this.txt.asString();
    }

    public static final FromStrings extends JoinedString {
        public FromStrings(Iterable<String> src) {
            super(new TextOf(",", src));
        }
    }

    public static final FromText extends JoinedString {
        public FromText(Iterable<Text> src) {
            super(new TextOf(new TextOf(",", src)));
        }
    }
}
```

Here we have logic in base class with private primary constructor,
and subclasses with secondary constructors only. Because primary constructor
is private, nobody will be able to extend this class from outside,
all subtypes must by nested classes. Also noone can override the logic
method because it's final. <br/>
The issue of this class is it's not possible to call primary constructor directly,
you'll need additional nested implementation like
```java
public static final FromText extends JoinedText {
    public FromText(final Text text) {
        super(text);
    }
}
```
which don't do anything useful, just call base constructor.
But anyway, this implementation looks better for me.

## Conclusion

Let's compare these solutions:
```java
// using factory methods
JoinedText.fromTexts(txt);

// using nested classes
new JoinedText.FromTexts(txt);
```
As for me, the second one is better, but it's harder to write
(requires more code), the first one is also not so bad and can be used sometimes.
What do you think about it? If you find more elegant way to fight with
Java, please post a comment.

We don't have the absolutely elegant way to do some things in Java, like implementing
secondary constructors with generic parameters of same type, but if we're using 'not-so-elegant'
methods (like static methods or class inheritance) intelligently and carefully,
our code will be clear and maintainable.


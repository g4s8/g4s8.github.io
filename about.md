---
layout: page
title: About
permalink: /about/
---

Hi, my name is Kirill and I'm an Android developer.<br/>
I'm working with Android since 2013 .<br/>
Also I have some experience in web (back-end) services making
and in iOS development.<br/>

All the while I had some reservations about the way
of how applications were designed and developed
and now I'm beginning to realize how it should be.
This books really impressed me and helped with understanding:
 - [Object Thinking by David West](https://www.amazon.com/Object-Thinking-Developer-Reference-David/dp/0735619654)
 - [Elegant Objects by Yegor Bugayenko](https://www.amazon.com/Elegant-Objects-1-Yegor-Bugayenko/dp/1519166915)
 - [Pattern Language by Christopher Alexander](https://www.amazon.com/Pattern-Language-Buildings-Construction-Environmental/dp/0195019199)

I've started to write this blog because I'm fed up with
current Android framework code and with common used popular libraries.
At the moment the major part of Android SDK looks like a procedural code
combined into classes with a data-structures presented as objects.
Furthermore it force us to write main application components:
[Activities](https://developer.android.com/guide/components/activities/activity-lifecycle.html),
[Fragments](https://developer.android.com/guide/components/fragments.html),
[Services](https://developer.android.com/guide/components/services.html),
etc as a bunch of [lifecycle-callbacks](https://github.com/xxv/android-lifecycle)
inherited from framework base classes:
they does not describe object behaviour rather they exists only
to be triggered on specific event by the system.
We need to use doubtful in OOP practices like
separated construction and initialization,
[class casting](http://www.yegor256.com/2015/04/02/class-casting-is-anti-pattern.html),
[virtual methods](http://www.javaworld.com/article/2073649/core-java/why-extends-is-evil.html)
to fit framework requirements.

It's really hard to apply object oriented principles here
but nothing is impossible.
I'm going to find OO solutions to common Android problems
and post them in this blog!

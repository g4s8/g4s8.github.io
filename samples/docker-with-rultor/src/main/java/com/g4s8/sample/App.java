/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Kirill (g4s8.public@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to read
 * the Software only. Permissions is hereby NOT GRANTED to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.g4s8.sample;

import java.io.IOException;
import java.util.Arrays;
import org.takes.http.Exit;
import org.takes.http.FtCli;
import org.takes.tk.TkText;

/**
 * Application.
 *
 * @since 0.1
 */
public final class App {
    /**
     * Arguments.
     */
    private final Iterable<String> args;

    /**
     * Ctor.
     * @param arguments Command line arguments
     */
    public App(final Iterable<String> arguments) {
        this.args = arguments;
    }

    /**
     * Start the service.
     * @throws IOException If fails
     */
    public void start() throws IOException {
        new FtCli(new TkText("OK"), this.args).start(Exit.NEVER);
    }

    /**
     * Entry point.
     * @param args Command line args
     * @throws Exception On error
     */
    public static void main(final String... args) throws Exception {
        new App(Arrays.asList(args)).start();
    }
}

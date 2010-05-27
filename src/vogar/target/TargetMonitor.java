/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vogar.target;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;
import vogar.Result;

/**
 * Accepts a connection for a host process to monitor this action.
 */
class TargetMonitor {

    private static final int ACCEPT_TIMEOUT_MILLIS = 10 * 1000;
    private static final Pattern XML_INVALID_CHARS
            = Pattern.compile("[^\\x09\\x0A\\x0D\\x20-\\xD7FF\\xE000-\\xFFFD]+");

    private static final String ns = null; // no namespaces
    ServerSocket serverSocket;
    private Socket socket;
    private XmlSerializer serializer;

    public void await(int port) {
        if (socket != null) {
            throw new IllegalStateException();
        }

        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(ACCEPT_TIMEOUT_MILLIS);
            serverSocket.setReuseAddress(true);
            socket = serverSocket.accept();

            serializer = XmlPullParserFactory.newInstance().newSerializer();
            serializer.setOutput(socket.getOutputStream(), "UTF-8");
            serializer.startDocument("UTF-8", null);
            serializer.startTag(ns, "vogar-monitor");
        } catch (IOException e) {
            throw new RuntimeException("Failed to accept a monitor on localhost:" + port, e);
        } catch (XmlPullParserException e) {
            throw new RuntimeException(e);
        }
    }

    public void outcomeStarted(String outcomeName, String actionName) {
        try {
            synchronized (serializer) {
                serializer.startTag(ns, "outcome");
                serializer.attribute(ns, "name", outcomeName);
                serializer.attribute(ns, "action", actionName);
                serializer.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void output(String text) {
        try {
            synchronized (serializer) {
                serializer.text(sanitize(text));
                serializer.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Replaces XML-invalid characters with the corresponding U+XXXX code point escapes.
     */
    private static String sanitize(String text) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = XML_INVALID_CHARS.matcher(text);
        while (matcher.find()) {
            matcher.appendReplacement(result, "");
            result.append(escapeCodePoint(matcher.group()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String escapeCodePoint(CharSequence cs) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < cs.length(); ++i) {
            result.append(String.format("U+%04X", (int) cs.charAt(i)));
        }
        return result.toString();
    }

    public void outcomeFinished(Result result) {
        try {
            synchronized (serializer) {
                serializer.startTag(ns, "result");
                serializer.attribute(ns, "value", result.name());
                serializer.endTag(ns, "result");
                serializer.endTag(ns, "outcome");
                serializer.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            synchronized (serializer) {
                serializer.endTag(ns, "vogar-monitor");
                serializer.endDocument();
            }
            socket.close();
            serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        socket = null;
        serverSocket = null;
        serializer = null;
    }
}

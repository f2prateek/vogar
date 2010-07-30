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

package vogar.monitor;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import vogar.Console;
import vogar.Outcome;
import vogar.Result;

/**
 * Connects to a target process to monitor its action using XML over raw
 * sockets.
 */
public final class SocketHostMonitor implements HostMonitor {
    /**
     * Sometimes we fail to parse XML documents; echo up to this many bytes back to the user when
     * that happens.
     */
    private static final int BAD_XML_SNIPPET_SIZE = 1024;

    private final long monitorTimeoutSeconds;
    private final int port;
    private Socket socket;
    private InputStream in;
    private Handler handler;

    public SocketHostMonitor(long monitorTimeoutSeconds, int port, Handler handler) {
        this.monitorTimeoutSeconds = monitorTimeoutSeconds;
        this.port = port;
        this.handler = handler;
    }

    /**
     * Connect to the target process on the given port, read all of its
     * outcomes into {@code handler}, and disconnect.
     */
    @Override public boolean connect() {
        int attempt = 0;
        do {
            try {
                Socket socketToCheck = new Socket("localhost", port);
                InputStream inToCheck = new BufferedInputStream(socketToCheck.getInputStream());
                if (checkStream(inToCheck)) {
                    socket = socketToCheck;
                    in = inToCheck;
                    in.mark(BAD_XML_SNIPPET_SIZE);
                    Console.getInstance().verbose("action monitor connected to " + socket.getRemoteSocketAddress());
                    return true;
                }
                inToCheck.close();
                socketToCheck.close();
            } catch (ConnectException ignored) {
            } catch (SocketException ignored) {
            } catch (IOException e) {
                Console.getInstance().info("Failed to connect to localhost:" + port, e);
                return false;
            }

            if (attempt++ == monitorTimeoutSeconds) {
                Console.getInstance().info("Exceeded " + monitorTimeoutSeconds
                        + " attempts to connect to localhost:" + port);
                return false;
            }

            Console.getInstance().verbose("connection " + attempt + " to localhost:"
                    + port + " failed; retrying in 1s");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        } while (true);
    }

    /**
     * Somewhere between the host and client process, broken socket connections
     * are being accepted. Before we try to do any work on such a connection,
     * check it to make sure it's not dead!
     *
     * TODO: file a bug (against adb?) for this
     */
    private boolean checkStream(InputStream in) throws IOException {
        in.mark(1);
        if (in.read() == -1) {
            return false;
        } else {
            in.reset();
            return true;
        }
    }

    @Override public boolean monitor() {
        if (socket == null || in == null) {
            throw new IllegalStateException();
        }

        try {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            InputSource inputSource = new InputSource(in);
            inputSource.setEncoding("UTF-8");
            parser.parse(inputSource, new ClientXmlHandler(handler));
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            Console.getInstance().verbose("connection error from localhost:" + port + " " + e);
            return false;
        } catch (SAXException e) {
            try {
                in.reset();
                byte[] offendingXml = new byte[BAD_XML_SNIPPET_SIZE];
                int bytes = in.available() > 0 ? in.read(offendingXml) : 0;
                Console.getInstance().warn("received bad XML from localhost:" + port
                        + " " + new String(offendingXml, 0, bytes, "UTF-8"));
            } catch (IOException another) {
                Console.getInstance().warn("received bad XML from localhost:" + port + " " + e);
            }
            return false;
        }

        close();
        return true;
    }

    /**
     * Close this host monitor. This may be called by other threads to violently
     * release the host socket and thread.
     */
    @Override public void close() {
        Socket s = socket;
        if (s == null) {
            return;
        }

        try {
            s.close();
        } catch (IOException ignored) {
        } finally {
            socket = null;
            in = null;
        }
    }

    class ClientXmlHandler extends DefaultHandler {
        private final Handler handler;

        private String currentOutcomeName;
        private Result currentResult;
        private StringBuilder output = new StringBuilder();
        private StringBuilder unstructuredOutput = new StringBuilder();
        private boolean inUnstructuredOutput = false;

        ClientXmlHandler(Handler handler) {
            this.handler = handler;
        }

        /*
         * Our XML wire format looks like this:
         *
         * <?xml version='1.0' encoding='UTF-8' ?>
         * <vogar-monitor>
         *   <unstructured-output>
         *     ... some unstructured output ...
         *   </unstructured-output>
         *   <outcome name="java.util.FormatterTest" action="java.util.FormatterTest"
         *            runner="vogar.target.JUnitRunner">
         *     test output
         *     more test output
         *     <result value="SUCCESS" />
         *   </outcome>
         * </vogar-monitor>
         */

        @Override public void startElement(String uri, String localName,
                String qName, Attributes attributes) throws SAXException {
            if (qName.equals("unstructured-output")) {
                if (currentOutcomeName != null || inUnstructuredOutput) {
                    throw new IllegalStateException(
                            "can't have unstructed output inside an outcome");
                }
                inUnstructuredOutput = true;
            } else if (qName.equals("outcome")) {
                if (currentOutcomeName != null || inUnstructuredOutput) {
                    throw new IllegalStateException();
                }

                currentOutcomeName = attributes.getValue("name");
                handler.output(currentOutcomeName, "");
                handler.runnerClass(currentOutcomeName, attributes.getValue("runner"));

            } else if (qName.equals("result")) {
                currentResult = Result.valueOf(attributes.getValue("value"));

            } else if (!qName.equals("vogar-monitor")) {
                throw new IllegalArgumentException("Unrecognized: " + qName);
            }
        }

        @Override public void characters(char[] ch, int start, int length)
                throws SAXException {
            if (currentOutcomeName != null) {
                String text = new String(ch, start, length);
                output.append(text);
                handler.output(currentOutcomeName, text);
            } else if (inUnstructuredOutput) {
                String text = new String(ch, start, length);
                unstructuredOutput.append(text);
            }
        }

        @Override public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if (qName.equals("outcome")) {
                handler.outcome(new Outcome(currentOutcomeName, currentResult,
                        Collections.singletonList(output.toString())));
                currentOutcomeName = null;
                currentResult = null;
                output.delete(0, output.length());
            } else if (qName.equals("unstructured-output")) {
                handler.print(unstructuredOutput.toString());
                unstructuredOutput.delete(0, unstructuredOutput.length());
                inUnstructuredOutput = false;
            }
        }
    }
}
